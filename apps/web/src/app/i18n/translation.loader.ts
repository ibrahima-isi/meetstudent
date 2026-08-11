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
