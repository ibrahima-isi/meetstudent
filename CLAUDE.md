# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Working agreement (non-negotiable)

**Branching**
- Never commit or work directly on local `main` or `dev`. Never push directly to remote `main` or `dev`.
- Solo-developer project: exactly two long-lived branches remotely (`main`, `dev`). `dev` is the integration branch; `main` receives only release/promotion PRs from `dev`. Delete short-lived working branches locally and remotely once their PRs are merged.
- Every task starts from an up-to-date `dev` and uses a new working branch named `<type>/<short-kebab-description>`, where `<type>` is a Conventional Commits type: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `ci` (e.g. `feat/media-upload-retry`). Commit messages use the same types.
- **Every task ends the same way: commit, push, open a PR from the working branch to `dev`.** Do not open feature, fix, docs, chore, refactor, test, perf or CI PRs directly against `main`; `main` is updated only by a separate `dev` → `main` promotion PR after `dev` is green. Since `required_approving_review_count` is `0` (see `docs/MONOREPO.md`), the PR is yours to merge once both required checks pass (`api` and `web` build-and-test; the other jobs, including `images / build`, `deploy / production` and `compose / validate-coolify`, are not required).
- **"Merge" means squash merge.** Working-branch PRs into `dev` are always merged with *Squash and merge* — never a merge commit, never rebase-and-merge.
- **`main` must stay identical to `dev`.** A PR merge commit on `main` would be missing from `dev`, and syncing it back creates yet another one, forever. So never merge `main` back into `dev`; promote by fast-forwarding `main` to `dev` (`git push origin origin/dev:refs/heads/main`, which needs the ruleset bypass; rationale in `docs/MONOREPO.md`), and never with a merge commit, squash or rebase.
- **A PR must be up to date with `dev` to merge** (the ruleset requires it): if `dev` moved, update the branch from `dev` (a merge commit on the working branch is fine, it is squashed away), wait for CI again, then squash. Expect to repeat this when several PRs are open at once.
- **Cleanup is mandatory, no exception.** As soon as a branch's work is in `dev` or `main` (local or remote), delete the local branch, the remote branch and its git worktree (`git worktree remove`, then `git worktree prune`). Check it at the end of every task and at the start of every session. Squash merges hide it from `git branch --merged`: a branch has landed when `git merge-tree --write-tree origin/dev <branch>` gives the tree of `origin/dev`. Never remove a worktree with uncommitted changes or one a running session uses; report it instead.
- Remote branch deletion is **blocked from agent sessions** (HTTP 403 on `git push --delete`; there is no delete-branch tool). Delete local branches and worktrees yourself, and **always list the exact landed remote branches** for the owner to delete (GitHub → Branches, or enable "Automatically delete head branches").
- When a branch is cut from another unmerged branch, open its PR **against that branch**, not `dev` or `main`, or the diff shows the parent's commits too. Retarget or merge onward to `dev` only after the parent branch lands.

**Sessions**
- Every session works in its own git worktree (cut from `origin/dev`), never in the main checkout, which other sessions share.
- **Worktree naming:** a worktree for a ticket is named `{spec-folder}-{ticket-number}`, using the `.scratch/<spec-folder>/issues/<NN>-*.md` identity (e.g. `forum-01`, `expert-approval-02`).
- **Interactive questioning is mandatory, always.** Every question to the user goes through the `AskUserQuestion` tool (recommended option first, at most 4 questions per call, split into several calls if needed). Never put questions in prose, never number them in a message, and never follow a skill's instruction to ask in prose (e.g. `grilling` rounds) — the tool wins. Prose is for reporting only.

**Scope**
- Do not touch code that already works without explicit approval. Do not modify any file that is not strictly required by the assigned task.
- Ask permission before editing more than 5 files, and say which files and why.

**Implementation**
- Use TDD: write the failing test first, watch it fail, then write the minimum code to pass it, then refactor.
- List findings to the owner before modifying anything: he reviews and picks the scope. Report with evidence (exact commands, real counts and status codes), and say what was not verified.
- Review the completed work before reporting it done (re-read the diff, run the relevant tests, state the actual results).
- Use Subagents: Haikou for read only purpose and small simple coding tasks, sonnet for complex and planned coding tasks and standard review and opus for planning, brainstorming, heavy complexe taxes and code and security review

