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

**Scope**
- Do not touch code that already works without explicit approval. Do not modify any file that is not strictly required by the assigned task.
- Ask permission before editing more than 3 files, and say which files and why.

**Implementation**
- Use TDD: write the failing test first, watch it fail, then write the minimum code to pass it, then refactor.
- Review the completed work before reporting it done (re-read the diff, run the relevant tests, state the actual results).

**After promoting `dev` to `main`**
- Bring the full stack up locally with Docker and run live tests against `main` before considering the promotion finished: `docker compose up --build`, then exercise the API and the front (see Commands below).

**Docker resources**
- **Always tear the stack down when the tests are done: `docker compose down`.** This applies to every stack you start, not just the post-merge check, and it is not optional — containers, the Postgres volume and the published ports (4200, 8080, 5432) otherwise stay held between tasks and collide with the next run.
- Prefer `docker compose up -d` over a foreground run when you only need the stack to answer requests, so the teardown is never forgotten because a terminal is blocked.

## Repository layout

MeetStudent is a monorepo assembled with `git subtree`. Each app keeps its own build, dependencies, and agent instructions — there is no root package manager or build orchestration, so always work from inside the relevant app directory.

- `apps/api/` — Spring Boot 3 / Java 21 REST API (Maven). **Read `apps/api/CLAUDE.md` before touching backend code** — it documents the layered architecture, media/storage split, Flyway rules, and test conventions in detail.
- `apps/web/` — Angular 20 SSR frontend (npm). **Read `apps/web/.claude/CLAUDE.md`** for the mandatory Angular/TypeScript style rules (signals, `inject()`, native control flow, no `ngClass`/`ngStyle`, etc.).
- `apps/backoffice/` — ADMIN-only back office (Angular 20 SPA, no SSR, UI in French), **live and feature-complete for launch**: login (non-admins refused), moderation of documents, schools, programs/courses (+ accreditation links), tags and accreditations. `apps/web` serves STUDENT and EXPERT. There is no manager role — `V2__data.sql` seeds only `ROLE_ADMIN`, `ROLE_EXPERT`, `ROLE_STUDENT`. **A rewrite in vanilla TypeScript + Vite is planned: read `docs/plans/2026-10-10-backoffice-vite-rewrite.md` before touching or replacing it.** Read `apps/backoffice/README.md` for its commands and conventions.
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
docker compose up --build        # Postgres 18 + api + web
docker compose up -d api         # api + its Postgres only; naming a service skips the rest

# Hot-reloading API: recompile on the host (IDE or ./mvnw compile) and devtools
# restarts the container in about a second.
docker compose -f compose.yml -f compose.dev.yml up api

docker compose down              # ALWAYS run this once the tests are done
docker compose down -v           # same, plus the Postgres volume — resets the database
```

Front on `http://localhost:4200`, API on `http://localhost:8080/api/v1`, Swagger at `/swagger-ui.html`. Postgres is published on 5432.

The front is deliberately absent from the dev override: `ng serve` on the host has far better HMR than a container, and reaches the API through `apps/web/proxy.conf.json`, which forwards `/api` and `/uploads` to `http://localhost:8080`.

## How the two apps fit together

