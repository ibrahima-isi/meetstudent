# Portable media transfer, rollback, and disaster recovery

This runbook covers transferring MeetStudent media between **Disk -> S3** and **S3 -> S3**, pausing and draining media/reference mutations for cutover, rehearsing rollback (reverse sync after reopening writes), and recovering from off-site backups when the active S3 provider is unavailable.

Tooling: [`infra/media/media_transfer.py`](../infra/media/media_transfer.py) (Python 3 stdlib CLI, zero external packages).
Architecture reference: [ADR 0005](adr/0005-stockage-portable-des-medias.md) and [production deployment guide](DEPLOY.md).

## 1. Inventory and pre-copy checks

The inventory covers **both**:

- Every row in PostgreSQL `media.storage_key`
- Every served legacy public file under `public/**` (even without a `media` row, e.g. legacy files with spaces such as `public/legacy/old logo.png`)

Temporary write files (`.media-*.tmp`) and symlinks are ignored and never transferred.

Before copying, run `precheck` against PostgreSQL and the source storage:

```bash
python3 infra/media/media_transfer.py precheck \
  --source-type docker-volume \
  --source-upload-volume <project>_api-uploads \
  --source-private-volume <project>_api-private \
  --psql-cmd "docker exec -i <db-container> psql -U <user> -d <db>"
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
python3 infra/media/media_transfer.py sync \
  --source-type docker-volume \
  --source-upload-volume <project>_api-uploads \
  --source-private-volume <project>_api-private \
  --target-type s3 \
  --target-s3-bucket "$MEDIA_S3_BUCKET" \
  --target-s3-region "$MEDIA_S3_REGION" \
  --target-s3-endpoint "$MEDIA_S3_ENDPOINT" \
  --target-s3-path-style \
  --target-s3-access-key "$MEDIA_S3_ACCESS_KEY_ID" \
  --target-s3-secret-key "$MEDIA_S3_SECRET_ACCESS_KEY"
```

Verify keys, sizes, and streaming SHA-256 checksums (never relying on S3 `ETag`):

```bash
python3 infra/media/media_transfer.py verify \
  --source-type docker-volume ... \
  --target-type s3 ...
```

`verify` exits non-zero (`3`) and explicitly lists `missing_on_target`, `size_mismatches`, `checksum_mismatches`, and `extra_on_target`.

## 3. Cutover: pause, drain, final sync, and abort behavior

### Pause and drain mechanics

- Setting `MEDIA_WRITES_PAUSED=true` (`file.writes-paused=true`) or calling `POST /api/v1/admin/media/maintenance/pause` (ADMIN-only) pauses all endpoints that mutate media or media references:
  - `POST /api/v1/media`, `DELETE /api/v1/media/{id}`, `PATCH /api/v1/media/{id}/verification`
  - `POST|PUT|PATCH|DELETE /api/v1/schools/**`
  - `POST|PUT|PATCH|DELETE /api/v1/programs/**`
  - `POST|PUT|PATCH|DELETE /api/v1/courses/**`
  - `DELETE /api/v1/users/{id}`
- Paused write endpoints answer **HTTP `503 Service Unavailable`** with `Retry-After` (default `60` seconds, configurable via `MEDIA_WRITES_RETRY_AFTER_SECONDS`), while public media reads (`/uploads/public/**`), private media reads (`/api/v1/media/{id}`), auth/role checks (`401`/`403`), catalogue reads, and non-media profile edits keep working.
- `MediaWriteGate` tracks `inFlightWrites` and `pendingDeferredDeletions` (`TransactionSynchronization.afterCommit()` / `afterCompletion()`). `pause-drain` and `cutover` wait until both counters reach `0` **and** `pg_stat_activity` reports zero active write transactions (`backend_xid IS NOT NULL` or active queries touching `media`, `schools`, `programs`, `courses`, or `users`).
- The API configures `server.shutdown: graceful` (`30s` phase timeout) and Compose services configure `stop_grace_period: 35s` so container restarts drain active requests and deferred deletions before closing database connections.

### Final sync, pre-reopen read checks, and abort rules

`cutover` measures the full pause window (`pause_duration_ms`) from pause start until writes reopen:

1. Pause writes and drain API requests, deferred deletions, and PostgreSQL write transactions.
2. Run final `sync --delete` to reconcile additions, updates, and deletions since initial copy.
3. Run strict `verify` (keys, sizes, SHA-256).
4. **Abort if window threshold (`--max-pause-seconds`) is exceeded or inventories diverge:** `cutover` halts without activating the target and immediately calls `POST /api/v1/admin/media/maintenance/resume` to reopen writes on the unmodified source.
5. Activate the target API with `MEDIA_STORAGE_BACKEND=s3` and `MEDIA_WRITES_PAUSED=true` so writes remain paused across restart.
6. Run `verify-api` against the target API while still paused to confirm public reads, private reads, `401` anonymous / `403` non-owner denials, and paused writes (`503`).
7. Reopen writes (`POST /api/v1/admin/media/maintenance/resume` and set `MEDIA_WRITES_PAUSED=false` in deployment config).

## 4. Rollback after writes reopen (reverse sync)

If writes have already reopened on the target storage (S3 bucket B or S3 vs disk), rolling back to the source storage requires reconciling post-cutover **additions, in-place modifications, and deletions**:

1. Pause writes on the current (target) API and drain HTTP writes, deferred deletions, and PostgreSQL transactions (`pause-drain` or `cutover` with source/target reversed).
2. Run reverse `sync --delete` with `--source` set to the active target bucket and `--target` set to the original source storage (disk volumes or previous S3 bucket). This copies new uploads back to source, overwrites modified keys, and deletes keys removed since cutover.
3. Run strict `verify` (`target -> source`) to confirm zero missing, mismatched, or extraneous objects.
4. Restart the API back on the source storage configuration with `MEDIA_WRITES_PAUSED=true`.
5. Run `verify-api` to verify public reads, private reads, and `401`/`403` refusals on the restored source API.
6. Reopen writes (`POST /api/v1/admin/media/maintenance/resume` and `MEDIA_WRITES_PAUSED=false`).

## 5. Disaster recovery when the active S3 provider is unavailable

If the active S3 provider experiences a regional outage, deleted bucket, or irreversible credential loss, reverse sync from the active S3 bucket is **not possible**. Recover together from the **last complete encrypted off-site archive**:

1. List available complete backups (archives that include the final `SHA256SUMS` marker) using [`infra/backup/restore.sh`](../infra/backup/restore.sh) (`restore.sh --list`; see [DEPLOY.md section 9.5](DEPLOY.md#95-restore)).
2. Stop the `api` container (or keep `MEDIA_WRITES_PAUSED=true` during restoration) and restore the last complete archive (`db.dump`, media archives, checksums) onto either local disk volumes or a healthy replacement S3 bucket.
3. Start the API pointing at the restored PostgreSQL database and restored media storage with `MEDIA_WRITES_PAUSED=true`, verify public/private reads and auth checks, then set `MEDIA_WRITES_PAUSED=false` and resume writes.
4. **RPO / data-loss boundary:** Any database mutations or media uploads/deletions committed **after the timestamp of the restored archive** (`<YYYYMMDDTHHMMSSZ>`) and before the S3 provider outage are not present in that archive and may be permanently lost unless recoverable later from the provider. Record the restored archive timestamp in incident notes.

## 6. Automated verification

Run unit tests and the full isolated rehearsal (PostgreSQL 18 + two isolated MinIO instances + `meetstudent-api`):

```bash
python3 -m unittest infra/media/media_transfer_test.py
./infra/tests/media-transfer.test.sh
```
