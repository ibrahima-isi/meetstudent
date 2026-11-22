#!/usr/bin/env bash
# End-to-end rehearsal and verification test suite for Ticket portable-media-storage/04.
# Exercises real PostgreSQL 18 + real meetstudent-api + two isolated MinIO instances:
#   1. Inventory (PostgreSQL media rows + legacy public/**, ignoring .media-*.tmp & symlinks)
#   2. Pre-copy checks (legacy private paths, invalid keys, missing files) + S3 non-rewrite check
#   3. Repeatable Disk -> S3 copy, pause/drain (HTTP + PG tx + deferred deletes), cutover & abort paths
#   4. Post-reopen Rollback (Reverse Sync S3 -> Disk reconciling additions, modifications, deletions)
#   5. Repeatable S3 -> S3 migration & post-reopen reverse sync (MinIO A <-> MinIO B)
#
# Uses unique ms-t04-* names and ephemeral loopback ports (55434, 59004, 59005, 58084).
# Always tears down containers, volumes, network, and temp files on EXIT.
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
TOOL="$ROOT/infra/media/media_transfer.py"
JAR_DEFAULT="$ROOT/apps/api/target/meetstudent.jar"
API_JAR=${API_JAR:-$JAR_DEFAULT}

PREFIX="ms-t04"
NET="${PREFIX}-net"
PG_CONT="${PREFIX}-pg"
MINIO_A_CONT="${PREFIX}-minio-a"
MINIO_B_CONT="${PREFIX}-minio-b"
API_CONT="${PREFIX}-api"
VOL_UPLOADS="${PREFIX}-uploads"
VOL_PRIVATE="${PREFIX}-private"

PG_PORT=${PG_PORT:-55434}
MINIO_A_PORT=${MINIO_A_PORT:-59004}
MINIO_B_PORT=${MINIO_B_PORT:-59005}
API_PORT=${API_PORT:-58084}

PG_DB=meetstudent
PG_USER=meetstudent
PG_PASS=ms-t04-pg-password
JWT_SECRET=bXktdDA0LXZlcnktbG9uZy1yYW5kb20tand0LXNlY3JldC1rZXktZm9yLWlzb2xhdGVkLW1lZGlhLXRlc3RzLTEyMzQ1
ADMIN_EMAIL=admin@meetstudent.test
ADMIN_PASS=AdminTestPass123!

S3_A_BUCKET=meetstudent-media-a
S3_A_KEY=minio-a-access
S3_A_SECRET=minio-a-secret-key

S3_B_BUCKET=meetstudent-media-b
S3_B_KEY=minio-b-access
S3_B_SECRET=minio-b-secret-key

WORK=""
failures=0

pass() { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
check() {
  local name=$1; shift
  if "$@" >/dev/null 2>&1; then pass "$name"; else fail "$name"; fi
}
sha() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1
}

teardown() {
  echo "teardown ($PREFIX)"
  docker rm -f "$API_CONT" "$PG_CONT" "$MINIO_A_CONT" "$MINIO_B_CONT" >/dev/null 2>&1 || true
  docker volume rm -f "$VOL_UPLOADS" "$VOL_PRIVATE" >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
  [ -z "$WORK" ] || rm -rf "$WORK"
}
trap teardown EXIT

pg_exec() {
  docker exec -i "$PG_CONT" psql -v ON_ERROR_STOP=1 -U "$PG_USER" -d "$PG_DB" "$@"
}

transfer() {
  MEDIA_TRANSFER_PSQL_CMD="docker exec -i $PG_CONT psql -U $PG_USER -d $PG_DB" \
    python3 "$TOOL" "$@"
}

wait_minio() {
  local port=$1
  for _ in $(seq 1 40); do
    curl -fsS "http://127.0.0.1:$port/minio/health/ready" >/dev/null 2>&1 && return 0
    sleep 1
  done
  return 1
}

wait_api() {
  for _ in $(seq 1 90); do
    curl -fsS "http://127.0.0.1:$API_PORT/api/v1/schools" >/dev/null 2>&1 && return 0
    sleep 1
  done
  docker logs --tail 40 "$API_CONT" >&2 || true
  return 1
}

start_api() {
  local backend=$1 paused=$2 bucket=${3:-} region=${4:-} endpoint=${5:-} ak=${6:-} sk=${7:-}
  docker stop -t 35 "$API_CONT" >/dev/null 2>&1 || true
  docker rm -f "$API_CONT" >/dev/null 2>&1 || true
  docker run -d --name "$API_CONT" --network "$NET" -p "127.0.0.1:$API_PORT:8080" \
    -v "$API_JAR:/app/app.jar:ro" \
    -v "$VOL_UPLOADS:/data/uploads" \
    -v "$VOL_PRIVATE:/data/private" \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e SPRING_DATASOURCE_URL="jdbc:postgresql://$PG_CONT:5432/$PG_DB" \
    -e SPRING_DATASOURCE_USERNAME="$PG_USER" \
    -e SPRING_DATASOURCE_PASSWORD="$PG_PASS" \
    -e JWT_SECRET_KEY="$JWT_SECRET" \
    -e CORS_ALLOWED_ORIGINS="https://site.meetstudent.test" \
    -e ADMIN_EMAIL="$ADMIN_EMAIL" \
    -e ADMIN_PASSWORD="$ADMIN_PASS" \
    -e FILE_UPLOAD_DIR=/data/uploads \
    -e FILE_PRIVATE_DIR=/data/private \
    -e MEDIA_STORAGE_BACKEND="$backend" \
    -e MEDIA_WRITES_PAUSED="$paused" \
    -e MEDIA_S3_BUCKET="$bucket" \
    -e MEDIA_S3_REGION="$region" \
    -e MEDIA_S3_ENDPOINT="$endpoint" \
    -e MEDIA_S3_PATH_STYLE=true \
    -e MEDIA_S3_ACCESS_KEY_ID="$ak" \
    -e MEDIA_S3_SECRET_ACCESS_KEY="$sk" \
    eclipse-temurin:21-jre-alpine java -jar /app/app.jar >/dev/null
  wait_api
}

