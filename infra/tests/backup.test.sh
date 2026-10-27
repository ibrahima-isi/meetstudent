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
export PROXY_NETWORK=${TEST_PROXY_NETWORK:-meetstudent-backup-test-proxy}

# The off-site storage: MinIO instead of R2, same S3 protocol.
S3_NAME=meetstudent-backup-test-s3
# Named like an EU endpoint (".eu."): the scripts refuse any other R2_ENDPOINT.
S3_HOST=s3.eu.backup.test
S3_USER=backup-test-access-key
S3_SECRET=backup-test-secret-key
export R2_ENDPOINT=http://$S3_HOST:9000
export R2_BUCKET=meetstudent-backups
export R2_ACCESS_KEY_ID=$S3_USER
export R2_SECRET_ACCESS_KEY=$S3_SECRET
# The originals, as kept in the owner's password manager.
export BACKUP_CRYPT_PASSWORD=backup-test-crypt-password
export BACKUP_CRYPT_SALT=backup-test-crypt-salt

PROJECT_A=meetstudent-backup-test-a # the populated stack that is backed up
PROJECT_B=meetstudent-backup-test-b # the empty stack it is restored into
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
  stack "$PROJECT_A" down -v --remove-orphans >/dev/null 2>&1 || true
  stack "$PROJECT_B" down -v --remove-orphans >/dev/null 2>&1 || true
  docker rm -f "$S3_NAME" >/dev/null 2>&1 || true
  docker network rm "$PROXY_NETWORK" >/dev/null 2>&1 || true
  [ -z "$WORK" ] || rm -rf "$WORK"
}

# The API of a stack, called from inside its backend network (no port is published).
api() { # api <project> <curl args...>
  local p=$1; shift
  docker run --rm --network "${p}_backend" -v "$WORK:/w:ro" "$CURL_IMAGE" -sS --max-time 30 "$@"
}
sign_in() { # sign_in <project> <email> <password>: prints the access token
  api "$1" -H 'Content-Type: application/json' -d "{\"username\":\"$2\",\"password\":\"$3\"}" \
    http://api:8080/api/v1/auth | jq -r '.accessToken // empty'
}

# The S3 server as seen by its own client (mc), without rclone and without the key.
s3() { docker exec "$S3_NAME" sh -c "mc alias set t http://127.0.0.1:9000 $S3_USER $S3_SECRET >/dev/null && $1"; }

