#!/bin/sh
# Nightly encrypted off-site backup. SHA256SUMS is the completion marker.
set -eu
set -o pipefail
umask 077

REMOTE=${BACKUP_REMOTE:-r2crypt:}
UPLOADS_DIR=${UPLOADS_DIR:-/data/uploads}
PRIVATE_DIR=${PRIVATE_DIR:-/data/private}
. "$(dirname "$0")/backup-env.sh"
configure_media
if [ "$MEDIA_BACKEND" = s3 ] && [ "$media_provider" = "$backup_provider" ]; then
  fail_config "active media and backups must use independent providers"
fi

# Keep the lock outside /tmp so a killed run leaves only its disposable staging data.
mkdir -p /run/lock
exec 9>/run/lock/meetstudent-backup
flock -n 9 || { echo "another backup is running" >&2; exit 1; }
rm -rf /tmp/backup.*
WORK=$(mktemp -d /tmp/backup.XXXXXX)
export WORK
trap 'rm -rf "$WORK"' EXIT
trap 'exit 130' INT TERM

capture_database() {
  psql -X -qAt -v ON_ERROR_STOP=1 >"$WORK/archive/inventory.json" <<'SQL'
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SELECT pg_export_snapshot() AS snapshot \gset
SELECT coalesce(json_agg(json_build_object(
    'key', storage_key, 'size', size_bytes, 'visibility', visibility
) ORDER BY storage_key, id), '[]'::json)
FROM public.media;
\setenv MEDIA_BACKUP_SNAPSHOT :snapshot
\! pg_dump --snapshot="$MEDIA_BACKUP_SNAPSHOT" --format=custom --no-owner --no-privileges --file="$WORK/archive/db.dump"
\if :SHELL_ERROR
  SELECT 1 / 0;
\endif
COMMIT;
SQL
}

capture_media() {
  inventory="$WORK/archive/inventory.json"
  verify_inventory "$inventory" "" || return 1
  if [ "$MEDIA_BACKEND" = s3 ]; then
    public_source=$MEDIA_REMOTE
    private_source=$MEDIA_REMOTE
    private_target="$WORK/media"
    jq -r '.[] | select(.visibility == "PRIVATE") | .key' "$inventory" >"$WORK/private.keys" || return 1
  else
    public_source=$UPLOADS_DIR
    private_source=$PRIVATE_DIR
    private_target="$WORK/media/private"
    jq -r '.[] | select(.visibility == "PRIVATE") | .key | ltrimstr("private/")' "$inventory"       >"$WORK/private.keys" || return 1
  fi

  rclone copy "$public_source" "$WORK/media" --include '/public/**' || return 1
  if [ -s "$WORK/private.keys" ]; then
    rclone copy "$private_source" "$private_target" --files-from-raw "$WORK/private.keys" --no-traverse || return 1
    rclone check "$private_target" "$private_source" --files-from-raw "$WORK/private.keys"       --one-way --download || return 1
  fi
  rclone check "$WORK/media" "$public_source" --include '/public/**' --download || return 1
  verify_inventory "$inventory" "$WORK/media"
}

# A deletion or a replacement after the snapshot can invalidate it. Start over
# from a fresh snapshot instead of publishing a dump with a missing file.
for attempt in 1 2 3; do
  rm -rf "$WORK/archive" "$WORK/media"
  mkdir -p "$WORK/archive" "$WORK/media/public" "$WORK/media/private"
  echo "backup attempt $attempt of 3"
  if capture_database && pg_restore --list "$WORK/archive/db.dump" >/dev/null && capture_media; then
    break
  fi
  [ "$attempt" -lt 3 ] || { echo "backup remained incomplete" >&2; exit 1; }
  sleep 2
done

tar -czf "$WORK/archive/uploads.tar.gz" -C "$WORK/media" public
tar -czf "$WORK/archive/private.tar.gz" -C "$WORK/media/private" .
(cd "$WORK/archive" && sha256sum db.dump uploads.tar.gz private.tar.gz inventory.json >"$WORK/SHA256SUMS")

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
echo "[$STAMP] sending encrypted backup"
rclone copy "$WORK/archive" "$REMOTE$STAMP" --immutable
rclone check "$WORK/archive" "$REMOTE$STAMP" --one-way --download
rclone copyto "$WORK/SHA256SUMS" "$REMOTE$STAMP/SHA256SUMS" --immutable
echo "[$STAMP] backup complete"
