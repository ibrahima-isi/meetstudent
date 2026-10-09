# MeetStudent backoffice

Light ADMIN-only back office (Angular 20, plain SPA, no SSR, UI in French). `apps/web` serves students and experts; only `ROLE_ADMIN` accounts can sign in here.

## Commands (run from `apps/backoffice`)

```bash
npm install
npm start       # ng serve on http://localhost:4200
npm test        # Karma + Jasmine (watch)
npm test -- --no-watch --browsers=ChromeHeadless   # what CI runs
npm run build   # static files in dist/backoffice/browser
```

If `ng serve` of `apps/web` is also running, pass another port: `npm start -- --port 4300`.

## API URL

`src/environments/environment.ts` holds the default (`http://localhost:8080/api/v1`). To change it per deployment **without rebuilding**, serve a `config.json` next to `index.html`:

```json
{ "apiUrl": "https://api.example.com/api/v1" }
```

Static media (`Media.publicUrl`, e.g. school logos) is relative to the **server root**, not to `/api/v1`. The `SERVER_URL` token is derived from the API URL by dropping `/api/v1` (a relative `/api/v1` means same origin); set `"serverUrl"` in `config.json` to override it. Inject `SERVER_URL` (or use `MediaService.publicUrl()`) to build image URLs.

It is fetched at bootstrap; if it is missing or invalid the build-time default applies. In `ng serve`, put it in `public/config.json` (do not commit it).

## Deploying

Serve `dist/backoffice/browser` as static files with a fallback to `index.html` for unknown paths (SPA routing). The API must allow this origin in CORS.

## Conventions

Same rules as `apps/web/.claude/CLAUDE.md` (signals, `inject()`, `OnPush`, native control flow, zoneless). Layout: `features/<area>/` pages (lazy-loaded in `app.routes.ts`, children of the `Shell` route behind `adminGuard`), `services/` one per backend resource, `shared/components/` reusable UI (`app-alert`), aliases `@services/*`, `@models/*`, `@shared/*`, `@guards/*`. Use `API_URL` (never `environment.apiUrl` directly) to build request URLs.