- The API is versioned under `/api/v1/...`; Swagger UI at `http://localhost:8080/swagger-ui.html`.
- The frontend targets it via `src/environments/environment.ts`. Production is **single-origin**: a reverse proxy sends `/api/*` and `/uploads/public/*` to the API and everything else to the web container, so the browser bundle uses **relative** URLs: `apiUrl` `/api/v1`, `serverUrl` `''`, `mediaBaseUrl` `''`. `Media.publicUrl` is relative to the server root, *not* to `/api/v1`, and `MediaService.resolveUrl` prefixes it with `mediaBaseUrl` (never `apiUrl`). `ng serve` gets the same relative behaviour from `proxy.conf.json`.
- A relative URL has no origin to resolve against during SSR, so the web container needs `API_URL` (e.g. `http://api:8080/api/v1`) and `SERVER_URL` (e.g. `http://api:8080`). `applyServerEnvironment(environment, process.env)` applies them server-side only (`apiUrl`/`serverUrl`; `mediaBaseUrl` is deliberately not overridden, because it ends up in server-rendered `<img src>` that the visitor's browser loads) — **in two places, and both are needed**. The build emits `environment.ts` into two server chunks, one reachable from `src/server.ts` and one reachable from the application, so they are separate objects. The call that makes the *services* see the override is the environment initializer in `src/app/app.config.server.ts`, which shares an object with them; `server.ts` covers its own copy. Keep both if you touch either. This path is live, not theoretical: pages fetch in `ngOnInit` and therefore run during SSR — and when it breaks, it breaks quietly, because the pages still render with `ng-server-context="ssr"` and HTTP 200, just with mock or empty data.
- Auth is a dual-token system (short-lived access token + DB-backed rotating refresh token). On the client, `TokenService` holds `token`/`refreshToken`/`user` as signals backed by `localStorage` (guarded for SSR, where `localStorage` is undefined), and `jwtInterceptor` attaches the bearer token.
- Personal documents (diplomas, certificates, bulletins, videos) are **private** media: they cannot be loaded with a plain `<img src>` and must go through `GET /api/v1/media/{id}` with the auth header. Only public categories (school logo/cover, user photo, course/program photo) are servable statically.
- `apps/web/docs/backend-api-integration.md` is the living handoff describing in-flight API contract changes and their frontend impact; check it before assuming a DTO shape.

## Frontend structure notes

- **The app uses the Angular router, with the locale in the URL.** Every screen lives under `/:lang` (`fr` default, `en`); `localeGuard` validates the segment and `authGuard` protects `home`, `schools/:id` and `profile` (redirect to `/<lang>/login?returnUrl=…`). Guarded routes are rendered **client-side** (`RenderMode.Client` in `app.routes.server.ts`, listed before `**`): the guard cannot see `localStorage` during SSR, so server-rendering them would bounce every reload to the login. A spec fails if a guarded route is missing from the server routes. The landing is the marketing page at `/:lang`; the public school catalogue is `/:lang/schools` (SSR, no guard). Landing, catalogue, login and register stay server-rendered; all four sit inside the public shell (Dock navbar), and login/register use the split-screen auth layout (brand panel on ≥1024px, `.field-input` form controls).
- i18n is Transloco (runtime, `src/app/i18n/fr.json` + `en.json`, **keys in English**, French is the source language, both files must be updated together). Dark/light is `ThemeService` + a `dark` class on `<html>` (Tailwind `@custom-variant dark`), set before first paint by a script in `index.html`; style with the semantic tokens in `styles.css` (`bg-card`, `text-foreground`, `border-border`…) or pair every raw palette class with a `dark:` class — `shared/dark-mode.guard.spec.ts` fails otherwise.
- `src/app/features/<area>/<page>/` holds page components grouped by audience (`auth`, `public`, `student`); `src/app/services/` holds one service per backend resource; `src/app/shared/components/` holds reusable UI.
- TS path aliases are configured: `@services/*`, `@models/*`, `@shared/*`, `@i18n/*`. Use them instead of deep relative imports. There is **no mock data**: a failing API shows the shared `error-state` with a retry, never fake schools.
- Roles reach the client as `ROLE_STUDENT` / `ROLE_EXPERT` / `ROLE_ADMIN` (`models/roles.ts`); the wishlist is by **school**; ratings: students rate schools, experts also programs and courses (the server enforces it).
- The app is **zoneless** (`provideZonelessChangeDetection`) with client hydration and event replay — state must flow through signals; code relying on Zone.js change detection will not update the view.
- `apps/web/meetstudent/` is a separate legacy React/Vite prototype (the design source for the Angular pages). Only modify it when a task explicitly targets it.
- Angular styling is Tailwind v4 via `@tailwindcss/postcss` (`.postcssrc.json`), no `tailwind.config` file.

## Project state (2026-10-10) and lessons

**Shipped on `dev` = `main`** (commit `75294df` at the time of writing): API hardened for production, web (landing, auth, home with API search/filters/paging, school detail with ratings and wishlist, profile with real save, documents with progress/validation/moderation badge, dark/light, fr/en), backoffice (see layout above), production kit (`compose.prod.yml` with Traefik/TLS/rate limits, backoffice image, `scripts/backup.sh`/`restore.sh`, `docs/DEPLOY.md` runbook, `apps/api/docs/production.md` env list). Two full live smoke tests (real Postgres + prod-profile API + SSR web + backoffice + Traefik replica, Playwright, **no shims**) ended in GO. Not covered by any test: real TLS/Let's Encrypt, the api/web Docker image builds (the sandbox proxy blocks Maven/npm inside containers — run natively instead), real phones.

**Known gaps (decisions, not bugs):** no email verification and no password reset (the register flow goes straight to login); schools/programs have no capacity, tuition, level, description or contact data (the API has none); the program photo is returned but not rendered in the web; `MediaDTO` has no owner or upload date, so moderation shows filename/category/type/size/status only; the backoffice sidebar Escape handler only fires when focus is inside the menu.

**API contract quirks every client must respect:**
- Single reads answer **200** (they used to answer 302 and broke login); errors are `{field: message}` on 400 validation, and the standard `ErrorResponse` for 401/403/404/405/413/415. Anonymous access to private or unknown media is 401; a non-admin gets 403.
- `PUT` on schools/programs/courses behaves like a **PATCH** (null = keep): codes, photos and the school of a program can be changed but never cleared; `tags: []` does clear tags. Create requests need `name`.
- Limits: school `code` ≤5, `name` ≤50, address fields ≤255; accreditation name ≤50, code ≤5, description ≤255; tag name ≤255; uploads ≤10 MB, extensions pdf/jpg/jpeg/png/webp/mp4/webm/mov with a matching MIME type.
- Tags have **no update** endpoint and are not paged; accreditations and schools are paged; programs have no school filter and no list-courses-by-program endpoint (courses are embedded in each program); accreditation links to a program need start/end years as query params; deleting a program deletes its courses; deleting a tag/accreditation still in use, or a duplicate code/name, comes back as a generic 500.
- Schools are searched by name with `/schools/name/{term}` (a term containing `/` cannot work); the city filter is `/schools/search?city=`.
- Admin account: in production it is created from `ADMIN_EMAIL`/`ADMIN_PASSWORD` at boot (12+ chars); the seeded `admin@meetstudent.com`/`password` row is neutralised.

**Process lessons:** run the whole stack natively (Postgres in Docker, API jar, SSR server, static backoffice, a Traefik replica of `compose.prod.yml`) and drive it with Playwright before promoting — unit tests missed the 302 login blocker, the SSR guard bounce and the role-name mismatch. Subagents work best one task per branch in isolated worktrees (`isolation: worktree`) with explicit TDD and a report format; remove `.claude/worktrees/*` when they finish or the stop hook keeps flagging untracked files.

## Agent instruction files

`apps/api/AGENTS.md` is a symlink to `apps/api/CLAUDE.md`. In `apps/web`, `.claude/CLAUDE.md` is the single frontend agent guide.
