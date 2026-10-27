# The `backup` service of the Coolify stack (ADR 0004, coolify-deploy/03):
# a Postgres client of the same major version as the database, and rclone.
# It idles; a Coolify Scheduled Task runs /usr/local/bin/backup.sh in it every
# night, and restore.sh brings a backup back. Configured by environment only
# (see the service in compose.coolify.yml); the image holds no secret.
FROM postgres:18-alpine

RUN apk add --no-cache rclone

COPY --chmod=0755 backup.sh restore.sh /usr/local/bin/
# Sourced by both scripts from their own folder, never run on its own.
COPY --chmod=0644 backup-env.sh /usr/local/bin/

# The postgres image stops with SIGINT; exit at once on it or on SIGTERM.
CMD ["sh", "-c", "trap 'exit 0' INT TERM; sleep infinity & wait"]