start_s3() { # on the outbound network of stack $1
  docker run -d --name "$S3_NAME" --network "${1}_egress" --network-alias "$S3_HOST" \
    -e MINIO_ROOT_USER="$S3_USER" -e MINIO_ROOT_PASSWORD="$S3_SECRET" \
    -e MINIO_DEFAULT_BUCKETS="$R2_BUCKET" bitnamilegacy/minio:2025.5.24 >/dev/null || return 1
  for _ in $(seq 1 60); do s3 "mc ls t/$R2_BUCKET" >/dev/null 2>&1 && return 0; sleep 1; done
  return 1
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
  done

  # A run killed hard (SIGKILL, container restart) skips its cleanup trap and
  # leaves students' documents in /tmp: the next run removes them first.
  out=$(in_backup 'mkdir /tmp/backup.killed && echo document >/tmp/backup.killed/private.tar.gz
    backup.sh; ls -A /tmp')
  check "a backup run removes the local copy a killed run left behind" \
    bash -c '! grep -q "^backup\." <<<"$0"' "$out"
}

# --- roundtrip: a populated stack, MinIO as R2, a restore into an empty stack ---

STUDENT_EMAIL=student.backup@meetstudent.test
STUDENT_PASSWORD=student-backup-password
SCHOOL="Backup Test School $$"

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

  echo "populated stack ($PROJECT_A): db, api, backup + S3 on the backup's outbound network"
  if ! stack "$PROJECT_A" up -d --pull never --wait --wait-timeout 300 db api backup >/dev/null 2>&1; then
    stack "$PROJECT_A" ps -a
    fail "the stack with its backup service starts"
    return
  fi
  pass "the stack with its backup service starts"
  check "the S3 server answers" start_s3 "$PROJECT_A"

  # A student, a private document of hers, a school created by the admin.
  api "$PROJECT_A" -o /dev/null -H 'Content-Type: application/json' http://api:8080/api/v1/users -d \
    "{\"firstname\":\"Awa\",\"lastname\":\"Backup\",\"email\":\"$STUDENT_EMAIL\",\"password\":\"$STUDENT_PASSWORD\",\"confirmedPassword\":\"$STUDENT_PASSWORD\"}"
  local student admin media_id
  student=$(sign_in "$PROJECT_A" "$STUDENT_EMAIL" "$STUDENT_PASSWORD")
  { printf '%%PDF-1.4\n%% diploma of %s\n' "$STUDENT_EMAIL"; head -c 4096 /dev/urandom; printf '\n%%%%EOF\n'; } >"$WORK/diploma.pdf"
  media_id=$(api "$PROJECT_A" -H "Authorization: Bearer $student" -F category=DIPLOMA \
    -F 'file=@/w/diploma.pdf;type=application/pdf' http://api:8080/api/v1/media | jq -r '.id // empty')
  admin=$(sign_in "$PROJECT_A" "$ADMIN_EMAIL" "$ADMIN_PASSWORD")
  api "$PROJECT_A" -o /dev/null -H "Authorization: Bearer $admin" -H 'Content-Type: application/json' \
    -d "{\"name\":\"$SCHOOL\",\"code\":\"BKP$(( $$ % 100 ))\"}" http://api:8080/api/v1/schools
  check "the stack is populated (student, private document $media_id, school)" \
    bash -c '[ -n "$0" ] && [ -n "$1" ] && [ -n "$2" ]' "$student" "$media_id" "$admin"

  echo "backup"
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
  check "the database dump and both upload archives were sent ($(grep -c . <<<"$objects") objects)" \
    test "$(grep -c . <<<"$objects")" -ge 3
  check "no object name gives away a date or a file name" \
    bash -c '! grep -Eiq "20[0-9]{6}T|dump|upload|private|sha256|tar|gz" <<<"$0"' "$objects"
  local readable=0 key
  while read -r key; do
    [ -n "$key" ] || continue
    s3 "mc cat 't/$R2_BUCKET/$key'" >"$WORK/object" 2>/dev/null
    # Neither a gzip archive, nor a Postgres dump, nor a text checksum list.
    if [ "$(head -c 2 "$WORK/object" | od -An -tx1 | tr -d ' \n')" = 1f8b ] \
      || [ "$(head -c 5 "$WORK/object")" = PGDMP ] || LC_ALL=C grep -aqE '^[0-9a-f]{64}  ' "$WORK/object"; then
      readable=$((readable + 1))
    fi
  done <<<"$objects"
  check "no object is readable in clear ($readable readable)" test "$readable" -eq 0

  echo "an interrupted backup"
  # The connection drops after 1 KiB (rclone's own cut-off, one file at a time):
  # some files of the backup are sent, not all. backup.sh sends SHA256SUMS last,
  # so this folder never looks complete, and the restore below must skip it.
  sleep 1
  stack "$PROJECT_A" exec -T -e RCLONE_MAX_TRANSFER=1K -e RCLONE_CUTOFF_MODE=hard -e RCLONE_TRANSFERS=1 \
    backup backup.sh >/dev/null 2>&1; rc=$?
  check "a backup cut off mid-upload fails (exit $rc)" test "$rc" -ne 0
  local listed
  listed=$(stack "$PROJECT_A" exec -T backup restore.sh --list 2>/dev/null)
  check "restore.sh --list still offers the complete backup only ($(tr '\n' ' ' <<<"$listed"))" \
    test "$(grep -c . <<<"$listed")" -eq 1

  echo "restore into an empty stack ($PROJECT_B)"
  stack "$PROJECT_B" up -d --pull never --wait --wait-timeout 120 db backup >/dev/null 2>&1
  docker network connect --alias "$S3_HOST" "${PROJECT_B}_egress" "$S3_NAME"
  # The backup service mounts the volumes read-only: a restore is a one-off run
  # of the same service with the two volumes mounted writable under /restore.
  out=$(stack "$PROJECT_B" run --rm -T --pull never -v api-uploads:/restore/uploads -v api-private:/restore/private \
    backup restore.sh latest --yes 2>&1); rc=$?
  check "the latest backup is restored (exit $rc)" test "$rc" -eq 0
  [ "$rc" -eq 0 ] || printf '%s\n' "$out" | sed 's/^/        /'
  if ! stack "$PROJECT_B" up -d --pull never --wait --wait-timeout 300 api >/dev/null 2>&1; then
    fail "the api starts on the restored database"
    return
  fi
  pass "the api starts on the restored database"
  student=$(sign_in "$PROJECT_B" "$STUDENT_EMAIL" "$STUDENT_PASSWORD")
  check "the student signs in to the restored stack" test -n "$student"
  check "the school is there" grep -qF "$SCHOOL" <<<"$(api "$PROJECT_B" "http://api:8080/api/v1/schools/name/Backup%20Test%20School")"
  api "$PROJECT_B" -H "Authorization: Bearer $student" -o - "http://api:8080/api/v1/media/$media_id" >"$WORK/restored.pdf"
  check "the private document is identical byte for byte" test "$(sha "$WORK/restored.pdf")" = "$(sha "$WORK/diploma.pdf")"

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
