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
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED` | no | none (compose: the `STARTTLS_ENABLE` value) | Set `true` with STARTTLS: otherwise STARTTLS is opportunistic and an attacker can strip it, sending credentials in clear. |

## Email

Account emails are **off by default** (the email verification and password reset endpoints use this layer, and the web pages for them are live: `/:lang/verify-email`, `/:lang/forgot-password`, `/:lang/reset-password`):
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

## Password reset

`POST /api/v1/auth/password-reset/request` (`{"email","lang"?}`) always answers `202 {"status":"accepted"}` for a
well-formed address, so it reveals nothing about accounts, limits or whether mail is enabled; a link valid for
`app.tokens.reset-ttl-minutes` (60) is mailed when the account exists, mail is on and the limits allow it
(60 s cooldown per account, and at most 3 per day per address and purpose across accounts, which binds before the
per-account daily cap of 5; a refused request is silent; issuance is serialised per address, by a PostgreSQL advisory
lock, so parallel requests cannot exceed the limits). A new request does not cancel reset links already sent: an
attacker spending the budget cannot kill the owner's live link, and a successful reset burns the others.
Passwords must be 8 to 72 UTF-8 bytes (BCrypt limit) on registration, profile update and reset. `POST /api/v1/auth/password-reset/confirm` (`{"token","password","confirmedPassword"}`) answers `204`,
or `400` with `{"password"}`, `{"confirmedPassword"}` or `{"token":"Lien invalide ou expiré"}`; the password and
confirmation are checked before the token is consumed. On success the token and every other open reset token of the
account are burned, all refresh tokens of the account are deleted and the address is marked verified.
**Access tokens already issued remain valid until they expire** (`JWT_ACCESS_TTL_MINUTES`, default 60 in the API,
set to 15 by `compose.prod.yml`): there is no access-token revocation list. Residual risks (accepted): anyone can exhaust an
address's daily reset budget, after which the owner relies on links already sent (1 h) or an admin manual reset; an
unknown address answers a few milliseconds faster than a known one (mitigated by the Traefik per-IP rate limit); a
reset does not evict an attacker already holding an access token, and `PATCH /users/{id}` changes password or email
without the current password (pre-existing). Follow-ups: `password_changed_at`/token-version check in
`JwtAuthenticationFilter`, or require the current password for those changes. With `MAIL_ENABLED=false` the request
endpoint still answers `202` but nothing is sent and no token is created.

## Profile must be set explicitly

Without `SPRING_PROFILES_ACTIVE` the API falls back to the `dev` profile, whose `app.mail.dev-log: true`
prints emails, one-time links included, to the log. Production must always set `SPRING_PROFILES_ACTIVE=prod`
(`compose.prod.yml` does); the prod profile refuses to start with dev-log on.

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
