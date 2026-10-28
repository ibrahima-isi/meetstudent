# Production deployment (single VPS, Docker Compose, HTTPS)

Files: `compose.prod.yml`, `.env.production.example`, `apps/backoffice/Dockerfile`,
`scripts/backup.sh`, `scripts/restore.sh`; for Coolify, `compose.coolify.yml` and `infra/`.

Two stacks, one per server, never both: sections 1 to 8 deploy with `compose.prod.yml` and its own
Traefik. The production on the Hetzner VPS runs `compose.coolify.yml` under Coolify instead, behind
Coolify's Traefik, with images built by the CI and a `backup` service: see
[section 9](#9-coolify-production-on-the-hetzner-vps).

## Architecture

```
Internet --80/443--> Traefik v3 (Let's Encrypt, redirect, headers, rate limits)
   https://DOMAIN/api/*, /uploads/public/*  --> api         (Spring Boot, profile prod)
   https://DOMAIN/*                          --> web         (Angular SSR, node)
   https://BO_DOMAIN/*                       --> backoffice  (nginx, static SPA)
                                                 api --> db  (Postgres 18, private network)
```

Only Traefik publishes ports. Postgres sits on an `internal` Docker network with
no route to the outside. The Traefik dashboard is disabled.

The Coolify stack has the same routes and middlewares, but the Traefik is Coolify's (shared middlewares
in `infra/coolify/proxy/dynamic/meetstudent.yaml`), the images come from GHCR, and a `backup` service
sends encrypted nightly backups to Cloudflare R2 (section 9).

## 1. VPS prerequisites

- Ubuntu 22.04/24.04 LTS, at least 2 vCPU / 2 GB RAM (the JVM plus the Angular
  and Maven builds are tight on 1 GB; add 2 GB swap if you must).
- Two DNS **A records** pointing at the VPS public IP (add AAAA only if IPv6 is
  really configured): `DOMAIN` (e.g. `example.com`) and `BO_DOMAIN`
  (e.g. `admin.example.com`). Wait until they resolve; Let's Encrypt needs them.
