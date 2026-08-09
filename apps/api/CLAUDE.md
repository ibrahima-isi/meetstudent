# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MeetStudent backend — a Spring Boot 3 (Java 21) REST API managing schools, programs, courses, accreditations, and a rating system, with JWT auth and role-based access control (PUBLIC / STUDENT / EXPERT / ADMIN).

## Commands

```bash
# Build and run all tests (unit + integration)
./mvnw clean verify

# Unit tests only (surefire: **/*Test.java)
./mvnw test

# Integration tests only (failsafe: **/*IntegrationTests.java)
./mvnw failsafe:integration-test failsafe:verify

# Run a single test class / method
./mvnw test -Dtest=UserServiceTest
./mvnw test -Dtest=UserServiceTest#methodName
./mvnw verify -Dit.test=UserControllerIntegrationTests -Dtest=skip -DfailIfNoTests=false

# Run the app locally (dev profile, hot reload via devtools)
./mvnw spring-boot:run

# Run with Docker — the compose file now lives at the repo root and brings up
# the whole stack (Postgres + API + Angular front). See ../../compose.yml.
(cd ../.. && docker compose up --build)      # full stack
(cd ../.. && docker compose up --build api)  # API + its Postgres only

# Hot-reloading API in Docker (devtools). Recompile on the host — from your IDE
# or `./mvnw compile` — and the container restarts in about a second.
(cd ../.. && docker compose -f compose.yml -f compose.dev.yml up --build api)
```

Swagger UI is at `http://localhost:8080/swagger-ui.html`. All endpoints are versioned under `/api/v1/...`.

The test naming convention is enforced by the build: unit tests must end in `Test`, integration tests in `IntegrationTests`, or they won't run.

## Architecture

Standard layered architecture under `src/main/java/com/bowe/meetstudent/`:

- **controllers/** — `@RestController`s; validate input, call services, map entities to DTOs via mappers, return `ResponseEntity<DTO>` or `Page<DTO>`. Annotated with Swagger docs (`@Tag`, `@Operation`, `@ApiResponse`). List endpoints are paginated with `Pageable`.
- **services/** — business logic, `@Transactional`. Services return entities; mapping to DTOs happens in the controller.
- **repositories/** — Spring Data JPA interfaces.
- **entities/** — JPA entities (subpackages `rates/`, `embedded/`).
- **dto/**, **mappers/** — API contracts and Entity↔DTO conversion (ModelMapper plus manual mappers in `mappers/implementations/`).
- **security/** — Spring Security + JWT (Auth0 java-jwt). Dual-token system: short-lived access tokens plus database-backed refresh tokens with rotation and revocation. Endpoints are secured by default; `WebSecurityConfig` holds the access rules. A custom `UserPrincipal` is the authenticated principal (also used by JPA auditing in `JpaAuditingConfig`).

Dependency injection is by constructor via Lombok `@RequiredArgsConstructor`. Prefer `var` for clearly inferred local variables.

### Database & migrations

- PostgreSQL in dev/prod (profiles: `application.yml`, `application-docker.yml`, `application-prod.yml`); H2 in tests.
- All schema changes go through Flyway SQL files in `src/main/resources/db/migration`, named `V<Version>__<Description>.sql`.
- **H2 compatibility:** never use `@Column(columnDefinition = "text[]")` — it breaks H2. Use `@JdbcTypeCode(java.sql.Types.ARRAY)` on `List<String>` fields instead.
- Media files (logos, diplomas, videos) live in `uploads/`; `MediaService` deletes files from disk when the owning entity is deleted or the file is replaced.

### Media & documents

Personal documents (diplomas, certificates, bulletins, presentation videos) are modeled by the `Media` entity (one row per stored file) with category-derived visibility and a verification status — they are **not** stored as string fields on `UserEntity` anymore.

- **Storage split:** public files (`SCHOOL_LOGO`, `SCHOOL_COVER`, `USER_PHOTO`) go under `uploads/public/` and are served statically (`/uploads/public/**`); private files (personal documents) go under `storage/private/` (`file.private-dir`), are **never** mapped statically, and are reachable only through `GET /api/v1/media/{id}` with an owner-or-admin check. Configure both via `file.upload-dir` / `file.private-dir`.
- **Service split:** `MediaStorageService` does low-level file I/O (atomic write via `.tmp` + `ATOMIC_MOVE`, load, delete, anti-traversal); `MediaService` does orchestration — role-gated upload, idempotency (`Idempotency-Key` header, deduped on `(ownerId, idempotencyKey)`), download authorization, moderation, and delete.
- **Categories & roles:** `MediaCategory` carries `getVisibility()`, `isModerated()`, `getAllowedUploadRoles()`, `isPersonalDocument()`. Personal documents are uploadable by `ROLE_STUDENT`/`ROLE_EXPERT`/`ROLE_ADMIN`; school media is `ROLE_ADMIN`-only; user photo is any authenticated role.
- **Entity images:** `School` (`logoMediaId`, `coverMediaId`), `Course` (`photoMediaId`), and `Program` (`photoMediaId`) reference public `Media` rows by FK instead of URL strings. Upload flow: `POST /api/v1/media?category=SCHOOL_LOGO|SCHOOL_COVER|COURSE_PHOTO|PROGRAM_PHOTO` (ADMIN-only) → read `MediaDTO.publicUrl` (a relative `/uploads/public/...` URL, set only on public media) → `PUT`/`PATCH` the entity with the media id. Response DTOs expose the resolved media as `logo`/`cover`/`photo` objects. Replacing or deleting an entity deletes the orphaned media (`MediaService.deleteById`); `V16` adds the FK columns (`ON DELETE SET NULL`) and drops the legacy URL columns.
- **Moderation:** moderated media starts `PENDING`; an admin sets `VERIFIED`/`REJECTED` (with optional reason) via `PATCH /api/v1/media/{id}/verification`. Status is informational — a `REJECTED` document is not functionally blocked.
- **Endpoints:** `POST /api/v1/media?category=...` (upload), `GET /api/v1/media/{id}` (download), `GET /api/v1/media/mine` (own media), `GET /api/v1/media?status=PENDING` (admin queue), `PATCH /api/v1/media/{id}/verification` (admin), `DELETE /api/v1/media/{id}` (owner/admin). Downloads render inline only for a MIME allowlist, else force `attachment`, and always send `X-Content-Type-Options: nosniff` + a sandbox CSP.
- **Migrations:** `V14`/`V15` create the `media` table and migrate legacy `users.diplomas`/`certificates`/`presentation_video_url` data; `MediaMigrationRunner` relocates pre-existing private files into `storage/private/` at startup. These run only against Postgres — the H2 test suite (Flyway disabled) generates the schema from the entity.

### Testing

Tests live in `src/test/java/com/bowe/meetstudent/` split into `unit/` (Mockito) and `integration/` (MockMvc + H2).

- Test config (`src/test/resources/application.yml`) disables Flyway and uses `ddl-auto: create-drop` with H2 in `MODE=PostgreSQL`.
- **Authentication in tests:** do NOT use `jwt().authorities(...)` from `SecurityMockMvcRequestPostProcessors` — the generic principal causes a `ClassCastException` against the custom `UserPrincipal`. Use `TestDataUtil.mockUser(String role)`, which builds a `UserPrincipalAuthenticationToken` with a valid `UserPrincipal`.
- Integration tests must use the full versioned path (`/api/v1/schools`, not `/api/schools`).

### Feature workflow

When adding a feature: entity → repository → service → DTOs → mapper → controller (with Swagger annotations) → Flyway migration → unit/integration tests. Create a git branch for features and bug fixes.
