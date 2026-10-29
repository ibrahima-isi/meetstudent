import { HttpErrorResponse, HttpEvent, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { isPlatformBrowser } from '@angular/common';
import { Injector, PLATFORM_ID, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, of, shareReplay, switchMap, throwError } from 'rxjs';
import { AuthService } from '@services/auth.service';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { environment } from '../../environments/environment';

const AUTH_URL = `${environment.apiUrl}/auth`;

/**
 * Login and refresh answer 401 for bad credentials; refreshing there would loop.
 * The password-reset calls are public: a 401 there is never about our session.
 * Everything else under `/auth` (the verification resend, say) is an ordinary
 * authenticated call and is refreshed like any other.
 */
const isAuthEndpoint = (req: HttpRequest<unknown>): boolean =>
  req.url === AUTH_URL ||
  req.url === `${AUTH_URL}/refresh` ||
  req.url.startsWith(`${AUTH_URL}/password-reset/`);

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
    { queryParams: { reason: 'expired', ...(onAuthScreen ? {} : { returnUrl: router.url }) } },
  );
}

/**
 * On a 401, refreshes the access token once (concurrent 401s share that single
 * refresh; if another tab already rotated the session, its tokens are used and no
 * refresh is made), retries the original request once with the new token, and ends the
 * session (logout and redirect to login) if the refresh fails. Must come after
 * `jwtInterceptor` in the chain.
 */
export const refreshInterceptor: HttpInterceptorFn = (req, next) => {
  // A server render has no browser session or storage to refresh or clear.
  if (!isPlatformBrowser(inject(PLATFORM_ID))) return next(req);
  const auth = inject(AuthService);
  const tokens = inject(TokenService);
  const injector = inject(Injector);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || isAuthEndpoint(req)) {
        return throwError(() => error);
      }

      // A refresh already in flight in this tab is joined as is. Otherwise look at what other tabs left in storage first:
      // the server treats a replay of an already rotated refresh token as theft and ends the session everywhere.
      const sentWith = req.headers.get('Authorization');
      const held = tokens.refreshToken();
      if (!refresh$) tokens.syncFromStorage();
      const stored = tokens.refreshToken();
      const current = tokens.token();
      if (!refresh$ && stored && current && (stored !== held || (sentWith !== null && sentWith !== `Bearer ${current}`))) {
        return retryWith(current);
      }
      if (!stored) {
        if (tokens.token()) endSession(auth, injector);
        else auth.logout();
        return throwError(() => error);
      }

      refresh$ ??= auth.refreshToken(stored).pipe(
        switchMap((res) => [res.accessToken]),
        catchError((refreshError: unknown) => {
          // Two tabs may have refreshed together: the other one won and stored its tokens, and the server refused this
          // tab's now stale refresh token. Ending the session here would log out with a token we do not hold any more
          // (deleting the winner's session) and sign the winner tab out too: use the stored tokens instead.
          tokens.syncFromStorage();
          const winnerRefresh = tokens.refreshToken();
          const winnerAccess = tokens.token();
          if (winnerRefresh && winnerAccess && winnerRefresh !== stored) return of(winnerAccess);
          endSession(auth, injector);
          return throwError(() => refreshError);
        }),
        finalize(() => (refresh$ = null)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );

      return refresh$.pipe(switchMap((accessToken) => retryWith(accessToken)));
    }),
  );

  /** The original request once more, with `accessToken`; a second 401 ends the session. */
  function retryWith(accessToken: string): Observable<HttpEvent<unknown>> {
    return next(req.clone({ setHeaders: { Authorization: `Bearer ${accessToken}` } })).pipe(
      catchError((retryError: unknown) => {
        if (retryError instanceof HttpErrorResponse && retryError.status === 401) {
          endSession(auth, injector);
        }
        return throwError(() => retryError);
      }),
    );
  }
};
