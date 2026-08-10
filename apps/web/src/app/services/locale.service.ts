import { DOCUMENT } from '@angular/common';
import { inject, Injectable, REQUEST, signal } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
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

  // Null in the browser — that is how this service knows which platform it is on.
  private readonly request = inject(REQUEST, { optional: true });

  readonly active = signal<Locale>(DEFAULT_LOCALE);

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

  use(locale: Locale): void {
    this.transloco.setActiveLang(locale);
    this.active.set(locale);
  }

  /** Call only on an explicit user choice — never on a negotiated guess. */
  remember(locale: Locale): void {
    this.document.cookie =
      `${LOCALE_COOKIE}=${locale}; path=/; max-age=${COOKIE_MAX_AGE_SECONDS}; samesite=lax`;
  }

  private cookieHeader(): string | null {
    return this.request ? this.request.headers.get('cookie') : this.document.cookie;
  }

  private acceptLanguage(): string | null {
    if (this.request) {
      return this.request.headers.get('accept-language');
    }

    const navigator = this.document.defaultView?.navigator;
    if (!navigator) {
      return null;
    }

    return navigator.languages?.join(',') ?? navigator.language;
  }
}
