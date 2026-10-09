#!/bin/sh
# Runs from /docker-entrypoint.d at container start: renders /config.json
# from BO_API_URL so one image works for any domain.
set -eu

: "${BO_API_URL:?BO_API_URL must be set (e.g. https://example.com/api/v1)}"

# Escape backslashes and double quotes so the value is always valid JSON.
escaped=$(printf '%s' "$BO_API_URL" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')
printf '{"apiUrl":"%s"}\n' "$escaped" > /usr/share/nginx/html/config.json
echo "40-write-config.sh: wrote config.json (apiUrl=$BO_API_URL)"
