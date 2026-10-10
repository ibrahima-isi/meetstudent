# Landing, Dock navbar and auth redesign — design

Date: 2026-10-10 · Scope: `apps/web` only (no API change) · Status: awaiting review

## Intent

The web app looks elementary. The "landing" is really the school catalogue under a plain header; login/register are a white card floating in empty space with no way back to the home page. The owner wants a polished, high-standing first impression (reference spirit: Diplomeo, Campus France, Linear — inspired, not copied): a real marketing landing with animated/3D visuals, a modern navbar, and professional auth screens. The landing carries most of the project's visual value.

Success: a visitor lands on a refined, fast, animated page, understands the product, searches for a school or signs up in one click, can always get back home, and the same visual language runs through the navbar and auth screens, in light and dark, fr and en, on desktop and mobile.

## Decisions (from brainstorming)

| Topic | Decision |
|---|---|
| Media | Everything generated (CSS/SVG/gradients); no video files required. Slots can accept real media later. |
| Brand direction | Premium indigo/violet: mesh gradients, frosted glass, elegant dark mode (Linear-like). |
| 3D technique | CSS 3D transforms + SVG + animated gradients, no WebGL/three.js. |
| Catalogue | Moves to a public page `/:lang/schools`; landing takes `/:lang`. |
| Navbar | Floating frosted pill, auto-hiding ("Dock" behaviour). |
| Landing sections | Animated key figures, How it works (3 steps), Featured schools, Testimonials. |
| Auth layout | Split screen: form one side, animated brand panel the other. |
| Delivery | 3 successive PRs into `dev`. |
| Content policy | Testimonials are clearly labelled examples until real reviews exist; key figures come from the API and the section is hidden if unavailable. No invented figures or reviews presented as real. |

## PR 1 — Design system and Dock navbar

**Tokens** (`styles.css`): indigo/violet scale, mesh-gradient and glass surfaces, shadows, radii; semantic tokens only (the dark-mode guard spec must stay green). A finer type scale with a modern sans (Inter or Geist, self-hosted or via the existing font setup).

**Buttons**: primary (solid indigo, glow on hover, clear focus ring), secondary (outlined, quiet), ghost. One shared directive/component so every page uses the same hierarchy.

**Shared UI** under `shared/components/`: `GlassCard`, `AnimatedBackground` (orbs + grain, pure CSS), a `reveal` on-scroll animation directive. All motion is disabled under `prefers-reduced-motion`.

**Navbar** (`shared/components/dock-navbar`), used by landing, catalogue, auth and signed-in pages:
- Centred frosted pill, offset from the top. Left: logo/brand linking to `/:lang` (always the way home). Centre: Schools, How it works, Reviews (anchor links on the landing, route links elsewhere). Right: compact FR|EN switch, icon-only theme toggle, "Log in" (secondary), "Sign up" (primary). Signed-in: avatar menu replaces the auth buttons.
- Behaviour: hides on scroll down, returns on scroll up or when the pointer is within ~80px of the viewport top; always shown near the top of the page. Keyboard focus into the bar also reveals it.
- Mobile: logo + menu button opening a full-screen panel; no hover logic (touch).
- Implementation: signals + the component `host` object (no `@HostListener`/`@HostBinding`), listeners registered only in the browser (SSR-safe, zoneless), `OnPush`.
- Replaces the current `language-switcher`/`theme-toggle` placement; those components are restyled or folded in, not duplicated.

## PR 2 — Landing and `/schools`

**Routes**: `/:lang` → new `LandingPage` (SSR-rendered, SEO title/meta/alternate links as today). New public `/:lang/schools` → the existing catalogue component moved as-is (logic, API search/filters/paging unchanged), SSR-rendered, no `authGuard`. `app.routes.server.ts` and its guard spec updated: guarded routes (`home`, `schools/:id`, `profile`) stay client-rendered.

**Landing sections**:
1. Hero: strong headline and subtitle, search bar that navigates to `/:lang/schools?q=…`, primary CTA (explore schools) and secondary CTA (create account), CSS-3D floating school cards with pointer parallax over an animated mesh background.
2. Key figures: animated counters from existing API totals (schools, programmes…); section hidden if the API call fails or returns nothing.
3. How it works: three steps (search, compare, choose) with small 3D/SVG visuals.
4. Featured schools: cards from the real API, shared `error-state` with retry on failure, never fake schools.
5. Testimonials: labelled examples, structured so real reviews drop in later.
6. Final CTA band and footer.

All copy via Transloco, keys in English, `fr.json` and `en.json` updated together.

## PR 3 — Auth screens

Split layout replacing `auth-layout`: brand panel (animated background, tagline, floating cards, example testimonial) beside the form; the Dock navbar above and a "← Back to home" link. Less empty white: tinted background, larger fields, clearer error and loading states. Register keeps its 2-step flow with a clearer progress indicator. The brand panel is hidden below the `lg` breakpoint. Login/register logic, validation and API calls are unchanged.

## Out of scope

Backend/API changes; WebGL/three.js (possible later as a desktop-only lazy hero upgrade); real video assets; email verification and password reset (existing known gaps); the backoffice; the legacy React prototype.

## Testing and verification

- TDD per unit; Karma specs next to each component/directive: navbar visibility rules (scroll direction, pointer proximity, focus reveal, reduced motion, SSR no-op), landing sections incl. hidden figures on API failure, `/schools` public rendering, routing and server-route guard spec.
- `npm test -- --no-watch --browsers=ChromeHeadless` and `npm run build` (SSR) green before each PR.
- Live check with the stack up (`docker compose up -d`, then `docker compose down`), driven in a browser at desktop and mobile widths, light and dark, fr and en. Evidence reported per PR.

## Risks

- Moving the catalogue changes the URL of the landing's current content; internal links, alternate-link service and SEO metadata need updating together.
- Auto-hiding navbars can hurt accessibility; mitigated by focus reveal, always-visible near top, and no hiding while the mobile menu is open.
- CSS 3D/blur cost on low-end phones; mitigated by reduced effects below `md` and under `prefers-reduced-motion`.
