# School / Course / Program media reconciliation — design

**Date:** 2026-07-22
**Branch:** `feat/media-access-control` (Point B, follows PR #13)
**Status:** approved (design), pending implementation plan

## Problem

The media access-control work (PR #13) removed the old
`POST /api/v1/media/{entityType}/upload` endpoint. That left the public image
fields on three entities with **no upload path**:

- `School.logoUrl`, `School.coverPhotoUrl`
- `Course.photoUrl`
- `Program.photoUrl`

These are plain `String` columns holding a URL, disconnected from the new
`Media` entity. There is currently no supported way to set them.

## Decision

Adopt **Option 2 (media_id FK)**: replace the URL-string fields with nullable
foreign keys to the `Media` table. Public images become first-class `Media`
rows uploaded through the existing `POST /api/v1/media?category=...` flow, and
each entity references its media by id. Chosen over the smaller Option 1
(keep URL strings + add `publicUrl`) for a cleaner long-term model.

Confirmed sub-decisions:

- **Legacy data:** no migration. Existing `*_url` values are discarded when the
  columns are dropped; admins re-upload after deploy (acceptable pre-deploy).
- **DTO shape:** asymmetric. Request bodies carry an Integer `*MediaId`;
  responses carry a resolved `MediaDTO` object (with `publicUrl`).
- **Entity representation:** plain `Integer` FK column resolved in the mapper
  via `MediaService`, mirroring the `UserMapper`-injects-`MediaService`
  decoupling pattern (not a JPA `@ManyToOne`).

## Components

### 1. `MediaCategory` — new categories

Add two categories, identical shape to `SCHOOL_LOGO`/`SCHOOL_COVER`
(PUBLIC, non-moderated, `ROLE_ADMIN`-only):

```java
COURSE_PHOTO(MediaVisibility.PUBLIC, false, Set.of("ROLE_ADMIN")),
PROGRAM_PHOTO(MediaVisibility.PUBLIC, false, Set.of("ROLE_ADMIN")),
```

### 2. `MediaDTO.publicUrl`

Add a `String publicUrl` field. `MediaMapper.toDTO` populates it **only for
PUBLIC media**, as `"/uploads/" + storageKey` (public storage keys are
`public/<uuid>.<ext>`, statically served under `/uploads/public/**`). Private
media leave it `null` — they are reached through `GET /api/v1/media/{id}`.

This is the field that lets a client turn a public `Media` row into a
renderable image URL. Relative by design; the frontend prepends its host.

### 3. Entities — FK columns replace URL strings

| Entity  | Removed              | Added (nullable Integer) |
|---------|----------------------|--------------------------|
| School  | `logoUrl`, `coverPhotoUrl` | `logoMediaId`, `coverMediaId` |
| Course  | `photoUrl`           | `photoMediaId`           |
| Program | `photoUrl`           | `photoMediaId`           |

Columns: `logo_media_id`, `cover_media_id`, `photo_media_id`. Plain `Integer`
fields (no JPA relationship) — keeps the entity graph decoupled from `Media`
and degrades gracefully when a referenced media row is gone.

### 4. DTOs — asymmetric input/output

Each DTO gains **both** an input id and an output object:

- `SchoolDTO`: remove `logoUrl`/`coverPhotoUrl`; add input `logoMediaId`,
  `coverMediaId` (Integer) and output `logo`, `cover` (`MediaDTO`).
- `CourseDTO`: remove `photoUrl`; add input `photoMediaId`, output `photo`.
- `ProgramDTO`: remove `photoUrl`; add input `photoMediaId`, output `photo`.

`toEntity` reads the `*MediaId` inputs; `toDTO` resolves the ids to `MediaDTO`
objects via `MediaService.findById` (returns `null` if the row is missing).

### 5. Mappers

`SchoolMapper`, `CourseMapper`, `ProgramMapper` gain a `MediaService` +
`MediaMapper` dependency (like `UserMapper`). `toDTO` resolves each `*MediaId`
to a `MediaDTO`; `toEntity` copies the `*MediaId` inputs straight through.

### 6. Services — replace/delete cleanup

Add an internal `MediaService.deleteById(Integer mediaId)` that deletes the row
and its file with **no principal check** (these entity endpoints are already
`ROLE_ADMIN`-gated in `WebSecurityConfig`). Used for server-side orchestration.

- **On update/patch:** when a `*MediaId` changes to a different non-null value
  (or to null), delete the previously-referenced media via `deleteById`.
  Replaces the old string-based `deleteOldMediaIfChanged`/`deleteMediaByUrl`
  calls in `SchoolService` (and the equivalents wherever `Course`/`Program`
  handle it).
- **On entity delete:** delete referenced media (`logoMediaId`, `coverMediaId`,
  `photoMediaId`) via `deleteById` before removing the entity.

The legacy `deleteMediaByUrl` / `deleteOldMediaIfChanged` / `deleteMedia`
helpers on `MediaService` become unused for these entities; remove the calls.
Leave the helper methods only if still referenced elsewhere (verify with a
usage search; delete if fully orphaned).

### 7. Migration `V16` (Postgres only)

```sql
ALTER TABLE schools  ADD COLUMN logo_media_id  INTEGER,
                     ADD COLUMN cover_media_id INTEGER;
ALTER TABLE courses  ADD COLUMN photo_media_id INTEGER;
ALTER TABLE programs ADD COLUMN photo_media_id INTEGER;

ALTER TABLE schools
  ADD CONSTRAINT fk_schools_logo_media  FOREIGN KEY (logo_media_id)
      REFERENCES media(id) ON DELETE SET NULL,
  ADD CONSTRAINT fk_schools_cover_media FOREIGN KEY (cover_media_id)
      REFERENCES media(id) ON DELETE SET NULL;
ALTER TABLE courses
  ADD CONSTRAINT fk_courses_photo_media FOREIGN KEY (photo_media_id)
      REFERENCES media(id) ON DELETE SET NULL;
ALTER TABLE programs
  ADD CONSTRAINT fk_programs_photo_media FOREIGN KEY (photo_media_id)
      REFERENCES media(id) ON DELETE SET NULL;

ALTER TABLE schools  DROP COLUMN logo_url, DROP COLUMN cover_photo_url;
ALTER TABLE courses  DROP COLUMN photo_url;
ALTER TABLE programs DROP COLUMN photo_url;
```

The H2 test suite runs with Flyway disabled and generates the schema from the
entities, so it needs no FK constraint — the plain `Integer` columns suffice
and dangling ids resolve to `null`.

## Data flow (happy path)

```
1. Admin: POST /api/v1/media?category=SCHOOL_LOGO  (multipart file)
          -> 201 { id: 42, ..., publicUrl: "/uploads/public/ab12.jpg" }
2. Admin: PUT  /api/v1/schools/{id}  { logoMediaId: 42, ... }
          -> 200 { logo: { id:42, ..., publicUrl }, ... }
3. Anyone: GET /api/v1/schools/{id}
          -> logo.publicUrl -> <img src="{host}/uploads/public/ab12.jpg">
```

## Error handling

- `*MediaId` referencing a non-existent/deleted media → `toDTO` resolves to
  `null` (no error; image simply absent).
- Uploading `SCHOOL_LOGO`/`COURSE_PHOTO`/`PROGRAM_PHOTO` as non-admin →
  existing `assertCanUpload` throws `AccessDeniedException` (403).
- Replacing a media id deletes the orphaned old file best-effort; failure to
  delete the file does not fail the transaction (row still updated).

## Testing

Follow the repo rule: **test each access rule and its opposite.**

Unit:
- `MediaCategoryTest`: `COURSE_PHOTO`/`PROGRAM_PHOTO` are PUBLIC, non-moderated,
  admin-only, not personal documents.
- `MediaMapperTest`: `publicUrl` set for PUBLIC media, `null` for PRIVATE.
- Mapper tests (School/Course/Program): `*MediaId` resolves to `MediaDTO`;
  missing id → `null`; `toEntity` copies ids through.
- Service tests: update changing `*MediaId` deletes the old media; entity
  delete deletes referenced media; `deleteById` removes row + file.

Integration (MockMvc + H2):
- Admin uploads `SCHOOL_LOGO`/`COURSE_PHOTO`/`PROGRAM_PHOTO` → 201 with
  `publicUrl`; non-admin → 403 (rule + opposite).
- `PUT /schools/{id}` with `logoMediaId` → response `logo` carries `publicUrl`.
- `GET` on each entity exposes resolved media object.
- Deleting a school with a `logoMediaId` removes the `Media` row.

Keep `./mvnw clean verify` green throughout.

## Out of scope

- Migrating existing legacy URL values (dropped per decision).
- Reworking the private/personal-document flow (unchanged).
- Any frontend work.
```

## Documentation

Update `CLAUDE.md` "Media & documents" section: note `COURSE_PHOTO`/
`PROGRAM_PHOTO` categories, the `*MediaId` FK model on School/Course/Program,
`MediaDTO.publicUrl`, and `V16`.
