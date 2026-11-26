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
# In S3 mode (MEDIA_STORAGE_BACKEND=s3): restore to an empty MEDIA_S3_BUCKET.
set -eu
set -o pipefail
umask 077

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
  --list) [ "$#" -eq 1 ] || usage; backups; exit 0 ;;
  '') usage ;;
esac
NAME=$1
[ "$#" -eq 2 ] && [ "$2" = --yes ] || {
  echo "This ERASES database '$PGDATABASE' on $PGHOST and active media ($MEDIA_STORAGE_BACKEND), and replaces them" >&2
  echo "with backup '$NAME'. Run again with --yes to proceed." >&2
  exit 2
}
[ "$NAME" != latest ] || NAME=$(backups | tail -n 1)
[ -n "$NAME" ] || { echo "restore: no backup found on $REMOTE" >&2; exit 1; }

case "$NAME" in *[!0-9TZ]*) echo "restore: invalid backup ID" >&2; exit 2 ;; esac
printf '%s\n' "$NAME" | grep -qxE '[0-9]{8}T[0-9]{6}Z' || { echo "restore: invalid backup ID" >&2; exit 2; }

mkdir -p "$(dirname "$LOCK_FILE")"
exec 9>"$LOCK_FILE"
flock 9

WORK=$(mktemp -d /tmp/restore.XXXXXX)
trap 'rm -rf "$WORK"' EXIT
trap 'exit 130' INT TERM

mkdir -p "$WORK/archive" "$WORK/stage/uploads" "$WORK/stage/private"
echo "downloading $REMOTE$NAME"
rclone copy "$REMOTE$NAME" "$WORK/archive" --include /SHA256SUMS --include /db.dump \
  --include /uploads.tar.gz --include /private.tar.gz --include /media-manifest.tsv --include /inventory.json
(
  cd "$WORK/archive"
  awk '
    length($1) != 64 || $1 ~ /[^0-9a-f]/ || NF != 2 { bad = 1 }
    $2 !~ /^(db[.]dump|uploads[.]tar[.]gz|private[.]tar[.]gz|media-manifest[.]tsv|inventory[.]json)$/ { bad = 1 }
    seen[$2]++ { bad = 1 }
    END { exit(bad || !seen["db.dump"] || !seen["uploads.tar.gz"] || !seen["private.tar.gz"]) }
  ' SHA256SUMS || { echo "restore: invalid SHA256SUMS" >&2; exit 1; }
  sha256sum -c SHA256SUMS
  # Sidecars are trusted only when the completion marker covers them.
  for sidecar in media-manifest.tsv inventory.json; do
    if ! awk -v name="$sidecar" '$2 == name { present = 1 } END { exit !present }' SHA256SUMS; then
      rm -f "$sidecar"
    fi
  done
  pg_restore --list db.dump >"$WORK/dump.toc"
)

python3 - "$WORK" <<'PYTHON'
import json
import sys
import tarfile
from pathlib import Path, PurePosixPath

root = Path(sys.argv[1])
for name in ("uploads", "private"):
    with tarfile.open(root / "archive" / f"{name}.tar.gz", "r:gz") as archive:
        destination = root / "stage" / name
        for entry in archive:
            path = PurePosixPath(entry.name)
            if path.is_absolute() or ".." in path.parts or not (entry.isfile() or entry.isdir()):
                sys.exit("restore: unsafe media archive member")
            archive.extract(entry, destination, filter="data")

inventory = root / "archive" / "inventory.json"
if inventory.is_file():
    rows = json.loads(inventory.read_text())
    if not isinstance(rows, list):
        sys.exit("restore: invalid JSON media inventory")
    for row in rows:
        key = row["key"]
        visibility = row["visibility"]
        if (visibility not in {"PUBLIC", "PRIVATE"} or not key.startswith(visibility.lower() + "/")
                or any(part in {"", ".", ".."} for part in key.split("/"))
                or any(ord(char) < 32 or char == chr(92) for char in key)):
            sys.exit("restore: invalid media key in JSON inventory")
        path = root / "stage" / ("uploads" if visibility == "PUBLIC" else "") / key
        if not path.is_file() or (row["size"] is not None and path.stat().st_size != row["size"]):
            sys.exit("restore: media inventory is incomplete or has a size mismatch")
