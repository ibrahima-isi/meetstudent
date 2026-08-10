# PR 1 — i18n foundation, implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up Transloco and a locale-negotiation service in `apps/web`, with no template touched and the existing 58 specs passing unmodified.

**Architecture:** Three layers, smallest first. Pure functions in `locale.ts` own the parsing rules (cookie, `Accept-Language`) and are unit-testable with no Angular at all. `TranslationLoader` resolves a locale to a bundled JSON module through a dynamic `import()`, so SSR needs no network. `LocaleService` composes the two and is the only piece that knows whether it runs on the server or in the browser. `app.config.ts` wires them together.

**Tech Stack:** Angular 20.3 (standalone, zoneless, SSR), `@jsverse/transloco` 8.4.0, Karma + Jasmine, TypeScript 5.9.

**Design spec:** [`2026-08-10-i18n-and-routing-design.md`](./2026-08-10-i18n-and-routing-design.md). This plan implements PR 1 of its five-PR breakdown.

## Global Constraints

- **Branch:** `feat/i18n-foundation`, cut from `main`. Never commit on `main`.
- **TDD, observed:** write the failing test, run it, *see it fail*, then implement. Do not write implementation first.
- **Default locale is `fr`.** Supported: `['fr', 'en']`. French is the source language.
- **`@jsverse/transloco@8.4.0`** — peer `@angular/core >=16.0.0`, verified compatible with Angular 20.3.
- **`noPropertyAccessFromIndexSignature: true`** in `tsconfig.json`. Index-signature types must be read with brackets: `translation['common']`, never `translation.common`. This *will* fail the build otherwise.
- **`strict: true`**, `isolatedModules: true`, `module: "preserve"`.
- **Angular style rules** (`apps/web/.claude/CLAUDE.md`): `inject()` not constructor injection; signals for state; `providedIn: 'root'`; no NgModules; no `standalone: true` in decorators.
- **Every `TestBed` in this repo provides `provideZonelessChangeDetection()`.** Follow that; the app is zoneless.
- **Test naming:** `*.spec.ts`. Anything else never runs.
- **Commits:** Conventional Commits (`feat:`, `test:`, `chore:`), signed. If a commit fails with `1Password: failed to fill whole buffer`, a 1Password approval is pending — ask the user to approve, then retry. Never use `--no-gpg-sign`.
- **The 58 existing `it()` across 12 spec files must stay green at every commit.** No existing spec is modified by this PR.
- **No template, no component, and no existing service is touched by this PR.**

---

### Task 1: Locale constants and parsing rules

Pure functions, no Angular. Everything downstream depends on these names.

**Files:**
- Create: `apps/web/src/app/i18n/locale.ts`
- Test: `apps/web/src/app/i18n/locale.spec.ts`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `SUPPORTED_LOCALES: readonly ['fr', 'en']`
  - `type Locale = 'fr' | 'en'`
  - `DEFAULT_LOCALE: Locale` (value `'fr'`)
  - `LOCALE_COOKIE: string` (value `'meetstudent_locale'`)
  - `isLocale(value: string | null | undefined): value is Locale`
  - `readLocaleCookie(cookieHeader: string | null | undefined): Locale | null`
  - `negotiateFromAcceptLanguage(header: string | null | undefined): Locale | null`

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/app/i18n/locale.spec.ts`:

```ts
import {
  DEFAULT_LOCALE,
  isLocale,
  negotiateFromAcceptLanguage,
  readLocaleCookie,
} from './locale';

