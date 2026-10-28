# Production configuration

Run the API with `SPRING_PROFILES_ACTIVE=prod`. Everything below is read from environment variables;
the prod profile has no fallbacks for the required ones, so a missing value stops startup.

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `JWT_SECRET_KEY` | yes | none | HMAC secret for access tokens. At least 32 bytes (UTF-8), generated with `openssl rand -base64 48`; startup is refused otherwise (see below). |
| `SPRING_DATASOURCE_URL` | yes | none | e.g. `jdbc:postgresql://db:5432/meetstudent` |
| `SPRING_DATASOURCE_USERNAME` | yes | none | Database user. |
| `SPRING_DATASOURCE_PASSWORD` | yes | none | Database password. |
| `CORS_ALLOWED_ORIGINS` | yes | none | Comma-separated front origins, e.g. `https://app.example.com`. |
| `ADMIN_EMAIL` | yes | none | Email of the platform admin account. |
| `ADMIN_PASSWORD` | yes | none | Initial admin password, at least 12 characters; does not rotate an existing password. |
| `JWT_ACCESS_TTL_MINUTES` | no | `60` | Access token lifetime in minutes (must be >= 1). The web client refreshes on 401. |
| `FILE_UPLOAD_DIR` | no | `uploads` | Public media (served at `/uploads/public/**`). Mount a persistent volume. |
| `FILE_PRIVATE_DIR` | no | `storage/private` | Private documents (never served statically). Mount a persistent volume. |
| `MAIL_ENABLED` | no | `false` | Send account and contact emails through SMTP. When `false` nothing is sent, nothing mail-related is validated and the API boots normally. |
| `MAIL_PROVIDER` | no | `custom` | `brevo`, `gmail`, `ses` or `custom` (case-insensitive; blank = `custom`). A preset fills in **defaults** for `SPRING_MAIL_HOST`, `_PORT` (587), `..._SMTP_AUTH` (`true`), `..._STARTTLS_ENABLE` and `..._STARTTLS_REQUIRED` (`true`): any explicit non-blank `SPRING_MAIL_*` value wins. Only applied when `MAIL_ENABLED=true`. An unknown value stops startup naming `MAIL_PROVIDER` and the allowed values. |
| `MAIL_SES_REGION` | no | `eu-west-1` | AWS region of the SES SMTP endpoint, used by `MAIL_PROVIDER=ses` only (host `email-smtp.<region>.amazonaws.com`). Must look like `eu-west-1`, otherwise startup fails naming this variable. |
| `MAIL_FROM` | if mail on | none | Sender address. Boot fails if `MAIL_ENABLED=true` and it is blank. |
| `CONTACT_RECIPIENT` | no | `contact@meetstudent.com` | Mailbox that receives the messages of the public contact form (`POST /api/v1/contact`). Distinct from the address shown on the site (`contactEmail` in the site settings). Needs `MAIL_ENABLED=true` to be mailed; messages are stored either way. |
| `FRONTEND_BASE_URL` | if mail on | `http://localhost:4200` (none under `prod`) | Public origin of the web app, used for the links in emails: absolute, no path, query or fragment, and `https` under `prod`. Never taken from the request. |
| `SPRING_MAIL_HOST` | if mail on, unless a preset gives it | none | SMTP host. Boot fails naming this variable if `MAIL_ENABLED=true` and it is still missing or empty after the preset. |
| `SPRING_MAIL_PORT` | no | `25` | SMTP port (usually 587 with STARTTLS). |
| `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | no | none | SMTP credentials. Secrets: keep them in `.env` only. |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` | no | none | Standard JavaMail switches (`true`/`false`). |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED` | no | none (compose: the `STARTTLS_ENABLE` value) | Set `true` with STARTTLS: otherwise STARTTLS is opportunistic and an attacker can strip it, sending credentials in clear. |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE` | no | none | `true` for implicit TLS (port 465, `MAIL_PROVIDER=custom`); leave the STARTTLS variables unset then. Passed through `compose.yml` and `compose.prod.yml`. |
| `APP_CONTACT_MAIL_RETRY_MAX_ATTEMPTS` | no | `3` | Contact mail: total attempts on a transient failure, clamped to 1..5. |
| `APP_CONTACT_MAIL_RETRY_INITIAL_DELAY` | no | `2s` | Contact mail: wait before the 2nd attempt (a Spring duration: `2s`, `500ms`). Never below 0, each wait is capped at 60 s. |
| `APP_CONTACT_MAIL_RETRY_BACKOFF_MULTIPLIER` | no | `5` | Contact mail: each further wait is the previous one times this (2 s, then 10 s with the defaults). |
| `APP_CONTACT_MAIL_PENDING_TIMEOUT` | no | `15m` | Contact mail: a message `PENDING` for longer than this (a Spring duration) can be resent again; before that `POST /api/v1/admin/messages/{id}/resend` answers 409. Blank, zero or negative = `15m`. |
| `APP_MAIL_TEST_COOLDOWN_SECONDS` | no | `10` | Minimum seconds between two `POST /api/v1/admin/mail/test` of the same admin (`0` = no limit). Property `app.mail.test-cooldown-seconds`. Passed through `compose.yml` and `compose.prod.yml`. |

