import { RenderMode, ServerRoute } from '@angular/ssr';

/**
 * Server-rendered, not prerendered: the root redirect needs the request to read
 * `Accept-Language`, and `schools/:id` needs live data. Prerendering the two
 * landing pages is a later SEO optimisation.
 *
 * It also decides whether `REQUEST` is injectable at all — `@angular/ssr`
 * provides it under `RenderMode.Server` and nowhere else, and `LocaleService`
 * negotiates from its headers.
 */
export const serverRoutes: ServerRoute[] = [
  {
    path: '**',
    renderMode: RenderMode.Server,
  },
];
