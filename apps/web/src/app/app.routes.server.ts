import { RenderMode, ServerRoute } from '@angular/ssr';

/**
 * Server-rendered, not prerendered: the root redirect needs the request to read
 * `Accept-Language`, and `schools/:id` needs live data. Prerendering the two
 * landing pages is a later SEO optimisation.
 *
 * It also decides whether `REQUEST` is injectable at all — `@angular/ssr`
 * provides it under `RenderMode.Server` and nowhere else, and `LocaleService`
 * negotiates from its headers.
 *
 * The screens behind `authGuard` are rendered in the browser instead. The
 * session is in `localStorage`, which the server cannot read, so a server
 * render would always see an anonymous visitor and redirect a logged-in one to
 * the login on every reload or deep link. They are private, so nothing is lost
 * for SEO; landing, login and register stay server-rendered.
 *
 * `reset-password` and `verify-email` are public but client-rendered too: their token travels in the
 * URL fragment, which the server never receives, and the page must reach the
 * browser as a shell with no token or state in it. Keep these before
 * the catch-all, and in step with the `authGuard` routes (a spec checks it).
 */
export const serverRoutes: ServerRoute[] = [
  { path: ':lang/home', renderMode: RenderMode.Client },
  { path: ':lang/profile', renderMode: RenderMode.Client },
  { path: ':lang/schools/:id', renderMode: RenderMode.Client },
  { path: ':lang/reset-password', renderMode: RenderMode.Client },
  { path: ':lang/verify-email', renderMode: RenderMode.Client },
  {
    path: '**',
    renderMode: RenderMode.Server,
  },
];
