import { TestBed } from '@angular/core/testing';
import { DOCUMENT } from '@angular/common';
import {
  PLATFORM_ID,
  provideZonelessChangeDetection,
  REQUEST,
  WritableSignal,
} from '@angular/core';
import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { Locale } from '@i18n/locale';
import { LocaleService } from './locale.service';

/**
 * A stand-in for `document` so the tests never read the real browser's
 * `navigator.language` — which would make them depend on the CI machine's
 * locale.
 */
function fakeDocument(
  options: { cookie?: string; languages?: string[]; language?: string } = {},
) {
  const navigator =
    options.languages || options.language
      ? {
          languages: options.languages,
          language: options.language ?? options.languages?.[0],
        }
      : null;

  return {
    cookie: options.cookie ?? '',
    defaultView: navigator ? { navigator } : null,
  };
}

/**
 * Angular's SSR document is domino's, whose `cookie` accessor throws
 * `NotYetImplemented`. Reaching for it on the server is a 500, not a fallback.
 */
function ssrDocument() {
  return {
    get cookie(): string {
      throw new Error('NotYetImplemented');
    },
    set cookie(_value: string) {
      throw new Error('NotYetImplemented');
    },
    defaultView: null,
  };
}

const serverPlatform = { provide: PLATFORM_ID, useValue: 'server' };

function configure(providers: unknown[]) {
  TestBed.configureTestingModule({
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { fr: { greeting: 'Bonjour' }, en: { greeting: 'Hello' } },
        translocoConfig: { availableLangs: ['fr', 'en'], defaultLang: 'fr' },
      }),
    ],
    providers: [provideZonelessChangeDetection(), ...(providers as never[])],
  });

  return TestBed.inject(LocaleService);
}

