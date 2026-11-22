#!/bin/sh
# Encrypted off-site backup of the Coolify stack (ADR 0004, ADR 0005).
#
#   backup.sh      # run nightly by a Coolify Scheduled Task in the `backup` service
#
# One backup = a folder named after its UTC time (20261027T031500Z) holding
#   db.dump             pg_dump of the database, custom format (shared PG snapshot)
#   uploads.tar.gz      public media and legacy uploads (excluding .media-*.tmp)
#   private.tar.gz      private documents (excluding .media-*.tmp)
#   media-manifest.tsv  snapshot inventory with per-file byte sizes and SHA-256 checksums
#   SHA256SUMS          sent last: a folder without it is an interrupted backup
# written to $BACKUP_REMOTE (default backupcrypt: when BACKUP_S3_* is set, else
# r2crypt:): names and contents are encrypted before they leave the VPS.
# Retention is the bucket's lifecycle rule (30 days EU), not this script's business.
set -eu
set -o pipefail

UPLOADS_DIR=${UPLOADS_DIR:-/data/uploads}
PRIVATE_DIR=${PRIVATE_DIR:-/data/private}
LOCK_FILE=${BACKUP_LOCK_FILE:-/var/lock/meetstudent-backup.lock}
MAX_ATTEMPTS=${BACKUP_MAX_ATTEMPTS:-3}
RETRY_DELAY=${BACKUP_RETRY_DELAY:-2}

# Configuration check (exit 2) and crypt password for rclone, shared with restore.sh.
. "$(dirname "$0")/backup-env.sh"
. "$(dirname "$0")/media-manifest.sh"
REMOTE=${BACKUP_REMOTE:-$DEFAULT_BACKUP_REMOTE}
backup_env_assert_remote_configured "$REMOTE"
backup_env_assert_provider_independence "$REMOTE"

case $MAX_ATTEMPTS in ''|*[!0-9]*|0) echo "backup.sh: invalid BACKUP_MAX_ATTEMPTS '$MAX_ATTEMPTS'" >&2; exit 2 ;; esac
case $RETRY_DELAY in ''|*[!0-9]*) echo "backup.sh: invalid BACKUP_RETRY_DELAY '$RETRY_DELAY'" >&2; exit 2 ;; esac

# Serialize concurrent runs before touching /tmp/backup.*.
mkdir -p "$(dirname "$LOCK_FILE")"
exec 9>"$LOCK_FILE"
flock 9

# A run killed hard (SIGKILL, container restart) never ran its trap: remove what it left.
rm -rf /tmp/backup.*
WORK=$(mktemp -d /tmp/backup.XXXXXX)
trap 'rm -rf "$WORK"' EXIT
trap 'exit 130' INT TERM

