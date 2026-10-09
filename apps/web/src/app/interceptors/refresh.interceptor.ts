import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, finalize, shareReplay, switchMap, throwError } from 'rxjs';
import { AuthService } from '@services/auth.service';
import { TokenService } from '@services/token.service';
import { environment } from '../../environments/environment';

const AUTH_URL = `${environment.apiUrl}/auth`;

/** Login and refresh answer 401 for bad credentials; refreshing there would loop. */
const isAuthEndpoint = (req: HttpRequest<unknown>): boolean =>
  req.url === AUTH_URL || req.url.startsWith(`${AUTH_URL}/`);

/** The refresh in flight, shared so a burst of 401s costs one call. */
let refresh$: Observable<string> | null = null;

/**
 * On a 401, refreshes the access token once (concurrent 401s share that single
 * refresh), retries the original request once with the new token, and logs out
 * if the refresh fails. Must come after `jwtInterceptor` in the chain.
 */
export const refreshInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const tokens = inject(TokenService);

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
          auth.logout();
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
