import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslocoService, provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from './transloco.config';

/** Keys read by templates of more than one page: removing them for one page must not break the others. */
describe('shared translation keys', () => {
  const expected: [string, string, string][] = [
    ['schools.viewDetails', 'Voir les détails', 'View details'],
  ];

  for (const [lang, index] of [['fr', 1], ['en', 2]] as const) {
    it(`keeps them in ${lang}`, async () => {
      TestBed.configureTestingModule({
        providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), provideTransloco(translocoOptions)],
      });
      const transloco = TestBed.inject(TranslocoService);
      await firstValueFrom(transloco.load(lang));
      for (const row of expected) {
        expect(transloco.translate(row[0], {}, lang)).withContext(row[0]).toBe(row[index]);
      }
    });
  }
});
