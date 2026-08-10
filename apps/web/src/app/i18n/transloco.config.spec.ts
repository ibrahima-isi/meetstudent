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
