import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, REQUEST, inject } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { DEFAULT_LOCALE, Locale, SUPPORTED_LOCALES, isLocale } from './locale';

/**
 * Publishes the `<link rel="alternate" hreflang="...">` set that tells a
 * crawler the same page exists in both languages, plus the `x-default` that
 * says which one to serve when it has no preference.
 *
 * Separate from `LocaleService` on purpose: that one answers *which* locale is
 * active, this one advertises the alternatives. They change together but they
 * are not the same question.
 */
@Injectable({ providedIn: 'root' })
export class AlternateLinksService {
  private readonly document = inject(DOCUMENT);
  private readonly router = inject(Router);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly request = inject(REQUEST, { optional: true });

  /**
   * Follows the router: the path is what changes, the locales never do.
   *
   * Nothing is emitted up front. `NavigationEnd` fires for the first navigation
   * too — including on the server, where SSR performs it before serialising —
   * and at initialisation `router.url` is still `/`, which would advertise the
   * wrong page.
   */
  start(): void {
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => this.update(event.urlAfterRedirects));
  }

  update(url: string): void {
    const origin = this.origin();
    if (!origin) {
      // Prerender and RenderMode.Client both resolve REQUEST to null. A
      // relative hreflang is ignored by every crawler, so emitting nothing is
      // better than emitting something that does not count.
      return;
    }

    const suffix = this.withoutLocale(url);
    const head = this.document.head;

    for (const link of Array.from(head.querySelectorAll('link[rel="alternate"]'))) {
      link.remove();
    }

    for (const locale of SUPPORTED_LOCALES) {
      head.appendChild(this.link(locale, `${origin}/${locale}${suffix}`));
    }
    head.appendChild(
      this.link('x-default', `${origin}/${DEFAULT_LOCALE}${suffix}`),
    );
  }

  private link(hreflang: Locale | 'x-default', href: string): HTMLLinkElement {
    const link = this.document.createElement('link');
    link.setAttribute('rel', 'alternate');
    link.setAttribute('hreflang', hreflang);
    link.setAttribute('href', href);
    return link;
  }

  /**
   * `/fr/schools/7?tab=x#y` → `/schools/7?tab=x#y`, so the same suffix can be
   * hung off either locale. A first segment that is not a locale is a path that
   * lost its prefix — the guard redirects those, but guessing wrong here would
   * publish two URLs that 404.
   */
  private withoutLocale(url: string): string {
    const [first, ...rest] = url.split('/').slice(1);
    const head = first?.split(/[?#]/)[0];

    if (!isLocale(head)) {
      return url === '/' ? '' : url;
    }

    const remainder = first.slice(head.length);
    const tail = rest.length > 0 ? `/${rest.join('/')}` : '';
    return `${remainder}${tail}`;
  }

  private origin(): string | null {
    if (this.isBrowser) {
      return this.document.location.origin;
    }

    const url = this.request?.url;
    return url ? new URL(url).origin : null;
  }
}
