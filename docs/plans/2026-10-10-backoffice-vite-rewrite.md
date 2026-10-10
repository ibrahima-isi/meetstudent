# Plan — rewrite the backoffice in vanilla TypeScript + Vite

Written 2026-10-10 against `dev` @ `75294df` (= `main`). Written for a **fresh session** that has none of the
context below: read this file, root `CLAUDE.md` (working agreement, project state, API contract quirks) and
`apps/backoffice/README.md`, then start at "How to run the session".

## 1. Goal and non-goals

**Goal.** Replace the Angular 20 backoffice (`apps/backoffice`) with a framework-free **vanilla TypeScript + Vite**
single-page app that does exactly what the current one does, with the same UI language (French), the same API
contract, the same runtime configuration and the same deployment shape (static files behind nginx).

**Non-goals.** No new features, no new screens, no API changes, no visual redesign, no i18n library (inline French
strings), no SSR. Anything the API cannot do today (see root `CLAUDE.md` → "API contract quirks") stays undone.

**Why this is a rewrite, not a fix.** The Angular backoffice works and passed two live smoke tests; the rewrite is a
product decision (smaller bundle, no framework upgrade treadmill, full control of the DOM). That means **the Angular
app stays live and untouched until the new one reaches parity** — the project is going live with the Angular
version, and nothing in this plan may put that at risk.

## 2. Starting state (what already exists and must be matched)

Angular app, `apps/backoffice/src/app`: 42 source files (~3.9k lines of TypeScript, templates are inline) and 31 spec
files (~3.9k lines, **261 Karma specs**) that are the executable specification of the behaviour (read them before
porting a page).