pick_unique_stamp() {
  last=$1
  while :; do
    s=$(date -u +%Y%m%dT%H%M%SZ)
    if [ "$s" = "$last" ]; then
      sleep 1
      continue
    fi
    case $REMOTE in
      /*)
        if [ -e "${REMOTE%/}/$s" ]; then
          sleep 1
          continue
        fi
        ;;
      *)
        if [ -n "$(rclone lsf "$REMOTE$s" 2>/dev/null || true)" ]; then
          sleep 1
          continue
        fi
        ;;
    esac
    printf '%s\n' "$s"
    return 0
  done
}

# Export one PostgreSQL snapshot shared between db.dump and the media table inventory.
# Returns 10 if PostgreSQL cannot be reached / snapshot cannot start, 1 on dump failure.
snapshot_database() {
  adir=$1
  out_dir=$2
  psql_pid=

  mkfifo "$adir/pg_in"
  psql -X -A -t -q -v ON_ERROR_STOP=1 < "$adir/pg_in" > "$adir/pg_out" 2> "$adir/pg_err" &
  psql_pid=$!
  exec 3>"$adir/pg_in"

  printf 'BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;\n' >&3
  printf 'SELECT pg_export_snapshot() \\g %s/snap.id.tmp\n' "$adir" >&3
  printf '\\! mv %s/snap.id.tmp %s/snap.id\n' "$adir" "$adir" >&3

  waited=0
  while [ ! -f "$adir/snap.id" ]; do
    if ! kill -0 "$psql_pid" 2>/dev/null; then
      exec 3>&- || true
      wait "$psql_pid" 2>/dev/null || true
      cat "$adir/pg_err" >&2 || true
      return 10
    fi
    [ "$waited" -lt 300 ] || {
      exec 3>&- || true
      kill "$psql_pid" 2>/dev/null || true
      wait "$psql_pid" 2>/dev/null || true
      echo "backup.sh: timed out waiting for PostgreSQL snapshot" >&2
      return 10
    }
    sleep 0.1
    waited=$((waited + 1))
  done

  SNAP=$(tr -d '[:space:]' < "$adir/snap.id")
  [ -n "$SNAP" ] || {
    exec 3>&- || true
    wait "$psql_pid" 2>/dev/null || true
    return 10
  }

  printf "SELECT id, visibility, category, COALESCE(size_bytes, -1), encode(convert_to(storage_key, 'UTF8'), 'hex') FROM media ORDER BY id \\g %s/db_media.tsv.tmp\n" "$adir" >&3
  printf '\\! mv %s/db_media.tsv.tmp %s/db_media.tsv\n' "$adir" "$adir" >&3

  waited=0
  while [ ! -f "$adir/db_media.tsv" ]; do
    if ! kill -0 "$psql_pid" 2>/dev/null; then
      exec 3>&- || true
      wait "$psql_pid" 2>/dev/null || true
      cat "$adir/pg_err" >&2 || true
      return 1
    fi
    [ "$waited" -lt 300 ] || {
      exec 3>&- || true
      kill "$psql_pid" 2>/dev/null || true
      wait "$psql_pid" 2>/dev/null || true
      echo "backup.sh: timed out exporting media inventory" >&2
      return 1
    }
    sleep 0.1
    waited=$((waited + 1))
  done

  echo "[$STAMP] dumping database $PGDATABASE (snapshot $SNAP)"
  if ! pg_dump --format=custom --no-owner --no-privileges --snapshot="$SNAP" --file="$out_dir/db.dump"; then
    exec 3>&- || true
    kill "$psql_pid" 2>/dev/null || true
    wait "$psql_pid" 2>/dev/null || true
    return 1
  fi

  printf 'COMMIT;\n\\q\n' >&3
  exec 3>&-
  wait "$psql_pid" || { cat "$adir/pg_err" >&2 || true; return 1; }

  # A dump pg_restore cannot read is not a backup.
  pg_restore --list "$out_dir/db.dump" >/dev/null || return 1
}

collect_media_disk() {
  adir=$1
  out_dir=$2

  echo "[$STAMP] scanning served public files in $UPLOADS_DIR/public"
  manifest_scan_tree "$UPLOADS_DIR/public" "public/" "$adir/observed_public.tsv" || return 1

  echo "[$STAMP] archiving $UPLOADS_DIR and $PRIVATE_DIR"
  tar --exclude='.media-*.tmp' -czf "$out_dir/uploads.tar.gz" -C "$UPLOADS_DIR" . || return 1
  tar --exclude='.media-*.tmp' -czf "$out_dir/private.tar.gz" -C "$PRIVATE_DIR" . || return 1

  mkdir -p "$adir/verify/uploads" "$adir/verify/private" || return 1
  tar -xzf "$out_dir/uploads.tar.gz" -C "$adir/verify/uploads" || return 1
  tar -xzf "$out_dir/private.tar.gz" -C "$adir/verify/private" || return 1
  manifest_scan_media_roots "$adir/verify/uploads" "$adir/verify/private" "$adir/scanned.tsv" || return 1
  rm -rf "$adir/verify" || return 1

  manifest_build_and_verify \
    "$adir/scanned.tsv" \
    "$adir/db_media.tsv" \
    "$adir/observed_public.tsv" \
    "$STAMP" \
    "$SNAP" \
    "disk" \
    "$out_dir/media-manifest.tsv" || return 1
}

collect_s3_public_inventory() {
  out_tsv=$1
  tab=$(printf '\t')
  : > "$out_tsv"
  # If the bucket has no public/ prefix yet, rclone lsf exits 3 (directory not found);
  # we already verified bucket access with max-depth 1 on bucket root.
  raw_list=$(rclone lsf -R --files-only --format "sp" --separator "$tab" \
    --exclude ".media-*.tmp" --exclude "**/.media-*.tmp" \
    "media:$MEDIA_S3_BUCKET/public" 2>"${out_tsv}.err") || {
      rc=$?
      if [ "$rc" -eq 3 ]; then
        rm -f "${out_tsv}.err"
        return 0
      fi
      cat "${out_tsv}.err" >&2 || true
      rm -f "${out_tsv}.err"
      return "$rc"
    }
  rm -f "${out_tsv}.err"
  [ -n "$raw_list" ] || return 0
  printf '%s\n' "$raw_list" | while IFS="$tab" read -r sz rel; do
    [ -n "$rel" ] || continue
    hex=$(manifest_hex_encode "public/$rel")
    printf '%s\t%s\n' "$hex" "$sz"
  done > "$out_tsv" || return 1
}

