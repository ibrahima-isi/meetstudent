/**
 * Value of `HTTP_TRANSFER_CACHE_ORIGIN_MAP` on the server.
 *
 * The server calls the API at its in-network address (`API_URL`, e.g.
 * `http://api:8080`). Without a map, that address is serialised into the page's
 * transfer state — leaking an internal host name, and keying the response
 * differently from the browser's request. Mapping it to the origin the visitor
 * reached makes the key `https://site/api/v1/...`, which is what the browser
 * asks for (see `absoluteUrlInterceptor`), so it reuses the cached response.
 *
 * Empty when there is nothing to hide (relative `apiUrl`) or no request to
 * learn the visitor's origin from (prerender, client-side render).
 */
export function transferCacheOriginMap(apiUrl: string, request: Request | null): Record<string, string> {
  if (!request || !URL.canParse(apiUrl)) {
    return {};
  }

  return { [new URL(apiUrl).origin]: new URL(request.url).origin };
}