describe('locale', () => {
  it('defaults to French', () => {
    expect(DEFAULT_LOCALE).toBe('fr');
  });

  describe('isLocale', () => {
    it('accepts supported locales', () => {
      expect(isLocale('fr')).toBe(true);
      expect(isLocale('en')).toBe(true);
    });

    it('rejects anything else', () => {
      expect(isLocale('es')).toBe(false);
      expect(isLocale('')).toBe(false);
      expect(isLocale(null)).toBe(false);
      expect(isLocale(undefined)).toBe(false);
    });
  });

  describe('readLocaleCookie', () => {
    it('finds the locale among other cookies', () => {
      expect(readLocaleCookie('theme=dark; meetstudent_locale=en; sid=abc')).toBe('en');
    });

    it('tolerates surrounding whitespace', () => {
      expect(readLocaleCookie('  meetstudent_locale = fr  ')).toBe('fr');
    });

    it('returns null when the cookie is absent', () => {
      expect(readLocaleCookie('theme=dark')).toBeNull();
      expect(readLocaleCookie('')).toBeNull();
      expect(readLocaleCookie(null)).toBeNull();
    });

    it('returns null when the stored value is not a supported locale', () => {
      expect(readLocaleCookie('meetstudent_locale=de')).toBeNull();
    });

    it('does not match a cookie whose name merely ends with the same text', () => {
      expect(readLocaleCookie('other_meetstudent_locale=en')).toBeNull();
    });
  });

  describe('negotiateFromAcceptLanguage', () => {
    it('picks the highest-quality supported language', () => {
      expect(negotiateFromAcceptLanguage('de;q=1.0, en;q=0.8, fr;q=0.9')).toBe('fr');
    });

    it('treats a missing q as 1.0 and keeps document order on ties', () => {
      expect(negotiateFromAcceptLanguage('en, fr')).toBe('en');
    });

    it('matches on the primary subtag', () => {
      expect(negotiateFromAcceptLanguage('fr-SN,fr;q=0.9')).toBe('fr');
      expect(negotiateFromAcceptLanguage('en-GB')).toBe('en');
    });

    it('ignores languages explicitly refused with q=0', () => {
      expect(negotiateFromAcceptLanguage('fr;q=0, en;q=0.5')).toBe('en');
    });

    it('returns null when nothing is supported', () => {
      expect(negotiateFromAcceptLanguage('de, es')).toBeNull();
      expect(negotiateFromAcceptLanguage('')).toBeNull();
      expect(negotiateFromAcceptLanguage(null)).toBeNull();
    });
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

```bash
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless --include='**/i18n/locale.spec.ts'
```

Expected: the build fails to resolve `./locale` — `Could not resolve "./locale"`. That is the red.

- [ ] **Step 3: Write the minimal implementation**

Create `apps/web/src/app/i18n/locale.ts`:

```ts
/**
 * French is the source language: the UI was authored in French, so `fr` is both
 * the default and the fallback. English is a translation.
 */
export const SUPPORTED_LOCALES = ['fr', 'en'] as const;

export type Locale = (typeof SUPPORTED_LOCALES)[number];

export const DEFAULT_LOCALE: Locale = 'fr';

/** Written only on an explicit user choice, never on a negotiated guess. */
export const LOCALE_COOKIE = 'meetstudent_locale';

export function isLocale(value: string | null | undefined): value is Locale {
  return value != null && (SUPPORTED_LOCALES as readonly string[]).includes(value);
}

/**
 * Reads the remembered locale out of a raw `Cookie` header (server) or
 * `document.cookie` (browser) — both use the same `a=1; b=2` shape.
 */
export function readLocaleCookie(cookieHeader: string | null | undefined): Locale | null {
  if (!cookieHeader) {
    return null;
  }

  for (const pair of cookieHeader.split(';')) {
    const separator = pair.indexOf('=');
    if (separator === -1) {
      continue;
    }

    if (pair.slice(0, separator).trim() !== LOCALE_COOKIE) {
      continue;
    }

    const value = decodeURIComponent(pair.slice(separator + 1).trim());
    return isLocale(value) ? value : null;
  }

  return null;
}

/**
 * Picks the best supported locale from an `Accept-Language` header, honouring
 * quality values. Returns null when the client asked for nothing we serve —
 * the caller decides the fallback.
 */
export function negotiateFromAcceptLanguage(
  header: string | null | undefined,
): Locale | null {
  if (!header) {
    return null;
  }

  const ranked = header
    .split(',')
    .map((part) => {
      const [tag, ...params] = part.trim().split(';');
      const quality = params
        .map((param) => param.trim())
        .find((param) => param.startsWith('q='));
      const parsed = quality ? Number.parseFloat(quality.slice(2)) : 1;

      return {
        tag: tag.trim().toLowerCase(),
        // A malformed q is treated as a refusal rather than a preference.
        quality: Number.isNaN(parsed) ? 0 : parsed,
      };
    })
    .filter((entry) => entry.tag !== '' && entry.quality > 0)
    // Array.prototype.sort is stable, so equal qualities keep header order.
    .sort((a, b) => b.quality - a.quality);

  for (const { tag } of ranked) {
    const primary = tag.split('-')[0];
    if (isLocale(primary)) {
      return primary;
    }
  }

  return null;
}
```

- [ ] **Step 4: Run the test and watch it pass**

```bash
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless --include='**/i18n/locale.spec.ts'
```

Expected: PASS, 13 specs.

- [ ] **Step 5: Commit**

```bash
git add apps/web/src/app/i18n/locale.ts apps/web/src/app/i18n/locale.spec.ts
git commit -m "feat(web): add locale constants and negotiation rules"
```

---

### Task 2: Bundled translation loader

The loader resolves a locale to a JSON module through a dynamic `import()`. No HTTP, so it behaves identically under SSR and in the browser, and each locale stays its own lazy chunk.

**Files:**
- Modify: `apps/web/tsconfig.json` (add `resolveJsonModule`, add the `@i18n/*` path)
- Create: `apps/web/src/app/i18n/fr.json`
- Create: `apps/web/src/app/i18n/en.json`
- Create: `apps/web/src/app/i18n/translation.loader.ts`
- Test: `apps/web/src/app/i18n/translation.loader.spec.ts`

**Interfaces:**
- Consumes: `Locale`, `DEFAULT_LOCALE`, `isLocale` from Task 1.
- Produces: `class TranslationLoader implements TranslocoLoader` with `getTranslation(lang: string): Promise<Translation>`.

- [ ] **Step 1: Install the dependency**

```bash
cd apps/web && npm install @jsverse/transloco@8.4.0
```

Expected: `package.json` and `package-lock.json` change; no peer-dependency warning about `@angular/core`.

- [ ] **Step 2: Enable JSON imports and the path alias**

In `apps/web/tsconfig.json`, inside `compilerOptions`, add `"resolveJsonModule": true` next to `"isolatedModules": true`, and add the alias to `paths`:

```jsonc
"resolveJsonModule": true,
"paths": {
  "@services/*": ["src/app/services/*"],
  "@models/*": ["src/app/models/*"],
  "@shared/*": ["src/app/shared/*"],
  "@data/*": ["src/app/data/*"],
  "@i18n/*": ["src/app/i18n/*"]
}
```

`tsconfig.app.json` and `tsconfig.spec.json` both `extends` this file, so nothing else needs changing.

- [ ] **Step 3: Write the failing test**

Create `apps/web/src/app/i18n/translation.loader.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslationLoader } from './translation.loader';

describe('TranslationLoader', () => {
  let loader: TranslationLoader;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [TranslationLoader, provideZonelessChangeDetection()],
    });
    loader = TestBed.inject(TranslationLoader);
  });

  it('resolves the French bundle', async () => {
    const translation = await loader.getTranslation('fr');

    expect(translation['common']['retry']).toBe('Réessayer');
  });

  it('resolves the English bundle', async () => {
    const translation = await loader.getTranslation('en');

    expect(translation['common']['retry']).toBe('Retry');
  });

  it('leaves language names untranslated in both bundles', async () => {
    const french = await loader.getTranslation('fr');
    const english = await loader.getTranslation('en');

    // Endonyms: a language picker shows "Français" and "English" whatever the
    // active locale, so both bundles carry the same values.
    expect(french['language']).toEqual(english['language']);
  });

  it('falls back to the default locale for an unknown language', async () => {
    const translation = await loader.getTranslation('de');

    expect(translation['common']['retry']).toBe('Réessayer');
  });
});
```

- [ ] **Step 4: Run the test and watch it fail**

```bash
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless --include='**/i18n/translation.loader.spec.ts'
```

Expected: FAIL — `Could not resolve "./translation.loader"`.

- [ ] **Step 5: Create the translation files**

Create `apps/web/src/app/i18n/fr.json`:

```json
{
  "common": {
    "appName": "MeetStudent",
    "retry": "Réessayer"
  },
  "language": {
    "fr": "Français",
    "en": "English"
  }
}
```

Create `apps/web/src/app/i18n/en.json`:

```json
{
  "common": {
    "appName": "MeetStudent",
    "retry": "Retry"
  },
  "language": {
    "fr": "Français",
    "en": "English"
  }
}
```

- [ ] **Step 6: Write the minimal implementation**

Create `apps/web/src/app/i18n/translation.loader.ts`:

```ts
import { Injectable } from '@angular/core';
import { Translation, TranslocoLoader } from '@jsverse/transloco';
import { DEFAULT_LOCALE, isLocale, Locale } from './locale';

/**
 * Translations are bundled, not fetched. An HTTP loader would need an absolute
 * URL under SSR, where a relative one has no base; a dynamic import is correct
 * on both platforms and keeps each locale in its own lazy chunk.
 */
const TRANSLATIONS: Record<Locale, () => Promise<{ default: Translation }>> = {
  fr: () => import('./fr.json'),
  en: () => import('./en.json'),
};

@Injectable({ providedIn: 'root' })
export class TranslationLoader implements TranslocoLoader {
  async getTranslation(lang: string): Promise<Translation> {
    const locale = isLocale(lang) ? lang : DEFAULT_LOCALE;
    const loaded = await TRANSLATIONS[locale]();

    return loaded.default;
  }
}
```

- [ ] **Step 7: Run the test and watch it pass**

```bash
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless --include='**/i18n/translation.loader.spec.ts'
```

Expected: PASS, 4 specs.

- [ ] **Step 8: Commit**

```bash
git add apps/web/package.json apps/web/package-lock.json apps/web/tsconfig.json apps/web/src/app/i18n/
git commit -m "feat(web): load bundled translations through a dynamic import"
```

---

### Task 3: LocaleService

The only piece that knows whether it is running on the server or in the browser. On the server it reads headers off `REQUEST`; in the browser it reads `document`.

**Files:**
- Create: `apps/web/src/app/services/locale.service.ts`
- Test: `apps/web/src/app/services/locale.service.spec.ts`

**Interfaces:**
- Consumes: everything from Task 1; `TranslocoService` from `@jsverse/transloco`.
- Produces:
  - `LocaleService.active: Signal<Locale>`
  - `LocaleService.negotiate(fromUrl?: string | null): Locale`
  - `LocaleService.use(locale: Locale): void`
  - `LocaleService.remember(locale: Locale): void`

  PR 2's `localeGuard` calls `negotiate()` then `use()`. PR 3's language switcher calls `remember()` then navigates.

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/app/services/locale.service.spec.ts`:

```ts
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
    function serverRequest(headers: Record<string, string>) {
      return { provide: REQUEST, useValue: new Request('http://localhost/', { headers }) };
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
```

- [ ] **Step 2: Run the test and watch it fail**

```bash
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless --include='**/locale.service.spec.ts'
```

Expected: FAIL — `Could not resolve "./locale.service"`.

- [ ] **Step 3: Write the minimal implementation**

Create `apps/web/src/app/services/locale.service.ts`:

```ts
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
```

- [ ] **Step 4: Run the test and watch it pass**

```bash
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless --include='**/locale.service.spec.ts'
```

Expected: PASS, 9 specs.

- [ ] **Step 5: Commit**

```bash
git add apps/web/src/app/services/locale.service.ts apps/web/src/app/services/locale.service.spec.ts
git commit -m "feat(web): negotiate the active locale on both platforms"
```

---

### Task 4: Wire Transloco into the application config

The Transloco options live in their own file rather than inline in `app.config.ts`. That keeps i18n configuration with the rest of the i18n code, and it lets the test exercise the *real* options object without dragging `provideRouter` and `provideClientHydration` into a `TestBed`, where they are noise at best.

**Files:**
- Create: `apps/web/src/app/i18n/transloco.config.ts`
- Modify: `apps/web/src/app/app.config.ts`
- Test: `apps/web/src/app/i18n/transloco.config.spec.ts`

`app.config.server.ts` is **not** modified: it calls `mergeApplicationConfig(appConfig, serverConfig)`, so these providers already reach SSR.

**Interfaces:**
- Consumes: `TranslationLoader` (Task 2), `SUPPORTED_LOCALES` / `DEFAULT_LOCALE` (Task 1).
- Produces: `translocoOptions: TranslocoOptions`, and a working `TranslocoService` injectable anywhere in the app. PR 2 depends on this.

- [ ] **Step 1: Write the failing test**

Create `apps/web/src/app/i18n/transloco.config.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from './transloco.config';
import { TranslationLoader } from './translation.loader';

describe('translocoOptions', () => {
  it('declares French as the default and the fallback', () => {
    expect(translocoOptions.config.defaultLang).toBe('fr');
    expect(translocoOptions.config.fallbackLang).toBe('fr');
    expect(translocoOptions.config.availableLangs).toEqual(['fr', 'en']);
    expect(translocoOptions.loader).toBe(TranslationLoader);
  });

  it('serves the bundled French translations when installed', async () => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideTransloco(translocoOptions)],
    });
    const transloco = TestBed.inject(TranslocoService);

    await firstValueFrom(transloco.load('fr'));

    expect(transloco.getActiveLang()).toBe('fr');
    expect(transloco.translate('common.retry')).toBe('Réessayer');
  });
});
```

- [ ] **Step 2: Run the test and watch it fail**

```bash
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless --include='**/transloco.config.spec.ts'
```

Expected: FAIL — `Could not resolve "./transloco.config"`.

- [ ] **Step 3: Write the minimal implementation**

Create `apps/web/src/app/i18n/transloco.config.ts`:

```ts
import { isDevMode } from '@angular/core';
import { TranslocoOptions } from '@jsverse/transloco';
import { DEFAULT_LOCALE, SUPPORTED_LOCALES } from './locale';
import { TranslationLoader } from './translation.loader';

export const translocoOptions: TranslocoOptions = {
  config: {
    availableLangs: [...SUPPORTED_LOCALES],
    defaultLang: DEFAULT_LOCALE,
    // French is the source language, so a key missing from en.json falls back
    // to real French copy rather than rendering the raw key.
    fallbackLang: DEFAULT_LOCALE,
    reRenderOnLangChange: true,
    prodMode: !isDevMode(),
    missingHandler: {
      logMissingKey: true,
      useFallbackTranslation: true,
      allowEmpty: false,
    },
  },
  loader: TranslationLoader,
};
```

Then in `apps/web/src/app/app.config.ts`, add the imports and the provider:

```ts
import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors, withFetch } from '@angular/common/http';
import { provideTransloco } from '@jsverse/transloco';

