#!/usr/bin/env bash
# Tests for the encrypted off-site backup of the Coolify stack (the `backup`
# service of compose.coolify.yml, image infra/backup). Ticket coolify-deploy/03,
# ADR 0004, spec "point 2".
#
#   infra/tests/backup.test.sh            # both parts below
#   infra/tests/backup.test.sh scripts    # the scripts alone, in the backup image: no stack,
#                                         # a local folder as the storage (seconds)
#   infra/tests/backup.test.sh roundtrip  # the round trip, observed as a client
#
# A real S3 server (MinIO) stands in for Cloudflare R2. It sits on the backup
# service's outbound network only, as R2 sits on the internet: never on the
# stack's internal `backend` network. The test populates a stack (a student,
# a private document, a school), backs it up, checks that what reached S3 is
# unreadable without the key, restores it into an empty stack and finds the
# same account, school and document there, byte for byte. It also checks that
# a backup fails loudly when S3 is unreachable or the configuration incomplete.
#
# It never pulls the application images: the api image must be present locally
# as ghcr.io/ibrahima-isi/meetstudent-api:$IMAGE_TAG. The backup image under test
# is built from infra/backup with the same tag. Both stacks, the S3 server and
# the test proxy network are always torn down (down -v); the images stay.
# Needs: docker (compose v2), jq, sha256sum or shasum.
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
STACK_FILE=${STACK_FILE:-$ROOT/compose.coolify.yml}
export IMAGE_TAG=${IMAGE_TAG:-latest}
REGISTRY=ghcr.io/ibrahima-isi