- Install Docker Engine and the compose plugin (official instructions:
  https://docs.docker.com/engine/install/ubuntu/), then check
  `docker compose version`.
- Firewall: allow only SSH, HTTP and HTTPS.

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

Docker publishes ports by editing iptables directly and bypasses ufw rules.
That is acceptable here because the compose file publishes only 80 and 443.
Never add a `ports:` entry to `db` or `api`.

## 2. Clone and configure

```bash
git clone https://github.com/ibrahima-isi/meetstudent.git
cd meetstudent
git checkout main            # deploy only what was promoted to main
cp .env.production.example .env
chmod 600 .env
nano .env
```

Fill in every variable (the file documents them):

| Variable | Meaning |
|---|---|
| `DOMAIN` | public site, no scheme |
| `BO_DOMAIN` | backoffice host |
| `ACME_EMAIL` | Let's Encrypt contact |
| `CORS_ALLOWED_ORIGINS` | `https://${DOMAIN},https://${BO_DOMAIN}` |
| `JWT_SECRET_KEY` | `openssl rand -base64 48` |
| `POSTGRES_PASSWORD` | `openssl rand -base64 32 \| tr -d '/+=\n'` |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | first admin account, password at least 12 chars |
| `JWT_ACCESS_TTL_MINUTES` | access token lifetime, default 15 |
| `BO_API_URL` | optional, defaults to `https://${DOMAIN}/api/v1` |
| `POSTGRES_DB`, `POSTGRES_USER` | optional, default `meetstudent` |
| `ACME_CA_SERVER` | optional, Let's Encrypt staging while testing |
| `MAIL_ENABLED` | optional, `true` to send mail; default `false` (nothing is sent) |
| `MAIL_PROVIDER` | optional, `brevo` \| `gmail` \| `ses` \| `custom` (default). A preset only supplies defaults for the SMTP connection; any `SPRING_MAIL_*` below still wins |
| `MAIL_SES_REGION` | optional, AWS region of the SES SMTP endpoint (`MAIL_PROVIDER=ses`), default `eu-west-1` |
| `MAIL_FROM` | sender address, required when `MAIL_ENABLED=true` |
| `FRONTEND_BASE_URL` | optional, origin used in email links; default `https://${DOMAIN}` (must be https) |
| `CONTACT_RECIPIENT` | optional, mailbox that receives the contact form; default `contact@meetstudent.com` |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | SMTP server; the host is required when `MAIL_ENABLED=true` unless a preset provides it |
| `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | SMTP credentials (secret): a Brevo SMTP key, a Gmail app password or SES SMTP credentials |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` | `true`/`false`, typical for port 587 (set by the presets) |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED` | recommended `true` with STARTTLS (otherwise it is opportunistic and can be downgraded); defaults to the `STARTTLS_ENABLE` value (set by the presets) |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE` | `true` for implicit TLS (port 465); leave the STARTTLS variables unset then |
| `APP_CONTACT_MAIL_RETRY_MAX_ATTEMPTS`, `APP_CONTACT_MAIL_RETRY_INITIAL_DELAY`, `APP_CONTACT_MAIL_RETRY_BACKOFF_MULTIPLIER` | optional, retries of the contact mail on a transient failure: defaults `3`, `2s`, `5` (waits of 2 s then 10 s) |
| `BACKUP_DIR`, `BACKUP_KEEP_DAYS` | optional, used by `scripts/backup.sh` |

Do not use `$` in secrets: compose would try to interpolate it.
`POSTGRES_PASSWORD` is applied only when the database volume is first created.

Email is optional and off by default: leave the `MAIL_*` / `SPRING_MAIL_*` variables unset and the API
starts and runs without sending anything. If `MAIL_ENABLED=true` with a missing SMTP host, sender address, an
unknown `MAIL_PROVIDER` or a non-https `FRONTEND_BASE_URL`, the API refuses to start and names the variable.
The health check does not contact the SMTP server. Details: `apps/api/docs/production.md`. The provider setup,
the test checklist and the troubleshooting table are in section 2b below.
Two account flows use the mail layer, both single-use links valid for a limited time:

- **Email verification** is optional: unverified accounts sign in normally. A link (24 h) is mailed at
  registration and when the address changes; `POST /api/v1/auth/email-verification/resend` sends a new one.
- **Password reset**: `POST /api/v1/auth/password-reset/request` mails a link (1 h) and
  `POST /api/v1/auth/password-reset/confirm` sets the new password. The request always answers `202` for a
  well-formed address, whether or not an account exists, a limit applies or mail is disabled. Limits: one mail
  per 60 s per account and at most 3 per day per address and purpose (across accounts; this binds before the
  per-account daily cap of 5). A refused request stays silent, and issuance is serialised per address so parallel
  requests cannot exceed them. Asking again does not cancel links already sent (they all go to the owner's
  mailbox); using one burns the others. Passwords are 8 to 72 bytes (UTF-8). Using the link ends every
  access/refresh session of the account through the persisted credential version and deletes all refresh
  tokens. Outstanding reset and email-change links are invalidated in the same transaction. Only the
  new password permits a new login, for students, experts and administrators alike.
- Residual risks, accepted for launch: anyone can exhaust an address's 3-per-day reset budget, after which that
  owner relies on links already sent (valid 1 h) or an administrator-assisted password change through
  the authenticated API; an unknown address answers a few milliseconds faster than a known one
  (Traefik's per-IP rate limit bounds how often that can be probed). Self-service profile password
  changes require the current password and revoke sessions; email changes retain their separate
  current-password and email-confirmation workflow.
- With `MAIL_ENABLED=false` forgot-password still answers `202` but nothing is sent and no token is created:
  recovery is impossible until SMTP is configured.

Never run the API without `SPRING_PROFILES_ACTIVE=prod` (compose sets it): the default `dev` profile prints
emails and their one-time links to the log.

Validate before starting (prints nothing on success, names the missing variable otherwise):

```bash
docker compose -f compose.prod.yml config -q
```

## 2b. Email: providers, test checklist, troubleshooting

### Main path: three built-in providers

`MAIL_PROVIDER=brevo|gmail|ses` fills in the SMTP connection for you: host, port `587`, authentication and
**mandatory** STARTTLS (no silent downgrade to plain text). It only supplies defaults: any `SPRING_MAIL_*`
variable you set explicitly wins, and an empty one counts as unset. `MAIL_PROVIDER=custom` (the default, also
when the variable is absent) applies nothing: only your `SPRING_MAIL_*` variables count. Nothing is checked or
applied while `MAIL_ENABLED=false`. Credentials are never part of a preset.

Brevo (the recommended provider), six variables in `.env`:

```bash
MAIL_ENABLED=true
MAIL_PROVIDER=brevo
MAIL_FROM=no-reply@example.com
SPRING_MAIL_USERNAME=your-brevo-smtp-login
SPRING_MAIL_PASSWORD=your-brevo-smtp-key
CONTACT_RECIPIENT=contact@example.com
```

To switch provider, change exactly these lines and restart (`docker compose -f compose.prod.yml up -d`):

| | `MAIL_PROVIDER` | `SPRING_MAIL_USERNAME` | `SPRING_MAIL_PASSWORD` | `MAIL_FROM` | Also |
|---|---|---|---|---|---|
| Brevo | `brevo` (host `smtp-relay.brevo.com`) | Brevo SMTP login | Brevo **SMTP key** (not the API key) | a sender validated in the Brevo dashboard | |
| Gmail | `gmail` (host `smtp.gmail.com`) | your Gmail address | a Google **app password** (16 letters, no spaces) | your Gmail address | needs 2-step verification; for a demo only |
| AWS SES | `ses` (host `email-smtp.<region>.amazonaws.com`) | SES **SMTP username** | SES **SMTP password** | an identity verified in that region | `MAIL_SES_REGION=eu-west-1` (default) |

- Gmail: your normal password does not work; the From address is always rewritten to the authenticated account;
  personal accounts have a low daily sending limit. Use it to test, not for production.
- SES: the SMTP credentials are generated in the SES console (SMTP settings): they are **not** your IAM access
  keys. The region of the credentials, of the verified identity and of `MAIL_SES_REGION` must be the same. In the
  SES sandbox only verified recipient addresses receive mail until you request production access.
- Brevo: validate the sender (and ideally authenticate the domain) in the Brevo dashboard first; the SMTP key
  is under SMTP & API > SMTP.

Other setups (these need `MAIL_PROVIDER=custom`, i.e. leave it unset):

```bash
# Generic provider on port 465 (implicit TLS)
MAIL_ENABLED=true
MAIL_FROM=no-reply@example.com
SPRING_MAIL_HOST=smtp.your-provider.com
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=...
SPRING_MAIL_PASSWORD=...
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
```

Implicit TLS is supported through `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true` (the variable is passed through
`compose.prod.yml`; keep the STARTTLS variables unset). It was exercised against a local SMTP server with TLS on
connect and login, not against a real provider. A server with a self-signed or expired certificate is refused
(`TLS_FAILED`); there is deliberately no switch to trust any certificate.

Catch-all test inbox (Mailpit), for the local/demo stack `compose.yml` (not `compose.prod.yml`): start it with
`docker compose --profile mail up --build` and set, in `.env`:

```bash
MAIL_ENABLED=true
SPRING_MAIL_HOST=mailpit
SPRING_MAIL_PORT=1025
MAIL_FROM=no-reply@meetstudent.local
FRONTEND_BASE_URL=http://localhost:4200
CONTACT_RECIPIENT=contact@meetstudent.local
```

Read the mails on `http://localhost:8025`. Both Mailpit ports are bound to `127.0.0.1` and Mailpit accepts anything
without a login: never publish them. `FRONTEND_BASE_URL` may be plain http here: https is only enforced under the
`prod` profile (`compose.prod.yml`).

### How to test on the demo server

1. Start the stack, then check the API log: `Email sending is enabled (provider preset: brevo)`. A configuration
   error stops the API with a message naming the variable (`MAIL_PROVIDER`, `MAIL_FROM`, `SPRING_MAIL_HOST`...).
2. **SMTP self-test.** Sign in as the admin, then:

   ```bash
   TOKEN=$(curl -s -X POST https://$DOMAIN/api/v1/auth -H 'Content-Type: application/json' \
     -d '{"username":"'$ADMIN_EMAIL'","password":"'$ADMIN_PASSWORD'"}' | jq -r .accessToken)
   curl -s -i -X POST https://$DOMAIN/api/v1/admin/mail/test -H "Authorization: Bearer $TOKEN"
   ```

   `200 {"status":"SENT"}` and a short French + English mail in the **admin's own** mailbox. Otherwise `502`
   with the outcome (table below), `503 {"status":"DISABLED"}` while `MAIL_ENABLED` is not `true`, `429` with
   `Retry-After` if you call it twice within `APP_MAIL_TEST_COOLDOWN_SECONDS` (10 s by default). `200 {"status":"LOGGED"}`
   means the dev-log sender is active (local `docker` profile with `MAIL_ENABLED=false`): the mail was only written to
   the API log and **nothing was delivered**; it never happens in production, which refuses `dev-log`. The test is
   synchronous and takes a few seconds at most (the SMTP timeouts apply). The answer never contains an address, host
   or credential.
3. **Registration**: create an account with a real mailbox: a verification mail arrives; the link starts with
   `FRONTEND_BASE_URL`.
4. **Forgot password** (`/fr/forgot-password`): the reset mail arrives (the page answers the same whether or not
   the account exists).
5. **Contact form** (`/fr/contact`): the team mailbox (`CONTACT_RECIPIENT`) receives `[Contact] <subject>` with
   `Reply-To` set to the visitor. In the back office list (or `GET /api/v1/admin/messages`) the message shows
   `mailStatus` `SENT`.
6. **Admin resend**: `POST /api/v1/admin/messages/{id}/resend` answers `202`, the message goes back to `PENDING`
   and a second copy arrives; `404` for an unknown id, then `503` while mail is disabled, then `409` while a delivery
   of that message is still queued or running (`PENDING`), so a double click or a loop cannot queue duplicates.

Contact messages carry a delivery state: `PENDING` (queued or being sent), `SENT`, `FAILED` (every attempt failed,
or a permanent failure), `SKIPPED` (mail disabled), `UNKNOWN` (stored before this tracking existed), plus
`mailAttempts` and `mailSentAt`. A transient failure is retried up to 3 times (2 s, then 10 s) inside the
background task; bad credentials and refused addresses are never retried. Messages are stored whatever happens: a
`FAILED` one can be resent from the back office once the configuration is fixed. A `PENDING` one is refused with `409`
until it has been `PENDING` for `APP_CONTACT_MAIL_PENDING_TIMEOUT` (15 minutes by default): a message that stays so
(API restarted during delivery, or the contact mail queue was full and dropped it: the log says
`Contact mail queue is full`) then becomes resendable, so an admin is never stuck. `SKIPPED` also covers a mail that
the dev-log sender only wrote to the log.

### Troubleshooting by outcome

The outcome is what `POST /api/v1/admin/mail/test` answers; the API log carries the same word:
`docker compose -f compose.prod.yml logs api | grep "Email not sent"` prints
`type=... outcome=<OUTCOME> cause=<ExceptionClass>` and never an address, token or message text.

| Outcome | Meaning | Typical causes | What to do |
|---|---|---|---|
| `AUTH_FAILED` | The server refused the login. Not retried. | Wrong `SPRING_MAIL_USERNAME`/`PASSWORD`. **Brevo**: the API key was used instead of the SMTP key. **Gmail**: normal password instead of an app password, or 2-step verification is off. **SES**: IAM access keys instead of SMTP credentials, or credentials of another region. | Regenerate the SMTP credential for that provider; check there is no space or quote in `.env`; recreate the container (`up -d`). |
| `CONNECTION_FAILED` | The server could not be reached within 5 s. Retried. | Wrong `SPRING_MAIL_HOST`/`PORT`, typo in `MAIL_SES_REGION`, DNS failure, the VPS host blocks outbound SMTP ports (25/465/587), firewall. | `nc -vz smtp-relay.brevo.com 587` from the VPS; ask the hosting provider to unblock the port or use another one. |
| `TLS_FAILED` | The TLS handshake or STARTTLS failed. Retried. | Port 465 without `SSL_ENABLE=true`, or `SSL_ENABLE=true` on 587; STARTTLS required but the server offers none; expired or self-signed certificate; wrong system clock. | Match port and mode (587 = STARTTLS, 465 = implicit TLS); fix the certificate or the clock. |
| `REJECTED` | The server refused the sender or the recipient (5xx), or the address is malformed. Not retried. | **Brevo**: `MAIL_FROM` is not a validated sender. **SES**: `MAIL_FROM` not verified in that region, or sandbox mode and the recipient is not verified. Mailbox does not exist; `MAIL_FROM`/`CONTACT_RECIPIENT` malformed. | Validate the sender in the provider console; verify the recipient or leave the SES sandbox; fix the address. |
| `FAILED` | Anything else, including temporary 4xx refusals. Retried. | Provider rate or quota limit (Gmail daily limit, Brevo or SES quota), greylisting, a server-side error. | Read the `cause=` class in the log, check the provider's dashboard and quotas, try again later. |
| `DISABLED` (`503`) | Mail is switched off. | `MAIL_ENABLED` is not `true`. | Set `MAIL_ENABLED=true` and recreate the container. |

Deliverability of `MAIL_FROM` (a mail accepted by the SMTP server can still land in spam):

- Send from a domain you own and authenticate it with the provider: Brevo (Domains), SES (verified identity with
  Easy DKIM), or your own domain. The provider's console shows the exact DNS records to add. Gmail rewrites the
  sender to the Gmail account, so it cannot carry your domain.
- **SPF**: a TXT record on the domain listing the provider's servers (the provider gives the `include:` value), with
  a single SPF record per domain.
- **DKIM**: the provider's DKIM records (CNAME or TXT), so receivers can verify the signature.
- **DMARC**: a TXT record on `_dmarc.<domain>`, for instance `v=DMARC1; p=none; rua=mailto:you@example.com` to
  start by only collecting reports, tightened to `quarantine` once SPF and DKIM pass.
- Check the result with the headers of a received mail (`Authentication-Results`: spf=pass, dkim=pass, dmarc=pass).

## 3. Start

```bash
docker compose -f compose.prod.yml up -d --build
```

The first build takes several minutes (Maven and npm downloads). Tip: do a dry
run against Let's Encrypt **staging** first by setting
`ACME_CA_SERVER=https://acme-staging-v02.api.letsencrypt.org/directory` in
`.env` (browsers will warn about the staging certificate), then remove it,
`docker compose -f compose.prod.yml down`, `docker volume rm meetstudent-prod_traefik-acme`
and start again to get real certificates.

## 4. First-boot checks

```bash
docker compose -f compose.prod.yml ps          # all services "healthy" (api takes ~1 min)
docker compose -f compose.prod.yml logs -f traefik api
curl -sI http://$DOMAIN | head -3              # 301/308 to https
curl -sI https://$DOMAIN | head -5             # 200, strict-transport-security present
curl -s https://$DOMAIN/api/v1/schools | head -c 200
curl -sI https://$BO_DOMAIN | head -3
curl -s https://$BO_DOMAIN/config.json         # {"apiUrl":"https://DOMAIN/api/v1"}
curl -sI https://$DOMAIN/swagger-ui.html       # must NOT be 200 (profile prod disables it)
ss -tlnp | grep -E ':(5432|8080|4200)\b'       # must print nothing
```

The certificate is issued on the first HTTPS request (a few seconds). If
`traefik` logs show ACME errors, check the DNS records and that port 80 is open.

### How the admin account is created

The API creates the administrator at startup from `ADMIN_EMAIL` and
`ADMIN_PASSWORD` (set in `.env`), with `ROLE_ADMIN`. Log in to
`https://BO_DOMAIN` with those credentials. If the account already exists the
startup does not overwrite it, so changing `ADMIN_PASSWORD` in `.env` later does
not change the password. Change it through the authenticated profile or the emailed
password-reset workflow; both revoke all existing access/refresh sessions and pending
email-change links. Reset passwords survive application restarts. An existing student
or expert address is refused, never promoted. Only the untouched seeded admin may be
repurposed, and every published seed credential is neutralized with its sessions and
sensitive links. Check
`docker compose -f compose.prod.yml logs api | grep -i admin` after the first
boot. Keep both bootstrap variables configured: production still validates them
on every startup.

## 5. Smoke test checklist

On `https://DOMAIN`:

- [ ] The landing page loads over HTTPS with a valid certificate; `http://` redirects.
- [ ] Register a new STUDENT account.
- [ ] Log in with it; the profile page shows your data.
- [ ] Upload a document (diploma/certificate PDF or image under 10 MB); it appears in the list.
- [ ] Open the uploaded document again (private media is fetched with the auth header).
- [ ] A file over 10 MB is rejected.
- [ ] Log out; protected pages require login again.
- [ ] Reload a page: school logos/photos (`/uploads/public/...`) display.

On `https://BO_DOMAIN`:

- [ ] Log in as the admin from `.env`.
- [ ] Moderate the document uploaded above (approve/reject) and see the status change on the student side.
- [ ] Browser devtools show no CORS errors on API calls.
- [ ] Log out.

Rate-limit check: more than ~15 login attempts within a minute from one IP
should return `429`.

## 6. Updating and rollback

```bash
cd meetstudent
./scripts/backup.sh                      # always back up first
git fetch origin
git checkout main && git pull --ff-only origin main
docker compose -f compose.prod.yml up -d --build
docker compose -f compose.prod.yml ps
docker image prune -f                    # reclaim disk
```

Rollback of code: `git checkout <previous-tag-or-sha>` and run the same
`up -d --build`. Flyway migrations only go forward; if the release applied a
schema migration, roll back the data too with `scripts/restore.sh` using the
backup taken just before the update.

## 7. Backups

`scripts/backup.sh` writes `BACKUP_DIR/<UTC timestamp>/` containing
`db.sql.gz` (pg_dump), `uploads.tar.gz`, `private.tar.gz` and `SHA256SUMS`, and
deletes folders older than `BACKUP_KEEP_DAYS` (default 14). It runs from cron,
takes a lock, and exits non-zero on failure.

```bash
sudo mkdir -p /var/backups/meetstudent && sudo chown $USER /var/backups/meetstudent
crontab -e
# every night at 03:15, log to a file
15 3 * * * cd /home/<user>/meetstudent && ./scripts/backup.sh >> /var/log/meetstudent-backup.log 2>&1
```

The user running cron must be in the `docker` group. **Backups on the same VPS
do not protect against losing the VPS**: copy `BACKUP_DIR` off-site regularly
(`rsync`, `rclone` to object storage, or provider snapshots).

### Restore (and restore test)

```bash
./scripts/restore.sh /var/backups/meetstudent/20260101T031500Z
```

It verifies the checksums, asks for confirmation (`--yes` to skip), stops
api/web, drops and recreates the database from `db.sql.gz`, empties and refills
both volumes, and starts api/web again.

Rehearse a restore **before launch and after every change of the setup**: on a
scratch machine (or this one outside peak hours), run it, then repeat the smoke
test, in particular opening an uploaded document. A backup that was never
restored is not a backup.

## 8. Routine operations

```bash
docker compose -f compose.prod.yml logs --tail=200 -f api   # logs (rotated: 5 x 10 MB per service)
docker compose -f compose.prod.yml restart api
docker compose -f compose.prod.yml down                     # stop (data volumes are kept)
```

Never run `down -v` in production: it deletes the database and the uploads.
Keep the host patched (`unattended-upgrades`) and the Traefik image current.

## 9. Coolify (production on the Hetzner VPS)

Files: `compose.coolify.yml`, `infra/coolify/proxy/dynamic/meetstudent.yaml`, `infra/backup/`,
`infra/docker-bake.hcl`, the `deploy / production` job of `.github/workflows/ci.yml`.
Decisions: [ADR 0003](adr/0003-coolify-raw-compose-ghcr.md) (Raw compose, images from GHCR) and
[ADR 0004](adr/0004-encrypted-offsite-backups-r2-eu.md) (encrypted off-site backups). The VPS, the
Coolify install, the DNS and the R2 bucket are the owner's phases 1, 2, 4 and 5 of the
[plan](plans/2026-10-26-deploy-hetzner-coolify.md); this section is what the repository expects from them.

```
git push origin dev:main
  -> CI: api, web, backoffice tests green
  -> deploy / production: push ghcr.io/ibrahima-isi/meetstudent-{api,web,backoffice,backup}
                          (sha-<40-char commit> and latest), then call the Coolify deploy webhook
  -> Coolify pulls the images (never builds) and restarts the stack behind its own Traefik
```

Nothing else deploys: not a pull request, not `dev`, not a push to the repository (Coolify's auto
deploy stays off).

### 9.1 Proxy configuration (once per server)

The shared middlewares (`sec`, `strip-forwarded`, `rl-auth`, `rl-api`, `upload-limit`) and the HTTP to
HTTPS redirect (`https-redirect`) are not labels of the stack: they would vanish whenever an app container
restarts. Copy `infra/coolify/proxy/dynamic/meetstudent.yaml` to the server as
`/data/coolify/proxy/dynamic/meetstudent.yaml` (or paste it in Servers -> Proxy -> Dynamic configurations);
Traefik reloads it live. Copy it again whenever the file changes in the repository: the deploy does not.
Without it every route of the stack is disabled (404 everywhere). Its values match the labels of
`compose.prod.yml`'s `traefik` service: change both together.

The stack's labels use Coolify v4's names by default. Check them on the instance (Servers -> Proxy, the
Traefik command line) and set the variable only if a name differs:

| Variable | Default | Meaning |
|---|---|---|
| `PROXY_NETWORK` | `coolify` | Docker network shared by Coolify's Traefik and the stack's `web` and `backoffice` |
| `API_EDGE_NETWORK` | `meetstudent-api-edge` | Dedicated Docker network shared by Coolify's Traefik and the stack's `api` only (see below) |
| `PROXY_HTTP_ENTRYPOINT` | `http` | Traefik entry point on port 80 (the redirect routers) |
| `PROXY_HTTPS_ENTRYPOINT` | `https` | Traefik entry point on port 443 |
| `PROXY_CERT_RESOLVER` | `letsencrypt` | Traefik certificate resolver (Let's Encrypt HTTP-01) |

The proxy network is shared with every Coolify resource, so a plain `api` or `db` there can resolve to
another stack's container. The stack never uses those names: the API reaches Postgres as `meetstudent-db`
(internal `backend` network) and the SSR reaches the API as `meetstudent-api` (internal `ssr` network).
Keep these aliases if you edit the file.

#### The API's dedicated network (once per server, before the first deploy)

The `api` is deliberately **not** on `coolify`: that network is shared by every Coolify resource, and a
neighbour container there could call `api:8080` directly, past `rl-auth`/`rl-api`, the upload cap and
`strip-forwarded`, and forge `X-Forwarded-For` (the contact form's per-IP limit). The `api` is on its own
network, `meetstudent-api-edge`, that only Traefik and the `api` join. Two layers:

1. **Network**: nothing else is on `meetstudent-api-edge`, so nothing else can reach the API.
2. **Trusted proxies** (`TRUSTED_PROXIES`, a regex, default `10[.]250[.]77[.][0-9]+` = the subnet below): the API honours
   `X-Forwarded-*` only from a peer in that subnet (Tomcat's `RemoteIpValve`); from any other peer the headers are
   ignored and the socket address is the client. Keep the regex and the subnet in step.

Owner steps (the stack cannot do them: Coolify's proxy must join the network before it exists in the stack,
and an external network that is missing makes the deploy fail loudly instead of silently falling back):

```bash
# 1. On the server, create the network (not `--internal`: the api needs the internet for SMTP).
docker network create --subnet 10.250.77.0/24 meetstudent-api-edge
```

2. In Coolify: Servers -> the server -> Proxy -> Configuration, add the network to Traefik's service **and**
   declare it at the bottom, next to `coolify`, then restart the proxy:

```yaml
services:
  traefik:
    networks:
      - coolify
      - meetstudent-api-edge      # added
networks:
  coolify:
    external: true
  meetstudent-api-edge:           # added
    external: true
```

3. Deploy, then check from the server (all four must hold):

```bash
docker inspect -f '{{json .NetworkSettings.Networks}}' <api container> | jq 'keys'   # exactly backend, ssr, api-edge (real names: <project>_backend, <project>_ssr, meetstudent-api-edge)
docker inspect -f '{{json .NetworkSettings.Networks}}' coolify-proxy | jq 'keys'     # contains coolify and meetstudent-api-edge
docker run --rm --network coolify alpine nc -z -w 3 <api ip on meetstudent-api-edge> 8080   # fails (timeout)
curl -s -o /dev/null -w '%{http_code}\n' https://$DOMAIN/api/v1/schools              # 200
```

The first check is the one that matters: if `coolify` appears among the API container's networks, Coolify attached
it anyway. Turn off the resource's "Connect to Predefined Network" option (Coolify, resource -> Advanced), redeploy and
check again; until it lists exactly those three networks the isolation is not in place.

If the subnet clashes with something on the server (`docker network create` says "Pool overlaps"), pick another
`10.x.y.0/24`, create the network with it and set `TRUSTED_PROXIES` to match (for example `10[.]250[.]99[.][0-9]+`,
written without backslashes so that Coolify's variable form keeps it intact). If `meetstudent-api-edge` was created with another
subnet than `TRUSTED_PROXIES` says, the API ignores Traefik's headers (fail closed): the contact form's per-IP limit
then counts all visitors as one address (5 messages per hour for the whole site) and `https` redirects built by the API
may use `http`. A Coolify upgrade or a proxy reset that drops the network from the proxy configuration shows as a 502/504
on `/api` only; re-add step 2. If the owner cannot make the proxy join the network, the accepted fallback is
`API_EDGE_NETWORK=coolify` plus `TRUSTED_PROXIES` left to the private ranges, which restores the old exposure: see the
first risk below.

Risks this setup accepts, to keep in mind:

- **Nothing but Traefik on `meetstudent-api-edge`.** Do not attach other containers to it: every peer of that
  network can call the API directly, past the limits, and is trusted to set the client address. The `web` and
  `backoffice` stay on `coolify`: a compromised neighbour there can still call them directly, which exposes
  nothing the public site does not (static files and the server-rendered pages, no secret, no rate limit
  of theirs to dodge). Still, run nothing on this Coolify server that you do not trust.
- **Cloudflare DNS only (grey cloud), for good.** The rate limits count requests per client IP as Traefik
  sees it. Behind Cloudflare's proxy (orange cloud) every visitor arrives from a few Cloudflare addresses
  and shares one budget: 10 sign-ins a minute for the whole site. Turn the proxy on only after setting
  Traefik's `forwardedHeaders.trustedIPs` on the entry points to Cloudflare's published ranges.
- **`forwardedHeaders.insecure` (check in coolify-deploy/05).** If Coolify's Traefik command line sets
  `--entrypoints.<name>.forwardedHeaders.insecure=true`, Traefik trusts any `X-Forwarded-For` a client
  sends: a client can then pick its own address, which defeats the per-IP limits and lies in the API logs.
  Check the proxy's command line (Servers -> Proxy) on the first deploy, and remove the flag if present.

### 9.2 The Coolify resource

1. Projects -> `MeetStudent` -> environment `production` -> New resource -> Public repository
   `https://github.com/ibrahima-isi/meetstudent`, branch `main`, build pack **Docker Compose**, compose file
   `/compose.coolify.yml`.
2. Advanced -> Docker Compose -> **Raw** on (ADR 0003: Coolify's generated labels cannot express the rate
   limits, the upload cap or the headers). **Auto deploy off**: only the CI webhook deploys, once the images
   exist. Do not set domains in the Coolify UI: the routes are the stack's labels.
3. Environment variables, marking the secrets as secret. Coolify passes them to compose for interpolation.

| Variable | Required | Meaning |
|---|---|---|
| `DOMAIN`, `BO_DOMAIN` | yes | public site (web + API, single origin) and back office hosts, no scheme |
| `CORS_ALLOWED_ORIGINS` | yes | `https://${DOMAIN},https://${BO_DOMAIN}` |
| `JWT_SECRET_KEY` | yes, secret | `openssl rand -base64 48` |
| `POSTGRES_PASSWORD` | yes, secret | `openssl rand -base64 32 \| tr -d '/+=\n'`; applied only when the database volume is created |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | yes (password secret) | first admin account, password at least 12 characters (section 4) |
| `IMAGE_TAG` | no, default `latest` | image tag of the four services; `sha-<40-char commit>` to roll back (9.6) |
| `PROXY_NETWORK`, `PROXY_HTTP_ENTRYPOINT`, `PROXY_HTTPS_ENTRYPOINT`, `PROXY_CERT_RESOLVER` | no | see 9.1 |
| `API_EDGE_NETWORK`, `TRUSTED_PROXIES` | no, default `meetstudent-api-edge` and `10[.]250[.]77[.][0-9]+` | the API's dedicated network and the regex of the peers it trusts for `X-Forwarded-*` (9.1); change both together with the subnet |
| `JWT_ACCESS_TTL_MINUTES` | no, default `15` | access token lifetime |
| `POSTGRES_DB`, `POSTGRES_USER` | no, default `meetstudent` | database name and user |
| `BO_API_URL` | no, default `https://${DOMAIN}/api/v1` | API base URL of the back office |
| `MAIL_ENABLED`, `MAIL_PROVIDER`, `MAIL_SES_REGION`, `MAIL_FROM`, `CONTACT_RECIPIENT`, `FRONTEND_BASE_URL` | no | mail, as in section 2 and 2b (off by default) |
| `SPRING_MAIL_*`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED`, `APP_CONTACT_MAIL_*`, `APP_MAIL_TEST_COOLDOWN_SECONDS` | no | SMTP settings and mail tuning, as in section 2 |
| `R2_ENDPOINT`, `R2_BUCKET`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` | for backups, keys secret | the R2 bucket (9.4) |
| `BACKUP_CRYPT_PASSWORD`, `BACKUP_CRYPT_SALT` | for backups, secret | the backup encryption key (9.4) |

`ACME_EMAIL`, `ACME_CA_SERVER`, `BACKUP_DIR` and `BACKUP_KEEP_DAYS` belong to `compose.prod.yml` only:
Coolify's Traefik issues the certificates and the `backup` service replaces `scripts/backup.sh`.
Do not use `$` in a value: compose would interpolate it.

### 9.3 GitHub `production` environment and the deploy webhook

1. Coolify: Settings -> Advanced -> **API Access** on; Keys & Tokens -> an API token with the **deploy**
   permission.
2. GitHub -> Settings -> Environments -> **`production`**, deployment branches restricted to **`main`**.
   Its two secrets, readable only by a job of that environment on `main`:
   - `COOLIFY_WEBHOOK`: the resource's Webhooks -> "Deploy Webhook (auth required)" URL.
   - `COOLIFY_TOKEN`: the token of step 1.
3. The webhook URL must be reachable from GitHub's runners. With the "SSH tunnel only" dashboard option it
   is not, and every deploy job fails at its last step: the dashboard then needs its HTTPS hostname.

The `deploy / production` job runs only on a push to `main`, after `api / build-and-test`,
`web / build-and-test` and `backoffice / build-and-test` pass. It builds and pushes the four images, then
calls `curl -fsS -L --max-redirs 0 --max-time 60 -H "Authorization: Bearer $COOLIFY_TOKEN" "$COOLIFY_WEBHOOK"` (secrets
passed by environment, never printed): any answer other than 2xx, a redirect included (use the exact `https://`
URL Coolify shows), turns the run red, and the images are already on
GHCR, so a redeploy from Coolify (or a re-run of the job) finishes the job. A hung push or webhook stops the
job after 30 minutes (the call itself after 60 seconds). Deploys run one at a time (concurrency group
`deploy-production`): a running deploy is never cancelled, and a newer push never cancels a run on `main`.
GitHub keeps only one pending run per group, though: if two pushes land while a deploy runs, the older
pending one is cancelled and only the newest deploys, which is the one wanted. The job is not a required
check: it only exists on `main`.

After the very first run, set each package to **public** (GitHub -> Packages -> `meetstudent-api`,
`meetstudent-web`, `meetstudent-backoffice`, `meetstudent-backup` -> Package settings -> Change visibility):
packages start private, and Coolify pulls without credentials. The images hold no secret.

### 9.4 Nightly encrypted backups

The `backup` service idles (`sleep infinity`); a Coolify **Scheduled Task** runs the backup in it:
resource -> Scheduled Tasks -> name `backup`, command `/usr/local/bin/backup.sh`, frequency `15 3 * * *`,
container `backup`. Coolify notifies a failed task (Notifications, plan phase 2): `backup.sh` exits non-zero
on any error, and with `2` when a variable below is missing.

A backup is a folder named after its UTC time (`20261027T031500Z`) holding the custom-format database dump,
an archive of each upload volume and their checksums. It goes to the R2 bucket through an rclone `crypt`
remote: names and contents are encrypted on the VPS, and no local copy is kept.

- R2 (plan phase 5): bucket with **Jurisdiction: EU**, lifecycle rule deleting objects after 30 days (the
  retention lives there, not in the script), API token scoped to that bucket, read and write.
  That token sits on the VPS and can delete objects: whoever takes the server can erase the backups too.
  Add a **bucket lock** rule (R2 -> bucket -> Settings -> Bucket lock) with a retention of at least 30 days,
  so no object can be deleted or overwritten before then, by the token or anyone else.
  `R2_ENDPOINT` is `https://<ACCOUNT_ID>.eu.r2.cloudflarestorage.com` (the scripts refuse, with exit `2`, an
  endpoint without `.eu.`), `R2_BUCKET` the bucket name, `R2_ACCESS_KEY_ID` and `R2_SECRET_ACCESS_KEY` the
  token's keys.
- `BACKUP_CRYPT_PASSWORD` and `BACKUP_CRYPT_SALT`: two long random strings
  (`openssl rand -base64 32 | tr -d '/+=\n'`). Store them **in clear**, both in the Coolify secrets and in the
  password manager, before the first backup; the scripts obscure them for rclone. Without both, no backup
  can ever be read again; changing them makes the older backups unreadable with the new ones.
- `backup` is the only service on the `egress` network (with outbound access): `backend`, where Postgres
  lives, is internal and has no route to R2. It mounts the upload volumes read-only.

Run the task once by hand after the first deploy (from the task's page, or `backup.sh` in the `backup`
container's terminal) and check its log ends with `backup complete`. `restore.sh --list`, run in the same
container, lists the complete backups: `SHA256SUMS` is sent last, so a folder without it (an upload cut off
halfway) is never listed nor taken as `latest`. A run killed hard leaves its local copy in `/tmp`; the next
run removes it.

### 9.5 Restore

DESTRUCTIVE: the database and both upload volumes are replaced. Nothing is touched until the backup is
downloaded and its checksums match. The `backup` service mounts the volumes read-only, so a restore is a
one-off run of that service with the volumes mounted writable at `/restore`, the api stopped:

```bash
ssh <vps>
docker compose ls                                # the stack's project: the resource UUID
cd /data/coolify/applications/<uuid>             # Coolify's copy of the compose file and its .env
docker volume ls | grep -E 'api-(uploads|private)'   # the real volume names under this project
docker compose -p <uuid> -f docker-compose.yaml stop api
docker compose -p <uuid> -f docker-compose.yaml run --rm \
  -v <uploads volume>:/restore/uploads -v <private volume>:/restore/private \
  backup restore.sh latest --yes                 # or a folder name from restore.sh --list
docker compose -p <uuid> -f docker-compose.yaml start api
```

The project, the directory and the volume names are Coolify's; check them on the instance (Coolify may prefix
the volumes with the resource UUID). This is the same command as the comment of `infra/backup/restore.sh`,
which a local test runs against MinIO. Then repeat the smoke test of section 5 and open an uploaded
document. Rehearse it in a throwaway stack before launch and every quarter: a backup never restored is not
a backup.

### 9.6 Rollback

Set `IMAGE_TAG=sha-<full 40-character commit>` of a good commit in the resource's variables, then
**Redeploy** in Coolify: the four images of that commit are pulled from GHCR, nothing is rebuilt. While
`IMAGE_TAG` is pinned, every following deploy (webhook included) stays on that version: set it back to
`latest` once `main` is fixed. Flyway migrations only go forward: to undo one, restore (9.5) the backup taken
before it.

### 9.7 Routine

- Logs, restarts and the container terminal are in the Coolify UI. `compose.prod.yml` stays the path without
  Coolify (sections 1 to 8); never run both stacks on one server.
- Update Coolify by hand, after a backup, then check the certificates and the HTTP to HTTPS redirect again.
- From outside after each deploy that touches the proxy: HSTS and `X-Content-Type-Options` present, a burst
  of about twenty logins ends in 429, an 11 MB upload is refused (section 5).

## Design notes

- **Rate limiting** (Traefik, per client IP): `/api/v1/auth*` and
  `POST /api/v1/users` allow 10 requests/minute with a burst of 5; the rest of
  `/api` allows 30/s with a burst of 100. Login, refresh and registration share one budget per IP. Students sharing one school NAT address
  share the same budget; raise `rl-auth` if that bites: in `compose.prod.yml`, and for Coolify in
  `infra/coolify/proxy/dynamic/meetstudent.yaml` (copied to the server again, section 9.1).
  Per-IP limiting relies on Docker preserving the real client IP, which it does
  for IPv4. If you enable IPv6 and Docker's userland proxy hides the source
  address, use IPv4 only.
- **Upload cap**: `POST /api/v1/media` is limited to 10 MB at Traefik (the API
  enforces the same 10 MB). Other request bodies are not capped at the proxy.
- **Security headers**: HSTS (1 year, subdomains), `X-Content-Type-Options`,
  `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`, and
  `Content-Security-Policy: frame-ancestors 'none'`. A full CSP is
  **deliberately not set**: Angular SSR injects inline state/style blocks and the
  policy could not be verified against the real app in a browser here, and a wrong
  CSP blanks the site. Add one after testing in report-only mode.
  HSTS preload is not enabled.
- **Docker socket**: Traefik mounts it read-only. That limits writes through the
  socket but it still exposes container metadata; a socket proxy is the next
  hardening step.
- **API user**: the API image runs as root inside its container (no `USER` in
  its Dockerfile), so the named volumes are writable with no extra step. If the
  image is made unprivileged later, `chown` `/data/uploads` and `/data/private`
  to that uid first.
- **Backoffice build**: `apps/backoffice/Dockerfile` expects
  `package.json`/`package-lock.json` and the Angular 20 application-builder output
  at `dist/backoffice/browser`. If the scaffold's project name differs, change
  `DIST_DIR` under `backoffice.build.args` in `compose.prod.yml`.

## Known launch limitations

- Email verification and password reset work end to end (API and web pages) only with `MAIL_ENABLED=true`
  and a working SMTP provider (Brevo, SES, ...). Without it nothing is sent: a user who forgets a password
  cannot recover it (an admin must intervene in the database), and the web hides the verification banner.
  Set `MAIL_PROVIDER` (or `SPRING_MAIL_HOST`), `MAIL_FROM`, `FRONTEND_BASE_URL` (https) and the credentials; the
  API refuses to start if the provider, `MAIL_FROM` or the SMTP host is missing or invalid while mail is enabled.
  No real Brevo, Gmail or SES connection has been tested from the build sandbox: run the section 2b checklist on the
  demo server first.
- Migration `V24` permanently deletes older duplicate ratings (it keeps the latest per user and target) before
  adding the unique constraints: run `scripts/backup.sh` before the first deploy that includes it.
- Anyone can register as a teacher (`ROLE_EXPERT`), but the account starts `PENDING`: until an admin approves it
  the expert rates schools only, like a student (programs and courses answer 403). Migration `V29` marks every
  expert that already exists `APPROVED`, so no acquired right is removed; run `scripts/backup.sh` before the first
  deploy that includes it. Admins approve or reject experts in the back office, entry
  "Experts" (tabs pending / approved / rejected, optional reason of 255 characters at rejection); a decision takes
  effect on the next request without the expert signing in again. Ratings an expert already published are kept
  when the account is rejected. The SQL update (`update users set expert_status = 'APPROVED', expert_decision_date = now() where id = ...`)
  is now only an emergency fallback, for instance when no admin can reach the back office. An admin who changes a role to expert via
  `PATCH /users/{id}/role` approves the account at the same time (even a previously rejected expert moved to another
  role and back); leaving the expert role clears the status, and setting the expert role on an expert changes nothing.
- Single server, single database: no high availability; recovery depends on
  your off-site backups.
- No full CSP, and no WAF or CDN in front.
