#!/bin/sh
# Restore a backup made by backup.sh (ADR 0004, coolify-deploy/03).
# DESTRUCTIVE: replaces the database and the content of both upload volumes.
#
#   restore.sh --list                 # backups available off site, oldest first
#   restore.sh <20261027T031500Z|latest> --yes
#
# The `backup` service mounts the upload volumes read-only at /data. A restore
# writes them at /restore instead: run it as a one-off container of that service
# with both volumes mounted there, the api stopped, then start the api again.
# With the stack's compose project:
#
#   docker compose -p <project> -f compose.coolify.yml stop api
#   docker compose -p <project> -f compose.coolify.yml run --rm \
#     -v api-uploads:/restore/uploads -v api-private:/restore/private \
#     backup restore.sh latest --yes
#   docker compose -p <project> -f compose.coolify.yml start api
#
# Nothing is touched until the backup is downloaded and its checksums match.
# Same environment as backup.sh; the crypt password and salt must be the ones
# the backup was made with.
set -eu
set -o pipefail

REMOTE=${BACKUP_REMOTE:-r2crypt:}
UPLOADS_DIR=${RESTORE_UPLOADS_DIR:-/restore/uploads}
PRIVATE_DIR=${RESTORE_PRIVATE_DIR:-/restore/private}

usage() { echo "usage: restore.sh --list | restore.sh <backup|latest> --yes" >&2; exit 2; }

missing=
for var in PGHOST PGUSER PGPASSWORD PGDATABASE RCLONE_CONFIG_R2_ENDPOINT RCLONE_CONFIG_R2_ACCESS_KEY_ID \
  RCLONE_CONFIG_R2_SECRET_ACCESS_KEY RCLONE_CONFIG_R2CRYPT_REMOTE BACKUP_CRYPT_PASSWORD BACKUP_CRYPT_SALT; do
  eval "[ -n \"\${$var:-}\" ]" || missing="$missing $var"
done
case ${RCLONE_CONFIG_R2CRYPT_REMOTE:-} in *:) missing="$missing R2_BUCKET" ;; esac
[ -z "$missing" ] || { echo "restore: not configured, missing:$missing" >&2; exit 2; }

RCLONE_CONFIG_R2CRYPT_PASSWORD=$(printf '%s' "$BACKUP_CRYPT_PASSWORD" | rclone obscure -)
RCLONE_CONFIG_R2CRYPT_PASSWORD2=$(printf '%s' "$BACKUP_CRYPT_SALT" | rclone obscure -)
export RCLONE_CONFIG_R2CRYPT_PASSWORD RCLONE_CONFIG_R2CRYPT_PASSWORD2

backups() { rclone lsf --dirs-only "$REMOTE" | sed 's#/$##' | grep -E '^[0-9]{8}T[0-9]{6}Z$' | sort; }

case ${1:-} in
  --list) backups; exit 0 ;;
  '') usage ;;
esac
NAME=$1
[ "${2:-}" = --yes ] || {
  echo "This ERASES database '$PGDATABASE' on $PGHOST and both upload volumes, and replaces them" >&2
  echo "with backup '$NAME'. Run again with --yes to proceed." >&2
  exit 2
}
[ "$NAME" != latest ] || NAME=$(backups | tail -n 1)
[ -n "$NAME" ] || { echo "restore: no backup found on $REMOTE" >&2; exit 1; }

for dir in "$UPLOADS_DIR" "$PRIVATE_DIR"; do
  probe="$dir/.restore-probe"
  touch "$probe" 2>/dev/null && rm -f "$probe" || {
    echo "restore: $dir is not a writable mount; mount the upload volumes there (see the usage)" >&2
    exit 1
  }
done

WORK=$(mktemp -d /tmp/restore.XXXXXX)
trap 'rm -rf "$WORK"' EXIT
trap 'exit 130' INT TERM

echo "downloading $REMOTE$NAME"
rclone copy "$REMOTE$NAME" "$WORK"
(cd "$WORK" && sha256sum -c SHA256SUMS)

echo "recreating database $PGDATABASE"
psql -v ON_ERROR_STOP=1 -d postgres \
  -c "DROP DATABASE IF EXISTS \"$PGDATABASE\" WITH (FORCE)" \
  -c "CREATE DATABASE \"$PGDATABASE\" OWNER \"$PGUSER\""
pg_restore --exit-on-error --no-owner --no-privileges -d "$PGDATABASE" "$WORK/db.dump"

for name in uploads private; do
  [ "$name" = uploads ] && dst=$UPLOADS_DIR || dst=$PRIVATE_DIR
  echo "restoring $dst"
  find "$dst" -mindepth 1 -delete
  tar -xzf "$WORK/$name.tar.gz" -C "$dst"
done

echo "restore of $NAME complete; start the api again"
