export interface RuntimeConfig {
  apiUrl?: string;
}

/**
 * Reads `/config.json` (served next to index.html) so the API URL can change
 * per deployment without rebuilding. Any failure yields `{}`: the build-time
 * environment default then applies.
 */
export async function loadRuntimeConfig(fetchFn: typeof fetch = fetch): Promise<RuntimeConfig> {
  try {
    const res = await fetchFn('config.json', { cache: 'no-store' });
    if (!res.ok) return {};
    const body: unknown = await res.json();
    const apiUrl = (body as { apiUrl?: unknown } | null)?.apiUrl;
    if (typeof apiUrl === 'string' && apiUrl.trim()) {
      return { apiUrl: apiUrl.trim().replace(/\/+$/, '') };
    }
  } catch {
    // missing or malformed config.json: keep the defaults
  }
  return {};
}
