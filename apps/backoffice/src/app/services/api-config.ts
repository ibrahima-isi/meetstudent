import { InjectionToken } from '@angular/core';
import { environment } from '../../environments/environment';

/**
 * API root (`.../api/v1`). Defaults to the build-time environment; `main.ts`
 * overrides it with the value from the runtime `config.json` when present.
 */
export const API_URL = new InjectionToken<string>('API_URL', {
  providedIn: 'root',
  factory: () => environment.apiUrl,
});
