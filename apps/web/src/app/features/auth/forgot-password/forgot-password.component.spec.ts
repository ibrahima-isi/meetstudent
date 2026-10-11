import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Meta } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { environment } from '../../../../environments/environment';
import { ForgotPasswordComponent } from './forgot-password.component';

describe('ForgotPasswordComponent', () => {
  let fixture: ComponentFixture<ForgotPasswordComponent>;
  let httpMock: HttpTestingController;
  const base = `${environment.apiUrl}/auth`;

  const el = () => fixture.nativeElement as HTMLElement;
  const text = () => el().textContent ?? '';

  async function render(lang: 'fr' | 'en', emailEnabled: boolean | 'error' = true) {
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    fixture = TestBed.createComponent(ForgotPasswordComponent);
    fixture.detectChanges();
    // Read once per app: a second render in the same test hits the cache.
    for (const caps of httpMock.match(`${base}/capabilities`)) {
      if (emailEnabled === 'error') {
        caps.flush(null, { status: 500, statusText: 'Server Error' });
      } else {
        caps.flush({ emailEnabled });
      }
    }
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function submit(email: string) {
    const input = el().querySelector('#email') as HTMLInputElement;
    input.value = email;
    input.dispatchEvent(new Event('input'));
    (el().querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ForgotPasswordComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco(translocoOptions),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('titles the page in French and in English', async () => {
    await render('fr');
    expect(el().querySelector('h1')?.textContent?.trim()).toBe('Mot de passe oublié ?');
    fixture.destroy();

    await render('en');
    expect(el().querySelector('h1')?.textContent?.trim()).toBe('Forgot your password?');
  });

  it('uses the shared field-input class and the email autocomplete hint', async () => {
    await render('en');
    const input = el().querySelector('#email') as HTMLInputElement;

    expect(input.classList).toContain('field-input');
    expect(input.getAttribute('autocomplete')).toBe('email');
  });

  it('blocks an invalid email, flags it with aria-invalid and sends nothing', async () => {
    await render('en');

    await submit('nope');
    await settle();

    const input = el().querySelector('#email') as HTMLInputElement;
    expect(input.getAttribute('aria-invalid')).toBe('true');
    const message = el().querySelector(`#${input.getAttribute('aria-describedby')}`);
    expect(message?.getAttribute('role')).toBe('alert');
  });

  it('posts the address and the active language, then shows the neutral notice', async () => {
    await render('en');

    await submit('awa@example.com');
    const req = httpMock.expectOne(`${base}/password-reset/request`);
    expect(req.request.body).toEqual({ email: 'awa@example.com', lang: 'en' });
    req.flush({ status: 'accepted' }, { status: 202, statusText: 'Accepted' });
    await settle();

    expect(text()).toContain('If an account exists for this address, a reset link has been sent');
    expect(el().querySelector('a[href="/en/login"]')).not.toBeNull();
  });

  it('shows the French neutral notice', async () => {
    await render('fr');

    await submit('awa@example.com');
    httpMock.expectOne(`${base}/password-reset/request`).flush({}, { status: 202, statusText: 'Accepted' });
    await settle();

    expect(text()).toContain('Si un compte existe pour cette adresse, un lien de réinitialisation a été envoyé');
  });

  it('says exactly the same thing for two different addresses', async () => {
    await render('en');
    await submit('known@example.com');
    httpMock.expectOne(`${base}/password-reset/request`).flush({}, { status: 202, statusText: 'Accepted' });
    await settle();
    const first = text();
    fixture.destroy();

    await render('en');
    await submit('stranger@example.com');
    httpMock.expectOne(`${base}/password-reset/request`).flush({}, { status: 202, statusText: 'Accepted' });
    await settle();

    expect(text()).toBe(first);
    expect(text()).not.toContain('example.com');
  });

  it('shows its own field error on a 400, never the API text', async () => {
    await render('en');

    await submit('awa@example.com');
    httpMock
      .expectOne(`${base}/password-reset/request`)
      .flush({ email: "L'adresse e-mail est invalide (API)" }, { status: 400, statusText: 'Bad Request' });
    await settle();

    expect(text()).toContain('Please enter a valid email address');
    expect(text()).not.toContain('(API)');
    expect(text()).not.toContain('reset link has been sent');
    expect((el().querySelector('#email') as HTMLInputElement).getAttribute('aria-invalid')).toBe('true');
  });

  it('shows a generic failure banner when the server is down', async () => {
    await render('en');

    await submit('awa@example.com');
    httpMock.expectOne(`${base}/password-reset/request`).flush(null, { status: 500, statusText: 'Server Error' });
    await settle();

    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Something went wrong');
    expect(text()).not.toContain('reset link has been sent');
  });

  it('keeps the submit disabled while the request is pending', async () => {
    await render('en');

    await submit('awa@example.com');

    expect((el().querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBeTrue();
    httpMock.expectOne(`${base}/password-reset/request`).flush({}, { status: 202, statusText: 'Accepted' });
  });

  describe('when email delivery is not available', () => {
    it('adds a neutral notice in English', async () => {
      await render('en', false);

      expect(text()).toContain('Email delivery is not available yet');
    });

    it('adds a neutral notice in French', async () => {
      await render('fr', false);

      expect(text()).toContain("L'envoi d'e-mails n'est pas encore disponible");
    });

    it('does so when the capabilities call fails', async () => {
      await render('en', 'error');

      expect(text()).toContain('Email delivery is not available yet');
    });

    it('stays silent when delivery is available, and the form is the same', async () => {
      await render('en', true);

      expect(text()).not.toContain('Email delivery is not available yet');
      expect(el().querySelector('#email')).not.toBeNull();
    });
  });

  describe('search engines', () => {
    it('asks not to be indexed and lets go on destroy', async () => {
      const meta = TestBed.inject(Meta);
      await render('en');

      expect(meta.getTag('name="robots"')?.content).toBe('noindex,nofollow');

      fixture.destroy();
      expect(meta.getTag('name="robots"')).toBeNull();
    });
  });
});
