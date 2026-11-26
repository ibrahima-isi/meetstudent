# Sourced by backup.sh and restore.sh (ADR 0004, ADR 0005): checks the
# configuration both read, enforces EU jurisdiction and provider independence
# for new backups, configures rclone remotes for backup and optional S3 media,
# and hands rclone the crypt password and salt.
# Exits 2, before anything is read or sent, when the configuration is invalid.

backup_env_lower() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

backup_env_check_http_url() {
  url=$1 name=$2
  scheme_lower=$(backup_env_lower "$url")
  case $scheme_lower in
    http://*|https://*) ;;
    *)
      echo "${0##*/}: $name must be an http:// or https:// URL" >&2
      exit 2
      ;;
  esac
  case $url in
    *@*|*\?*|*#*|*' '*|*'	'*)
      echo "${0##*/}: $name must not contain credentials, query, fragment, or whitespace" >&2
      exit 2
      ;;
  esac
  rest=${url#*://}
  authority=${rest%%/*}
  case $authority in
    ""|:*)
      echo "${0##*/}: $name must include a valid hostname" >&2
      exit 2
      ;;
    \[*\]) ;;
    \[*\]:*)
      port=${authority#\[*\]:}
      case $port in ''|*[!0-9]*) echo "${0##*/}: $name must include a valid port" >&2; exit 2 ;; esac
      ;;
    *:*)
      host_only=${authority%%:*}
      port=${authority#*:}
      [ -n "$host_only" ] || { echo "${0##*/}: $name must include a valid hostname" >&2; exit 2; }
      case $port in ''|*[!0-9]*) echo "${0##*/}: $name must include a valid hostname/port" >&2; exit 2 ;; esac
      ;;
  esac
}

backup_env_url_host() {
  rest=${1#*://}
  authority=${rest%%/*}
  case $authority in
    \[*\]:*) authority=${authority%:*} ;;
    \[*\]) ;;
    *:*) authority=${authority%%:*} ;;
  esac
  backup_env_lower "$authority"
}

backup_env_not_hetzner() {
  ep=$(backup_env_lower "${1:-}")
  label=$2
  case $ep in
    *your-objectstorage.com*|*hetzner.*)
      echo "${0##*/}: $label ($1) uses Hetzner (same provider as the VPS); backup storage must use an independent provider" >&2
      exit 2
      ;;
  esac
}

backup_env_check_eu() {
  ep=$(backup_env_lower "${1:-}")
  reg=$(backup_env_lower "${2:-}")
  label=$3

  backup_env_not_hetzner "${1:-}" "$label"

  host=
  if [ -n "$ep" ]; then
    backup_env_check_http_url "$1" "$label"
    host=$(backup_env_url_host "$1")
    backup_env_not_hetzner "$host" "$label"
  fi

  case $reg in
    us-*|ap-*|ca-*|sa-*|me-*|af-*|il-*|cn-*|uk-*|ch-*|eu-west-2|eu-central-2)
      echo "${0##*/}: $label region '$2' is outside the EU" >&2
      exit 2
      ;;
  esac

  case $host in
    *eu-west-2*|*eu-central-2*)
      echo "${0##*/}: $label ($1) targets a non-EU region" >&2
      exit 2
      ;;
  esac

  case $host in
    *r2.cloudflarestorage.com*)
      case $host in
        *.eu.*) return 0 ;;
        *)
          echo "${0##*/}: $label $1 is not an EU endpoint (https://<ACCOUNT_ID>.eu.r2.cloudflarestorage.com)" >&2
          exit 2
          ;;
      esac
      ;;
  esac

  if [ -z "$host" ] || case $host in *amazonaws.com*) true ;; *) false ;; esac; then
    case $reg in
      eu-west-1|eu-west-3|eu-central-1|eu-north-1|eu-south-1|eu-south-2) return 0 ;;
      *)
        echo "${0##*/}: $label region '$2' is not an EU AWS region (expected eu-west-1, eu-west-3, eu-central-1, eu-north-1, eu-south-1, or eu-south-2)" >&2
        exit 2
        ;;
    esac
  fi

  case $host in
    *.eu.*|*.eu-*|*-eu-*) return 0 ;;
  esac

  case $reg in
    eu-west-1|eu-west-3|eu-central-1|eu-north-1|eu-south-1|eu-south-2|fr-par|nl-ams|pl-waw|gra|rbx|sbg|eu-west-par)
      case $host in
        *"$reg"*|*.scw.cloud*|*.cloud.ovh.net*) return 0 ;;
      esac
      ;;
  esac

  echo "${0##*/}: $label ($1, region '$2') is not an EU endpoint" >&2
  exit 2
}