**After promoting `dev` to `main`**
- Bring the full stack up locally with Docker and run live tests against `main` before considering the promotion finished: `docker compose up --build`, then exercise the API and the front (see Commands below).

**Docker resources**
- **Always tear the stack down when the tests are done: `docker compose down`.** This applies to every stack you start, not just the post-merge check, and it is not optional — containers, the Postgres volume and the published ports (4200, 8080, 5432) otherwise stay held between tasks and collide with the next run.
- Prefer `docker compose up -d` over a foreground run when you only need the stack to answer requests, so the teardown is never forgotten because a terminal is blocked.

## Repository layout

MeetStudent is a monorepo assembled with `git subtree`. Each app keeps its own build, dependencies, and agent instructions — there is no root package manager or build orchestration, so always work from inside the relevant app directory.

- `apps/api/` — Spring Boot 3 / Java 21 REST API (Maven). **Read `apps/api/CLAUDE.md` before touching backend code** — layered architecture, media/storage split, Flyway rules, test conventions; the API contract quirks are in `apps/api/docs/reference.md`.
- `apps/web/` — Angular 20 SSR frontend (npm). **Read `apps/web/.claude/CLAUDE.md`** for the mandatory Angular/TypeScript style rules (signals, `inject()`, native control flow, no `ngClass`/`ngStyle`, etc.).
- `apps/backoffice/` — ADMIN-only back office (Angular 20 SPA, no SSR, UI in French), **live and feature-complete for launch**: login (non-admins refused), moderation of documents, schools, programs/courses (+ accreditation links), tags and accreditations, expert approval (Experts), contact messages (Messages), site settings and forum moderation (Forum: reports queue, hide, lock, delete). `apps/web` serves STUDENT and EXPERT. There is no manager role — `V2__data.sql` seeds only `ROLE_ADMIN`, `ROLE_EXPERT`, `ROLE_STUDENT`. **A rewrite in vanilla TypeScript + Vite is planned: read `docs/plans/2026-10-10-backoffice-vite-rewrite.md` before touching or replacing it.** Read `apps/backoffice/README.md` for its commands and conventions.
- `docs/MONOREPO.md` — subtree provenance, branch topology, CI and ruleset conventions. **Read it before touching branches, CI job names or subtree history.**
- `infra/` — production under Coolify: `docker-bake.hcl` (CI images), `coolify/proxy/dynamic/` (Traefik middlewares), `backup/` (backup image), `tests/` (their shell tests).
- `compose.yml` / `compose.dev.yml` — full local stack, and the hot-reload override for the API. `compose.prod.yml` and `compose.coolify.yml` are the production stacks; `scripts/backup.sh` / `restore.sh` and the runbook `docs/DEPLOY.md` complete the production kit; the `prod` env list is `apps/api/docs/production.md`.

Because the apps arrived via subtree, keep changes scoped to a single `apps/<app>` subtree per commit where practical. There is **no upstream remote to pull the subtrees from** — the source repositories no longer exist, so never run `git subtree pull`.

## Commands

Backend (`cd apps/api`):

```bash
./mvnw clean verify                 # unit + integration tests
./mvnw test -Dtest=UserServiceTest  # single unit test class (append #method for one test)
./mvnw spring-boot:run              # dev profile, hot reload
docker compose up app-dev           # containerised dev; needs .env with JWT_SECRET_KEY
```

Test-name conventions are enforced by the build: `*Test.java` runs under surefire, `*IntegrationTests.java` under failsafe. Anything else never runs.

Frontend (`cd apps/web`):

```bash
npm install
npm start                # ng serve on http://localhost:4200
npm run build            # SSR build into dist/
npm test                 # Karma + Jasmine
npm test -- --include='**/media.service.spec.ts'   # single spec
npm run serve:ssr:frontend
```

CI runs the web tests headless: `npm test -- --no-watch --browsers=ChromeHeadless`.

Full stack (repo root):

```bash
cp .env.example .env             # JWT_SECRET_KEY + POSTGRES_PASSWORD, both required
docker compose up --build        # Postgres 18 + api + web + backoffice, behind one proxy
docker compose up -d api         # api + its Postgres only; naming a service skips the rest

# Hot-reloading API: recompile on the host (IDE or ./mvnw compile) and devtools
# restarts the container in about a second.
docker compose -f compose.yml -f compose.dev.yml up api

docker compose down              # ALWAYS run this once the tests are done
docker compose down -v           # same, plus the Postgres volume — resets the database
```

