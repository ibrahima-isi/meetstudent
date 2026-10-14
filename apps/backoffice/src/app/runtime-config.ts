export interface RuntimeConfig {
  webUrl?: string;
  apiUrl?: string;
  serverUrl?: string;
}

const cleanUrl = (value: unknown): string | undefined =>
  typeof value === 'string' && value.trim() ? value.trim().replace(/\/+$/, '') : undefined;

const publicWebUrl = (value: unknown): string | undefined => {
  const cleaned = cleanUrl(value);
  if (!cleaned) return undefined;
  try {
    const url = new URL(cleaned);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
      return undefined;
    }
    return url.href.replace(/\/+$/, '');
  } catch {
    return undefined;
  }
};

/**
 * Reads `/config.json` (served next to index.html) so the API URL can change
 * per deployment without rebuilding. Any failure yields `{}`: the build-time
 * environment default then applies.
 */
export async function loadRuntimeConfig(fetchFn: typeof fetch = fetch): Promise<RuntimeConfig> {
  try {
    const res = await fetchFn('config.json', { cache: 'no-store' });
    if (!res.ok) return {};
    const body = (await res.json()) as { apiUrl?: unknown; serverUrl?: unknown; webUrl?: unknown } | null;
    const config: RuntimeConfig = {};
    const apiUrl = cleanUrl(body?.apiUrl);
    const serverUrl = cleanUrl(body?.serverUrl);
    const webUrl = publicWebUrl(body?.webUrl);
    if (webUrl) config.webUrl = webUrl;
    if (apiUrl) config.apiUrl = apiUrl;
    if (serverUrl) config.serverUrl = serverUrl;
    return config;
  } catch {
    // missing or malformed config.json: keep the defaults
    return {};
  }
}
