/**
 * The values in `environment.ts` are baked into the bundle at build time, which
 * is fine for the browser: it reaches the API through the ports published on the
 * host. The SSR bundle runs inside its own container, where `localhost` is the
 * web container itself and nothing answers on 8080 — server-side requests need
 * the API's in-network address instead.
 *
 * Rather than rebuild the image per environment, the server entry point
 * (`server.ts`) overrides the values from `process.env` before Angular
 * bootstraps. The browser bundle has its own module instance and is unaffected.
 */
export interface RuntimeEnvironment {
  apiUrl: string;
  serverUrl: string;
}

function readOverride(raw: string | undefined): string | null {
  const value = raw?.trim();
  if (!value) {
    return null;
  }

  // Callers build URLs as `${apiUrl}/users`, so a trailing slash would yield `//users`.
  return value.replace(/\/+$/, '');
}

/**
 * Applies `API_URL` / `SERVER_URL` overrides onto `target`, in place.
 *
 * Mutation is deliberate: services capture `environment.apiUrl` in field
 * initialisers, so they must observe the override through the same object
 * reference they already imported. Call this before the app bootstraps.
 */
export function applyServerEnvironment(
  target: RuntimeEnvironment,
  vars: Record<string, string | undefined>,
): RuntimeEnvironment {
  const apiUrl = readOverride(vars['API_URL']);
  if (apiUrl) {
    target.apiUrl = apiUrl;
  }

  const serverUrl = readOverride(vars['SERVER_URL']);
  if (serverUrl) {
    target.serverUrl = serverUrl;
  }

  return target;
}