`CONTACT_RECIPIENT`, `MAIL_PROVIDER`, `MAIL_SES_REGION`, the `SPRING_MAIL_*` variables and the retry variables are all
passed to the API by `compose.prod.yml` (and, with mail off by default, by `compose.yml`). The step-by-step provider
setup, test checklist and troubleshooting table are in `docs/DEPLOY.md` (section 2b).

## JWT secret check

Under `prod`, `JwtSecretStartupCheck` aborts startup when `JWT_SECRET_KEY` is blank, shorter than 32 bytes (UTF-8),
or equal (case-insensitive, trimmed) to a value published in the repository: the placeholders of the `.env` examples,
the `dev` default and the `test` profile secret. Generate a real one with `openssl rand -base64 48` (64 characters).
`dev`, `docker` and `test` are not checked. Changing the secret signs everyone out.

## Backups (Coolify stack)

The API does not read these: the `backup` service of `compose.coolify.yml` does, when the nightly Coolify
Scheduled Task runs `backup.sh` in it (ADR 0004). rclone is configured by environment only: a remote `r2`
(S3, provider Cloudflare) wrapped by a `crypt` remote `r2crypt`, so names and contents are encrypted before
they leave the VPS. The database settings (`POSTGRES_*`) are shared with the stack. Setup, restore and the
R2 bucket rules (EU jurisdiction, 30-day lifecycle): `docs/DEPLOY.md` section 9.4 and 9.5.

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `R2_ENDPOINT` | for backups | none | `https://<ACCOUNT_ID>.eu.r2.cloudflarestorage.com` (EU jurisdiction endpoint). The scripts refuse an endpoint without `.eu.` (exit `2`). |
| `R2_BUCKET` | for backups | none | Bucket name; the token is scoped to it, so rclone never checks or creates it. |
| `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` | for backups | none | Keys of the bucket-scoped R2 API token (read + write). Secrets. |
| `BACKUP_CRYPT_PASSWORD` | for backups | none | `crypt` password, the original in clear (Coolify secret + password manager); the scripts obscure it for rclone. Without it no backup can be read. |
| `BACKUP_CRYPT_SALT` | for backups | none | `crypt` salt (rclone's `password2`), same handling as the password. |

None of them is needed to render or start the stack. `backup.sh` and `restore.sh` refuse to run without all of
them and exit `2`, naming the missing ones, so a forgotten variable shows up as a failed scheduled task. The
check lives in `infra/backup/backup-env.sh`, sourced by both scripts.

## Email

Account emails are **off by default** (the email verification and password reset endpoints use this layer, and the web pages for them are live: `/:lang/verify-email`, `/:lang/forgot-password`, `/:lang/reset-password`):
`MAIL_ENABLED=false`, no SMTP setting is needed to start, and the endpoints that would send mail still
answer. With `MAIL_ENABLED=true` the API validates the mail configuration at boot and refuses to start on
a missing `SPRING_MAIL_HOST` or `MAIL_FROM`, or on a `FRONTEND_BASE_URL` that is not an `https` origin.

- Connection, read and write timeouts are 5 s, and emails go out on a small bounded background pool after
  the request's transaction commits, so a slow SMTP server cannot slow down or fail an HTTP response.
  If the queue is full the email is dropped and a warning is logged. The contact-form mail has its **own**
  pool (`contactMailExecutor`: 2 threads, queue of 50), so a flood of contact messages can only fill that queue
  and never delays a verification or reset mail.
- **Providers**: `MAIL_PROVIDER=brevo|gmail|ses` supplies the SMTP connection defaults (host, 587, auth,
  mandatory STARTTLS) through an `EnvironmentPostProcessor` (`MailProviderEnvironmentPostProcessor`) that runs after
  the configuration files are loaded and only when `MAIL_ENABLED=true`; it adds a property source holding only the
  `spring.mail.*` keys that are absent or blank, so an explicit `SPRING_MAIL_*` value always wins and credentials
  are never preset. `custom` (default) changes nothing. `MailConfig` then keeps its fail-fast checks (host, from,
  `FRONTEND_BASE_URL`) and adds `MAIL_PROVIDER` / `MAIL_SES_REGION`, never echoing a value. Brevo: password is the
  **SMTP key**, sender must be validated. Gmail: 2-step verification + app password, low sending limits. SES: SMTP
  credentials (not IAM keys), verified identity and recipients (sandbox), same region everywhere.
- **Delivery outcome**: `EmailSender.send` returns a `SendOutcome` (`SENT`, `DISABLED`, `LOGGED` (dev-log only: written to the log, not delivered), `AUTH_FAILED`,
  `CONNECTION_FAILED`, `TLS_FAILED`, `REJECTED`, `FAILED`) and still never throws. `SmtpEmailSender` classifies by
  exception type and SMTP code over the whole cause chain (never by reading a message into a log or an answer); the
  outcome and the log line (`Email not sent type=... outcome=... cause=<class>`) carry no address, host, credential
  or token. Account mails are fire-and-forget as before (the outcome is only logged); contact mails persist it
  (below) and `POST /api/v1/admin/mail/test` returns it.
- **SMTP self-test**: `POST /api/v1/admin/mail/test` (ADMIN, no body) sends a short French + English mail
  synchronously, through the real sender, to the authenticated admin's **own** address only (read from the database,
  never from the request). `200 {"status":"SENT"}`; `502 {"status":"AUTH_FAILED|CONNECTION_FAILED|TLS_FAILED|REJECTED|FAILED"}`;
  `200 {"status":"LOGGED"}` when only the dev-log sender is active (nothing is delivered);
  `503 {"status":"DISABLED"}` while mail is disabled (nothing is sent); `429` with `Retry-After` and the standard error
  body within `app.mail.test-cooldown-seconds` (10) of the previous test of the same admin (a failed test counts too,
  and the cooldown is per admin, in memory); `401` anonymous, `403` non-admin, `404` if the admin account no longer
  exists. It takes a few seconds at most (the SMTP timeouts apply). In the `dev`/`docker` profile with `MAIL_ENABLED=false` the
  dev-log sender is active and answers `LOGGED` (never `SENT`): the mail is only written to the local log, and a contact
  message mailed that way ends `SKIPPED`.
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

