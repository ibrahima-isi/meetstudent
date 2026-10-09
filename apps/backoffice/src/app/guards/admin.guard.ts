import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '@services/auth.service';
import { TokenService } from '@services/token.service';

/**
 * Only ROLE_ADMIN gets in. Anonymous visitors go to the login with the wanted
 * URL in `returnUrl`; a signed-in non-admin is logged out and sent to the
 * login with `reason=forbidden`, which the login page explains.
 */
export const adminGuard: CanActivateFn = (_route, state) => {
  const tokens = inject(TokenService);
  const router = inject(Router);

  if (!tokens.isAuthenticated()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  if (!tokens.isAdmin()) {
    inject(AuthService).logout();
    return router.createUrlTree(['/login'], { queryParams: { reason: 'forbidden' } });
  }
  return true;
};
