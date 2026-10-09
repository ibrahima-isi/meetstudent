import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { Router, provideRouter } from '@angular/router';
import { LocaleService } from '@services/locale.service';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from '@services/auth.service';
import { TokenService } from '@services/token.service';
import { environment } from '../../environments/environment';
import { jwtInterceptor } from './jwt.interceptor';
import { refreshInterceptor } from './refresh.interceptor';

describe('refreshInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let tokens: TokenService;
  let auth: AuthService;
  let router: Router;
  let navigate: jasmine.Spy;
  let activeLocale: ReturnType<typeof signal<'fr' | 'en'>>;

  const refreshUrl = `${environment.apiUrl}/auth/refresh`;

  beforeEach(() => {
    localStorage.clear();
    activeLocale = signal<'fr' | 'en'>('fr');
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: LocaleService, useValue: { active: activeLocale } },
        provideHttpClient(withInterceptors([jwtInterceptor, refreshInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    tokens = TestBed.inject(TokenService);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    navigate = spyOn(router, 'navigate').and.resolveTo(true);
    tokens.setTokens('old-access', 'old-refresh');
  });

  afterEach(() => {
    backend.verify();
    localStorage.clear();
  });

  it('refreshes once on a 401 and retries the request with the new token', () => {
    let result: unknown;
    http.get('/api/data').subscribe((r) => (result = r));

    backend.expectOne('/api/data').flush(null, { status: 401, statusText: 'Unauthorized' });
    const refresh = backend.expectOne(refreshUrl);
    expect(refresh.request.body).toEqual({ refreshToken: 'old-refresh' });
    refresh.flush({ accessToken: 'new-access', refreshToken: 'new-refresh' });

    const retry = backend.expectOne('/api/data');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new-access');
    retry.flush({ ok: true });

    expect(result).toEqual({ ok: true });
  });

  it('triggers a single refresh for a burst of concurrent 401s', () => {
    const results: unknown[] = [];
    http.get('/api/a').subscribe((r) => results.push(r));
    http.get('/api/b').subscribe((r) => results.push(r));

    backend.expectOne('/api/a').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/b').flush(null, { status: 401, statusText: 'Unauthorized' });

    backend.expectOne(refreshUrl).flush({ accessToken: 'n', refreshToken: 'r' });

    backend.expectOne('/api/a').flush('a');
    backend.expectOne('/api/b').flush('b');
    expect(results).toEqual(['a', 'b']);
  });

  it('does not retry a second time when the retried request is still 401', () => {
    let error: unknown;
    http.get('/api/data').subscribe({ error: (e) => (error = e) });

    backend.expectOne('/api/data').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne(refreshUrl).flush({ accessToken: 'n', refreshToken: 'r' });
    backend.expectOne('/api/data').flush(null, { status: 401, statusText: 'Unauthorized' });

    backend.expectNone(refreshUrl);
    expect(error).toBeTruthy();
  });

  it('logs out and surfaces the error when the refresh fails', () => {
    spyOn(auth, 'logout').and.callThrough();
    let error: unknown;
    http.get('/api/data').subscribe({ error: (e) => (error = e) });

    backend.expectOne('/api/data').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne(refreshUrl).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.logout).toHaveBeenCalledTimes(1);
    expect(tokens.token()).toBeNull();
    expect(error).toBeTruthy();
  });

  it('logs out without calling the API when there is no refresh token', () => {
    tokens.clear();
    let error: unknown;
    http.get('/api/data').subscribe({ error: (e) => (error = e) });

    backend.expectOne('/api/data').flush(null, { status: 401, statusText: 'Unauthorized' });

    backend.expectNone(refreshUrl);
    expect(error).toBeTruthy();
  });

  it('never tries to refresh for the auth endpoints themselves', () => {
    const errors: unknown[] = [];
    http.post(`${environment.apiUrl}/auth`, {}).subscribe({ error: (e) => errors.push(e) });
    http.post(refreshUrl, {}).subscribe({ error: (e) => errors.push(e) });

    backend
      .expectOne(`${environment.apiUrl}/auth`)
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne(refreshUrl).flush(null, { status: 401, statusText: 'Unauthorized' });

    backend.expectNone(refreshUrl);
    expect(errors.length).toBe(2);
  });

  it('passes non-401 errors through untouched', () => {
    let status = 0;
    http.get('/api/data').subscribe({ error: (e) => (status = e.status) });

    backend.expectOne('/api/data').flush(null, { status: 500, statusText: 'Server Error' });

    expect(status).toBe(500);
  });

  describe('after a failed refresh', () => {
    function failRefresh(): void {
      http.get('/api/data').subscribe({ error: () => undefined });
      backend.expectOne('/api/data').flush(null, { status: 401, statusText: 'Unauthorized' });
      backend.expectOne(refreshUrl).flush(null, { status: 401, statusText: 'Unauthorized' });
    }

    it('sends the visitor to the login of the active locale, remembering the page', () => {
      spyOnProperty(router, 'url').and.returnValue('/fr/schools/7');

      failRefresh();

      expect(tokens.token()).toBeNull();
      expect(navigate).toHaveBeenCalledOnceWith(['/', 'fr', 'login'], {
        queryParams: { returnUrl: '/fr/schools/7' },
      });
    });

    it('uses the locale being read', () => {
      activeLocale.set('en');
      spyOnProperty(router, 'url').and.returnValue('/en/profile');

      failRefresh();

      expect(navigate.calls.mostRecent().args[0]).toEqual(['/', 'en', 'login']);
    });

    it('does not remember the login screen itself as the page to return to', () => {
      spyOnProperty(router, 'url').and.returnValue('/fr/login?registered=1');

      failRefresh();

      expect(navigate).toHaveBeenCalledOnceWith(['/', 'fr', 'login'], undefined);
    });

    it('navigates once for a burst of concurrent 401s', () => {
      http.get('/api/a').subscribe({ error: () => undefined });
      http.get('/api/b').subscribe({ error: () => undefined });
      backend.expectOne('/api/a').flush(null, { status: 401, statusText: 'Unauthorized' });
      backend.expectOne('/api/b').flush(null, { status: 401, statusText: 'Unauthorized' });

      backend.expectOne(refreshUrl).flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(navigate).toHaveBeenCalledTimes(1);
    });

    it('does not navigate when the refresh succeeds', () => {
      http.get('/api/data').subscribe();
      backend.expectOne('/api/data').flush(null, { status: 401, statusText: 'Unauthorized' });
      backend.expectOne(refreshUrl).flush({ accessToken: 'n', refreshToken: 'r' });
      backend.expectOne('/api/data').flush({});

      expect(navigate).not.toHaveBeenCalled();
    });

    it('does not navigate for a visitor who was never signed in', () => {
      tokens.clear();
      http.get('/api/data').subscribe({ error: () => undefined });

      backend.expectOne('/api/data').flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(navigate).not.toHaveBeenCalled();
    });
  });
});