| Area | Angular files (spec next to each) | Behaviour to port |
|---|---|---|
| Config | `runtime-config.ts`, `services/api-config.ts` | `GET config.json` (no-store) at bootstrap → `{apiUrl?, serverUrl?}`; failure/invalid → defaults. `SERVER_URL` = `apiUrl` minus `/api/v1` (`''` = same origin) unless `serverUrl` given. Default `apiUrl` today is `http://localhost:8080/api/v1`; production sets it via `BO_API_URL` (nginx entrypoint writes `config.json`). |
| Auth | `services/auth.service.ts`, `token.service.ts`, `guards/admin.guard.ts`, `interceptors/jwt.interceptor.ts`, `refresh.interceptor.ts`, `features/auth/login-page` | `POST /auth {username,password}` → `{accessToken,refreshToken}`; then `GET /users/email/{email}`; only `role.name === 'ROLE_ADMIN'` stays (otherwise tokens cleared + "Accès réservé aux administrateurs."). Tokens/user in `localStorage` keys `auth_token`, `auth_refresh_token`, `auth_user` (storage access always try/catch). Bearer on every API call. On 401 (not on `/auth`, `/auth/refresh`): one shared `POST /auth/refresh`, retry the original request once, logout on failure. Guard: anonymous → `/login?returnUrl=…`; signed-in non-admin → logout + `/login?reason=forbidden`. `returnUrl` starting with `//` is ignored. |
| Shell | `features/shell` | Sidebar nav (labels: Modération, Écoles, Filières et cours, Tags et accréditations; paths `/moderation`, `/schools`, `/programs`, `/tags`) + logout. **Below `md` the sidebar is an off-canvas drawer** with backdrop, hamburger (`aria-controls="main-sidebar"`, `aria-expanded`), closes on navigation / Escape (focus back to the toggle) / backdrop click, focuses its first link when opened. |
| Moderation | `features/moderation`, `services/media.service.ts` | Tabs PENDING (default) / VERIFIED / REJECTED, 20 per page, `GET /media?status=…&page=&size=`; view a private file = `GET /media/{id}` as **blob with the auth header** → object URL in a new tab, revoked after 60 s (popup blocked → revoke + alert); approve / reject (`PATCH /media/{id}/verification`, reason **required** on reject, trimmed, ≤500) with inline confirmation, optimistic removal + rollback + alert on failure. Shows filename, category, content type, size, status, rejection reason. |
| Schools | `features/schools`, `services/school.service.ts`, `tag.service.ts` | List 10/page sorted by name, name search (`/schools/name/{term}`), create/edit form (`code` ≤5, `name` required ≤50, `address.{location,city,country}` ≤255, tags as checkboxes, logo + cover upload), inline delete confirmation (steps back a page if the last item of a later page is deleted), `requestSeq` stale-response guard, server `{field: message}` errors mapped onto controls. Images uploaded **on save** (`POST /media` multipart `category=SCHOOL_LOGO|SCHOOL_COVER` + `file`), client-validated (non-empty, ≤10 MB, JPEG/PNG/WebP with matching MIME), uploaded ids kept if the school save then fails. |
| Programs & courses | `features/programs`, `program.service.ts`, `course.service.ts`, `accreditation.service.ts`, `shared/components/image-field`, `shared/utils/form-errors.ts` | Programs: paginated, search by name, form (`code` ≤5, `name` required ≤50, `duration` positive integer years, school select — first 200 schools, photo `PROGRAM_PHOTO`), delete confirmation states the course count. Courses sub-view from the courses embedded in each program (reload the programs page after every change): `code`, `name`, photo `COURSE_PHOTO`. Accreditation links on saved programs only: link/unlink with start and end years (query params); to change the years, unlink + link. |
| Tags & accreditations | `features/tags` | Two tabs. Tags: list (plain array), create, delete — **no edit**; duplicate names checked case-insensitively before sending. Accreditations: paginated 10/page sorted by name, create, inline edit (PUT), delete (name ≤50 required, code ≤5, description ≤255). Delete/duplicate failures are 500 → friendly "probablement encore utilisé" / "existe peut-être déjà" messages (409 on delete gets the same). |
| Shared | `shared/components/alert`, `shared/utils/http-errors.ts` | Error alert; `fieldErrorsFrom(err)` (400 body `{field: message}`) and `messageFrom(err)` (French messages for 0/401/403/404/413/415/5xx). |
| Routing | `app.routes.ts` | `/login`, `/` → `/moderation`, `/moderation`, `/schools`, `/programs`, `/tags`, `**` → 404 page. All but login/404 behind the admin guard inside the shell. |
| Build/deploy | `Dockerfile`, `docker/nginx.conf`, `docker/40-write-config.sh`, `.github/workflows/ci.yml` (job `backoffice / build-and-test`), `compose.prod.yml` (`backoffice.build.args.DIST_DIR`), `docs/DEPLOY.md` | nginx-unprivileged on 8080, SPA fallback to `index.html`, `config.json` written at container start from `BO_API_URL`, cache headers. Angular output is `dist/backoffice/browser`. |

Visual parity reference: the Tailwind v4 classes in the Angular templates (light mode only; the backoffice has no
dark mode). Reuse the same utility classes so screens look the same.

## 3. Decisions already taken, and defaults for the open ones

**Taken (do not re-litigate):** vanilla TypeScript (strict), Vite, no UI framework at runtime, French inline strings,
same `config.json` runtime config, same nginx image shape, same API, strangler migration (section 5).

**Defaults to confirm in the first ten minutes (change only with a reason):**
- **Styling:** Tailwind v4 through `@tailwindcss/vite` (zero runtime), same classes as today. Hand-written CSS only if the
  owner insists.
- **Tests:** Vitest + jsdom (or happy-dom) for unit/component tests, written **first** (TDD, repo rule); Playwright smoke
  e2e kept **outside** the production bundle (devDependency, `e2e/` folder, not run in CI at first).
- **Routing:** History API router (`/moderation`, `/schools`…) — nginx already falls back to `index.html`. Hash routing is
  the fallback if a deployment cannot rewrite.
