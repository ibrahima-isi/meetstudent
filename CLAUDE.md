# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Working agreement (non-negotiable)

**Branching**
- Never commit or work directly on local `main`. Never push to remote `main` or `dev`.
- Every task starts with a new branch named `<type>/<short-kebab-description>`, where `<type>` is a Conventional Commits type: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `ci` (e.g. `feat/media-upload-retry`). Commit messages use the same types.

**Scope**
- Do not touch code that already works without explicit approval. Do not modify any file that is not strictly required by the assigned task.
- Ask permission before editing more than 3 files, and say which files and why.

**Implementation**
- Use TDD: write the failing test first, watch it fail, then write the minimum code to pass it, then refactor.
- Review the completed work before reporting it done (re-read the diff, run the relevant tests, state the actual results).

**After merging to local `main`**
- Bring the full stack up locally with Docker and run live tests against it before considering the work finished.

## Repository layout

MeetStudent is a monorepo assembled with `git subtree`. Each app keeps its own build, dependencies, and agent instructions — there is no root package manager or build orchestration, so always work from inside the relevant app directory.

- `apps/api/` — Spring Boot 3 / Java 21 REST API (Maven). **Read `apps/api/CLAUDE.md` before touching backend code** — it documents the layered architecture, media/storage split, Flyway rules, and test conventions in detail.
- `apps/web/` — Angular 20 SSR frontend (npm). **Read `apps/web/.claude/CLAUDE.md`** for the mandatory Angular/TypeScript style rules (signals, `inject()`, native control flow, no `ngClass`/`ngStyle`, etc.).
- `apps/backoffice/` — placeholder, empty.
- `docs/`, `infra/`, `shared/` — empty placeholders reserved for future cross-app content.

Because the apps arrived via subtree, keep changes scoped to a single `apps/<app>` subtree per commit where practical.

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

## How the two apps fit together

- The API is versioned under `/api/v1/...`; Swagger UI at `http://localhost:8080/swagger-ui.html`.
- The frontend targets it via `src/environments/environment.ts`, which deliberately holds **two** URLs: `apiUrl` (`.../api/v1`) for REST calls and `serverUrl` (server root) for static media. `Media.publicUrl` is relative to the server root, *not* to `/api/v1` — resolving it against `apiUrl` produces broken images.
- Auth is a dual-token system (short-lived access token + DB-backed rotating refresh token). On the client, `TokenService` holds `token`/`refreshToken`/`user` as signals backed by `localStorage` (guarded for SSR, where `localStorage` is undefined), and `jwtInterceptor` attaches the bearer token.
- Personal documents (diplomas, certificates, bulletins, videos) are **private** media: they cannot be loaded with a plain `<img src>` and must go through `GET /api/v1/media/{id}` with the auth header. Only public categories (school logo/cover, user photo, course/program photo) are servable statically.
- `apps/web/docs/backend-api-integration.md` is the living handoff describing in-flight API contract changes and their frontend impact; check it before assuming a DTO shape.

## Frontend structure notes

- `src/app/features/<area>/<page>/` holds page components grouped by audience (`auth`, `public`, `student`); `src/app/services/` holds one service per backend resource; `src/app/shared/components/` holds reusable UI.
- TS path aliases are configured: `@services/*`, `@models/*`, `@shared/*`, `@data/*`. Use them instead of deep relative imports.
- The app is **zoneless** (`provideZonelessChangeDetection`) with client hydration and event replay — state must flow through signals; code relying on Zone.js change detection will not update the view.
- `apps/web/meetstudent/` is a separate legacy React/Vite prototype (the design source for the Angular pages). Only modify it when a task explicitly targets it.
- Angular styling is Tailwind v4 via `@tailwindcss/postcss` (`.postcssrc.json`), no `tailwind.config` file.

## Agent instruction files

`apps/api/AGENTS.md` is a symlink to `apps/api/CLAUDE.md`. In `apps/web`, `.claude/CLAUDE.md`, `.gemini/GEMINI.md`, and `.github/copilot-instructions.md` carry near-identical Angular guidance — when updating those rules, update all three so the tools stay in sync.
