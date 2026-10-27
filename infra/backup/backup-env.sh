# Sourced by backup.sh and restore.sh (ADR 0004, coolify-deploy/03): checks the
# configuration both read, then hands rclone the crypt password and salt.
# Exits 2, before anything is read or sent, when the configuration is missing
# or the R2 endpoint is outside the EU.

missing=
for var in PGHOST PGUSER PGPASSWORD PGDATABASE RCLONE_CONFIG_R2_ENDPOINT RCLONE_CONFIG_R2_ACCESS_KEY_ID \
  RCLONE_CONFIG_R2_SECRET_ACCESS_KEY RCLONE_CONFIG_R2CRYPT_REMOTE BACKUP_CRYPT_PASSWORD BACKUP_CRYPT_SALT; do
  eval "[ -n \"\${$var:-}\" ]" || missing="$missing $var"
done
case ${RCLONE_CONFIG_R2CRYPT_REMOTE:-} in *:) missing="$missing R2_BUCKET" ;; esac
[ -z "$missing" ] || { echo "${0##*/}: not configured, missing:$missing" >&2; exit 2; }

# User story 25: students' documents stay in the EU. A bucket of R2's EU
# jurisdiction is only reachable at https://<ACCOUNT_ID>.eu.r2.cloudflarestorage.com.
case $RCLONE_CONFIG_R2_ENDPOINT in
  *.eu.*) ;;
  *)
    echo "${0##*/}: R2_ENDPOINT $RCLONE_CONFIG_R2_ENDPOINT is not an EU endpoint" \
      "(https://<ACCOUNT_ID>.eu.r2.cloudflarestorage.com)" >&2
    exit 2
    ;;
esac

# The originals stay in Coolify and the password manager; rclone wants them obscured.
RCLONE_CONFIG_R2CRYPT_PASSWORD=$(printf '%s' "$BACKUP_CRYPT_PASSWORD" | rclone obscure -)
RCLONE_CONFIG_R2CRYPT_PASSWORD2=$(printf '%s' "$BACKUP_CRYPT_SALT" | rclone obscure -)
export RCLONE_CONFIG_R2CRYPT_PASSWORD RCLONE_CONFIG_R2CRYPT_PASSWORD2
