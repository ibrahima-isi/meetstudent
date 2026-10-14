import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { isPlatformBrowser } from '@angular/common';
import { Injector, PLATFORM_ID, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, shareReplay, switchMap, throwError } from 'rxjs';
import { API_URL } from '@services/api-config';
import { AuthService } from '@services/auth.service';
import { TokenService } from '@services/token.service';

/** Login and refresh answer 401 for bad credentials; refreshing there would loop. */
const isAuthEndpoint = (req: HttpRequest<unknown>, apiUrl: string): boolean =>
  req.url === `${apiUrl}/auth` || req.url.startsWith(`${apiUrl}/auth/`);

/** The refresh in flight, shared so a burst of 401s costs one call. */
let refresh$: Observable<string> | null = null;

function endSession(auth: AuthService, injector: Injector): void {
  auth.logout();
  if (!isPlatformBrowser(injector.get(PLATFORM_ID))) return;
  const router = injector.get(Router);
  const onLogin = /^\/login(?:\?|$)/.test(router.url);
  void router.navigate(['/login'], {
    queryParams: { reason: 'expired', ...(onLogin ? {} : { returnUrl: router.url }) },
  });
}

/**
 * On a 401, refreshes the access token once (concurrent 401s share that single
 * refresh), retries the original request once with the new token, and logs out
 * if the refresh fails. Must come after `jwtInterceptor` in the chain.
 */
export const refreshInterceptor: HttpInterceptorFn = (req, next) => {
  // A server render has no browser session or storage to refresh or clear.
  if (!isPlatformBrowser(inject(PLATFORM_ID))) return next(req);
  const auth = inject(AuthService);
  const tokens = inject(TokenService);
  const apiUrl = inject(API_URL);
  const injector = inject(Injector);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || isAuthEndpoint(req, apiUrl)) {
        return throwError(() => error);
      }

      const stored = tokens.refreshToken();
      if (!stored) {
        if (tokens.token()) endSession(auth, injector);
        else auth.logout();
        return throwError(() => error);
      }

      refresh$ ??= auth.refreshToken(stored).pipe(
        switchMap((res) => [res.accessToken]),
        catchError((refreshError: unknown) => {
          endSession(auth, injector);
          return throwError(() => refreshError);
        }),
        finalize(() => (refresh$ = null)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );

      return refresh$.pipe(
        switchMap((accessToken) =>
          next(req.clone({ setHeaders: { Authorization: `Bearer ${accessToken}` } })).pipe(
            catchError((retryError: unknown) => {
              if (retryError instanceof HttpErrorResponse && retryError.status === 401) {
                endSession(auth, injector);
              }
              return throwError(() => retryError);
            }),
          ),
        ),
      );
    }),
  );
};
