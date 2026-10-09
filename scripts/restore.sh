#!/usr/bin/env bash
# Restore a backup made by scripts/backup.sh. DESTRUCTIVE: replaces the current
# database and upload volumes.
#
#   scripts/restore.sh /var/backups/meetstudent/20260101T030000Z [--yes]
#
# Steps: verify checksums, stop api+web, recreate the database from db.sql.gz,
# empty and refill both volumes, start api+web again.
set -euo pipefail

SRC="${1:-}"
[ -d "$SRC" ] || { echo "usage: $0 <backup-dir> [--yes]" >&2; exit 2; }
SRC="$(cd "$SRC" && pwd)"

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$REPO_DIR/compose.prod.yml}"
PROJECT="${COMPOSE_PROJECT_NAME:-meetstudent-prod}"
TAR_IMAGE="${TAR_IMAGE:-postgres:18-alpine}"

env_get() {
  local key="$1" file="$REPO_DIR/.env"
  [ -f "$file" ] || return 0
  grep -E "^${key}=" "$file" | tail -n1 | cut -d= -f2- || true
}
PGUSER_="${POSTGRES_USER:-$(env_get POSTGRES_USER)}"; PGUSER_="${PGUSER_:-meetstudent}"
PGDB_="${POSTGRES_DB:-$(env_get POSTGRES_DB)}";       PGDB_="${PGDB_:-meetstudent}"

compose() { docker compose -p "$PROJECT" -f "$COMPOSE_FILE" "$@"; }

for f in db.sql.gz uploads.tar.gz private.tar.gz SHA256SUMS; do
  [ -f "$SRC/$f" ] || { echo "missing $SRC/$f" >&2; exit 1; }
done
(cd "$SRC" && sha256sum -c SHA256SUMS)

if [ "${2:-}" != "--yes" ]; then
  echo "This will ERASE database '$PGDB_' and both upload volumes of project '$PROJECT'"
  echo "and replace them with $SRC"
  read -r -p "Type 'restore' to continue: " answer
  [ "$answer" = "restore" ] || { echo "aborted"; exit 1; }
fi

echo "stopping api and web"
compose stop api web >/dev/null 2>&1 || true

echo "ensuring db is up"
compose up -d db
for _ in $(seq 1 30); do
  compose exec -T db pg_isready -U "$PGUSER_" -d postgres >/dev/null 2>&1 && break
  sleep 2
done

echo "recreating database $PGDB_"
compose exec -T db psql -v ON_ERROR_STOP=1 -U "$PGUSER_" -d postgres \
  -c "DROP DATABASE IF EXISTS \"$PGDB_\" WITH (FORCE)" \
  -c "CREATE DATABASE \"$PGDB_\" OWNER \"$PGUSER_\""
gzip -dc "$SRC/db.sql.gz" \
  | compose exec -T db psql -v ON_ERROR_STOP=1 -q -U "$PGUSER_" -d "$PGDB_" >/dev/null

for name in uploads private; do
  echo "restoring volume api-$name"
  docker run --rm -i --entrypoint sh \
    -v "${PROJECT}_api-${name}:/dst" "$TAR_IMAGE" \
    -c 'find /dst -mindepth 1 -delete && tar -xzf - -C /dst' < "$SRC/$name.tar.gz"
done

echo "starting api and web"
compose up -d api web
echo "restore complete. Check: docker compose -f compose.prod.yml ps"