import { routes } from './app.routes';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { jwtInterceptor } from './interceptors/jwt.interceptor';
import { translocoOptions } from '@i18n/transloco.config';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([jwtInterceptor]), withFetch()),
    provideClientHydration(withEventReplay()),
    provideTransloco(translocoOptions),
  ],
};
```

- [ ] **Step 4: Run the test and watch it pass**

```bash
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless --include='**/transloco.config.spec.ts'
```

Expected: PASS, 2 specs.

- [ ] **Step 5: Run the whole suite and confirm nothing regressed**

```bash
cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless
```

Expected: **86 specs, 0 failures** — the 58 that existed before, plus 13 (Task 1), 4 (Task 2), 9 (Task 3) and 2 (Task 4). No existing spec is removed or edited. If any of the original 58 fails, stop: this PR is not allowed to change existing behaviour.

- [ ] **Step 6: Confirm the production build still succeeds**

```bash
cd apps/web && npm run build
```

Expected: build succeeds. The initial-bundle budget warning about 521 kB may still appear — **that is pre-existing and out of scope**; lazy loading in PR 2 addresses it. Any *new* error is in scope.

- [ ] **Step 7: Verify on the real stack**

```bash
docker compose up --build
```

Then open `http://localhost:4200`. Expected: the app renders exactly as before — this PR touches no template, so a visible change means something is wrong.

