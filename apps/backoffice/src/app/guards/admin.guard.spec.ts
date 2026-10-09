import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { adminGuard } from './admin.guard';
import { TokenService } from '@services/token.service';

describe('adminGuard', () => {
  let tokens: TokenService;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    tokens = TestBed.inject(TokenService);
    router = TestBed.inject(Router);
  });

  afterEach(() => localStorage.clear());

  const run = (url = '/schools') =>
    TestBed.runInInjectionContext(() =>
      adminGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );

  const signIn = (roleName: string) => {
    tokens.setTokens('a', 'r');
    tokens.setUser({ id: 1, firstname: 'A', lastname: 'B', email: 'a@x.io', role: { name: roleName } });
  };

  it('lets an admin in', () => {
    signIn('ROLE_ADMIN');
    expect(run()).toBeTrue();
  });

  it('sends an anonymous visitor to login, remembering the wanted url', () => {
    const result = run('/schools') as UrlTree;
    expect(router.serializeUrl(result)).toBe('/login?returnUrl=%2Fschools');
  });

  it('logs out and sends a non-admin to login with a forbidden reason', () => {
    signIn('ROLE_EXPERT');
    const result = run() as UrlTree;

    expect(router.serializeUrl(result)).toBe('/login?reason=forbidden');
    expect(tokens.isAuthenticated()).toBeFalse();
  });
});
