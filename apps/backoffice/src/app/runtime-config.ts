export interface RuntimeConfig {
  apiUrl?: string;
  serverUrl?: string;
}

const cleanUrl = (value: unknown): string | undefined =>
  typeof value === 'string' && value.trim() ? value.trim().replace(/\/+$/, '') : undefined;

/**
 * Reads `/config.json` (served next to index.html) so the API URL can change
 * per deployment without rebuilding. Any failure yields `{}`: the build-time
 * environment default then applies.
 */
export async function loadRuntimeConfig(fetchFn: typeof fetch = fetch): Promise<RuntimeConfig> {
  try {
    const res = await fetchFn('config.json', { cache: 'no-store' });
    if (!res.ok) return {};
    const body = (await res.json()) as { apiUrl?: unknown; serverUrl?: unknown } | null;
    const config: RuntimeConfig = {};
    const apiUrl = cleanUrl(body?.apiUrl);
    const serverUrl = cleanUrl(body?.serverUrl);
    if (apiUrl) config.apiUrl = apiUrl;
    if (serverUrl) config.serverUrl = serverUrl;
    return config;
  } catch {
    // missing or malformed config.json: keep the defaults
    return {};
  }
}
