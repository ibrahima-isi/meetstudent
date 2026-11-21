# Monorepo — structure and subtree history

This repository was assembled from two previously separate repositories using
`git subtree add`. Both source histories were preserved: every commit that
existed in the original repos is still reachable here, rewritten under its new
prefix.

## Structure

```
apps/
  api/          Spring Boot 3 / Java 21 REST API (Maven)
  web/          Angular 20 + SSR client (npm)
  backoffice/   ADMIN-only back office (Angular 20 SPA, static build served by nginx);
                a vanilla TypeScript + Vite rewrite is planned, see docs/plans/2026-10-10-backoffice-vite-rewrite.md
docs/           cross-cutting documentation (this file)
infra/          production under Coolify: docker-bake.hcl (CI images), coolify/proxy/dynamic/
                (Traefik middlewares), backup/ (backup image), tests/ (their shell tests)
shared/         placeholder
compose.yml       full local stack: Postgres 18 + api + web
compose.dev.yml   dev override: hot-reloading api
compose.prod.yml  production stack for one VPS (Traefik + TLS); see docs/DEPLOY.md
compose.coolify.yml  production stack under Coolify (GHCR images); see docs/DEPLOY.md section 9
.github/workflows/ci.yml   the only CI entry point
```

Two Docker details that cost time once and will again otherwise: Postgres 18+
must be mounted at `/var/lib/postgresql`, not `.../data`, or it aborts at
startup; and the dev override needs `entrypoint: []` plus its own `image:` tag,
because the runtime stage sets `ENTRYPOINT java -jar app.jar` and would
otherwise both prefix the command and overwrite the production image.

**Always run `docker compose down` once the tests are done.** Every stack that
gets started gets torn down — this is a standing rule, not a tidiness
preference. A stack left running holds the Postgres volume and ports 4200, 8080
and 5432, so the next `docker compose up` collides with it, and the failure
surfaces as a port bind error or a container that silently reuses stale data
rather than as anything that names the real cause. Use `docker compose down -v`
when the database itself should be reset.

Each app keeps its own build, dependencies and agent instructions. There is no
root package manager: always run build commands from inside `apps/<app>`.

## How each subtree was added

Values below are read from the `git-subtree-*` trailers on the merge commits,
not from memory.

| Prefix | Merge commit | Source commit (`git-subtree-split`) | Content at the time |
|---|---|---|---|
| `apps/api` | `0c4622a` | `7899a12` | ex-`backend`, root-level `pom.xml`, `src/`, `mvnw` |
| `apps/web` | `9d13c1a` | `c8cf8e9` | ex-`customer_frontend`, root-level `angular.json`, `src/` |

The `apps/api` subtree was added first, onto mainline `66cdd57`; `apps/web`
followed, onto mainline `0c4622a`.

## Upstream remotes: none, deliberately

**There is no live remote to pull either subtree from.** As of the migration:

- `git remote -v` lists only `origin` → `git@github-isi:ibrahima-isi/meetstudent.git`
- no repository named `backend` or `customer_frontend` exists under the
  `ibrahima-isi` account
- a GitHub-wide commit search for `c8cf8e9` returns only this repository

In other words the two source repos are gone, or live somewhere not reachable
from this account. The monorepo is now the single source of truth, and that is
the intended end state — the subtrees are historical, not tracked.

Practical consequence: **do not run `git subtree pull`.** There is nothing
upstream to pull. Work directly in `apps/api` and `apps/web`.

### If you ever re-attach an upstream

Should an old repo resurface and you want to pull changes back in, the command
shape is:

```bash
git remote add api-upstream <url-of-the-old-backend-repo>
git fetch api-upstream
git subtree pull --prefix=apps/api api-upstream <branch> --squash
```

`--squash` keeps the imported history collapsed into a single commit, which is
what the original `subtree add` did not do — expect the first such pull to be
noisy if you switch strategies mid-flight. Use the same shape for `apps/web`
with `--prefix=apps/web`.

### Adding a future app (e.g. the backoffice)

If `apps/backoffice` is ever populated from an existing repository:

```bash
git remote add bo-upstream <url>
git fetch bo-upstream
git subtree add --prefix=apps/backoffice bo-upstream <branch> --squash
```

