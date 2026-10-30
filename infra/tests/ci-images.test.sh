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
  assert_eq "meetstudent-api meetstudent-backoffice meetstudent-backup meetstudent-web" \
    "$(jq -r '.target | keys | join(" ")' <<<"$def")" "images built"
  for img in meetstudent-api meetstudent-web meetstudent-backoffice meetstudent-backup; do
    assert_eq "$REGISTRY/$img:sha-$SHA $REGISTRY/$img:latest" \
      "$(jq -r --arg t "$img" '.target[$t].tags | join(" ")' <<<"$def")" "$img tags"
    assert_eq "linux/amd64" \
      "$(jq -r --arg t "$img" '.target[$t].platforms | join(" ")' <<<"$def")" "$img platforms"
  done
  assert_eq "apps/api apps/web apps/backoffice infra/backup" \
    "$(jq -r '[.target["meetstudent-api","meetstudent-web","meetstudent-backoffice","meetstudent-backup"].context] | join(" ")' <<<"$def")" \
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

# Deploys queue one after another instead of cancelling each other. A new
# push cancels the superseded run on a PR or `dev`, never on `main`: there
# the run carries a deploy, and cancelling it could stop it mid-push.
test_deploys_queue_instead_of_cancelling_each_other() {
  assert_eq '{"group":"deploy-production","cancel-in-progress":false}' \
    "$(job deploy | jq -c '.concurrency')" "deploy concurrency"
  assert_eq '{"group":"${{ github.workflow }}-${{ github.ref }}","cancel-in-progress":"${{ github.ref != '"'refs/heads/main'"' }}"}' \
    "$(yq -o=json -I=0 '.concurrency' "$WORKFLOW")" "workflow concurrency"
}

# --- deploy webhook (coolify-deploy/04) --------------------------------------
# Once the images are pushed, the deploy job asks Coolify to deploy through the
# resource's authenticated webhook. Nothing else may trigger a deploy.

# A local stand-in for Coolify's webhook: answers $1 and appends the request's
# path and Authorization header to $2. Prints its port once listening.
fake_coolify() {
  # exec: the background job's pid is the server itself, so kill stops it.
  exec python3 -u - "$1" "$2" <<'PY'
import http.server, sys
status, log = int(sys.argv[1]), sys.argv[2]
class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        with open(log, "a") as f:
            f.write("%s %s\n" % (self.path, self.headers.get("Authorization", "")))
        self.send_response(status)
        if 300 <= status < 400:
            self.send_header("Location", "https://127.0.0.1:1/api/v1/deploy")
        self.end_headers()
        self.wfile.write(b'{"message":"fake coolify"}')
    def log_message(self, *a): pass
s = http.server.HTTPServer(("127.0.0.1", 0), H)
print(s.server_address[1], flush=True)
s.serve_forever()
PY
}

# Runs the deploy job's webhook step as the runner does (bash -eo pipefail)
# against a fake Coolify answering $1. Sets step_status, step_output, step_seen.
TOKEN="test-coolify-token-$$"
run_webhook_step() {
  local script tmp port pid
  script="$(job deploy | jq -r '[(.steps // [])[] | select((.env // {}) | has("COOLIFY_TOKEN"))][0].run // "false"')"
  tmp="$(mktemp -d)"
  fake_coolify "$1" "$tmp/seen" >"$tmp/port" &
  pid=$!
  for _ in $(seq 50); do [[ -s "$tmp/port" ]] && break; sleep 0.1; done
  port="$(cat "$tmp/port")"
  step_output="$(COOLIFY_TOKEN="$TOKEN" COOLIFY_WEBHOOK="http://127.0.0.1:$port/api/v1/deploy?uuid=test" \
    bash --noprofile --norc -eo pipefail -c "$script" 2>&1)"
  step_status=$?
  step_seen="$(cat "$tmp/seen" 2>/dev/null)"
  kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
  rm -rf "$tmp"
}

