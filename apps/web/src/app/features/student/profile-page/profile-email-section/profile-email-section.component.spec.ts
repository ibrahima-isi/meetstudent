import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { User } from '@models/entities';
import { environment } from '../../../../../environments/environment';
import { ProfileEmailSectionComponent } from './profile-email-section.component';

describe('ProfileEmailSectionComponent', () => {
  let fixture: ComponentFixture<ProfileEmailSectionComponent>;
  let httpMock: HttpTestingController;
  let user: ReturnType<typeof signal<User | null>>;
  const base = environment.apiUrl;
  const changeUrl = `${base}/users/7/email-change`;

  const el = () => fixture.nativeElement as HTMLElement;
  const text = () => el().textContent ?? '';
  const button = (re: RegExp) =>
    Array.from(el().querySelectorAll('button')).find((b) => re.test(b.textContent ?? '')) as HTMLButtonElement | undefined;
  const input = (testId: string) => el().querySelector(`[data-testid="${testId}"]`) as HTMLInputElement;

  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  /** Renders the section and answers the two requests it makes on start. */
  async function render(options: { verified?: boolean; emailEnabled?: boolean; pending?: string | null } = {}) {
    const { verified = true, emailEnabled = true, pending = null } = options;
    user.set({ id: 7, email: 'awa@example.com', emailVerified: verified } as User);
    fixture = TestBed.createComponent(ProfileEmailSectionComponent);
    await settle();
    httpMock.match(`${base}/auth/capabilities`).forEach((r) => r.flush({ emailEnabled }));
    httpMock.match(changeUrl).filter((r) => r.request.method === 'GET').forEach((r) => r.flush(pending ? { pendingEmail: pending } : {}));
    await settle();
  }

  async function openChangeForm() {
    button(/change email address/i)!.click();
    await settle();
  }

  async function fillAndSubmit(newEmail: string, password: string) {
    const emailInput = input('email-new');
    emailInput.value = newEmail;
    emailInput.dispatchEvent(new Event('input'));
    const passwordInput = input('email-password');
    passwordInput.value = password;
    passwordInput.dispatchEvent(new Event('input'));
    await settle();
    button(/send the confirmation link/i)!.click();
    await settle();
  }

  beforeEach(async () => {
    user = signal<User | null>(null);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: TokenService, useValue: { isAuthenticated: signal(true), user, setUser: (u: User) => user.set(u) } },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    await firstValueFrom(TestBed.inject(LocaleService).use('en'));
  });

  afterEach(() => httpMock.verify());

  describe('the current address', () => {
    it('shows the address of the signed-in user as confirmed', async () => {
      await render({ verified: true });

      expect(text()).toContain('awa@example.com');
      expect(text()).toContain('Confirmed');
      expect(button(/resend the confirmation email/i)).toBeUndefined();
    });

    it('says when it is not confirmed yet and offers to send the email again', async () => {
      await render({ verified: false });

      expect(text()).toContain('Not confirmed yet');
      expect(button(/resend the confirmation email/i)).toBeDefined();
    });

    it('sends the confirmation email again in the language being read', async () => {
      await render({ verified: false });

      button(/resend the confirmation email/i)!.click();
      httpMock.expectOne(`${base}/auth/email-verification/resend?lang=en`).flush({ status: 'accepted' });
      await settle();

      expect(text()).toContain('Email sent');
    });

    it('refreshes the cached user when the server says it is already confirmed', async () => {
      await render({ verified: false });

      button(/resend the confirmation email/i)!.click();
      httpMock.expectOne(`${base}/auth/email-verification/resend?lang=en`).flush({ status: 409 }, { status: 409, statusText: 'Conflict' });
      httpMock.expectOne(`${base}/users/id/7`).flush({ id: 7, email: 'awa@example.com', emailVerified: true });
      await settle();

      expect(text()).toContain('already confirmed');
      expect(user()?.emailVerified).toBeTrue();
    });

    it('tells the user to wait when the server rate-limits the resend', async () => {
      await render({ verified: false });

      button(/resend the confirmation email/i)!.click();
      httpMock.expectOne(`${base}/auth/email-verification/resend?lang=en`).flush({}, { status: 429, statusText: 'Too Many Requests' });
      await settle();

      expect(text()).toContain('Too many attempts');
    });

    it('does not offer a resend, nor a change, while the server cannot send email', async () => {
      await render({ verified: false, emailEnabled: false });

      expect(button(/resend the confirmation email/i)).toBeUndefined();
      expect(button(/change email address/i)).toBeUndefined();
      expect(text()).toContain('not available');
    });
  });

  describe('changing the address', () => {
    it('asks for the new address and the current password, and sends both with the language', async () => {
      await render();
      await openChangeForm();

      await fillAndSubmit('new@example.com', 'S3cure-pass');

      const req = httpMock.expectOne((r) => r.method === 'POST' && r.url === changeUrl);
      expect(req.request.body).toEqual({ newEmail: 'new@example.com', currentPassword: 'S3cure-pass', lang: 'en' });
      req.flush({ status: 'accepted' }, { status: 202, statusText: 'Accepted' });
      await settle();

      expect(text()).toContain('new@example.com');
      expect(text()).toContain('Waiting for confirmation');
      expect(input('email-new')).toBeNull();
      expect(user()?.email).toBe('awa@example.com');
    });

    it('does not call the API with an empty or malformed form', async () => {
      await render();
      await openChangeForm();

      await fillAndSubmit('not-an-email', '');

      httpMock.expectNone((r) => r.method === 'POST' && r.url === changeUrl);
      expect(input('email-new')).not.toBeNull();
    });

    it('shows the API message under the password when it is wrong, and keeps the form open', async () => {
      await render();
      await openChangeForm();
      await fillAndSubmit('new@example.com', 'wrong');

      httpMock.expectOne((r) => r.method === 'POST' && r.url === changeUrl)
        .flush({ currentPassword: 'Mot de passe incorrect' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      expect(el().querySelector('[data-testid="email-error-currentPassword"]')?.textContent).toContain('Mot de passe incorrect');
      expect(input('email-new')).not.toBeNull();
    });

    it('shows the API message under the new address when it is already used', async () => {
      await render();
      await openChangeForm();
      await fillAndSubmit('taken@example.com', 'S3cure-pass');

      httpMock.expectOne((r) => r.method === 'POST' && r.url === changeUrl)
        .flush({ newEmail: 'Cet email est déjà utilisé' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      expect(el().querySelector('[data-testid="email-error-newEmail"]')?.textContent).toContain('Cet email est déjà utilisé');
    });

    it('explains a rate limit and an unavailable mail service in its own words', async () => {
      await render();
      await openChangeForm();
      await fillAndSubmit('new@example.com', 'S3cure-pass');
      httpMock.expectOne((r) => r.method === 'POST' && r.url === changeUrl).flush({}, { status: 429, statusText: 'Too Many Requests' });
      await settle();
      expect(text()).toContain('Too many attempts');

      button(/send the confirmation link/i)!.click();
      await settle();
      httpMock.expectOne((r) => r.method === 'POST' && r.url === changeUrl).flush({}, { status: 503, statusText: 'Unavailable' });
      await settle();
      expect(text()).toContain('not available');
    });

    it('says how long to wait when the current password is locked out (429 with Retry-After)', async () => {
      await render();
      await openChangeForm();
      await fillAndSubmit('new@example.com', 'wrong-pass');
      httpMock.expectOne((r) => r.method === 'POST' && r.url === changeUrl)
        .flush({}, { status: 429, statusText: 'Too Many Requests', headers: { 'Retry-After': '900' } });
      await settle();

      expect(text()).toContain('Too many attempts. Try again in 15 min.');
      expect(text()).not.toContain('went wrong');
      expect(el().querySelector('[data-testid="email-password"]')).not.toBeNull();
    });

    it('closes the form on cancel without calling the API', async () => {
      await render();
      await openChangeForm();

      button(/^\s*cancel\s*$/i)!.click();
      await settle();

      expect(input('email-new')).toBeNull();
      httpMock.expectNone((r) => r.method === 'POST');
    });
  });

  describe('a pending change', () => {
    it('shows the address waiting for its link, loaded from the API', async () => {
      await render({ pending: 'waiting@example.com' });

      expect(text()).toContain('waiting@example.com');
      expect(text()).toContain('Waiting for confirmation');
      expect(text()).toContain('awa@example.com');
    });

    it('sends the link again', async () => {
      await render({ pending: 'waiting@example.com' });

      button(/resend the link/i)!.click();
      httpMock.expectOne(`${changeUrl}/resend?lang=en`).flush({ status: 'accepted' }, { status: 202, statusText: 'Accepted' });
      await settle();

      expect(text()).toContain('Email sent');
    });

    it('cancels the change', async () => {
      await render({ pending: 'waiting@example.com' });

      button(/cancel the change/i)!.click();
      httpMock.expectOne((r) => r.method === 'DELETE' && r.url === changeUrl).flush(null, { status: 204, statusText: 'No Content' });
      await settle();

      expect(text()).not.toContain('waiting@example.com');
      expect(text()).toContain('Change cancelled');
    });

    it('drops the pending address when the server says there is nothing to resend', async () => {
      await render({ pending: 'waiting@example.com' });

      button(/resend the link/i)!.click();
      httpMock.expectOne(`${changeUrl}/resend?lang=en`).flush({}, { status: 409, statusText: 'Conflict' });
      await settle();

      expect(text()).not.toContain('waiting@example.com');
    });
  });
});
