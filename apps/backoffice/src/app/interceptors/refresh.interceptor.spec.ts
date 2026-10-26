import { Router, provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { PLATFORM_ID, provideZonelessChangeDetection } from '@angular/core';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from '@services/auth.service';
import { TokenService } from '@services/token.service';
import { API_URL } from '@services/api-config';
import { jwtInterceptor } from './jwt.interceptor';
import { refreshInterceptor } from './refresh.interceptor';

const api = 'http://api.test/api/v1';
const unauthorized = { status: 401, statusText: 'Unauthorized' };

describe('refreshInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let tokens: TokenService;
  let auth: AuthService;
  const logoutUrl = `${api}/auth/logout`;
  const refreshUrl = `${api}/auth/refresh`;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideHttpClient(withInterceptors([jwtInterceptor, refreshInterceptor])),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: api },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    tokens = TestBed.inject(TokenService);
    auth = TestBed.inject(AuthService);
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    tokens.setTokens('old-access', 'old-refresh');
  });

  afterEach(() => {
    // A logout also ends the session on the server (fire-and-forget); answer it before checking for leftovers.
    backend.match(logoutUrl).forEach((req) => req.flush(null, { status: 204, statusText: 'No Content' }));
    backend.verify();
    localStorage.clear();
  });

  it('attaches the bearer token', () => {
    http.get('/api/data').subscribe();
    expect(backend.expectOne('/api/data').request.headers.get('Authorization')).toBe(
      'Bearer old-access',
    );
  });

  it('refreshes on a 401 and retries with the new token', () => {
    let result: unknown;
    http.get('/api/data').subscribe((r) => (result = r));

    backend.expectOne('/api/data').flush(null, unauthorized);
    const refresh = backend.expectOne(refreshUrl);
    expect(refresh.request.body).toEqual({ refreshToken: 'old-refresh' });
    refresh.flush({ accessToken: 'new-access', refreshToken: 'new-refresh' });

    const retry = backend.expectOne('/api/data');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new-access');
    retry.flush({ ok: true });
    expect(result).toEqual({ ok: true });
  });

  it('shares one refresh across a burst of 401s', () => {
    const results: unknown[] = [];
    http.get('/api/a').subscribe((r) => results.push(r));
    http.get('/api/b').subscribe((r) => results.push(r));

    backend.expectOne('/api/a').flush(null, unauthorized);
    backend.expectOne('/api/b').flush(null, unauthorized);
    backend.expectOne(refreshUrl).flush({ accessToken: 'n', refreshToken: 'r' });
    backend.expectOne('/api/a').flush('a');
    backend.expectOne('/api/b').flush('b');

    expect(results).toEqual(['a', 'b']);
  });

  it('retries only once when the retry is still 401', () => {
    let error: unknown;
    http.get('/api/data').subscribe({ error: (e) => (error = e) });

    backend.expectOne('/api/data').flush(null, unauthorized);
    backend.expectOne(refreshUrl).flush({ accessToken: 'n', refreshToken: 'r' });
    backend.expectOne('/api/data').flush(null, unauthorized);

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

    backend.expectOne('/api/data').flush(null, unauthorized);
    backend.expectOne(refreshUrl).flush(null, unauthorized);

    expect(auth.logout).toHaveBeenCalledTimes(1);
    expect(backend.expectOne(logoutUrl).request.body).toEqual({ refreshToken: 'old-refresh' });
    expect(tokens.token()).toBeNull();
    expect(error).toBeTruthy();
  });

  it('logs out without calling the API when there is no refresh token', () => {
    tokens.clear();
    spyOn(auth, 'logout').and.callThrough();
    let error: unknown;
    http.get('/api/data').subscribe({ error: (e) => (error = e) });

    backend.expectOne('/api/data').flush(null, unauthorized);

    backend.expectNone(refreshUrl);
    expect(auth.logout).toHaveBeenCalled();
    expect(error).toBeTruthy();
  });

  it('never refreshes for the auth endpoints themselves', () => {
    const errors: unknown[] = [];
    http.post(`${api}/auth`, {}).subscribe({ error: (e) => errors.push(e) });
    http.post(refreshUrl, {}).subscribe({ error: (e) => errors.push(e) });

    backend.expectOne(`${api}/auth`).flush(null, unauthorized);
    backend.expectOne(refreshUrl).flush(null, unauthorized);

    backend.expectNone(refreshUrl);
    expect(errors.length).toBe(2);
  });

  it('returns an expired admin session to login with the interrupted page', () => {
    spyOnProperty(TestBed.inject(Router), 'url').and.returnValue('/schools?page=2');
    http.get('/api/data').subscribe({ error: () => undefined });
    backend.expectOne('/api/data').flush(null, unauthorized);
    backend.expectOne(refreshUrl).flush(null, unauthorized);
    expect(tokens.token()).toBeNull();
    expect(tokens.refreshToken()).toBeNull();
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledOnceWith(['/login'], {
      queryParams: { reason: 'expired', returnUrl: '/schools?page=2' },
    });
  });

  it('passes non-401 errors through', () => {
    let status = 0;
    http.get('/api/data').subscribe({ error: (e) => (status = e.status) });
    backend.expectOne('/api/data').flush(null, { status: 500, statusText: 'Server Error' });
    expect(status).toBe(500);
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
        { provide: API_URL, useValue: api },
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
