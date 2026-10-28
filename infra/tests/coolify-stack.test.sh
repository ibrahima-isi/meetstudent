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
export ACME_EMAIL=${ACME_EMAIL:-ops@meetstudent.test}  # compose.prod.yml, for the CSP checks

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
  check "IMAGE_TAG=sha-<commit> pins the four images, backup included (rollback)" \
    jq -e --arg t "sha-$sha" '[.services.api.image, .services.web.image, .services.backoffice.image, .services.backup.image]
      | all(. != null and endswith(":" + $t))' <<<"$(IMAGE_TAG=sha-$sha rendered)"
  check "the entry network is Coolify's external proxy network (coolify by default), plus the api's own (meetstudent-api-edge)" \
    q '[.networks[] | select(.external == true)] | map(.name) | sort == ["coolify", "meetstudent-api-edge"]'
  check "PROXY_NETWORK renames the entry network" \
    jq -e '[.networks[] | select(.external == true)] | map(.name) | sort == ["meetstudent-api-edge", "other-proxy"]' <<<"$(PROXY_NETWORK=other-proxy rendered)"
  check "API_EDGE_NETWORK renames the api's network, label included" \
    jq -e '.networks["api-edge"].name == "other-api-edge"
      and .services.api.labels["traefik.docker.network"] == "other-api-edge"' <<<"$(API_EDGE_NETWORK=other-api-edge rendered)"
  # The api must be reachable by Traefik only: never on the network shared with every Coolify resource.
  check "the api is not on the shared proxy network" \
    q '. as $s | [.services.api.networks | keys[] | $s.networks[.].name] | index("coolify") == null'
  check "the api is not on the shared proxy network, whatever PROXY_NETWORK is" \
    jq -e '. as $s | [.services.api.networks | keys[] | $s.networks[.].name] | index("other-proxy") == null' <<<"$(PROXY_NETWORK=other-proxy rendered)"
  check "the api reaches Traefik on its dedicated external network, and says so in its traefik.docker.network label" \
    q '. as $s | .services.api.networks | keys | map($s.networks[.]) | any(.external == true and .name == "meetstudent-api-edge")
       and $s.services.api.labels["traefik.docker.network"] == "meetstudent-api-edge"'
  check "only the api is on the dedicated api network" \
    q '[.services | to_entries[] | select(.value.networks | has("api-edge")) | .key] == ["api"]'
  check "web and backoffice are still routed on the shared proxy network" \
    q '[.services.web, .services.backoffice | .networks | has("edge")] == [true, true]
       and .services.web.labels["traefik.docker.network"] == "coolify"
       and .services.backoffice.labels["traefik.docker.network"] == "coolify"'
  # Spring honours X-Forwarded-* only from the peers matching this regex: the dedicated network's subnet.
  local proxies
  proxies=$(q '.services.api.environment.TRUSTED_PROXIES' <<<"$json" | jq -r .) || proxies=
  check "the api trusts forwarded headers from a narrow proxy regex (TRUSTED_PROXIES), not the private ranges" \
    test -n "$proxies"
  check "TRUSTED_PROXIES matches Traefik's network (10.250.77.x)" \
    bash -c '[[ "$1" =~ ^($0)$ ]]' "$proxies" "10.250.77.2"
  local neighbour
  for neighbour in 10.250.78.2 10.0.0.5 172.18.0.3 192.168.1.5 127.0.0.1 203.0.113.9; do
    check "TRUSTED_PROXIES refuses $neighbour" \
      bash -c '! [[ "$1" =~ ^($0)$ ]]' "$proxies" "$neighbour"
  done
  check "Postgres sits on internal networks only" \
    q '. as $stack | .services.db.networks | keys | all(. as $n | $stack.networks[$n].internal == true)'
  check "no container label declares a middleware (they live in the proxy's dynamic file)" \
    q '[.services[].labels // {} | keys[] | select(startswith("traefik.http.middlewares."))] | length == 0'
  check "the stack refuses to render without DOMAIN" \
    bash -c '! DOMAIN= docker compose -f "$0" config -q' "$STACK_FILE"
}

# --- static: Content-Security-Policy -------------------------------------------
#
# Back office: enforcing, rendered at container start by apps/backoffice/docker/40-write-config.sh
# from BO_API_URL and the inline scripts of its index.html. Web SSR: Report-Only, written in the
# proxy files (hashes of the inline scripts of apps/web/src/index.html). Both proxy variants, the
# Coolify dynamic file and the labels of compose.prod.yml, must carry the same values.

DYNAMIC_FILE="$ROOT/infra/coolify/proxy/dynamic/meetstudent.yaml"
PROD_FILE="$ROOT/compose.prod.yml"
BO_SCRIPT="$ROOT/apps/backoffice/docker/40-write-config.sh"

