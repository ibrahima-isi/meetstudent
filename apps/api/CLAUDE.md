# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

## Workflow

Branch policy, worktrees and PR rules are in `../../CLAUDE.md`: backend work starts from an up-to-date `dev` and its PR targets `dev`.


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

- **controllers/** — `@RestController`s; validate input, call services, map entities to DTOs via mappers, return `ResponseEntity<DTO>` or `Page<DTO>`. Annotated with Swagger docs (`@Tag`, `@Operation`, `@ApiResponse`). List endpoints are paginated with `Pageable`; a public list either fixes its order (rates, forum: `?sort=` ignored) or checks `?sort=` against its `SortAllowlist` (exact names of fields the response shows, never a path through an association: the row order would leak the joined column).
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
- **Upload limits, entity images, moderation, endpoints, migrations:** see "Media details" in `docs/reference.md`.

### Mail, tokens, accounts, forum

Mail layer (`EmailSender` port, after-commit listeners on bounded executors), single-use tokens, optional email verification, two-step email change, password reset, one rating per user per target, expert approval, forum and forum moderation, brute-force limits: all described in "Mail, tokens, accounts, ratings, experts and forum" in `docs/reference.md`. Read it before touching `mail/`, `UserTokenService`, `auth/`, rate controllers, `AdminExpertController` or `ForumController`.

### Production

The `prod` profile (env vars, admin bootstrap via `AdminBootstrapRunner`, proxy headers, disabled Swagger) is documented in `docs/production.md`. Access token lifetime is `security.jwt.access-ttl-minutes` (`JWT_ACCESS_TTL_MINUTES`, default 60); invalid/expired refresh tokens raise `InvalidRefreshTokenException` (HTTP 401).

Refresh-token reuse detection (`V37`), first admin bootstrap (`AdminBootstrapRunner`) and the development seed (`DevSeedRunner`): see "Sessions and seed" in `docs/reference.md`.

### API contract quirks

The behaviour every client must respect (status codes, PATCH-like `PUT`, limits, filters) lives in one place: "API contract quirks" in `docs/reference.md`. Update it in the same PR as any contract change.

### Testing

Tests live in `src/test/java/com/bowe/meetstudent/` split into `unit/` (Mockito) and `integration/` (MockMvc + H2).

- Test config (`src/test/resources/application.yml`) disables Flyway and uses `ddl-auto: create-drop` with H2 in `MODE=PostgreSQL`.
- **Isolation without restarting Spring:** a test class that needs an empty database for every test declares `@ExtendWith(ResetStateExtension.class)` (after `@SpringBootTest`, `support/`): before and after each test it truncates every H2 table with `RESTART IDENTITY` and forgets the in-memory rate limiters, so the cached context is reused. Do not use `@DirtiesContext(AFTER_EACH_TEST_METHOD)`: each use rebuilt the context for every test and made the suite take 6-7 minutes in CI.
- **Authentication in tests:** do NOT use `jwt().authorities(...)` from `SecurityMockMvcRequestPostProcessors` — the generic principal causes a `ClassCastException` against the custom `UserPrincipal`. Use `TestDataUtil.mockUser(String role)`, which builds a `UserPrincipalAuthenticationToken` with a valid `UserPrincipal`.
- Integration tests must use the full versioned path (`/api/v1/schools`, not `/api/schools`).

### Feature workflow

When adding a feature: entity → repository → service → DTOs → mapper → controller (with Swagger annotations) → Flyway migration → unit/integration tests.
