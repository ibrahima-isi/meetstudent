# Gemini Context & Instructions

This file provides context and instructions for Gemini (AI assistant) when working on the `meetstudent` project.

## Project Overview
**Name:** MeetStudent
**Description:** A Spring Boot backend application for managing student-related data (Schools, Courses, Programs, etc.).
**Type:** REST API
**Build Tool:** Maven
**Java Version:** 17

## Tech Stack
- **Framework:** Spring Boot 3.4.6
- **Database:** PostgreSQL (Production/Dev), H2 (Test)
- **ORM:** Spring Data JPA
- **Migrations:** Flyway (`src/main/resources/db/migration`)
- **Documentation:** SpringDoc OpenAPI (Swagger UI)
- **Utilities:** 
    - Lombok (Boilerplate reduction)
    - ModelMapper (Entity <-> DTO mapping)
    - DataFaker (Test data generation)
- **Security:** Spring Security + JWT (Auth0 java-jwt)

## Architecture & Patterns
The project follows a standard layered architecture:
1.  **Controllers (`controllers/`)**: Handle HTTP requests, input validation, and return DTOs.
    - Use `@RestController`.
    - Use `@RequiredArgsConstructor` for dependency injection.
    - Document endpoints with `@Operation` and `@ApiResponse`.
    - Return `ResponseEntity<DTO>` or `Page<DTO>`.
2.  **Services (`services/`)**: Business logic.
    - Transaction management (`@Transactional`).
    - Return Entities or DTOs (prefer Entities to keep Service layer flexible, map in Controller). *Correction based on code observed: Controller calls mapper.*
3.  **Repositories (`repositories/`)**: Data access interfaces extending `JpaRepository`.
4.  **Entities (`entities/`)**: JPA entities mapping to database tables.
5.  **DTOs (`dto/`)**: Data Transfer Objects for API contracts.
6.  **Mappers (`mappers/`)**: Logic to convert between DTOs and Entities.

## Coding Conventions & Guidelines

### General
- **Style:** Standard Java conventions.
- **Variable Declaration:** Use `var` for local variables where type is inferred clearly.
- **Injection:** Constructor injection via Lombok's `@RequiredArgsConstructor`.

### Controllers
- Always use DTOs for request bodies and response types.
- Paginate list endpoints using `Pageable`.
- Annotate endpoints with Swagger/OpenAPI descriptions (`@Tag`, `@Operation`, `@ApiResponse`).

### Database & Migrations
- **Flyway:** All schema changes must be done via SQL migration files in `src/main/resources/db/migration`.
- **Naming:** Follow the pattern `V<Version>__<Description>.sql` (e.g., `V9__add_new_table.sql`).

### Security
- Endpoints are secured by default.
- JWT is used for stateless authentication.
- `SecurityConfig` manages access rules.

## Development Tasks
When asked to implement a feature:
1. Create a git branch for feature and bug fix
2. Check if a new Entity is needed.
3. Create/Update Repository.
4. Create/Update Service (business logic).
5. Create DTOs.
6. Create Mapper (if manual) or configure ModelMapper.
7. Create Controller with Swagger annotations.
8. Add Flyway migration script.
9. Add Unit/Integration tests.
