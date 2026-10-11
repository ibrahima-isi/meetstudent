import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { HttpInterceptorFn } from '@angular/common/http';
import { PLATFORM_ID, REQUEST, inject } from '@angular/core';

/**
 * Gives relative API URLs the origin of the page, on both sides.
 *
 * The HTTP transfer cache keys a response by its URL, so the server and the
 * browser must ask for the same one or the browser fetches everything again
 * after hydration.
 *
 * - With `API_URL` (containers), the server fetches the API at its in-network
 *   address and `transferCacheOriginMap` maps that origin to the visitor's.
 * - Without it (`ng serve`), the server's URL is relative: it takes the origin
 *   of the request being rendered, which is also where Angular would send it.
 * - The browser takes the origin of the document.
 *
 * All three give `https://site/api/v1/...`. It must come last in the
 * interceptor list: the others match on the relative `apiUrl`.
 */
export const absoluteUrlInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/') || req.url.startsWith('//')) {
    return next(req);
  }

  const origin = isPlatformBrowser(inject(PLATFORM_ID))
    ? inject(DOCUMENT).location.origin
    : requestOrigin(inject(REQUEST, { optional: true }));

  return origin ? next(req.clone({ url: `${origin}${req.url}` })) : next(req);
};

function requestOrigin(request: Request | null): string | null {
  return request && URL.canParse(request.url) ? new URL(request.url).origin : null;
}
