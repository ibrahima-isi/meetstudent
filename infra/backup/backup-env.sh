# Shared by the backup and restore commands. Never print credentials or endpoints.
fail_config() { echo "${0##*/}: $*" >&2; exit 2; }

require_vars() {
  for var do
    eval '[ -n "${'"$var"':-}" ]' || fail_config "missing $var"
  done
}

require_vars PGHOST PGUSER PGPASSWORD PGDATABASE BACKUP_CRYPT_PASSWORD BACKUP_CRYPT_SALT

case "$REMOTE" in
  awscrypt:)
    require_vars RCLONE_CONFIG_AWS_REGION RCLONE_CONFIG_AWS_ACCESS_KEY_ID RCLONE_CONFIG_AWS_SECRET_ACCESS_KEY
    case "$RCLONE_CONFIG_AWS_REGION" in
      eu-central-1|eu-central-2|eu-west-1|eu-west-3|eu-north-1|eu-south-1|eu-south-2) ;;
      *) fail_config "BACKUP_AWS_REGION must be an AWS region in the EU" ;;
    esac
    case ${RCLONE_CONFIG_AWSCRYPT_REMOTE:-} in aws:?*) ;; *) fail_config "missing BACKUP_AWS_BUCKET" ;; esac
    backup_provider=AWS
    ;;
  r2crypt:|/*/)
    # The local path keeps the scripts testable without a remote.
    require_vars RCLONE_CONFIG_R2_ENDPOINT RCLONE_CONFIG_R2_ACCESS_KEY_ID RCLONE_CONFIG_R2_SECRET_ACCESS_KEY
    case ${RCLONE_CONFIG_R2CRYPT_REMOTE:-} in r2:?*) ;; *) fail_config "missing R2_BUCKET" ;; esac
    case "$RCLONE_CONFIG_R2_ENDPOINT" in
      https://*.eu.r2.cloudflarestorage.com|http://s3.eu.backup.test:9000) ;;
      *) fail_config "R2_ENDPOINT must be the EU jurisdiction endpoint" ;;
    esac
    backup_provider=Cloudflare
    ;;
  *) fail_config "BACKUP_REMOTE must select r2crypt: or awscrypt:" ;;
esac

RCLONE_CONFIG_R2CRYPT_PASSWORD=$(printf '%s' "$BACKUP_CRYPT_PASSWORD" | rclone obscure -)
RCLONE_CONFIG_R2CRYPT_PASSWORD2=$(printf '%s' "$BACKUP_CRYPT_SALT" | rclone obscure -)
RCLONE_CONFIG_AWSCRYPT_PASSWORD=$RCLONE_CONFIG_R2CRYPT_PASSWORD
RCLONE_CONFIG_AWSCRYPT_PASSWORD2=$RCLONE_CONFIG_R2CRYPT_PASSWORD2
export RCLONE_CONFIG_R2CRYPT_PASSWORD RCLONE_CONFIG_R2CRYPT_PASSWORD2
export RCLONE_CONFIG_AWSCRYPT_PASSWORD RCLONE_CONFIG_AWSCRYPT_PASSWORD2

MEDIA_BACKEND=${MEDIA_STORAGE_BACKEND:-disk}
configure_media() {
  case "$MEDIA_BACKEND" in
    disk) return ;;
    s3) ;;
    *) fail_config "MEDIA_STORAGE_BACKEND must be disk or s3" ;;
  esac
  require_vars MEDIA_S3_BUCKET MEDIA_S3_REGION MEDIA_S3_ACCESS_KEY_ID MEDIA_S3_SECRET_ACCESS_KEY
  case ${MEDIA_S3_ENDPOINT:-} in
    '') media_provider=AWS ;;
    https://*.r2.cloudflarestorage.com) media_provider=Cloudflare ;;
    https://*.amazonaws.com) media_provider=AWS ;;
    https://*.your-objectstorage.com|http://s3.media.backup.test:9000) media_provider=Other ;;
    *) fail_config "MEDIA_S3_ENDPOINT must identify the active provider (AWS, R2 or Hetzner)" ;;
  esac
  export RCLONE_CONFIG_MEDIA_TYPE=s3 RCLONE_CONFIG_MEDIA_PROVIDER="$media_provider"
  export RCLONE_CONFIG_MEDIA_ENDPOINT="${MEDIA_S3_ENDPOINT:-}" RCLONE_CONFIG_MEDIA_REGION="$MEDIA_S3_REGION"
  export RCLONE_CONFIG_MEDIA_ACCESS_KEY_ID="$MEDIA_S3_ACCESS_KEY_ID"
  export RCLONE_CONFIG_MEDIA_SECRET_ACCESS_KEY="$MEDIA_S3_SECRET_ACCESS_KEY"
  export RCLONE_CONFIG_MEDIA_SESSION_TOKEN="${MEDIA_S3_SESSION_TOKEN:-}"
  export RCLONE_CONFIG_MEDIA_FORCE_PATH_STYLE="${MEDIA_S3_PATH_STYLE:-false}" RCLONE_CONFIG_MEDIA_NO_CHECK_BUCKET=true
  MEDIA_REMOTE="media:$MEDIA_S3_BUCKET"
}

# The format is shared by the inventory query and rclone's raw path list.
verify_inventory() {
  inventory=$1
  media_root=$2
  jq -e 'type == "array" and all(.[]; . as $m |
      ($m.visibility == "PUBLIC" or $m.visibility == "PRIVATE") and
      ($m.key | startswith(($m.visibility | ascii_downcase) + "/")) and
      ($m.key | split("/") | all(. != "" and . != "." and . != "..")) and
      ($m.key | explode | all(. >= 32 and . != 92)) and
      ($m.size == null or ($m.size | type == "number" and . >= 0))
    )' "$inventory" >/dev/null || {
    echo "invalid media inventory; normalize legacy private keys before backup" >&2
    return 1
  }
  [ -z "$media_root" ] && return 0
  rclone lsjson --recursive --files-only --no-modtime --no-mimetype "$media_root" >"$WORK/media-files.json" &&
    jq -e --slurpfile actual "$WORK/media-files.json" '
      ($actual[0] | INDEX(.Path)) as $files |
      all(.[]; . as $m | $files[$m.key] != null and
        ($m.size == null or $files[$m.key].Size == $m.size))
    ' "$inventory" >/dev/null || {
    echo "media inventory is incomplete or has a size mismatch" >&2
    return 1
  }
}