api_status() {
  curl -sS -o /dev/null -w '%{http_code}' "$@"
}

api_header() {
  local hdr=$1; shift
  curl -sS -D - -o /dev/null "$@" | tr -d '\r' | grep -i "^$hdr:" | head -1 | cut -d' ' -f2-
}

sign_in() {
  local email=$1 pass=$2
  curl -fsS -H 'Content-Type: application/json' \
    -d "{\"username\":\"$email\",\"password\":\"$pass\"}" \
    "http://127.0.0.1:$API_PORT/api/v1/auth" | jq -r '.accessToken'
}

SRC_DISK_ARGS=(--source-type docker-volume --source-upload-volume "$VOL_UPLOADS" --source-private-volume "$VOL_PRIVATE")
DST_DISK_ARGS=(--target-type docker-volume --target-upload-volume "$VOL_UPLOADS" --target-private-volume "$VOL_PRIVATE")
SRC_S3A_ARGS=(--source-type s3 --source-s3-bucket "$S3_A_BUCKET" --source-s3-region us-east-1 --source-s3-endpoint "http://127.0.0.1:$MINIO_A_PORT" --source-s3-path-style --source-s3-access-key "$S3_A_KEY" --source-s3-secret-key "$S3_A_SECRET")
DST_S3A_ARGS=(--target-type s3 --target-s3-bucket "$S3_A_BUCKET" --target-s3-region us-east-1 --target-s3-endpoint "http://127.0.0.1:$MINIO_A_PORT" --target-s3-path-style --target-s3-access-key "$S3_A_KEY" --target-s3-secret-key "$S3_A_SECRET")
SRC_S3B_ARGS=(--source-type s3 --source-s3-bucket "$S3_B_BUCKET" --source-s3-region eu-west-1 --source-s3-endpoint "http://127.0.0.1:$MINIO_B_PORT" --source-s3-path-style --source-s3-access-key "$S3_B_KEY" --source-s3-secret-key "$S3_B_SECRET")
DST_S3B_ARGS=(--target-type s3 --target-s3-bucket "$S3_B_BUCKET" --target-s3-region eu-west-1 --target-s3-endpoint "http://127.0.0.1:$MINIO_B_PORT" --target-s3-path-style --target-s3-access-key "$S3_B_KEY" --target-s3-secret-key "$S3_B_SECRET")
API_ARGS=(--api-url "http://127.0.0.1:$API_PORT" --api-admin-email "$ADMIN_EMAIL" --api-admin-password "$ADMIN_PASS")

