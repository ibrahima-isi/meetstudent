#!/bin/sh
# Runs from /docker-entrypoint.d at container start:
#  1. renders /config.json from BO_API_URL and BO_WEB_URL so one image works for any domain;
#  2. renders the Content-Security-Policy that nginx.conf includes, from the same BO_API_URL
#     (the API is a different origin from the back office, so the policy must name it) and
#     from the hashes of the inline scripts actually present in index.html (no hash to
#     maintain by hand: if the theme script changes, the policy follows at the next start).
# BO_HTML_DIR and BO_CSP_CONF exist for tests; the image uses the defaults.
set -eu

: "${BO_API_URL:?BO_API_URL must be set (e.g. https://example.com/api/v1)}"
html_dir=${BO_HTML_DIR:-/usr/share/nginx/html}
csp_conf=${BO_CSP_CONF:-/tmp/bo-csp.conf}

# --- Content-Security-Policy, validated first so that a bad URL writes nothing ---------------

# Origin (scheme://host[:port]) of the API. A path means the API shares the back office's
# origin, which 'self' already covers. Anything else is refused: the value ends up inside a
# header, so only plain URL characters are accepted (no quote, space or semicolon).
case "$BO_API_URL" in
  /*) api_origin="" ;;
  http://* | https://*)
    api_origin=$(printf '%s' "$BO_API_URL" | sed -E 's#^(https?://[^/?\#]+).*$#\1#')
    if printf '%s' "$api_origin" | grep -Eq '[^]A-Za-z0-9.:/_[-]'; then
      echo "40-write-config.sh: BO_API_URL has characters not allowed in a CSP source: $BO_API_URL" >&2
      exit 1
    fi
    ;;
  *)
    echo "40-write-config.sh: BO_API_URL must be an http(s) URL or a path, got: $BO_API_URL" >&2
    exit 1
    ;;
esac

# 'sha256-<base64>' sources for every inline <script> of index.html, hashing exactly the bytes
# between the tags (whitespace included), as the browser does. Scripts with a src are skipped.
script_hashes() {
  tmp=$(mktemp -d)
  awk -v dir="$tmp" '
    function flush(   f) { f = dir "/" (++n); printf "%s", body > f; close(f) }
    {
      line = $0
      while (1) {
        if (!inside) {
          p = match(line, /<script[ >]/)
          if (!p) break
          rest = substr(line, p)
          e = index(rest, ">")
          if (!e) break
          skip = (substr(rest, 1, e) ~ / src=/)
          line = substr(rest, e + 1)
          inside = 1; body = ""; first = 1
        }
        q = index(line, "</script>")
        if (q) {
          body = body (first ? "" : "\n") substr(line, 1, q - 1)
          if (!skip) flush()
          inside = 0; body = ""
          line = substr(line, q + 9)
        } else {
          body = body (first ? "" : "\n") line
          first = 0
          break
        }
      }
    }
  ' "$html_dir/index.html"
  for f in "$tmp"/*; do
    [ -f "$f" ] || continue
    # hex digest -> base64, 3 bytes (6 hex digits) at a time; busybox has no openssl.
    sha256sum "$f" | cut -d' ' -f1 | awk '
      BEGIN { chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"; q = sprintf("%c", 39) }
      function hexv(c) { return index("0123456789abcdef", c) - 1 }
      {
        out = ""
        for (i = 1; i <= length($0); i += 6) {
          grp = substr($0, i, 6); nb = length(grp) / 2; v = 0
          for (j = 1; j <= 3; j++) v = v * 256 + (j <= nb ? hexv(substr(grp, 2*j-1, 1)) * 16 + hexv(substr(grp, 2*j, 1)) : 0)
          out = out substr(chars, int(v / 262144) + 1, 1) substr(chars, int(v / 4096) % 64 + 1, 1)
          out = out (nb > 1 ? substr(chars, int(v / 64) % 64 + 1, 1) : "=") (nb > 2 ? substr(chars, v % 64 + 1, 1) : "=")
        }
        printf "%ssha256-%s%s ", q, out, q
      }'
  done
  rm -rf "$tmp"
}

hashes=$(script_hashes)

# connect-src: XHR to the API. img-src: school logos live under the API's /uploads/public, and
# blob:/data: serve upload previews. media-src: blob: for private documents, which are fetched
# with the bearer token and opened in a new tab. object-src 'none' rules out plugins;
# frame-ancestors 'none' repeats the proxy's frame protection for any other way of serving.
policy="default-src 'self'; script-src 'self' ${hashes}; style-src 'self'; img-src 'self' data: blob: ${api_origin}; connect-src 'self' ${api_origin}; media-src 'self' blob:; font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"
policy=$(printf '%s' "$policy" | tr -s ' ' | sed -e 's/ ;/;/g')
printf 'add_header Content-Security-Policy "%s" always;\n' "$policy" > "$csp_conf"
echo "40-write-config.sh: wrote Content-Security-Policy (api origin: ${api_origin:-same origin})"

# --- config.json ---------------------------------------------------------------------------

# Escape backslashes and double quotes so the value is always valid JSON.
escaped=$(printf '%s' "$BO_API_URL" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')
web_escaped=$(printf '%s' "${BO_WEB_URL:-http://localhost:4200}" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')
printf '{"apiUrl":"%s","webUrl":"%s"}\n' "$escaped" "$web_escaped" > "$html_dir/config.json"
echo "40-write-config.sh: wrote config.json (apiUrl=$BO_API_URL)"
