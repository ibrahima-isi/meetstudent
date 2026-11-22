#!/bin/sh
# Restore a backup made by backup.sh (ADR 0004, ADR 0005).
# DESTRUCTIVE: replaces the database and the media storage (disk volumes or S3 bucket).
# Supports both new archives (with media-manifest.tsv) and historical disk-format
# archives (db.dump + uploads.tar.gz + private.tar.gz + SHA256SUMS).
#
#   restore.sh [--remote <remote>] --list
#   restore.sh [--remote <remote>] <20261027T031500Z|latest> --yes
#
# In disk mode (default): mount api-uploads at /restore/uploads and api-private at /restore/private.
# In S3 mode (MEDIA_STORAGE_BACKEND=s3): no volume mounts are required; media is synced to MEDIA_S3_BUCKET.
set -eu
set -o pipefail

UPLOADS_DIR=${RESTORE_UPLOADS_DIR:-/restore/uploads}
PRIVATE_DIR=${RESTORE_PRIVATE_DIR:-/restore/private}
LOCK_FILE=${BACKUP_LOCK_FILE:-/var/lock/meetstudent-backup.lock}

usage() { echo "usage: restore.sh [--remote <remote>] --list | restore.sh [--remote <remote>] <backup|latest> --yes" >&2; exit 2; }

REMOTE_ARG=
while [ $# -gt 0 ]; do
  case $1 in
    --remote)
      [ $# -ge 2 ] || usage
      REMOTE_ARG=$2
      shift 2
      ;;
    --remote=*)
      REMOTE_ARG=${1#--remote=}
      shift
      ;;
    *) break ;;
  esac
done

# Configuration check (exit 2) and crypt password for rclone, shared with backup.sh.
. "$(dirname "$0")/backup-env.sh"
. "$(dirname "$0")/media-manifest.sh"
REMOTE=${REMOTE_ARG:-${BACKUP_REMOTE:-$DEFAULT_BACKUP_REMOTE}}
backup_env_assert_remote_configured "$REMOTE"

# Complete backups only: backup.sh sends SHA256SUMS last, so a folder without
# it is an interrupted backup. sed -n, not grep: with pipefail a grep matching
# nothing would kill the caller before it can say "no backup found".
backups() {
  rclone lsf -R --files-only --include '/*/SHA256SUMS' "$REMOTE" \
    | sed -n 's#^\([0-9]\{8\}T[0-9]\{6\}Z\)/SHA256SUMS$#\1#p' | sort
}

case ${1:-} in
  --list) backups; exit 0 ;;
  '') usage ;;
esac
NAME=$1
[ "${2:-}" = --yes ] || {
  echo "This ERASES database '$PGDATABASE' on $PGHOST and active media ($MEDIA_STORAGE_BACKEND), and replaces them" >&2
  echo "with backup '$NAME'. Run again with --yes to proceed." >&2
  exit 2
}
[ "$NAME" != latest ] || NAME=$(backups | tail -n 1)
[ -n "$NAME" ] || { echo "restore: no backup found on $REMOTE" >&2; exit 1; }

mkdir -p "$(dirname "$LOCK_FILE")"
exec 9>"$LOCK_FILE"
flock 9

if [ "$MEDIA_STORAGE_BACKEND" = "disk" ]; then
  for dir in "$UPLOADS_DIR" "$PRIVATE_DIR"; do
    probe="$dir/.restore-probe"
    touch "$probe" 2>/dev/null && rm -f "$probe" || {
      echo "restore: $dir is not a writable mount; mount the upload volumes there (see the usage)" >&2
      exit 1
    }
  done
else
  rclone lsf "media:$MEDIA_S3_BUCKET" --max-depth 1 >/dev/null || {
    echo "restore: cannot reach S3 destination media bucket '$MEDIA_S3_BUCKET'" >&2
    exit 1
  }
fi

WORK=$(mktemp -d /tmp/restore.XXXXXX)
trap 'rm -rf "$WORK"' EXIT
trap 'exit 130' INT TERM

mkdir -p "$WORK/archive" "$WORK/stage/uploads" "$WORK/stage/private"
echo "downloading $REMOTE$NAME"
rclone copy "$REMOTE$NAME" "$WORK/archive"
(cd "$WORK/archive" && sha256sum -c SHA256SUMS)

