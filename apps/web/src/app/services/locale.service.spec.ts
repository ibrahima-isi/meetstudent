import { TestBed } from '@angular/core/testing';
import { DOCUMENT } from '@angular/common';
import { provideZonelessChangeDetection, REQUEST } from '@angular/core';
import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';
import { LocaleService } from './locale.service';

/**
 * A stand-in for `document` so the tests never read the real browser's
 * `navigator.language` — which would make them depend on the CI machine's
 * locale.
 */
function fakeDocument(options: { cookie?: string; languages?: string[] } = {}) {
  return {
    cookie: options.cookie ?? '',
    defaultView: options.languages
      ? { navigator: { languages: options.languages, language: options.languages[0] } }
      : null,
  };
}

function configure(providers: unknown[]) {
  TestBed.configureTestingModule({
    imports: [
      TranslocoTestingModule.forRoot({
        langs: { fr: {}, en: {} },
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
        serverRequest({ cookie: 'meetstudent_locale=en', 'accept-language': 'en' }),
        { provide: DOCUMENT, useValue: fakeDocument() },
      ]);

      expect(service.negotiate('fr')).toBe('fr');
    });

    it('prefers a remembered cookie over Accept-Language', () => {
      const service = configure([
        serverRequest({ cookie: 'meetstudent_locale=en', 'accept-language': 'fr' }),
        { provide: DOCUMENT, useValue: fakeDocument() },
      ]);

      expect(service.negotiate(null)).toBe('en');
    });

    it('falls back to Accept-Language when nothing is remembered', () => {
      const service = configure([
        serverRequest({ 'accept-language': 'en-GB,en;q=0.9' }),
        { provide: DOCUMENT, useValue: fakeDocument() },
      ]);

      expect(service.negotiate(null)).toBe('en');
    });

    it('falls back to French when the client asks for nothing we serve', () => {
      const service = configure([
        serverRequest({ 'accept-language': 'de,es;q=0.8' }),
        { provide: DOCUMENT, useValue: fakeDocument() },
      ]);

      expect(service.negotiate(null)).toBe('fr');
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
  });

  describe('use', () => {
    it('sets the active language on Transloco and on the signal', () => {
      const service = configure([{ provide: DOCUMENT, useValue: fakeDocument() }]);
      const transloco = TestBed.inject(TranslocoService);

      service.use('en');

      expect(transloco.getActiveLang()).toBe('en');
      expect(service.active()).toBe('en');
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
  });
});