backup_env_s3_identity() {
  ep=$(backup_env_lower "${1:-}")
  prov=$(backup_env_lower "${2:-}")
  # Trim trailing slashes for normalized endpoint comparison.
  while :; do
    case $ep in
      */) ep=${ep%/} ;;
      *) break ;;
    esac
  done
  case $ep in
    *r2.cloudflarestorage.com*) echo "cloudflare-r2" ;;
    *your-objectstorage.com*|*hetzner.*) echo "hetzner" ;;
    *amazonaws.com*) echo "aws" ;;
    *scw.cloud*) echo "scaleway" ;;
    *ovh.net*) echo "ovh" ;;
    "")
      case $prov in
        aws|"") echo "aws" ;;
        cloudflare*) echo "cloudflare-r2" ;;
        hetzner*) echo "hetzner" ;;
        *) echo "provider:$prov" ;;
      esac
      ;;
    *) echo "custom:$ep" ;;
  esac
}

missing=
for var in PGHOST PGUSER PGPASSWORD PGDATABASE BACKUP_CRYPT_PASSWORD BACKUP_CRYPT_SALT; do
  eval "[ -n \"\${$var:-}\" ]" || missing="$missing $var"
done

MEDIA_STORAGE_BACKEND=${MEDIA_STORAGE_BACKEND:-disk}
case $MEDIA_STORAGE_BACKEND in
  disk) ;;
  s3)
    for var in MEDIA_S3_BUCKET MEDIA_S3_REGION MEDIA_S3_ACCESS_KEY_ID MEDIA_S3_SECRET_ACCESS_KEY; do
      eval "[ -n \"\${$var:-}\" ]" || missing="$missing $var"
    done
    if [ -n "${MEDIA_S3_ENDPOINT:-}" ]; then
      backup_env_check_http_url "$MEDIA_S3_ENDPOINT" "MEDIA_S3_ENDPOINT"
    fi
    export RCLONE_CONFIG_MEDIA_TYPE=s3
    if [ -n "${MEDIA_S3_ENDPOINT:-}" ]; then
      export RCLONE_CONFIG_MEDIA_PROVIDER=Other
      export RCLONE_CONFIG_MEDIA_ENDPOINT="$MEDIA_S3_ENDPOINT"
    else
      export RCLONE_CONFIG_MEDIA_PROVIDER=AWS
    fi
    export RCLONE_CONFIG_MEDIA_REGION="${MEDIA_S3_REGION:-}"
    export RCLONE_CONFIG_MEDIA_FORCE_PATH_STYLE="${MEDIA_S3_PATH_STYLE:-false}"
    export RCLONE_CONFIG_MEDIA_ACCESS_KEY_ID="${MEDIA_S3_ACCESS_KEY_ID:-}"
    export RCLONE_CONFIG_MEDIA_SECRET_ACCESS_KEY="${MEDIA_S3_SECRET_ACCESS_KEY:-}"
    export RCLONE_CONFIG_MEDIA_NO_CHECK_BUCKET=true
    if [ -n "${MEDIA_S3_SESSION_TOKEN:-}" ]; then
      export RCLONE_CONFIG_MEDIA_SESSION_TOKEN="$MEDIA_S3_SESSION_TOKEN"
    fi
    ;;
  *)
    echo "${0##*/}: invalid MEDIA_STORAGE_BACKEND '$MEDIA_STORAGE_BACKEND' (expected disk or s3)" >&2
    exit 2
    ;;
esac

