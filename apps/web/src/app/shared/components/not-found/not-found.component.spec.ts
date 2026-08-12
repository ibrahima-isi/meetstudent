import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { translocoOptions } from '@i18n/transloco.config';
import { NotFoundComponent } from './not-found.component';

describe('NotFoundComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotFoundComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideTransloco(translocoOptions),
      ],
    }).compileComponents();
  });

  it('links home through the active locale rather than a bare slash', async () => {
    const transloco = TestBed.inject(TranslocoService);
    await transloco.load('fr').toPromise();

    const fixture = TestBed.createComponent(NotFoundComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    const link = fixture.nativeElement.querySelector('a');
    expect(link.getAttribute('href')).toBe('/fr');
  });
});
