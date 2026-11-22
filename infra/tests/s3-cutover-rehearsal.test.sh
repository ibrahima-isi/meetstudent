#!/usr/bin/env bash
# End-to-end production S3 cutover rehearsal for Ticket portable-media-storage/05.
#
# Exercises real PostgreSQL 18 + real meetstudent-api + real infra/backup image +
# three isolated MinIO S3-compatible servers:
#   - Endpoint A: path-style=true,  region=us-east-1 (Disk -> S3 cutover target)
#   - Endpoint B: path-style=false, region=eu-west-1 (virtual-hosted distinct S3 target)
#   - Backup S3:  independent EU S3 destination (s3.eu.backup-offsite.test, eu-central-1)
#
# Uses unique ms-t05-* container/volume/network names and non-default loopback ports
# (55435, 59006, 59007, 59008, 58085). Always tears everything down on EXIT.
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
TOOL="$ROOT/infra/media/media_transfer.py"
API_JAR=${API_JAR:-$ROOT/apps/api/target/meetstudent.jar}

PREFIX="ms-t05"
NET="${PREFIX}-net"
PG_CONT="${PREFIX}-pg"
MINIO_A_CONT="${PREFIX}-minio-a"
MINIO_B_CONT="${PREFIX}-minio-b"
MINIO_BKP_CONT="${PREFIX}-minio-backup"
API_CONT="${PREFIX}-api"
VOL_UPLOADS="${PREFIX}-uploads"
VOL_PRIVATE="${PREFIX}-private"
BACKUP_IMAGE="${PREFIX}-backup:test"

PG_PORT=${PG_PORT:-55435}
MINIO_A_PORT=${MINIO_A_PORT:-59006}
MINIO_B_PORT=${MINIO_B_PORT:-59007}
MINIO_BKP_PORT=${MINIO_BKP_PORT:-59008}
API_PORT=${API_PORT:-58085}

PG_DB=meetstudent
PG_USER=meetstudent
PG_PASS=ms-t05-pg-password
JWT_SECRET=bXktdDA1LXZlcnktbG9uZy1yYW5kb20tand0LXNlY3JldC1rZXktZm9yLWlzb2xhdGVkLWN1dG92ZXItMTIzNDU=
ADMIN_EMAIL=admin@meetstudent.test
ADMIN_PASS=AdminTestPass123!

S3_A_BUCKET=meetstudent-media-a
S3_A_KEY=minio-a-access
S3_A_SECRET=minio-a-secret-key

S3_B_BUCKET=meetstudent-media-b
S3_B_DOMAIN=s3.eu-media-b.test
S3_B_KEY=minio-b-access
S3_B_SECRET=minio-b-secret-key

BKP_BUCKET=meetstudent-backups-eu
BKP_DOMAIN=s3.eu.backup-offsite.test
BKP_KEY=backup-s3-access
BKP_SECRET=backup-s3-secret-key
BACKUP_CRYPT_PASSWORD=ms-t05-crypt-password
BACKUP_CRYPT_SALT=ms-t05-crypt-salt

WORK=""
failures=0

pass() { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
check() {
  local name=$1; shift
  if "$@" >/dev/null 2>&1; then pass "$name"; else fail "$name"; return 1; fi
}
sha() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1
}

teardown() {
  echo "teardown ($PREFIX)"
  docker rm -f "$API_CONT" "$PG_CONT" "$MINIO_A_CONT" "$MINIO_B_CONT" "$MINIO_BKP_CONT" >/dev/null 2>&1 || true
  docker volume rm -f "$VOL_UPLOADS" "$VOL_PRIVATE" >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
  docker rmi -f "$BACKUP_IMAGE" >/dev/null 2>&1 || true
  [ -z "$WORK" ] || rm -rf "$WORK"
}
trap teardown EXIT

mc_exec() {
  local cname=$1 user=$2 secret=$3 cmd=$4
  docker exec "$cname" sh -c "mc alias set t http://127.0.0.1:9000 $user $secret >/dev/null && $cmd"
}