BACKUP_HAS_S3=0
if [ -n "${BACKUP_S3_BUCKET:-}" ] || [ -n "${BACKUP_S3_ENDPOINT:-}" ] || [ -n "${BACKUP_S3_REGION:-}" ] \
  || [ -n "${BACKUP_S3_ACCESS_KEY_ID:-}" ] || [ -n "${BACKUP_S3_SECRET_ACCESS_KEY:-}" ]; then
  BACKUP_HAS_S3=1
  for var in BACKUP_S3_BUCKET BACKUP_S3_REGION BACKUP_S3_ACCESS_KEY_ID BACKUP_S3_SECRET_ACCESS_KEY; do
    eval "[ -n \"\${$var:-}\" ]" || missing="$missing $var"
  done
  if [ -z "${BACKUP_S3_ENDPOINT:-}" ] && [ "$(backup_env_lower "${BACKUP_S3_PROVIDER:-aws}")" != "aws" ]; then
    missing="$missing BACKUP_S3_ENDPOINT"
  fi
fi

BACKUP_HAS_R2=0
if [ -n "${RCLONE_CONFIG_R2_ENDPOINT:-}" ] || [ -n "${RCLONE_CONFIG_R2_ACCESS_KEY_ID:-}" ] \
  || [ -n "${RCLONE_CONFIG_R2_SECRET_ACCESS_KEY:-}" ] \
  || { [ -n "${RCLONE_CONFIG_R2CRYPT_REMOTE:-}" ] && [ "${RCLONE_CONFIG_R2CRYPT_REMOTE:-}" != "r2:" ]; }; then
  BACKUP_HAS_R2=1
  for var in RCLONE_CONFIG_R2_ENDPOINT RCLONE_CONFIG_R2_ACCESS_KEY_ID RCLONE_CONFIG_R2_SECRET_ACCESS_KEY RCLONE_CONFIG_R2CRYPT_REMOTE; do
    eval "[ -n \"\${$var:-}\" ]" || missing="$missing $var"
  done
  case ${RCLONE_CONFIG_R2CRYPT_REMOTE:-} in *:) missing="$missing R2_BUCKET" ;; esac
fi

BACKUP_HAS_AWS=0
if [ -n "${BACKUP_AWS_BUCKET:-}${BACKUP_AWS_REGION:-}${BACKUP_AWS_ACCESS_KEY_ID:-}${BACKUP_AWS_SECRET_ACCESS_KEY:-}" ]; then
  BACKUP_HAS_AWS=1
  for var in BACKUP_AWS_BUCKET BACKUP_AWS_REGION BACKUP_AWS_ACCESS_KEY_ID BACKUP_AWS_SECRET_ACCESS_KEY; do
    eval '[ -n "${'"$var"':-}" ]' || missing="$missing $var"
  done
fi

if [ "$BACKUP_HAS_S3" -eq 0 ] && [ "$BACKUP_HAS_R2" -eq 0 ] && [ "$BACKUP_HAS_AWS" -eq 0 ]; then
  missing="$missing RCLONE_CONFIG_R2_ENDPOINT RCLONE_CONFIG_R2_ACCESS_KEY_ID RCLONE_CONFIG_R2_SECRET_ACCESS_KEY RCLONE_CONFIG_R2CRYPT_REMOTE"
fi

[ -z "$missing" ] || { echo "${0##*/}: not configured, missing:$missing" >&2; exit 2; }

if [ "$BACKUP_HAS_R2" -eq 1 ]; then
  backup_env_check_http_url "$RCLONE_CONFIG_R2_ENDPOINT" "R2_ENDPOINT"
  r2_host=$(backup_env_url_host "$RCLONE_CONFIG_R2_ENDPOINT")
  # User story 25: students' documents stay in the EU. A bucket of R2's EU
  # jurisdiction is only reachable at https://<ACCOUNT_ID>.eu.r2.cloudflarestorage.com.
  case $r2_host in
    *eu-west-2*|*eu-central-2*)
      echo "${0##*/}: R2_ENDPOINT $RCLONE_CONFIG_R2_ENDPOINT targets a non-EU region" >&2
      exit 2
      ;;
    *.eu.*) ;;
    *)
      echo "${0##*/}: R2_ENDPOINT $RCLONE_CONFIG_R2_ENDPOINT is not an EU endpoint" \
        "(https://<ACCOUNT_ID>.eu.r2.cloudflarestorage.com)" >&2
      exit 2
      ;;
  esac
  backup_env_not_hetzner "$r2_host" "R2_ENDPOINT"
