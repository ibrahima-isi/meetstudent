# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Working agreement (non-negotiable)

**Branching**
- Never commit or work directly on local `main` or `dev`. Never push directly to remote `main` or `dev`.
- Solo-developer project: exactly two long-lived branches remotely (`main`, `dev`). `dev` is the integration branch; `main` receives only release/promotion PRs from `dev`. Delete short-lived working branches locally and remotely once their PRs are merged.
- Every task starts from an up-to-date `dev` and uses a new working branch named `<type>/<short-kebab-description>`, where `<type>` is a Conventional Commits type: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `ci` (e.g. `feat/media-upload-retry`). Commit messages use the same types.
- **Every task ends the same way: commit, push, open a PR from the working branch to `dev`.** Do not open feature, fix, docs, chore, refactor, test, perf or CI PRs directly against `main`; `main` is updated only by a separate `dev` → `main` promotion PR after `dev` is green. Since `required_approving_review_count` is `0` (see `docs/MONOREPO.md`), the PR is yours to merge once both checks pass.
- **"Merge" means squash merge.** Working-branch PRs into `dev` are always merged with *Squash and merge* — never a merge commit, never rebase-and-merge.
- **`main` must stay identical to `dev`.** A PR merge commit on `main` would be missing from `dev`, and syncing it back creates yet another one, forever. So never merge `main` back into `dev`; promote by fast-forwarding `main` to `dev` (`git push origin dev:main`, which needs the ruleset bypass), and never with a merge commit, squash or rebase.
- **A PR must be up to date with `dev` to merge** (the ruleset requires it): if `dev` moved, update the branch from `dev` (a merge commit on the working branch is fine, it is squashed away), wait for CI again, then squash. Expect to repeat this when several PRs are open at once.
- Remote branch deletion is **blocked from agent sessions** (HTTP 403 on `git push --delete`; there is no delete-branch tool). Delete local branches yourself, and leave the remote ones to the owner (GitHub → Branches, or enable "Automatically delete head branches").
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
- Review the completed work before reporting it done (re-read the diff, run the relevant tests, state the actual results).
- Use Subagents: Haikou for read only purpose and small simple coding tasks, sonnet for complex and planned coding tasks and standard review and opus for planning, brainstorming, heavy complexe taxes and code and security review

**After promoting `dev` to `main`**
- Bring the full stack up locally with Docker and run live tests against `main` before considering the promotion finished: `docker compose up --build`, then exercise the API and the front (see Commands below).

**Docker resources**
- **Always tear the stack down when the tests are done: `docker compose down`.** This applies to every stack you start, not just the post-merge check, and it is not optional — containers, the Postgres volume and the published ports (4200, 8080, 5432) otherwise stay held between tasks and collide with the next run.
- Prefer `docker compose up -d` over a foreground run when you only need the stack to answer requests, so the teardown is never forgotten because a terminal is blocked.

## Repository layout

MeetStudent is a monorepo assembled with `git subtree`. Each app keeps its own build, dependencies, and agent instructions — there is no root package manager or build orchestration, so always work from inside the relevant app directory.

- `apps/api/` — Spring Boot 3 / Java 21 REST API (Maven). **Read `apps/api/CLAUDE.md` before touching backend code** — it documents the layered architecture, media/storage split, Flyway rules, and test conventions in detail.
- `apps/web/` — Angular 20 SSR frontend (npm). **Read `apps/web/.claude/CLAUDE.md`** for the mandatory Angular/TypeScript style rules (signals, `inject()`, native control flow, no `ngClass`/`ngStyle`, etc.).
- `apps/backoffice/` — ADMIN-only back office (Angular 20 SPA, no SSR, UI in French), **live and feature-complete for launch**: login (non-admins refused), moderation of documents, schools, programs/courses (+ accreditation links), tags and accreditations, expert approval (Experts), contact messages (Messages), site settings and forum moderation (Forum: reports queue, hide, lock, delete). `apps/web` serves STUDENT and EXPERT. There is no manager role — `V2__data.sql` seeds only `ROLE_ADMIN`, `ROLE_EXPERT`, `ROLE_STUDENT`. **A rewrite in vanilla TypeScript + Vite is planned: read `docs/plans/2026-10-10-backoffice-vite-rewrite.md` before touching or replacing it.** Read `apps/backoffice/README.md` for its commands and conventions.
- `docs/MONOREPO.md` — subtree provenance, branch topology, CI and ruleset conventions. **Read it before touching branches, CI job names or subtree history.**
- `infra/`, `shared/` — empty placeholders reserved for future cross-app content.
- `compose.yml` / `compose.dev.yml` — full local stack, and the hot-reload override for the API.

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
- `apps/web/meetstudent/` is a separate legacy React/Vite prototype (the design source for the Angular pages). Only modify it when a task explicitly targets it.
- Angular styling is Tailwind v4 via `@tailwindcss/postcss` (`.postcssrc.json`), no `tailwind.config` file.

