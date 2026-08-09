# MeetStudent Backend 🎓

A robust Spring Boot REST API for a student meeting and collaboration platform. This backend manages schools, educational programs, courses, accreditations, and a comprehensive rating system.

## 🚀 Tech Stack

- **Framework:** Spring Boot 3.5.x (Java 21)
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

### 4. Media & Document Management
- **`Media` entity:** Each uploaded file is a row carrying its category, visibility, owner, and verification status. Uploads return a media **id** (`MediaDTO`), never a disk path.
- **Public vs private storage:** Public media (school logos/covers, user photos) is served statically under `/uploads/public/**`. Personal documents (diplomas, certificates, bulletins, presentation videos) are **private** — stored outside the static tree and reachable only via `GET /api/v1/media/{id}` with an owner-or-admin check.
- **Role-gated upload & idempotency:** Personal documents are uploadable by STUDENT/EXPERT/ADMIN, school media by ADMIN only. Send an `Idempotency-Key` header to make retries safe.
- **Moderation:** Documents start `PENDING`; an admin marks them `VERIFIED`/`REJECTED` (with an optional reason).
- **Automatic cleanup:** Files are deleted from disk when the owning entity/media is deleted or replaced.

**Media endpoints**
| Method | Path | Access |
| :--- | :--- | :--- |
| `POST` | `/api/v1/media?category=DIPLOMA` (multipart `file`, optional `Idempotency-Key` header) | Role depends on category |
| `GET` | `/api/v1/media/{id}` | Public media: anyone; private: owner or admin |
| `GET` | `/api/v1/media/mine` | Authenticated (own media) |
| `GET` | `/api/v1/media?status=PENDING` | ADMIN (moderation queue) |
| `PATCH` | `/api/v1/media/{id}/verification` | ADMIN |
| `DELETE` | `/api/v1/media/{id}` | Owner or admin |

> **Breaking change:** `diplomas`, `certificates`, and the presentation video are no longer string fields on the user. They are uploaded via `POST /api/v1/media?category=...` and returned on the user as `MediaDTO` objects (`diplomas`/`certificates` are `List<MediaDTO>`, `presentationVideo` is a `MediaDTO`). The old `POST /api/v1/media/{entityType}/upload` endpoint has been removed.

## 📦 Getting Started

### 1. Configuration
Copy the example env file at the **repository root** and fill it in:
```bash
cp .env.example .env   # JWT_SECRET_KEY and POSTGRES_PASSWORD
```

### 2. Run with Docker
The compose file lives at the repository root and starts Postgres, the API and
the Angular front together:
```bash
docker compose up --build       # full stack
docker compose up --build api   # API + Postgres only
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