# 'sha256-<base64>' of each inline <script> (no src) of an HTML file, one per line. Independent of
# the shell implementation under test: Python's hashlib.
inline_script_hashes() {
  python3 -I - "$1" <<'PY'
import base64, hashlib, re, sys
html = open(sys.argv[1], encoding="utf-8").read()
for m in re.finditer(r'<script(?![^>]*\ssrc=)[^>]*>(.*?)</script>', html, re.S):
    print("'sha256-" + base64.b64encode(hashlib.sha256(m.group(1).encode()).digest()).decode() + "'")
PY
}

# Runs the back office entrypoint script on a copy of index.html $1 with BO_API_URL=$2; prints the
# CSP line it wrote (empty and non-zero when the script refuses the URL).
bo_policy() {
  local work
  work=$(mktemp -d)
  cp "$1" "$work/index.html"
  BO_HTML_DIR=$work BO_CSP_CONF=$work/csp.conf BO_API_URL=$2 sh "$BO_SCRIPT" >/dev/null 2>&1 || { rm -rf "$work"; return 1; }
  cat "$work/csp.conf"
  rm -rf "$work"
}

bo_refuses() { ! bo_policy "$1" "$2" >/dev/null; }

csp_tests() {
  echo "static: Content-Security-Policy"
  local bo_index="$ROOT/apps/backoffice/src/index.html" web_index="$ROOT/apps/web/src/index.html"
  local dyn prod
  dyn() { yq -r "$1" "$DYNAMIC_FILE"; }
  prod() { docker compose -f "$PROD_FILE" config --format json 2>/dev/null | jq -r "$1"; }

  # -- back office: enforcing, built at start
  local theme_hash policy
  theme_hash=$(inline_script_hashes "$bo_index" | head -1)
  policy=$(bo_policy "$bo_index" https://api.meetstudent.test/api/v1 || true)
  check "back office: the inline theme script of index.html has exactly one hash (a second inline script must be reviewed)" \
    test "$(inline_script_hashes "$bo_index" | wc -l | tr -d ' ')" = 1
  check "back office: the policy is enforcing, in an add_header nginx includes" \
    grep -q '^add_header Content-Security-Policy "default-src '"'"'self'"'"';' <<<"$policy"
  check "back office: script-src is 'self' plus the hash of the theme script, nothing else" \
    grep -qF "script-src 'self' $theme_hash;" <<<"$policy"
  check "back office: no 'unsafe-inline' and no 'unsafe-eval' anywhere" \
    bash -c '! grep -q -e unsafe-inline -e unsafe-eval <<<"$0"' "$policy"
  check "back office: style-src is 'self' only" grep -qF "style-src 'self';" <<<"$policy"
  check "back office: the API origin (not its path) is allowed for connect-src and img-src" \
    bash -c 'grep -qF "connect-src '"'"'self'"'"' https://api.meetstudent.test;" <<<"$0" && grep -qF "img-src '"'"'self'"'"' data: blob: https://api.meetstudent.test;" <<<"$0" && ! grep -q "api.meetstudent.test/" <<<"$0"' "$policy"
  check "back office: blob: is allowed for images and media (private documents are fetched as blobs)" \
    bash -c 'grep -qF "media-src '"'"'self'"'"' blob:;" <<<"$0"' "$policy"
  check "back office: object-src allows blob: (a private PDF opens in a blob: tab that inherits the policy) and nothing else" \
    bash -c 'grep -qF "object-src blob:;" <<<"$0" && ! grep -E "object-src [^;]*(self|https?:|data:|\*|none)" <<<"$0"' "$policy"
  check "back office: no iframe source is opened (frame-src is not set: the code has no iframe)" \
    bash -c '! grep -q "frame-src" <<<"$0"' "$policy"
  check "back office: base, forms and framing are locked" \
    bash -c 'for d in "base-uri '"'"'self'"'"'" "form-action '"'"'self'"'"'" "frame-ancestors '"'"'none'"'"'"; do grep -qF "$d" <<<"$0" || exit 1; done' "$policy"
  check "back office: a relative BO_API_URL (same origin) adds no external source" \
    bash -c '! grep -q "https\?://" <<<"$0"' "$(bo_policy "$bo_index" /api/v1 || echo http://FAILED)"
  check "back office: a port in BO_API_URL is kept" \
    grep -qF "connect-src 'self' http://localhost:8080;" <<<"$(bo_policy "$bo_index" http://localhost:8080/api/v1 || true)"

  local evil
  for evil in "https://x.test'; script-src * ;/api" "https://x.test;y.test/api" 'ftp://x.test/api' 'api.x.test/api'; do
    check "back office: the script refuses BO_API_URL=$evil (it would end up inside a header)" \
      bo_refuses "$bo_index" "$evil"
  done

  local changed
  changed=$(mktemp -d)
  sed 's#var dark = false;#var dark = true;#' "$bo_index" >"$changed/index.html"
  check "back office: editing the inline script changes the hash the script computes (no stale hash)" \
    grep -qF "script-src 'self' $(inline_script_hashes "$changed/index.html" | head -1);" <<<"$(bo_policy "$changed/index.html" /api/v1 || true)"
  sed 's#</head>#<script src="/extra.js"></script><script>alert(1)</script></head>#' "$bo_index" >"$changed/index.html"
  check "back office: every inline script gets its hash, an external one gets none" \
    test "$(bo_policy "$changed/index.html" /api/v1 | grep -o 'sha256-' | wc -l | tr -d ' ')" = 2
  rm -rf "$changed"

  if [ -f "$ROOT/apps/backoffice/dist/backoffice/browser/index.html" ]; then
    local built="$ROOT/apps/backoffice/dist/backoffice/browser/index.html"
    check "back office (built output): only the theme script is inline, no inline <style>, no on* handler" \
      bash -c '[ "$(grep -c "<style" "$0")" = 0 ] && ! grep -qE "[[:space:]]on[a-z]+=" "$0"' "$built"
    check "back office (built output): the policy covers the built index.html" \
      grep -qF "script-src 'self' $(inline_script_hashes "$built" | head -1);" <<<"$(bo_policy "$built" /api/v1 || true)"
  fi

  # -- web SSR: Report-Only, hashes written in the proxy files
  local web_hashes web_policy
  web_hashes=$(inline_script_hashes "$web_index")
  web_policy=$(dyn '.http.middlewares."sec-web".headers.customResponseHeaders."Content-Security-Policy-Report-Only"')
  check "web: the proxy sends Content-Security-Policy-Report-Only on the web router" \
    grep -q "^default-src 'self'; script-src 'self' " <<<"$web_policy"
  check "web: both inline scripts of index.html (theme, session marker) are hashed in the policy" \
    bash -c 'while read -r h; do grep -qF "$h" <<<"$0" || exit 1; done <<<"$1"; [ "$(wc -l <<<"$1" | tr -d " ")" = 2 ]' "$web_policy" "$web_hashes"
  check "web: the inline onload handler Angular adds to its stylesheet link is allowed by hash, not by 'unsafe-inline' in script-src" \
    bash -c 'grep -qF "script-src-attr '"'"'unsafe-hashes'"'"' '"'"'sha256-MhtPZXr7+LpJUY5qtMutB+qWfQtMaPccfe7QXtCcEYc='"'"'" <<<"$0" && ! grep -qE "script-src [^;]*unsafe-inline" <<<"$0"' "$web_policy"
  check "web: it stays Report-Only, nothing enforced beyond frame-ancestors" \
    test "$(dyn '.http.middlewares."sec-web".headers.contentSecurityPolicy')" = "frame-ancestors 'none'"
  check "web: same origin only (connect-src 'self'), object-src 'none'" \
    bash -c 'grep -qF "connect-src '"'"'self'"'"';" <<<"$0" && grep -qF "object-src '"'"'none'"'"'" <<<"$0"' "$web_policy"

  # -- the proxy files
  check "proxy: the API middleware sec keeps frame-ancestors only (JSON needs no page policy)" \
    bash -c '[ "$(yq -r ".http.middlewares.sec.headers.contentSecurityPolicy" "$0")" = "frame-ancestors '"'"'none'"'"'" ] && [ "$(yq -r ".http.middlewares.sec.headers.customResponseHeaders" "$0")" = null ]' "$DYNAMIC_FILE"
  check "proxy: sec-bo sets no CSP of its own (a Traefik one would overwrite the back office's)" \
    bash -c '[ "$(yq -r ".http.middlewares.\"sec-bo\".headers.contentSecurityPolicy" "$0")" = null ] && [ "$(yq -r ".http.middlewares.\"sec-bo\".headers.stsSeconds" "$0")" = 31536000 ]' "$DYNAMIC_FILE"
  local prod_labels='.services | map(.labels // {}) | add'
  check "compose.prod.yml: the web router uses sec-web" \
    test "$(prod "$prod_labels | .\"traefik.http.routers.web.middlewares\"")" = sec-web@docker
  check "compose.prod.yml: the back office router uses sec-bo" \
    test "$(prod "$prod_labels | .\"traefik.http.routers.backoffice.middlewares\"")" = sec-bo@docker
  check "compose.prod.yml: the api routers keep sec" \
    test "$(prod "$prod_labels | [.\"traefik.http.routers.api-auth.middlewares\", .\"traefik.http.routers.api-upload.middlewares\", .\"traefik.http.routers.api.middlewares\", .\"traefik.http.routers.uploads.middlewares\"] | map(endswith(\",sec@docker\")) | all")" = true
  # -- compose.coolify.yml: which router gets which middleware (same split as compose.prod.yml)
  local coolify_labels='.services | map(.labels // {}) | add'
  coolify() { rendered | jq -r "$coolify_labels | $1"; }
  check "compose.coolify.yml: the web router uses sec-web@file" \
    test "$(coolify '."traefik.http.routers.web.middlewares"')" = sec-web@file
  check "compose.coolify.yml: the back office router uses sec-bo@file" \
    test "$(coolify '."traefik.http.routers.backoffice.middlewares"')" = sec-bo@file
  check "compose.coolify.yml: the api routers keep sec@file" \
    test "$(coolify '[."traefik.http.routers.api-auth.middlewares", ."traefik.http.routers.api-upload.middlewares", ."traefik.http.routers.api.middlewares", ."traefik.http.routers.uploads.middlewares"] | map(endswith(",sec@file")) | all')" = true
  check "compose.coolify.yml: the web and back office routers do not use the bare sec@file" \
    test "$(coolify '[."traefik.http.routers.web.middlewares", ."traefik.http.routers.backoffice.middlewares"] | map(split(",") | index("sec@file") != null) | any')" = false
  local referenced missing=""
  referenced=$(coolify 'to_entries | map(select(.key | test("^traefik\\.http\\.routers\\..*\\.middlewares$"))) | .[].value' | tr ',' '\n' | grep '@file$' | sed 's/@file$//' | sort -u)
  for m in $referenced; do
    [ "$(dyn ".http.middlewares.\"$m\" | type")" = '!!map' ] || missing="$missing $m"
  done
  check "compose.coolify.yml: every @file middleware it references exists in the dynamic file${missing:+ (missing:$missing)}" \
    test -n "$referenced" -a -z "$missing"
  check "compose.prod.yml: the web Report-Only policy is the Coolify file's, byte for byte (and not empty)" \
    test -n "$web_policy" -a "$(prod '.services | map(.labels // {}) | add | ."traefik.http.middlewares.sec-web.headers.customresponseheaders.Content-Security-Policy-Report-Only"')" = "$web_policy"
  check "compose.prod.yml: sec-bo carries no CSP label" \
    test "$(prod '.services | map(.labels // {}) | add | keys | map(select(startswith("traefik.http.middlewares.sec-bo.headers.contentsecuritypolicy"))) | length')" = 0
}

