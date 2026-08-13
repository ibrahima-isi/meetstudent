# Design — i18n foundation and the router

Written 2026-08-10 against `main` @ `2f106b8`. Every "current state" claim below
was verified in the code, with a `file:line` reference so it can be re-checked
rather than trusted.

This is the design for **Phase 1** of
[`2026-08-09-frontend-completion.md`](./2026-08-09-frontend-completion.md),
enlarged to absorb decision 4 of that plan (i18n), which was still open.

## Why i18n and routing are one piece of work

The plan treats them separately: routing is Phase 1, i18n is an unresolved
decision. They are not separable, because **the locale is the first segment of
every URL** (`/fr/schools/123`). Declaring the route table without `:lang` and
adding it afterwards means writing the table twice and re-touching every
component that navigates.

So this spec covers both, and Phase 1 of the plan is superseded by the PR
breakdown at the end of this document.

## Decisions

All settled. Do not re-litigate them during implementation.

| # | Decision | Consequence |
|---|---|---|
| 1 | **Two locales: `fr` (default) and `en`.** | French is the *source* language — the UI is already 100% French, so extraction lifts those strings verbatim into `fr.json`. English copy has to be **written**, which is authoring, not a mechanical move. Translation keys stay English, per the root `CLAUDE.md`; that is code, not content. |
| 2 | **Locale lives in the URL** — `/fr/…`, `/en/…`. `/` negotiates and redirects, falling back to `/fr`. | A shared link keeps the language it was copied in. Both versions are indexable, with `hreflang="x-default"` pointing at `/fr`. Routing and i18n become one design. |
| 3 | **Transloco (runtime), not `@angular/localize`.** | One build, one server bundle, one Docker image. Adding a locale is a file, not a build-config change. |
| 4 | **Chrome only — business content is not translated.** | School names and descriptions stay in the language they were authored in. No DB schema change, no backoffice translation UI. |
| 5 | **The front owns every user-facing string, including error text.** | `ErrorResponse.message` is never rendered to a user — logs and development only. `apps/api` is not touched by this work. |
| 6 | **No `users.locale` column.** The choice persists in a cookie. | Keeps `apps/api` closed, consistent with 4 and 5. |

### Why Transloco rather than the official path

`@angular/localize` is the Angular-blessed option and `extract-i18n` is already
declared at [`angular.json:69`](../../angular.json). That is the only argument in
its favour, and it loses on a concrete point:

[`Dockerfile:19`](../../Dockerfile) builds **one** artifact — `dist/frontend` —
launched by `node dist/frontend/server/server.mjs`. `ng build --localize`
produces `dist/frontend/browser/{en,fr}/` and changes the shape of the server
entry point, so both the Dockerfile and `server.ts` would need rework, and the
image would carry two browser bundles. In exchange for a few kilobytes on a
521 kB bundle that lazy loading is about to cut anyway.

The deciding argument is elsewhere. The language list changed during the design
conversation — five locales became two. Optimise for the cost of *adding* a
locale, not for the cost of two. Compile-time makes every addition structural;
runtime makes it a file.

Verified compatible: `@jsverse/transloco@8.4.0`, peer `@angular/core >=16.0.0`.

### What the RTL decision removed

The earlier five-locale set included Arabic. Dropping it removes the largest
cost item in the whole design: 67 directional Tailwind occurrences (`pl-11`,
`left-3`, `pr-4`…) that would have had to become logical properties, plus
six-form plural rules. English and French share the same simple plural rule.
None of that work is needed.

## Route architecture

```ts
export const routes: Routes = [
  // No locale in the URL: negotiate, then redirect, preserving the path.
  { path: '', pathMatch: 'full', canActivate: [localeRedirectGuard], children: [] },

  { path: ':lang', canActivate: [localeGuard], children: [
      { path: '',            loadComponent: () => …LandingPageComponent },
      { path: 'home',        loadComponent: () => …HomePageComponent },
      { path: 'schools/:id', loadComponent: () => …SchoolDetailPageComponent },
      { path: 'profile',     loadComponent: () => …ProfilePageComponent },

      { path: '', component: AuthLayoutComponent, children: [
          { path: 'login',    loadComponent: () => …LoginFormComponent },
          { path: 'register', loadComponent: () => …RegisterFormComponent },
          { path: 'verify',   loadComponent: () => …EmailVerificationComponent },
      ]},

      { path: '**', loadComponent: () => …NotFoundComponent },
  ]},

  { path: '**', canActivate: [localeRedirectGuard], children: [] },
];
```