wait_minio() {
  local port=$1 cname=$2 user=$3 secret=$4 bucket=$5
  for _ in $(seq 1 45); do
    if curl -fsS "http://127.0.0.1:$port/minio/health/ready" >/dev/null 2>&1; then
      mc_exec "$cname" "$user" "$secret" "mc mb -p t/$bucket" >/dev/null 2>&1 && return 0
    fi
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
  local backend=$1 paused=$2 bucket=${3:-} region=${4:-} endpoint=${5:-} path_style=${6:-true} ak=${7:-} sk=${8:-}
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
    -e MEDIA_S3_PATH_STYLE="$path_style" \
    -e MEDIA_S3_ACCESS_KEY_ID="$ak" \
    -e MEDIA_S3_SECRET_ACCESS_KEY="$sk" \
    eclipse-temurin:21-jre-alpine java -jar /app/app.jar >/dev/null
  wait_api
}

run_backup_cmd() {
  local cmd=$1; shift
  docker run --rm --network "$NET" --entrypoint sh \
    -v "$VOL_UPLOADS:/data/uploads:ro" \
    -v "$VOL_PRIVATE:/data/private:ro" \
    -e PGHOST="$PG_CONT" \
    -e PGUSER="$PG_USER" \
    -e PGPASSWORD="$PG_PASS" \
    -e PGDATABASE="$PG_DB" \
    -e BACKUP_S3_PROVIDER=Other \
    -e BACKUP_S3_ENDPOINT="http://$BKP_DOMAIN:9000" \
    -e BACKUP_S3_REGION=eu-central-1 \
    -e BACKUP_S3_BUCKET="$BKP_BUCKET" \
    -e BACKUP_S3_PATH_STYLE=true \
    -e BACKUP_S3_ACCESS_KEY_ID="$BKP_KEY" \
    -e BACKUP_S3_SECRET_ACCESS_KEY="$BKP_SECRET" \
    -e BACKUP_CRYPT_PASSWORD="$BACKUP_CRYPT_PASSWORD" \
    -e BACKUP_CRYPT_SALT="$BACKUP_CRYPT_SALT" \
    "$@" "$BACKUP_IMAGE" -c "$cmd"
}

