import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { firstValueFrom, isObservable } from 'rxjs';
import { localeGuard } from './locale.guard';
import { LocaleService } from '@services/locale.service';

function snapshotFor(lang: string | null, url: string) {
  const route = { paramMap: { get: (key: string) => (key === 'lang' ? lang : null) } };
  return {
    route: route as unknown as ActivatedRouteSnapshot,
    state: { url } as RouterStateSnapshot,
  };
}

describe('localeGuard', () => {
  let localeService: jasmine.SpyObj<LocaleService>;

  function run(lang: string | null, url: string) {
    const { route, state } = snapshotFor(lang, url);
    return TestBed.runInInjectionContext(() => localeGuard(route, state));
  }

  beforeEach(() => {
    localeService = jasmine.createSpyObj<LocaleService>('LocaleService', ['negotiate', 'use']);
    localeService.use.and.returnValue(of({}));
    localeService.negotiate.and.returnValue('fr');

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: LocaleService, useValue: localeService },
      ],
    });
  });

  it('activates a supported locale and waits for its translations', async () => {
    const result = run('en', '/en/home');

    expect(isObservable(result)).toBe(true);
    await expectAsync(firstValueFrom(result as never)).toBeResolvedTo(true);
    expect(localeService.use).toHaveBeenCalledWith('en');
  });

  it('redirects an unknown first segment, preserving the rest of the path', () => {
    const result = run('login', '/login');

    expect(result instanceof UrlTree).toBe(true);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/fr/login');
    expect(localeService.use).not.toHaveBeenCalled();
  });

  it('preserves deeper paths when redirecting', () => {
    const result = run('schools', '/schools/12');

    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/fr/schools/12');
  });

  it('preserves the query string when redirecting', () => {
    const result = run('schools', '/schools/12?tab=programs');

    expect(TestBed.inject(Router).serializeUrl(result as UrlTree))
      .toBe('/fr/schools/12?tab=programs');
  });

  it('negotiates rather than assuming French', () => {
    localeService.negotiate.and.returnValue('en');

    const result = run('login', '/login');

    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/en/login');
  });
});
