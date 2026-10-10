import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, withComponentInputBinding, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom, of } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { routes } from './app.routes';

describe('routes', () => {
  let harness: RouterTestingHarness;
  let authenticated: ReturnType<typeof signal<boolean>>;
  let currentUser: ReturnType<typeof signal<unknown>>;
  const navbar = () => (harness.fixture.nativeElement as HTMLElement).querySelector('app-dock-navbar');

  beforeEach(async () => {
    // LocaleService is stubbed on purpose. The real one negotiates from
    // `navigator.languages` when no cookie is set, which is the CI machine's
    // locale — Chrome Headless reports en-US, so a real service would send `/`
    // to `/en` here and this suite would pass or fail depending on the machine.
    // Negotiation itself is covered by locale.service.spec.ts and
    // locale.guard.spec.ts; this suite is about the route table.
    const locale = {
      negotiate: () => 'fr' as const,
      use: () => of({}),
      remember: () => undefined,
      active: signal('fr' as const),
    };

    authenticated = signal(false);
    currentUser = signal<unknown>(null);

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco(translocoOptions),
        { provide: LocaleService, useValue: locale },
        {
          provide: TokenService,
          useValue: { isAuthenticated: authenticated, user: currentUser, setUser: () => undefined, clear: () => undefined },
        },
      ],
    });

    // The stub above returns `of({})` instead of loading, so nothing would put
    // a translation in the cache and the 404's `*transloco` would render an
    // empty view. In the app the guard does this by awaiting `use()`; here the
    // load is done by hand so the route under test is what is being observed.
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));

    harness = await RouterTestingHarness.create();
  });

  it('redirects the bare root to the negotiated locale', async () => {
    await harness.navigateByUrl('/');

    expect(TestBed.inject(Router).url).toBe('/fr');
  });

  it('redirects an un-prefixed path, keeping the path', async () => {
    await harness.navigateByUrl('/login');

    expect(TestBed.inject(Router).url).toBe('/fr/login');
  });

  it('serves the landing page at the locale root', async () => {
    await harness.navigateByUrl('/fr');

    expect(TestBed.inject(Router).url).toBe('/fr');
  });

  it('renders the landing, not the catalogue, at the locale root inside the shell', async () => {
    await harness.navigateByUrl('/fr');

    const host = harness.fixture.nativeElement as HTMLElement;
    expect(host.querySelector('app-landing-page')).toBeTruthy();
    expect(host.querySelector('app-schools-page')).toBeNull();
    expect(navbar()).toBeTruthy();
  });

  it('resolves the #reviews fragment to the landing', async () => {
    await harness.navigateByUrl('/fr#reviews');

    expect(TestBed.inject(Router).url).toBe('/fr#reviews');
    expect((harness.fixture.nativeElement as HTMLElement).querySelector('app-landing-page')).toBeTruthy();
    expect(harness.routeNativeElement?.textContent).not.toContain('404');
  });

  it('renders an unknown path under a valid locale as the 404', async () => {
    await harness.navigateByUrl('/fr/nope');

    expect(harness.routeNativeElement?.textContent).toContain('404');
  });

  it('has no /verify screen: registration goes straight to login', async () => {
    await harness.navigateByUrl('/fr/verify');

    expect(harness.routeNativeElement?.textContent).toContain('404');
  });

  it('keeps both locales addressable for the same screen', async () => {
    await harness.navigateByUrl('/en/login');
    expect(TestBed.inject(Router).url).toBe('/en/login');

    await harness.navigateByUrl('/fr/login');
    expect(TestBed.inject(Router).url).toBe('/fr/login');
  });

  it('frames the locale root with the navbar', async () => {
    await harness.navigateByUrl('/fr');

    expect(navbar()).toBeTruthy();
  });

  it('frames the login screen with the navbar', async () => {
    await harness.navigateByUrl('/fr/login');

    expect(navbar()).toBeTruthy();
    expect((harness.fixture.nativeElement as HTMLElement).querySelector('app-login-form')).toBeTruthy();
  });

  it('sends an anonymous visitor of a guarded route to the login inside the shell', async () => {
    await harness.navigateByUrl('/fr/home');

    expect(TestBed.inject(Router).url).toContain('/fr/login');
    expect(navbar()).toBeTruthy();
    expect((harness.fixture.nativeElement as HTMLElement).querySelector('app-login-form')).toBeTruthy();
  });

  it('renders the 404 without the navbar', async () => {
    await harness.navigateByUrl('/fr/zzz');

    expect(harness.routeNativeElement?.textContent).toContain('404');
    expect(navbar()).toBeNull();
  });

  it('serves the catalogue at /schools inside the shell', async () => {
    await harness.navigateByUrl('/fr/schools');

    expect(TestBed.inject(Router).url).toBe('/fr/schools');
    expect(navbar()).toBeTruthy();
    expect((harness.fixture.nativeElement as HTMLElement).querySelector('app-schools-page')).toBeTruthy();
  });

  it('still guards a school detail', async () => {
    await harness.navigateByUrl('/fr/schools/7');

    expect(TestBed.inject(Router).url).toContain('/fr/login');
  });

  describe('the signed-in pages', () => {
    const host = () => harness.fixture.nativeElement as HTMLElement;

    beforeEach(() => {
      authenticated.set(true);
      currentUser.set({ id: 3, firstname: 'Awa', lastname: 'Diop', email: 'a@b.sn', role: { name: 'ROLE_STUDENT' } });
    });

    for (const url of ['/fr/home', '/fr/profile', '/fr/schools/1']) {
      it(`renders ${url} inside the shell, under the navbar`, async () => {
        await harness.navigateByUrl(url);

        expect(TestBed.inject(Router).url).toBe(url);
        expect(navbar()).toBeTruthy();
        expect(host().querySelector('main#main')).toBeTruthy();
      });
    }

    it('shows exactly one theme toggle on the home page: the navbar one', async () => {
      await harness.navigateByUrl('/fr/home');

      expect(host().querySelectorAll('app-theme-toggle').length).toBe(1);
      expect(navbar()?.querySelector('app-theme-toggle')).toBeTruthy();
    });

    it('sends an anonymous visitor to the login with the returnUrl of the guarded page', async () => {
      authenticated.set(false);
      await harness.navigateByUrl('/fr/home');

      expect(TestBed.inject(Router).url).toBe('/fr/login?returnUrl=%2Ffr%2Fhome');
    });
  });
});
