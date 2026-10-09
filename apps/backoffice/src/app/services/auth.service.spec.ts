import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService, NotAdminError } from './auth.service';
import { TokenService } from './token.service';
import { API_URL } from './api-config';

const api = 'http://api.test/api/v1';

describe('AuthService', () => {
  let auth: AuthService;
  let tokens: TokenService;
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: api },
      ],
    });
    auth = TestBed.inject(AuthService);
    tokens = TestBed.inject(TokenService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    localStorage.clear();
  });

  const loginAs = (roleName: string) => {
    let user: unknown;
    let error: unknown;
    auth.login('a@x.io', 'pw').subscribe({ next: (u) => (user = u), error: (e) => (error = e) });
    const req = backend.expectOne(`${api}/auth`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'a@x.io', password: 'pw' });
    req.flush({ accessToken: 'acc', refreshToken: 'ref' });
    backend.expectOne(`${api}/users/email/a%40x.io`).flush({
      id: 1, firstname: 'A', lastname: 'B', email: 'a@x.io', role: { name: roleName },
    });
    return { user, error };
  };

  it('stores tokens and the profile of an admin', () => {
    const { user, error } = loginAs('ROLE_ADMIN');

    expect(error).toBeUndefined();
    expect(user).toBeTruthy();
    expect(tokens.token()).toBe('acc');
    expect(tokens.refreshToken()).toBe('ref');
    expect(tokens.isAdmin()).toBeTrue();
    expect(localStorage.getItem('auth_token')).toBe('acc');
  });

  it('refuses a non-admin and leaves nothing stored', () => {
    const { error } = loginAs('ROLE_STUDENT');

    expect(error).toBeInstanceOf(NotAdminError);
    expect(tokens.token()).toBeNull();
    expect(tokens.user()).toBeNull();
    expect(localStorage.length).toBe(0);
  });

  it('passes bad credentials through and stores nothing', () => {
    let status = 0;
    auth.login('a@x.io', 'bad').subscribe({ error: (e) => (status = e.status) });
    backend.expectOne(`${api}/auth`).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(status).toBe(401);
    expect(tokens.isAuthenticated()).toBeFalse();
  });

  it('refreshToken posts the token and stores the rotated pair', () => {
    auth.refreshToken('old').subscribe();
    const req = backend.expectOne(`${api}/auth/refresh`);
    expect(req.request.body).toEqual({ refreshToken: 'old' });
    req.flush({ accessToken: 'n', refreshToken: 'nr' });

    expect(tokens.token()).toBe('n');
    expect(tokens.refreshToken()).toBe('nr');
  });

  it('logout clears the session', () => {
    tokens.setTokens('a', 'r');
    auth.logout();
    expect(tokens.isAuthenticated()).toBeFalse();
  });
});
