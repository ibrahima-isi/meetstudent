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
