# PR 3 — the language switcher

Written 2026-08-12 against `main` @ `84487b4`, on branch `feat/language-switcher`.

PR 3 of the five in
[`2026-08-10-i18n-and-routing-design.md`](./2026-08-10-i18n-and-routing-design.md).
PRs 1 and 2 are merged. The design spec assigns this PR four things: **language
control, cookie persistence, `<html lang>`, `hreflang`.**

## What is already in place

Read from the code, not assumed:

- `LocaleService.use(locale)` — sets the active language and returns its load.
  `localeGuard` awaits it on every navigation, so it is the single point where
  the active locale changes.
- `LocaleService.remember(locale)` — writes the `meetstudent_locale` cookie,
  browser-only. **Written in PR 1 and never called.** This PR is what calls it.
- `LocaleService.negotiate()` — already reads that cookie ahead of
  `Accept-Language`, verified live on the stack: a `meetstudent_locale=en`
  cookie sends `/` to `/en` against a French `Accept-Language`.
- `fr.json` / `en.json` already carry a `language` namespace — `fr: "Français"`,
  `en: "English"`, each untranslated on purpose: a language's own name is what
  the speaker of that language recognises.

So the persistence half of "cookie persistence" exists and is proven. What is
missing is the control that calls it, and the two document-level signals.

## What this PR does not do

- No new chrome. `App` is a bare `<router-outlet>` after PR 2 and stays that
  way; adding a global header is a design decision nobody has taken.
- No screen copy. PRs 4 and 5 extract it.
- No per-locale prerendering, no sitemap. Out of scope in the design spec.

---

### Task 1: `<html lang>` follows the active locale

`src/index.html` hardcodes `<html lang="en">`. Two things are wrong with that:
it is wrong for every French page, and it never changes when the locale does.

**Red:** in `locale.service.spec.ts`, after `use('en')` the fake document's
`documentElement.lang` is `'en'`; after `use('fr')`, `'fr'`.

**Green:** `use()` sets it. That is the right home for it — `use()` is the only
way the active locale moves, it runs on the server and in the browser, and
`localeGuard` awaits it, so SSR serialises the correct attribute rather than
patching it after hydration.

Also change the static default in `index.html` from `en` to `fr`, matching the
source language, so the pre-hydration document is right for the common case.

**Files:** `src/app/services/locale.service.ts`, `.spec.ts`, `src/index.html`.

---

### Task 2: `hreflang` alternates

Decision 2 of the design spec: *"Both versions are indexable, with
`hreflang="x-default"` pointing at `/fr`."* Nothing emits those tags today.

Three `<link rel="alternate">` tags in `<head>`, rewritten on every navigation:

```html
<link rel="alternate" hreflang="fr" href="https://host/fr/login">
<link rel="alternate" hreflang="en" href="https://host/en/login">
<link rel="alternate" hreflang="x-default" href="https://host/fr/login">
```

They must be **absolute** — Google ignores relative `hreflang` — which means the
origin has to come from somewhere:

| | Origin |
|---|---|
| Server | `new URL(request.url).origin`, from the `REQUEST` token |
| Browser | `document.location.origin` |

`LocaleService` already injects `REQUEST` optionally and already branches on
`isPlatformBrowser`, so the same shape applies. A separate
`AlternateLinksService` rather than more surface on `LocaleService`: one is
about *which* locale is active, the other about advertising the alternatives.

Driven by `NavigationEnd`, because the path is what changes.

**Red:** navigating to `/fr/login` puts three links in the fake document's head
with the right `hreflang`/`href` pairs; navigating again to `/en/register`
rewrites them rather than appending a second set.

**Files:** `src/app/i18n/alternate-links.service.ts` + `.spec.ts`, wired in
`app.config.ts`.

---

### Task 3: the language switcher component

A control listing both locales, marking the active one, and switching to the
other. Switching means three things, in order:

1. `remember(locale)` — this is an explicit choice, which is exactly the case
   `remember()` was written for and the only case it may be called in.
2. Navigate to the same URL with the first segment replaced, **preserving query
   parameters and the fragment** — a switch must not lose the page's state.
3. Let `localeGuard` do the rest. It runs on the new URL, calls `use()`, and
   awaits the translations.

The component does not call `use()` itself. Navigating is enough, and calling
both would load twice.

**Red:** clicking `en` on `/fr/schools/7?tab=programs#rates` calls
`remember('en')` and navigates to `/en/schools/7?tab=programs#rates`; the active
locale is rendered as pressed and is not a link to itself.

**Files:** `src/app/shared/components/language-switcher/language-switcher.component.ts`
+ `.spec.ts`.

---

### Task 4: place it, and verify live

Two placements, chosen for coverage rather than for count:

- `landing-page` header — the first screen an anonymous visitor sees.
- `auth-layout` — login, register, verify.

That is every screen a first-time visitor reaches before signing in, which is
when the choice matters. Past that point the cookie carries it: `negotiate()`
reads `meetstudent_locale` before `Accept-Language`, so a returning visitor
lands in their language without touching the control again.

**Deliberate gap:** `home`, `profile` and `school-detail` get no switcher. They
are post-login screens whose language is already settled by the cookie. Adding
it to all five means five template edits for a control nobody needs there. Worth
revisiting when the app grows a real global header — which is where this belongs.

**Live verification**, on `docker compose up --build`:

- `curl /fr/login` and `/en/login` carry `<html lang="fr">` / `lang="en"` and
  three correct `hreflang` links each
- switching in the browser lands on the mirrored URL and sets the cookie
- a second visit to `/` follows the cookie
- then `docker compose down`

## Done when

Every screen states its language to a machine, both versions of every URL
advertise each other, and a visitor can change language and have that survive a
reload.
