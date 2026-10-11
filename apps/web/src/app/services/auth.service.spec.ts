import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';
import { TokenService } from './token.service';

describe('AuthService password reset and capabilities', () => {
  let service: AuthService;
  let backend: HttpTestingController;
  const base = `${environment.apiUrl}/auth`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('requests a reset with the email and the language', () => {
    service.requestPasswordReset('awa@example.com', 'fr').subscribe();

    const req = backend.expectOne(`${base}/password-reset/request`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'awa@example.com', lang: 'fr' });
    req.flush({ status: 'accepted' }, { status: 202, statusText: 'Accepted' });
  });

  it('confirms a reset with the token and both passwords', () => {
    service.confirmPasswordReset('tok', 'Secret123', 'Secret123').subscribe();

    const req = backend.expectOne(`${base}/password-reset/confirm`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ token: 'tok', password: 'Secret123', confirmedPassword: 'Secret123' });
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('reads the capabilities once and shares the answer', () => {
    const answers: unknown[] = [];
    service.getCapabilities().subscribe((c) => answers.push(c));
    service.getCapabilities().subscribe((c) => answers.push(c));

    backend.expectOne(`${base}/capabilities`).flush({ emailEnabled: true });
    service.getCapabilities().subscribe((c) => answers.push(c));

    expect(answers).toEqual([{ emailEnabled: true }, { emailEnabled: true }, { emailEnabled: true }]);
  });

  it('falls back to email disabled when the capabilities call fails', () => {
    let result: unknown;
    service.getCapabilities().subscribe((c) => (result = c));

    backend.expectOne(`${base}/capabilities`).flush(null, { status: 500, statusText: 'Server Error' });

    expect(result).toEqual({ emailEnabled: false });
  });

  it('confirms an email with the token alone', () => {
    service.confirmEmailVerification('tok').subscribe();

    const req = backend.expectOne(`${base}/email-verification/confirm`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ token: 'tok' });
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('asks for a new confirmation email with no body of its own', () => {
    service.resendEmailVerification().subscribe();

    const req = backend.expectOne(`${base}/email-verification/resend`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ status: 'accepted' }, { status: 202, statusText: 'Accepted' });
  });

  it('refreshes the cached user from the server', () => {
    const tokens = TestBed.inject(TokenService);
    tokens.user.set({ id: 7, email: 'awa@example.com', emailVerified: false } as never);
    const setUser = spyOn(tokens, 'setUser');
    let result: unknown;

    service.refreshCurrentUser().subscribe((u) => (result = u));

    const req = backend.expectOne(`${environment.apiUrl}/users/id/7`);
    expect(req.request.method).toBe('GET');
    const fresh = { id: 7, email: 'awa@example.com', emailVerified: true };
    req.flush(fresh);
    expect(setUser).toHaveBeenCalledWith(fresh as never);
    expect(result).toEqual(fresh);
  });
});