## Contact form

`POST /api/v1/contact` (public; `{"email","subject","message","language"?,"website"?}`) stores the message in
`contact_messages` (`V25`) and, once the transaction has committed, mails it on the same bounded background pool
to `CONTACT_RECIPIENT` (subject `[Contact] <subject>`, `Reply-To` = the sender, line breaks stripped from every
header value). It answers `202 {"status":"accepted"}`, or `400 {field: message}` (email 254, subject 150, message
5000 characters). With `MAIL_ENABLED=false` the message is still stored and the answer is still `202`.

- **Delivery state** (`V27`): the row is created `PENDING` (`mail_status varchar(16)`, `mail_attempts`, `mail_sent_at`;
  rows from before `V27` are `UNKNOWN`). `ContactEmailListener` runs on the dedicated `contactMailExecutor`, sends,
  retries **transient** outcomes (`CONNECTION_FAILED`, `TLS_FAILED`, `FAILED`) up to `app.contact.mail.retry.*`
  (3 attempts, 2 s then 10 s; the wait holds a pool thread, hence the bounds: at most 5 attempts, 60 s per wait),
  never `AUTH_FAILED`, `REJECTED` or `DISABLED`, then saves in its own `REQUIRES_NEW` transaction `SENT` (+ `mail_sent_at`),
  `FAILED` or `SKIPPED` (mail disabled or only logged by the dev-log sender, 0 attempts) and the attempts of this run. A failure of the listener or of that
  save never reaches the `202`. If the contact queue is full the mail is dropped with a warning and the row stays
  `PENDING`; so does a row whose delivery was cut by a restart. The honeypot stores and sends nothing.