collect_media_s3() {
  adir=$1
  out_dir=$2

  echo "[$STAMP] inspecting S3 bucket $MEDIA_S3_BUCKET"
  rclone lsf "media:$MEDIA_S3_BUCKET" --max-depth 1 >/dev/null || return 1

  collect_s3_public_inventory "$adir/observed_public.tsv" || return 1

  mkdir -p "$adir/stage/uploads/public" "$adir/stage/private" || return 1
  echo "[$STAMP] downloading S3 media from $MEDIA_S3_BUCKET"
  rclone copy --exclude ".media-*.tmp" --exclude "**/.media-*.tmp" \
    "media:$MEDIA_S3_BUCKET/public" "$adir/stage/uploads/public" || {
      rc=$?
      [ "$rc" -eq 3 ] || return "$rc"
    }
  rclone copy --exclude ".media-*.tmp" --exclude "**/.media-*.tmp" \
    "media:$MEDIA_S3_BUCKET/private" "$adir/stage/private" || {
      rc=$?
      [ "$rc" -eq 3 ] || return "$rc"
    }

  manifest_scan_media_roots "$adir/stage/uploads" "$adir/stage/private" "$adir/scanned.tsv" || return 1
  manifest_build_and_verify \
    "$adir/scanned.tsv" \
    "$adir/db_media.tsv" \
    "$adir/observed_public.tsv" \
    "$STAMP" \
    "$SNAP" \
    "s3" \
    "$out_dir/media-manifest.tsv" || return 1

  tar -czf "$out_dir/uploads.tar.gz" -C "$adir/stage/uploads" . || return 1
  tar -czf "$out_dir/private.tar.gz" -C "$adir/stage/private" . || return 1
  tar -tzf "$out_dir/uploads.tar.gz" >/dev/null || return 1
  tar -tzf "$out_dir/private.tar.gz" >/dev/null || return 1
  rm -rf "$adir/stage" || return 1
}

run_attempt() {
  adir=$1
  out_dir="$adir/out"
  mkdir -p "$out_dir" || return 1

  snapshot_database "$adir" "$out_dir" || return $?

  case $MEDIA_STORAGE_BACKEND in
    disk) collect_media_disk "$adir" "$out_dir" || return 1 ;;
    s3) collect_media_s3 "$adir" "$out_dir" || return 1 ;;
  esac

  (cd "$out_dir" && sha256sum db.dump uploads.tar.gz private.tar.gz media-manifest.tsv > SHA256SUMS) || return 1

  echo "[$STAMP] sending to $REMOTE$STAMP"
  rclone copy --exclude /SHA256SUMS "$out_dir" "$REMOTE$STAMP" || return 1
  # Last, once everything else is there: restore.sh only offers folders holding it,
  # so an upload cut off halfway is never mistaken for a backup.
  rclone copy "$out_dir/SHA256SUMS" "$REMOTE$STAMP" || return 1
}

attempt=1
last_stamp=
while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
  STAMP=$(pick_unique_stamp "$last_stamp")
  last_stamp=$STAMP
  ATTEMPT_DIR="$WORK/attempt-$attempt"
  mkdir -p "$ATTEMPT_DIR"

  rc=0
  run_attempt "$ATTEMPT_DIR" || rc=$?
  rm -rf "$ATTEMPT_DIR"

  if [ "$rc" -eq 0 ]; then
    echo "[$STAMP] backup complete"
    exit 0
  fi

  # PostgreSQL unreachable/auth error: fail fast instead of sleeping on retries.
  if [ "$rc" -eq 10 ]; then
    exit 1
  fi

  if [ "$attempt" -lt "$MAX_ATTEMPTS" ]; then
    echo "[$STAMP] backup inconsistent or failed on attempt $attempt/$MAX_ATTEMPTS; retrying in ${RETRY_DELAY}s" >&2
    sleep "$RETRY_DELAY"
  fi
  attempt=$((attempt + 1))
done

echo "[$STAMP] backup failed after $MAX_ATTEMPTS attempt(s)" >&2
exit 1
