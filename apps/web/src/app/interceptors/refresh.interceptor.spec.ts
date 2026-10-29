import { TestBed } from '@angular/core/testing';
import { PLATFORM_ID, provideZonelessChangeDetection, signal } from '@angular/core';
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

  const logoutUrl = `${environment.apiUrl}/auth/logout`;
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
    // A logout also ends the session on the server (fire-and-forget); answer it before checking for leftovers.
    backend.match(logoutUrl).forEach((req) => req.flush(null, { status: 204, statusText: 'No Content' }));
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
    expect(tokens.token()).toBeNull();
    expect(tokens.refreshToken()).toBeNull();
    expect(TestBed.inject(Router).navigate).toHaveBeenCalled();
    expect(error).toBeTruthy();
  });

  it('logs out and surfaces the error when the refresh fails', () => {
    spyOn(auth, 'logout').and.callThrough();
    let error: unknown;
    http.get('/api/data').subscribe({ error: (e) => (error = e) });

    backend.expectOne('/api/data').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne(refreshUrl).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.logout).toHaveBeenCalledTimes(1);
    expect(backend.expectOne(logoutUrl).request.body).toEqual({ refreshToken: 'old-refresh' });
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

  it('refreshes and retries an authenticated call under /auth, such as the verification resend', () => {
    const resend = `${environment.apiUrl}/auth/email-verification/resend`;
    let result: unknown;
    http.post(resend, {}).subscribe((r) => (result = r));

    backend.expectOne(resend).flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne(refreshUrl).flush({ accessToken: 'new-access', refreshToken: 'new-refresh' });
    const retry = backend.expectOne(resend);
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new-access');
    retry.flush({ status: 'accepted' });

    expect(result).toEqual({ status: 'accepted' });
  });

  it('never refreshes for the public password-reset calls, even with a stale session', () => {
    const errors: unknown[] = [];
    for (const path of ['request', 'confirm']) {
      http.post(`${environment.apiUrl}/auth/password-reset/${path}`, {}).subscribe({ error: (e) => errors.push(e) });
      backend
        .expectOne(`${environment.apiUrl}/auth/password-reset/${path}`)
        .flush(null, { status: 401, statusText: 'Unauthorized' });
    }

    backend.expectNone(refreshUrl);
    expect(errors.length).toBe(2);
  });

  it('passes non-401 errors through untouched', () => {
    let status = 0;
    http.get('/api/data').subscribe({ error: (e) => (status = e.status) });

    backend.expectOne('/api/data').flush(null, { status: 500, statusText: 'Server Error' });

    expect(status).toBe(500);
  });

  describe('when another tab already rotated the session', () => {
    /** Another tab rotated and stored the new pair; this tab has not heard of it yet (no `storage` event delivered). */
    function otherTabRotatedSilently(): void {
      localStorage.setItem('auth_token', 'tab2-access');
      localStorage.setItem('refresh_token', 'tab2-refresh');
    }

    it('retries with the stored tokens instead of replaying the stale refresh token', () => {
      let result: unknown;
      http.get('/api/data').subscribe((r) => (result = r));
      const first = backend.expectOne('/api/data');
      expect(first.request.headers.get('Authorization')).toBe('Bearer old-access');
      otherTabRotatedSilently();
      first.flush(null, { status: 401, statusText: 'Unauthorized' });

      backend.expectNone(refreshUrl);
      const retry = backend.expectOne('/api/data');
      expect(retry.request.headers.get('Authorization')).toBe('Bearer tab2-access');
      retry.flush({ ok: true });

      expect(result).toEqual({ ok: true });
      expect(tokens.refreshToken()).toBe('tab2-refresh');
      expect(tokens.token()).toBe('tab2-access');
      expect(navigate).not.toHaveBeenCalled();
    });

    it('retries without refreshing when the new tokens were already adopted from the storage event', () => {
      let result: unknown;
      http.get('/api/data').subscribe((r) => (result = r));
      const first = backend.expectOne('/api/data');
      otherTabRotatedSilently();
      window.dispatchEvent(new StorageEvent('storage', { key: 'refresh_token', newValue: 'tab2-refresh', storageArea: localStorage }));
      first.flush(null, { status: 401, statusText: 'Unauthorized' });

      backend.expectNone(refreshUrl);
      const retry = backend.expectOne('/api/data');
      expect(retry.request.headers.get('Authorization')).toBe('Bearer tab2-access');
      retry.flush({ ok: true });
      expect(result).toEqual({ ok: true });
    });

    it('ends the session if the retry with the stored tokens is still refused', () => {
      let error: unknown;
      http.get('/api/data').subscribe({ error: (e) => (error = e) });
      const first = backend.expectOne('/api/data');
      otherTabRotatedSilently();
      first.flush(null, { status: 401, statusText: 'Unauthorized' });

      backend.expectOne('/api/data').flush(null, { status: 401, statusText: 'Unauthorized' });

      backend.expectNone(refreshUrl);
      expect(tokens.token()).toBeNull();
      expect(navigate).toHaveBeenCalledTimes(1);
      expect(error).toBeTruthy();
    });

    it('does not refresh when the other tab signed out', () => {
      let error: unknown;
      http.get('/api/data').subscribe({ error: (e) => (error = e) });
      const first = backend.expectOne('/api/data');
      localStorage.clear();
      first.flush(null, { status: 401, statusText: 'Unauthorized' });

      backend.expectNone(refreshUrl);
      expect(tokens.token()).toBeNull();
      expect(error).toBeTruthy();
    });

    it('refreshes exactly once, with the held token, when nothing changed in storage', () => {
      let result: unknown;
      http.get('/api/data').subscribe((r) => (result = r));
      backend.expectOne('/api/data').flush(null, { status: 401, statusText: 'Unauthorized' });

      const refresh = backend.expectOne(refreshUrl);
      expect(refresh.request.body).toEqual({ refreshToken: 'old-refresh' });
      refresh.flush({ accessToken: 'new-access', refreshToken: 'new-refresh' });
      backend.expectOne('/api/data').flush({ ok: true });

      expect(result).toEqual({ ok: true });
    });

    it('keeps a single refresh for a burst of 401s, and a late 401 from before it retries without a second one', () => {
      const results: unknown[] = [];
      http.get('/api/a').subscribe((r) => results.push(r));
      http.get('/api/b').subscribe((r) => results.push(r));
      const a = backend.expectOne('/api/a');
      const b = backend.expectOne('/api/b');
      a.flush(null, { status: 401, statusText: 'Unauthorized' });
      backend.expectOne(refreshUrl).flush({ accessToken: 'new-access', refreshToken: 'new-refresh' });
      backend.expectOne('/api/a').flush('a');

      // /api/b was sent with the old access token and answers 401 only now, after the refresh finished.
      b.flush(null, { status: 401, statusText: 'Unauthorized' });
      backend.expectNone(refreshUrl);
      const retry = backend.expectOne('/api/b');
      expect(retry.request.headers.get('Authorization')).toBe('Bearer new-access');
      retry.flush('b');

      expect(results).toEqual(['a', 'b']);
    });
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
        queryParams: { reason: 'expired', returnUrl: '/fr/schools/7' },
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

      expect(navigate).toHaveBeenCalledOnceWith(['/', 'fr', 'login'], { queryParams: { reason: 'expired' } });
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


describe('refreshInterceptor during server rendering', () => {
  it('passes a 401 through without browser logout, refresh or redirect', () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: PLATFORM_ID, useValue: 'server' },
        provideHttpClient(withInterceptors([refreshInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    const auth = TestBed.inject(AuthService);
    const logout = spyOn(auth, 'logout').and.callThrough();
    const backend = TestBed.inject(HttpTestingController);
    let status = 0;
    TestBed.inject(HttpClient).get('/api/data').subscribe({ error: (error) => status = error.status });
    backend.expectOne('/api/data').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(status).toBe(401);
    expect(logout).not.toHaveBeenCalled();
    backend.verify();
  });
});