main() {
  if [ ! -f "$API_JAR" ]; then
    echo "API jar not found at $API_JAR; build it first with ./mvnw package -DskipTests" >&2
    exit 2
  fi

  teardown
  WORK=$(mktemp -d "${TMPDIR:-/tmp}/ms-t04-work.XXXXXX")
  docker network create "$NET" >/dev/null
  docker volume create "$VOL_UPLOADS" >/dev/null
  docker volume create "$VOL_PRIVATE" >/dev/null

  echo "starting isolated PostgreSQL 18 and MinIO A + B ($PREFIX)"
  docker run -d --name "$PG_CONT" --network "$NET" -p "127.0.0.1:$PG_PORT:5432" \
    -e POSTGRES_DB="$PG_DB" -e POSTGRES_USER="$PG_USER" -e POSTGRES_PASSWORD="$PG_PASS" \
    postgres:18-alpine >/dev/null
  docker run -d --name "$MINIO_A_CONT" --network "$NET" -p "127.0.0.1:$MINIO_A_PORT:9000" \
    -e MINIO_ROOT_USER="$S3_A_KEY" -e MINIO_ROOT_PASSWORD="$S3_A_SECRET" \
    -e MINIO_DEFAULT_BUCKETS="$S3_A_BUCKET" bitnamilegacy/minio:2025.5.24 >/dev/null
  docker run -d --name "$MINIO_B_CONT" --network "$NET" -p "127.0.0.1:$MINIO_B_PORT:9000" \
    -e MINIO_ROOT_USER="$S3_B_KEY" -e MINIO_ROOT_PASSWORD="$S3_B_SECRET" \
    -e MINIO_DEFAULT_BUCKETS="$S3_B_BUCKET" bitnamilegacy/minio:2025.5.24 >/dev/null

  for _ in $(seq 1 30); do
    docker exec "$PG_CONT" pg_isready -U "$PG_USER" -d "$PG_DB" >/dev/null 2>&1 && break
    sleep 1
  done
  check "MinIO A ready" wait_minio "$MINIO_A_PORT"
  check "MinIO B ready" wait_minio "$MINIO_B_PORT"
  check "API starts on disk backend" start_api disk false

  # Populate sample payloads.
  printf '\x89PNG\r\n\x1a\nSCHOOL-LOGO-V1' >"$WORK/logo.png"
  printf '\x89PNG\r\n\x1a\nLEGACY-LOGO-V1' >"$WORK/legacy-logo.png"
  printf '\x89PNG\r\n\x1a\nUSER-PHOTO-V1' >"$WORK/user-photo.png"
  printf '%%PDF-1.4\n%%DIPLOMA-ONE\n' >"$WORK/diploma1.pdf"
  printf '%%PDF-1.4\n%%DIPLOMA-TWO\n' >"$WORK/diploma2.pdf"
  printf '%%PDF-1.4\n%%DIPLOMA-THREE\n' >"$WORK/diploma3.pdf"

  # Register 2 students and authenticate admin.
  curl -fsS -o /dev/null -H 'Content-Type: application/json' \
    -d '{"firstname":"Awa","lastname":"One","email":"s1@meetstudent.test","password":"StudentPass123!","confirmedPassword":"StudentPass123!"}' \
    "http://127.0.0.1:$API_PORT/api/v1/users"
  curl -fsS -o /dev/null -H 'Content-Type: application/json' \
    -d '{"firstname":"Moussa","lastname":"Two","email":"s2@meetstudent.test","password":"StudentPass123!","confirmedPassword":"StudentPass123!"}' \
    "http://127.0.0.1:$API_PORT/api/v1/users"
  ADMIN_TOK=$(sign_in "$ADMIN_EMAIL" "$ADMIN_PASS")
  S1_TOK=$(sign_in "s1@meetstudent.test" "StudentPass123!")
  S2_TOK=$(sign_in "s2@meetstudent.test" "StudentPass123!")

  # Upload media and create school -> program -> course hierarchy.
  LOGO_JSON=$(curl -fsS -H "Authorization: Bearer $ADMIN_TOK" -F category=SCHOOL_LOGO -F "file=@$WORK/logo.png;type=image/png" "http://127.0.0.1:$API_PORT/api/v1/media")
  LOGO_ID=$(jq -r '.id' <<<"$LOGO_JSON")
  LOGO_URL=$(jq -r '.publicUrl' <<<"$LOGO_JSON")
  UPHOTO_JSON=$(curl -fsS -H "Authorization: Bearer $S1_TOK" -F category=USER_PHOTO -F "file=@$WORK/user-photo.png;type=image/png" "http://127.0.0.1:$API_PORT/api/v1/media")
  UPHOTO_URL=$(jq -r '.publicUrl' <<<"$UPHOTO_JSON")
  D1_ID=$(curl -fsS -H "Authorization: Bearer $S1_TOK" -F category=DIPLOMA -F "file=@$WORK/diploma1.pdf;type=application/pdf" "http://127.0.0.1:$API_PORT/api/v1/media" | jq -r '.id')
  D2_ID=$(curl -fsS -H "Authorization: Bearer $S1_TOK" -F category=DIPLOMA -F "file=@$WORK/diploma2.pdf;type=application/pdf" "http://127.0.0.1:$API_PORT/api/v1/media" | jq -r '.id')
  SCHOOL_ID=$(curl -fsS -H "Authorization: Bearer $ADMIN_TOK" -H 'Content-Type: application/json' \
    -d "{\"name\":\"ESMT Dakar\",\"code\":\"ESM04\",\"logoMediaId\":$LOGO_ID}" "http://127.0.0.1:$API_PORT/api/v1/schools" | jq -r '.id')
  PROG_ID=$(curl -fsS -H "Authorization: Bearer $ADMIN_TOK" -H 'Content-Type: application/json' \
    -d "{\"name\":\"Ingenieur\",\"code\":\"ING04\",\"duration\":3,\"schoolId\":$SCHOOL_ID}" "http://127.0.0.1:$API_PORT/api/v1/programs" | jq -r '.id')
  COURSE_ID=$(curl -fsS -H "Authorization: Bearer $ADMIN_TOK" -H 'Content-Type: application/json' \
    -d "{\"name\":\"Systemes\",\"code\":\"SYS04\",\"programId\":$PROG_ID}" "http://127.0.0.1:$API_PORT/api/v1/courses" | jq -r '.id')
  check "school/program/course fixture created ($SCHOOL_ID/$PROG_ID/$COURSE_ID)" test -n "$SCHOOL_ID" -a -n "$PROG_ID" -a -n "$COURSE_ID"
  S2_UID=$(curl -fsS -H "Authorization: Bearer $S2_TOK" "http://127.0.0.1:$API_PORT/api/v1/users/email/s2@meetstudent.test" | jq -r '.id')

  # Seed legacy public file (with space!), .media-*.tmp temp files, and a symlink on disk volumes.
  docker run --rm -i -v "$VOL_UPLOADS:/data/uploads" -v "$VOL_PRIVATE:/data/private" --entrypoint sh postgres:18-alpine -c '
    mkdir -p /data/uploads/public/legacy
    cat > "/data/uploads/public/legacy/old logo.png"
    printf "tmp" > /data/uploads/public/.media-deadbeef.tmp
    printf "tmp" > /data/private/.media-deadbeef.tmp
    ln -s /etc/passwd /data/uploads/public/linked-secret.txt
  ' <"$WORK/legacy-logo.png"

  echo "1. Pre-copy checks and inventory"
  local precheck_ok
  precheck_ok=$(transfer precheck "${SRC_DISK_ARGS[@]}")
  check "clean precheck passes" jq -e '.ok == true and (.ignored_temp_files | length) >= 1 and (.ignored_symlinks | length) == 1' <<<"$precheck_ok"
  local inv_json
  inv_json=$(transfer inventory "${SRC_DISK_ARGS[@]}")
  check "inventory covers DB rows and legacy public/** with space while ignoring .media-*.tmp" \
    jq -e '.items["public/legacy/old logo.png"].in_db == false and .total_keys == 5 and (.items | keys | any(contains(".media-")) | not)' <<<"$inv_json"

  # Inject legacy private path, invalid key, and missing file -> precheck MUST fail loudly!
  pg_exec -c "
    INSERT INTO media (storage_key, original_filename, content_type, size_bytes, category, visibility, owner_id, verification_status)
    VALUES
      ('users/legacy-unmigrated.pdf', 'legacy.pdf', 'application/pdf', 10, 'DIPLOMA', 'PRIVATE', $S2_UID, 'PENDING'),
      ('private/../escape.pdf', 'bad.pdf', 'application/pdf', 10, 'DIPLOMA', 'PRIVATE', $S2_UID, 'PENDING'),
      ('private/missing-on-disk.pdf', 'miss.pdf', 'application/pdf', 10, 'DIPLOMA', 'PRIVATE', $S2_UID, 'PENDING');
  " >/dev/null
  local precheck_bad rc
  precheck_bad=$(transfer precheck "${SRC_DISK_ARGS[@]}" 2>&1); rc=$?
  check "precheck fails non-zero on legacy private, invalid key, and missing file (exit $rc)" \
    bash -c '[ "$0" -ne 0 ] && jq -e "(.legacy_private_keys | length) == 1 and (.invalid_keys | length) == 2 and (.missing_on_source | length) == 1" <<<"$1"' "$rc" "$precheck_bad"
  pg_exec -c "DELETE FROM media WHERE storage_key IN ('users/legacy-unmigrated.pdf', 'private/../escape.pdf', 'private/missing-on-disk.pdf');" >/dev/null

  echo "2. Repeatable Disk -> S3 (MinIO A) initial sync + live delta + abort rehearsals"
  local sync1 sync2
  sync1=$(transfer sync "${SRC_DISK_ARGS[@]}" "${DST_S3A_ARGS[@]}")
  check "initial Disk -> S3 copies all 5 files" jq -e '.copied == 5 and .updated == 0 and .skipped == 0' <<<"$sync1"
  sync2=$(transfer sync "${SRC_DISK_ARGS[@]}" "${DST_S3A_ARGS[@]}")
  check "second Disk -> S3 sync is repeatable (0 copied)" jq -e '.copied == 0 and .updated == 0 and .skipped == 5' <<<"$sync2"

  # Delete D1 while Disk API is still live (tests final sync --delete removes it from MinIO A).
  curl -fsS -X DELETE -H "Authorization: Bearer $S1_TOK" "http://127.0.0.1:$API_PORT/api/v1/media/$D1_ID" >/dev/null

  # Abort rehearsal A: inventory divergence between final sync and verify.
  local abort_div
  abort_div=$(transfer cutover "${SRC_DISK_ARGS[@]}" "${DST_S3A_ARGS[@]}" "${API_ARGS[@]}" --skip-initial-sync \
    --after-sync-cmd "python3 -c 'import sys; sys.path.insert(0, \"$ROOT/infra/media\"); from media_transfer import S3Endpoint; S3Endpoint(\"$S3_A_BUCKET\",\"us-east-1\",\"http://127.0.0.1:$MINIO_A_PORT\",True,\"$S3_A_KEY\",\"$S3_A_SECRET\").write_bytes(\"public/legacy/old logo.png\", b\"TAMPERED\", \"image/png\")'" 2>&1); rc=$?
  check "cutover aborts on inventory divergence and reopens source writes (exit $rc)" \
    bash -c '[ "$0" -ne 0 ] && jq -e ".status == \"ABORTED\" and .abort_reason == \"INVENTORY_DIVERGENCE\"" <<<"$1"' "$rc" "$abort_div"
  check "source API writes reopened after divergence abort" \
    test "$(jq -r '.writesPaused' <<<"$(curl -fsS -H "Authorization: Bearer $ADMIN_TOK" "http://127.0.0.1:$API_PORT/api/v1/admin/media/maintenance")")" = "false"

  # Abort rehearsal B: active PostgreSQL write transaction holding pause past threshold.
  pg_exec -c "BEGIN; UPDATE schools SET name = name WHERE id = $SCHOOL_ID; SELECT pg_sleep(2.5); COMMIT;" >/dev/null &
  local pg_blocker_pid=$!
  sleep 0.4
  local abort_to
  abort_to=$(transfer cutover "${SRC_DISK_ARGS[@]}" "${DST_S3A_ARGS[@]}" "${API_ARGS[@]}" --skip-initial-sync \
    --max-pause-seconds 0.5 --drain-timeout-seconds 0.5 2>&1); rc=$?
  wait "$pg_blocker_pid" || true
  check "cutover aborts on window/drain timeout with open PG write tx and reopens source writes (exit $rc)" \
    bash -c '[ "$0" -ne 0 ] && jq -e ".status == \"ABORTED\" and (.abort_reason == \"WINDOW_EXCEEDED\" or .abort_reason == \"DRAIN_TIMEOUT\")" <<<"$1"' "$rc" "$abort_to"
  check "source API writes reopened after timeout abort" \
    test "$(jq -r '.writesPaused' <<<"$(curl -fsS -H "Authorization: Bearer $ADMIN_TOK" "http://127.0.0.1:$API_PORT/api/v1/admin/media/maintenance")")" = "false"

  echo "3. Pause enforcement check across media/reference mutations + real Disk -> MinIO A cutover"
  transfer pause-drain "${API_ARGS[@]}" >/dev/null
  check "paused upload returns 503" test "$(api_status -H "Authorization: Bearer $S1_TOK" -F category=DIPLOMA -F "file=@$WORK/diploma3.pdf;type=application/pdf" "http://127.0.0.1:$API_PORT/api/v1/media")" = "503"
  check "paused upload includes Retry-After header" test -n "$(api_header Retry-After -H "Authorization: Bearer $S1_TOK" -F category=DIPLOMA -F "file=@$WORK/diploma3.pdf;type=application/pdf" "http://127.0.0.1:$API_PORT/api/v1/media")"
  check "paused media delete returns 503" test "$(api_status -X DELETE -H "Authorization: Bearer $S1_TOK" "http://127.0.0.1:$API_PORT/api/v1/media/$D2_ID")" = "503"
  check "paused verification returns 503" test "$(api_status -X PATCH -H "Authorization: Bearer $ADMIN_TOK" -H 'Content-Type: application/json' -d '{"status":"VERIFIED"}' "http://127.0.0.1:$API_PORT/api/v1/media/$D2_ID/verification")" = "503"
  check "paused school PATCH returns 503" test "$(api_status -X PATCH -H "Authorization: Bearer $ADMIN_TOK" -H 'Content-Type: application/json' -d '{"name":"X"}' "http://127.0.0.1:$API_PORT/api/v1/schools/$SCHOOL_ID")" = "503"
  check "paused program PATCH returns 503" test "$(api_status -X PATCH -H "Authorization: Bearer $ADMIN_TOK" -H 'Content-Type: application/json' -d '{"name":"X"}' "http://127.0.0.1:$API_PORT/api/v1/programs/$PROG_ID")" = "503"
  check "paused course PATCH returns 503" test "$(api_status -X PATCH -H "Authorization: Bearer $ADMIN_TOK" -H 'Content-Type: application/json' -d '{"name":"X"}' "http://127.0.0.1:$API_PORT/api/v1/courses/$COURSE_ID")" = "503"
  check "paused account DELETE returns 503" test "$(api_status -X DELETE -H "Authorization: Bearer $S2_TOK" -H 'Content-Type: application/json' -d '{"currentPassword":"StudentPass123!"}' "http://127.0.0.1:$API_PORT/api/v1/users/$S2_UID")" = "503"
  check "paused public read still returns 200" test "$(api_status "http://127.0.0.1:$API_PORT$LOGO_URL")" = "200"
  check "paused private read auth still returns 401 anon / 403 non-owner / 200 owner" \
    bash -c '[ "$(curl -sS -o /dev/null -w "%{http_code}" "'"http://127.0.0.1:$API_PORT/api/v1/media/$D2_ID"'")" = 401 ] && [ "$(curl -sS -o /dev/null -w "%{http_code}" -H "Authorization: Bearer '"$S2_TOK"'" "'"http://127.0.0.1:$API_PORT/api/v1/media/$D2_ID"'")" = 403 ] && [ "$(curl -sS -o /dev/null -w "%{http_code}" -H "Authorization: Bearer '"$S1_TOK"'" "'"http://127.0.0.1:$API_PORT/api/v1/media/$D2_ID"'")" = 200 ]'
  transfer resume "${API_ARGS[@]}" >/dev/null

  # Full cutover Disk -> MinIO A (switches API to s3 + MEDIA_WRITES_PAUSED=true, verifies reads/auth BEFORE resume, then resumes).
  local cutover_disk_s3a
  cutover_disk_s3a=$(transfer cutover "${SRC_DISK_ARGS[@]}" "${DST_S3A_ARGS[@]}" "${API_ARGS[@]}" \
    --max-pause-seconds 45 --drain-timeout-seconds 15 \
    --activate-cmd "bash -c '
      docker stop -t 35 \"$API_CONT\" >/dev/null
      docker rm -f \"$API_CONT\" >/dev/null
      docker run -d --name \"$API_CONT\" --network \"$NET\" -p \"127.0.0.1:$API_PORT:8080\" \
        -v \"$API_JAR:/app/app.jar:ro\" \
        -e SPRING_PROFILES_ACTIVE=prod \
        -e SPRING_DATASOURCE_URL=\"jdbc:postgresql://$PG_CONT:5432/$PG_DB\" \
        -e SPRING_DATASOURCE_USERNAME=\"$PG_USER\" \
        -e SPRING_DATASOURCE_PASSWORD=\"$PG_PASS\" \
        -e JWT_SECRET_KEY=\"$JWT_SECRET\" \
        -e CORS_ALLOWED_ORIGINS=\"https://site.meetstudent.test\" \
        -e ADMIN_EMAIL=\"$ADMIN_EMAIL\" \
        -e ADMIN_PASSWORD=\"$ADMIN_PASS\" \
        -e MEDIA_STORAGE_BACKEND=s3 \
        -e MEDIA_WRITES_PAUSED=true \
        -e MEDIA_S3_BUCKET=\"$S3_A_BUCKET\" \
        -e MEDIA_S3_REGION=us-east-1 \
        -e MEDIA_S3_ENDPOINT=\"http://$MINIO_A_CONT:9000\" \
        -e MEDIA_S3_PATH_STYLE=true \
        -e MEDIA_S3_ACCESS_KEY_ID=\"$S3_A_KEY\" \
        -e MEDIA_S3_SECRET_ACCESS_KEY=\"$S3_A_SECRET\" \
        eclipse-temurin:21-jre-alpine java -jar /app/app.jar >/dev/null
      for _ in \$(seq 1 90); do curl -fsS \"http://127.0.0.1:$API_PORT/api/v1/schools\" >/dev/null 2>&1 && break; sleep 1; done
      python3 \"$TOOL\" verify-api --source-type s3 --source-s3-bucket \"$S3_A_BUCKET\" --source-s3-region us-east-1 --source-s3-endpoint \"http://127.0.0.1:$MINIO_A_PORT\" --source-s3-path-style --source-s3-access-key \"$S3_A_KEY\" --source-s3-secret-key \"$S3_A_SECRET\" --api-url \"http://127.0.0.1:$API_PORT\" --api-admin-email \"$ADMIN_EMAIL\" --api-admin-password \"$ADMIN_PASS\" --owner-token \"$S1_TOK\" --other-token \"$S2_TOK\" --expect-writes-paused >/dev/null
    '")
  check "Disk -> MinIO A cutover committed and measured pause window" \
    jq -e '.status == "COMMITTED" and .pause_duration_ms > 0 and .verification.ok == true' <<<"$cutover_disk_s3a" \
    || printf '%s\n' "$cutover_disk_s3a" >&2

  # Prove S3 mode did NOT rewrite a legacy PRIVATE row at startup!
  pg_exec -c "INSERT INTO media (storage_key, original_filename, content_type, size_bytes, category, visibility, owner_id, verification_status) VALUES ('users/s3-no-rewrite.pdf', 's3.pdf', 'application/pdf', 10, 'DIPLOMA', 'PRIVATE', $S2_UID, 'PENDING');" >/dev/null
  check "restart in s3 mode keeps legacy private key untouched (MediaMigrationRunner disabled)" \
    start_api s3 false "$S3_A_BUCKET" us-east-1 "http://$MINIO_A_CONT:9000" "$S3_A_KEY" "$S3_A_SECRET"
  local kept_key
  kept_key=$(pg_exec -tAc "SELECT storage_key FROM media WHERE storage_key = 'users/s3-no-rewrite.pdf';")
  check "legacy private key remained users/s3-no-rewrite.pdf in s3 mode" test "$kept_key" = "users/s3-no-rewrite.pdf"
  pg_exec -c "DELETE FROM media WHERE storage_key = 'users/s3-no-rewrite.pdf';" >/dev/null

  echo "4. Post-reopen Rollback (Reverse Sync MinIO A -> Disk: additions, modifications, deletions)"
  # While writes are OPEN on MinIO A:
  # - Addition: upload diploma3
  D3_ID=$(curl -fsS -H "Authorization: Bearer $S1_TOK" -F category=DIPLOMA -F "file=@$WORK/diploma3.pdf;type=application/pdf" "http://127.0.0.1:$API_PORT/api/v1/media" | jq -r '.id')
  # - Modification: overwrite legacy public file content in MinIO A
  printf '\x89PNG\r\n\x1a\nLEGACY-LOGO-V2-MODIFIED' >"$WORK/legacy-logo-v2.png"
  python3 -c "import sys; sys.path.insert(0, '$ROOT/infra/media'); from media_transfer import S3Endpoint; S3Endpoint('$S3_A_BUCKET','us-east-1','http://127.0.0.1:$MINIO_A_PORT',True,'$S3_A_KEY','$S3_A_SECRET').write_bytes('public/legacy/old logo.png', open('$WORK/legacy-logo-v2.png','rb').read(), 'image/png')"
  # - Deletion: delete diploma2
  curl -fsS -X DELETE -H "Authorization: Bearer $S1_TOK" "http://127.0.0.1:$API_PORT/api/v1/media/$D2_ID" >/dev/null

  local rollback_s3a_disk
  rollback_s3a_disk=$(transfer cutover "${SRC_S3A_ARGS[@]}" "${DST_DISK_ARGS[@]}" "${API_ARGS[@]}" \
    --max-pause-seconds 45 --drain-timeout-seconds 15 --skip-initial-sync \
    --activate-cmd "bash -c '
      docker stop -t 35 \"$API_CONT\" >/dev/null
      docker rm -f \"$API_CONT\" >/dev/null
      docker run -d --name \"$API_CONT\" --network \"$NET\" -p \"127.0.0.1:$API_PORT:8080\" \
        -v \"$API_JAR:/app/app.jar:ro\" -v \"$VOL_UPLOADS:/data/uploads\" -v \"$VOL_PRIVATE:/data/private\" \
        -e SPRING_PROFILES_ACTIVE=prod \
        -e SPRING_DATASOURCE_URL=\"jdbc:postgresql://$PG_CONT:5432/$PG_DB\" \
        -e SPRING_DATASOURCE_USERNAME=\"$PG_USER\" -e SPRING_DATASOURCE_PASSWORD=\"$PG_PASS\" \
        -e JWT_SECRET_KEY=\"$JWT_SECRET\" -e CORS_ALLOWED_ORIGINS=\"https://site.meetstudent.test\" \
        -e ADMIN_EMAIL=\"$ADMIN_EMAIL\" -e ADMIN_PASSWORD=\"$ADMIN_PASS\" \
        -e FILE_UPLOAD_DIR=/data/uploads -e FILE_PRIVATE_DIR=/data/private \
        -e MEDIA_STORAGE_BACKEND=disk -e MEDIA_WRITES_PAUSED=true \
        eclipse-temurin:21-jre-alpine java -jar /app/app.jar >/dev/null
      for _ in \$(seq 1 90); do curl -fsS \"http://127.0.0.1:$API_PORT/api/v1/schools\" >/dev/null 2>&1 && break; sleep 1; done
    '")
  check "Rollback (MinIO A -> Disk) committed and reconciled additions, modifications, deletions" \
    jq -e '.status == "COMMITTED" and .final_sync.copied == 1 and .final_sync.updated == 1 and .final_sync.deleted == 1 and .verification.ok == true' <<<"$rollback_s3a_disk" \
    || printf '%s\n' "$rollback_s3a_disk" >&2

  curl -fsS -H "Authorization: Bearer $S1_TOK" "http://127.0.0.1:$API_PORT/api/v1/media/$D3_ID" >"$WORK/fetched-d3.pdf"
  curl -fsS "http://127.0.0.1:$API_PORT/uploads/public/legacy/old%20logo.png" >"$WORK/fetched-legacy-v2.png"
  check "rolled-back Disk API serves post-cutover addition (D3) byte-for-byte" test "$(sha "$WORK/fetched-d3.pdf")" = "$(sha "$WORK/diploma3.pdf")"
  check "rolled-back Disk API serves post-cutover modification (legacy v2) byte-for-byte" test "$(sha "$WORK/fetched-legacy-v2.png")" = "$(sha "$WORK/legacy-logo-v2.png")"
  check "rolled-back Disk API no longer serves deleted media (D2 -> 403/404)" test "$(api_status -H "Authorization: Bearer $S1_TOK" "http://127.0.0.1:$API_PORT/api/v1/media/$D2_ID")" = "403"

  echo "5. S3 -> S3 migration (MinIO A -> MinIO B) and post-reopen reverse sync (MinIO B -> MinIO A)"
  # Move back to MinIO A first cleanly, then test S3 (MinIO A) -> S3 (MinIO B) and reverse sync!
  check "resync Disk -> MinIO A cleanly" transfer sync "${SRC_DISK_ARGS[@]}" "${DST_S3A_ARGS[@]}" --delete
  check "activate API on MinIO A" start_api s3 false "$S3_A_BUCKET" us-east-1 "http://$MINIO_A_CONT:9000" "$S3_A_KEY" "$S3_A_SECRET"

  local s3_to_s3_sync
  s3_to_s3_sync=$(transfer sync "${SRC_S3A_ARGS[@]}" "${DST_S3B_ARGS[@]}")
  check "initial S3 -> S3 copy succeeds" jq -e '.copied == 4 and .updated == 0' <<<"$s3_to_s3_sync"
  local s3_to_s3_cutover
  s3_to_s3_cutover=$(transfer cutover "${SRC_S3A_ARGS[@]}" "${DST_S3B_ARGS[@]}" "${API_ARGS[@]}" \
    --max-pause-seconds 45 --drain-timeout-seconds 15 \
    --activate-cmd "bash -c '
      docker stop -t 35 \"$API_CONT\" >/dev/null && docker rm -f \"$API_CONT\" >/dev/null
      docker run -d --name \"$API_CONT\" --network \"$NET\" -p \"127.0.0.1:$API_PORT:8080\" \
        -v \"$API_JAR:/app/app.jar:ro\" \
        -e SPRING_PROFILES_ACTIVE=prod \
        -e SPRING_DATASOURCE_URL=\"jdbc:postgresql://$PG_CONT:5432/$PG_DB\" \
        -e SPRING_DATASOURCE_USERNAME=\"$PG_USER\" -e SPRING_DATASOURCE_PASSWORD=\"$PG_PASS\" \
        -e JWT_SECRET_KEY=\"$JWT_SECRET\" -e CORS_ALLOWED_ORIGINS=\"https://site.meetstudent.test\" \
        -e ADMIN_EMAIL=\"$ADMIN_EMAIL\" -e ADMIN_PASSWORD=\"$ADMIN_PASS\" \
        -e MEDIA_STORAGE_BACKEND=s3 -e MEDIA_WRITES_PAUSED=true \
        -e MEDIA_S3_BUCKET=\"$S3_B_BUCKET\" -e MEDIA_S3_REGION=eu-west-1 \
        -e MEDIA_S3_ENDPOINT=\"http://$MINIO_B_CONT:9000\" -e MEDIA_S3_PATH_STYLE=true \
        -e MEDIA_S3_ACCESS_KEY_ID=\"$S3_B_KEY\" -e MEDIA_S3_SECRET_ACCESS_KEY=\"$S3_B_SECRET\" \
        eclipse-temurin:21-jre-alpine java -jar /app/app.jar >/dev/null
      for _ in \$(seq 1 90); do curl -fsS \"http://127.0.0.1:$API_PORT/api/v1/schools\" >/dev/null 2>&1 && break; sleep 1; done
    '")
  check "S3 -> S3 cutover (MinIO A -> MinIO B) committed" jq -e '.status == "COMMITTED" and .verification.ok == true' <<<"$s3_to_s3_cutover"

  # Mutate MinIO B after reopen: add diploma2 back, modify legacy, delete diploma3.
  D2_NEW_ID=$(curl -fsS -H "Authorization: Bearer $S1_TOK" -F category=DIPLOMA -F "file=@$WORK/diploma2.pdf;type=application/pdf" "http://127.0.0.1:$API_PORT/api/v1/media" | jq -r '.id')
  printf '\x89PNG\r\n\x1a\nLEGACY-LOGO-V3-S3B' >"$WORK/legacy-logo-v3.png"
  python3 -c "import sys; sys.path.insert(0, '$ROOT/infra/media'); from media_transfer import S3Endpoint; S3Endpoint('$S3_B_BUCKET','eu-west-1','http://127.0.0.1:$MINIO_B_PORT',True,'$S3_B_KEY','$S3_B_SECRET').write_bytes('public/legacy/old logo.png', open('$WORK/legacy-logo-v3.png','rb').read(), 'image/png')"
  curl -fsS -X DELETE -H "Authorization: Bearer $S1_TOK" "http://127.0.0.1:$API_PORT/api/v1/media/$D3_ID" >/dev/null

  local rollback_s3b_s3a
  rollback_s3b_s3a=$(transfer cutover "${SRC_S3B_ARGS[@]}" "${DST_S3A_ARGS[@]}" "${API_ARGS[@]}" \
    --max-pause-seconds 45 --drain-timeout-seconds 15 --skip-initial-sync \
    --activate-cmd "bash -c '
      docker stop -t 35 \"$API_CONT\" >/dev/null && docker rm -f \"$API_CONT\" >/dev/null
      docker run -d --name \"$API_CONT\" --network \"$NET\" -p \"127.0.0.1:$API_PORT:8080\" \
        -v \"$API_JAR:/app/app.jar:ro\" \
        -e SPRING_PROFILES_ACTIVE=prod \
        -e SPRING_DATASOURCE_URL=\"jdbc:postgresql://$PG_CONT:5432/$PG_DB\" \
        -e SPRING_DATASOURCE_USERNAME=\"$PG_USER\" -e SPRING_DATASOURCE_PASSWORD=\"$PG_PASS\" \
        -e JWT_SECRET_KEY=\"$JWT_SECRET\" -e CORS_ALLOWED_ORIGINS=\"https://site.meetstudent.test\" \
        -e ADMIN_EMAIL=\"$ADMIN_EMAIL\" -e ADMIN_PASSWORD=\"$ADMIN_PASS\" \
        -e MEDIA_STORAGE_BACKEND=s3 -e MEDIA_WRITES_PAUSED=true \
        -e MEDIA_S3_BUCKET=\"$S3_A_BUCKET\" -e MEDIA_S3_REGION=us-east-1 \
        -e MEDIA_S3_ENDPOINT=\"http://$MINIO_A_CONT:9000\" -e MEDIA_S3_PATH_STYLE=true \
        -e MEDIA_S3_ACCESS_KEY_ID=\"$S3_A_KEY\" -e MEDIA_S3_SECRET_ACCESS_KEY=\"$S3_A_SECRET\" \
        eclipse-temurin:21-jre-alpine java -jar /app/app.jar >/dev/null
      for _ in \$(seq 1 90); do curl -fsS \"http://127.0.0.1:$API_PORT/api/v1/schools\" >/dev/null 2>&1 && break; sleep 1; done
    '")
  check "S3 -> S3 Rollback (MinIO B -> MinIO A) reconciled additions, modifications, deletions" \
    jq -e '.status == "COMMITTED" and .final_sync.copied == 1 and .final_sync.updated == 1 and .final_sync.deleted == 1 and .verification.ok == true' <<<"$rollback_s3b_s3a"

  curl -fsS -H "Authorization: Bearer $S1_TOK" "http://127.0.0.1:$API_PORT/api/v1/media/$D2_NEW_ID" >"$WORK/fetched-d2-new.pdf"
  curl -fsS "http://127.0.0.1:$API_PORT/uploads/public/legacy/old%20logo.png" >"$WORK/fetched-legacy-v3.png"
  check "MinIO A API serves S3->S3 rollback addition byte-for-byte" test "$(sha "$WORK/fetched-d2-new.pdf")" = "$(sha "$WORK/diploma2.pdf")"
  check "MinIO A API serves S3->S3 rollback modification byte-for-byte" test "$(sha "$WORK/fetched-legacy-v3.png")" = "$(sha "$WORK/legacy-logo-v3.png")"
  check "MinIO A API removed deleted media after S3->S3 rollback" test "$(api_status -H "Authorization: Bearer $S1_TOK" "http://127.0.0.1:$API_PORT/api/v1/media/$D3_ID")" = "403"

  if [ "$failures" -gt 0 ]; then
    echo "$failures failure(s)"
    return 1
  fi
  echo "all passed"
}

main "$@"
