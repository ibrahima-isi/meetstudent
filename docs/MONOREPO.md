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
  backoffice/   placeholder, no code yet
docs/           cross-cutting documentation (this file)
infra/          placeholder
shared/         placeholder
compose.yml       full local stack: Postgres 18 + api + web
compose.dev.yml   dev override: hot-reloading api
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

## Branches

This is a solo-developer project, and the branch model is deliberately small:

| Branch | Role |
|---|---|
| `main` | source of truth, carries the monorepo layout |
| `dev` | integration branch |

**Two long-lived branches remotely, and only `main` locally.** Feature branches
are created from `main`, pushed, merged through a PR, then deleted on both
sides. Nothing else is kept around.

A third branch, `stage`, was deleted on 2026-08-09. It never left the
pre-migration layout, so it could not run CI, and a solo workflow does not need a
third environment. Its three commits unique to `main` were merge commits with an
empty diff against their merge base — no code was lost.

Deleting it took two steps, and the second is easy to miss: removing
`refs/heads/stage` from the `protected-branches` ruleset was not enough, because
a **legacy branch-protection object** still carried `allow_deletions: false`.
The push then failed with `protected branch hook declined` instead of
`repository rule violations` — the wording is the only clue that a legacy rule,
not a ruleset, is refusing. `DELETE /repos/…/branches/stage/protection` cleared
it. `main` and `dev` may still carry similar remnants.

### The lesson from `dev`

`dev` stayed on the **pre-migration** layout long after the migration: its head
was `7899a12`, the very commit used as the `apps/api` subtree source, so it still
had `pom.xml` and `src/` at the root. Any PR targeting it showed the whole
monorepo restructuring (~342 files) instead of the actual change, and the CI jobs
could not run at all on a tree with no `apps/api`.

It was realigned by merging `main` into `dev` through PR #18 — a fast-forward,
since `dev` was a strict ancestor of `main` — rather than by force-pushing, which
`protected-branches` forbids. `dev` now carries the monorepo layout.

`required-ci` now targets `dev` as well, so both checks gate it.

Watch for this whenever a long-lived branch predates the migration: check
`git merge-base --is-ancestor origin/<branch> origin/main` before opening a PR
against it.

## CI and branch protection

A single workflow, `.github/workflows/ci.yml`, runs two jobs — the published
check names are what branch protection matches:

- `api / build-and-test`
- `web / build-and-test`

Protection is enforced through **rulesets**, not legacy branch protection rules:

- `protected-branches` → `main`, `dev`: no deletion, no force push, signed
  commits, PR with 1 approval
- `required-ci` → `main`, `dev`: both checks above must pass, branch must be up
  to date

Both bypass lists are **empty**, so the rules apply to the repository owner too.
One consequence to plan for on a solo project: with one approval required and no
bypass, you cannot merge your own PR — nobody is there to approve it. Pick one
of setting `required_approving_review_count` to `0` and letting CI be the gate,
adding a temporary bypass, or merging with `gh pr merge --admin`.

When renaming a job, remember the check name comes from the job's `name:` field,
not its id — rename it in the rulesets at the same time or PRs block on a check
nobody produces.

Rulesets have to be edited **by hand in the GitHub UI**. A classic OAuth token
with the `repo` scope can read them but not write: `PATCH /repos/…/rulesets/{id}`
answers `404`, which is GitHub's way of reporting a missing Administration
permission rather than a missing resource.

Two habits worth keeping when editing a target list: enter **one pattern per
`Include by pattern` dialog**, without quotes or commas — pasting
`"main", "dev"` creates a single literal pattern that matches no branch and
silently protects nothing — and pick check names from the autocomplete rather
than typing them, since a stray space around the `/` produces a required check
nobody ever reports.
