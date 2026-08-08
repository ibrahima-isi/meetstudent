export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api/v1',
  /**
   * Server root, used for static media under `/uploads/public/**`.
   * Kept separate from `apiUrl` because `Media.publicUrl` is relative to the
   * server root, not to `/api/v1` — and because static files may later move to
   * a CDN independently of the API.
   */
  serverUrl: 'http://localhost:8080'
};