**`:lang` is greedy, and that is deliberate.** It matches *any* first segment, so
`/login` would bind `lang = 'login'`. `localeGuard` turns that into a feature: a
segment that is not a known locale triggers a redirect to
`/{negotiated}/{original path}`. `/schools/12` lands on `/fr/schools/12`, and
every un-prefixed link keeps working instead of 404-ing. One guard, no special
case.

**`localeGuard`** validates the segment against the supported list, sets it as
Transloco's active language, and returns `true`. It runs identically under SSR
and in the browser.

**The auth layout.** [`app.html:33`](../../src/app/app.html) wraps `login`,
`register` and `verify` in a shared gradient container. It becomes a layout route
with its own `<router-outlet>` — not the same `@if` block written three times.

### Render mode

[`app.routes.server.ts:5`](../../src/app/app.routes.server.ts) currently declares
`path: '**'` as `RenderMode.Prerender`. That is harmless today only because
`app.routes.ts` is empty, so prerendering produces `/` and nothing else. **It
breaks the moment parameterised routes exist** — a parameter cannot be
prerendered without `getPrerenderParams`.

It becomes `RenderMode.Server`: the root redirect needs the request to read
`Accept-Language`, and `schools/:id` needs live data. Prerendering the two
landing pages (`/fr`, `/en`) is a later SEO optimisation, explicitly out of scope
here.

## i18n plumbing

**Translations are bundled, not fetched.** `assets` points at `public/`
(`angular.json`), so an HTTP loader would serve `/i18n/en.json` — which fails
under SSR, where a relative URL has no base. Instead the loader resolves a
dynamic `import()`:

```ts
const translations: Record<string, () => Promise<{ default: Translation }>> = {
  fr: () => import('./i18n/fr.json'),
  en: () => import('./i18n/en.json'),
};
```

This requires `"resolveJsonModule": true` in `tsconfig.json` (currently absent —
verified). It is correct under SSR by construction: no fetch, no `TransferState`,
no flash on hydration, and each locale stays a separate lazy chunk.

**`LocaleService`** holds the active locale as a signal and owns negotiation, in
this order:

1. the URL prefix,
2. the cookie (a previous *explicit* choice),
3. `Accept-Language`,
4. `fr`.

On the server it reads `inject(REQUEST, { optional: true })` — verified present
as `InjectionToken<Request | null>` in `@angular/core` — which is `null` in the
browser, where it falls back to the cookie then `navigator.language`. **The
cookie is written only on an explicit user choice**, never inferred from
negotiation.

### Collation is currently hard-coded to French

Four call sites sort with `localeCompare(…, 'fr')`:
[`home-page.component.ts:82`](../../src/app/features/student/home-page/home-page.component.ts),
`:84`, [`landing-page.component.ts:82`](../../src/app/features/public/landing-page/landing-page.component.ts),
`:84`, and
[`school-detail-page.component.ts:142`](../../src/app/features/student/school-detail-page/school-detail-page.component.ts).
They take the active locale instead. Minor, but it is an i18n bug and it lives in
files this work already opens.

## Component migration

Seven components lose their navigation `output()` chain and inject `Router`
instead. Two cases are not mechanical.

**The email between `register` and `verify`.** Today it travels through
`handleRegisterSuccess($event)` into `App.userEmail`
([`app.ts:40`](../../src/app/app.ts)). It does **not** become a query parameter:
an email address is personal data, and URLs are shared, logged and cached. It
travels through a short-lived signal on a service, and the verification screen
renders an email field when the value is absent. That also fixes reload and
direct access, which a query parameter would only half-fix.

**Front-authored defaults living in data fields.**
[`school.service.ts:46`](../../src/app/services/school.service.ts) substitutes
`'Établissement'` and `'Aucune description disponible'` when the API returns
nothing; [`user.service.ts:46`](../../src/app/services/user.service.ts) does the
same. These strings are chrome — the front wrote them — even though they sit in
a data field.

Translating them inside the service would be a trap: the service would need the
active locale injected, and the value would be frozen at fetch time, so it would
still read French after a language switch. **The service leaves the field empty
and the template applies the fallback through a translation key.** Services stay
locale-free; the fallback stays reactive.

This distinction generalises. The chrome/content boundary is not "front versus
API" — it is *who authored the string*.

## Testing

TDD per PR, as the root `CLAUDE.md` requires: the failing spec first, observed
red, then the minimum implementation.

New specs: `LocaleService` negotiation (server with `Accept-Language`, browser
with cookie, fallback to `fr`, explicit choice wins), `localeGuard` (known
locale passes, unknown locale redirects while preserving the path), the bundled
loader resolving both locales.

