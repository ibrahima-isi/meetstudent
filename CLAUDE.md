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

# Run with Docker (requires .env with JWT_SECRET_KEY)
docker compose up app-dev    # dev container with hot reload
docker compose up app        # production-like standalone JAR
docker compose up test       # runs ./mvnw clean verify in a container
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

### Testing

Tests live in `src/test/java/com/bowe/meetstudent/` split into `unit/` (Mockito) and `integration/` (MockMvc + H2).

- Test config (`src/test/resources/application.yml`) disables Flyway and uses `ddl-auto: create-drop` with H2 in `MODE=PostgreSQL`.
- **Authentication in tests:** do NOT use `jwt().authorities(...)` from `SecurityMockMvcRequestPostProcessors` — the generic principal causes a `ClassCastException` against the custom `UserPrincipal`. Use `TestDataUtil.mockUser(String role)`, which builds a `UserPrincipalAuthenticationToken` with a valid `UserPrincipal`.
- Integration tests must use the full versioned path (`/api/v1/schools`, not `/api/schools`).

### Feature workflow

When adding a feature: entity → repository → service → DTOs → mapper → controller (with Swagger annotations) → Flyway migration → unit/integration tests. Create a git branch for features and bug fixes.