- [ ] **Step 8: Commit and open the PR**

```bash
git add apps/web/src/app/app.config.ts apps/web/src/app/i18n/transloco.config.ts apps/web/src/app/i18n/transloco.config.spec.ts
git commit -m "feat(web): provide Transloco with the bundled loader"
git push -u origin feat/i18n-foundation
gh pr create --base main --title "feat(web): i18n foundation" --body "Implements PR 1 of apps/web/docs/plans/2026-08-10-i18n-and-routing-design.md. Transloco, the bundled translation loader and locale negotiation. No template touched; the 58 pre-existing specs pass unmodified."
```

---

## Definition of done

- `npm test -- --no-watch --browsers=ChromeHeadless` reports 86 specs, 0 failures.
- `npm run build` succeeds with no new error.
- `docker compose up --build` serves a visually unchanged app.
- No template, component, or pre-existing service was modified.

**Files created (10):** in `src/app/i18n/` — `locale.ts`, `locale.spec.ts`, `fr.json`, `en.json`, `translation.loader.ts`, `translation.loader.spec.ts`, `transloco.config.ts`, `transloco.config.spec.ts`; in `src/app/services/` — `locale.service.ts`, `locale.service.spec.ts`.

**Pre-existing files modified (4):** `tsconfig.json`, `package.json`, `package-lock.json`, `src/app/app.config.ts`.

That exceeds the three-file rule in the root `CLAUDE.md`, so present this list to the user for approval before the first edit. The design spec's PR 1 row already anticipated it.

## What this PR deliberately does not do

- No `:lang` route, no guard, no `<router-outlet>` — PR 2.
- No language switcher UI — PR 3. `remember()` exists and is tested, but nothing calls it yet.
- No string extracted from any template — PRs 4 and 5.
- `translateSignal()` from Transloco 8.4 is the right tool for this zoneless app and is used from PR 4 onwards. It is not used here because no template is translated yet.
