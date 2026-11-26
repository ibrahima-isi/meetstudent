# Portable media transfer, cutover, rollback, and disaster recovery

This runbook covers qualifying an operator-supplied private S3 bucket and independent EU backup destination, transferring MeetStudent media between **Disk -> S3** and **S3 -> S3**, pausing and draining media/reference mutations for Coolify production cutover, rehearsing rollback (reverse sync after reopening writes), and recovering from off-site backups when the active S3 provider is unavailable.

Tooling: [`infra/media/media_transfer.py`](../infra/media/media_transfer.py) (Python 3 stdlib CLI, zero external packages).
Architecture reference: [ADR 0004](adr/0004-encrypted-offsite-backups-r2-eu.md), [ADR 0005](adr/0005-stockage-portable-des-medias.md), [production deployment guide](DEPLOY.md), and [API media storage settings](../apps/api/docs/production.md#media-storage).

## 0. Operator bucket qualification and independent EU backup check

Configure the deploy-time S3 variables (`MEDIA_S3_BUCKET`, `MEDIA_S3_REGION`, optional `MEDIA_S3_ENDPOINT`, `MEDIA_S3_PATH_STYLE`, `MEDIA_S3_ACCESS_KEY_ID`, `MEDIA_S3_SECRET_ACCESS_KEY`, optional `MEDIA_S3_SESSION_TOKEN`) for your chosen provider:

- **AWS S3:** omit `MEDIA_S3_ENDPOINT`, set `MEDIA_S3_REGION` (e.g. `eu-west-3`), `MEDIA_S3_PATH_STYLE=false`.
- **Cloudflare R2 (EU):** set `MEDIA_S3_ENDPOINT=https://<ACCOUNT_ID>.eu.r2.cloudflarestorage.com`, `MEDIA_S3_REGION=auto`, `MEDIA_S3_PATH_STYLE=true`.
- **Hetzner Object Storage:** set `MEDIA_S3_ENDPOINT=https://<LOCATION>.your-objectstorage.com` (e.g. `https://fsn1.your-objectstorage.com`), `MEDIA_S3_REGION=<LOCATION>` (`fsn1`), `MEDIA_S3_PATH_STYLE=false` (or `true` if path-style is required).

Before copying or cutting over, export the configured `MEDIA_S3_*` variables and the backup destination variables (`BACKUP_S3_*` or `R2_ENDPOINT`, plus `BACKUP_REMOTE` if set) in the shell running `qualify-bucket`. The CLI reads these from the environment, including the credentials and any session token. Set `MEDIA_S3_PATH_STYLE=true` for path-style addressing or `false` for virtual-hosted addressing.

```bash
python3 infra/media/media_transfer.py qualify-bucket \
  --require-https \
  --check-backup-independence
```

`qualify-bucket` verifies and cleans up ephemeral probe keys under `public/__qualify_probe__/` and `private/__qualify_probe__/`, failing non-zero if any check breaks:

- **Private-only bucket access:** unauthenticated HTTP `GET` requests to `public/` objects, `private/` objects, and bucket listing (`?list-type=2`) MUST return `401` or `403` (never `200`).
- **S3 permissions & semantics:** missing object `HEAD` returns `404` (verifying `s3:ListBucket` so missing keys return `404` rather than `403`/`503`), `PutObject`, `GetObject` SHA-256 roundtrip, `Range: bytes=2-5` (`206 Partial Content` + `Content-Range`), `ListObjectsV2`, and `DeleteObject` all succeed; `--require-https` refuses non-HTTPS endpoints.
- **Independent EU backup destination:** verifies EU jurisdiction, refuses Hetzner Object Storage (same provider as VPS), and refuses colocating backups on the same provider/endpoint as `MEDIA_S3_*`. If active media uses Cloudflare R2, configure `BACKUP_S3_*` on an independent EU S3 destination (e.g. AWS `eu-west-1`/`eu-west-3` or Scaleway `fr-par`/`nl-ams`) with a 30-day lifecycle + object lock rule while keeping `R2_*` configured during the 30-day overlap. Use `restore.sh --remote r2crypt: --list` to find historical archives in R2.

## 1. Pre-cutover encrypted backup, inventory, and pre-copy checks

Before starting cutover, take a fresh encrypted off-site backup and record the **pre-cutover restore point** (`YYYYMMDDTHHMMSSZ`). Run the backup commands from the directory containing Coolify's generated `docker-compose.yaml`, with `COOLIFY_PROJECT` set to the resource UUID.

```bash
docker compose -p "$COOLIFY_PROJECT" -f docker-compose.yaml exec -T backup /usr/local/bin/backup.sh &&
  (set -o pipefail; docker compose -p "$COOLIFY_PROJECT" -f docker-compose.yaml \
    exec -T backup /usr/local/bin/restore.sh --list | tail -n 1)
```

The inventory covers **both**:

- Every row in PostgreSQL `media.storage_key`
- Every served legacy public file under `public/**` (even without a `media` row, e.g. legacy files with spaces such as `public/legacy/old logo.png`)

Temporary write files (`.media-*.tmp`) and symlinks are ignored and never transferred.

Run the Python CLI in Bash from the repository root. Set `UPLOAD_VOLUME` and `PRIVATE_VOLUME` to the actual Docker volume names, and keep `MEDIA_TRANSFER_PSQL_CMD` exported for `precheck`, `sync`, and `verify`.

```bash
# Set DB_CONTAINER, DB_USER, and DB_NAME to match the deployment.
export MEDIA_TRANSFER_PSQL_CMD="docker exec -i $DB_CONTAINER psql -U $DB_USER -d $DB_NAME"
disk_source=(
  --source-type docker-volume
  --source-upload-volume "$UPLOAD_VOLUME"
  --source-private-volume "$PRIVATE_VOLUME"
)
python3 infra/media/media_transfer.py precheck "${disk_source[@]}"
```

`precheck` fails non-zero if any of the following exist:

- Legacy private rows (`visibility = 'PRIVATE'` whose `storage_key` does not start with `private/`).
- Invalid storage keys failing `MediaStorage.isValidKey` (`.`, `..`, empty segment, `\`, `\0`, or wrong prefix).
- Public visibility rows not starting with `public/`.
- Missing source files or `media.size_bytes` mismatches.

**Important (`s3` mode non-rewrite guarantee):** `MediaMigrationRunner` is annotated `@ConditionalOnProperty(name = "file.storage-backend", havingValue = "disk", matchIfMissing = true)` and is completely disabled when `MEDIA_STORAGE_BACKEND=s3`. Object mode never rewrites legacy private keys at startup. Any historical `PRIVATE` rows must be resolved in `disk` mode before cutover.

## 2. Repeatable initial copy and SHA-256 verification

Initial copy runs while the application stays online (`MEDIA_WRITES_PAUSED=false`). It supports **Disk -> S3** (`--source-type disk|docker-volume`, `--target-type s3`) and **S3 -> S3** (`--source-type s3`, `--target-type s3` with separate endpoints/buckets/credentials):

```bash
# Disk -> S3 initial copy (repeatable; identical size + SHA-256 objects are skipped)
target_s3=(
  --target-type s3
  --target-s3-bucket "$MEDIA_S3_BUCKET"
  --target-s3-region "$MEDIA_S3_REGION"
  --target-s3-endpoint "${MEDIA_S3_ENDPOINT:-}"
  --target-s3-access-key "$MEDIA_S3_ACCESS_KEY_ID"
  --target-s3-secret-key "$MEDIA_S3_SECRET_ACCESS_KEY"
  --target-s3-session-token "${MEDIA_S3_SESSION_TOKEN:-}"
)
if [[ "${MEDIA_S3_PATH_STYLE:-false}" == true ]]; then
  target_s3+=(--target-s3-path-style)
fi
python3 infra/media/media_transfer.py sync "${disk_source[@]}" "${target_s3[@]}"
```

Run the transfer examples from a trusted operator shell. The CLI's role-specific S3 options for `sync` and `verify` take credentials as command-line arguments.

Reuse the same Bash session to verify keys, sizes, and streaming SHA-256 checksums (never relying on S3 `ETag`):

```bash
python3 infra/media/media_transfer.py verify "${disk_source[@]}" "${target_s3[@]}"
```

`verify` exits `3` on target differences and lists `missing_on_target`, `size_mismatches`, `checksum_mismatches`, and `extra_on_target`. A source precheck failure exits `2`.

## 3. Cutover: pause, drain, final sync, and abort behavior

### Pause and drain mechanics

- Setting `MEDIA_WRITES_PAUSED=true` (`file.writes-paused=true`) or calling `POST /api/v1/admin/media/maintenance/pause` (ADMIN-only) pauses all endpoints that mutate media or media references:
  - `POST /api/v1/media`, `DELETE /api/v1/media/{id}`, `PATCH /api/v1/media/{id}/verification`
  - `POST|PUT|PATCH|DELETE /api/v1/schools/**`
  - `POST|PUT|PATCH|DELETE /api/v1/programs/**`
  - `POST|PUT|PATCH|DELETE /api/v1/courses/**`
  - `DELETE /api/v1/users/{id}`
- Paused write endpoints answer **HTTP `503 Service Unavailable`** with `Retry-After` (default `30` seconds in Coolify and the other Compose stacks, `60` seconds when running Spring Boot directly). Override it with `MEDIA_WRITES_RETRY_AFTER_SECONDS`. Public media reads (`/uploads/public/**`), private media reads (`/api/v1/media/{id}`), auth/role checks (`401`/`403`), catalogue reads, and non-media profile edits keep working.
- `MediaWriteGate` tracks `inFlightWrites` and `pendingDeferredDeletions` (`TransactionSynchronization.afterCommit()` / `afterCompletion()`). `pause-drain` and `cutover` wait until both counters reach `0` **and** `pg_stat_activity` reports zero active write transactions (`backend_xid IS NOT NULL` or active queries touching `media`, `schools`, `programs`, `courses`, or `users`).
- The API configures `server.shutdown: graceful` (`30s` phase timeout) and Compose services configure `stop_grace_period: 35s` so container restarts drain active requests and deferred deletions before closing database connections.

### Final sync, Coolify activation, pre-reopen read/refusal checks, and abort rules

The steps below describe a manual Coolify cutover. For an automated cutover, use `cutover` with `--activate-cmd` and `--abort-cmd` to switch the deployment and restore the source if the switch fails or a later check aborts. The CLI pauses and resumes writes itself, reporting the elapsed pause as `pause_duration_ms`. Pass `--verify-reads` to run the API checks before it resumes writes.

1. Pause writes and drain API requests, deferred deletions, and PostgreSQL write transactions (`pause-drain`).
2. Run final `sync --delete` to reconcile additions, updates, and deletions since initial copy.
3. Run strict `verify` (keys, sizes, streaming SHA-256).
4. **Before activation, abort if the pause window is exceeded or inventories diverge.** Reopen writes on the source (`POST /api/v1/admin/media/maintenance/resume`). For automated cutover, set the threshold with `cutover --max-pause-seconds`.
5. **Activate S3 with writes still paused:** in Coolify environment variables, set `MEDIA_STORAGE_BACKEND=s3`, all qualified `MEDIA_S3_*` variables, and **`MEDIA_WRITES_PAUSED=true`**, then redeploy the stack (`api` and `backup`). Keeping `MEDIA_WRITES_PAUSED=true` guarantees the newly started S3-mode API container boots paused.
6. **Before reopening writes:** run `verify-api` with `--expect-writes-paused`, using `--source-type s3` and the corresponding `--source-s3-*` flags for the active bucket. Supply `--api-url`, an admin token (`--api-token`), and both `--owner-token` and `--other-token` for private-media checks. Use the same API and token flags with automated `cutover --verify-reads`.
   - Public reads (`/uploads/public/...`, including legacy paths and `Range: bytes=0-3` `206` responses) return `200` / `206` with exact SHA-256 checksums.
   - Private reads (`GET /api/v1/media/{id}`) return `200` with exact SHA-256 for both **owner** and **admin** tokens.
   - The CLI checks anonymous private reads (`401`), non-owner private reads (`403`), sampled anonymous S3 reads (`401`/`403`), and a media delete probe (`503` with `Retry-After`). Check paused upload and school update requests manually.
7. **Reopen writes:** set `MEDIA_WRITES_PAUSED=false` in Coolify environment variables and redeploy (or call `POST /api/v1/admin/media/maintenance/resume` immediately after updating Coolify env to `MEDIA_WRITES_PAUSED=false` so any later restart keeps writes open).
8. **After reopening writes:** verify a new media upload (`POST /api/v1/media`), a media deletion (`DELETE /api/v1/media/{id}`), and run a fresh S3-mode encrypted backup to the independent EU destination, recording the new **post-cutover restore point**:

```bash
docker compose -p "$COOLIFY_PROJECT" -f docker-compose.yaml exec -T backup /usr/local/bin/backup.sh &&
  (set -o pipefail; docker compose -p "$COOLIFY_PROJECT" -f docker-compose.yaml \
    exec -T backup /usr/local/bin/restore.sh --list | tail -n 1)
```

## 4. Rollback after writes reopen (reverse sync)

If writes have already reopened on the target storage (S3 bucket vs disk, or S3 bucket B vs S3 bucket A), rolling back to the source storage requires reconciling all post-cutover **files created, modified, and deleted after activation**:

1. Pause writes on the current (target) API (`MEDIA_WRITES_PAUSED=true` / `pause-drain`) and drain HTTP writes, deferred deletions, and PostgreSQL write transactions.
2. Run reverse `sync --delete` with the `--source-*` flags pointing to the active bucket and the `--target-*` flags pointing to the original storage (disk volumes or previous S3 bucket). This copies new post-activation uploads back to source, overwrites modified keys, and deletes keys removed after activation.
3. Run strict `verify` (`target -> source`) to confirm zero missing, mismatched, or extraneous objects.
4. Switch Coolify deployment config back to the source storage (`MEDIA_STORAGE_BACKEND=disk` or previous `MEDIA_S3_*`) while keeping **`MEDIA_WRITES_PAUSED=true`**, then redeploy.
5. **Before reopening writes:** run `verify-api` with `--expect-writes-paused` and the same API authentication options as in cutover. Point the `--source-*` flags to the restored storage.
6. Set `MEDIA_WRITES_PAUSED=false` in Coolify (and redeploy or call `/resume`), then run a fresh backup on the restored source storage.

## 5. Disaster recovery when the active S3 provider is unavailable

If the active S3 provider experiences a regional outage, deleted bucket, or irreversible credential loss, reverse sync from the active S3 bucket is **not possible**. Recover together from the **last complete encrypted off-site archive**:

1. List available complete backups (archives that include the final `SHA256SUMS` marker) using [`infra/backup/restore.sh`](../infra/backup/restore.sh) (`restore.sh --list`; see [DEPLOY.md section 9.5](DEPLOY.md#95-restore)).
2. Stop the `api` container during the restore. Restore the last complete archive (`db.dump`, media archives, checksums) onto either local disk volumes or a healthy replacement S3 bucket.
3. Start the API pointing at the restored PostgreSQL database and restored media storage with `MEDIA_WRITES_PAUSED=true`, verify public/private reads and auth checks, then set `MEDIA_WRITES_PAUSED=false` and resume writes.
4. **RPO / data-loss boundary:** Any database mutations or media uploads/deletions committed **after the timestamp of the restored archive** (`<YYYYMMDDTHHMMSSZ>`) and before the S3 provider outage are not present in that archive and may be permanently lost unless recoverable later from the provider. Record the restored archive timestamp in incident notes.

## 6. Automated verification

Run unit tests, the transfer/rollback suite, and the full production S3 cutover rehearsal (PostgreSQL 18 + `meetstudent-api` + `infra/backup` + isolated MinIO endpoints covering path-style, virtual-hosted, and independent EU backup):

```bash
(cd infra/media && python3 -B -m unittest discover -s . -p '*_test.py')
./infra/tests/media-transfer.test.sh
./infra/tests/s3-cutover-rehearsal.test.sh
```