**Existing specs that break, and when.** The suite is 58 `it()` across 12 files.
Five files are affected, each reworked in the PR that changes its contract —
never afterwards:

| Spec | Line | Why it breaks | PR |
|---|---|---|---|
| `app.spec.ts` | `:29` | Expects an `<h1>` containing `MeetStudent`, rendered today by the landing page through the state machine. With a bare `<router-outlet>` it needs `provideRouter`. | 2 |
| `school-detail-page.component.spec.ts` | `:55` | `setInput('school', mockSchool)` — the input disappears; the component reads `:id`. | 2 |
| `user-documents.component.spec.ts` | `:75` | Asserts `statusLabel('VERIFIED') === 'Vérifié'`. | 5 |
| `school.service.spec.ts` | `:57`, `:78` | Asserts the `'Établissement'` default, which moves out of the service into the template. | 5 |
| `user.service.spec.ts` | `:47` | Same default, same move. | 5 |

`star-rating.component.spec.ts` is **not** affected — its `setInput` calls target
its own inputs, not the navigation contract.

Final verification on the real stack, not on the test suite alone:
`docker compose up --build`, then `http://localhost:4200`.

## PR breakdown

One branch and one PR each, in order. The 58 web specs stay green at every step.

| # | Branch | Content | Files | Risk |
|---|---|---|---|---|
| 1 | `feat/i18n-foundation` | Transloco, `LocaleService`, bundled loader, plus one translated namespace in `en.json`/`fr.json` to prove the pipeline end to end | ~6 new, 3 modified | Low — **no template touched**, existing specs pass unmodified |
| 2 | `feat/router-foundation` | Route table with `:lang`, both guards, lazy loading, `<router-outlet>`, state machine deleted, `school-detail` reads `:id`, auth layout, 404, `RenderMode.Server` | ~20 | **High** — this is the rewrite |
| 3 | `feat/language-switcher` | Language control, cookie persistence, `<html lang>`, `hreflang` | ~4 | Low |
| 4 | `refactor/i18n-auth-screens` | Extraction: login, register, verify | ~8 | Low on the code, but the English copy is **written, not moved** |
| 5 | `refactor/i18n-public-student-screens` | Extraction: landing, home, school-detail, profile, documents; service defaults moved to templates; collation | ~11 | Same — plus three specs to rework |

Because French is the source language, PRs 4 and 5 are only half mechanical. Moving
the existing French strings into `fr.json` is a copy-paste; producing `en.json`
means writing English UI copy that does not exist yet. Budget for authoring, and
expect the English wording to need a review pass the French side does not.

**PR 1 precedes PR 2** so the route table is written once, with `:lang` in it
from the start.

**PR 2 does not split honestly.** Removing the state machine deprives
`school-detail` of its `[school]` input, so it must read `:id` in the same
delivery. Splitting by component instead produces an intermediate state where
`app.html` juggles both outputs and an outlet — riskier than the large PR. The
plan already says it: *"Do it in one focused PR."* Its file list is presented for
approval before any edit, per the three-file rule in the root `CLAUDE.md`.

**No PR in this sequence touches `compose.yml` or the `Dockerfile`.** Phase 0
(email verification, `apps/api`) adds Mailpit to `compose.yml`, so the two
sessions cannot conflict.

## Out of scope

Stated explicitly so nothing here is mistaken for an oversight:

- `authGuard` on `home` and `profile` — Phase 2 of the plan. Routes are declared
  without an authentication guard so this work does one thing.
- Deleting the `@data/*` mock fallbacks — Phase 3.
- Translating business content — decision 4.
- A `code` field on `ErrorResponse` — decision 5. It becomes necessary for
  Phase 4's "clear rejection messages" on uploads, where six distinct
  `IllegalArgumentException`s all surface as a 400 and the front cannot tell them
  apart. Worth its own piece of work, in `apps/api`.
- Per-locale prerendering of the landing pages.

## Risks

- **PR 2 rewrites the navigation layer**, it does not extend it. Every
  component's inputs and outputs change at once. Keep the specs green at each
  commit inside the PR, not only at the end.
- **Guards run under SSR.** A guard that reads `localStorage` throws on the
  server. `TokenService` already guards for this — `localeGuard` and
  `LocaleService` must keep the same discipline. The cookie must be read through
  `REQUEST` on the server, never through `document.cookie`.
- **`:lang` being greedy is load-bearing.** If `localeGuard` stops redirecting on
  an unknown segment, every un-prefixed URL 404s at once. It deserves a spec of
  its own.
