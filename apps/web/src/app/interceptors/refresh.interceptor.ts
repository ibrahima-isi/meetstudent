import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { isPlatformBrowser } from '@angular/common';
import { Injector, PLATFORM_ID, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, shareReplay, switchMap, throwError } from 'rxjs';
import { AuthService } from '@services/auth.service';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { environment } from '../../environments/environment';

const AUTH_URL = `${environment.apiUrl}/auth`;

/** Login and refresh answer 401 for bad credentials; refreshing there would loop. */
const isAuthEndpoint = (req: HttpRequest<unknown>): boolean =>
  req.url === AUTH_URL || req.url.startsWith(`${AUTH_URL}/`);

/** The refresh in flight, shared so a burst of 401s costs one call. */
let refresh$: Observable<string> | null = null;

/**
 * Ends the session after a refresh that failed: clears the tokens, then sends
 * the visitor to the login of the language being read, with the page they were
 * on as `returnUrl` so they come back to it. Without the navigation the stale
 * page (and its data) would stay on screen until a reload. Browser only — a
 * server render holds no session. Router and locale are resolved here, not at
 * interceptor setup, to stay out of the HTTP client's dependency graph.
 */
function endSession(auth: AuthService, injector: Injector): void {
  auth.logout();
  if (!isPlatformBrowser(injector.get(PLATFORM_ID))) {
    return;
  }

  const router = injector.get(Router);
  const locale = injector.get(LocaleService).active();
  const onAuthScreen = /\/(login|register)(\?|$)/.test(router.url);
  void router.navigate(
    ['/', locale, 'login'],
    onAuthScreen ? undefined : { queryParams: { returnUrl: router.url } },
  );
}

/**
 * On a 401, refreshes the access token once (concurrent 401s share that single
 * refresh), retries the original request once with the new token, and ends the
 * session (logout and redirect to login) if the refresh fails. Must come after
 * `jwtInterceptor` in the chain.
 */
export const refreshInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const tokens = inject(TokenService);
  const injector = inject(Injector);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || isAuthEndpoint(req)) {
        return throwError(() => error);
      }

      const stored = tokens.refreshToken();
      if (!stored) {
        auth.logout();
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
          next(req.clone({ setHeaders: { Authorization: `Bearer ${accessToken}` } })),
        ),
      );
    }),
  );
};