fi

if [ "$BACKUP_HAS_S3" -eq 1 ]; then
  backup_env_check_eu "${BACKUP_S3_ENDPOINT:-}" "$BACKUP_S3_REGION" "BACKUP_S3_ENDPOINT"
  export RCLONE_CONFIG_BACKUP_S3_TYPE=s3
  if [ -n "${BACKUP_S3_PROVIDER:-}" ]; then
    export RCLONE_CONFIG_BACKUP_S3_PROVIDER="$BACKUP_S3_PROVIDER"
  elif [ -z "${BACKUP_S3_ENDPOINT:-}" ]; then
    export RCLONE_CONFIG_BACKUP_S3_PROVIDER=AWS
  else
    export RCLONE_CONFIG_BACKUP_S3_PROVIDER=Other
  fi
  export RCLONE_CONFIG_BACKUP_S3_REGION="$BACKUP_S3_REGION"
  if [ -n "${BACKUP_S3_ENDPOINT:-}" ]; then
    export RCLONE_CONFIG_BACKUP_S3_ENDPOINT="$BACKUP_S3_ENDPOINT"
  fi
  export RCLONE_CONFIG_BACKUP_S3_FORCE_PATH_STYLE="${BACKUP_S3_PATH_STYLE:-false}"
  export RCLONE_CONFIG_BACKUP_S3_ACCESS_KEY_ID="$BACKUP_S3_ACCESS_KEY_ID"
  export RCLONE_CONFIG_BACKUP_S3_SECRET_ACCESS_KEY="$BACKUP_S3_SECRET_ACCESS_KEY"
  export RCLONE_CONFIG_BACKUP_S3_NO_CHECK_BUCKET=true
  if [ -n "${BACKUP_S3_SESSION_TOKEN:-}" ]; then
    export RCLONE_CONFIG_BACKUP_S3_SESSION_TOKEN="$BACKUP_S3_SESSION_TOKEN"
  fi
  export RCLONE_CONFIG_BACKUPCRYPT_TYPE=crypt
  export RCLONE_CONFIG_BACKUPCRYPT_REMOTE="backup_s3:${BACKUP_S3_BUCKET}"
fi

if [ "$BACKUP_HAS_AWS" -eq 1 ]; then
  backup_env_check_eu "" "$BACKUP_AWS_REGION" "BACKUP_AWS_REGION"
  export RCLONE_CONFIG_AWS_TYPE=s3 RCLONE_CONFIG_AWS_PROVIDER=AWS
  export RCLONE_CONFIG_AWS_REGION="$BACKUP_AWS_REGION"
  export RCLONE_CONFIG_AWS_ACCESS_KEY_ID="$BACKUP_AWS_ACCESS_KEY_ID"
  export RCLONE_CONFIG_AWS_SECRET_ACCESS_KEY="$BACKUP_AWS_SECRET_ACCESS_KEY"
  export RCLONE_CONFIG_AWS_NO_CHECK_BUCKET=true
  export RCLONE_CONFIG_AWSCRYPT_TYPE=crypt RCLONE_CONFIG_AWSCRYPT_REMOTE="aws:$BACKUP_AWS_BUCKET"
fi

# The originals stay in Coolify and the password manager; rclone wants them obscured.
BACKUP_CRYPT_PASSWORD_OBSCURED=$(printf '%s' "$BACKUP_CRYPT_PASSWORD" | rclone obscure -)
BACKUP_CRYPT_SALT_OBSCURED=$(printf '%s' "$BACKUP_CRYPT_SALT" | rclone obscure -)
if [ "$BACKUP_HAS_R2" -eq 1 ]; then
  export RCLONE_CONFIG_R2CRYPT_PASSWORD="$BACKUP_CRYPT_PASSWORD_OBSCURED"
  export RCLONE_CONFIG_R2CRYPT_PASSWORD2="$BACKUP_CRYPT_SALT_OBSCURED"
