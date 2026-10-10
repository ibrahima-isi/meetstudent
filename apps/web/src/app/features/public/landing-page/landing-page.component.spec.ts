import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { TranslocoService } from '@jsverse/transloco';
import { firstValueFrom, of, throwError } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { ProgramService } from '@services/program.service';
import { SchoolService } from '@services/school.service';
import { LandingPageComponent } from './landing-page.component';

describe('LandingPageComponent', () => {
  let fixture: ComponentFixture<LandingPageComponent>;
  const root = () => fixture.nativeElement as HTMLElement;

  const SECTIONS = [
    'app-landing-hero',
    'app-key-figures',
    'app-how-it-works',
    'app-featured-schools',
    'app-testimonials',
    'app-cta-band',
    'app-site-footer',
  ];

  async function create(schools: unknown, programs: unknown) {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: SchoolService, useValue: { getSchools: () => schools } },
        { provide: ProgramService, useValue: { getPrograms: () => programs } },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use('fr'));
    fixture = TestBed.createComponent(LandingPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  describe('with the API up', () => {
    beforeEach(() =>
      create(
        of({ content: [], totalElements: 12 }),
        of({ content: [], totalElements: 34 }),
      ),
    );

    it('renders the seven sections in order', () => {
      const all = Array.from(root().querySelectorAll(SECTIONS.join(',')));

      expect(all.map((e) => e.tagName.toLowerCase())).toEqual(SECTIONS);
    });

    it('has exactly one h1 and no heading level skipped', () => {
      expect(root().querySelectorAll('h1').length).toBe(1);

      const levels = Array.from(root().querySelectorAll('h1,h2,h3,h4')).map((h) => Number(h.tagName[1]));
      levels.forEach((level, i) => expect(level - (levels[i - 1] ?? 1)).toBeLessThanOrEqual(1));
      expect(root().querySelector('app-key-figures h2')).toBeTruthy();
    });

    it('has a footer landmark with the contentinfo role', () => {
      expect(root().querySelector('app-site-footer footer[role="contentinfo"]')).toBeTruthy();
    });

    it('relies on the global scroll-padding to clear the navbar', () => {
      expect(parseFloat(getComputedStyle(document.documentElement).scrollPaddingTop)).toBeGreaterThanOrEqual(80);
    });

    describe('meta description', () => {
      const metas = () => document.head.querySelectorAll('meta[name="description"]');
      const FR = "Comparez les établissements, formations et avis d'étudiants et d'experts au Sénégal, et trouvez l'école qui vous correspond.";
      const EN = 'Compare schools, programmes and reviews from students and experts in Senegal, and find the school that suits you.';
      afterEach(() => metas().forEach((m) => m.remove()));

      it('is set in French, once', () => {
        expect(metas().length).toBe(1);
        expect(metas()[0].getAttribute('content')).toBe(FR);
      });

      it('follows the language switch without adding a tag', async () => {
        const transloco = TestBed.inject(TranslocoService);
        await firstValueFrom(transloco.load('en'));
        transloco.setActiveLang('en');
        await fixture.whenStable();

        expect(metas().length).toBe(1);
        expect(metas()[0].getAttribute('content')).toBe(EN);
      });
    });

    it('exposes the anchors the navbar scrolls to', () => {
      expect(root().querySelector('#how-it-works')).toBeTruthy();
      expect(root().querySelector('#reviews')).toBeTruthy();
    });
  });

  describe('with the API down', () => {
    beforeEach(() =>
      create(
        throwError(() => new Error('down')),
        throwError(() => new Error('down')),
      ),
    );

    it('keeps every static section', () => {
      for (const selector of ['app-landing-hero', 'app-how-it-works', 'app-testimonials', 'app-cta-band', 'app-site-footer']) {
        expect(root().querySelector(selector)).withContext(selector).toBeTruthy();
      }
      expect(root().querySelectorAll('h1').length).toBe(1);
    });

    it('hides the key figures and shows the retry state for featured schools', () => {
      expect(root().querySelector('app-key-figures section')).toBeNull();
      expect(root().querySelector('app-featured-schools app-error-state')).toBeTruthy();
    });
  });
});