## Project state (2026-10-16) and lessons

**Shipped on `dev` = `main`** (commit `284ff82` at the time of writing; since 2026-10-10 it gained signup that signs the user in, student/teacher registration, About/Contact pages and site settings edited from the back office, email change in two steps, session revocation on password change/reset/email confirmation plus 7-day inactivity expiry, user deletion with cleanup, one rating per user per target, proper 4xx/409/406 statuses): API hardened for production, web (landing, auth, home with API search/filters/paging, school detail with ratings and wishlist, profile with real save, documents with progress/validation/moderation badge, dark/light, fr/en), backoffice (see layout above), production kit (`compose.prod.yml` with Traefik/TLS/rate limits, backoffice image, `scripts/backup.sh`/`restore.sh`, `docs/DEPLOY.md` runbook, `apps/api/docs/production.md` env list). Two full live smoke tests (real Postgres + prod-profile API + SSR web + backoffice + Traefik replica, Playwright, **no shims**) ended in GO, and a third on 2026-10-16 (real Postgres V1-V24, dev-profile API, SSR web, built-in browser) covered registration roles, ratings, password change, account deletion and the 4xx statuses. Not covered by any test: real TLS/Let's Encrypt, the api/web Docker image builds (the sandbox proxy blocks Maven/npm inside containers — run natively instead), real phones.

**Known gaps (decisions, not bugs):** email verification is optional and available in the API (unverified accounts sign in normally; the mail is only sent when `MAIL_ENABLED=true`, and the web has the `/:lang/verify-email` page plus a banner for unverified accounts), password reset is available in the API and the web (`/api/v1/auth/password-reset/request|confirm`, pages `/:lang/forgot-password` and `/:lang/reset-password`; registration signs the user in right away); without SMTP configured no mail leaves, so production needs `MAIL_ENABLED=true` and the `SPRING_MAIL_*` variables; a password change or reset revokes the sessions (access tokens carry a password version) and changing the password or deleting your own account asks for the current password; registration lets anyone self-declare as `ROLE_EXPERT` (teacher) but the account starts `PENDING` (`V29`, `users.expert_status`, existing experts `APPROVED`): only an `APPROVED` expert rates programs and courses (once per user per target, `V24` deletes older duplicates, so back up first), enforced by `@RequiresApprovedExpert` / `ExpertAccess`, which reads the status from the database on every request so a decision applies without signing in again; the back office has an Experts approval queue (`/api/v1/admin/experts`, see `apps/api/CLAUDE.md`), the three teacher mails exist (request received at registration, approved, rejected with the escaped reason; see `apps/api/CLAUDE.md`); a rating race that loses answers 409 and a retry then updates in place; schools have no capacity, tuition, level, description or contact data (the API has none), and programs have only the optional fields `description`, `availableSeats`, `annualFee`, `level` (no enrolment tracking; see API contract quirks); the program photo is returned but not rendered in the web; `MediaDTO` has no owner or upload date, so moderation shows filename/category/type/size/status only; the backoffice sidebar Escape handler only fires when focus is inside the menu.

