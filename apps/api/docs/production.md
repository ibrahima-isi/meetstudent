# Production configuration

Run the API with `SPRING_PROFILES_ACTIVE=prod`. Everything below is read from environment variables;
the prod profile has no fallbacks for the required ones, so a missing value stops startup.

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `JWT_SECRET_KEY` | yes | none | HMAC secret for access tokens. Use 32+ random characters. |
| `SPRING_DATASOURCE_URL` | yes | none | e.g. `jdbc:postgresql://db:5432/meetstudent` |
| `SPRING_DATASOURCE_USERNAME` | yes | none | Database user. |
| `SPRING_DATASOURCE_PASSWORD` | yes | none | Database password. |
| `CORS_ALLOWED_ORIGINS` | yes | none | Comma-separated front origins, e.g. `https://app.example.com`. |
| `ADMIN_EMAIL` | yes | none | Email of the platform admin account. |
| `ADMIN_PASSWORD` | yes | none | Admin password, at least 12 characters. |
| `JWT_ACCESS_TTL_MINUTES` | no | `60` | Access token lifetime in minutes (must be >= 1). The web client refreshes on 401. |
| `FILE_UPLOAD_DIR` | no | `uploads` | Public media (served at `/uploads/public/**`). Mount a persistent volume. |
| `FILE_PRIVATE_DIR` | no | `storage/private` | Private documents (never served statically). Mount a persistent volume. |

## Admin bootstrap

`V2__data.sql` seeds `admin@meetstudent.com` with the password `password`, and applied migrations
cannot be edited. Under the `prod` profile `AdminBootstrapRunner` runs on every boot and:

1. refuses to start unless `ADMIN_EMAIL` is set and `ADMIN_PASSWORD` is at least 12 characters;
2. takes the admin account for `ADMIN_EMAIL` (otherwise the seeded default admin, otherwise creates
   one), and sets its email, `ROLE_ADMIN` and bcrypt password from the environment;
3. replaces the seeded default hash on any remaining account with a random unusable password, so
   nobody can log in with `password`.

It is idempotent. Changing `ADMIN_PASSWORD` and restarting rotates the password. Changing
`ADMIN_EMAIL` creates a new admin; remove or demote the old one by hand.

## Behind a TLS proxy

`server.forward-headers-strategy: framework` is set, so the proxy must send `X-Forwarded-For`,
`X-Forwarded-Proto` and `X-Forwarded-Host`, and the API port must not be reachable except through it.

## Disabled in prod

Swagger UI, `/v3/api-docs` and springdoc's actuator documentation (`springdoc.show-actuator`, `true` in
the base `application.yml`) are all off. Only the actuator `health` endpoint is exposed, without details.
`src/test/.../ProdProfileConfigTest` pins these settings.

## Passwords

User passwords (register and profile update) must be at least 8 characters, matching the web form.