# Dummy values: the stack refuses to render without the required ones.
export DOMAIN=${DOMAIN:-site.meetstudent.test}
export BO_DOMAIN=${BO_DOMAIN:-admin.meetstudent.test}
export POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-test-postgres-password}
export JWT_SECRET_KEY=${JWT_SECRET_KEY:-dGVzdC1qd3Qtc2VjcmV0LWtleS10aGF0LWlzLWxvbmctZW5vdWdoLWZvci1oczI1Ng==}
export CORS_ALLOWED_ORIGINS=${CORS_ALLOWED_ORIGINS:-https://$DOMAIN,https://$BO_DOMAIN}
export ADMIN_EMAIL=${ADMIN_EMAIL:-admin@meetstudent.test}
export ADMIN_PASSWORD=${ADMIN_PASSWORD:-test-admin-password-123}
TEST_PREFIX=${TEST_PREFIX:-meetstudent-backup-test}
export PROXY_NETWORK=${TEST_PROXY_NETWORK:-${TEST_PREFIX}-proxy}
export API_EDGE_NETWORK=${TEST_API_EDGE_NETWORK:-${TEST_PREFIX}-api-edge}

# The off-site storage: MinIO instead of R2, same S3 protocol.
S3_NAME=${TEST_PREFIX}-s3
# Named like an EU endpoint (".eu."): the scripts refuse any other R2_ENDPOINT.
S3_HOST=s3.eu.backup.test
S3_USER=backup-test-access-key
S3_SECRET=backup-test-secret-key
export R2_ENDPOINT=http://$S3_HOST:9000
export R2_BUCKET=meetstudent-backups
export R2_ACCESS_KEY_ID=$S3_USER
export R2_SECRET_ACCESS_KEY=$S3_SECRET
# Active media S3 and an independent EU backup S3 (MinIO) for S3-mode roundtrip.
MEDIA_S3_NAME=${TEST_PREFIX}-media-s3
MEDIA_S3_HOST=s3.media.test
MEDIA_S3_USER=media-test-access-key
MEDIA_S3_SECRET=media-test-secret-key
BACKUP2_S3_NAME=${TEST_PREFIX}-backup2-s3
BACKUP2_S3_HOST=s3.eu.independent.test
BACKUP2_S3_USER=backup2-test-access-key
BACKUP2_S3_SECRET=backup2-test-secret-key
BACKUP2_BUCKET=meetstudent-backups-eu
# The originals, as kept in the owner's password manager.
export BACKUP_CRYPT_PASSWORD=backup-test-crypt-password
export BACKUP_CRYPT_SALT=backup-test-crypt-salt

PROJECT_A=${TEST_PREFIX}-a # populated disk stack
PROJECT_B=${TEST_PREFIX}-b # empty disk stack (new + historical restore)
PROJECT_C=${TEST_PREFIX}-c # empty -> populated S3 stack (historical restore + S3 backup)
PROJECT_D=${TEST_PREFIX}-d # empty S3 stack (S3-mode restore)
CURL_IMAGE=curlimages/curl:8.11.1
WORK=

failures=0
pass() { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
check() { # check "description" command...
  local name=$1; shift
  if "$@" >/dev/null 2>&1; then pass "$name"; else fail "$name"; fi
}
sha() { if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1; }

stack() { local p=$1; shift; docker compose -p "$p" -f "$STACK_FILE" "$@"; }

teardown() {
  echo "teardown"
  docker rm -f "$S3_NAME" "$MEDIA_S3_NAME" "$BACKUP2_S3_NAME" >/dev/null 2>&1 || true
  stack "$PROJECT_A" down -v --remove-orphans >/dev/null 2>&1 || true
  stack "$PROJECT_B" down -v --remove-orphans >/dev/null 2>&1 || true
  stack "$PROJECT_C" down -v --remove-orphans >/dev/null 2>&1 || true
  stack "$PROJECT_D" down -v --remove-orphans >/dev/null 2>&1 || true
  docker network rm "$PROXY_NETWORK" "$API_EDGE_NETWORK" >/dev/null 2>&1 || true
  [ -z "$WORK" ] || rm -rf "$WORK"
}

# The API of a stack, called from inside its backend network (no port is published).
api() { # api <project> <curl args...>
  local p=$1; shift
  docker run --rm --network "${p}_backend" -v "$WORK:/w:ro" "$CURL_IMAGE" -sS --max-time 30 "$@"
}
api_status() { # api_status <project> <curl args...>
  api "$@" -o /dev/null -w '%{http_code}'
}
sign_in() { # sign_in <project> <email> <password>: prints the access token
  api "$1" -H 'Content-Type: application/json' -d "{\"username\":\"$2\",\"password\":\"$3\"}" \
    http://api:8080/api/v1/auth | jq -r '.accessToken // empty'
}

# The S3 server as seen by its own client (mc), without rclone and without the key.
mc_exec() {
  local cname=$1 user=$2 secret=$3 cmd=$4
  docker exec "$cname" sh -c "mc alias set t http://127.0.0.1:9000 $user $secret >/dev/null && $cmd"
}
s3() { mc_exec "$S3_NAME" "$S3_USER" "$S3_SECRET" "$1"; }
media_s3() { mc_exec "$MEDIA_S3_NAME" "$MEDIA_S3_USER" "$MEDIA_S3_SECRET" "$1"; }
backup2_s3() { mc_exec "$BACKUP2_S3_NAME" "$BACKUP2_S3_USER" "$BACKUP2_S3_SECRET" "$1"; }

start_minio() {
  local cname=$1 net=$2 alias=$3 user=$4 secret=$5 buckets=$6
  docker run -d --name "$cname" --network "$net" --network-alias "$alias" \
    -e MINIO_ROOT_USER="$user" -e MINIO_ROOT_PASSWORD="$secret" \
    -e MINIO_DEFAULT_BUCKETS="$buckets" bitnamilegacy/minio:2025.5.24 >/dev/null || return 1
  local first_bucket=${buckets%%,*}
  for _ in $(seq 1 60); do mc_exec "$cname" "$user" "$secret" "mc ls t/$first_bucket" >/dev/null 2>&1 && return 0; sleep 1; done
  return 1
}

start_s3() { # on the outbound network of stack $1
  start_minio "$S3_NAME" "${1}_egress" "$S3_HOST" "$S3_USER" "$S3_SECRET" "$R2_BUCKET"
}

BACKUP_IMAGE=$REGISTRY/meetstudent-backup:$IMAGE_TAG
build_backup_image() {
  echo "building the backup image $BACKUP_IMAGE from infra/backup"
  docker build -q -t "$BACKUP_IMAGE" "$ROOT/infra/backup" >/dev/null
}

# --- scripts: backup.sh and restore.sh in their image, without a stack ---------

# in_backup <shell command> [docker run options]: runs the command in the backup
# image (busybox sh, like the scripts in production) with a complete configuration,
# no database, and the empty local folder /remote as the off-site storage.
# Prints stdout and stderr together; returns the command's exit status.
in_backup() {
  local cmd=$1; shift
  docker run --rm --entrypoint sh \
    -e PGHOST=db.invalid -e PGUSER=u -e PGPASSWORD=p -e PGDATABASE=d \
    -e RCLONE_CONFIG_R2_ENDPOINT=https://account.eu.r2.cloudflarestorage.com \
    -e RCLONE_CONFIG_R2_ACCESS_KEY_ID=k -e RCLONE_CONFIG_R2_SECRET_ACCESS_KEY=s \
    -e RCLONE_CONFIG_R2CRYPT_REMOTE=r2:bucket -e BACKUP_CRYPT_PASSWORD=pw -e BACKUP_CRYPT_SALT=salt \
    -e BACKUP_REMOTE=/remote/ "$@" "$BACKUP_IMAGE" -c "mkdir /remote && $cmd" 2>&1
}

scripts_tests() {
  build_backup_image || { fail "the backup image builds"; return; }
  echo "scripts: restore.sh against a local storage"
  local out rc
  out=$(in_backup 'restore.sh latest --yes'); rc=$?
  check "restore says so when the storage holds no backup (exit $rc: $out)" \
    bash -c '[ "$0" -eq 1 ] && grep -q "no backup found" <<<"$1"' "$rc" "$out"

  # Two complete backups, then an interrupted one: its files went out, not its
  # SHA256SUMS (backup.sh sends it last). Restoring it would fail at the
  # checksums at best; it must not be offered.
  local seed='for b in 20261025T031500Z 20261026T031500Z; do
      mkdir /remote/$b && echo dump >/remote/$b/db.dump && (cd /remote/$b && sha256sum db.dump >SHA256SUMS); done
    mkdir /remote/20261027T031500Z && echo dump >/remote/20261027T031500Z/db.dump'
  out=$(in_backup "$seed && restore.sh --list" | grep -v NOTICE)
  check "--list shows complete backups only ($(tr '\n' ' ' <<<"$out"))" \
    test "$out" = $'20261025T031500Z\n20261026T031500Z'
  out=$(in_backup "$seed && mkdir -p /tmp/u /tmp/p && RESTORE_UPLOADS_DIR=/tmp/u RESTORE_PRIVATE_DIR=/tmp/p restore.sh latest --yes")
  check "latest is the newest complete backup" grep -q "^downloading /remote/20261026T031500Z$" <<<"$out"

  # ADR 0004, user story 25: the documents stay in the EU. R2's EU-jurisdiction
  # endpoints are <account>.eu.r2.cloudflarestorage.com; anything else is refused.
  local script
  for script in backup.sh 'restore.sh --list'; do
    out=$(in_backup "$script" -e RCLONE_CONFIG_R2_ENDPOINT=https://account.r2.cloudflarestorage.com); rc=$?
    check "${script%% *} refuses an R2 endpoint outside the EU (exit $rc)" \
      bash -c '[ "$0" -eq 2 ] && grep -q "R2_ENDPOINT" <<<"$1"' "$rc" "$out"

    local bad_r2
    for bad_r2 in \
      'ftp://account.eu.r2.cloudflarestorage.com' \
      'https://user:secret@account.eu.r2.cloudflarestorage.com' \
      'https://account.eu.r2.cloudflarestorage.com?foo=bar' \
      'https://account.eu.r2.cloudflarestorage.com#frag' \
      'https://' \
      'https:///eu/path' \
      'https://evil.test/foo.eu.bar'; do
      out=$(in_backup "$script" -e RCLONE_CONFIG_R2_ENDPOINT="$bad_r2"); rc=$?
      check "${script%% *} refuses malformed R2_ENDPOINT '$bad_r2' (exit $rc)" \
        bash -c '[ "$0" -eq 2 ] && grep -q "R2_ENDPOINT" <<<"$1"' "$rc" "$out"
    done
  done

  # A run killed hard (SIGKILL, container restart) skips its cleanup trap and
  # leaves students' documents in /tmp: the next run removes them first.
  out=$(in_backup 'mkdir /tmp/backup.killed && echo document >/tmp/backup.killed/private.tar.gz
    backup.sh; ls -A /tmp')
  check "a backup run removes the local copy a killed run left behind" \
    bash -c '! grep -q "^backup\." <<<"$0"' "$out"

  # Media backend configuration checks (disk vs s3).
  out=$(in_backup 'backup.sh' -e MEDIA_STORAGE_BACKEND=azure); rc=$?
  check "backup.sh refuses an unknown MEDIA_STORAGE_BACKEND (exit $rc)" \
    bash -c '[ "$0" -eq 2 ] && grep -q "MEDIA_STORAGE_BACKEND" <<<"$1"' "$rc" "$out"

  out=$(in_backup 'backup.sh' -e MEDIA_STORAGE_BACKEND=s3 -e MEDIA_S3_BUCKET=b); rc=$?
  check "backup.sh refuses MEDIA_STORAGE_BACKEND=s3 without required MEDIA_S3_* vars (exit $rc)" \
    bash -c '[ "$0" -eq 2 ] && grep -q "MEDIA_S3_REGION" <<<"$1"' "$rc" "$out"

  # Independent EU S3-compatible backup destination (BACKUP_S3_* without R2_*).
  in_backup_s3() {
    local cmd=$1; shift
    docker run --rm --entrypoint sh \
      -e PGHOST=db.invalid -e PGUSER=u -e PGPASSWORD=p -e PGDATABASE=d \
      -e BACKUP_CRYPT_PASSWORD=pw -e BACKUP_CRYPT_SALT=salt \
      -e BACKUP_S3_ENDPOINT=https://s3.fr-par.scw.cloud \
      -e BACKUP_S3_REGION=fr-par \
      -e BACKUP_S3_BUCKET=ms-backups \
      -e BACKUP_S3_ACCESS_KEY_ID=k -e BACKUP_S3_SECRET_ACCESS_KEY=s \
      -e BACKUP_REMOTE=/remote/ "$@" "$BACKUP_IMAGE" -c "mkdir /remote && $cmd" 2>&1
  }
  out=$(in_backup_s3 'restore.sh --list'); rc=$?
  check "BACKUP_S3_* configures an EU S3 destination without requiring R2_* (exit $rc)" \
    test "$rc" -eq 0

  out=$(in_backup_s3 'backup.sh' -e BACKUP_S3_ENDPOINT=https://s3.us-east-1.amazonaws.com -e BACKUP_S3_REGION=us-east-1); rc=$?
  check "backup.sh refuses a non-EU BACKUP_S3_* destination (exit $rc)" \
    bash -c '[ "$0" -eq 2 ] && grep -qi "EU" <<<"$1"' "$rc" "$out"

  # Reject non-EU European regions (eu-west-2 London/UK, eu-central-2 Zurich/CH) for ALL endpoints.
  local non_eu_ep non_eu_reg
  for pair in \
    '|eu-west-2' \
    '|eu-central-2' \
    'https://s3.eu-west-2.custom.test|eu-west-2' \
    'https://s3.eu-central-2.custom.test|eu-central-2' \
    'https://s3.eu-west-2.custom.test|eu-west-1' \
    'https://s3.eu.custom.test|eu-west-2' \
    'https://s3.eu.custom.test|eu-central-2' \
    'https://us-east.example.com/.eu./|custom'; do
    non_eu_ep=${pair%%|*}
    non_eu_reg=${pair#*|}
    out=$(in_backup_s3 'backup.sh' -e BACKUP_S3_ENDPOINT="$non_eu_ep" -e BACKUP_S3_REGION="$non_eu_reg"); rc=$?
    check "backup.sh refuses non-EU European endpoint='$non_eu_ep' region='$non_eu_reg' (exit $rc)" \
      bash -c '[ "$0" -eq 2 ] && grep -qi "EU" <<<"$1"' "$rc" "$out"
  done

  out=$(in_backup_s3 'backup.sh' -e BACKUP_S3_ENDPOINT=https://fsn1.your-objectstorage.com -e BACKUP_S3_REGION=fsn1); rc=$?
  check "backup.sh refuses Hetzner Object Storage as backup destination (same provider as VPS, exit $rc)" \
    bash -c '[ "$0" -eq 2 ] && grep -qi "VPS\|Hetzner\|independent" <<<"$1"' "$rc" "$out"

  # Provider independence between active media S3 and backup destination.
  out=$(in_backup 'backup.sh' \
    -e MEDIA_STORAGE_BACKEND=s3 -e MEDIA_S3_BUCKET=m -e MEDIA_S3_REGION=auto \
    -e MEDIA_S3_ENDPOINT=https://other-acct.eu.r2.cloudflarestorage.com \
    -e MEDIA_S3_ACCESS_KEY_ID=mk -e MEDIA_S3_SECRET_ACCESS_KEY=ms); rc=$?
  check "backup.sh refuses writing backups to R2 when active media also uses R2 (exit $rc)" \
    bash -c '[ "$0" -eq 2 ] && grep -qi "independent" <<<"$1"' "$rc" "$out"

  # Historical R2 archives stay readable via --remote r2crypt: even after active media moved to R2.
  out=$(in_backup_s3 'restore.sh --remote r2crypt: --list' \
    -e RCLONE_CONFIG_R2_ENDPOINT=https://account.eu.r2.cloudflarestorage.com \
    -e RCLONE_CONFIG_R2_ACCESS_KEY_ID=k -e RCLONE_CONFIG_R2_SECRET_ACCESS_KEY=s \
    -e RCLONE_CONFIG_R2CRYPT_REMOTE=r2:bucket \
    -e MEDIA_STORAGE_BACKEND=s3 -e MEDIA_S3_BUCKET=m -e MEDIA_S3_REGION=auto \
    -e MEDIA_S3_ENDPOINT=https://other-acct.eu.r2.cloudflarestorage.com \
    -e MEDIA_S3_ACCESS_KEY_ID=mk -e MEDIA_S3_SECRET_ACCESS_KEY=ms \
    -e BACKUP_REMOTE=); rc=$?
  check "restore.sh accepts --remote r2crypt: to read historical R2 even when active media uses R2" \
    bash -c '[ "$0" -ne 2 ] && ! grep -qi "independent\|usage:" <<<"$1"' "$rc" "$out"

  scripts_pg_tests
}

scripts_pg_tests() {
  echo "scripts: PostgreSQL snapshot, manifest, checksums, locking, and retry"
  SCRIPTS_PG_NET="${TEST_PREFIX}-scripts-net"
  SCRIPTS_PG_NAME="${TEST_PREFIX}-scripts-pg"
  SCRIPTS_PG_WORK=$(mktemp -d "${TMPDIR:-/tmp}/ms-scripts-pg.XXXXXX")
  cleanup_scripts_pg() {
    docker rm -f "${SCRIPTS_PG_NAME:-}" >/dev/null 2>&1 || true
    docker network rm "${SCRIPTS_PG_NET:-}" >/dev/null 2>&1 || true
    [ -z "${SCRIPTS_PG_WORK:-}" ] || rm -rf "$SCRIPTS_PG_WORK"
  }
  trap 'cleanup_scripts_pg; trap - EXIT RETURN' EXIT RETURN
  local pg_net="$SCRIPTS_PG_NET" pg_name="$SCRIPTS_PG_NAME" swork="$SCRIPTS_PG_WORK"

  mkdir -p "$swork/uploads/public/legacy" "$swork/private" "$swork/remote"
  docker network create "$pg_net" >/dev/null
  docker run -d --name "$pg_name" --network "$pg_net" \
    -e POSTGRES_USER=u -e POSTGRES_PASSWORD=p -e POSTGRES_DB=d \
    postgres:18-alpine >/dev/null
  for _ in $(seq 1 60); do
    docker exec "$pg_name" pg_isready -h 127.0.0.1 -U u -d d >/dev/null 2>&1 && break
    sleep 0.5
  done

  docker exec -i "$pg_name" psql -v ON_ERROR_STOP=1 -U u -d d >/dev/null <<'SQL'
CREATE TABLE users (id integer PRIMARY KEY);
INSERT INTO users VALUES (1);
CREATE TABLE media (
    id integer PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY,
    storage_key varchar(512) NOT NULL,
    original_filename varchar(255),
    content_type varchar(128),
    size_bytes bigint,
    category varchar(32) NOT NULL,
    visibility varchar(16) NOT NULL,
    owner_id integer REFERENCES users(id),
    verification_status varchar(16),
    rejection_reason varchar(500),
    idempotency_key varchar(128),
    created_at timestamp(6) without time zone,
    modified_at timestamp(6) without time zone,
    created_by integer,
    modified_by integer
);
SQL

  printf 'private-pdf-bytes' >"$swork/private/doc1.pdf"
  printf 'public-logo-bytes' >"$swork/uploads/public/logo.png"
  printf 'legacy public bytes' >"$swork/uploads/public/legacy/old logo.png"
  printf 'temp-public' >"$swork/uploads/public/.media-inflight.tmp"
  printf 'temp-private' >"$swork/private/.media-inflight.tmp"

  local sz_doc sz_logo sz_legacy sha_doc sha_logo sha_legacy
  sz_doc=$(wc -c <"$swork/private/doc1.pdf" | tr -d ' ')
  sz_logo=$(wc -c <"$swork/uploads/public/logo.png" | tr -d ' ')
  sz_legacy=$(wc -c <"$swork/uploads/public/legacy/old logo.png" | tr -d ' ')
  sha_doc=$(sha "$swork/private/doc1.pdf")
  sha_logo=$(sha "$swork/uploads/public/logo.png")
  sha_legacy=$(sha "$swork/uploads/public/legacy/old logo.png")

  docker exec -i "$pg_name" psql -v ON_ERROR_STOP=1 -U u -d d >/dev/null <<SQL
INSERT INTO media (id, storage_key, original_filename, content_type, size_bytes, category, visibility, owner_id)
VALUES
  (1, 'private/doc1.pdf', 'doc1.pdf', 'application/pdf', $sz_doc, 'DIPLOMA', 'PRIVATE', 1),
  (2, 'public/logo.png', 'logo.png', 'image/png', $sz_logo, 'SCHOOL_LOGO', 'PUBLIC', NULL);
SELECT setval(pg_get_serial_sequence('media', 'id'), 2);
SQL

  run_pg_backup() {
    local cmd=$1; shift
    docker run --rm --network "$pg_net" --entrypoint sh \
      -e PGHOST="$pg_name" -e PGUSER=u -e PGPASSWORD=p -e PGDATABASE=d \
      -e RCLONE_CONFIG_R2_ENDPOINT=https://account.eu.r2.cloudflarestorage.com \
      -e RCLONE_CONFIG_R2_ACCESS_KEY_ID=k -e RCLONE_CONFIG_R2_SECRET_ACCESS_KEY=s \
      -e RCLONE_CONFIG_R2CRYPT_REMOTE=r2:bucket -e BACKUP_CRYPT_PASSWORD=pw -e BACKUP_CRYPT_SALT=salt \
      -e BACKUP_REMOTE=/remote/ \
      -v "$swork/uploads:/data/uploads:ro" \
      -v "$swork/private:/data/private:ro" \
      -v "$swork/remote:/remote" \
      "$@" "$BACKUP_IMAGE" -c "$cmd" 2>&1
  }

  local out rc stamp
  out=$(run_pg_backup 'backup.sh'); rc=$?
  check "backup.sh succeeds with DB inventory + legacy public files + .media-*.tmp ignored (exit $rc)" test "$rc" -eq 0
  stamp=$(run_pg_backup 'restore.sh --list' | grep -v NOTICE | tail -n 1)
  check "backup produced media-manifest.tsv with SHA256SUMS covering it" \
    bash -c '[ -n "$0" ] && [ -f "$1/$0/media-manifest.tsv" ] && grep -q "media-manifest.tsv" "$1/$0/SHA256SUMS"' "$stamp" "$swork/remote"
  local manifest_file="$swork/remote/$stamp/media-manifest.tsv"
  check "media-manifest.tsv records private/doc1.pdf, public/logo.png, and legacy public/legacy/old logo.png with SHA-256" \
    bash -c 'grep -q "$0" "$3" && grep -q "$1" "$3" && grep -q "$2" "$3"' "$sha_doc" "$sha_logo" "$sha_legacy" "$manifest_file"
  check ".media-*.tmp files are excluded from manifest and tarballs" \
    bash -c '! grep -q "\.media-" "$0" && ! tar -tzf "$1/uploads.tar.gz" | grep -q "\.media-" && ! tar -tzf "$1/private.tar.gz" | grep -q "\.media-"' "$manifest_file" "$swork/remote/$stamp"

  # Missing referenced file triggers retry and fails without leaving an incomplete backup listed.
  docker exec "$pg_name" psql -v ON_ERROR_STOP=1 -U u -d d \
    -c "INSERT INTO media (id, storage_key, size_bytes, category, visibility, owner_id) VALUES (3, 'private/missing.pdf', 12, 'DIPLOMA', 'PRIVATE', 1);" >/dev/null
  sleep 1
  out=$(run_pg_backup 'backup.sh' -e BACKUP_MAX_ATTEMPTS=2 -e BACKUP_RETRY_DELAY=1); rc=$?
  check "backup.sh refuses missing DB-referenced file after retries (exit $rc)" \
    bash -c '[ "$0" -ne 0 ] && grep -qi "retrying\|missing\|inconsistent" <<<"$1"' "$rc" "$out"
  local listed_after_fail
  listed_after_fail=$(run_pg_backup 'restore.sh --list' | grep -v NOTICE)
  check "failed backup keeps the previous complete backup as the only usable latest ($stamp)" \
    test "$listed_after_fail" = "$stamp"

  # Transient inconsistency: file absent during attempt 1 appears before attempt 2 -> automatic retry succeeds.
  docker exec "$pg_name" psql -v ON_ERROR_STOP=1 -U u -d d \
    -c "UPDATE media SET storage_key='private/delayed.pdf', size_bytes=13 WHERE id=3;" >/dev/null
  sleep 1
  (sleep 1; printf 'delayed-bytes' >"$swork/private/delayed.pdf") &
  local writer_pid=$!
  out=$(run_pg_backup 'backup.sh' -e BACKUP_MAX_ATTEMPTS=4 -e BACKUP_RETRY_DELAY=1); rc=$?
  wait "$writer_pid" 2>/dev/null || true
  check "backup.sh automatically retries transient inconsistency and succeeds once complete (exit $rc)" \
    bash -c '[ "$0" -eq 0 ] && grep -qi "retry" <<<"$1"' "$rc" "$out"

  # Concurrent backup runs serialize cleanly via lock and produce distinct complete archives.
  local lock_dir="$swork/lock"
  mkdir -p "$lock_dir"
  sleep 1
  out=$(docker run --rm --network "$pg_net" --entrypoint sh \
    -e PGHOST="$pg_name" -e PGUSER=u -e PGPASSWORD=p -e PGDATABASE=d \
    -e RCLONE_CONFIG_R2_ENDPOINT=https://account.eu.r2.cloudflarestorage.com \
    -e RCLONE_CONFIG_R2_ACCESS_KEY_ID=k -e RCLONE_CONFIG_R2_SECRET_ACCESS_KEY=s \
    -e RCLONE_CONFIG_R2CRYPT_REMOTE=r2:bucket -e BACKUP_CRYPT_PASSWORD=pw -e BACKUP_CRYPT_SALT=salt \
    -e BACKUP_REMOTE=/remote/ \
    -v "$swork/uploads:/data/uploads:ro" \
    -v "$swork/private:/data/private:ro" \
    -v "$swork/remote:/remote" \
    "$BACKUP_IMAGE" -c 'backup.sh & p1=$!; backup.sh & p2=$!; wait "$p1" && wait "$p2" && find /tmp -mindepth 1' 2>&1); rc=$?
  local count_after_concurrent
  count_after_concurrent=$(run_pg_backup 'restore.sh --list' | grep -v NOTICE | grep -c .)
  check "concurrent backup runs serialize cleanly and leave distinct complete archives ($count_after_concurrent total, exit $rc)" \
    bash -c '[ "$0" -eq 0 ] && [ "$1" -eq 4 ] && ! grep -q "^/tmp/" <<<"$2"' "$rc" "$count_after_concurrent" "$out"

  # Shared PostgreSQL snapshot: concurrent DB+file churn never desynchronizes db.dump vs media-manifest.tsv.
  sleep 1
  (
    for i in $(seq 1 8); do
      printf 'churn-%s' "$i" >"$swork/private/churn-$i.pdf"
      docker exec "$pg_name" psql -v ON_ERROR_STOP=1 -U u -d d \
        -c "INSERT INTO media (storage_key, size_bytes, category, visibility, owner_id) VALUES ('private/churn-$i.pdf', $(wc -c <"$swork/private/churn-$i.pdf" | tr -d ' '), 'DIPLOMA', 'PRIVATE', 1);" >/dev/null 2>&1 || true
      sleep 0.05
      docker exec "$pg_name" psql -v ON_ERROR_STOP=1 -U u -d d \
        -c "DELETE FROM media WHERE storage_key='private/churn-$i.pdf';" >/dev/null 2>&1 || true
      rm -f "$swork/private/churn-$i.pdf"
    done
  ) &
  local churn_pid=$!
  out=$(run_pg_backup 'backup.sh' -e BACKUP_MAX_ATTEMPTS=5 -e BACKUP_RETRY_DELAY=1); rc=$?
  wait "$churn_pid" 2>/dev/null || true
  local snap_stamp dump_keys manifest_db_keys
  snap_stamp=$(run_pg_backup 'restore.sh --list' | grep -v NOTICE | tail -n 1)
  dump_keys=$(docker run --rm --entrypoint sh -v "$swork/remote:/remote:ro" "$BACKUP_IMAGE" -c \
    "pg_restore --data-only -t media -f - /remote/$snap_stamp/db.dump | awk '/^COPY public\.media /{in_copy=1; next} /^\\\\\./{in_copy=0} in_copy && NF {print \$2}' | sort")
  manifest_db_keys=$(awk -F '\t' '$1=="db" {print $7}' "$swork/remote/$snap_stamp/media-manifest.tsv" | while read -r h; do
    printf '%b\n' "$(printf '%s' "$h" | sed 's/../\\x&/g')"
  done | sort)
  check "db.dump and media-manifest.tsv share a single PostgreSQL snapshot under concurrent churn (exit $rc)" \
    bash -c '[ "$0" -eq 0 ] && [ "$1" = "$2" ]' "$rc" "$dump_keys" "$manifest_db_keys"

  # Restore aborts BEFORE touching DB or files if media-manifest.tsv reports a missing/corrupted file.
  cp -r "$swork/remote/$snap_stamp" "$swork/remote/20261122T235959Z"
  sed -i.bak 's/^\(db\t1\t.*\t\)[0-9a-f]\{64\}\(\t.*\)$/\10000000000000000000000000000000000000000000000000000000000000000\2/' \
    "$swork/remote/20261122T235959Z/media-manifest.tsv"
  rm -f "$swork/remote/20261122T235959Z/media-manifest.tsv.bak"
  (cd "$swork/remote/20261122T235959Z" && sha256sum db.dump uploads.tar.gz private.tar.gz media-manifest.tsv > SHA256SUMS)
  mkdir -p "$swork/restored_u" "$swork/restored_p"
  printf 'keep-me' >"$swork/restored_p/keep.txt"
  out=$(run_pg_backup 'RESTORE_UPLOADS_DIR=/ru RESTORE_PRIVATE_DIR=/rp restore.sh 20261122T235959Z --yes' \
    -v "$swork/restored_u:/ru" -v "$swork/restored_p:/rp"); rc=$?
  check "restore.sh aborts before wiping DB/volumes when media-manifest.tsv checksum fails (exit $rc)" \
    bash -c '[ "$0" -ne 0 ] && [ -f "$1/keep.txt" ] && grep -qi "mismatch" <<<"$2"' "$rc" "$swork/restored_p" "$out"
}

# --- roundtrip: disk & S3 backup/restore + historical archive compatibility ---

STUDENT_EMAIL=student.backup@meetstudent.test
STUDENT_PASSWORD=student-backup-password
OTHER_EMAIL=other.backup@meetstudent.test
OTHER_PASSWORD=other-backup-password
SCHOOL="Backup Test School $$"

verify_restored_media_and_auth() {
  local p=$1 logo_url=$2 photo_url=$3 media_id=$4
  local exp_diploma=$5 exp_logo=$6 exp_photo=$7 exp_legacy=$8
  local student other admin

  student=$(sign_in "$p" "$STUDENT_EMAIL" "$STUDENT_PASSWORD")
  other=$(sign_in "$p" "$OTHER_EMAIL" "$OTHER_PASSWORD")
  admin=$(sign_in "$p" "$ADMIN_EMAIL" "$ADMIN_PASSWORD")
  check "$p: student signs in on restored stack" test -n "$student"
  check "$p: school is present" grep -qF "$SCHOOL" <<<"$(api "$p" "http://api:8080/api/v1/schools/name/Backup%20Test%20School")"

  check "$p: anonymous read of private document is refused (401)" \
    test "$(api_status "$p" "http://api:8080/api/v1/media/$media_id")" = 401
  check "$p: another student is refused private document (403)" \
    test "$(api_status "$p" -H "Authorization: Bearer $other" "http://api:8080/api/v1/media/$media_id")" = 403
  api "$p" -H "Authorization: Bearer $student" -o - "http://api:8080/api/v1/media/$media_id" >"$WORK/check-diploma.pdf"
  check "$p: owner reads private document byte for byte" \
    test "$(sha "$WORK/check-diploma.pdf")" = "$exp_diploma"
  api "$p" -H "Authorization: Bearer $admin" -o - "http://api:8080/api/v1/media/$media_id" >"$WORK/check-diploma-admin.pdf"
  check "$p: admin reads private document byte for byte" \
    test "$(sha "$WORK/check-diploma-admin.pdf")" = "$exp_diploma"

  check "$p: public school logo answers 200 anonymously" \
    test "$(api_status "$p" "http://api:8080$logo_url")" = 200
  api "$p" -o - "http://api:8080$logo_url" >"$WORK/check-logo.png"
  check "$p: public school logo is identical byte for byte" test "$(sha "$WORK/check-logo.png")" = "$exp_logo"

  check "$p: public user photo answers 200 anonymously" \
    test "$(api_status "$p" "http://api:8080$photo_url")" = 200
  api "$p" -o - "http://api:8080$photo_url" >"$WORK/check-photo.png"
  check "$p: public user photo is identical byte for byte" test "$(sha "$WORK/check-photo.png")" = "$exp_photo"

  check "$p: legacy served public path with spaces answers 200 anonymously" \
    test "$(api_status "$p" "http://api:8080/uploads/public/legacy/old%20logo.png")" = 200
  api "$p" -o - "http://api:8080/uploads/public/legacy/old%20logo.png" >"$WORK/check-legacy.png"
  check "$p: legacy public file is identical byte for byte" test "$(sha "$WORK/check-legacy.png")" = "$exp_legacy"

  check "$p: temp write file (.media-inflight.tmp) was not restored (404)" \
    test "$(api_status "$p" "http://api:8080/uploads/public/.media-inflight.tmp")" = 404
}

roundtrip_tests() {
  docker image inspect "$REGISTRY/meetstudent-api:$IMAGE_TAG" >/dev/null 2>&1 || {
    echo "missing local image $REGISTRY/meetstudent-api:$IMAGE_TAG (this test never pulls it)" >&2
    exit 2
  }
  build_backup_image || { fail "the backup image builds"; return; }
  trap teardown EXIT
  teardown
  WORK=$(mktemp -d "${TMPDIR:-/tmp}/meetstudent-backup-test.XXXXXX")
  docker network create "$PROXY_NETWORK" >/dev/null
  docker network create "$API_EDGE_NETWORK" >/dev/null

  echo "populated stack ($PROJECT_A, disk): db, api, backup + S3 on backup's outbound network"
  if ! stack "$PROJECT_A" up -d --pull never --wait --wait-timeout 300 db api backup >/dev/null 2>&1; then
    stack "$PROJECT_A" ps -a
    fail "the stack with its backup service starts"
    return
  fi
  pass "the stack with its backup service starts"
  check "the S3 server answers" start_s3 "$PROJECT_A"

  # Student 1, Student 2, private DIPLOMA, public USER_PHOTO, admin SCHOOL_LOGO + school, legacy public file.
  api "$PROJECT_A" -o /dev/null -H 'Content-Type: application/json' http://api:8080/api/v1/users -d \
    "{\"firstname\":\"Awa\",\"lastname\":\"Backup\",\"email\":\"$STUDENT_EMAIL\",\"password\":\"$STUDENT_PASSWORD\",\"confirmedPassword\":\"$STUDENT_PASSWORD\"}"
  api "$PROJECT_A" -o /dev/null -H 'Content-Type: application/json' http://api:8080/api/v1/users -d \
    "{\"firstname\":\"Moussa\",\"lastname\":\"Other\",\"email\":\"$OTHER_EMAIL\",\"password\":\"$OTHER_PASSWORD\",\"confirmedPassword\":\"$OTHER_PASSWORD\"}"
  local student admin media_id logo_json logo_id logo_url photo_json photo_url school_id
  student=$(sign_in "$PROJECT_A" "$STUDENT_EMAIL" "$STUDENT_PASSWORD")
  admin=$(sign_in "$PROJECT_A" "$ADMIN_EMAIL" "$ADMIN_PASSWORD")

  { printf '%%PDF-1.4\n%% diploma of %s\n' "$STUDENT_EMAIL"; head -c 4096 /dev/urandom; printf '\n%%%%EOF\n'; } >"$WORK/diploma.pdf"
  { printf '\x89PNG\r\n\x1a\nlogo'; head -c 1024 /dev/urandom; } >"$WORK/logo.png"
  { printf '\x89PNG\r\n\x1a\navatar'; head -c 1024 /dev/urandom; } >"$WORK/photo.png"
  { printf '\x89PNG\r\n\x1a\nlegacy'; head -c 1024 /dev/urandom; } >"$WORK/legacy.png"

  media_id=$(api "$PROJECT_A" -H "Authorization: Bearer $student" -F category=DIPLOMA \
    -F 'file=@/w/diploma.pdf;type=application/pdf' http://api:8080/api/v1/media | jq -r '.id // empty')
  photo_json=$(api "$PROJECT_A" -H "Authorization: Bearer $student" -F category=USER_PHOTO \
    -F 'file=@/w/photo.png;type=image/png' http://api:8080/api/v1/media)
  photo_url=$(jq -r '.publicUrl // empty' <<<"$photo_json")
  logo_json=$(api "$PROJECT_A" -H "Authorization: Bearer $admin" -F category=SCHOOL_LOGO \
    -F 'file=@/w/logo.png;type=image/png' http://api:8080/api/v1/media)
  logo_id=$(jq -r '.id // empty' <<<"$logo_json")
  logo_url=$(jq -r '.publicUrl // empty' <<<"$logo_json")
  school_id=$(api "$PROJECT_A" -H "Authorization: Bearer $admin" -H 'Content-Type: application/json' \
    -d "{\"name\":\"$SCHOOL\",\"code\":\"BKP$(( $$ % 100 ))\",\"logoMediaId\":$logo_id}" http://api:8080/api/v1/schools | jq -r '.id // empty')

  # Legacy served public file (no media row) + temp write files that backup must ignore.
  docker run --rm -v "${PROJECT_A}_api-uploads:/u" -v "${PROJECT_A}_api-private:/p" -v "$WORK:/w:ro" \
    postgres:18-alpine sh -c 'mkdir -p /u/public/legacy && cp /w/legacy.png "/u/public/legacy/old logo.png" && echo tmp >/u/public/.media-inflight.tmp && echo tmp >/p/.media-inflight.tmp'

  check "the stack is populated (private $media_id, logo $logo_url, photo $photo_url, school $school_id)" \
    bash -c '[ -n "$0" ] && [ -n "$1" ] && [ -n "$2" ] && [ -n "$3" ] && [ -n "$4" ]' "$student" "$media_id" "$logo_url" "$photo_url" "$school_id"

  local sha_diploma sha_logo sha_photo sha_legacy
  sha_diploma=$(sha "$WORK/diploma.pdf")
  sha_logo=$(sha "$WORK/logo.png")
  sha_photo=$(sha "$WORK/photo.png")
  sha_legacy=$(sha "$WORK/legacy.png")

  echo "backup (disk mode)"
  check "the backup service cannot modify the uploaded files" \
    bash -c '! docker compose -p "$0" -f "$1" exec -T backup sh -c "touch /data/uploads/x || touch /data/private/x"' \
    "$PROJECT_A" "$STACK_FILE"
  local out rc
  out=$(stack "$PROJECT_A" exec -T backup backup.sh 2>&1); rc=$?
  check "a backup run succeeds (exit $rc)" test "$rc" -eq 0
  [ "$rc" -eq 0 ] || printf '%s\n' "$out" | sed 's/^/        /'
  check "the backup leaves no local copy in the backup service" \
    bash -c '[ -z "$(docker compose -p "$0" -f "$1" exec -T backup find /tmp -mindepth 1)" ]' "$PROJECT_A" "$STACK_FILE"

  echo "what reached the off-site storage"
  local objects
  objects=$(s3 "mc ls -r --json t/$R2_BUCKET" | jq -r '.key // empty')
  check "the dump, archives, manifest and checksums were sent ($(grep -c . <<<"$objects") objects)" \
    test "$(grep -c . <<<"$objects")" -ge 4
  check "no object name gives away a date or a file name" \
    bash -c '! grep -Eiq "20[0-9]{6}T|dump|upload|private|manifest|sha256|tar|gz" <<<"$0"' "$objects"
  local readable=0 key
  while read -r key; do
    [ -n "$key" ] || continue
    s3 "mc cat 't/$R2_BUCKET/$key'" >"$WORK/object" 2>/dev/null
    if [ "$(head -c 2 "$WORK/object" | od -An -tx1 | tr -d ' \n')" = 1f8b ] \
      || [ "$(head -c 5 "$WORK/object")" = PGDMP ] || LC_ALL=C grep -aqE '^[0-9a-f]{64}  |# meetstudent-media-manifest' "$WORK/object"; then
      readable=$((readable + 1))
    fi
  done <<<"$objects"
  check "no object is readable in clear ($readable readable)" test "$readable" -eq 0

  echo "an interrupted backup"
  sleep 1
  stack "$PROJECT_A" exec -T -e RCLONE_MAX_TRANSFER=1K -e RCLONE_CUTOFF_MODE=hard -e RCLONE_TRANSFERS=1 \
    backup backup.sh >/dev/null 2>&1; rc=$?
  check "a backup cut off mid-upload fails (exit $rc)" test "$rc" -ne 0
  local listed disk_stamp
  listed=$(stack "$PROJECT_A" exec -T backup restore.sh --list 2>/dev/null)
  disk_stamp=$(tail -n 1 <<<"$listed")
  check "restore.sh --list still offers the complete backup only ($(tr '\n' ' ' <<<"$listed"))" \
    test "$(grep -c . <<<"$listed")" -eq 1

  # Also seed a historical disk-format archive (no media-manifest.tsv, only db.dump + uploads.tar.gz + private.tar.gz + SHA256SUMS).
  local hist_stamp="20260101T000000Z"
  stack "$PROJECT_A" exec -T backup sh -c '
    . /usr/local/bin/backup-env.sh
    w=$(mktemp -d /tmp/hist.XXXXXX)
    pg_dump --format=custom --no-owner --no-privileges --file="$w/db.dump"
    tar --exclude=".media-*.tmp" -czf "$w/uploads.tar.gz" -C /data/uploads .
    tar --exclude=".media-*.tmp" -czf "$w/private.tar.gz" -C /data/private .
    (cd "$w" && sha256sum db.dump uploads.tar.gz private.tar.gz > SHA256SUMS)
    rclone copy "$w" "r2crypt:'"$hist_stamp"'"
    rm -rf "$w"
  ' >/dev/null 2>&1
  check "historical disk-format archive (no manifest) seeded for backwards-compatibility checks" \
    grep -qx "$hist_stamp" <<<"$(stack "$PROJECT_A" exec -T backup restore.sh --list 2>/dev/null)"

  echo "restore new archive into an empty disk stack ($PROJECT_B)"
  stack "$PROJECT_B" up -d --pull never --wait --wait-timeout 120 db backup >/dev/null 2>&1
  docker network connect --alias "$S3_HOST" "${PROJECT_B}_egress" "$S3_NAME"
  out=$(stack "$PROJECT_B" run --rm -T --pull never -v api-uploads:/restore/uploads -v api-private:/restore/private \
    backup restore.sh "$disk_stamp" --yes 2>&1); rc=$?
  check "the latest new archive is restored into empty disk volumes (exit $rc)" test "$rc" -eq 0
  [ "$rc" -eq 0 ] || printf '%s\n' "$out" | sed 's/^/        /'
  if ! stack "$PROJECT_B" up -d --pull never --wait --wait-timeout 300 api >/dev/null 2>&1; then
    fail "the api starts on the restored disk database"
    return
  fi
  pass "the api starts on the restored disk database"
  verify_restored_media_and_auth "$PROJECT_B" "$logo_url" "$photo_url" "$media_id" \
    "$sha_diploma" "$sha_logo" "$sha_photo" "$sha_legacy"

  echo "restore historical archive into empty disk stack ($PROJECT_B)"
  stack "$PROJECT_B" stop api >/dev/null 2>&1
  docker run --rm -v "${PROJECT_B}_api-uploads:/u" -v "${PROJECT_B}_api-private:/p" postgres:18-alpine sh -c 'rm -rf /u/* /p/*'
  out=$(stack "$PROJECT_B" run --rm -T --pull never -v api-uploads:/restore/uploads -v api-private:/restore/private \
    backup restore.sh "$hist_stamp" --yes 2>&1); rc=$?
  check "historical archive restores into empty disk volumes (exit $rc)" test "$rc" -eq 0
  stack "$PROJECT_B" up -d --pull never --wait --wait-timeout 300 api >/dev/null 2>&1
  verify_restored_media_and_auth "$PROJECT_B" "$logo_url" "$photo_url" "$media_id" \
    "$sha_diploma" "$sha_logo" "$sha_photo" "$sha_legacy"

  echo "restore historical archive into an empty S3 destination ($PROJECT_C)"
  local media_bucket_c="meetstudent-media-hist" media_bucket_d="meetstudent-media-restored"
  MEDIA_STORAGE_BACKEND=s3 \
  MEDIA_S3_BUCKET="$media_bucket_c" \
  MEDIA_S3_REGION=us-east-1 \
  MEDIA_S3_ENDPOINT="http://$MEDIA_S3_HOST:9000" \
  MEDIA_S3_PATH_STYLE=true \
  MEDIA_S3_ACCESS_KEY_ID="$MEDIA_S3_USER" \
  MEDIA_S3_SECRET_ACCESS_KEY="$MEDIA_S3_SECRET" \
    stack "$PROJECT_C" up -d --pull never --wait --wait-timeout 120 db backup >/dev/null 2>&1
  check "the active-media S3 server answers" \
    start_minio "$MEDIA_S3_NAME" "${PROJECT_C}_egress" "$MEDIA_S3_HOST" "$MEDIA_S3_USER" "$MEDIA_S3_SECRET" "$media_bucket_c,$media_bucket_d"
  docker network connect --alias "$MEDIA_S3_HOST" "$API_EDGE_NETWORK" "$MEDIA_S3_NAME"
  docker network connect --alias "$S3_HOST" "${PROJECT_C}_egress" "$S3_NAME"
  # Restore into empty S3 WITHOUT mounting /restore/uploads or /restore/private.
  out=$(MEDIA_STORAGE_BACKEND=s3 \
    MEDIA_S3_BUCKET="$media_bucket_c" \
    MEDIA_S3_REGION=us-east-1 \
    MEDIA_S3_ENDPOINT="http://$MEDIA_S3_HOST:9000" \
    MEDIA_S3_PATH_STYLE=true \
    MEDIA_S3_ACCESS_KEY_ID="$MEDIA_S3_USER" \
    MEDIA_S3_SECRET_ACCESS_KEY="$MEDIA_S3_SECRET" \
    stack "$PROJECT_C" run --rm -T --pull never backup restore.sh "$hist_stamp" --yes 2>&1); rc=$?
  check "historical archive restores into empty S3 bucket without disk mounts (exit $rc)" test "$rc" -eq 0
  [ "$rc" -eq 0 ] || printf '%s\n' "$out" | sed 's/^/        /'
  MEDIA_STORAGE_BACKEND=s3 \
  MEDIA_S3_BUCKET="$media_bucket_c" \
  MEDIA_S3_REGION=us-east-1 \
  MEDIA_S3_ENDPOINT="http://$MEDIA_S3_HOST:9000" \
  MEDIA_S3_PATH_STYLE=true \
  MEDIA_S3_ACCESS_KEY_ID="$MEDIA_S3_USER" \
  MEDIA_S3_SECRET_ACCESS_KEY="$MEDIA_S3_SECRET" \
    stack "$PROJECT_C" up -d --pull never --wait --wait-timeout 300 api >/dev/null 2>&1
  verify_restored_media_and_auth "$PROJECT_C" "$logo_url" "$photo_url" "$media_id" \
    "$sha_diploma" "$sha_logo" "$sha_photo" "$sha_legacy"
  local s3_anon_code
  s3_anon_code=$(docker run --rm --network "${PROJECT_C}_egress" "$CURL_IMAGE" -s -o /dev/null -w '%{http_code}' \
    "http://$MEDIA_S3_HOST:9000/$media_bucket_c/public/legacy/old%20logo.png" || true)
  check "the active-media S3 bucket stays private (anonymous direct HTTP returns 403, got $s3_anon_code)" \
    test "$s3_anon_code" = 403

  echo "S3-mode backup to an independent EU S3 destination ($PROJECT_C -> BACKUP_S3_*)"
  check "the independent backup S3 server answers" \
    start_minio "$BACKUP2_S3_NAME" "${PROJECT_C}_egress" "$BACKUP2_S3_HOST" "$BACKUP2_S3_USER" "$BACKUP2_S3_SECRET" "$BACKUP2_BUCKET"
  # Mutate PROJECT_C while running in S3 mode: upload new certificate, replace school logo, add S3 legacy public + temp noise.
  local s3_student s3_admin cert_id logo2_json logo2_id logo2_url
  s3_student=$(sign_in "$PROJECT_C" "$STUDENT_EMAIL" "$STUDENT_PASSWORD")
  s3_admin=$(sign_in "$PROJECT_C" "$ADMIN_EMAIL" "$ADMIN_PASSWORD")
  { printf '%%PDF-1.4\n%% certificate S3\n'; head -c 4096 /dev/urandom; printf '\n%%%%EOF\n'; } >"$WORK/cert.pdf"
  { printf '\x89PNG\r\n\x1a\nlogo2'; head -c 1024 /dev/urandom; } >"$WORK/logo2.png"
  { printf '\x89PNG\r\n\x1a\ns3banner'; head -c 1024 /dev/urandom; } >"$WORK/s3banner.png"
  local sha_cert sha_logo2 sha_s3banner
  sha_cert=$(sha "$WORK/cert.pdf")
  sha_logo2=$(sha "$WORK/logo2.png")
  sha_s3banner=$(sha "$WORK/s3banner.png")

  cert_id=$(api "$PROJECT_C" -H "Authorization: Bearer $s3_student" -F category=CERTIFICATE \
    -F 'file=@/w/cert.pdf;type=application/pdf' http://api:8080/api/v1/media | jq -r '.id // empty')
  logo2_json=$(api "$PROJECT_C" -H "Authorization: Bearer $s3_admin" -F category=SCHOOL_LOGO \
    -F 'file=@/w/logo2.png;type=image/png' http://api:8080/api/v1/media)
  logo2_id=$(jq -r '.id // empty' <<<"$logo2_json")
  logo2_url=$(jq -r '.publicUrl // empty' <<<"$logo2_json")
  api "$PROJECT_C" -o /dev/null -X PATCH -H "Authorization: Bearer $s3_admin" -H 'Content-Type: application/json' \
    -d "{\"logoMediaId\":$logo2_id}" "http://api:8080/api/v1/schools/$school_id"
  docker cp "$WORK/s3banner.png" "$MEDIA_S3_NAME:/tmp/s3banner.png"
  media_s3 "mc cp /tmp/s3banner.png 't/$media_bucket_c/public/legacy/s3 banner.png'" >/dev/null
  media_s3 "mc cp /tmp/s3banner.png 't/$media_bucket_c/public/.media-s3-temp.tmp'" >/dev/null

  out=$(MEDIA_STORAGE_BACKEND=s3 \
    MEDIA_S3_BUCKET="$media_bucket_c" \
    MEDIA_S3_REGION=us-east-1 \
    MEDIA_S3_ENDPOINT="http://$MEDIA_S3_HOST:9000" \
    MEDIA_S3_PATH_STYLE=true \
    MEDIA_S3_ACCESS_KEY_ID="$MEDIA_S3_USER" \
    MEDIA_S3_SECRET_ACCESS_KEY="$MEDIA_S3_SECRET" \
    BACKUP_S3_ENDPOINT="http://$BACKUP2_S3_HOST:9000" \
    BACKUP_S3_REGION=eu-west-1 \
    BACKUP_S3_BUCKET="$BACKUP2_BUCKET" \
    BACKUP_S3_PATH_STYLE=true \
    BACKUP_S3_ACCESS_KEY_ID="$BACKUP2_S3_USER" \
    BACKUP_S3_SECRET_ACCESS_KEY="$BACKUP2_S3_SECRET" \
    stack "$PROJECT_C" run --rm -T --pull never backup backup.sh 2>&1); rc=$?
  check "S3-mode backup to independent EU S3 destination succeeds (exit $rc)" test "$rc" -eq 0
  [ "$rc" -eq 0 ] || printf '%s\n' "$out" | sed 's/^/        /'

  local b2_objects s3_stamp
  b2_objects=$(backup2_s3 "mc ls -r --json t/$BACKUP2_BUCKET" | jq -r '.key // empty')
  check "independent backup bucket received encrypted objects ($(grep -c . <<<"$b2_objects") objects)" \
    test "$(grep -c . <<<"$b2_objects")" -ge 4
  s3_stamp=$(BACKUP_S3_ENDPOINT="http://$BACKUP2_S3_HOST:9000" \
    BACKUP_S3_REGION=eu-west-1 BACKUP_S3_BUCKET="$BACKUP2_BUCKET" BACKUP_S3_PATH_STYLE=true \
    BACKUP_S3_ACCESS_KEY_ID="$BACKUP2_S3_USER" BACKUP_S3_SECRET_ACCESS_KEY="$BACKUP2_S3_SECRET" \
    stack "$PROJECT_C" run --rm -T --pull never backup restore.sh --list 2>/dev/null | grep -v NOTICE | tail -n 1)
  check "historical R2 archives remain listable alongside independent BACKUP_S3_*" \
    grep -qx "$hist_stamp" <<<"$(BACKUP_S3_ENDPOINT="http://$BACKUP2_S3_HOST:9000" \
      BACKUP_S3_REGION=eu-west-1 BACKUP_S3_BUCKET="$BACKUP2_BUCKET" BACKUP_S3_PATH_STYLE=true \
      BACKUP_S3_ACCESS_KEY_ID="$BACKUP2_S3_USER" BACKUP_S3_SECRET_ACCESS_KEY="$BACKUP2_S3_SECRET" \
      stack "$PROJECT_C" run --rm -T --pull never backup restore.sh --remote r2crypt: --list 2>/dev/null)"

  # Deleting an active S3 object causes S3-mode backup to fail after retries while keeping previous complete backup intact.
  local cert_s3_key
  cert_s3_key=$(media_s3 "mc ls -r --json t/$media_bucket_c/private/" | jq -r '.key // empty' | tail -n 1)
  local del_target="t/$media_bucket_c/private/${cert_s3_key##*/}"
  media_s3 "mc rm '$del_target'" >/dev/null
  sleep 1
  MEDIA_STORAGE_BACKEND=s3 MEDIA_S3_BUCKET="$media_bucket_c" MEDIA_S3_REGION=us-east-1 \
  MEDIA_S3_ENDPOINT="http://$MEDIA_S3_HOST:9000" MEDIA_S3_PATH_STYLE=true \
  MEDIA_S3_ACCESS_KEY_ID="$MEDIA_S3_USER" MEDIA_S3_SECRET_ACCESS_KEY="$MEDIA_S3_SECRET" \
  BACKUP_S3_ENDPOINT="http://$BACKUP2_S3_HOST:9000" BACKUP_S3_REGION=eu-west-1 \
  BACKUP_S3_BUCKET="$BACKUP2_BUCKET" BACKUP_S3_PATH_STYLE=true \
  BACKUP_S3_ACCESS_KEY_ID="$BACKUP2_S3_USER" BACKUP_S3_SECRET_ACCESS_KEY="$BACKUP2_S3_SECRET" \
    stack "$PROJECT_C" run --rm -T --pull never -e BACKUP_MAX_ATTEMPTS=2 -e BACKUP_RETRY_DELAY=1 \
    backup backup.sh >/dev/null 2>&1; rc=$?
  check "S3-mode backup detects missing S3 object for DB row and fails without publishing incomplete backup (exit $rc)" \
    bash -c '[ -n "$0" ] && [ "$1" -ne 0 ]' "$cert_s3_key" "$rc"

  echo "restore S3-mode backup into fresh empty S3 destination ($PROJECT_D)"
  MEDIA_STORAGE_BACKEND=s3 MEDIA_S3_BUCKET="$media_bucket_d" MEDIA_S3_REGION=us-east-1 \
  MEDIA_S3_ENDPOINT="http://$MEDIA_S3_HOST:9000" MEDIA_S3_PATH_STYLE=true \
  MEDIA_S3_ACCESS_KEY_ID="$MEDIA_S3_USER" MEDIA_S3_SECRET_ACCESS_KEY="$MEDIA_S3_SECRET" \
  BACKUP_S3_ENDPOINT="http://$BACKUP2_S3_HOST:9000" BACKUP_S3_REGION=eu-west-1 \
  BACKUP_S3_BUCKET="$BACKUP2_BUCKET" BACKUP_S3_PATH_STYLE=true \
  BACKUP_S3_ACCESS_KEY_ID="$BACKUP2_S3_USER" BACKUP_S3_SECRET_ACCESS_KEY="$BACKUP2_S3_SECRET" \
    stack "$PROJECT_D" up -d --pull never --wait --wait-timeout 120 db backup >/dev/null 2>&1
  docker network connect --alias "$MEDIA_S3_HOST" "${PROJECT_D}_egress" "$MEDIA_S3_NAME"
  docker network connect --alias "$BACKUP2_S3_HOST" "${PROJECT_D}_egress" "$BACKUP2_S3_NAME"
  out=$(MEDIA_STORAGE_BACKEND=s3 MEDIA_S3_BUCKET="$media_bucket_d" MEDIA_S3_REGION=us-east-1 \
    MEDIA_S3_ENDPOINT="http://$MEDIA_S3_HOST:9000" MEDIA_S3_PATH_STYLE=true \
    MEDIA_S3_ACCESS_KEY_ID="$MEDIA_S3_USER" MEDIA_S3_SECRET_ACCESS_KEY="$MEDIA_S3_SECRET" \
    BACKUP_S3_ENDPOINT="http://$BACKUP2_S3_HOST:9000" BACKUP_S3_REGION=eu-west-1 \
    BACKUP_S3_BUCKET="$BACKUP2_BUCKET" BACKUP_S3_PATH_STYLE=true \
    BACKUP_S3_ACCESS_KEY_ID="$BACKUP2_S3_USER" BACKUP_S3_SECRET_ACCESS_KEY="$BACKUP2_S3_SECRET" \
    stack "$PROJECT_D" run --rm -T --pull never backup restore.sh "$s3_stamp" --yes 2>&1); rc=$?
  check "S3-mode backup restores into empty S3 bucket (exit $rc)" test "$rc" -eq 0
  [ "$rc" -eq 0 ] || printf '%s\n' "$out" | sed 's/^/        /'
  MEDIA_STORAGE_BACKEND=s3 MEDIA_S3_BUCKET="$media_bucket_d" MEDIA_S3_REGION=us-east-1 \
  MEDIA_S3_ENDPOINT="http://$MEDIA_S3_HOST:9000" MEDIA_S3_PATH_STYLE=true \
  MEDIA_S3_ACCESS_KEY_ID="$MEDIA_S3_USER" MEDIA_S3_SECRET_ACCESS_KEY="$MEDIA_S3_SECRET" \
    stack "$PROJECT_D" up -d --pull never --wait --wait-timeout 300 api >/dev/null 2>&1

  verify_restored_media_and_auth "$PROJECT_D" "$logo2_url" "$photo_url" "$media_id" \
    "$sha_diploma" "$sha_logo2" "$sha_photo" "$sha_legacy"
  local d_student
  d_student=$(sign_in "$PROJECT_D" "$STUDENT_EMAIL" "$STUDENT_PASSWORD")
  api "$PROJECT_D" -H "Authorization: Bearer $d_student" -o - "http://api:8080/api/v1/media/$cert_id" >"$WORK/check-cert.pdf"
  check "$PROJECT_D: new S3-uploaded private certificate is identical byte for byte" \
    test "$(sha "$WORK/check-cert.pdf")" = "$sha_cert"
  api "$PROJECT_D" -o - "http://api:8080/uploads/public/legacy/s3%20banner.png" >"$WORK/check-s3banner.png"
  check "$PROJECT_D: S3 legacy public file is identical byte for byte" \
    test "$(sha "$WORK/check-s3banner.png")" = "$sha_s3banner"
  check "$PROJECT_D: S3 temp file (.media-s3-temp.tmp) was ignored (404)" \
    test "$(api_status "$PROJECT_D" "http://api:8080/uploads/public/.media-s3-temp.tmp")" = 404

  echo "S3 restore pre-validates storage keys BEFORE dropping existing DB ($PROJECT_D)"
  stack "$PROJECT_D" exec -T db psql -v ON_ERROR_STOP=1 -U meetstudent -d meetstudent \
    -c "CREATE TABLE IF NOT EXISTS restore_sentinel (val text); TRUNCATE restore_sentinel; INSERT INTO restore_sentinel VALUES ('must-survive');" >/dev/null
  local bad_hist_stamp="20260102T000000Z"
  stack "$PROJECT_D" exec -T \
    -e BACKUP_S3_ENDPOINT="http://$BACKUP2_S3_HOST:9000" \
    -e BACKUP_S3_REGION=eu-west-1 \
    -e BACKUP_S3_BUCKET="$BACKUP2_BUCKET" \
    -e BACKUP_S3_PATH_STYLE=true \
    -e BACKUP_S3_ACCESS_KEY_ID="$BACKUP2_S3_USER" \
    -e BACKUP_S3_SECRET_ACCESS_KEY="$BACKUP2_S3_SECRET" \
    backup sh -c '
      . /usr/local/bin/backup-env.sh
      w=$(mktemp -d /tmp/badhist.XXXXXX)
      trap "rm -rf $w" EXIT
      psql -v ON_ERROR_STOP=1 -d postgres -c "DROP DATABASE IF EXISTS scratch_legacy WITH (FORCE);" -c "CREATE DATABASE scratch_legacy OWNER \"$PGUSER\";" >/dev/null
      psql -v ON_ERROR_STOP=1 -d scratch_legacy -c "CREATE TABLE media (id integer PRIMARY KEY, storage_key varchar(512) NOT NULL, category varchar(32) NOT NULL, visibility varchar(16) NOT NULL); INSERT INTO media VALUES (1, '\''users/legacy-pre-s3.pdf'\'', '\''DIPLOMA'\'', '\''PRIVATE'\'');" >/dev/null
      pg_dump --format=custom --no-owner --no-privileges -d scratch_legacy --file="$w/db.dump"
      psql -v ON_ERROR_STOP=1 -d postgres -c "DROP DATABASE IF EXISTS scratch_legacy WITH (FORCE);" >/dev/null
      mkdir -p "$w/u/users" "$w/p"
      printf "legacy" > "$w/u/users/legacy-pre-s3.pdf"
      tar -czf "$w/uploads.tar.gz" -C "$w/u" .
      tar -czf "$w/private.tar.gz" -C "$w/p" .
      (cd "$w" && sha256sum db.dump uploads.tar.gz private.tar.gz > SHA256SUMS)
      rclone copy "$w" "backupcrypt:'"$bad_hist_stamp"'"
    ' >/dev/null 2>&1
  stack "$PROJECT_D" stop api >/dev/null 2>&1
  out=$(MEDIA_STORAGE_BACKEND=s3 MEDIA_S3_BUCKET="$media_bucket_d" MEDIA_S3_REGION=us-east-1 \
    MEDIA_S3_ENDPOINT="http://$MEDIA_S3_HOST:9000" MEDIA_S3_PATH_STYLE=true \
    MEDIA_S3_ACCESS_KEY_ID="$MEDIA_S3_USER" MEDIA_S3_SECRET_ACCESS_KEY="$MEDIA_S3_SECRET" \
    BACKUP_S3_ENDPOINT="http://$BACKUP2_S3_HOST:9000" BACKUP_S3_REGION=eu-west-1 \
    BACKUP_S3_BUCKET="$BACKUP2_BUCKET" BACKUP_S3_PATH_STYLE=true \
    BACKUP_S3_ACCESS_KEY_ID="$BACKUP2_S3_USER" BACKUP_S3_SECRET_ACCESS_KEY="$BACKUP2_S3_SECRET" \
    stack "$PROJECT_D" run --rm -T --pull never backup restore.sh "$bad_hist_stamp" --yes 2>&1); rc=$?
  local sentinel_after
  sentinel_after=$(stack "$PROJECT_D" exec -T db psql -tAc "SELECT val FROM restore_sentinel;" -U meetstudent -d meetstudent 2>/dev/null || true)
  check "incompatible historical archive fails before dropping existing DB in S3 mode (exit $rc, sentinel='$sentinel_after')" \
    bash -c '[ "$0" -ne 0 ] && [ "$1" = "must-survive" ] && grep -qi "private/\|public/\|legacy" <<<"$2"' "$rc" "$sentinel_after" "$out"

  echo "failures are loud"
  stack "$PROJECT_B" run --rm -T --pull never -e BACKUP_CRYPT_PASSWORD=not-the-key \
    -v api-uploads:/restore/uploads -v api-private:/restore/private backup restore.sh latest --yes >/dev/null 2>&1; rc=$?
  check "without the crypt key the backup cannot be restored (exit $rc)" test "$rc" -ne 0
  stack "$PROJECT_A" exec -T -e BACKUP_CRYPT_SALT= backup backup.sh >/dev/null 2>&1; rc=$?
  check "an incomplete configuration fails the backup (exit $rc)" test "$rc" -ne 0
  docker stop "$S3_NAME" >/dev/null
  stack "$PROJECT_A" exec -T backup backup.sh >/dev/null 2>&1; rc=$?
  check "an unreachable S3 fails the backup (exit $rc)" test "$rc" -ne 0
  check "a failed backup leaves no local copy either" \
    bash -c '[ -z "$(docker compose -p "$0" -f "$1" exec -T backup find /tmp -mindepth 1)" ]' "$PROJECT_A" "$STACK_FILE"
}

case ${1:-all} in
  scripts) scripts_tests ;;
  roundtrip) roundtrip_tests ;;
  all) scripts_tests; roundtrip_tests ;;
  *) echo "usage: $0 [scripts|roundtrip]" >&2; exit 2 ;;
esac

if [ "$failures" -gt 0 ]; then echo "$failures failure(s)"; exit 1; fi
echo "all passed"
