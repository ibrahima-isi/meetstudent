import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  const authenticated = signal(false);

  function run(url: string) {
    return TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );
  }

  beforeEach(() => {
    authenticated.set(false);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: TokenService, useValue: { isAuthenticated: authenticated } },
        { provide: LocaleService, useValue: { active: signal('en') } },
      ],
    });
  });

  it('allows an authenticated visitor', () => {
    authenticated.set(true);

    expect(run('/en/home')).toBeTrue();
  });

  it('redirects an anonymous visitor to the active locale login with a returnUrl', () => {
    const result = run('/en/schools/42') as UrlTree;

    expect(result instanceof UrlTree).toBeTrue();
    expect(TestBed.inject(Router).serializeUrl(result)).toBe(
      '/en/login?returnUrl=%2Fen%2Fschools%2F42',
    );
  });
});
