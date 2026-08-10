import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { inject, Injectable, PLATFORM_ID, REQUEST, Signal, signal } from '@angular/core';
import { Translation, TranslocoService } from '@jsverse/transloco';
import { Observable } from 'rxjs';
import {
  DEFAULT_LOCALE,
  isLocale,
  Locale,
  LOCALE_COOKIE,
  negotiateFromAcceptLanguage,
  readLocaleCookie,
} from '@i18n/locale';

/** One year. A language preference is not a credential. */
const COOKIE_MAX_AGE_SECONDS = 31_536_000;

@Injectable({ providedIn: 'root' })
export class LocaleService {
  private readonly transloco = inject(TranslocoService);
  private readonly document = inject(DOCUMENT);

  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  /**
   * The header source on the server, and nothing more. `@angular/ssr` provides
   * it only under `RenderMode.Server`; prerendering, build-time route
   * extraction and `RenderMode.Client` all resolve it to null. A null request
   * therefore means "no headers to read", never "we are in a browser" — the
   * platform is what says that.
   */
  private readonly request = inject(REQUEST, { optional: true });

  private readonly activeLocale = signal<Locale>(DEFAULT_LOCALE);

  /** Read-only: the locale only moves through `use()`, which also loads it. */
  readonly active: Signal<Locale> = this.activeLocale.asReadonly();

  /**
   * URL prefix, then an explicit past choice, then what the client asked for,
   * then French. The URL wins so a shared link keeps the language it was
   * copied in, whatever the recipient's own preference.
   */
  negotiate(fromUrl?: string | null): Locale {
    if (isLocale(fromUrl)) {
      return fromUrl;
    }

    const remembered = readLocaleCookie(this.cookieHeader());
    if (remembered) {
      return remembered;
    }

    return negotiateFromAcceptLanguage(this.acceptLanguage()) ?? DEFAULT_LOCALE;
  }

  /**
   * Activates a locale and returns its load, so a caller can wait for the
   * translation to actually be in. `setActiveLang` alone only pushes to a
   * subject: a route guard that did not block on the load would let SSR
   * serialise untranslated HTML, then flash on hydration.
   *
   * The verdict is left to the caller — a `CanActivateFn` composes this as
   * `use(locale).pipe(map(() => true))`, because the same guard also has to
   * return a `UrlTree` on the branch where the URL segment is not a locale.
   */
  use(locale: Locale): Observable<Translation> {
    this.transloco.setActiveLang(locale);
    this.activeLocale.set(locale);

    return this.transloco.load(locale);
  }

  /**
   * Call only on an explicit user choice — never on a negotiated guess. A no-op
   * off the browser: there is no cookie jar to write to, and Angular's SSR
   * document throws on `cookie`.
   */
  remember(locale: Locale): void {
    if (!this.isBrowser) {
      return;
    }

    this.document.cookie =
      `${LOCALE_COOKIE}=${locale}; path=/; max-age=${COOKIE_MAX_AGE_SECONDS}; samesite=lax`;
  }

  private cookieHeader(): string | null {
    if (this.isBrowser) {
      return this.document.cookie;
    }

    return this.request?.headers.get('cookie') ?? null;
  }

  private acceptLanguage(): string | null {
    if (!this.isBrowser) {
      return this.request?.headers.get('accept-language') ?? null;
    }

    const navigator = this.document.defaultView?.navigator;
    if (!navigator) {
      return null;
    }

    // `languages` is an empty array, not null, when the browser has no ordered
    // list — so this cannot be a `??` chain or the real preference is lost.
    const { languages, language } = navigator;

    return languages?.length ? languages.join(',') : (language ?? null);
  }
}
