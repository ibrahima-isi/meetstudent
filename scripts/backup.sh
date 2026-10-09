#!/usr/bin/env bash
# Backup of the production stack: Postgres dump + the two upload volumes.
#
#   scripts/backup.sh
#
# Writes $BACKUP_DIR/<UTC timestamp>/{db.sql.gz,uploads.tar.gz,private.tar.gz,SHA256SUMS}
# and deletes dated folders older than $BACKUP_KEEP_DAYS. Safe to run from
# cron: no TTY needed, one run at a time (flock), a failed run leaves no
# half-written dated folder, exit status is non-zero on any failure.
#
# Settings come from the environment or from the repo's .env (the same file
# compose uses): BACKUP_DIR, BACKUP_KEEP_DAYS, POSTGRES_USER, POSTGRES_DB.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$REPO_DIR/compose.prod.yml}"
PROJECT="${COMPOSE_PROJECT_NAME:-meetstudent-prod}"

# Read only the few keys we need from .env (never `source` it: values such as
# passwords may contain shell metacharacters).
env_get() {
  local key="$1" file="$REPO_DIR/.env"
  [ -f "$file" ] || return 0
  grep -E "^${key}=" "$file" | tail -n1 | cut -d= -f2- || true
}

BACKUP_DIR="${BACKUP_DIR:-$(env_get BACKUP_DIR)}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/meetstudent}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-$(env_get BACKUP_KEEP_DAYS)}"
KEEP_DAYS="${KEEP_DAYS:-14}"
PGUSER_="${POSTGRES_USER:-$(env_get POSTGRES_USER)}"
PGUSER_="${PGUSER_:-meetstudent}"
PGDB_="${POSTGRES_DB:-$(env_get POSTGRES_DB)}"
PGDB_="${PGDB_:-meetstudent}"

case "$KEEP_DAYS" in ''|*[!0-9]*) echo "BACKUP_KEEP_DAYS must be a number" >&2; exit 2;; esac

compose() { docker compose -p "$PROJECT" -f "$COMPOSE_FILE" "$@"; }

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

# One run at a time.
exec 9>"$BACKUP_DIR/.lock"
if ! flock -n 9; then
  echo "another backup is running, aborting" >&2
  exit 1
fi

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
FINAL="$BACKUP_DIR/$STAMP"
TMP="$BACKUP_DIR/.tmp-$STAMP"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP"

echo "[$STAMP] dumping database $PGDB_"
# Credentials come from the container's own environment; -T = no TTY for cron.
compose exec -T db pg_dump -U "$PGUSER_" -d "$PGDB_" --no-owner --no-privileges \
  | gzip -9 > "$TMP/db.sql.gz"
gzip -t "$TMP/db.sql.gz"
[ "$(gzip -dc "$TMP/db.sql.gz" | head -c 1 | wc -c)" -gt 0 ] || { echo "empty dump" >&2; exit 1; }

# The postgres image is already present on the host and ships busybox tar.
TAR_IMAGE="${TAR_IMAGE:-postgres:18-alpine}"
for name in uploads private; do
  echo "[$STAMP] archiving volume api-$name"
  docker run --rm --entrypoint tar \
    -v "${PROJECT}_api-${name}:/src:ro" "$TAR_IMAGE" \
    -C /src -czf - . > "$TMP/$name.tar.gz"
  gzip -t "$TMP/$name.tar.gz"
done

(cd "$TMP" && sha256sum db.sql.gz uploads.tar.gz private.tar.gz > SHA256SUMS)
chmod 600 "$TMP"/*
mv "$TMP" "$FINAL"
trap - EXIT

echo "[$STAMP] backup complete: $FINAL"

# Retention: only dated folders created by this script.
find "$BACKUP_DIR" -maxdepth 1 -type d -name '20??????T??????Z' -mtime "+$KEEP_DAYS" \
  -exec rm -rf {} + -print | sed 's/^/pruned: /'
