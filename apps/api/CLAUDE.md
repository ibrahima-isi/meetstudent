# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MeetStudent backend — a Spring Boot 3 (Java 21) REST API managing schools, programs, courses, accreditations, and a rating system, with JWT auth and role-based access control (PUBLIC / STUDENT / EXPERT / ADMIN).

## Repository workflow

Follow the repo-level branch policy in `../../CLAUDE.md`: start backend work from an up-to-date `dev`, create a short-lived working branch, and open its PR against `dev`. Do not open backend feature, fix, docs, chore, refactor, test, perf or CI PRs directly against `main`; `main` is updated only by a separate `dev` → `main` promotion PR.

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

### Mail and tokens

- **Mail layer** (`mail/`): `EmailSender` port with `SmtpEmailSender` (`MAIL_ENABLED=true` + `SPRING_MAIL_HOST`/`MAIL_FROM`), `DisabledEmailSender` (default: nothing leaves the process) and `DevLogEmailSender` (`app.mail.dev-log`, local only, refused under `prod`). `EmailTemplates` builds fr/en messages; links are `app.frontend-base-url + /<lang>/verify-email#token=...` (fragment, never the request Host). Services publish `AccountEmailRequested`; `AccountEmailListener` sends it after commit on the bounded `mailExecutor`, so a mail failure never fails the request. `GET /api/v1/auth/capabilities` reports `emailEnabled`.
- **Tokens**: `UserTokenService` issues single-use tokens (32 random bytes, Base64url, 43 chars) and stores only their SHA-256 hex in `user_tokens` (`V17`), with the purpose (`EMAIL_VERIFICATION`, `PASSWORD_RESET`), the address they were issued for and `issued_at`/`expires_at` from the injected `Clock`. Issuing an `EMAIL_VERIFICATION` token consumes the older open one of the user (a `PASSWORD_RESET` issue does not: otherwise anyone could kill the owner's live link by spending the address budget, and a successful reset burns the others); issuance is serialised per lower-cased address by `AddressIssueLock` (`pg_advisory_xact_lock` on Postgres, a striped in-JVM lock on H2) because the limits are check-then-insert; rows older than 7 days are purged lazily. Limits per (user, purpose): `app.tokens.cooldown-seconds` (60, only against tokens issued for the user's current address) and `app.tokens.max-per-day` (5) and `app.tokens.max-per-address-per-day` (3, per purpose, counted on `lower(email)` across all users, deleted accounts included: `user_tokens.user_id` is `ON DELETE SET NULL` so the rows survive until the 7-day purge; orphaned tokens cannot be consumed; `V18` index) raise `TooManyRequestsException` (429 + `Retry-After`); registration and profile updates swallow a refusal, only resend shows the 429. Any unusable token (unknown, expired, used, address changed) is the same `InvalidActionTokenException`: 400 `{"token":"Lien invalide ou expiré"}`.
- **Email verification is optional**: unverified accounts sign in normally. `POST /api/v1/users` (optional `lang` fr|en) sends the mail; `POST /api/v1/auth/email-verification/confirm` (public, POST so link prefetchers cannot burn it) -> 204; `.../resend` (Bearer) -> 202, 409 already verified, 429, 503 mail disabled. The address no longer changes through `PUT/PATCH /users/{id}` (a different `email` there is 400 `{"email": ...}`, the same one in any case is ignored): `EmailChangeService` does it in two steps, `POST /api/v1/users/{id}/email-change` (owner only, `{newEmail, currentPassword, lang}`, 202; 400 `{currentPassword|newEmail}`; 429; 503 while mail is disabled) stores nothing on the user and issues an `EMAIL_CHANGE` token bound to the NEW address (the cooldown and caps apply to that address) which is mailed there (`/<lang>/verify-email#token=...&type=change`); the old address keeps signing in until `POST /api/v1/auth/email-change/confirm` (public) swaps it, verified, and burns the other pending/verification links. `GET` returns `{pendingEmail}` (absent when none), `DELETE` cancels, `POST .../resend` mails the pending address again (409 when nothing is pending). A link whose address someone else took meanwhile is the usual `InvalidActionTokenException`. Confirmation increments the persisted credential version and deletes refresh tokens under the user lock in the same transaction. Every old access/refresh session is then refused; the web clears its local identity and invites sign-in with the new address. `V17` marks existing users verified; `AdminBootstrapRunner` marks the admin verified. No token is created while mail is disabled. Email uniqueness is enforced case-insensitively in code (registration and profile update answer 400 `{"email":...}`); there is deliberately no unique `lower(email)` index (existing data may hold case variants), so `findByEmailIgnoreCase` would throw if such duplicates already exist: residual risk, clean the data before adding the index.
- **Password reset**: `POST /api/v1/auth/password-reset/request` (`ForgotPasswordRequest`) always answers `202 {"status":"accepted"}` for a well-formed email (known, unknown, case variant, cooldown, capped, mail disabled): `PasswordResetService.request` looks the address up as a list (`findAllByEmailIgnoreCaseOrderByIdAsc`, oldest account wins, duplicates never throw), does dummy work for unknown addresses, swallows `TooManyRequestsException` and creates no token while mail is disabled. `POST .../confirm` (`ResetPasswordRequest`) -> 204; password length and confirmation are checked first (`FieldValidationException` -> 400 `{"field": message}`), then in one transaction the token is consumed (`PASSWORD_RESET`), the password is written with the targeted `UserRepository.updatePasswordAndRevokeSessions`, the credential version is incremented and other open reset/email-change tokens are burned, `RefreshTokenService.deleteByUserId` ends the sessions and `markEmailVerified` runs. Passwords are limited to 72 UTF-8 bytes (`@MaxUtf8Bytes`, BCrypt) on register, profile update and reset; changing the password through `UserService.patch` also burns outstanding reset tokens. Reset revokes existing access tokens through the persisted credential version. Self-service profile password changes require the current password and revoke access/refresh sessions too. Residual: the per-address reset budget can be exhausted by anyone (the owner then relies on links already sent or an admin reset), and an unknown address answers a few ms faster than a known one (Traefik per-IP limits). Both paths sit under `/api/v1/auth/**` (permitAll, no `WebSecurityConfig` change). `PasswordResetIntegrationTests` compares status, headers and body byte for byte across the cases.
- **Tests**: integration tests that rely on the after-commit mail must be non-transactional and `@Import(CapturingMailConfig.class)` (`CapturingEmailSender.last()` / `extractToken`). H2 never runs `V17`; validate migrations against Postgres with `ddl-auto: validate`.

