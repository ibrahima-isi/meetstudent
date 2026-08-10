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
    // No `prodMode`: Transloco 8 never reads it. The missing-key warning is
    // gated by the ambient `ngDevMode` global, which the build already strips.
    missingHandler: {
      logMissingKey: true,
      useFallbackTranslation: true,
      allowEmpty: false,
    },
  },
  loader: TranslationLoader,
};
