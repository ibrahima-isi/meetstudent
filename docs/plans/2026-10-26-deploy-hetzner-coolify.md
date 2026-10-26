# Plan — deploy MeetStudent on a Hetzner VPS with Coolify

Written 2026-10-26 against `dev` @ `5b4aba6`. Decisions come from a grilling session with the owner and are
recorded in [ADR 0003](../adr/0003-coolify-raw-compose-ghcr.md) (Coolify Raw compose, images from GHCR) and
[ADR 0004](../adr/0004-encrypted-offsite-backups-r2-eu.md) (encrypted off-site backups on R2 EU). Read them,
root `CLAUDE.md`, `compose.prod.yml` and `docs/DEPLOY.md` before starting any phase.

## Decisions at a glance

| Topic | Decision |
|---|---|
| Server | One Hetzner Cloud VPS, x86, 4 vCPU / 8 GB, Germany (Falkenstein or Nuremberg). Coolify and the app on the same machine |
| Proxy | Coolify's Traefik (default proxy). Our own Traefik service is dropped |
| Build | GitHub Actions builds the three images after the tests, pushes them to GHCR (public), tags `sha-<commit>` + `latest` |
| Deploy trigger | Push to `main` (the `dev` → `main` promotion), CI green → `deploy` job in `ci.yml` → authenticated Coolify webhook |
| Compose | New `compose.coolify.yml`, deployed in **Raw** mode with our Traefik labels; `compose.prod.yml` stays as the non-Coolify path |
| Database | Postgres 18 stays inside the stack, on an internal network |
| Backups | Dedicated `backup` service, run nightly by a Coolify Scheduled Task: `pg_dump` + upload volumes → `rclone crypt` → Cloudflare R2 bucket in the EU jurisdiction, 30-day lifecycle |
| Staging | None for now |
| Domain | `meetstudent.me` |

## Decisions left to the owner

These are not open questions for an agent: do not pick them, ask the owner.

- **Subdomain layout.** The stack needs two public hostnames: `DOMAIN` (web + API, single origin) and
  `BO_DOMAIN` (back office). The owner chooses them under `meetstudent.me` and whether a `www` redirect exists.
- **Coolify dashboard access.** Either a hostname under `meetstudent.me` served over HTTPS with 2FA (port 8000
  closed afterwards), or SSH tunnel only (`ssh -L 8000:localhost:8000`, port 8000 never opened).

DNS records required once the hostnames are chosen, all pointing at the VPS:

| Record | Name | Value |
|---|---|---|
| A / AAAA | `DOMAIN` | VPS IPv4 / IPv6 |
| A / AAAA | `BO_DOMAIN` | VPS IPv4 / IPv6 |
| A / AAAA | dashboard hostname (only with the HTTPS option) | VPS IPv4 / IPv6 |
| A / AAAA | `www` (only if the owner wants it) | VPS IPv4 / IPv6, redirected by a router |

If the DNS sits at Cloudflare, keep these records **DNS only** (grey cloud) until the certificates are issued:
the Let's Encrypt HTTP-01 challenge must reach Traefik directly.

## Phase 1 — Hetzner VPS (owner, console)

1. Create the server: Ubuntu 24.04 LTS, x86 4 vCPU / 8 GB, IPv4 + IPv6, the owner's SSH key, no password.
2. Attach a **Hetzner Cloud Firewall** (it filters before the host, so Docker cannot bypass it the way it bypasses
   `ufw`, see `docs/DEPLOY.md` §1): inbound `22/tcp`, `80/tcp`, `443/tcp`; `8000/tcp` only during the Coolify
   install, restricted to the owner's IP, then removed.
3. Harden SSH: a sudo user, `PermitRootLogin prohibit-password` (Coolify needs root SSH to localhost with its own
   key), `PasswordAuthentication no`; enable `unattended-upgrades`.
4. Optional: Hetzner server backups (snapshot of the whole disk). They complement, never replace, Phase 6.

## Phase 2 — Coolify install (owner)

