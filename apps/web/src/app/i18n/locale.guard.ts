import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { isLocale } from '@i18n/locale';
import { LocaleService } from '@services/locale.service';

/**
 * `:lang` matches any first segment, so this guard is what separates a locale
 * from an ordinary path. A recognised locale is activated, and the guard waits
 * on its translations — returning `true` early would let SSR serialise
 * untranslated HTML and flash on hydration.
 *
 * Anything else is treated as a path that lost its prefix and is redirected to
 * the negotiated locale, so un-prefixed links keep working instead of 404-ing.
 */
export const localeGuard: CanActivateFn = (route, state) => {
  const locales = inject(LocaleService);
  const segment = route.paramMap.get('lang');

  if (isLocale(segment)) {
    return locales.use(segment).pipe(map(() => true));
  }

  return inject(Router).parseUrl(`/${locales.negotiate(null)}${state.url}`);
};
