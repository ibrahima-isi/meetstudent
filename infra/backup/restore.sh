#!/bin/sh
# Restore replaces the target database and media. Stop the API first.
set -eu
set -o pipefail
umask 077

REMOTE=${BACKUP_REMOTE:-r2crypt:}
UPLOADS_DIR=${RESTORE_UPLOADS_DIR:-/restore/uploads}
PRIVATE_DIR=${RESTORE_PRIVATE_DIR:-/restore/private}

usage() { echo "usage: restore.sh --list | restore.sh <backup|latest> --yes" >&2; exit 2; }

. "$(dirname "$0")/backup-env.sh"

backups() {
  rclone lsf -R --files-only --include '/*/SHA256SUMS' "$REMOTE" \
    | sed -n 's#^\([0-9]\{8\}T[0-9]\{6\}Z\)/SHA256SUMS$#\1#p' | sort
}

case ${1:-} in
  --list) [ "$#" -eq 1 ] || usage; backups; exit 0 ;;
  '') usage ;;
esac
[ "$#" -eq 2 ] && [ "$2" = --yes ] || usage
NAME=$1
[ "$NAME" != latest ] || NAME=$(backups | tail -n 1)
[ -n "$NAME" ] || { echo "restore: no backup found" >&2; exit 1; }
case "$NAME" in *[!0-9TZ]*) fail_config 'invalid backup ID' ;; esac
printf '%s\n' "$NAME" | grep -qxE '[0-9]{8}T[0-9]{6}Z' || fail_config 'invalid backup ID'
configure_media

WORK=$(mktemp -d /tmp/restore.XXXXXX)
trap 'rm -rf "$WORK"' EXIT
trap 'exit 130' INT TERM
mkdir -p "$WORK/media/uploads" "$WORK/media/private"

echo "downloading $REMOTE$NAME"
rclone copy "$REMOTE$NAME" "$WORK" --include /SHA256SUMS --include /db.dump \
  --include /uploads.tar.gz --include /private.tar.gz --include /inventory.json
(
  cd "$WORK"
  # Check exactly the supported layouts. The legacy format has three entries.
  awk '
    length($1) != 64 || $1 ~ /[^0-9a-f]/ || NF != 2 { bad = 1 }
    $2 !~ /^(db\.dump|uploads\.tar\.gz|private\.tar\.gz|inventory\.json)$/ { bad = 1 }
    seen[$2]++ { bad = 1 }
    END { exit(bad || !seen["db.dump"] || !seen["uploads.tar.gz"] || !seen["private.tar.gz"]) }
  ' SHA256SUMS
  sha256sum -c SHA256SUMS
  pg_restore --list db.dump >/dev/null
)

# Extract both archives in staging before changing the target. The original
# archive layout uses paths relative to the uploads and private volume roots.
python3 - "$WORK" <<'PYTHON'
import sys
import tarfile
from pathlib import Path, PurePosixPath

root = Path(sys.argv[1])
for name in ("uploads", "private"):
    with tarfile.open(root / f"{name}.tar.gz", "r:gz") as archive:
        destination = root / "media" / name
        for entry in archive:
            path = PurePosixPath(entry.name)
            if path.is_absolute() or ".." in path.parts or not (entry.isfile() or entry.isdir()):
                sys.exit("unsafe media archive member")
            archive.extract(entry, destination, filter="data")
PYTHON
mkdir -p "$WORK/media/uploads/public" "$WORK/objects"
if awk '$2 == "inventory.json" { present = 1 } END { exit !present }' "$WORK/SHA256SUMS"; then
  # Use real directories in the verification tree. A rename within staging
  # keeps even a large restore from duplicating the files.
  mv "$WORK/media/uploads/public" "$WORK/objects/public"
  mv "$WORK/media/private" "$WORK/objects/private"
  verify_inventory "$WORK/inventory.json" "$WORK/objects"
  mv "$WORK/objects/public" "$WORK/media/uploads/public"
  mv "$WORK/objects/private" "$WORK/media/private"
fi

if [ "$MEDIA_BACKEND" = s3 ]; then
  # List before copying, and use immutable writes in case another writer appears.
  rclone lsf -R "$MEDIA_REMOTE" >"$WORK/destination.list"
  [ ! -s "$WORK/destination.list" ] || fail_config 'S3 restore requires an empty destination bucket'
  for prefix in public private; do
    src="$WORK/media/$prefix"
    [ "$prefix" != public ] || src="$WORK/media/uploads/public"
    rclone copy "$src" "$MEDIA_REMOTE/$prefix" --immutable
    if [ -n "$(find "$src" -type f -print -quit)" ]; then
      rclone check "$src" "$MEDIA_REMOTE/$prefix" --download
    fi
  done
else
  for dir in "$UPLOADS_DIR" "$PRIVATE_DIR"; do
    probe=$(mktemp "$dir/.restore-probe.XXXXXX") || fail_config 'restore requires writable mounts under /restore'
    rm "$probe"
  done
  [ "$(realpath "$UPLOADS_DIR")" != "$(realpath "$PRIVATE_DIR")" ] || fail_config 'restore requires separate media mounts'
fi

echo "recreating database"
psql -X -v ON_ERROR_STOP=1 -v target="$PGDATABASE" -v owner="$PGUSER" -d postgres <<'SQL'
DROP DATABASE IF EXISTS :"target" WITH (FORCE);
CREATE DATABASE :"target" OWNER :"owner";
SQL
pg_restore --single-transaction --exit-on-error --no-owner --no-privileges -d "$PGDATABASE" "$WORK/db.dump"

if [ "$MEDIA_BACKEND" = disk ]; then
  find "$UPLOADS_DIR" -mindepth 1 -delete
  find "$PRIVATE_DIR" -mindepth 1 -delete
  cp -a "$WORK/media/uploads/." "$UPLOADS_DIR/"
  cp -a "$WORK/media/private/." "$PRIVATE_DIR/"
fi

echo "restore of $NAME complete; start the api again"