Residual risks of the mail limits (accepted): an attacker can pre-consume the address budget of a future user (verification is optional, so low impact); plus-addressing and dots make different addresses for the cap; DB uniqueness on `users.email` is still case-sensitive (case-insensitivity is enforced in code only, so two concurrent case-variant registrations can both succeed).

### Production

The `prod` profile (env vars, admin bootstrap via `AdminBootstrapRunner`, proxy headers, disabled Swagger) is documented in `docs/production.md`. Access token lifetime is `security.jwt.access-ttl-minutes` (`JWT_ACCESS_TTL_MINUTES`, default 60); invalid/expired refresh tokens raise `InvalidRefreshTokenException` (HTTP 401).

### First admin and development seed

- `AdminBootstrapRunner` runs on every profile except `test`. `prod` requires `ADMIN_EMAIL` + `ADMIN_PASSWORD` (12+ chars) and aborts startup without them; `dev`/`docker` treat them as optional: with neither set the seeded `admin@meetstudent.com` / `password` row is left alone, with both set a new admin is initialized (or the unchanged published seed is reclaimed) and the seeded default hash is neutralised exactly as in production; setting only one is an error. An existing admin password is preserved across restarts; changing `ADMIN_PASSWORD` no longer rotates it. Existing non-admin accounts cannot be promoted by bootstrap. Seed credential changes revoke sessions and action links. `app.admin.*` lives in `application.yml`.
- `config/seed/DevSeedRunner` (profiles `dev` and `docker` only, never `prod`/`test`) loads `src/main/resources/seed/senegal.json` (`SeedData`): tags, accreditations, schools, programs, courses and program/accreditation links, **no users and no ratings**. It is additive and idempotent (natural keys: tag name, accreditation code, school name, program name within the school, course name within the program) and never edits existing rows. The shipped file is a sourced core set (every school carries a `source` URL): public universities at faculty/UFR level, UADB at degree level, the ISEP network, major private institutions, notable lycées. The model has no school type, so the type lives in tags. It deliberately omits what could not be sourced (most private institutes, vocational centres, courses, accreditation links: they need start/end years). `SeedDataFileTest` guards the file against the schema limits (name <= 50, code <= 5, no duplicates).

### Testing

Tests live in `src/test/java/com/bowe/meetstudent/` split into `unit/` (Mockito) and `integration/` (MockMvc + H2).

- Test config (`src/test/resources/application.yml`) disables Flyway and uses `ddl-auto: create-drop` with H2 in `MODE=PostgreSQL`.
- **Authentication in tests:** do NOT use `jwt().authorities(...)` from `SecurityMockMvcRequestPostProcessors` — the generic principal causes a `ClassCastException` against the custom `UserPrincipal`. Use `TestDataUtil.mockUser(String role)`, which builds a `UserPrincipalAuthenticationToken` with a valid `UserPrincipal`.
- Integration tests must use the full versioned path (`/api/v1/schools`, not `/api/schools`).

### Feature workflow

When adding a feature: entity → repository → service → DTOs → mapper → controller (with Swagger annotations) → Flyway migration → unit/integration tests. Create a working branch from `dev` for features and bug fixes, then open the PR back to `dev`.