PYTHON
if [ -f "$WORK/archive/media-manifest.tsv" ]; then
  manifest_scan_media_roots "$WORK/stage/uploads" "$WORK/stage/private" "$WORK/scanned.tsv"
  manifest_verify_tree "$WORK/archive/media-manifest.tsv" "$WORK/scanned.tsv"
fi

if [ "$MEDIA_STORAGE_BACKEND" = "s3" ]; then
  mkdir -p "$WORK/s3_root/public"
  if [ -d "$WORK/stage/uploads/public" ]; then
    (cd "$WORK/stage/uploads/public" && tar --exclude='.media-*.tmp' -cf - .) \
      | (cd "$WORK/s3_root/public" && tar -xf -)
  fi
  mv "$WORK/stage/private" "$WORK/s3_root/private"
  find "$WORK/s3_root" -name '.media-*.tmp' -type f -delete
  manifest_scan_tree "$WORK/s3_root" "" "$WORK/s3_expected.tsv"

  # Pre-validate all database media rows BEFORE dropping or recreating $PGDATABASE.
  : > "$WORK/precheck_db_media.tsv"
  if [ -f "$WORK/archive/media-manifest.tsv" ]; then
    LC_ALL=C awk -F '\t' '
      BEGIN {
        for (i = 0; i < 256; i++) chr_of[sprintf("%02x", i)] = sprintf("%c", i)
      }
      function from_hex(h,   i, out) {
        out = ""
        for (i = 1; i <= length(h); i += 2) out = out chr_of[tolower(substr(h, i, 2))]
        return out
      }
      $0 !~ /^#/ && $1 == "db" {
        printf "%s\t%s\t%s\t%s\n", $2, $3, $7, from_hex($7)
      }
    ' "$WORK/archive/media-manifest.tsv" > "$WORK/precheck_db_media.tsv"
  elif [ ! -f "$WORK/archive/inventory.json" ] && grep -qE '[[:space:]]TABLE DATA[[:space:]]+[^ ]+[[:space:]]+"?media"?([[:space:]]|$)' "$WORK/dump.toc"; then
    pg_restore -f - --table=media --data-only "$WORK/archive/db.dump" \
      | LC_ALL=C awk '
          BEGIN {
            FS = "\t"
            in_copy = 0
            for (i = 0; i < 256; i++) hex_of[sprintf("%c", i)] = sprintf("%02x", i)
          }
          function to_hex(s,   i, out) {
            out = ""
            for (i = 1; i <= length(s); i++) out = out hex_of[substr(s, i, 1)]
            return out
          }
          /^COPY ([^ ]+\.)?"?media"? \(/ && /FROM stdin;$/ {
            in_copy = 1
            cols_part = $0
            sub(/^COPY ([^ ]+\.)?"?media"? \(/, "", cols_part)
            sub(/\) FROM stdin;$/, "", cols_part)
            n = split(cols_part, cols, /, */)
            id_col = 0; key_col = 0; vis_col = 0
            for (i = 1; i <= n; i++) {
              gsub(/^"|"$|[[:space:]]/, "", cols[i])
              if (cols[i] == "id") id_col = i
              else if (cols[i] == "storage_key") key_col = i
              else if (cols[i] == "visibility") vis_col = i
            }
            if (id_col == 0 || key_col == 0 || vis_col == 0) {
              printf "restore: missing id, storage_key, or visibility column in media COPY header\n" > "/dev/stderr"
              exit 1
            }
            next
          }
          in_copy && /^\\\.$/ { in_copy = 0; next }
          in_copy && NF > 0 {
            printf "%s\t%s\t%s\t%s\n", $(id_col), $(vis_col), to_hex($(key_col)), $(key_col)
          }
        ' > "$WORK/precheck_db_media.tsv"
  fi

  LC_ALL=C awk '
    BEGIN { FS = "\t"; err = 0 }
    function is_valid_s3_key(key, vis,   n, segs, i) {
      if (key == "" || key == "\\N") return 0
      if (index(key, "\\") > 0) return 0
      if (vis == "PRIVATE" && substr(key, 1, 8) != "private/") return 0
      if (vis == "PUBLIC" && substr(key, 1, 7) != "public/") return 0
      if (substr(key, 1, 7) != "public/" && substr(key, 1, 8) != "private/") return 0
      n = split(key, segs, "/")
      if (n < 2) return 0
      for (i = 1; i <= n; i++) {
        if (segs[i] == "" || segs[i] == "." || segs[i] == "..") return 0
      }
      return 1
    }
    FILENAME == ARGV[1] {
      if (NF >= 1) have[$1] = 1
      next
    }
    FILENAME == ARGV[2] {
      if (NF < 4) next
      id = $1; vis = $2; khex = $3; kraw = $4
      if (!is_valid_s3_key(kraw, vis)) {
        printf "restore: incompatible media id=%s visibility=%s key=%s for S3 mode (requires valid public/ or private/ key)\n", id, vis, kraw > "/dev/stderr"
        err = 1
      } else if (!(khex in have)) {
        printf "restore: database media id=%s key=%s is missing from archive media payload\n", id, kraw > "/dev/stderr"
        err = 1
      }
      next
    }
    END { if (err) exit 1 }
  ' "$WORK/s3_expected.tsv" "$WORK/precheck_db_media.tsv"
  rclone lsf -R "media:$MEDIA_S3_BUCKET" >"$WORK/destination.list"
  [ ! -s "$WORK/destination.list" ] || { echo "restore: S3 destination bucket must be empty" >&2; exit 2; }
  for prefix in public private; do
    src="$WORK/s3_root/$prefix"
    if [ -n "$(find "$src" -type f -print -quit)" ]; then
      echo "restoring s3://$MEDIA_S3_BUCKET/$prefix"
      rclone copy --immutable "$src" "media:$MEDIA_S3_BUCKET/$prefix"
      rclone check --download "$src" "media:$MEDIA_S3_BUCKET/$prefix"
    fi
  done
else
  for dir in "$UPLOADS_DIR" "$PRIVATE_DIR"; do
    probe=$(mktemp "$dir/.restore-probe.XXXXXX") || {
      echo "restore: writable media mounts are required" >&2
      exit 2
    }
    rm "$probe"
  done
  [ "$(realpath "$UPLOADS_DIR")" != "$(realpath "$PRIVATE_DIR")" ] || {
    echo "restore: separate media mounts are required" >&2
    exit 2
  }
fi

echo "recreating database"
psql -X -v ON_ERROR_STOP=1 -v target="$PGDATABASE" -v owner="$PGUSER" -d postgres <<'SQL'
DROP DATABASE IF EXISTS :"target" WITH (FORCE);
CREATE DATABASE :"target" OWNER :"owner";
SQL
pg_restore --single-transaction --exit-on-error --no-owner --no-privileges -d "$PGDATABASE" "$WORK/archive/db.dump"

if [ "$MEDIA_STORAGE_BACKEND" = "disk" ]; then
  find "$UPLOADS_DIR" -mindepth 1 -delete
  find "$PRIVATE_DIR" -mindepth 1 -delete
  cp -a "$WORK/stage/uploads/." "$UPLOADS_DIR/"
  cp -a "$WORK/stage/private/." "$PRIVATE_DIR/"
  find "$UPLOADS_DIR" "$PRIVATE_DIR" -name '.media-*.tmp' -type f -delete
fi

echo "restore of $NAME complete; start the api again"
