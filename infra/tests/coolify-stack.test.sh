#!/usr/bin/env bash
# Tests for the Coolify stack (compose.coolify.yml) and its proxy protections
# (infra/coolify/proxy/dynamic/meetstudent.yaml). Ticket coolify-deploy/02.
#
#   infra/tests/coolify-stack.test.sh static   # rendered compose only; no container. Run by CI.
#   infra/tests/coolify-stack.test.sh live     # static + the real stack behind a replica of
#                                              # Coolify's Traefik, observed as an HTTP client.
#
# `live` never builds: it needs the three images present locally under their GHCR
# names (ghcr.io/ibrahima-isi/meetstudent-{api,web,backoffice}:$IMAGE_TAG) and starts
# with --pull never. It always tears its stack, network and proxy down (down -v).
# Needs: docker (compose v2), jq, curl.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
STACK_FILE=${STACK_FILE:-$ROOT/compose.coolify.yml}

# Dummy values: the stack refuses to render without the required ones.
export DOMAIN=${DOMAIN:-site.meetstudent.test}
export BO_DOMAIN=${BO_DOMAIN:-admin.meetstudent.test}
export POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-test-postgres-password}
export JWT_SECRET_KEY=${JWT_SECRET_KEY:-dGVzdC1qd3Qtc2VjcmV0LWtleS10aGF0LWlzLWxvbmctZW5vdWdoLWZvci1oczI1Ng==}
export CORS_ALLOWED_ORIGINS=${CORS_ALLOWED_ORIGINS:-https://$DOMAIN,https://$BO_DOMAIN}
export ADMIN_EMAIL=${ADMIN_EMAIL:-admin@meetstudent.test}
export ADMIN_PASSWORD=${ADMIN_PASSWORD:-test-admin-password-123}

failures=0
pass() { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
check() { # check "description" command...
  local name=$1; shift
  if "$@" >/dev/null 2>&1; then pass "$name"; else fail "$name"; fi
}

rendered() { docker compose -f "$STACK_FILE" config --format json; }

# --- static: the rendered stack ------------------------------------------------

static_tests() {
  echo "static: $(basename "$STACK_FILE") rendered with dummy variables"
  local json
  if ! json=$(rendered 2>&1); then
    fail "the stack renders with the required variables set"
    printf '%s\n' "$json" | sed 's/^/        /'
    return
  fi
  pass "the stack renders with the required variables set"

  q() { jq -e "$1" <<<"$json"; }
  check "no service publishes a port (Coolify's Traefik is the only entry)" \
    q '[.services[] | select(.ports != null and (.ports | length) > 0)] | length == 0'
  check "the stack brings no proxy of its own" \
    q '[.services[] | select(.image // "" | test("traefik"))] | length == 0'
  for app in api web backoffice; do
    check "$app runs the CI image ghcr.io/ibrahima-isi/meetstudent-$app:latest, always pulled" \
      q ".services.$app.image == \"ghcr.io/ibrahima-isi/meetstudent-$app:latest\" and .services.$app.pull_policy == \"always\""
  done
  local sha=0123456789abcdef0123456789abcdef01234567
  check "IMAGE_TAG=sha-<commit> pins the three images (rollback)" \
    jq -e --arg t "sha-$sha" '[.services.api.image, .services.web.image, .services.backoffice.image]
      | all(endswith(":" + $t))' <<<"$(IMAGE_TAG=sha-$sha rendered)"
  check "the entry network is Coolify's external proxy network (coolify by default)" \
    q '[.networks[] | select(.external == true)] | map(.name) == ["coolify"]'
  check "PROXY_NETWORK renames the entry network" \
    jq -e '[.networks[] | select(.external == true)] | map(.name) == ["other-proxy"]' <<<"$(PROXY_NETWORK=other-proxy rendered)"
  check "Postgres sits on internal networks only" \
    q '. as $stack | .services.db.networks | keys | all(. as $n | $stack.networks[$n].internal == true)'
  check "no container label declares a middleware (they live in the proxy's dynamic file)" \
    q '[.services[].labels // {} | keys[] | select(startswith("traefik.http.middlewares."))] | length == 0'
  check "the stack refuses to render without DOMAIN" \
    bash -c '! DOMAIN= docker compose -f "$0" config -q' "$STACK_FILE"
}

# --- live: the stack behind a replica of Coolify's Traefik ---------------------

PROJECT=${PROJECT:-meetstudent-coolify-test}
TEST_PROXY_NETWORK=${TEST_PROXY_NETWORK:-meetstudent-test-coolify}
HTTP_PORT=${HTTP_PORT:-18080}
HTTPS_PORT=${HTTPS_PORT:-18443}
DYNAMIC_DIR="$ROOT/infra/coolify/proxy/dynamic"
PROXY_NAME="$PROJECT-proxy"
DECOY_NAME="$PROJECT-decoy"

stack() { docker compose -p "$PROJECT" -f "$STACK_FILE" "$@"; }

teardown() {
  echo "teardown"
  stack down -v --remove-orphans >/dev/null 2>&1 || true
  docker rm -f "$PROXY_NAME" "$DECOY_NAME" >/dev/null 2>&1 || true
  docker network rm "$PROXY_NETWORK" >/dev/null 2>&1 || true
}

# Entrypoints, providers and resolver named like the default proxy of Coolify v4
# (/data/coolify/proxy/docker-compose.yml on the server; re-check it on the real
# instance). The ACME CA is unreachable on purpose, so routers fall back to
# Traefik's self-signed default certificate (the "local certificate").
start_proxy() {
  docker network create "$PROXY_NETWORK" >/dev/null
  docker run -d --name "$PROXY_NAME" --network "$PROXY_NETWORK" \
    -p "127.0.0.1:$HTTP_PORT:80" -p "127.0.0.1:$HTTPS_PORT:443" \
    -v /var/run/docker.sock:/var/run/docker.sock:ro \
    -v "$DYNAMIC_DIR:/traefik/dynamic:ro" \
    traefik:v3.6 \
    --ping=true --ping.entrypoint=http \
    --entrypoints.http.address=:80 --entrypoints.https.address=:443 \
    --entrypoints.http.http.encodequerysemicolons=true \
    --entrypoints.https.http.encodequerysemicolons=true \
    --providers.file.directory=/traefik/dynamic/ --providers.file.watch=true \
    --providers.docker=true --providers.docker.exposedbydefault=false \
    --certificatesresolvers.letsencrypt.acme.httpchallenge=true \
    --certificatesresolvers.letsencrypt.acme.httpchallenge.entrypoint=http \
    --certificatesresolvers.letsencrypt.acme.storage=/tmp/acme.json \
    --certificatesresolvers.letsencrypt.acme.caserver=https://127.0.0.1:9/directory \
    --global.checknewversion=false --global.sendanonymoususage=false >/dev/null
  # Another resource on the shared proxy network answering to the names `api` and `db`.
  docker run -d --name "$DECOY_NAME" --network "$PROXY_NETWORK" \
    --network-alias api --network-alias db alpine:latest sleep infinity >/dev/null
}

# curl through the proxy, as a browser on the public hostnames would. Never fails:
# the assertions judge the answer.
https() { local host=$1; shift; curl -sk --max-time 20 --resolve "$host:$HTTPS_PORT:127.0.0.1" "$@" "https://$host:$HTTPS_PORT${URL_PATH:-/}" || true; }
http_() { local host=$1; shift; curl -s --max-time 20 --resolve "$host:$HTTP_PORT:127.0.0.1" "$@" "http://$host:$HTTP_PORT${URL_PATH:-/}" || true; }
status() { "$@" -o /dev/null -w '%{http_code}' || true; }
header() { local name=$1; shift; { "$@" -o /dev/null -D - || true; } | tr -d '\r' | { grep -i "^$name:" || true; } | head -1 | cut -d' ' -f2-; }

ip_on() { # ip_on <container> <network>
  docker inspect -f "{{(index .NetworkSettings.Networks \"$2\").IPAddress}}" "$1"
}
resolved_in() { # resolved_in <service> <hostname>: every IPv4 the service's container resolves
  stack exec -T "$1" nslookup "$2" 2>/dev/null | awk '/^Name:/{n=1} n && /^Address:/{print $2}' | grep -E '^[0-9.]+$' | sort -u
}

live_tests() {
  export PROXY_NETWORK=$TEST_PROXY_NETWORK
  for app in api web backoffice; do
    docker image inspect "ghcr.io/ibrahima-isi/meetstudent-$app:${IMAGE_TAG:-latest}" >/dev/null 2>&1 || {
      echo "missing local image ghcr.io/ibrahima-isi/meetstudent-$app:${IMAGE_TAG:-latest} (live never pulls nor builds)" >&2
      exit 2
    }
  done
  trap teardown EXIT
  teardown
  echo "live: starting the proxy replica and the stack ($PROJECT, network $PROXY_NETWORK)"
  start_proxy
  if ! stack up -d --pull never --wait --wait-timeout 300 >/dev/null 2>&1; then
    stack ps
    fail "the stack starts and becomes healthy"
    return
  fi
  pass "the stack starts and becomes healthy"

  echo "live: routing (single origin + back office domain)"
  check "/api on DOMAIN reaches the api" test "$(URL_PATH=/api/v1/schools status https "$DOMAIN")" = 200
  local ctype
  ctype=$(URL_PATH=/api/v1/schools header content-type https "$DOMAIN")
  check "/api answers JSON (not the SSR app)" grep -q application/json <<<"$ctype"
  local ssr_page
  ssr_page=$(URL_PATH=/fr https "$DOMAIN")
  check "/ on DOMAIN is the server-rendered web" grep -q 'ng-server-context="ssr' <<<"$ssr_page"
  local upload_probe
  upload_probe=$(URL_PATH=/uploads/public/missing.png https "$DOMAIN" -w '\n%{http_code}')
  check "/uploads/public on DOMAIN reaches the api (its 404, not the proxy's nor the SSR app's)" \
    bash -c '[ "$(tail -1 <<<"$0")" = 404 ] && ! grep -q -e "ng-server-context" -e "404 page not found" <<<"$0"' "$upload_probe"
  check "the back office is served on BO_DOMAIN, pointing at the single-origin API" \
    grep -q "https://$DOMAIN/api/v1" <<<"$(URL_PATH=/config.json https "$BO_DOMAIN")"

  echo "live: HTTP -> HTTPS"
  for host in "$DOMAIN" "$BO_DOMAIN"; do
    local code location
    code=$(URL_PATH=/fr status http_ "$host")
    location=$(URL_PATH=/fr header location http_ "$host")
    check "http://$host redirects permanently to https ($code -> $location)" \
      bash -c '[[ "$0" =~ ^30[18]$ && "$1" == https://"$2"* ]]' "$code" "$location" "$host"
  done

  echo "live: security headers"
  local target
  for target in "$DOMAIN /fr" "$DOMAIN /api/v1/schools" "$BO_DOMAIN /"; do
    set -- $target
    local hsts nosniff frame
    hsts=$(URL_PATH=$2 header strict-transport-security https "$1")
    nosniff=$(URL_PATH=$2 header x-content-type-options https "$1")
    frame=$(URL_PATH=$2 header x-frame-options https "$1")
    check "HSTS one year + subdomains on https://$1$2" grep -q 'max-age=31536000; includeSubDomains' <<<"$hsts"
    check "nosniff and frame deny on https://$1$2" bash -c '[ "$0" = nosniff ] && [ "$1" = DENY ]' "$nosniff" "$frame"
  done

  echo "live: SSR shows real data"
  local token school="Aaa Coolify Stack School $$"
  token=$({ URL_PATH=/api/v1/auth https "$DOMAIN" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" || true; } | jq -r '.accessToken // empty' 2>/dev/null || true)
  check "the admin signs in through the proxy" test -n "$token"
  URL_PATH=/api/v1/schools https "$DOMAIN" -o /dev/null -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' -d "{\"name\":\"$school\",\"code\":\"CST$(( $$ % 100 ))\"}" || true
  check "the server-rendered catalogue lists a school created through the API" \
    grep -q "$school" <<<"$(URL_PATH=/fr/schools https "$DOMAIN")"

  echo "live: limits"
  local codes
  codes=$(for _ in $(seq 1 20); do URL_PATH=/api/v1/auth status https "$DOMAIN" -H 'Content-Type: application/json' \
    -d '{"username":"nobody@meetstudent.test","password":"wrong-password"}'; echo; done)
  check "a burst of 20 sign-in attempts ends in 429 ($(sort <<<"$codes" | uniq -c | tr -s ' \n' ' '))" grep -q '^429$' <<<"$codes"
  local big="${TMPDIR:-/tmp}/meetstudent-11mb.bin"
  head -c $((11 * 1024 * 1024)) /dev/zero >"$big"
  local upload
  upload=$(URL_PATH=/api/v1/media status https "$DOMAIN" -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/octet-stream' --data-binary "@$big")
  rm -f "$big"
  check "an 11 MB upload is refused before the api (413, got $upload)" test "$upload" = 413

  echo "live: isolation"
  local db_ip
  db_ip=$(ip_on "$(stack ps -q db)" "${PROJECT}_backend")
  check "control: Postgres answers on its internal network" \
    docker run --rm --network "${PROJECT}_backend" alpine:latest nc -z -w 3 "$db_ip" 5432
  check "Postgres is unreachable from the proxy network" \
    bash -c '! docker exec "$0" nc -z -w 3 "$1" 5432' "$DECOY_NAME" "$db_ip"
  check "no container of the stack publishes a port on the host" \
    bash -c '! docker ps --filter "label=com.docker.compose.project=$0" --format "{{.Ports}}" | grep -q -- "->"' "$PROJECT"

  local api_host db_host
  api_host=$(stack exec -T web printenv API_URL | sed -E 's#^https?://([^:/]+).*#\1#')
  db_host=$(stack exec -T api printenv SPRING_DATASOURCE_URL | sed -E 's#^jdbc:postgresql://([^:/]+).*#\1#')
  check "SSR's API host ($api_host) resolves to this stack's api only, never to a proxy-network neighbour" \
    test "$(resolved_in web "$api_host")" = "$(ip_on "$(stack ps -q api)" "${PROJECT}_ssr")"
  check "the api's database host ($db_host) resolves to this stack's Postgres only" \
    test "$(resolved_in api "$db_host")" = "$db_ip"
}

case ${1:-static} in
  static) static_tests ;;
  live) static_tests; live_tests ;;
  *) echo "usage: $0 [static|live]" >&2; exit 2 ;;
esac

if [ "$failures" -gt 0 ]; then echo "$failures failure(s)"; exit 1; fi
echo "all passed"