# --- live: the stack behind a replica of Coolify's Traefik ---------------------

PROJECT=${PROJECT:-meetstudent-coolify-test}
TEST_PROXY_NETWORK=${TEST_PROXY_NETWORK:-meetstudent-test-coolify}
# The api's dedicated network (owner-created on the real server): its subnet must match TRUSTED_PROXIES' default.
TEST_API_NETWORK=${TEST_API_NETWORK:-meetstudent-test-api-edge}
TEST_API_SUBNET=${TEST_API_SUBNET:-10.250.77.0/24}
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
  docker network rm "$PROXY_NETWORK" "$API_EDGE_NETWORK" >/dev/null 2>&1 || true
}

# Entrypoints, providers and resolver named like the default proxy of Coolify v4
# (/data/coolify/proxy/docker-compose.yml on the server; re-check it on the real
# instance). The ACME CA is unreachable on purpose, so routers fall back to
# Traefik's self-signed default certificate (the "local certificate").
start_proxy() {
  docker network create "$PROXY_NETWORK" >/dev/null
  docker network create --subnet "$TEST_API_SUBNET" "$API_EDGE_NETWORK" >/dev/null
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
  # Like the owner step on the real server: Traefik joins the api's dedicated network too.
  docker network connect "$API_EDGE_NETWORK" "$PROXY_NAME"
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
  export API_EDGE_NETWORK=$TEST_API_NETWORK
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
  local api_ip
  api_ip=$(ip_on "$(stack ps -q api)" "$API_EDGE_NETWORK")
  check "control: the api answers on its dedicated network" \
    docker run --rm --network "$API_EDGE_NETWORK" alpine:latest nc -z -w 3 "$api_ip" 8080
  check "the api is unreachable from the proxy network (a neighbour cannot bypass Traefik's limits)" \
    bash -c '! docker exec "$0" nc -z -w 3 "$1" 8080' "$DECOY_NAME" "$api_ip"
  check "the api container is not attached to the proxy network" \
    bash -c '! docker inspect -f "{{json .NetworkSettings.Networks}}" "$0" | jq -e --arg n "$1" "has(\$n)"' \
      "$(stack ps -q api)" "$PROXY_NETWORK"
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
  static) static_tests; csp_tests ;;
  live) static_tests; csp_tests; live_tests ;;
  *) echo "usage: $0 [static|live]" >&2; exit 2 ;;
esac

if [ "$failures" -gt 0 ]; then echo "$failures failure(s)"; exit 1; fi
echo "all passed"