transfer() {
  MEDIA_TRANSFER_PSQL_CMD="docker exec -i $PG_CONT psql -U $PG_USER -d $PG_DB" \
    python3 "$TOOL" "$@"
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
DST_S3A_ARGS=(--target-type s3 --target-s3-bucket "$S3_A_BUCKET" --target-s3-region us-east-1 --target-s3-endpoint "http://127.0.0.1:$MINIO_A_PORT" --target-s3-path-style --target-s3-access-key "$S3_A_KEY" --target-s3-secret-key "$S3_A_SECRET")
SRC_S3A_ARGS=(--source-type s3 --source-s3-bucket "$S3_A_BUCKET" --source-s3-region us-east-1 --source-s3-endpoint "http://127.0.0.1:$MINIO_A_PORT" --source-s3-path-style --source-s3-access-key "$S3_A_KEY" --source-s3-secret-key "$S3_A_SECRET")
DST_S3B_ARGS=(--target-type s3 --target-s3-bucket "$S3_B_BUCKET" --target-s3-region eu-west-1 --target-s3-endpoint "http://localhost:$MINIO_B_PORT" --target-s3-access-key "$S3_B_KEY" --target-s3-secret-key "$S3_B_SECRET")
SRC_S3B_ARGS=(--source-type s3 --source-s3-bucket "$S3_B_BUCKET" --source-s3-region eu-west-1 --source-s3-endpoint "http://localhost:$MINIO_B_PORT" --source-s3-access-key "$S3_B_KEY" --source-s3-secret-key "$S3_B_SECRET")
API_ARGS=(--api-url "http://127.0.0.1:$API_PORT" --api-admin-email "$ADMIN_EMAIL" --api-admin-password "$ADMIN_PASS")

main() {
  if [ ! -f "$API_JAR" ]; then
    echo "API jar not found at $API_JAR; build it first with ./mvnw package -DskipTests" >&2
    exit 2
  fi

  teardown
  WORK=$(mktemp -d "${TMPDIR:-/tmp}/ms-t05-work.XXXXXX")
  docker network create "$NET" >/dev/null
  docker volume create "$VOL_UPLOADS" >/dev/null
  docker volume create "$VOL_PRIVATE" >/dev/null

  echo "0. Building backup image and starting isolated PostgreSQL 18 + 3 S3 endpoints ($PREFIX)"
  check "backup image builds" docker build -q -t "$BACKUP_IMAGE" "$ROOT/infra/backup"

  docker run -d --name "$PG_CONT" --network "$NET" -p "127.0.0.1:$PG_PORT:5432" \
    -e POSTGRES_DB="$PG_DB" -e POSTGRES_USER="$PG_USER" -e POSTGRES_PASSWORD="$PG_PASS" \
    postgres:18-alpine >/dev/null

  docker run -d --name "$MINIO_A_CONT" --network "$NET" -p "127.0.0.1:$MINIO_A_PORT:9000" \
    -e MINIO_ROOT_USER="$S3_A_KEY" -e MINIO_ROOT_PASSWORD="$S3_A_SECRET" \
    bitnamilegacy/minio:2025.5.24 >/dev/null

  docker run -d --name "$MINIO_B_CONT" --network "$NET" \
    --network-alias "$S3_B_DOMAIN" --network-alias "${S3_B_BUCKET}.${S3_B_DOMAIN}" \
    -p "127.0.0.1:$MINIO_B_PORT:9000" \
    -e MINIO_ROOT_USER="$S3_B_KEY" -e MINIO_ROOT_PASSWORD="$S3_B_SECRET" \
    -e MINIO_DOMAIN="localhost,${S3_B_DOMAIN}" \
    bitnamilegacy/minio:2025.5.24 >/dev/null

  docker run -d --name "$MINIO_BKP_CONT" --network "$NET" \
    --network-alias "$BKP_DOMAIN" \
    -p "127.0.0.1:$MINIO_BKP_PORT:9000" \
    -e MINIO_ROOT_USER="$BKP_KEY" -e MINIO_ROOT_PASSWORD="$BKP_SECRET" \
    bitnamilegacy/minio:2025.5.24 >/dev/null

  for _ in $(seq 1 30); do
    docker exec "$PG_CONT" pg_isready -U "$PG_USER" -d "$PG_DB" >/dev/null 2>&1 && break
    sleep 1
  done
  check "S3 Endpoint A ready (path-style, us-east-1)" wait_minio "$MINIO_A_PORT" "$MINIO_A_CONT" "$S3_A_KEY" "$S3_A_SECRET" "$S3_A_BUCKET"
  check "S3 Endpoint B ready (virtual-hosted, eu-west-1)" wait_minio "$MINIO_B_PORT" "$MINIO_B_CONT" "$S3_B_KEY" "$S3_B_SECRET" "$S3_B_BUCKET"
  check "Independent EU Backup S3 ready (eu-central-1)" wait_minio "$MINIO_BKP_PORT" "$MINIO_BKP_CONT" "$BKP_KEY" "$BKP_SECRET" "$BKP_BUCKET"
  check "API starts on disk backend" start_api disk false

  echo "1. Operator bucket qualification across distinct endpoints & addressing modes"
  local qual_a qual_b
  qual_a=$(transfer qualify-bucket \
    --s3-bucket "$S3_A_BUCKET" --s3-region us-east-1 --s3-endpoint "http://127.0.0.1:$MINIO_A_PORT" --s3-path-style \
    --s3-access-key "$S3_A_KEY" --s3-secret-key "$S3_A_SECRET" \
    --check-backup-independence --backup-s3-endpoint "http://$BKP_DOMAIN:9000" --backup-s3-region eu-central-1)
  check "Endpoint A passes private bucket + CRUD + range + anonymous refusal + backup independence (path-style)" \
    jq -e '.ok == true and .addressing_mode == "path-style" and .checks.missing_object_404 == true and .checks.public.range_206 == true and .checks.public.anonymous_denied == true and .checks.private.anonymous_denied == true and .backup_independence.ok == true' <<<"$qual_a"

  qual_b=$(transfer qualify-bucket \
    --s3-bucket "$S3_B_BUCKET" --s3-region eu-west-1 --s3-endpoint "http://localhost:$MINIO_B_PORT" \
    --s3-access-key "$S3_B_KEY" --s3-secret-key "$S3_B_SECRET" \
    --check-backup-independence --backup-s3-endpoint "http://$BKP_DOMAIN:9000" --backup-s3-region eu-central-1)
  check "Endpoint B passes private bucket + CRUD + range + anonymous refusal + backup independence (virtual-hosted)" \
    jq -e '.ok == true and .addressing_mode == "virtual-hosted" and .checks.missing_object_404 == true and .checks.public.range_206 == true and .checks.public.anonymous_denied == true and .checks.private.anonymous_denied == true and .backup_independence.ok == true' <<<"$qual_b"

  local qual_http_rc
  transfer qualify-bucket --s3-bucket "$S3_A_BUCKET" --s3-region us-east-1 --s3-endpoint "http://127.0.0.1:$MINIO_A_PORT" --s3-path-style \
    --s3-access-key "$S3_A_KEY" --s3-secret-key "$S3_A_SECRET" --require-https >/dev/null 2>&1; qual_http_rc=$?
  check "qualify-bucket refuses http:// when --require-https is enabled (exit $qual_http_rc)" test "$qual_http_rc" -ne 0

  local qual_coloc_rc
  transfer qualify-bucket --s3-bucket "$S3_A_BUCKET" --s3-region us-east-1 --s3-endpoint "http://127.0.0.1:$MINIO_A_PORT" --s3-path-style \
    --s3-access-key "$S3_A_KEY" --s3-secret-key "$S3_A_SECRET" \
    --check-backup-independence --backup-s3-endpoint "http://127.0.0.1:$MINIO_A_PORT" --backup-s3-region eu-west-1 >/dev/null 2>&1; qual_coloc_rc=$?
  check "qualify-bucket refuses colocalized backup provider matching active media endpoint (exit $qual_coloc_rc)" test "$qual_coloc_rc" -ne 0

  # Negative real-S3 test: publicly readable public/ prefix must fail qualification.
  mc_exec "$MINIO_A_CONT" "$S3_A_KEY" "$S3_A_SECRET" "mc mb -p t/public-leak-check && mc anonymous set download t/public-leak-check/public" >/dev/null
  local qual_pub_out qual_pub_rc
  qual_pub_out=$(transfer qualify-bucket --s3-bucket public-leak-check --s3-region us-east-1 --s3-endpoint "http://127.0.0.1:$MINIO_A_PORT" --s3-path-style \
    --s3-access-key "$S3_A_KEY" --s3-secret-key "$S3_A_SECRET" 2>&1); qual_pub_rc=$?
  mc_exec "$MINIO_A_CONT" "$S3_A_KEY" "$S3_A_SECRET" "mc rb --force t/public-leak-check" >/dev/null 2>&1 || true
  check "qualify-bucket fails loudly when public/ allows anonymous downloads (exit $qual_pub_rc)" \
    bash -c '[ "$0" -ne 0 ] && jq -e ".ok == false and .checks.public.anonymous_denied == false" <<<"$1"' "$qual_pub_rc" "$qual_pub_out"

  echo "2. Seeding realistic production disk state & taking pre-cutover encrypted backup"
  printf '\x89PNG\r\n\x1a\nSCHOOL-LOGO-PROD' >"$WORK/logo.png"
  printf '\x89PNG\r\n\x1a\nUSER-PHOTO-PROD' >"$WORK/user-photo.png"
  printf '\x89PNG\r\n\x1a\nLEGACY-LOGO-PROD' >"$WORK/legacy-logo.png"
  printf '%%PDF-1.4\n%%DIPLOMA-ONE\n' >"$WORK/diploma1.pdf"
  printf '%%PDF-1.4\n%%DIPLOMA-TWO\n' >"$WORK/diploma2.pdf"
  printf '%%PDF-1.4\n%%CERT-DELTA\n' >"$WORK/cert-delta.pdf"
  printf '%%PDF-1.4\n%%POST-CUTOVER-DOC\n' >"$WORK/post-cutover.pdf"
  printf '\x89PNG\r\n\x1a\nPOST-CUTOVER-LOGO' >"$WORK/logo-post.png"

  curl -fsS -o /dev/null -H 'Content-Type: application/json' \
    -d '{"firstname":"Awa","lastname":"Owner","email":"owner@meetstudent.test","password":"StudentPass123!","confirmedPassword":"StudentPass123!"}' \
    "http://127.0.0.1:$API_PORT/api/v1/users"
  curl -fsS -o /dev/null -H 'Content-Type: application/json' \
    -d '{"firstname":"Moussa","lastname":"Other","email":"other@meetstudent.test","password":"StudentPass123!","confirmedPassword":"StudentPass123!"}' \
    "http://127.0.0.1:$API_PORT/api/v1/users"
  ADMIN_TOK=$(sign_in "$ADMIN_EMAIL" "$ADMIN_PASS")
  OWNER_TOK=$(sign_in "owner@meetstudent.test" "StudentPass123!")
  OTHER_TOK=$(sign_in "other@meetstudent.test" "StudentPass123!")

  LOGO_JSON=$(curl -fsS -H "Authorization: Bearer $ADMIN_TOK" -F category=SCHOOL_LOGO -F "file=@$WORK/logo.png;type=image/png" "http://127.0.0.1:$API_PORT/api/v1/media")
  LOGO_ID=$(jq -r '.id' <<<"$LOGO_JSON")
  LOGO_URL=$(jq -r '.publicUrl' <<<"$LOGO_JSON")
  UPHOTO_JSON=$(curl -fsS -H "Authorization: Bearer $OWNER_TOK" -F category=USER_PHOTO -F "file=@$WORK/user-photo.png;type=image/png" "http://127.0.0.1:$API_PORT/api/v1/media")
  UPHOTO_URL=$(jq -r '.publicUrl' <<<"$UPHOTO_JSON")
  D1_ID=$(curl -fsS -H "Authorization: Bearer $OWNER_TOK" -F category=DIPLOMA -F "file=@$WORK/diploma1.pdf;type=application/pdf" "http://127.0.0.1:$API_PORT/api/v1/media" | jq -r '.id')
  D2_ID=$(curl -fsS -H "Authorization: Bearer $OWNER_TOK" -F category=DIPLOMA -F "file=@$WORK/diploma2.pdf;type=application/pdf" "http://127.0.0.1:$API_PORT/api/v1/media" | jq -r '.id')
  SCHOOL_ID=$(curl -fsS -H "Authorization: Bearer $ADMIN_TOK" -H 'Content-Type: application/json' \
    -d "{\"name\":\"ESMT Cutover\",\"code\":\"ESC05\",\"logoMediaId\":$LOGO_ID}" "http://127.0.0.1:$API_PORT/api/v1/schools" | jq -r '.id')

  docker run --rm -i -v "$VOL_UPLOADS:/data/uploads" -v "$VOL_PRIVATE:/data/private" --entrypoint sh postgres:18-alpine -c '
    mkdir -p /data/uploads/public/legacy
    cat > "/data/uploads/public/legacy/old logo.png"
    printf "tmp" > /data/uploads/public/.media-cutover-noise.tmp
  ' <"$WORK/legacy-logo.png"

  local pre_bkp_rc pre_restore_point
  run_backup_cmd "backup.sh" -e MEDIA_STORAGE_BACKEND=disk >/dev/null 2>&1; pre_bkp_rc=$?
  check "pre-cutover encrypted backup in disk mode succeeds (exit $pre_bkp_rc)" test "$pre_bkp_rc" -eq 0
  pre_restore_point=$(run_backup_cmd "restore.sh --list" 2>/dev/null | grep -v NOTICE | tail -n 1)
  check "pre-cutover restore point recorded ($pre_restore_point)" grep -Eq '^[0-9]{8}T[0-9]{6}Z$' <<<"$pre_restore_point"

  local bkp_objects
  bkp_objects=$(mc_exec "$MINIO_BKP_CONT" "$BKP_KEY" "$BKP_SECRET" "mc ls -r --json t/$BKP_BUCKET" | jq -r '.key // empty')
  check "pre-cutover backup objects are encrypted on independent S3 ($(grep -c . <<<"$bkp_objects") objects)" \
    bash -c '[ "$(grep -c . <<<"$0")" -ge 4 ] && ! grep -Eiq "20[0-9]{6}T|dump|manifest|sha256" <<<"$0"' "$bkp_objects"

  echo "3. Initial media copy (Disk -> S3 Endpoint A) while writes remain open"
  check "precheck passes before initial copy" transfer precheck "${SRC_DISK_ARGS[@]}"
  local init_sync
  init_sync=$(transfer sync "${SRC_DISK_ARGS[@]}" "${DST_S3A_ARGS[@]}")
  check "initial Disk -> S3 copy transferred all 5 objects" jq -e '.copied == 5 and .updated == 0' <<<"$init_sync"

  # Mutate live disk API during/after initial copy to exercise final delta sync during pause.
  CERT_ID=$(curl -fsS -H "Authorization: Bearer $OWNER_TOK" -F category=CERTIFICATE -F "file=@$WORK/cert-delta.pdf;type=application/pdf" "http://127.0.0.1:$API_PORT/api/v1/media" | jq -r '.id')
  curl -fsS -X DELETE -H "Authorization: Bearer $OWNER_TOK" "http://127.0.0.1:$API_PORT/api/v1/media/$D2_ID" >/dev/null

  echo "4. Cutover pause, drain, final inventory verify, S3 activation, and PRE-REOPEN verification"
  local cutover_out
  cutover_out=$(transfer cutover "${SRC_DISK_ARGS[@]}" "${DST_S3A_ARGS[@]}" "${API_ARGS[@]}" \
    --max-pause-seconds 45 --drain-timeout-seconds 15 --skip-initial-sync \
    --verify-reads --owner-token "$OWNER_TOK" --other-token "$OTHER_TOK" \
    --activate-cmd "bash -c '
      set -e
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
      # Extra pre-reopen write-pause check across POST media and PATCH school while paused on S3:
      [ \"\$(curl -sS -o /dev/null -w %{http_code} -H \"Authorization: Bearer $OWNER_TOK\" -F category=DIPLOMA -F \"file=@$WORK/post-cutover.pdf;type=application/pdf\" \"http://127.0.0.1:$API_PORT/api/v1/media\")\" = 503 ]
      [ \"\$(curl -sS -o /dev/null -w %{http_code} -X PATCH -H \"Authorization: Bearer $ADMIN_TOK\" -H \"Content-Type: application/json\" -d '\''{\"name\":\"Blocked\"}'\'' \"http://127.0.0.1:$API_PORT/api/v1/schools/$SCHOOL_ID\")\" = 503 ]
    '")
  local cutover_rc=$?
  check "cutover Disk -> Endpoint A committed with pre-reopen read/auth/503/anon-S3 verification (exit $cutover_rc)" \
    bash -c '[ "$0" -eq 0 ] && jq -e ".status == \"COMMITTED\" and .final_sync.copied == 1 and .final_sync.deleted == 1 and .verification.ok == true and .pre_reopen_verification.ok == true and .pause_duration_ms > 0" <<<"$1"' "$cutover_rc" "$cutover_out" \
    || printf '%s\n' "$cutover_out" >&2
  local pause_ms
  pause_ms=$(jq -r '.pause_duration_ms // "unknown"' <<<"$cutover_out")

  echo "5. Post-reopen smoke checks: new upload, deletion, and fresh S3-mode encrypted backup"
  check "API reports writesPaused=false after cutover reopened writes" \
    test "$(jq -r '.writesPaused' <<<"$(curl -fsS -H "Authorization: Bearer $ADMIN_TOK" "http://127.0.0.1:$API_PORT/api/v1/admin/media/maintenance")")" = "false"

  local post_doc_id post_logo_json post_logo_url
  post_doc_id=$(curl -fsS -H "Authorization: Bearer $OWNER_TOK" -F category=DIPLOMA -F "file=@$WORK/post-cutover.pdf;type=application/pdf" "http://127.0.0.1:$API_PORT/api/v1/media" | jq -r '.id')
  post_logo_json=$(curl -fsS -H "Authorization: Bearer $ADMIN_TOK" -F category=SCHOOL_LOGO -F "file=@$WORK/logo-post.png;type=image/png" "http://127.0.0.1:$API_PORT/api/v1/media")
  post_logo_url=$(jq -r '.publicUrl' <<<"$post_logo_json")
  check "post-reopen uploads succeeded (private=$post_doc_id, public=$post_logo_url)" test -n "$post_doc_id" -a -n "$post_logo_url"

  curl -fsS -H "Authorization: Bearer $OWNER_TOK" "http://127.0.0.1:$API_PORT/api/v1/media/$post_doc_id" >"$WORK/fetched-post-doc.pdf"
  curl -fsS "http://127.0.0.1:$API_PORT$post_logo_url" >"$WORK/fetched-post-logo.png"
  check "post-reopen private upload readable byte-for-byte" test "$(sha "$WORK/fetched-post-doc.pdf")" = "$(sha "$WORK/post-cutover.pdf")"
  check "post-reopen public upload readable byte-for-byte" test "$(sha "$WORK/fetched-post-logo.png")" = "$(sha "$WORK/logo-post.png")"

  check "post-reopen media deletion returns 204" \
    test "$(api_status -X DELETE -H "Authorization: Bearer $OWNER_TOK" "http://127.0.0.1:$API_PORT/api/v1/media/$CERT_ID")" = "204"
  check "deleted post-reopen media no longer readable (403)" \
    test "$(api_status -H "Authorization: Bearer $OWNER_TOK" "http://127.0.0.1:$API_PORT/api/v1/media/$CERT_ID")" = "403"

  # Ensure timestamp advances >= 1s for distinct S3-mode restore point.
  sleep 1
  local post_bkp_rc post_restore_point
  run_backup_cmd "backup.sh" \
    -e MEDIA_STORAGE_BACKEND=s3 \
    -e MEDIA_S3_BUCKET="$S3_A_BUCKET" \
    -e MEDIA_S3_REGION=us-east-1 \
    -e MEDIA_S3_ENDPOINT="http://$MINIO_A_CONT:9000" \
    -e MEDIA_S3_PATH_STYLE=true \
    -e MEDIA_S3_ACCESS_KEY_ID="$S3_A_KEY" \
    -e MEDIA_S3_SECRET_ACCESS_KEY="$S3_A_SECRET" >/dev/null 2>&1; post_bkp_rc=$?
  check "post-reopen S3-mode encrypted backup to independent EU S3 succeeds (exit $post_bkp_rc)" test "$post_bkp_rc" -eq 0
  post_restore_point=$(run_backup_cmd "restore.sh --list" 2>/dev/null | grep -v NOTICE | tail -n 1)
  check "post-cutover restore point ($post_restore_point) is newer than pre-cutover ($pre_restore_point)" \
    test -n "$post_restore_point" -a "$post_restore_point" != "$pre_restore_point"

  echo "6. Distinct second S3 endpoint (virtual-hosted, eu-west-1) live API verification"
  local sync_ab cut_ab_out cut_ab_rc
  sync_ab=$(transfer sync "${SRC_S3A_ARGS[@]}" "${DST_S3B_ARGS[@]}")
  check "sync Endpoint A (path-style) -> Endpoint B (virtual-hosted) succeeds" jq -e '.ok == true and .copied >= 5' <<<"$sync_ab"
  cut_ab_out=$(transfer cutover "${SRC_S3A_ARGS[@]}" "${DST_S3B_ARGS[@]}" "${API_ARGS[@]}" \
    --max-pause-seconds 45 --drain-timeout-seconds 15 --skip-initial-sync \
    --verify-reads --owner-token "$OWNER_TOK" --other-token "$OTHER_TOK" \
    --activate-cmd "bash -c '
      set -e
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
        -e MEDIA_S3_BUCKET=\"$S3_B_BUCKET\" \
        -e MEDIA_S3_REGION=eu-west-1 \
        -e MEDIA_S3_ENDPOINT=\"http://$S3_B_DOMAIN:9000\" \
        -e MEDIA_S3_PATH_STYLE=false \
        -e MEDIA_S3_ACCESS_KEY_ID=\"$S3_B_KEY\" \
        -e MEDIA_S3_SECRET_ACCESS_KEY=\"$S3_B_SECRET\" \
        eclipse-temurin:21-jre-alpine java -jar /app/app.jar >/dev/null
      for _ in \$(seq 1 90); do curl -fsS \"http://127.0.0.1:$API_PORT/api/v1/schools\" >/dev/null 2>&1 && break; sleep 1; done
    '"); cut_ab_rc=$?
  check "API cutover to virtual-hosted Endpoint B (eu-west-1, path-style=false) passes all pre-reopen checks (exit $cut_ab_rc)" \
    bash -c '[ "$0" -eq 0 ] && jq -e ".status == \"COMMITTED\" and .pre_reopen_verification.ok == true" <<<"$1"' "$cut_ab_rc" "$cut_ab_out" \
    || printf '%s\n' "$cut_ab_out" >&2

  local epb_doc_id
  epb_doc_id=$(curl -fsS -H "Authorization: Bearer $OWNER_TOK" -F category=DIPLOMA -F "file=@$WORK/post-cutover.pdf;type=application/pdf" "http://127.0.0.1:$API_PORT/api/v1/media" | jq -r '.id')
  check "API writes and reads new object on virtual-hosted Endpoint B after reopen (id=$epb_doc_id)" \
    test "$(api_status -H "Authorization: Bearer $OWNER_TOK" "http://127.0.0.1:$API_PORT/api/v1/media/$epb_doc_id")" = "200"
  check "API deletes object on virtual-hosted Endpoint B (204)" \
    test "$(api_status -X DELETE -H "Authorization: Bearer $OWNER_TOK" "http://127.0.0.1:$API_PORT/api/v1/media/$epb_doc_id")" = "204"

  echo "REHEARSAL_SUMMARY:"
  echo "  PRE_CUTOVER_RESTORE_POINT=$pre_restore_point"
  echo "  POST_CUTOVER_RESTORE_POINT=$post_restore_point"
  echo "  PAUSE_DURATION_MS=$pause_ms"
  echo "  ENDPOINT_A=http://127.0.0.1:$MINIO_A_PORT (region=us-east-1, addressing=path-style)"
  echo "  ENDPOINT_B=http://localhost:$MINIO_B_PORT (region=eu-west-1, addressing=virtual-hosted)"
  echo "  BACKUP_ENDPOINT=http://$BKP_DOMAIN:9000 (region=eu-central-1, addressing=path-style)"

  if [ "$failures" -gt 0 ]; then
    echo "$failures failure(s)"
    return 1
  fi
  echo "all passed"
}

main "$@"
