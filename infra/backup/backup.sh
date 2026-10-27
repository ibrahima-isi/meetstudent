#!/bin/sh
# Encrypted off-site backup of the Coolify stack (ADR 0004, coolify-deploy/03).
#
#   backup.sh      # run nightly by a Coolify Scheduled Task in the `backup` service
#
# One backup = a folder named after its UTC time (20261027T031500Z) holding
#   db.dump          pg_dump of the database, custom format
#   uploads.tar.gz   the public upload volume
#   private.tar.gz   the private upload volume (students' documents)
#   SHA256SUMS
# written to $BACKUP_REMOTE (default r2crypt:, an rclone crypt remote wrapping
# the S3 bucket): names and contents are encrypted before they leave the VPS.
# Retention is the bucket's lifecycle rule, not this script's business.
#
# The local copy lives in a temporary folder that is removed whatever happens.
# Exit status is non-zero on any failure, so Coolify notifies the owner.
#
# Environment (set by compose.coolify.yml): PGHOST, PGUSER, PGPASSWORD,
# PGDATABASE; RCLONE_CONFIG_R2_* and RCLONE_CONFIG_R2CRYPT_* for the remotes;
# BACKUP_CRYPT_PASSWORD and BACKUP_CRYPT_SALT, the originals of the crypt
# password and salt, which rclone receives obscured.
set -eu
set -o pipefail

REMOTE=${BACKUP_REMOTE:-r2crypt:}
UPLOADS_DIR=${UPLOADS_DIR:-/data/uploads}
PRIVATE_DIR=${PRIVATE_DIR:-/data/private}

missing=
for var in PGHOST PGUSER PGPASSWORD PGDATABASE RCLONE_CONFIG_R2_ENDPOINT RCLONE_CONFIG_R2_ACCESS_KEY_ID \
  RCLONE_CONFIG_R2_SECRET_ACCESS_KEY RCLONE_CONFIG_R2CRYPT_REMOTE BACKUP_CRYPT_PASSWORD BACKUP_CRYPT_SALT; do
  eval "[ -n \"\${$var:-}\" ]" || missing="$missing $var"
done
case ${RCLONE_CONFIG_R2CRYPT_REMOTE:-} in *:) missing="$missing R2_BUCKET" ;; esac
[ -z "$missing" ] || { echo "backup: not configured, missing:$missing" >&2; exit 2; }

RCLONE_CONFIG_R2CRYPT_PASSWORD=$(printf '%s' "$BACKUP_CRYPT_PASSWORD" | rclone obscure -)
RCLONE_CONFIG_R2CRYPT_PASSWORD2=$(printf '%s' "$BACKUP_CRYPT_SALT" | rclone obscure -)
export RCLONE_CONFIG_R2CRYPT_PASSWORD RCLONE_CONFIG_R2CRYPT_PASSWORD2

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
WORK=$(mktemp -d /tmp/backup.XXXXXX)
trap 'rm -rf "$WORK"' EXIT
trap 'exit 130' INT TERM

echo "[$STAMP] dumping database $PGDATABASE"
pg_dump --format=custom --no-owner --no-privileges --file="$WORK/db.dump"
# A dump pg_restore cannot read is not a backup.
pg_restore --list "$WORK/db.dump" >/dev/null

for name in uploads private; do
  [ "$name" = uploads ] && src=$UPLOADS_DIR || src=$PRIVATE_DIR
  echo "[$STAMP] archiving $src"
  tar -czf "$WORK/$name.tar.gz" -C "$src" .
done

(cd "$WORK" && sha256sum db.dump uploads.tar.gz private.tar.gz >SHA256SUMS)

echo "[$STAMP] sending to $REMOTE$STAMP"
rclone copy "$WORK" "$REMOTE$STAMP"

echo "[$STAMP] backup complete"
