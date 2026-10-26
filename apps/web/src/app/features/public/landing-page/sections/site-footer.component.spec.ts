import { provideZonelessChangeDetection, signal } from '@angular/core';
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
import { TokenService } from '@services/token.service';
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
    // Start signed out whatever an earlier spec left behind.
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(), provideRouter([]), provideHttpClient(),
        provideHttpClientTesting(), provideTransloco(translocoOptions),
      ],
    });
  });

  afterEach(() => {
    jasmine.clock().uninstall();
    localStorage.clear();
    document.querySelector('[data-test-server-marker]')?.remove();
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

  describe('account column by session state', () => {
    const account = () => root().querySelector<HTMLElement>('footer nav:last-of-type');
    const signIn = () => {
      const tokens = TestBed.inject(TokenService);
      tokens.setTokens('access', 'refresh');
    };

    it('shows profile and log out, and no login or register, to a signed-in user', async () => {
      signIn();
      await render('fr');
      expect(account()!.querySelector('a[href="/fr/login"]')).toBeNull();
      expect(account()!.querySelector('a[href="/fr/register"]')).toBeNull();
      expect(account()!.querySelector('a[href="/fr/profile"]')!.textContent).toContain('Profil');
      expect(account()!.querySelector('button')!.textContent).toContain('Se déconnecter');
    });

    it('translates the signed-in links in English', async () => {
      signIn();
      await render('en');
      expect(account()!.querySelector('a[href="/en/profile"]')!.textContent).toContain('Profile');
      expect(account()!.querySelector('button')!.textContent).toContain('Log out');
    });

    it('ends the session and goes home when the user logs out', async () => {
      signIn();
      await render('fr');
      const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
      account()!.querySelector('button')!.click();
      fixture.detectChanges();
      expect(TestBed.inject(TokenService).isAuthenticated()).toBeFalse();
      expect(navigate).toHaveBeenCalledWith(['/', 'fr']);
      expect(account()!.querySelector('a[href="/fr/login"]')).not.toBeNull();
    });

    it('renders no account column before hydration, then the right one', async () => {
      signIn();
      const marker = document.createElement('div');
      marker.setAttribute('ng-server-context', 'ssr');
      marker.setAttribute('data-test-server-marker', '');
      document.body.appendChild(marker);
      await firstValueFrom(TestBed.inject(LocaleService).use('fr'));
      fixture = TestBed.createComponent(SiteFooterComponent);
      // afterNextRender runs inside the first change detection in a browser, so the
      // server-side first render is observed through the state that drives the template.
      const hydrated = (fixture.componentInstance as unknown as { hydrated: () => boolean }).hydrated;
      expect(hydrated()).toBeFalse();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(hydrated()).toBeTrue();
      expect(account()!.querySelector('a[href="/fr/profile"]')).not.toBeNull();
      expect(account()!.querySelector('a[href="/fr/login"]')).toBeNull();
    });

    it('shows login and register to a visitor after hydration', async () => {
      const marker = document.createElement('div');
      marker.setAttribute('ng-server-context', 'ssr');
      marker.setAttribute('data-test-server-marker', '');
      document.body.appendChild(marker);
      await firstValueFrom(TestBed.inject(LocaleService).use('fr'));
      fixture = TestBed.createComponent(SiteFooterComponent);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(account()!.querySelector('a[href="/fr/login"]')).not.toBeNull();
      expect(account()!.querySelector('a[href="/fr/register"]')).not.toBeNull();
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
        // These journeys describe an anonymous visitor, independently of persisted sessions.
        { provide: TokenService, useValue: { isAuthenticated: signal(false), user: signal(null) } },
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
