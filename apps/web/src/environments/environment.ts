/**
 * Build-time values, used as-is by the BROWSER bundle.
 *
 * Production is single-origin: the reverse proxy sends `/api/*` and
 * `/uploads/public/*` to the API and everything else to the web container, so
 * the browser calls relative URLs and never needs to know where the API lives.
 * `ng serve` gets the same behaviour from `proxy.conf.json`.
 *
 * The SSR process has no page origin to resolve a relative URL against, so it
 * replaces both values with the absolute in-network ones from `API_URL` /
 * `SERVER_URL` (see `server-environment.ts`).
 */
export const environment = {
  production: false,
  apiUrl: '/api/v1',
  /**
   * Server root, for server-side requests to the API host outside `/api/v1`.
   * Empty means "same origin as the page". Overridden by `SERVER_URL` in SSR.
   */
  serverUrl: '',
  /**
   * Prefix for `Media.publicUrl` (relative to the server root, NOT to
   * `/api/v1`) when it becomes an `<img src>`. The result is serialised into
   * server-rendered HTML and loaded by the visitor's browser, so it must be
   * browser-reachable — which is why it is deliberately NOT overridden by
   * `SERVER_URL` in SSR, where the API's in-network address would be a broken
   * image. A CDN origin can go here later.
   */
  mediaBaseUrl: ''
};
