# MeetStudent Backend 🎓

A robust Spring Boot REST API for a student meeting and collaboration platform. This backend manages schools, educational programs, courses, accreditations, and a comprehensive rating system.

## 🚀 Tech Stack

- **Framework:** Spring Boot 3.4.7 (Java 17)
- **Security:** Spring Security + JWT (Auth0) + Database-backed Refresh Tokens
- **Database:** PostgreSQL (Cloud-hosted via **Neon**)
- **Migrations:** Flyway
- **Testing:** JUnit 5, Mockito, H2 (In-memory for integration tests)
- **Documentation:** SpringDoc OpenAPI 2.0 (Swagger UI)
- **Build Tool:** Maven
- **Utilities:** Lombok, ModelMapper, DataFaker

## 🛠️ Features

### 1. Advanced Authentication
- **Dual Token System:** Short-lived Access Tokens + Long-lived Refresh Tokens.
- **Token Rotation:** Every time a refresh token is used, a new pair is issued, and the old one is revoked.
- **Revocation:** Database-backed tokens allow for immediate session termination (Logout).

### 2. Role-Based Access Control (RBAC)
| Role | Permissions |
| :--- | :--- |
| **PUBLIC** | List/Search Schools, Programs, Courses, and see Ratings. |
| **STUDENT** | Manage personal profile, upload diplomas/certs, manage Wishlist, rate **Schools**. |
| **EXPERT** | Manage personal profile, upload diplomas/certs, rate **Schools, Programs, and Courses**. |
| **ADMIN** | Full CRUD for all entities, system-wide moderation, Tag management. |

### 3. School & Academic Management
- **School Tagging:** Categories like `PUBLIC`, `PRIVATE`, `GRANDE_ECOLE`.
- **Complex Search:** Filter schools by city, country, tag, and program name simultaneously.
- **Wishlist:** Students can save schools to their personal wishlist.
- **Accreditations:** Manage institutional certifications (e.g., AACSB) with validity periods.

### 4. Media Lifecycle Management
- **Automatic Cleanup:** Files (logos, diplomas, videos) are automatically deleted from the filesystem when an entity is deleted or when a file is replaced/updated.

## 📦 Getting Started

### 1. Configuration
Create a `.env` file in the root directory:
```bash
JWT_SECRET_KEY=your_secure_random_key
```

### 2. Run with Docker
```bash
docker compose up app-dev
```

### 3. API Documentation
- **Swagger UI:** `http://localhost:8080/swagger-ui.html`

## 🧪 Test Data (Payloads)

### 1. Authentication & Users
**Login (POST `/api/v1/auth`)**
```json
{
  "username": "admin@meetstudent.com",
  "password": "password"
}
```

**Register (POST `/api/v1/users`)**
```json
{
  "firstname": "Jane",
  "lastname": "Student",
  "email": "student@test.com",
  "password": "password",
  "confirmedPassword": "password",
  "role": { "id": 4 } 
}
```

**Refresh Token (POST `/api/v1/auth/refresh`)**
```json
{
  "refreshToken": "uuid-from-login-response"
}
```

### 2. Academic Entities (ADMIN ONLY)
**Create School (POST `/api/v1/schools`)**
```json
{
  "name": "HEC Paris",
  "code": "HECP1",
  "address": {
    "city": "Jouy-en-Josas",
    "country": "France",
    "location": "1 Rue de la Libération"
  }
}
```

**Create Program (POST `/api/v1/programs`)**
```json
{
  "name": "Master in Management",
  "code": "MIM01",
  "duration": 2,
  "schoolId": 1
}
```

**Create Course (POST `/api/v1/courses`)**
```json
{
  "name": "Corporate Strategy",
  "code": "STRAT",
  "programId": 1
}
```

**Link Accreditation to Program (POST `/api/v1/programs/{pId}/accreditations/{aId}`)**
- Query Params: `startsAt=2024&endsAt=2029`

### 3. Ratings (STUDENT/EXPERT)
**Rate School (POST `/api/v1/school-rates`)**
```json
{
  "note": 5.0,
  "comment": "Excellence académique et réseau exceptionnel.",
  "schoolId": 1,
  "userId": 2
}
```

### 4. Wishlist & Filtering
**Add to Wishlist (POST `/api/v1/users/{uId}/wishlist/{sId}`)**
- Role: STUDENT only.

**Search Schools (GET `/api/v1/schools/search`)**
- Params: `?city=Paris&tag=GRANDE_ECOLE&program=Management`

## 📁 Project Structure

- `src/main/java`: Layered architecture (Controllers, Services, Repositories, Entities, DTOs).
- `src/main/resources/db/migration`: Versioned schema updates.
- `src/test/java`: `unit` (Mockito) and `integration` (MockMvc + H2) test suites.
- `uploads/`: Local storage for media.
