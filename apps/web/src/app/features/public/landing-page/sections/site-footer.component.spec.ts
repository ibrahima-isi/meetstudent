import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { routes } from '../../../../app.routes';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { SiteFooterComponent } from './site-footer.component';

describe('SiteFooterComponent', () => {
  let fixture: ComponentFixture<SiteFooterComponent>;

  const root = () => fixture.nativeElement as HTMLElement;
  const hrefs = () =>
    Array.from(root().querySelectorAll('a')).map((a) => a.getAttribute('href'));

  async function render(lang: 'fr' | 'en', now?: Date) {
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    if (now) {
      // Mock the clock only around construction, never while Transloco loads.
      jasmine.clock().install();
      jasmine.clock().mockDate(now);
    }
    try {
      fixture = TestBed.createComponent(SiteFooterComponent);
    } finally {
      if (now) {
        jasmine.clock().uninstall();
      }
    }
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideRouter([]), provideTransloco(translocoOptions)],
    });
  });

  afterEach(() => {
    jasmine.clock().uninstall();
  });

  it('is a footer landmark', async () => {
    await render('fr');
    expect(root().querySelectorAll('footer').length).toBe(1);
    expect(root().querySelector('footer')!.getAttribute('role')).toBe('contentinfo');
  });

  it('links the brand to the language home', async () => {
    await render('fr');
    const brand = root().querySelector<HTMLAnchorElement>('footer a[data-testid="footer-brand"]')!;
    expect(brand).not.toBeNull();
    expect(brand.getAttribute('href')).toBe('/fr');
    expect(brand.textContent).toContain('MeetStudent');
  });

  it('links to schools, login and register in French', async () => {
    await render('fr');
    expect(hrefs()).toContain('/fr/schools');
    expect(hrefs()).toContain('/fr/login');
    expect(hrefs()).toContain('/fr/register');
  });

  it('offers all four translated product destinations in French', async () => {
    await render('fr');
    const links = Array.from(root().querySelectorAll<HTMLAnchorElement>('nav[aria-label="Produit"] a'));
    expect(links.map((a) => [a.textContent?.trim(), a.getAttribute('href')])).toEqual([
      ['Établissements', '/fr/schools'],
      ['Formations', '/fr/programs'],
      ['Cours', '/fr/courses'],
      ['Accréditations', '/fr/accreditations'],
    ]);
  });

  it('updates all product labels and destinations when the visitor switches to English', async () => {
    await render('fr');
    await firstValueFrom(TestBed.inject(LocaleService).use('en'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const links = Array.from(root().querySelectorAll<HTMLAnchorElement>('nav[aria-label="Product"] a'));
    expect(links.map((a) => [a.textContent?.trim(), a.getAttribute('href')])).toEqual([
      ['Schools', '/en/schools'],
      ['Programmes', '/en/programs'],
      ['Courses', '/en/courses'],
      ['Accreditations', '/en/accreditations'],
    ]);
  });

  it('keeps every product link focusable with a touch target at least 44 pixels in each dimension', async () => {
    await render('fr');
    for (const link of root().querySelectorAll<HTMLAnchorElement>('nav[aria-label="Produit"] a')) {
      link.focus();
      expect(document.activeElement).withContext(link.outerHTML).toBe(link);
      expect(link.tabIndex).toBe(0);
      expect(link.getBoundingClientRect().height).withContext(link.outerHTML).toBeGreaterThanOrEqual(44);
      expect(link.getBoundingClientRect().width).withContext(link.outerHTML).toBeGreaterThanOrEqual(44);
    }
  });

  it('links in English after switching language', async () => {
    await render('en');
    expect(hrefs()).toEqual(
      jasmine.arrayContaining(['/en', '/en/schools', '/en/login', '/en/register']),
    );
    expect(hrefs().some((h) => h?.startsWith('/fr'))).toBeFalse();
  });

  describe('account links as buttons', () => {
    const link = (path: string) =>
      root().querySelector<HTMLAnchorElement>(`footer nav a[href$="/${path}"]`)!;

    it('renders register as a primary button and login as a secondary button', async () => {
      await render('fr');
      expect(link('register').classList).toContain('btn');
      expect(link('register').classList).toContain('btn-primary');
      expect(link('login').classList).toContain('btn');
      expect(link('login').classList).toContain('btn-secondary');
    });

    it('uses the default button size', async () => {
      await render('fr');
      expect(link('register').classList).not.toContain('btn-lg');
      expect(link('login').classList).not.toContain('btn-lg');
    });

    it('keeps localized real links, not role=button', async () => {
      await render('en');
      for (const path of ['login', 'register']) {
        const a = link(path);
        expect(a.tagName).toBe('A');
        expect(a.getAttribute('href')).toBe(`/en/${path}`);
        expect(a.hasAttribute('role')).toBeFalse();
      }
    });
  });

  it('has only internal links', async () => {
    await render('fr');
    const links = Array.from(root().querySelectorAll('a'));
    expect(links.length).toBeGreaterThan(0);
    for (const a of links) {
      expect(a.getAttribute('href')).withContext(a.outerHTML).toMatch(/^\/(?!\/)/);
      expect(a.hasAttribute('target')).withContext(a.outerHTML).toBeFalse();
    }
  });

  it('shows the current year', async () => {
    await render('fr', new Date('2030-05-01T12:00:00Z'));
    expect(root().textContent).toContain('2030');
  });

  it('shows the rights text and tagline in French', async () => {
    await render('fr');
    expect(root().textContent).toContain('Tous droits réservés.');
    expect(root().textContent).toContain("Trouvez l'école qui vous correspond.");
  });

  it('shows the rights text and tagline in English', async () => {
    await render('en');
    expect(root().textContent).toContain('All rights reserved.');
    expect(root().textContent).toContain('Find the school that suits you.');
  });

  it('gives each nav landmark a distinct accessible name', async () => {
    await render('fr');
    const navs = Array.from(root().querySelectorAll('footer nav'));
    expect(navs.length).toBe(2);
    expect(navs.map((n) => n.getAttribute('aria-label'))).toEqual(['Produit', 'Compte']);
  });

  it('has no h1, so it never competes with the page title', async () => {
    await render('fr');
    expect(root().querySelector('footer')).not.toBeNull();
    expect(root().querySelectorAll('h1').length).toBe(0);
  });
});


