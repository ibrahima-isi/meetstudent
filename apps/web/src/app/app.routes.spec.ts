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
import { routes } from './app.routes';

describe('routes', () => {
  let harness: RouterTestingHarness;

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

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco(translocoOptions),
        { provide: LocaleService, useValue: locale },
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

  it('renders an unknown path under a valid locale as the 404', async () => {
    await harness.navigateByUrl('/fr/nope');

    expect(harness.routeNativeElement?.textContent).toContain('404');
  });

  it('keeps both locales addressable for the same screen', async () => {
    await harness.navigateByUrl('/en/login');
    expect(TestBed.inject(Router).url).toBe('/en/login');

    await harness.navigateByUrl('/fr/login');
    expect(TestBed.inject(Router).url).toBe('/fr/login');
  });
});