fi
if [ "$BACKUP_HAS_S3" -eq 1 ]; then
  export RCLONE_CONFIG_BACKUPCRYPT_PASSWORD="$BACKUP_CRYPT_PASSWORD_OBSCURED"
  export RCLONE_CONFIG_BACKUPCRYPT_PASSWORD2="$BACKUP_CRYPT_SALT_OBSCURED"
fi

if [ "$BACKUP_HAS_AWS" -eq 1 ]; then
  export RCLONE_CONFIG_AWSCRYPT_PASSWORD="$BACKUP_CRYPT_PASSWORD_OBSCURED"
  export RCLONE_CONFIG_AWSCRYPT_PASSWORD2="$BACKUP_CRYPT_SALT_OBSCURED"
fi

if [ -n "${BACKUP_REMOTE:-}" ]; then
  DEFAULT_BACKUP_REMOTE=$BACKUP_REMOTE
elif [ "$BACKUP_HAS_S3" -eq 1 ]; then
  DEFAULT_BACKUP_REMOTE=backupcrypt:
elif [ "$BACKUP_HAS_AWS" -eq 1 ]; then
  DEFAULT_BACKUP_REMOTE=awscrypt:
else
  DEFAULT_BACKUP_REMOTE=r2crypt:
fi

backup_env_assert_remote_configured() {
  target=$1
  case $target in
    backupcrypt:*)
      [ "$BACKUP_HAS_S3" -eq 1 ] || { echo "${0##*/}: remote '$target' requested but BACKUP_S3_* is not configured" >&2; exit 2; }
      ;;
    r2crypt:*)
      [ "$BACKUP_HAS_R2" -eq 1 ] || { echo "${0##*/}: remote '$target' requested but R2_* is not configured" >&2; exit 2; }
      ;;
    awscrypt:*)
      [ "$BACKUP_HAS_AWS" -eq 1 ] || { echo "${0##*/}: remote '$target' requested but BACKUP_AWS_* is not configured" >&2; exit 2; }
      ;;
    /*/) ;; # Isolated local fixture.
    *) echo "${0##*/}: select an encrypted backup remote" >&2; exit 2 ;;
  esac
}

backup_env_assert_provider_independence() {
  target=$1
  case $target in
    backupcrypt:*)
      b_ep=${BACKUP_S3_ENDPOINT:-}
      b_prov=${BACKUP_S3_PROVIDER:-$([ -z "$b_ep" ] && echo AWS || echo Other)}
      ;;
    awscrypt:*)
      b_ep=
      b_prov=AWS
      ;;
    r2crypt:*)
      b_ep=${RCLONE_CONFIG_R2_ENDPOINT:-}
      b_prov=${RCLONE_CONFIG_R2_PROVIDER:-Cloudflare}
      ;;
    *)
      if [ "$BACKUP_HAS_S3" -eq 1 ]; then
        b_ep=${BACKUP_S3_ENDPOINT:-}
        b_prov=${BACKUP_S3_PROVIDER:-$([ -z "$b_ep" ] && echo AWS || echo Other)}
      else
        b_ep=${RCLONE_CONFIG_R2_ENDPOINT:-}
        b_prov=${RCLONE_CONFIG_R2_PROVIDER:-Cloudflare}
      fi
      ;;
  esac

  b_id=$(backup_env_s3_identity "$b_ep" "$b_prov")
  if [ "$b_id" = "hetzner" ]; then
    echo "${0##*/}: backup provider ($b_id) must be independent of the VPS" >&2
    exit 2
  fi
  if [ "$MEDIA_STORAGE_BACKEND" = "s3" ]; then
    m_ep=${MEDIA_S3_ENDPOINT:-}
    m_prov=$([ -n "$m_ep" ] && echo Other || echo AWS)
    m_id=$(backup_env_s3_identity "$m_ep" "$m_prov")
    if [ "$b_id" = "$m_id" ]; then
      echo "${0##*/}: backup provider ($b_id) must be independent of active media storage ($m_id)" >&2
      exit 2
    fi
  fi
}