If it is started from scratch, no subtree is involved — just create the
directory and add a job to `.github/workflows/ci.yml`.

## Branches and project memory

[AGENTS.md](../AGENTS.md) defines the GitHub Flow branch, commit, authorization and cleanup policy. [CLAUDE.md](../CLAUDE.md) holds the rest of the shared project memory. Working branches start from `origin/main`, and an authorized PR targets `main`. A merge into `main` starts the production workflow described in [DEPLOY.md](DEPLOY.md#9-coolify-production-on-the-hetzner-vps).

### Retiring the legacy integration branch

Keep `dev` until its existing work is incorporated. After explicit authorization, open a one-time `dev` → `main` PR and use a GitHub merge commit to retain the original commits. Review the production changes before authorizing that merge. Verify GitHub reports the PR merged, then fetch and check `git merge-base --is-ancestor origin/dev origin/main`. Compare any local `dev` tip as well. Update outstanding working branches from `origin/main` before resuming them.

After separate cleanup approval, go to **Settings → Rules → Rulesets** and remove the `refs/heads/dev` target from `protected-branches` and `required-ci`. In **Settings → Branches**, check for a classic rule that still blocks deleting `dev` and retire the `dev`-specific rule if present. Keep the active rules for `main`. Delete the verified branch through **Branches** on GitHub and with `git branch -d dev` locally where the ancestry check passes. A `protected branch hook declined` response can indicate a classic rule even after the ruleset target has been removed.

After the migration, use **Settings → General → Pull Requests** to disable **Allow merge commits** and edit `github-flow-main` under **Rules → Rulesets** to allow only squash merges. Rebase merging, automatic merging, and automatic head-branch deletion should be off.

### Branch history

PR #18 realigned the old pre-monorepo `dev` with `main` without rewriting history. The retired `stage` branch never received the monorepo layout. Its classic branch protection had to be removed separately from its ruleset target before deletion.

## Working with agents: ask interactively, always

Any question to the owner goes through the `AskUserQuestion` tool, with the
recommended option first and at most 4 questions per call. No wall of questions
in prose, no numbered question lists in a message, even when a skill prescribes
them: the tool wins over the skill. Prose is for reporting results only. The
same rule is stated in the root `CLAUDE.md` (Working agreement → Sessions).

## Ticket completion tracking: one file per spec

Tickets are tracked in `.scratch/<spec-folder>/completion.md`, one file per spec, stored in the spec's own folder, created the moment the spec's tickets are created. When every ticket of a spec is merged and confirmed, the file is final and agents move on; the owner reads it to check the tickets were implemented, then deletes it. Details: `docs/agents/issue-tracker.md`.

## CI and branch protection

[CI](../.github/workflows/ci.yml) runs for PRs targeting `main` and pushes published to `main` after a merge. Protect `main` with these active rulesets and empty bypass lists:

| Ruleset | Main branch requirements |
|---|---|
| `protected-branches` | Block deletion and force pushes. Require verified commit signatures. |
| `required-ci` | Require `api / build-and-test` and `web / build-and-test` from GitHub Actions. Require an up-to-date branch. |
| `github-flow-main` | Require a PR with zero approvals for the solo maintainer. Require `backoffice / build-and-test` from GitHub Actions and an up-to-date branch. Allow squash merges and temporarily allow merge commits for the `dev` migration. |

GitHub matches required checks by the job's published `name:`. Update the ruleset when renaming a required check. Use one entry per branch pattern and select check names from autocomplete.

`changes / detect` lets a PR skip `api / build-and-test` if it touches neither `apps/api/` nor `ci.yml`. GitHub counts the skipped job as passing. Main runs always test the API, and a failed or silent filter also runs it. `images / build` checks PR images, `compose / validate-coolify` validates the deployment configuration, and `deploy / production` runs only after the three app checks pass on `main`. The CI contract is covered by [infra/tests/ci-images.test.sh](../infra/tests/ci-images.test.sh).

Maintain dependencies through explicitly authorized PRs. [Dependabot configuration](../.github/dependabot.yml) pauses scheduled version-update PRs for all ecosystems. Keep **Dependabot security updates** off under **Settings → Advanced Security** to prevent automatic security PRs.
