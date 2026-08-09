# Plan — finishing the web front

Written 2026-08-09 against `main` @ `fea25f1`. Every "current state" claim below
was verified in the code, not assumed.

## Where the front actually stands

Eight feature components, ten services, 58 passing specs, an SSR build that
works. The backend it talks to is mature: 9 controllers, 16 Flyway migrations,
93 passing tests. The gap is entirely on the front.

**The front does not use the router.** `app.routes.ts` is `[]`. Navigation is a
signal state machine in `app.ts` — `view()` over `'landing' | 'login' |
'register' | 'verify' | 'home' | 'school-detail' | 'profile'` — and `app.html`
switches on it with `@if`. There is no `<router-outlet>`. Consequences: no URL
per screen, no deep links, no browser history, nothing shareable or indexable,
and no lazy loading (hence the 521 kB initial bundle against a 500 kB budget).

Everything else in this plan is downstream of that.

### Verified gaps

| Gap | Evidence |
|---|---|
| Email verification is fiction | The component injects nothing (`inject(` appears 0 times); it compares the typed code to `user.verificationCode` read from `localStorage` behind a `setTimeout`. `UserEntity` has no verification field at all — there is no backend for it. |
| Cannot add to a wishlist | `addToWishlist` exists in `user.service.ts` but is referenced by no component or template. Only `removeFromWishlist` is wired. |
| Silent mock fallback | On any API error, `landing-page`, `home-page` and `school-detail-page` populate themselves from `@data/schools` / `@data/programmes`. The user sees plausible fake data with no indication anything failed. |
| No route guards | Student screens are reachable purely through local state; nothing checks authentication before rendering them. |
| No token refresh on 401 | `jwtInterceptor` attaches the bearer token and nothing more. `authService.refreshToken()` exists but no interceptor calls it, so a session dies at access-token expiry. |
| Errors only reach the console | `console.error` in the two list pages; no user-facing error, empty or loading state anywhere. |
| Rating UI is not submitted | `star-rating` calls `rateSchool` / `rateProgram` / `rateCourse`, but outside the specs those paths are only referenced from that one shared component — no page offers a rating flow. |
| Dead React prototype | 58 `.tsx` files under `apps/web/meetstudent/`, built by nothing, versioned and confusing. |

### API surface the front does not consume

Backend endpoints with no client method: accreditations CRUD, tags CRUD beyond
list/create, roles (entire controller), courses/programs/schools write
operations, media moderation (`GET /media?status=`, `PATCH
/media/{id}/verification`), rate deletion and per-entity rate listing.

Most of these are admin operations and belong to `apps/backoffice`, **not** to
this front. Decide that explicitly before scoping (see Decisions).

## What "100% done" is taken to mean

A student-facing product where every screen has a URL, authentication survives a
token expiry, failures are visible and honest, and every feature the backend
supports for a STUDENT or EXPERT is reachable. Admin/moderation surfaces are out
of scope — they belong to the backoffice.

## Decisions to confirm before starting

1. **Email verification.** It has no backend. Either (a) drop the screen and let
   registration log the user straight in, or (b) build it server-side first —
   entity field, migration, token issuance, mail sending, endpoints. (a) is a
   day; (b) is a project. **Recommended: (a) now, (b) as its own backlog item.**
2. **Admin features.** Confirm they are out of scope for `apps/web`.
3. **EXPERT role.** Experts may rate programs and courses, students only
   schools. Should this front serve both, or students only?
4. **i18n.** The UI is French, the code English, with no i18n setup. Single
   locale, or is a second one coming? This changes every template touched.
5. **The approval rule.** With empty bypass lists and one approval required, no
   PR in this plan can be merged solo. Settle it first — most likely
   `required_approving_review_count: 0`, leaving CI as the gate.

## Phases

Ordered by dependency. Each task is one branch, one PR, TDD as per the root
`CLAUDE.md`: write the failing spec, watch it fail, implement, refactor.

### Phase 1 — Routing foundation

The prerequisite for everything else. Do not start Phase 2 before this lands.

1. **Introduce the router.** Declare routes for the seven existing views, add
   `<router-outlet>` to `app.html`, delete the `view()` state machine and the
   `(onXxxClick)` output chain that drives it. Components stop emitting
   navigation events and inject `Router` instead.
   *Done when:* every screen has its own URL, browser back/forward works, and a
   full page reload on any URL renders the right screen through SSR.
2. **Route parameters.** `school-detail` takes the school id from the URL and
   loads it itself, instead of receiving a `School` object through an input.
   *Done when:* `/schools/:id` renders standalone with no prior navigation.
3. **Lazy loading.** `loadComponent` on every feature route.
   *Done when:* the production build no longer warns about the initial bundle
   budget.
4. **404 route.**

### Phase 2 — Session and access control

5. **Refresh interceptor.** On a 401, call `refreshToken()`, retry the original
   request once, and log out if the refresh itself fails. Queue concurrent 401s
   so a burst triggers one refresh, not many.
   *Done when:* an expired access token is invisible to the user, and specs
   cover the concurrent case.
6. **`authGuard`** on student routes, redirecting to `/login` with a return URL.
7. **`roleGuard`** if the EXPERT decision calls for it.
8. **Email verification**, per decision 1.

### Phase 3 — Honest data

9. **Delete the mock fallbacks** and `app/data/*.ts`. On error the page shows an
   error state with a retry action.
   *Done when:* stopping the API makes every list show an error, never fake
   schools.
10. **Loading and empty states** on all three list/detail pages.
11. **Shared error surface** — a toast or inline banner, one mechanism reused
    everywhere rather than per-page handling.

### Phase 4 — Missing features

12. **Add to wishlist** from the school card and the school detail page, with
    optimistic update and rollback on failure.
    *Done when:* a school can be added and removed, and the state survives a
    reload.
13. **Rating flow** — submit a rating from the school detail page, show the
    aggregate, prevent double submission, respect the role rules (students rate
    schools; experts also rate programs and courses).
14. **Search and filters against the API.** `searchSchools` and
    `searchProgramsByName` exist and are unused by the filter UI, which filters
    the already-loaded page in memory — so it silently only searches the first
    50 results.
15. **Documents polish** — upload progress, size and MIME validation mirroring
    the server's 10 MB limit, clear rejection messages, moderation status shown.

### Phase 5 — Quality

16. **Accessibility pass** — focus management on navigation, labels, keyboard
    paths through the star rating and the verification code inputs.
17. **Raise spec coverage** on the newly wired flows; the current 58 specs skew
    towards services.
18. **Remove `apps/web/meetstudent/`** once every screen is confirmed ported.
    Its 58 `.tsx` files are the original design source and nothing builds them.

## Risks

- **Phase 1 is a rewrite of the navigation layer**, not an addition. Every
  component's inputs and outputs change. Do it in one focused PR rather than
  spread across features, and keep the specs green at each step.
- **SSR + guards**: a guard that reads `localStorage` runs on the server, where
  it is undefined. `TokenService` already guards for this — keep that discipline
  in new code, or SSR will throw.
- **Deleting the mock fallback will make the app look broken** whenever the API
  is down. That is the point, but it changes the demo experience — make sure the
  Docker stack is the default way to run it.

## Running and verifying

```bash
docker compose up --build          # api + postgres + front, http://localhost:4200
docker compose up -d api           # api only, then `ng serve` on the host
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless
```

The database starts empty, so seed a few schools through the API before judging
any list screen — an empty list is not a bug.