describe('Footer product journeys through the public routes', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  const root = () => harness.fixture.nativeElement as HTMLElement;
  const settle = async () => {
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(), provideRouter(routes),
        provideHttpClient(), provideHttpClientTesting(), provideTransloco(translocoOptions),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  for (const lang of ['fr', 'en'] as const) {
    it(`opens each public catalogue from the ${lang} footer and requests real catalogue data`, async () => {
      harness = await RouterTestingHarness.create(`/${lang}/about`);
      http.expectOne('/api/v1/site-settings').flush({
        contactEmail: 'contact@example.test', aboutFr: 'À propos', aboutEn: 'About',
      });
      await settle();
      expect(root().querySelectorAll('footer').length).toBe(1);

      for (const resource of ['schools', 'programs', 'courses', 'accreditations']) {
        const link = root().querySelector<HTMLAnchorElement>(`footer a[href="/${lang}/${resource}"]`);
        expect(link).withContext(resource).not.toBeNull();
        if (!link) continue;
        link.click();
        await settle();
        expect(TestBed.inject(Router).url).toBe(`/${lang}/${resource}`);
        expect(root().querySelector(`app-${resource}-page`)).withContext(resource).not.toBeNull();
        const request = http.expectOne((r) => r.url === `/api/v1/${resource}`);
        expect(request.request.method).toBe('GET');
        request.flush({ content: [], last: true, totalElements: 0 });
        await settle();
        expect(root().querySelector('app-error-state')).toBeNull();
        expect(root().querySelectorAll('footer').length).toBe(1);
        expect(root().querySelector(`footer a.btn-secondary[href="/${lang}/login"]`)).not.toBeNull();
        expect(root().querySelector(`footer a.btn-primary[href="/${lang}/register"]`)).not.toBeNull();
      }

      await harness.navigateByUrl(`/${lang}/contact`);
      http.expectOne('/api/v1/site-settings').flush({
        contactEmail: 'contact@example.test', aboutFr: 'À propos', aboutEn: 'About',
      });
      await settle();
      expect(root().querySelectorAll('footer').length).toBe(1);
      expect(root().querySelectorAll('footer nav:first-of-type a').length).toBe(4);
    });
  }
});
