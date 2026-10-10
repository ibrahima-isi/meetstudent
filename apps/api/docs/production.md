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
| `MAIL_ENABLED` | no | `false` | Send account emails through SMTP. When `false` nothing is sent and the API boots normally. |
| `MAIL_FROM` | if mail on | none | Sender address. Boot fails if `MAIL_ENABLED=true` and it is blank. |
| `FRONTEND_BASE_URL` | if mail on | `http://localhost:4200` (none under `prod`) | Public origin of the web app, used for the links in emails: absolute, no path, query or fragment, and `https` under `prod`. Never taken from the request. |
| `SPRING_MAIL_HOST` | if mail on | none | SMTP host. Boot fails naming this variable if `MAIL_ENABLED=true` and it is missing or empty. |
| `SPRING_MAIL_PORT` | no | `25` | SMTP port (usually 587 with STARTTLS). |
| `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | no | none | SMTP credentials. Secrets: keep them in `.env` only. |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` | no | none | Standard JavaMail switches (`true`/`false`). |

## Email

Account emails are **off by default** (this is the sending layer only; the email verification and password reset endpoints that use it arrive in later changes, so the launch limitations in `docs/DEPLOY.md` still stand):
`MAIL_ENABLED=false`, no SMTP setting is needed to start, and the endpoints that would send mail still
answer. With `MAIL_ENABLED=true` the API validates the mail configuration at boot and refuses to start on
a missing `SPRING_MAIL_HOST` or `MAIL_FROM`, or on a `FRONTEND_BASE_URL` that is not an `https` origin.

- Connection, read and write timeouts are 5 s, and emails go out on a small bounded background pool after
  the request's transaction commits, so a slow SMTP server cannot slow down or fail an HTTP response.
  If the queue is full the email is dropped and a warning is logged.
- `/actuator/health` does **not** probe SMTP (`management.health.mail.enabled: false`): an SMTP outage
  never marks the API down.
- Logs carry the email type, the user id and the exception class only: never the link, the token, the
  address or the exception message (these can contain credentials).
- Links look like `<FRONTEND_BASE_URL>/<lang>/verify-email#token=...` and `.../reset-password#token=...`;
  the token is in the URL fragment so it does not reach server logs or Referer headers.
- `GET /api/v1/auth/capabilities` answers `{"emailEnabled": true|false}` (public) so the web app can avoid
  claiming an email was sent while mail is off.
- `app.mail.dev-log` (set in the `dev` and `docker` profiles) prints emails, links included, to the log.
  The `prod` profile refuses to start with it.

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
