import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';

/**
 * Keeps anonymous visitors off the student screens. They are sent to the login
 * of the locale they are reading, with the URL they wanted in `returnUrl` so
 * the login form can send them back.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(TokenService).isAuthenticated()) {
    return true;
  }

  const locale = inject(LocaleService).active();
  return inject(Router).createUrlTree(['/', locale, 'login'], {
    queryParams: { returnUrl: state.url },
  });
};