- **Lint/format:** `tsc --noEmit` strict + Prettier (printWidth 100, single quotes as today); ESLint optional.

## 4. Architecture of the new app

```
apps/backoffice-next/            (becomes apps/backoffice at the swap)
  index.html  vite.config.ts  tsconfig.json  package.json  package-lock.json
  public/favicon.ico            (+ public/config.json: dev only, git-ignored, never committed)
  src/
    main.ts                     bootstrap: load runtime config → create store/services → router → mount
    core/
      dom.ts                    h(tag, props, ...children) builder + text/attr helpers; NO innerHTML with data
      signal.ts                 signal / computed / effect (≈50 lines), plus a tiny disposable scope
      router.ts                 History API router: routes table, guards, lazy `import()`, returnUrl, 404
      http.ts                   fetch wrapper: base URL, bearer, JSON, ApiError, blob(), multipart, AbortSignal
      auth-interceptor.ts       single-flight refresh on 401, retry once, logout + redirect on failure
      config.ts                 runtime config + API_URL / SERVER_URL
      token-store.ts            localStorage-backed signals, try/catch everywhere
    services/                   auth, media, school, program, course, tag, accreditation (pure functions over http)
    models/                     entities, school, program, course, accreditation (types only)
    ui/                         alert, states (loading/empty/error+retry), pagination, confirm, image-field,
                                tabs, form helpers (field errors, validators), drawer
    pages/                      login, shell, moderation, schools, programs, tags, not-found (+ their forms)
    utils/                      http-errors (French messages), validate-image, format (size/date)
  e2e/                          Playwright smoke (optional, not shipped)
  docker/ + Dockerfile          copied from the Angular app (only DIST_DIR changes)
```

**Patterns that replace Angular features (decide once, apply everywhere):**
- **State → view:** pages are functions `(ctx) => { el, dispose }`. State lives in signals; `effect()` updates the DOM
  nodes that depend on it (fine-grained, no virtual DOM). Every page returns a `dispose()` that stops effects, aborts
  in-flight requests and revokes object URLs.
- **Safe DOM:** build elements with `h()` and `textContent`; **never** `innerHTML`/template-string HTML with API data
  (school names, rejection reasons, filenames are user-controlled). Lint rule or a grep test enforces it.
- **Stale responses:** one `AbortController` per list request; starting a new request aborts the previous one
  (replaces `switchMap`/`requestSeq`). Debounce search 300 ms.
- **Forms:** plain `<form>` + a small `field()` helper (value signal, validators, touched, server error). Server
  `{field: message}` errors are written onto the matching field.
- **Accessibility:** keep what exists — labelled inputs, `role="alert"` messages, `aria-expanded/controls` on the drawer,
  focus management, Escape handling; do not regress.
- **Security/CSP:** no inline scripts or `eval` so Traefik can later add a strict CSP; tokens stay in `localStorage`
  exactly as today (changing that is out of scope).

## 5. Migration strategy (strangler, zero risk to launch)

1. Build the new app in **`apps/backoffice-next/`** next to the Angular one. CI gets a new job
   `backoffice-next / build-and-test` (not a required check). The Angular job, Dockerfile, compose and DEPLOY.md are
   not touched until the swap.
