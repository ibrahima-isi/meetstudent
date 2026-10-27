#!/usr/bin/env bash
# The Coolify deployment guide keeps up with what it describes (coolify-deploy/04).
#
# docs/DEPLOY.md's Coolify section is what the owner follows to fill in the
# Coolify resource and the GitHub `production` environment: every variable
# compose.coolify.yml reads, and every secret the deploy job reads, must be
# named there. The backup service's variables are also listed in
# apps/api/docs/production.md. A variable added to the stack without its line
# in the guide turns this red. Run by the CI (compose / validate-coolify).
#
# Needs: yq (mikefarah v4), awk, grep. Run from anywhere:
#   infra/tests/deploy-docs.test.sh            # every test
#   infra/tests/deploy-docs.test.sh test_name  # one test
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 2

STACK_FILE=compose.coolify.yml
WORKFLOW=.github/workflows/ci.yml
GUIDE=docs/DEPLOY.md
PRODUCTION=apps/api/docs/production.md

failures=0
fail() { echo "    FAIL: $*"; failures=$((failures + 1)); }

# Variables a compose text interpolates: ${VAR}, ${VAR:-x}, ${VAR:?x}, never
# the escaped $${VAR} that the container's shell expands.
interpolated() { grep -oE '(^|[^$])\$\{[A-Za-z_][A-Za-z0-9_]*' | sed -E 's/.*\$\{//' | sort -u; }

# The second-level section of a Markdown file whose heading mentions $2.
section() { awk -v h="$2" '/^## / { inside = index($0, h) > 0 } inside' "$1"; }

# Each name of stdin must appear as `NAME` in the text $1; $2 says where.
all_named_in() {
  local text=$1 where=$2 var n=0
  while read -r var; do
    n=$((n + 1))
    grep -qF "\`$var\`" <<<"$text" || fail "$var is not documented in $where"
  done
  [[ $n -gt 0 ]] || fail "nothing to look for in $where"
}

test_the_guide_names_every_variable_of_the_coolify_stack() {
  local coolify
  coolify="$(section "$GUIDE" Coolify)"
  [[ -n "$coolify" ]] || { fail "$GUIDE has no Coolify section"; return; }
  all_named_in "$coolify" "the Coolify section of $GUIDE" < <(interpolated <"$STACK_FILE")
}

test_the_guide_names_every_secret_of_the_deploy_job() {
  all_named_in "$(section "$GUIDE" Coolify)" "the Coolify section of $GUIDE" < <(
    yq '.jobs.deploy' "$WORKFLOW" | grep -oE 'secrets\.[A-Z_]+' | sed 's/secrets\.//' \
      | grep -vx GITHUB_TOKEN | sort -u)
}

# Variables only the backup service reads: its own configuration.
test_the_production_guide_names_every_backup_variable() {
  all_named_in "$(cat "$PRODUCTION")" "$PRODUCTION" < <(
    comm -23 <(yq '.services.backup' "$STACK_FILE" | interpolated) \
      <(yq '.services | del(.backup)' "$STACK_FILE" | interpolated))
}

test_relative_links_of_both_guides_resolve() {
  local doc link target
  for doc in "$GUIDE" "$PRODUCTION"; do
    while read -r link; do
      target="${link%%#*}"
      [[ -z "$target" ]] && continue
      [[ -e "$(dirname "$doc")/$target" ]] || fail "$doc links to a missing $link"
    done < <(grep -oE '\]\([^)]+\)' "$doc" | sed -E 's/^\]\(|\)$//g' | grep -vE '^[a-z]+:')
  done
}

# --- runner -----------------------------------------------------------------
tests=("$@")
if [[ ${#tests[@]} -eq 0 ]]; then
  while read -r t; do tests+=("$t"); done < <(declare -F | awk '$3 ~ /^test_/ {print $3}')
fi
for t in "${tests[@]}"; do
  before=$failures
  "$t"
  if [[ $failures -eq $before ]]; then echo "ok   $t"; else echo "FAIL $t"; fi
done
if [[ $failures -eq 0 ]]; then echo "all passed"; else echo "$failures assertion(s) failed"; exit 1; fi
