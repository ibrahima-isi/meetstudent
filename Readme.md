# MeetStudent Backend 🎓

A robust Spring Boot REST API for a student meeting and collaboration platform. This backend manages schools, educational programs, courses, accreditations, and a comprehensive rating system.

## 🚀 Tech Stack

- **Framework:** Spring Boot 3.4.7 (Java 17)
- **Security:** Spring Security + JWT (Auth0)
- **Database:** PostgreSQL (Cloud-hosted via **Neon**)
- **Migrations:** Flyway
- **Testing:** JUnit 5, Mockito, H2 (In-memory for integration tests)
- **Documentation:** SpringDoc OpenAPI (Swagger UI)
- **Build Tool:** Maven
- **Infrastructure:** Docker & Docker Compose

## 🛠️ Features

- **Authentication:** Secure JWT-based login and role-based access control (ADMIN, STUDENT, EXPERT).
- **School Management:** Full CRUD operations for schools/universities including location and metadata.
- **Educational Programs:** Management of degrees and certifications linked to schools.
- **Course Catalog:** Individual subjects linked to specific programs.
- **Accreditation System:** Global accreditation management (AACSB, EQUIS, etc.) with Program-to-Accreditation relationships.
- **Rating & Reviews:** Multidimensional rating system for Schools, Programs, and Courses with automated average score calculations.
- **Media Management:** Dedicated endpoint for uploading and serving media assets (logos, cover photos, etc.).

## 📦 Getting Started

### Prerequisites
- Docker & Docker Compose
- Java 17+ (for local development)
- Maven 3.8+ (for local development)

### 1. Configuration
Create a `.env` file in the root directory with the following variables:
```bash
POSTGRES_USER=your_user
POSTGRES_PASSWORD=your_password
SPRING_DATASOURCE_URL=jdbc:postgresql://your-neon-host/meetstudent?sslmode=require
JWT_SECRET_KEY=your_secure_random_key
```

### 2. Run with Docker
Start the development environment (connected to Neon PostgreSQL):
```bash
docker compose up app-dev
```
The API will be available at `http://localhost:8080`.

### 3. Run Locally
```bash
./mvnw spring-boot:run
```

## 📚 API Documentation

Once the application is running, you can access the full interactive API documentation at:
- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **API Docs:** `http://localhost:8080/api-docs`

## 🧪 Testing Strategy

The project separates tests into two distinct phases to ensure speed and reliability:

### Unit Tests (Pure Logic)
- **Package:** `com.bowe.meetstudent.unit`
- **Focus:** Service-level business logic using Mockito (no database/Spring context).
- **Run:** `docker compose run --rm unit-test` or `mvn test`

### Integration Tests (Infrastructure & API)
- **Package:** `com.bowe.meetstudent.integration`
- **Focus:** Controller endpoints, JPA mappings, and Security filters using H2 Database.
- **Run:** `docker compose run --rm test` or `mvn verify`

## 📁 Project Structure

- `src/main/java`: Source code following a layered architecture (Controllers, Services, Repositories, Entities, DTOs, Mappers).
- `src/main/resources/db/migration`: Flyway SQL migration scripts.
- `src/test/java`: Separate `unit` and `integration` test suites.
- `compose.yml`: Multi-service Docker configuration.
- `.env`: (Local only) Secret management.