2. Reach the parity checklist (section 7) page by page; each page is its own branch/PR into `dev` (squash).
3. **Swap PR** (one reviewable change): delete the Angular sources, `git mv apps/backoffice-next apps/backoffice`, set
   `DIST_DIR=dist` in `apps/backoffice/Dockerfile` and `compose.prod.yml`, give the CI job back its required name
   `backoffice / build-and-test` (the ruleset's required checks are only `api / build-and-test` and `web / build-and-test`, and the branch must be up to date — read `docs/MONOREPO.md`; the backoffice job is informational today),
   update `README.md`, `docs/DEPLOY.md`, `docs/MONOREPO.md`, root `CLAUDE.md`.
4. Re-run the full live smoke test (section 8) on the swapped `dev`; only then promote `dev` → `main` by fast-forward.
5. **Rollback** = revert the swap PR (the Angular app is in git history); the image shape never changed, so the
   deployment needs no change either way.

## 6. Work breakdown (one branch + PR each, TDD, squash-merge, `dev` first)

Branch names follow `<type>/<short-kebab>` (root `CLAUDE.md`). Sizes are rough.

| # | Branch | Deliverable and acceptance | Size | Depends on |
|---|---|---|---|---|
| T0 | `feat/bo-next-scaffold` | `apps/backoffice-next` with Vite, strict TS, Vitest, Tailwind v4, Prettier, `npm run {dev,build,test,typecheck}`, CI job, empty shell renders. `npm ci && npm run build && npm test` green in CI. | S | — |
| T1 | `feat/bo-next-core` | `dom`, `signal`, `router`, `http`, `auth-interceptor`, `config`, `token-store` with unit tests ported from `runtime-config.spec`, `api-config.spec`, `token.service.spec`, `refresh.interceptor.spec` (burst of 401s → **one** refresh, retry once, logout on failure, auth endpoints skipped). | M | T0 |
| T2 | `feat/bo-next-shell-login` | Login page (admin only, French errors, `returnUrl` rules), admin guard, shell with responsive drawer, 404. Ports `login-page`, `admin.guard`, `shell`, `app.routes` specs. | M | T1 |
| T3 | `feat/bo-next-ui-kit` | alert, states, pagination, inline confirm, tabs, image-field + `validate-image`, form helpers, `http-errors`. Ports `alert`, `http-errors`, `form-errors`, `media.service` validation specs. | M | T1 |
| T4 | `feat/bo-next-moderation` | Moderation page complete (section 2 row). | M | T2, T3 |
| T5 | `feat/bo-next-tags-accreditations` | Tags + accreditations tabs. | M | T2, T3 |
| T6 | `feat/bo-next-schools` | Schools list/form/media upload. | L | T2, T3 |
| T7 | `feat/bo-next-programs-courses` | Programs, courses sub-view, accreditation links. | L | T3, T5, T6 (school select, accreditation service) |
| T8 | `test/bo-next-e2e-parity` | Playwright smoke against a stubbed API and against the real stack; 320/390/1280 px no horizontal overflow on every page; keyboard path through the drawer; bundle size recorded (budget: smaller than the Angular `dist` — measure it first). | M | T4–T7 |
| T9 | `refactor/bo-swap-to-vite` | The swap PR (section 5.3). Includes updating the memory files. | M | T8 |
| T10 | owner action | Delete merged remote branches (agent sessions get HTTP 403 on remote deletion). | S | T9 |

**Parallelism.** After T1, run T2 and T3 in parallel; after both, T4–T6 in parallel (separate worktrees:
`Agent` with `isolation: "worktree"`, each told its branch name, the TDD rule and the report format). T7 waits for the
shared services. Shared files (`ui/`, `core/`) will conflict: each task adds new files and only *appends* exports;
merge `dev` into the branch before opening the PR (the ruleset requires an up-to-date branch).

## 7. Parity checklist (tick every line in T8 and again in T9)

- [ ] Login: wrong password message; non-admin refused with message and tokens cleared; CORS from the BO origin works;
  `returnUrl` honoured except `//…`; logout clears everything.
- [ ] Session: expired access token refreshed silently (burst → one refresh); failed refresh → login with `returnUrl`.
- [ ] Moderation: three tabs, pagination, blob viewer with auth and URL revocation, approve, reject needs a reason,
  rollback on failure, empty/error/loading states.
- [ ] Schools: search, pagination, create/edit (all limits), tags, logo + cover (validation, upload on save, preview,
  served from `/uploads/public` via `SERVER_URL`), delete with confirmation, server field errors.
- [ ] Programs: search, pagination, create/edit, school select, photo, delete states the course count; courses CRUD;
  accreditation link/unlink with years.
- [ ] Tags: create (duplicate check), delete message; accreditations: create, inline edit, delete message.
- [ ] Every list: loading, empty, error with retry, no stale response overwriting a newer one.
- [ ] No horizontal page overflow at 320 / 390 / 1280 px; drawer opens/closes (button, Escape, backdrop, navigation).
- [ ] No `innerHTML` with API data anywhere (grep guard in tests).
- [ ] `config.json` override works without rebuilding; image runs under nginx-unprivileged with SPA fallback.
- [ ] Nothing in the Angular app's behaviour list above is missing (cross-check against its 261 specs).

## 8. Verification and promotion

- Per PR: `npm run typecheck`, `npm test`, `npm run build`, screenshots (Playwright, headless Chromium from
  `/opt/pw-browsers`, API stubbed with `page.route()`, admin session faked in `localStorage`), and the CI job.
- Before the swap is promoted: the **native full-stack smoke test** that ended in GO on 2026-10-09 — throwaway Postgres
  18 in Docker, API jar under the `prod` profile (`mvn -DskipTests package`, system `mvn`), web SSR under node, the new
  backoffice built and served statically with a `config.json`, a Traefik replica of `compose.prod.yml` in front, no
  shims. Docker image builds of the api/web cannot run inside the sandbox (the egress proxy blocks Maven/npm in
  containers); the backoffice image *can* be built if its build stage only needs npm from the host cache — try it, and
  state clearly if it could not be exercised.
- Promotion: `git push origin dev:main` (fast-forward, needs the ruleset bypass), then re-check the protection.

## 9. Risks

| Risk | Mitigation |
|---|---|
| Rewriting ~3.9k lines of UI (plus ~3.9k lines of specs to port) to reach the same screens | Strangler migration; the 261 Angular specs are the spec; nothing ships until parity; the Angular app stays the fallback. |
| XSS through hand-built DOM | `h()` + `textContent` only; grep test for `innerHTML`; filenames, names and rejection reasons are treated as hostile. |
| Memory/listener leaks without a framework lifecycle | Every page returns `dispose()`; tests assert effects stop and requests abort on navigation. |
| Subtle auth bugs (refresh races) | Port `refresh.interceptor.spec` first (T1), including the concurrent-401 case. |
| CI job names | Keep `backoffice / build-and-test` for the Angular app until the swap, then give the new app that name; the required checks are `api` and `web` only (see `docs/MONOREPO.md`). |
| Blob/object URL leaks in moderation | Revoke after 60 s and on dispose; unit-tested. |
| Scope creep ("while we're here") | Non-goals in section 1; new ideas go to a list at the end of the swap PR, not into code. |

## 10. How to run the session

1. Read root `CLAUDE.md` (working agreement, project state, API quirks), this plan, `apps/backoffice/README.md`, and skim
   the Angular specs of the page you are porting.
2. Confirm the three open defaults in section 3 with the owner (one message), then start at T0.
3. Per task: branch from an up-to-date `dev`, failing tests first (watch them fail), minimal code, `typecheck`/`test`/
   `build`, screenshots, commit with Conventional Commits and the attribution trailers, push, open a PR to `dev`,
   subscribe to it, update the branch from `dev` if the ruleset says it is behind, **squash-merge when CI is green**.
4. Use subagents for T2/T3 and T4–T6 as described in section 6; give each its own worktree, branch name, TDD rule and a
   report format (files, test counts, failing-test evidence, build result, what was verified in a browser, what was not
   done). Remove `.claude/worktrees/*` when they finish.
5. Never push to `main`/`dev` directly except the owner-approved fast-forward promotion; never touch the Angular app
   before T9; ask before editing more than three files outside the new app (the working agreement).