1. `curl -fsSL https://cdn.coollabs.io/coolify/install.sh | sudo bash`.
2. **Open `http://<ip>:8000` and create the admin account immediately**: the first visitor becomes the admin.
3. Enable 2FA on that account.
4. Settings → keep **Traefik** as the proxy; start it.
5. Settings → Configuration → Advanced → enable **API Access**. Keys & Tokens → create an API token with the
   **deploy** permission; store it in the password manager.
6. Apply the dashboard-access decision (hostname + close 8000, or tunnel only).
7. Settings → disable Coolify's automatic self-update; update it by hand, after a backup.
8. Notifications → e-mail (or Discord) for failed deployments and failed scheduled tasks.

## Phase 3 — Code changes (agent sessions, one branch and one PR each, into `dev`)

### 3a. `fix/hash-refresh-tokens` (TDD)

Refresh tokens are the only secrets stored in clear in the database; e-mail and reset tokens are already
SHA-256 (`UserTokenService.hash`, lowercase hex).

- Store `SHA-256(token)` in lowercase hex, look sessions up by that hash; the raw token only ever leaves in the
  login/refresh response. Reuse the same hashing as `UserTokenService`, do not invent a second one.
- `V35`: hash existing rows in place, so nobody is signed out (same spirit as V21):
  `UPDATE refresh_tokens SET token = encode(sha256(convert_to(token, 'UTF8')), 'hex');`
  The column is `varchar(255)`, 64 hex characters fit. Integration test: a token issued before V35 still refreshes.
- ADRs 0001/0002 are unchanged (locks, `sid`, inactivity); the hash only changes what is stored.

### 3b. `ci/coolify-deploy`

**`compose.coolify.yml`** — derived from `compose.prod.yml`:

- No `traefik` service, no `ports:` anywhere.
- `api`, `web`, `backoffice`: `image: ghcr.io/ibrahima-isi/meetstudent-<app>:${IMAGE_TAG:-latest}` with
  `pull_policy: always`, instead of `build:`. Environment, volumes, healthchecks, `security_opt` unchanged.
- Networks: `edge` becomes the external `coolify` network (Coolify's Traefik lives there); `backend` stays
  `internal: true`.
- Router labels: same rules and priorities as `compose.prod.yml`, with `entrypoints=https`, `tls=true`,
  `tls.certresolver=letsencrypt`, `traefik.docker.network=coolify`.
- Shared middlewares (`sec`, `strip-forwarded`, `rl-auth`, `rl-api`, `upload-limit`) and the HTTP → HTTPS
  redirect move to a **Traefik dynamic file** in Coolify's proxy configuration (`/data/coolify/proxy/dynamic/`,
  editable from Servers → Proxy) and are referenced as `<name>@file`. Declaring them as labels on an app
  container would make them vanish for every router whenever that container restarts.
- Verify on the real instance (Coolify version may differ): entrypoint names (`http`/`https`), resolver name
  (`letsencrypt`), network name (`coolify`). Adjust the labels, not the plan, if they differ.

**Backup service** (`infra/backup/`: Dockerfile + script):

- Image: `postgres:18-alpine` + `rclone`. Joins `backend` (reaches `db`), mounts `api-uploads` and
  `api-private` **read-only**, runs `sleep infinity` so the Scheduled Task can exec into it.
- Script: `pg_dump` (custom format) + `tar` of both volumes into a timestamped folder → `rclone copy` to an
  `r2crypt:` remote → delete the local copy. Exit non-zero on any failure so Coolify notifies.
- rclone configured purely by environment: an `s3` remote (provider Cloudflare, endpoint
  `https://<ACCOUNT_ID>.eu.r2.cloudflarestorage.com` for the EU jurisdiction, bucket-scoped R2 token) wrapped by
  a `crypt` remote (password + salt in Coolify secrets, originals in the password manager).
- A matching restore script in the same image. `scripts/backup.sh`/`restore.sh` stay for `compose.prod.yml`.

**`deploy` job in `.github/workflows/ci.yml`:**

