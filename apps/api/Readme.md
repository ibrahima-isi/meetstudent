# MeetStudent API

REST API of MeetStudent: Spring Boot 3, Java 21, PostgreSQL, Flyway, JWT auth.
It serves schools, programs, courses, ratings, media and the admin endpoints, under `/api/v1`.

## Commands

Run from `apps/api`.

```bash
./mvnw clean verify                 # unit + integration tests
./mvnw test -Dtest=UserServiceTest  # one test class
./mvnw spring-boot:run              # dev profile: Postgres on localhost:5433, or set CUSTOM_DEV_DB_URL
docker compose up -d api            # from the repo root: api + its Postgres
```

`.env` at the repo root needs `JWT_SECRET_KEY` and `POSTGRES_PASSWORD` (copy `.env.example`).
Swagger UI: `http://localhost:8080/swagger-ui.html`.

## More

- Agent guide (architecture, media, Flyway, tests): [CLAUDE.md](CLAUDE.md)
- API reference: [docs/reference.md](docs/reference.md)
- Production environment variables: [docs/production.md](docs/production.md)
- Deployment: [docs/DEPLOY.md](../../docs/DEPLOY.md)
