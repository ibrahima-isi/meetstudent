import { InjectionToken, inject } from '@angular/core';
import { environment } from '../../environments/environment';

/**
 * API root (`.../api/v1`). Defaults to the build-time environment; `main.ts`
 * overrides it with the value from the runtime `config.json` when present.
 */
export const API_URL = new InjectionToken<string>('API_URL', {
  providedIn: 'root',
  factory: () => environment.apiUrl,
});

/** Server root of an API url: `http://host/api/v1` -> `http://host`; a relative `/api/v1` -> `''` (same origin). */
export const serverUrlFrom = (apiUrl: string): string =>
  apiUrl.replace(/\/+$/, '').replace(/\/api\/v1$/, '');

/**
 * Server root, for static files such as `Media.publicUrl` (relative to the
 * server root, NOT to `/api/v1`). Derived from `API_URL`; `config.json` may
 * override it with `serverUrl`. Empty means same origin.
 */
export const SERVER_URL = new InjectionToken<string>('SERVER_URL', {
  providedIn: 'root',
  factory: () => serverUrlFrom(inject(API_URL)),
});