- `needs: [api, web, backoffice]`, `if: github.event_name == 'push' && github.ref == 'refs/heads/main'`,
  `environment: production` (holds the secrets, restricted to `main`), `permissions: packages: write`.
- `docker/login-action` to GHCR with `GITHUB_TOKEN`; `docker/build-push-action` for the three images, tags
  `sha-${{ github.sha }}` and `latest`; back office built with `DIST_DIR=dist/backoffice/browser`.
- Then `curl -fsS -H "Authorization: Bearer $COOLIFY_TOKEN" "$COOLIFY_WEBHOOK"`, secrets `COOLIFY_TOKEN` and
  `COOLIFY_WEBHOOK` (the resource's "Deploy Webhook (auth required)").
- Keep the job's `name:` out of the rulesets' required checks: it only runs on `main`.
- After the first push, set each GHCR package's visibility to **public** (packages start private).

Asking for more than five files here is expected (compose, Dockerfile, two scripts, `ci.yml`, `DEPLOY.md`):
ask the owner first, per the working agreement.

### 3c. Separate task — server-side sign-out

Signing out today only forgets the session in the browser (see the **Session** entry in `GLOSSARY.md`).
A `POST /api/v1/auth/logout` that deletes the current session, called by web and back office, is its own
ticket; it is not a prerequisite for this deployment.

## Phase 4 — Coolify resource (owner, with the plan open)

1. Projects → New project `MeetStudent` → environment `production`.
2. New resource → Public repository `https://github.com/ibrahima-isi/meetstudent`, branch `main`,
   build pack **Docker Compose**, compose path `/compose.coolify.yml`.
3. Advanced → Docker compose → **Raw** on. **Auto deploy off** (only the CI webhook deploys, once images exist).
4. Environment variables: every variable `compose.coolify.yml` requires (`DOMAIN`, `BO_DOMAIN`, `POSTGRES_PASSWORD`,
   `JWT_SECRET_KEY`, `CORS_ALLOWED_ORIGINS`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, mail settings, rclone/R2 settings),
   following `.env.production.example` and `apps/api/docs/production.md`. Mark secrets as secret.
5. Copy the "Deploy Webhook (auth required)" URL into the GitHub `production` environment as `COOLIFY_WEBHOOK`,
   the Phase 2 token as `COOLIFY_TOKEN`.
6. Scheduled Tasks → `15 3 * * *`, container `backup`, command `/usr/local/bin/backup.sh`.

## Phase 5 — Cloudflare R2 (owner)

1. Create the bucket with **Jurisdiction: EU**.
2. Lifecycle rule: delete objects older than 30 days.
3. API token scoped to that bucket only, read + write. Put its keys and the account ID into Coolify.
4. Generate the `rclone crypt` password and salt; store the originals in the password manager **before** the
   first backup.

## Phase 6 — First deployment and checks

1. Promote `dev` → `main` as usual (`git push origin dev:main`); watch the CI run reach the `deploy` job.
2. In Coolify: the deployment pulls the `latest` images, all five containers become healthy.
3. Certificates issued for `DOMAIN` and `BO_DOMAIN`; `http://` redirects to `https://`.
4. Run `docs/DEPLOY.md` §4 (first-boot checks) and §5 (smoke test); check that the admin from `ADMIN_EMAIL` signs
   in to the back office.
5. Check the security middlewares from outside: HSTS and `X-Content-Type-Options` present; a burst of about
   twenty logins in a few seconds ends in 429; an 11 MB upload is refused.
6. Run the backup task by hand, then **restore it** into a throwaway stack and open an uploaded document. A
   backup never restored is not a backup.

## Phase 7 — Operations

- Rollback: set `IMAGE_TAG=sha-<good commit>` in Coolify, redeploy; set it back to `latest` once `main` is fixed.
  Flyway migrations are not rolled back: restore the backup taken before a migration that must be undone.
- Coolify updates: by hand, after a backup, and re-check Phase 6 step 3.
- Restore test: quarterly, as in step 6.
- Update `docs/DEPLOY.md` with a Coolify section once Phase 6 has passed, and the CI/CD and security sections of
  the thesis (continuous delivery is then real).