**API contract quirks every client must respect:**
- Single reads answer **200** (they used to answer 302 and broke login); errors are `{field: message}` on 400 validation, and the standard `ErrorResponse` for 401/403/404/405/406/409/413/415. Anonymous access to private or unknown media is 401; a non-admin gets 403.
- `PUT` on schools/programs/courses behaves like a **PATCH** (null = keep): codes, photos and the school of a program can be changed but never cleared; `tags: []` does clear tags. Create requests need `name`.
- Limits: school `code` ≤5, `name` ≤50, address fields ≤255; accreditation name ≤50, code ≤5, description ≤255; tag name ≤255; uploads ≤10 MB, extensions pdf/jpg/jpeg/png/webp/mp4/webm/mov with a matching MIME type.
- Tags have **no update** endpoint and are not paged; accreditations and schools are paged; programs have no school filter and no list-courses-by-program endpoint (courses are embedded in each program); accreditation links to a program need start/end years as query params; deleting a program deletes its courses; deleting a tag/accreditation/role still in use, or a duplicate school/program/course/accreditation code or tag name, answers 409 `ErrorResponse` (also the catch-all for any other constraint violation; too-long or null values answer 400); an unknown `?sort=` property is 400, an unacceptable `Accept` header 406 (JSON body), an unknown tag id in a school body 400 `{"tags": ...}`.
- Course `description` is optional, ≤1000 (V31), without control characters other than \t \r \n; null or absent keeps it on PUT/PATCH, "" or blank clears it (stored null); written by ADMIN only, read publicly; the back office course form always sends the field when editing.
- Program optional fields: `description` ≤1000 without control characters other than \t \r \n, blank clears it; `availableSeats` 0..100000; `annualFee` ≥0, 2 decimals, ≤8 integer digits; `level` one of `BTS_DUT|LICENCE|MASTER|DOCTORAT|AUTRE`; only `description` can be cleared (the others can be replaced, never cleared).
- Program wishlist: `GET|POST|DELETE /api/v1/users/{userId}/wishlist/programs[/{programId}]` answer `List<ProgramDTO>`, STUDENT and owner only.
- Schools are searched by name with `/schools/name/{term}` (a term containing `/` cannot work); the city filter is `/schools/search?city=`.
- Admin account: in production it is created from `ADMIN_EMAIL`/`ADMIN_PASSWORD` at boot (12+ chars); the seeded `admin@meetstudent.com`/`password` row is neutralised.

**Process lessons:** run the whole stack natively (Postgres in Docker, API jar, SSR server, static backoffice, a Traefik replica of `compose.prod.yml`) and drive it with Playwright before promoting — unit tests missed the 302 login blocker, the SSR guard bounce and the role-name mismatch. Subagents work best one task per branch in isolated worktrees (`isolation: worktree`) with explicit TDD and a report format; remove `.claude/worktrees/*` when they finish or the stop hook keeps flagging untracked files.

## Agent instruction files

`apps/api/AGENTS.md` is a symlink to `apps/api/CLAUDE.md`. In `apps/web`, `.claude/CLAUDE.md` is the single frontend agent guide.

## Agent skills

### Issue tracker

New issues and specs use local Markdown under `.scratch/<feature>/`. Before reading or creating tickets, read `docs/agents/issue-tracker.md`; it also defines how to resolve existing GitHub tickets.

### Ticket progress

Completion tracking is **per spec**: one file `docs/completion/<spec-folder>.md` for each `.scratch/<spec-folder>/`, created as soon as the spec's tickets are created, with each ticket's entry saved right after its ticket file and before the next ticket. Once all tickets of the spec are merged and confirmed, leave the file alone and move on; the owner reviews it and deletes it. The legacy `docs/completion.md` is frozen, add nothing new to it. Rules in `docs/agents/issue-tracker.md`; read them when creating, claiming or completing tickets.

### Triage labels

Use the five default triage labels. Before triaging issues, read `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one shared root glossary and root ADR directory. Before exploring domain concepts or decisions, read `docs/agents/domain.md`.

### Communication

Respond in French, simply and very briefly. Use a very short analogy when helpful. Ask questions through interactive inline input tools.
