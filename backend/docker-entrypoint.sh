#!/bin/bash
set -e

# Read the secret
export POSTGRES_PASSWORD=$(cat /run/secrets/postgres_password)

# Execute the original entrypoint script
exec docker-entrypoint.sh "$@"