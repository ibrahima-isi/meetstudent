import { firstValueFrom } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, RESPONSE_INIT } from '@angular/core';
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
    await firstValueFrom(transloco.load('fr'));

    const fixture = TestBed.createComponent(NotFoundComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    const link = fixture.nativeElement.querySelector('a');
    expect(link.getAttribute('href')).toBe('/fr');
  });

  it('answers HTTP 404 when rendered on the server', () => {
    const responseInit: ResponseInit = { status: 200 };
    TestBed.configureTestingModule({ providers: [{ provide: RESPONSE_INIT, useValue: responseInit }] });

    TestBed.createComponent(NotFoundComponent);

    expect(responseInit.status).toBe(404);
  });

  it('still renders the 404 page when there is no server response to patch', async () => {
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));

    const fixture = TestBed.createComponent(NotFoundComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('404');
  });
});
