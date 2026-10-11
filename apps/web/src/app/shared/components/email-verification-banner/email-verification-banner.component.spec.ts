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
import { environment } from '../../../../environments/environment';
import { EmailVerificationBannerComponent } from './email-verification-banner.component';

describe('EmailVerificationBannerComponent', () => {
  let fixture: ComponentFixture<EmailVerificationBannerComponent>;
  let httpMock: HttpTestingController;
  let authenticated: ReturnType<typeof signal<boolean>>;
  let user: ReturnType<typeof signal<User | null>>;
  const base = `${environment.apiUrl}/auth`;
  const resendUrl = `${base}/email-verification/resend?lang=en`;

  const el = () => fixture.nativeElement as HTMLElement;
  const banner = () => el().querySelector('[data-testid="email-verification-banner"]');
  const button = (re: RegExp) =>
    Array.from(el().querySelectorAll('button')).find((b) => re.test(b.textContent ?? '')) as HTMLButtonElement;
  const status = () => el().querySelector('[role="status"]')?.textContent?.trim() ?? '';

  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function render(options: { verified?: boolean; auth?: boolean; emailEnabled?: boolean } = {}) {
    const { verified = false, auth = true, emailEnabled = true } = options;
    authenticated.set(auth);
    user.set({ id: 7, email: 'awa@example.com', emailVerified: verified } as User);
    fixture = TestBed.createComponent(EmailVerificationBannerComponent);
    await settle();
    const caps = httpMock.match(`${base}/capabilities`);
    caps.forEach((r) => r.flush({ emailEnabled }));
    await settle();
  }

  beforeEach(async () => {
    sessionStorage.clear();
    authenticated = signal(true);
    user = signal<User | null>(null);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: TokenService, useValue: { isAuthenticated: authenticated, user, setUser: (u: User) => user.set(u) } },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    await firstValueFrom(TestBed.inject(LocaleService).use('en'));
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.clear();
  });

  describe('when it shows', () => {
    it('shows for a signed-in user whose email is not verified, with email enabled', async () => {
      await render();

      expect(banner()).not.toBeNull();
      expect(el().textContent).toContain('Confirm your email address');
      expect(button(/resend/i)).toBeDefined();
    });

    it('is silent for a verified user, and does not even ask the server about mail', async () => {
      authenticated.set(true);
      user.set({ id: 7, emailVerified: true } as User);
      fixture = TestBed.createComponent(EmailVerificationBannerComponent);
      await settle();

      httpMock.expectNone(`${base}/capabilities`);
      expect(banner()).toBeNull();
    });

    it('is silent for a user from an older payload with no emailVerified at all', async () => {
      authenticated.set(true);
      user.set({ id: 7 } as User);
      fixture = TestBed.createComponent(EmailVerificationBannerComponent);
      await settle();

      expect(banner()).toBeNull();
    });

    it('is silent when nobody is signed in', async () => {
      authenticated.set(false);
      user.set(null);
      fixture = TestBed.createComponent(EmailVerificationBannerComponent);
      await settle();

      httpMock.expectNone(`${base}/capabilities`);
      expect(banner()).toBeNull();
    });

    it('is silent when the server cannot send email', async () => {
      await render({ emailEnabled: false });

      expect(banner()).toBeNull();
    });

    it('speaks French when French is active', async () => {
      await firstValueFrom(TestBed.inject(LocaleService).use('fr'));
      await render();

      expect(el().textContent).toContain('Confirmez votre adresse e-mail');
    });
  });

  it('clears the floating dock navbar instead of hiding under it', async () => {
    await render();

    expect(banner()?.classList).toContain('mt-[4.75rem]');
  });

  describe('resending', () => {
    it('hides the resend button once the address turns out to be confirmed already', async () => {
      await render();

      button(/resend/i).click();
      await settle();
      httpMock.expectOne(resendUrl).flush({}, { status: 409, statusText: 'Conflict' });
      httpMock.expectOne(`${environment.apiUrl}/users/id/7`).flush({ id: 7, emailVerified: true });
      await settle();

      expect(button(/resend/i)).toBeUndefined();
    });

    it('starts afresh when another user signs in', async () => {
      await render();
      button(/resend/i).click();
      await settle();
      httpMock.expectOne(resendUrl).flush({}, { status: 429, statusText: 'Too Many Requests' });
      await settle();
      expect(status()).toContain('Too many requests');

      user.set({ id: 8, email: 'bob@example.com', emailVerified: false } as User);
      await settle();

      expect(status()).toBe('');
      expect(banner()).not.toBeNull();
    });

    it('asks for a new email and confirms politely in a status region', async () => {
      await render();

      button(/resend/i).click();
      await settle();
      httpMock
        .expectOne(resendUrl)
        .flush({ status: 'accepted' }, { status: 202, statusText: 'Accepted' });
      await settle();

      expect(status()).toContain('A new email is on its way');
      expect(banner()).not.toBeNull();
    });

    it('disables the button while the request is in flight', async () => {
      await render();

      button(/resend/i).click();
      await settle();

      expect(el().querySelector('button[disabled]')).not.toBeNull();
      httpMock.expectOne(resendUrl).flush({}, { status: 202, statusText: 'Accepted' });
    });

    it('says the daily limit is reached on 429', async () => {
      await render();

      button(/resend/i).click();
      await settle();
      httpMock
        .expectOne(resendUrl)
        .flush({ message: 'x' }, { status: 429, statusText: 'Too Many Requests', headers: { 'Retry-After': '60' } });
      await settle();

      expect(status()).toContain('Too many requests');
      expect(status()).not.toContain('on its way');
    });

    it('on 409 says it is already confirmed and refreshes the cached user', async () => {
      await render();

      button(/resend/i).click();
      await settle();
      httpMock
        .expectOne(resendUrl)
        .flush({ message: 'x' }, { status: 409, statusText: 'Conflict' });
      httpMock
        .expectOne(`${environment.apiUrl}/users/id/7`)
        .flush({ id: 7, email: 'awa@example.com', emailVerified: true });
      await settle();

      expect(user()?.emailVerified).toBeTrue();
      expect(status()).toContain('already confirmed');
      expect(status()).not.toContain('Too many requests');
    });

    it('says something went wrong on any other failure', async () => {
      await render();

      button(/resend/i).click();
      await settle();
      httpMock
        .expectOne(resendUrl)
        .flush({ message: 'x' }, { status: 503, statusText: 'Unavailable' });
      await settle();

      expect(status()).toContain('Something went wrong');
    });
  });

  describe('dismissing', () => {
    it('hides the banner for the rest of the session and puts focus on the main landmark', async () => {
      const main = document.createElement('main');
      main.id = 'main';
      main.tabIndex = -1;
      document.body.appendChild(main);
      try {
        await render();

        button(/dismiss/i).click();
        await settle();

        expect(banner()).toBeNull();
        expect(document.activeElement).toBe(main);

        fixture.destroy();
        fixture = TestBed.createComponent(EmailVerificationBannerComponent);
        await settle();
        expect(banner()).toBeNull();

        // Someone else on the same tab still gets their reminder.
        user.set({ id: 8, email: 'bob@example.com', emailVerified: false } as User);
        await settle();
        expect(banner()).not.toBeNull();
      } finally {
        main.remove();
      }
    });
  });

  it('labels the dismiss button for assistive technology', async () => {
    await render();

    expect(button(/dismiss/i).getAttribute('aria-label') ?? button(/dismiss/i).textContent).toMatch(/dismiss/i);
  });
});
