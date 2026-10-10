# Production deployment (single VPS, Docker Compose, HTTPS)

Files: `compose.prod.yml`, `.env.production.example`, `apps/backoffice/Dockerfile`,
`scripts/backup.sh`, `scripts/restore.sh`.

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
| `MAIL_ENABLED` | optional, `true` to send account emails; default `false` (nothing is sent) |
| `MAIL_FROM` | sender address, required when `MAIL_ENABLED=true` |
| `FRONTEND_BASE_URL` | optional, origin used in email links; default `https://${DOMAIN}` (must be https) |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | SMTP server; the host is required when `MAIL_ENABLED=true` |
| `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | SMTP credentials (secret) |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` | `true`/`false`, typical for port 587 |
| `BACKUP_DIR`, `BACKUP_KEEP_DAYS` | optional, used by `scripts/backup.sh` |

Do not use `$` in secrets: compose would try to interpolate it.
`POSTGRES_PASSWORD` is applied only when the database volume is first created.

Email is optional and off by default: leave the `MAIL_*` / `SPRING_MAIL_*` variables unset and the API
starts and runs without sending anything. If `MAIL_ENABLED=true` with a missing SMTP host, sender address or
a non-https `FRONTEND_BASE_URL`, the API refuses to start and names the variable. The health check does not
contact the SMTP server. Details: `apps/api/docs/production.md`.

Validate before starting (prints nothing on success, names the missing variable otherwise):

```bash
docker compose -f compose.prod.yml config -q
```

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
not change the password (change it through the application). Check
`docker compose -f compose.prod.yml logs api | grep -i admin` after the first
boot. Note: this behaviour is implemented by a separate change; confirm it is
on the branch you deploy.

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

## Design notes

- **Rate limiting** (Traefik, per client IP): `/api/v1/auth*` and
  `POST /api/v1/users` allow 10 requests/minute with a burst of 5; the rest of
  `/api` allows 30/s with a burst of 100. Login, refresh and registration share one budget per IP. Students sharing one school NAT address
  share the same budget; raise `rl-auth` in `compose.prod.yml` if that bites.
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

- No email verification: accounts are usable immediately with any email address.
- No password reset: a user who forgets a password cannot recover it (an admin
  must intervene in the database).
- Single server, single database: no high availability; recovery depends on
  your off-site backups.
- No full CSP, and no WAF or CDN in front.