test_deploy_ends_by_calling_the_coolify_webhook_with_the_production_secrets() {
  local j last
  j="$(job deploy)"
  last="$(jq -c '.steps[-1]' <<<"$j")"
  assert_eq '{"COOLIFY_TOKEN":"${{ secrets.COOLIFY_TOKEN }}","COOLIFY_WEBHOOK":"${{ secrets.COOLIFY_WEBHOOK }}"}' \
    "$(jq -c '.env // null' <<<"$last")" "last deploy step env"
  assert_eq "true" "$(jq '.steps[-2].uses // "" | startswith("docker/bake-action@")' <<<"$j")" \
    "the images are pushed right before the webhook"
  # Secrets reach the script through env only: `${{ }}` inside `run` is pasted
  # into the script text, where it can leak or be injected.
  assert_eq "false" "$(jq '.run // "" | contains("${{")' <<<"$last")" "no expression inside run"
  # Only the deploy job (environment production, main only) may touch them.
  assert_eq "deploy" \
    "$(yq -o=json '.jobs' "$WORKFLOW" | jq -r '[to_entries[] | select(tostring | test("COOLIFY_")) | .key] | join(",")')" \
    "jobs referencing COOLIFY_*"
}

test_webhook_step_asks_coolify_once_with_the_bearer_token() {
  run_webhook_step 200
  assert_eq "0" "$step_status" "exit status on 200 (output: $step_output)"
  assert_eq "/api/v1/deploy?uuid=test Bearer $TOKEN" "$step_seen" "requests received by Coolify"
}

test_webhook_failure_turns_the_deploy_red() {
  local code
  # A redirect (say http:// to https://) deploys nothing: it must not pass either.
  for code in 302 401 404 500; do
    run_webhook_step "$code"
    [[ -n "$step_seen" ]] || fail "Coolify was never called (answer $code)"
    [[ "$step_status" -ne 0 ]] || fail "webhook answering $code left the step green"
  done
}

test_webhook_step_never_prints_the_token() {
  local code
  for code in 200 500; do
    run_webhook_step "$code"
    [[ "$step_output" != *"$TOKEN"* ]] || fail "token printed on $code: $step_output"
  done
}

# --- hardening of the workflow ------------------------------------------------

# A tag can be moved to other code; a full commit SHA cannot. The comment keeps
# the version readable and is what Dependabot updates along with the SHA.
test_every_action_is_pinned_to_a_full_commit_sha() {
  local bad
  bad="$(yq '.jobs[].steps[] | select(has("uses")) | .uses + " " + (.uses | line_comment)' "$WORKFLOW" \
    | grep -vE '^[a-z0-9-]+/[a-z0-9-]+@[0-9a-f]{40} v[0-9]+\.[0-9]+\.[0-9]+$')"
  assert_eq "" "$bad" "actions not pinned to <sha> # vX.Y.Z"
}

test_dependabot_keeps_the_pinned_actions_up_to_date_on_dev() {
  local f=.github/dependabot.yml
  [[ -f "$f" ]] || { fail "$f missing"; return; }
  assert_eq "2" "$(yq '.version' "$f")" "dependabot version"
  assert_eq '{"package-ecosystem":"github-actions","directory":"/","interval":"weekly","target-branch":"dev"}' \
    "$(yq -o=json -I=0 '.updates[] | select(.package-ecosystem == "github-actions")
      | {"package-ecosystem": .package-ecosystem, "directory": .directory, "interval": .schedule.interval, "target-branch": .target-branch}' "$f")" \
    "github-actions updates"
}

# Every job gets a read-only token unless it says otherwise (only deploy does).
test_the_token_is_read_only_by_default() {
  assert_eq '{"contents":"read"}' "$(yq -o=json -I=0 '.permissions' "$WORKFLOW")" "workflow permissions"
  assert_eq "deploy" "$(yq '[.jobs | to_entries[] | select(.value.permissions.packages == "write") | .key] | join(",")' "$WORKFLOW")" \
    "jobs allowed to write packages"
}

# A hung push or webhook must not hold the deploy queue for the 6-hour default.
test_a_hung_deploy_is_stopped() {
  assert_eq "30" "$(job deploy | jq -r '."timeout-minutes"')" "deploy timeout-minutes"
  local run
  run="$(job deploy | jq -r '.steps[-1].run')"
  [[ "$run" == *"--max-time 60"* ]] || fail "webhook curl has no --max-time 60: $run"
  [[ "$run" == *"-fsS -L --max-redirs 0"* ]] || fail "webhook curl lost -fsS -L --max-redirs 0: $run"
}

# --- api path filter ----------------------------------------------------------
# A pull request that leaves the API untouched (docs, web, Dependabot) skips the
# 6-7 minute api job; a skipped job satisfies the required check. Every push,
# and any doubt (the filter failing or answering nothing), runs it.

# Runs the changes job's filter step, as the runner does, in a throwaway
# repository whose HEAD is the merge commit GitHub checks out for a pull
# request: first parent the base, second parent a commit touching "$@".
# Prints the `api` output.
run_api_filter() { # event file...
  local event="$1" script tmp f
  shift
  script="$(job changes | jq -r '[(.steps // [])[] | select(.id == "filter")][0].run // "false"')"
  tmp="$(mktemp -d)"
  (
    cd "$tmp" || exit 1
    git init -q -b base && git config user.email t@t && git config user.name t \
      && git config commit.gpgsign false
    echo base >README.md && git add . && git commit -qm base
    git switch -qc pr
    for f in "$@"; do mkdir -p "$(dirname "$f")" && echo change >"$f"; done
    git add . && git commit -qm pr
    git switch -q base && git merge -q --no-ff -m merge pr
    : >"$tmp/out"
    EVENT_NAME="$event" GITHUB_OUTPUT="$tmp/out" bash --noprofile --norc -eo pipefail -c "$script" >/dev/null 2>&1
    sed -n 's/^api=//p' "$tmp/out"
  )
  rm -rf "$tmp"
}

test_api_tests_are_skipped_only_when_a_pull_request_leaves_the_api_untouched() {
  assert_eq "false" "$(run_api_filter pull_request docs/x.md apps/web/src/a.ts .github/dependabot.yml)" \
    "PR touching docs, web and dependabot.yml"
  assert_eq "true" "$(run_api_filter pull_request docs/x.md apps/api/pom.xml)" "PR touching apps/api"
  assert_eq "true" "$(run_api_filter pull_request .github/workflows/ci.yml)" "PR touching ci.yml"
  assert_eq "true" "$(run_api_filter push docs/x.md)" "push touching docs only"
}

test_api_job_runs_unless_the_filter_answers_false() {
  local j
  j="$(job api)"
  assert_eq "changes" "$(jq -r '.needs | if type == "array" then join(",") else . end' <<<"$j")" "api needs"
  # != 'false', not == 'true': a failed or silent filter runs the tests (fail closed).
  assert_eq "\${{ !cancelled() && needs.changes.outputs.api != 'false' }}" "$(jq -r '.if' <<<"$j")" \
    "api condition"
  assert_eq '{"api":"${{ steps.filter.outputs.api }}"}' "$(job changes | jq -c '.outputs')" "changes outputs"
  # The script reads the event from env: `${{ }}` inside `run` is pasted into the script.
  assert_eq "false" \
    "$(job changes | jq '[(.steps // [])[] | select(.id == "filter")][0].run // "" | contains("${{")')" \
    "no expression inside the filter script"
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