- **Back office**: `GET /api/v1/admin/messages` adds `mailStatus` (`UNKNOWN|PENDING|SENT|FAILED|SKIPPED`),
  `mailAttempts` and `mailSentAt` to each item. `POST /api/v1/admin/messages/{id}/resend` (ADMIN) sets the message back to
  `PENDING` (attempts 0, no sent time) and queues the same mail: `202 {"status":"accepted"}`, `404` unknown id,
  `503` while mail is disabled, `409` while a delivery of this message is queued or running, `401` anonymous, `403` non-admin
  (checks in this order: 404, 503, 409). Poll the list to see the result. The claim is one conditional `UPDATE`
  (`claimResend`), so concurrent clicks cannot queue duplicates or fill the 50-slot queue; a row `PENDING` for longer
  than `app.contact.mail.pending-timeout` (`APP_CONTACT_MAIL_PENDING_TIMEOUT`, 15 min) counts as lost and may be resent
  (`V28` `mail_pending_since`, falling back to `created_at`). Known limit: a very late old attempt of such a lost row
  can still overwrite the newer result.

- **Honeypot**: a non-blank `website` answers the same `202` and stores and sends nothing.
- **Per-IP limit**: 5 messages per hour per client address (honeypot hits count), in memory, so per API instance and
  reset by a restart; the 6th answers `429` with `Retry-After`. Tunable with `app.contact.rate-limit.*`.
  Traefik's own per-IP limit still applies in front of it.
- **Client address (limiter key)**: always a short literal IP, or the fixed key `unknown`. Under `prod` the API runs with
  `server.forward-headers-strategy: framework`: Spring's `ForwardedHeaderFilter` then hides `X-Forwarded-For` from the
  application and writes its first hop into `getRemoteAddr()`, so the resolver (`ClientIpResolver`) ends up trusting the
  **first `X-Forwarded-For` hop, read through `getRemoteAddr()`** (its own `X-Forwarded-For` read, enabled by
  `app.contact.trust-forwarded-for: true`, only matters without the framework strategy). Both values pass the same
  literal-IP check; anything else (a client-chosen string, a bracketed or over-long value) collapses to `unknown`, so
  junk can neither pick a key nor fill the limiter's memory. `::ffff:a.b.c.d` counts as the IPv4 address and every other
  IPv6 address is counted per /64 prefix. Anywhere else the socket address is used. This is safe only because Traefik is
  the sole entry point and replaces `X-Forwarded-For` with the real peer address. If the API is ever exposed without such
  a proxy, set `APP_CONTACT_TRUST_FORWARDED_FOR=false`, or a client could forge the header to dodge the limit.
- **Why `Forwarded` is stripped**: the RFC 7239 `Forwarded` header takes precedence over `X-Forwarded-For` in Spring's
  filter and its `for=` value lands unvalidated in `getRemoteAddr()`. Traefik v3 does not remove a client-sent
  `Forwarded`, so `curl -H 'Forwarded: for=1.2.3.4'` would pick the limiter key at will. Two locks: the `strip-forwarded`
  Traefik middleware (`customrequestheaders.Forwarded=` with an empty value) on every router that reaches the API in
  `compose.prod.yml`, and `ForwardedHeaderConfig` in the API, which hides `Forwarded` from Spring's filter when the
  framework strategy is on. Keep both if you touch either.
