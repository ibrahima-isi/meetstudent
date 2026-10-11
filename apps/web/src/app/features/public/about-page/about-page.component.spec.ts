import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { AboutPageComponent } from './about-page.component';

const settings = {
  contactEmail: 'c@example.sn',
  aboutFr: 'Premier paragraphe.\n\nSecond paragraphe,\nsur deux lignes.\n\n\n<b>Troisième</b>',
  aboutEn: 'Only one.',
};

describe('AboutPageComponent', () => {
  let fixture: ComponentFixture<AboutPageComponent>;
  let backend: HttpTestingController;
  let active: ReturnType<typeof signal<'fr' | 'en'>>;

  const el = () => fixture.nativeElement as HTMLElement;
  const paragraphs = () => Array.from(el().querySelectorAll('[data-about] p')).map((p) => p.textContent);
  const settle = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };

  beforeEach(async () => {
    active = signal<'fr' | 'en'>('fr');
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco(translocoOptions),
        { provide: LocaleService, useValue: { active } },
      ],
    });
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AboutPageComponent);
    await settle();
  });

  afterEach(() => backend.verify());

  it('has a title and shows the text of the language being read, one paragraph per block', async () => {
    expect(el().querySelector('h1')?.textContent).toContain('À propos');
    backend.expectOne('/api/v1/site-settings').flush(settings);
    await settle();

    expect(paragraphs()).toEqual(['Premier paragraphe.', 'Second paragraphe,\nsur deux lignes.', '<b>Troisième</b>']);
  });

  it('never interprets the text as HTML', async () => {
    backend.expectOne('/api/v1/site-settings').flush(settings);
    await settle();

    expect(el().querySelector('[data-about] b')).toBeNull();
  });

  it('follows the language without asking the API again', async () => {
    backend.expectOne('/api/v1/site-settings').flush(settings);
    await settle();

    active.set('en');
    await settle();

    expect(paragraphs()).toEqual(['Only one.']);
  });

  it('shows the error state with a retry when the settings cannot be read, never a made-up text', async () => {
    backend.expectOne('/api/v1/site-settings').flush(null, { status: 500, statusText: 'err' });
    await settle();
    expect(el().querySelector('[role=alert]')).toBeTruthy();
    expect(paragraphs()).toEqual([]);

    (el().querySelector('[role=alert] button') as HTMLButtonElement).click();
    backend.expectOne('/api/v1/site-settings').flush(settings);
    await settle();
    expect(paragraphs().length).toBe(3);
  });
});