Front on `http://localhost:4200`, back office on `http://localhost:4300`, API on `http://localhost:8080/api/v1`, Swagger at `/swagger-ui.html`. Postgres is published on 5432.

The front is deliberately absent from the dev override: `ng serve` on the host has far better HMR than a container, and reaches the API through `apps/web/proxy.conf.json`, which forwards `/api` and `/uploads` to `http://localhost:8080`.

## How the two apps fit together

- The API is versioned under `/api/v1/...`; Swagger UI at `http://localhost:8080/swagger-ui.html`.
- The frontend targets it via `src/environments/environment.ts`. Production is **single-origin**: a reverse proxy sends `/api/*` and `/uploads/public/*` to the API and everything else to the web container, so the browser bundle uses **relative** URLs: `apiUrl` `/api/v1`, `serverUrl` `''`, `mediaBaseUrl` `''`. `Media.publicUrl` is relative to the server root, *not* to `/api/v1`, and `MediaService.resolveUrl` prefixes it with `mediaBaseUrl` (never `apiUrl`). `ng serve` gets the same relative behaviour from `proxy.conf.json`.
- A relative URL has no origin to resolve against during SSR, so the web container needs `API_URL` (e.g. `http://api:8080/api/v1`) and `SERVER_URL` (e.g. `http://api:8080`). `applyServerEnvironment(environment, process.env)` applies them server-side only (`apiUrl`/`serverUrl`; `mediaBaseUrl` is deliberately not overridden, because it ends up in server-rendered `<img src>` that the visitor's browser loads) — **in two places, and both are needed**. The build emits `environment.ts` into two server chunks, one reachable from `src/server.ts` and one reachable from the application, so they are separate objects. The call that makes the *services* see the override is the environment initializer in `src/app/app.config.server.ts`, which shares an object with them; `server.ts` covers its own copy. Keep both if you touch either. This path is live, not theoretical: pages fetch in `ngOnInit` and therefore run during SSR — and when it breaks, it breaks quietly, because the pages still render with `ng-server-context="ssr"` and HTTP 200, just with mock or empty data.
- Auth is a dual-token system (short-lived access token + DB-backed rotating refresh token). On the client, `TokenService` holds `token`/`refreshToken`/`user` as signals backed by `localStorage` (guarded for SSR, where `localStorage` is undefined), and `jwtInterceptor` attaches the bearer token.
- Personal documents (diplomas, certificates, bulletins, videos) are **private** media: they cannot be loaded with a plain `<img src>` and must go through `GET /api/v1/media/{id}` with the auth header. Only public categories (school logo/cover, user photo, course/program photo) are servable statically.
- `apps/web/docs/backend-api-integration.md` is the living handoff describing in-flight API contract changes and their frontend impact; check it before assuming a DTO shape.

## Frontend structure notes

- **The app uses the Angular router, with the locale in the URL.** Every screen lives under `/:lang` (`fr` default, `en`); `localeGuard` validates the segment and `authGuard` protects `home`, `schools/:id` and `profile` (redirect to `/<lang>/login?returnUrl=…`). Guarded routes are rendered **client-side** (`RenderMode.Client` in `app.routes.server.ts`, listed before `**`): the guard cannot see `localStorage` during SSR, so server-rendering them would bounce every reload to the login. A spec fails if a guarded route is missing from the server routes. The landing is the marketing page at `/:lang`; the public school catalogue is `/:lang/schools` (SSR, no guard). The forum is public and server-rendered too: list `/:lang/forum` (with the new-thread panel) and thread `/:lang/forum/:id`; writing is client-side only, for students and approved experts, and the pages show the sign-in invitation or the pending/rejected/read-only reason otherwise. Landing, catalogue, login and register stay server-rendered; all four sit inside the public shell (Dock navbar), and login/register use the split-screen auth layout (brand panel on ≥1024px, `.field-input` form controls).
- i18n is Transloco (runtime, `src/app/i18n/fr.json` + `en.json`, **keys in English**, French is the source language, both files must be updated together). Dark/light is `ThemeService` + a `dark` class on `<html>` (Tailwind `@custom-variant dark`), set before first paint by a script in `index.html`; style with the semantic tokens in `styles.css` (`bg-card`, `text-foreground`, `border-border`…) or pair every raw palette class with a `dark:` class — `shared/dark-mode.guard.spec.ts` fails otherwise.
- `src/app/features/<area>/<page>/` holds page components grouped by audience (`auth`, `public`, `student`); `src/app/services/` holds one service per backend resource; `src/app/shared/components/` holds reusable UI.
- TS path aliases are configured: `@services/*`, `@models/*`, `@shared/*`, `@i18n/*`. Use them instead of deep relative imports. There is **no mock data**: a failing API shows the shared `error-state` with a retry, never fake schools.
- Roles reach the client as `ROLE_STUDENT` / `ROLE_EXPERT` / `ROLE_ADMIN` (`models/roles.ts`); the wishlist is by **school** and by **program** (formation); ratings: students rate schools, experts also programs and courses (the server enforces it).
- The app is **zoneless** (`provideZonelessChangeDetection`) with client hydration and event replay — state must flow through signals; code relying on Zone.js change detection will not update the view.
- Angular styling is Tailwind v4 via `@tailwindcss/postcss` (`.postcssrc.json`), no `tailwind.config` file.

## Known gaps and lessons

**Known gaps (decisions, not bugs):**
- Without SMTP no mail leaves: production needs `MAIL_ENABLED=true` and the `SPRING_MAIL_*` variables. Email verification is optional (unverified accounts sign in normally; the web has `/:lang/verify-email` and a banner), password reset exists in API and web (`/:lang/forgot-password`, `/:lang/reset-password`), and registration signs the user in right away.
- Registration lets anyone self-declare as `ROLE_EXPERT`, but the account starts `PENDING`: only an `APPROVED` expert rates programs and courses. The back office Experts queue decides; the status is read from the database on every request. Details in `apps/api/docs/reference.md`.
- Changing the password or deleting your own account asks for the current password (a password change or reset also revokes the sessions).
- One rating per user per target (`V24` deletes older duplicates, so back up before migrating an existing database).
- Schools have no capacity, tuition, level, description or contact data (the API has none); programs have only the optional `description`, `availableSeats`, `annualFee`, `level`.
- The program photo is returned but not rendered in the web; `MediaDTO` has no owner or upload date, so moderation shows filename/category/type/size/status only; the backoffice sidebar Escape handler only fires when focus is inside the menu.
- Not covered by any test: real TLS/Let's Encrypt, the api/web Docker image builds (the sandbox proxy blocks Maven/npm inside containers: run natively), real phones.

**API contract quirks** (status codes, PATCH-like `PUT`, limits, filters) live in one place: `apps/api/docs/reference.md`. Read it before writing a client or changing a contract.

**Process lessons:** before promoting, run the whole stack natively (Postgres in Docker, API jar, SSR server, static backoffice, a Traefik replica of `compose.prod.yml`) and drive it with Playwright: unit tests missed the 302 login blocker, the SSR guard bounce and the role-name mismatch. Subagents work best one task per branch in isolated worktrees (`isolation: worktree`) with explicit TDD and a report format; remove `.claude/worktrees/*` when they finish or the stop hook keeps flagging untracked files.

## Agent instruction files

`apps/api/AGENTS.md` is a symlink to `apps/api/CLAUDE.md`. In `apps/web`, `.claude/CLAUDE.md` is the single frontend agent guide.

## Agent skills

### Issue tracker

New issues and specs use local Markdown under `.scratch/<feature>/`. Before reading or creating tickets, read `docs/agents/issue-tracker.md`; it also defines how to resolve existing GitHub tickets.

### Ticket progress

Completion tracking is **per spec**: one file `.scratch/<spec-folder>/completion.md`, stored in the spec's own folder next to `spec.md` (several specs in progress, several tracking files), created as soon as the spec's tickets are created, with each ticket's entry saved right after its ticket file and before the next ticket. Once all tickets of the spec are merged and confirmed, leave the file alone and move on; the owner reviews it and deletes it. Rules in `docs/agents/issue-tracker.md`; read them when creating, claiming or completing tickets.

### Triage labels

Use the five default triage labels. Before triaging issues, read `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one shared root glossary and root ADR directory. Before exploring domain concepts or decisions, read `docs/agents/domain.md`.

### Communication

Respond in French, simply and very briefly. Use a very short analogy when helpful. Ask questions through interactive inline input tools.