- **Validation**: subject and message are stripped (Unicode spaces included) before the blank check and refuse control
  characters other than tab and line breaks (`400 {field: message}`). `Reply-To` is set only for a plain ASCII mailbox: a
  non-ASCII sender (`été@example.com`) stays in the body and no `Reply-To` is sent, since SMTP servers without SMTPUTF8
  would refuse the whole mail.
- The limit and the honeypot are decided before the database transaction opens: a 429 or a honeypot hit takes no
  connection.
- Logs carry the message id, the outcome, the attempt count and the exception class only: never the sender address or the text.

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
account are burned, pending email-change links are cancelled, all refresh tokens are deleted and the
address is marked verified. The persisted password version is incremented atomically: previously issued
access tokens are rejected on their next request, including the session that performed the reset.
The same revocation applies to profile password changes, which require the current password for
self-service. Email changes use their separate confirmation workflow.

Residual risks (accepted): anyone can exhaust an address's daily reset budget, after which the owner
relies on links already sent (1 h) or an administrator-assisted password change; an unknown address
answers a few milliseconds faster than a known one (mitigated by the Traefik per-IP rate limit).
With `MAIL_ENABLED=false` the request endpoint still answers `202` but nothing is sent and no token
is created. Recovery is available to students, experts and administrators alike.

## Profile must be set explicitly

Without `SPRING_PROFILES_ACTIVE` the API falls back to the `dev` profile, whose `app.mail.dev-log: true`
prints emails, one-time links included, to the log. Production must always set `SPRING_PROFILES_ACTIVE=prod`
(`compose.prod.yml` does); the prod profile refuses to start with dev-log on.

## Admin bootstrap

`V2__data.sql` seeds `admin@meetstudent.com` with the password `password`, and applied migrations
cannot be edited. Under the `prod` profile `AdminBootstrapRunner` runs on every boot and:

1. refuses to start unless `ADMIN_EMAIL` is set and `ADMIN_PASSWORD` is at least 12 characters;
2. preserves an existing `ROLE_ADMIN` account at `ADMIN_EMAIL`, including its current password;
   refuses startup if that address belongs to a non-admin account;
3. if no account has that address, reuses only the untouched seeded admin (default address,
   `ROLE_ADMIN` and exact published seed hash), or creates a new admin, using the configured password;
4. replaces the published seed hash on every remaining account with a random unusable password.
   Every seed credential replacement also invalidates existing access/refresh sessions and pending
   reset, verification and email-change links in the same transaction.

`ADMIN_PASSWORD` is now **initialization only**. Changing it and restarting no longer rotates an
existing admin password: passwords changed through the account profile or email reset survive
restarts. Use the authenticated password-change workflow or the emailed password-reset workflow
for rotation; both revoke existing sessions. Keep the bootstrap variables configured for startup
validation. Changing `ADMIN_EMAIL` to an unused address creates a new admin; it does not rename an
existing admin whose seed password was already replaced. Remove or demote the previous admin
explicitly when appropriate. An address belonging to a student or expert is never promoted.

In `dev`/`docker`, neither variable remains valid for local seeded development; setting both enables
the same safe initialization and seed neutralization, while setting only one aborts startup.

## Behind a TLS proxy

`server.forward-headers-strategy: framework` is set, so the proxy must send `X-Forwarded-For`,
`X-Forwarded-Proto` and `X-Forwarded-Host`, and the API port must not be reachable except through it.

## Disabled in prod

Swagger UI, `/v3/api-docs` and springdoc's actuator documentation (`springdoc.show-actuator`, `true` in
the base `application.yml`) are all off. Only the actuator `health` endpoint is exposed, without details.
`src/test/.../ProdProfileConfigTest` pins these settings.

## Passwords

User passwords (register and profile update) must be at least 8 characters, matching the web form.
