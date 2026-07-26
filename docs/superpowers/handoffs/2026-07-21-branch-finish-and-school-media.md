# Handoff — finish media branch + reconcile school/course/program media

**Created:** 2026-07-21 · **Branch:** `feat/media-access-control` (11 commits ahead of `dev`, `dev` is a clean ancestor, **not pushed**) · **Remote:** `origin` → `ibrahima-isi/meetstudent`

The media access-control plan (Tasks 1–11) is complete and committed; `./mvnw clean verify` is green (67 unit + 82 integration). Two items remain. Do them in order — Point A is quick and unblocks review; Point B is a fresh feature.

---

## Point A — Finish the branch & open a PR into `dev`

**Goal:** get the completed media work reviewed/merged.

1. Invoke `superpowers:finishing-a-development-branch`.
2. Pre-flight (should already hold): `./mvnw clean verify` green; working tree clean.
3. Push and open the PR **into `dev`** (not `main`): 11 commits, title e.g. `feat(media): access control for personal documents`. PR body should summarize: `Media` entity + public/private storage split, role-gated idempotent upload, owner/admin download authz, moderation workflow, user decoupling, `MediaMigrationRunner`, and the security-review hardening on the download endpoint. End the PR body with the Claude session link.
4. **Call out two known gaps in the PR description** so reviewers aren't surprised:
   - **Task 12 not yet run** — H2 tests don't exercise Flyway, so `V14`/`V15` + `ddl-auto: validate` are unverified against Postgres. Verify before merging to any deployed env: `docker compose up -d db` then `SPRING_PROFILES_ACTIVE=docker ./mvnw spring-boot:run`, watch for Flyway / `Schema-validation` errors.
   - **School/course/program uploads are broken** until Point B (see below).

> ⚠️ Commits in this repo are **signed via the 1Password agent**, which prompts for approval. In a non-interactive shell the signing can hang/timeout — if a commit or push stalls, ask the user to approve the 1Password prompt (or run the command themselves with the `!` prefix). It succeeds on retry once approved.

---

## Point B — Reconcile school/course/program media onto the `Media` model

**Problem:** Task 8 removed the old `POST /api/v1/media/{entityType}/upload` endpoint (it was the only caller of `MediaService.saveMedia`, now deleted). School/course/program still store **public image URLs as plain strings** and have no upload path anymore:

- `School.logoUrl`, `School.coverPhotoUrl`
- `Course.photoUrl`
- `Program.photoUrl`
- `SchoolService`/`CourseService`/`ProgramService` still call the **retained** `MediaService.deleteMediaByUrl(...)` and `deleteOldMediaIfChanged(...)` (URL-string helpers kept alive precisely for these three).

The new upload flow (`POST /api/v1/media?category=...`) returns a `MediaDTO` with an **id**, not a URL, and public files live at `uploads/public/<uuid>.<ext>` (served at `/uploads/public/<uuid>.<ext>`). `MediaCategory` already has `SCHOOL_LOGO`, `SCHOOL_COVER` (ADMIN-only, PUBLIC); there is **no** `COURSE_PHOTO`/`PROGRAM_PHOTO` category yet.

**Start with `superpowers:brainstorming`** — this needs a design decision before coding. The core choice:

- **Option 1 (minimal, recommended for the demo):** keep the URL-string fields on the three entities. Expose the public URL of an uploaded media so the admin flow can still be "upload → get URL → set on entity." Concretely: add a computed `publicUrl` to `MediaDTO` (populated only when `visibility == PUBLIC`, derived from `storageKey`), add `COURSE_PHOTO`/`PROGRAM_PHOTO` categories, and have the frontend upload via `POST /api/v1/media?category=SCHOOL_LOGO` then PUT the entity with `publicUrl`. Smallest change; the URL helpers and entity fields stay. Downside: two round-trips, and public files uploaded this way are never garbage-collected via the `Media` row (they're deleted by the existing URL helpers when the entity changes, so acceptable).
- **Option 2 (clean, more work):** replace the URL strings with a `media_id` FK to `Media` on each entity; drop the URL helpers entirely; entities reference `Media` rows and cleanup goes through `MediaService.delete`. Requires Flyway migrations for schools/courses/programs, DTO/mapper changes, and touches every rate/search test that builds these entities.

**Recommendation:** Option 1 now to unblock the demo, with Option 2 tracked as later cleanup. Confirm with the user in the brainstorm.

**Whichever is chosen, follow the repo's feature workflow** (`CLAUDE.md`): entity → repository → service → DTO → mapper → controller (+ Swagger) → Flyway migration → unit **and** integration tests **for each rule and its opposite** (the user is strict about this — e.g. non-admin cannot upload a school logo *and* admin can). Auth in integration tests uses `TestDataUtil.mockUser(id, role)`, full `/api/v1/...` paths.

**Definition of done for Point B:**
- An admin can upload a school logo/cover, course photo, and program photo through a supported endpoint, and the resulting image is retrievable.
- A non-admin cannot.
- Deleting/replacing the entity still cleans up the file (existing behavior preserved).
- `./mvnw clean verify` green; no dead code left (if Option 2, remove `deleteMediaByUrl`/`deleteOldMediaIfChanged`/`deleteMedia`/`getUploadBasePath`/`ensurePathInsideUploadDir` and the `uploadDir` field from `MediaService`).
- Update `CLAUDE.md` + `Readme.md` (the Readme currently states the old `{entityType}/upload` endpoint was removed — document its replacement).

---

## Quick reference

- Plan: `docs/superpowers/plans/2026-07-20-media-access-control.md`
- Spec: `docs/superpowers/specs/2026-07-20-media-access-control-design.md`
- Key classes: `MediaService`, `MediaStorageService`, `MediaController`, `MediaCategory`, `MediaMigrationRunner`, `repositories/MediaRepository`
- Retained URL helpers (Point B decides their fate): `MediaService.deleteMediaByUrl`, `deleteOldMediaIfChanged`, `deleteMedia`, `getUploadBasePath`, `ensurePathInsideUploadDir`, `uploadDir` field.
