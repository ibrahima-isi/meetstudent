#!/usr/bin/env bash
# Tests for the production images built and published by the CI
# (coolify-deploy/01, ADR 0003).
#
# What is observable locally is checked here: the image definition that
# `docker buildx bake` resolves (names, tags, platform, build args) and the
# contract of the CI jobs that build and publish it. The real GHCR push is
# only proven by a run on `main` (coolify-deploy/05).
#
# Needs: docker with buildx, jq, yq (mikefarah v4), git. Run from anywhere:
#   infra/tests/ci-images.test.sh            # every test
#   infra/tests/ci-images.test.sh test_name  # one test
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT" || exit 2

# A fixed, full commit SHA: the tags must carry all 40 characters.
SHA="0123456789abcdef0123456789abcdef01234567"
REGISTRY="ghcr.io/ibrahima-isi"
# The image definition. Bake runs from the repository root, as in the CI.
BAKE_FILE="infra/docker-bake.hcl"

failures=0
fail() { echo "    FAIL: $*"; failures=$((failures + 1)); }
assert_eq() { # expected actual message
  [[ "$1" == "$2" ]] || fail "$3"$'\n'"      expected: $1"$'\n'"      actual:   $2"
}

# The resolved image definition, as the CI would build it for $SHA. The file is
# named explicitly: by default bake would also merge compose.yml into it.
bake_print() { GITHUB_SHA="$SHA" docker buildx bake -f "$BAKE_FILE" --print 2>/dev/null; }

test_each_production_image_is_tagged_with_its_commit_and_latest() {
  local def
  def="$(bake_print)" || { fail "docker buildx bake --print failed"; return; }
  assert_eq "meetstudent-api meetstudent-backoffice meetstudent-web" \
    "$(jq -r '.target | keys | join(" ")' <<<"$def")" "images built"
  for img in meetstudent-api meetstudent-web meetstudent-backoffice; do
    assert_eq "$REGISTRY/$img:sha-$SHA $REGISTRY/$img:latest" \
      "$(jq -r --arg t "$img" '.target[$t].tags | join(" ")' <<<"$def")" "$img tags"
    assert_eq "linux/amd64" \
      "$(jq -r --arg t "$img" '.target[$t].platforms | join(" ")' <<<"$def")" "$img platforms"
  done
  assert_eq "apps/api apps/web apps/backoffice" \
    "$(jq -r '[.target["meetstudent-api","meetstudent-web","meetstudent-backoffice"].context] | join(" ")' <<<"$def")" \
    "build contexts"
  assert_eq "dist/backoffice/browser" \
    "$(jq -r '.target["meetstudent-backoffice"].args.DIST_DIR' <<<"$def")" "back office DIST_DIR"
}

test_no_image_is_tagged_without_a_full_commit_sha() {
  local bad out
  for bad in "" "0123456" "${SHA}0" "not-a-sha-not-a-sha-not-a-sha-not-a-sha!"; do
    if out="$(GITHUB_SHA="$bad" docker buildx bake -f "$BAKE_FILE" --print 2>&1)"; then
      fail "accepted GITHUB_SHA='$bad'"
    elif [[ "$out" != *"GITHUB_SHA must be the full 40-character commit SHA"* ]]; then
      fail "GITHUB_SHA='$bad' refused for another reason: $(tail -1 <<<"$out")"
    fi
  done
}

WORKFLOW=.github/workflows/ci.yml
job() { yq -o=json ".jobs.$1" "$WORKFLOW"; }

# The rulesets require checks by job `name:` (docs/MONOREPO.md): renaming a
# test job, or giving a new job one of these names, breaks every merge.
test_required_test_checks_keep_their_names() {
  assert_eq "api / build-and-test|web / build-and-test|backoffice / build-and-test" \
    "$(yq '[.jobs.api.name, .jobs.web.name, .jobs.backoffice.name] | join("|")' "$WORKFLOW")" \
    "test job names"
  assert_eq "3" "$(yq '[.jobs[] | select(.name == "*/ build-and-test")] | length' "$WORKFLOW")" \
    "jobs named like a test check"
}

test_workflow_passes_actionlint() {
  docker run --rm -v "$ROOT:/repo" -w /repo rhysd/actionlint:1.7.12 -no-color "$WORKFLOW" \
    || fail "actionlint reported errors"
}

test_pull_request_builds_every_image_without_pushing() {
  local j
  j="$(job images)"
  assert_eq "github.event_name == 'pull_request'" "$(jq -r '.if' <<<"$j")" "images job condition"
  assert_eq '{"contents":"read"}' "$(jq -c '.permissions' <<<"$j")" "images job permissions"
  local bake
  bake="$(jq -c '[(.steps // [])[] | select(.uses // "" | startswith("docker/bake-action@"))]' <<<"$j")"
  assert_eq "1" "$(jq 'length' <<<"$bake")" "one bake step"
  assert_eq "$BAKE_FILE|.|false" \
    "$(jq -r '.[0].with | "\(.files)|\(.source)|\(.push)"' <<<"$bake")" "bake files|source|push"
}

test_promotion_to_main_publishes_every_image_to_ghcr_after_the_tests() {
  local j
  j="$(job deploy)"
  assert_eq "api,web,backoffice" "$(jq -r '.needs | join(",")' <<<"$j")" "deploy needs"
  assert_eq "github.event_name == 'push' && github.ref == 'refs/heads/main'" \
    "$(jq -r '.if' <<<"$j")" "deploy condition"
  assert_eq "production" "$(jq -r '.environment' <<<"$j")" "deploy environment"
  assert_eq '{"contents":"read","packages":"write"}' "$(jq -c '.permissions' <<<"$j")" \
    "deploy permissions"
  local login
  login="$(jq -c '[(.steps // [])[] | select(.uses // "" | startswith("docker/login-action@"))][0].with' <<<"$j")"
  assert_eq 'ghcr.io|${{ github.actor }}|${{ secrets.GITHUB_TOKEN }}' \
    "$(jq -r '"\(.registry)|\(.username)|\(.password)"' <<<"$login")" "GHCR login"
  local bake
  bake="$(jq -c '[(.steps // [])[] | select(.uses // "" | startswith("docker/bake-action@"))]' <<<"$j")"
  assert_eq "1" "$(jq 'length' <<<"$bake")" "one bake step"
  assert_eq "$BAKE_FILE|.|true" \
    "$(jq -r '.[0].with | "\(.files)|\(.source)|\(.push)"' <<<"$bake")" "bake files|source|push"
}

# The images are public. The CI builds from a fresh checkout, so a build
# context holds exactly the tracked files under it: none may be a secret
# (an .env other than an *.example, a private key or a keystore).
test_no_secret_file_reaches_an_image_build_context() {
  local contexts leaked
  contexts="$(bake_print | jq -r '[.target[].context] | unique | .[]')"
  [[ -n "$contexts" ]] || { fail "no build context resolved"; return; }
  # shellcheck disable=SC2086 # one argument per context
  leaked="$(git ls-files -- $contexts \
    | grep -Ei '(^|/)\.env($|\.)|\.(pem|key|p12|pfx|jks|keystore)$|(^|/)id_(rsa|ed25519|ecdsa)($|[^/]*$)' \
    | grep -Ev '\.example$')"
  assert_eq "" "$leaked" "secret files in a build context"
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