tar -xzf "$WORK/archive/uploads.tar.gz" -C "$WORK/stage/uploads"
tar -xzf "$WORK/archive/private.tar.gz" -C "$WORK/stage/private"
if [ -f "$WORK/archive/media-manifest.tsv" ]; then
  manifest_scan_media_roots "$WORK/stage/uploads" "$WORK/stage/private" "$WORK/scanned.tsv"
  manifest_verify_tree "$WORK/archive/media-manifest.tsv" "$WORK/scanned.tsv"
fi

if [ "$MEDIA_STORAGE_BACKEND" = "s3" ]; then
  mkdir -p "$WORK/s3_root/public" "$WORK/s3_root/private"
  if [ -d "$WORK/stage/uploads/public" ]; then
    (cd "$WORK/stage/uploads/public" && tar --exclude='.media-*.tmp' -cf - .) \
      | (cd "$WORK/s3_root/public" && tar -xf -)
  fi
  if [ -d "$WORK/stage/private" ]; then
    (cd "$WORK/stage/private" && tar --exclude='.media-*.tmp' -cf - .) \
      | (cd "$WORK/s3_root/private" && tar -xf -)
  fi
  find "$WORK/s3_root" -type l -delete
  manifest_scan_tree "$WORK/s3_root" "" "$WORK/s3_expected.tsv"
fi

echo "recreating database $PGDATABASE"
psql -v ON_ERROR_STOP=1 -d postgres \
  -c "DROP DATABASE IF EXISTS \"$PGDATABASE\" WITH (FORCE)" \
  -c "CREATE DATABASE \"$PGDATABASE\" OWNER \"$PGUSER\""
pg_restore --exit-on-error --no-owner --no-privileges -d "$PGDATABASE" "$WORK/archive/db.dump"

if [ "$MEDIA_STORAGE_BACKEND" = "disk" ]; then
  for name in uploads private; do
    [ "$name" = uploads ] && dst=$UPLOADS_DIR || dst=$PRIVATE_DIR
    echo "restoring $dst"
    find "$dst" -mindepth 1 -delete
    tar -xzf "$WORK/archive/$name.tar.gz" -C "$dst"
    find "$dst" -name '.media-*.tmp' -type f -delete
  done
else
  # Verify every DB media row maps to a valid public/ or private/ key present in the archive.
  psql -X -A -t -v ON_ERROR_STOP=1 -d "$PGDATABASE" \
    -c "SELECT id, encode(convert_to(storage_key, 'UTF8'), 'hex') FROM media ORDER BY id" > "$WORK/restored_db_media.tsv"
  awk '
    BEGIN { FS = "\t"; err = 0 }
    FILENAME == ARGV[1] {
      if (NF >= 1) have[$1] = 1
      next
    }
    FILENAME == ARGV[2] {
      split($0, d, "|")
      if (length(d[1]) == 0) next
      if (!(d[2] in have)) {
        printf "restore: database media id=%s key_hex=%s is missing or lacks public/ or private/ prefix for S3 mode\n", d[1], d[2] > "/dev/stderr"
        err = 1
      }
      next
    }
    END { if (err) exit 1 }
  ' "$WORK/s3_expected.tsv" "$WORK/restored_db_media.tsv"

  for prefix in public private; do
    echo "restoring s3://$MEDIA_S3_BUCKET/$prefix"
    rclone sync "$WORK/s3_root/$prefix" "media:$MEDIA_S3_BUCKET/$prefix"
    if [ "$(find "$WORK/s3_root/$prefix" -type f | wc -l)" -gt 0 ]; then
      rclone check --size-only "$WORK/s3_root/$prefix" "media:$MEDIA_S3_BUCKET/$prefix"
    else
      leftover=$(rclone lsf -R --files-only "media:$MEDIA_S3_BUCKET/$prefix" 2>/dev/null || true)
      [ -z "$leftover" ] || { echo "restore: unexpected objects left under $prefix in $MEDIA_S3_BUCKET" >&2; exit 1; }
    fi
  done
fi

echo "restore of $NAME complete; start the api again"
