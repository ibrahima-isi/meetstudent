import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { HttpInterceptorFn } from '@angular/common/http';
import { PLATFORM_ID, inject } from '@angular/core';

/**
 * Gives the browser's relative API URLs the page origin.
 *
 * The HTTP transfer cache keys a response by its URL. The server fetches the
 * API at its in-network address and maps that origin to the visitor's origin
 * (`transferCacheOriginMap`), so the key it serialises is
 * `https://site/api/v1/...`. The browser would otherwise ask for the relative
 * `/api/v1/...`, miss that key and fetch the list again after hydration.
 *
 * Browser only — the server already has absolute URLs. It must come last in
 * the interceptor list: the others match on the relative `apiUrl`.
 */
export const absoluteUrlInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isPlatformBrowser(inject(PLATFORM_ID)) || !req.url.startsWith('/') || req.url.startsWith('//')) {
    return next(req);
  }

  return next(req.clone({ url: `${inject(DOCUMENT).location.origin}${req.url}` }));
};