describe('LocaleService', () => {
  describe('on the server', () => {
    // Not `new Request(url, { headers })`: the Fetch standard forbids scripts
    // from setting a `Cookie` header on an outgoing request, so a real,
    // browser-native `Request` silently drops it and this suite runs in a
    // real Chrome via Karma. Angular's actual SSR `REQUEST` is built by Node
    // from real inbound headers, never through that script-facing gate, so
    // this minimal stand-in — exposing only the `headers.get()` this service
    // calls — models production faithfully where the real `Request` cannot.
    function serverRequest(headers: Record<string, string>) {
      const byLowerName = new Map(
        Object.entries(headers).map(([name, value]) => [name.toLowerCase(), value]),
      );
      return {
        provide: REQUEST,
        useValue: { headers: { get: (name: string) => byLowerName.get(name.toLowerCase()) ?? null } },
      };
    }

    it('prefers the locale taken from the URL over everything else', () => {
      const service = configure([
        serverPlatform,
        serverRequest({ cookie: 'meetstudent_locale=en', 'accept-language': 'en' }),
        { provide: DOCUMENT, useValue: ssrDocument() },
      ]);

      expect(service.negotiate('fr')).toBe('fr');
    });

    it('prefers a remembered cookie over Accept-Language', () => {
      const service = configure([
        serverPlatform,
        serverRequest({ cookie: 'meetstudent_locale=en', 'accept-language': 'fr' }),
        { provide: DOCUMENT, useValue: ssrDocument() },
      ]);

      expect(service.negotiate(null)).toBe('en');
    });

    it('falls back to Accept-Language when nothing is remembered', () => {
      const service = configure([
        serverPlatform,
        serverRequest({ 'accept-language': 'en-GB,en;q=0.9' }),
        { provide: DOCUMENT, useValue: ssrDocument() },
      ]);

      expect(service.negotiate(null)).toBe('en');
    });

    it('falls back to French when the client asks for nothing we serve', () => {
      const service = configure([
        serverPlatform,
        serverRequest({ 'accept-language': 'de,es;q=0.8' }),
        { provide: DOCUMENT, useValue: ssrDocument() },
      ]);

      expect(service.negotiate(null)).toBe('fr');
    });
  });

  describe('on the server with no request', () => {
    // `REQUEST` is provided only under `RenderMode.Server`. Prerendering,
    // build-time route extraction and `RenderMode.Client` all resolve it to
    // null, so a null request means "no headers here" — never "we are in a
    // browser". Getting that wrong reaches for `document.cookie` on the SSR
    // document and turns a fallback into a 500.
    it('falls back to French rather than touching document.cookie', () => {
      const service = configure([
        serverPlatform,
        { provide: DOCUMENT, useValue: ssrDocument() },
      ]);

      expect(service.negotiate(null)).toBe('fr');
    });

    it('still honours a locale taken from the URL', () => {
      const service = configure([
        serverPlatform,
        { provide: DOCUMENT, useValue: ssrDocument() },
      ]);

      expect(service.negotiate('en')).toBe('en');
    });
  });

  describe('in the browser', () => {
    it('reads the remembered locale from document.cookie', () => {
      const service = configure([
        { provide: DOCUMENT, useValue: fakeDocument({ cookie: 'meetstudent_locale=en' }) },
      ]);

      expect(service.negotiate(null)).toBe('en');
    });

    it('falls back to the navigator languages', () => {
      const service = configure([
        { provide: DOCUMENT, useValue: fakeDocument({ languages: ['en-US', 'fr'] }) },
      ]);

      expect(service.negotiate(null)).toBe('en');
    });

    it('falls back to French when the navigator offers nothing supported', () => {
      const service = configure([
        { provide: DOCUMENT, useValue: fakeDocument({ languages: ['de-DE'] }) },
      ]);

      expect(service.negotiate(null)).toBe('fr');
    });

    it('falls back to navigator.language when languages is empty', () => {
      // An empty array is not nullish, so `languages ?? language` never fires:
      // joining it yields '' and the real preference is silently discarded.
      const service = configure([
        { provide: DOCUMENT, useValue: fakeDocument({ languages: [], language: 'en-GB' }) },
      ]);

      expect(service.negotiate(null)).toBe('en');
    });
  });

  describe('when the first URL segment is not a locale', () => {
    // PR 2's `:lang` route is greedy on purpose: `/login` binds `lang` to
    // 'login'. The guard turns that into a redirect, so this is the input
    // negotiate() sees most often — it must simply fall through.
    it('falls through to the remembered cookie', () => {
      const service = configure([
        {
          provide: DOCUMENT,
          useValue: fakeDocument({ cookie: 'meetstudent_locale=en', languages: ['de-DE'] }),
        },
      ]);

      expect(service.negotiate('login')).toBe('en');
    });

    it('falls through to the navigator when nothing is remembered', () => {
      const service = configure([
        { provide: DOCUMENT, useValue: fakeDocument({ languages: ['en-GB', 'fr'] }) },
      ]);

      expect(service.negotiate('login')).toBe('en');
    });

    it('falls all the way through to French', () => {
      const service = configure([
        { provide: DOCUMENT, useValue: fakeDocument({ languages: ['de-DE'] }) },
      ]);

      expect(service.negotiate('login')).toBe('fr');
    });
  });

  describe('use', () => {
    it('sets the active language on Transloco and on the signal', () => {
      const service = configure([{ provide: DOCUMENT, useValue: fakeDocument() }]);
      const transloco = TestBed.inject(TranslocoService);

      service.use('en');

      expect(transloco.getActiveLang()).toBe('en');
      expect(service.active()).toBe('en');
    });

    it('returns the load, so a guard can wait for the translation to be in', async () => {
      // `setActiveLang` only pushes to a subject. Without the load to block on,
      // PR 2's guard activates the route and SSR serialises before the JSON
      // chunk resolves — untranslated HTML, then a flash on hydration.
      const service = configure([{ provide: DOCUMENT, useValue: fakeDocument() }]);
      const transloco = TestBed.inject(TranslocoService);

      const loading = service.use('en');
      expect(transloco.translate('greeting')).toBe('greeting');

      const translation = await firstValueFrom(loading);

      expect(translation['greeting']).toBe('Hello');
      expect(transloco.translate('greeting')).toBe('Hello');
    });
  });

  describe('active', () => {
    it('is read-only — the locale only moves through use()', () => {
      const service = configure([{ provide: DOCUMENT, useValue: fakeDocument() }]);

      // A settable signal would let a consumer change the displayed locale
      // without loading its translation or telling Transloco.
      const settable = service.active as Partial<WritableSignal<Locale>>;

      expect(settable.set).toBeUndefined();
      expect(settable.update).toBeUndefined();
    });
  });

  describe('remember', () => {
    it('writes the choice to a cookie', () => {
      const document = fakeDocument();
      const service = configure([{ provide: DOCUMENT, useValue: document }]);

      service.remember('en');

      expect(document.cookie).toContain('meetstudent_locale=en');
      expect(document.cookie).toContain('path=/');
    });

    it('is a no-op off the browser, where there is no cookie jar to write to', () => {
      const service = configure([
        serverPlatform,
        { provide: DOCUMENT, useValue: ssrDocument() },
      ]);

      expect(() => service.remember('en')).not.toThrow();
    });
  });
});
