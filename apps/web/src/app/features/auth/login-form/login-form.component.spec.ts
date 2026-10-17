import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, ActivatedRouteSnapshot, convertToParamMap, provideRouter, Router } from '@angular/router';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { environment } from '../../../../environments/environment';
import { LoginFormComponent } from './login-form.component';

describe('LoginFormComponent translations', () => {
  let fixture: ComponentFixture<LoginFormComponent>;
  let httpMock: HttpTestingController;

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  async function render(lang: 'fr' | 'en') {
    const transloco = TestBed.inject(TranslocoService);
    // *transloco renders nothing until the dynamic import resolves.
    await firstValueFrom(transloco.load(lang));
    transloco.setActiveLang(lang);
    fixture = TestBed.createComponent(LoginFormComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LoginFormComponent],
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

  it('renders in French, the source language', async () => {
    await render('fr');

    expect(fixture.nativeElement.querySelector('h1').textContent.trim())
      .toBe('Bon retour parmi nous');
    expect(text()).toContain('Se connecter');
  });

  it('renders in English when English is active', async () => {
    await render('en');

    expect(fixture.nativeElement.querySelector('h1').textContent.trim())
      .toBe('Welcome Back');
    expect(text()).toContain('Sign In');
  });

  // Decision 5: ErrorResponse.message is for logs, never for the user.
  describe('when the sign-in is refused', () => {
    const fail = async (lang: 'fr' | 'en', status: number, body: object = {}) => {
      await render(lang);
      const component = fixture.componentInstance;
      component.loginForm.setValue({ email: 'awa@example.com', password: 'wrong' });
      component.handleSubmit();
      httpMock
        .expectOne(`${environment.apiUrl}/auth`)
        .flush(body, { status, statusText: 'Error' });
      fixture.detectChanges();
      await fixture.whenStable();
    };

    it('says the credentials are wrong on a 401, in English, never the API message', async () => {
      await fail('en', 401, { message: 'Bad credentials from Spring' });
      expect(text()).toContain('Incorrect email or password.');
      expect(text()).not.toContain('Bad credentials from Spring');
    });

    it('says the credentials are wrong on a 401, in French', async () => {
      await fail('fr', 401);
      expect(text()).toContain('E-mail ou mot de passe incorrect.');
    });

    it('does not reveal whether the email exists: the text is the same for any 401', async () => {
      await fail('en', 401, { message: 'User not found' });
      const unknownEmail = text();
      expect(unknownEmail).toContain('Incorrect email or password.');
      expect(unknownEmail).not.toContain('User not found');
    });

    for (const status of [0, 429, 500, 503]) {
      it(`keeps the generic text for a ${status}`, async () => {
        await fail('en', status, { message: 'internal detail' });
        expect(text()).toContain('Login failed. Please try again.');
        expect(text()).not.toContain('Incorrect email or password.');
        expect(text()).not.toContain('internal detail');
      });
    }
  });

  it('offers no social login: there is no OAuth backend behind it', async () => {
    await render('fr');

    expect(text()).not.toContain('Google');
    expect(text()).not.toContain('Microsoft');
    expect(text()).not.toContain('Ou continuez avec votre e-mail');
  });

  it('invites an expired session to reconnect in both languages', async () => {
    TestBed.inject(ActivatedRoute).snapshot = {
      queryParamMap: convertToParamMap({ reason: 'expired' }),
    } as ActivatedRouteSnapshot;
    await render('fr');
    expect(text()).toContain('Votre session a expiré. Reconnectez-vous');
    await render('en');
    expect(text()).toContain('Your session has expired. Sign in again');
  });

  describe('after registration', () => {
    function arriveFromRegistration(registered: boolean) {
      TestBed.inject(ActivatedRoute).snapshot = {
        queryParamMap: convertToParamMap(registered ? { registered: '1' } : {}),
      } as ActivatedRouteSnapshot;
    }

    async function renderAnswering(lang: 'fr' | 'en', emailEnabled: boolean) {
      arriveFromRegistration(true);
      await render(lang);
      httpMock.expectOne(`${environment.apiUrl}/auth/capabilities`).flush({ emailEnabled });
      fixture.detectChanges();
      await fixture.whenStable();
    }

    it('welcomes a new account in French', async () => {
      await renderAnswering('fr', false);

      expect(text()).toContain('Compte créé');
    });

    it('welcomes a new account in English', async () => {
      await renderAnswering('en', false);

      expect(text()).toContain('Account created');
    });

    it('mentions the confirmation email, in French, when email is enabled', async () => {
      await renderAnswering('fr', true);

      expect(text()).toContain('Compte créé');
      expect(text()).toContain('e-mail de confirmation');
    });

    it('mentions the confirmation email, in English, when email is enabled', async () => {
      await renderAnswering('en', true);

      expect(text()).toContain('confirmation email');
    });

    it('does not claim an email was sent when email is disabled', async () => {
      await renderAnswering('en', false);

      expect(text()).not.toContain('confirmation email');
    });

    it('does not claim an email was sent when the capabilities call fails', async () => {
      arriveFromRegistration(true);
      await render('en');
      httpMock
        .expectOne(`${environment.apiUrl}/auth/capabilities`)
        .flush(null, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();
      await fixture.whenStable();

      expect(text()).toContain('Account created');
      expect(text()).not.toContain('confirmation email');
    });

    it('asks nothing of the server on a plain visit', async () => {
      arriveFromRegistration(false);
      await render('en');

      httpMock.expectNone(`${environment.apiUrl}/auth/capabilities`);
    });

    it('shows no notice on a plain visit', async () => {
      arriveFromRegistration(false);
      await render('en');

      expect(text()).not.toContain('Account created');
    });
  });

  describe('forgotten password', () => {
    it('links to the forgot-password page of the language being read', async () => {
      await render('fr');
      const link = (fixture.nativeElement as HTMLElement).querySelector('a[href="/fr/forgot-password"]');

      expect(link?.textContent?.trim()).toBe('Mot de passe oublié ?');
    });

    it('is a real link, not a button that does nothing', async () => {
      await render('en');
      const el = fixture.nativeElement as HTMLElement;

      expect(el.querySelector('a[href$="/forgot-password"]')).not.toBeNull();
      expect(Array.from(el.querySelectorAll('button')).some((b) => b.textContent?.includes('Forgot'))).toBeFalse();
    });
  });

  it('explains the sign-out after a password change in both languages', async () => {
    TestBed.inject(ActivatedRoute).snapshot = {
      queryParamMap: convertToParamMap({ passwordChanged: '1' }),
    } as ActivatedRouteSnapshot;
    await render('fr');
    expect(text()).toContain('Mot de passe modifié');
    await render('en');
    expect(text()).toContain('Password changed');
  });

  describe('after a password reset', () => {
    function arriveFromReset(reset: boolean) {
      TestBed.inject(ActivatedRoute).snapshot = {
        queryParamMap: convertToParamMap(reset ? { reset: '1' } : {}),
      } as ActivatedRouteSnapshot;
    }

    it('confirms the new password in French', async () => {
      arriveFromReset(true);
      await render('fr');

      expect(text()).toContain('Mot de passe réinitialisé');
    });

    it('confirms the new password in English', async () => {
      arriveFromReset(true);
      await render('en');

      expect(text()).toContain('Password reset');
    });

    it('shows nothing on a plain visit', async () => {
      arriveFromReset(false);
      await render('en');

      expect(text()).not.toContain('Password reset');
    });
  });

  describe('returnUrl', () => {
    async function loginWith(returnUrl: string | null): Promise<jasmine.Spy> {
      TestBed.inject(ActivatedRoute).snapshot = {
        queryParamMap: convertToParamMap(returnUrl ? { returnUrl } : {}),
      } as ActivatedRouteSnapshot;
      const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
      jasmine.clock().install();
      await render('en');
      const component = fixture.componentInstance;
      component.loginForm.setValue({ email: 'awa@example.com', password: 'secret' });
      component.handleSubmit();
      httpMock.expectOne(`${environment.apiUrl}/auth`).flush({ accessToken: 'a', refreshToken: 'r' });
      httpMock.expectOne(`${environment.apiUrl}/users/email/awa@example.com`).flush({});
      jasmine.clock().tick(1000);
      return navigate;
    }

    afterEach(() => jasmine.clock().uninstall());

    it('sends the visitor back to the page they asked for', async () => {
      const navigate = await loginWith('/en/schools/42');

      expect(navigate).toHaveBeenCalledWith('/en/schools/42');
    });

    it('ignores a returnUrl that leaves the site', async () => {
      const navigate = await loginWith('//evil.example.com');

      expect(navigate).not.toHaveBeenCalledWith('//evil.example.com');
    });
  });

  describe('design system', () => {
    it('uses the shared field-input class on both inputs', async () => {
      await render('en');
      const el = fixture.nativeElement as HTMLElement;

      expect(el.querySelector('#email')?.classList).toContain('field-input');
      expect(el.querySelector('#password')?.classList).toContain('field-input');
    });

    it('renders the submit as a full-width large primary button', async () => {
      await render('en');
      const submit = (fixture.nativeElement as HTMLElement).querySelector('button[type="submit"]');

      for (const c of ['btn', 'btn-primary', 'btn-lg', 'w-full']) {
        expect(submit?.classList).toContain(c);
      }
    });

    it('flags an invalid touched email with aria-invalid', async () => {
      await render('en');
      const email = (fixture.nativeElement as HTMLElement).querySelector('#email') as HTMLInputElement;
      expect(email.getAttribute('aria-invalid')).toBeNull();

      fixture.componentInstance.loginForm.get('email')?.setValue('nope');
      fixture.componentInstance.loginForm.get('email')?.markAsTouched();
      fixture.detectChanges();

      expect(email.getAttribute('aria-invalid')).toBe('true');
    });

    it('links each input to its error message only while the error is shown', async () => {
      await render('en');
      const el = fixture.nativeElement as HTMLElement;
      const email = el.querySelector('#email') as HTMLInputElement;
      const password = el.querySelector('#password') as HTMLInputElement;
      expect(email.getAttribute('aria-describedby')).toBeNull();
      expect(password.getAttribute('aria-describedby')).toBeNull();

      fixture.componentInstance.loginForm.get('email')?.setValue('nope');
      fixture.componentInstance.loginForm.markAllAsTouched();
      fixture.detectChanges();

      for (const input of [email, password]) {
        const id = input.getAttribute('aria-describedby');
        expect(id).withContext(input.id).toBeTruthy();
        const message = el.querySelector(`#${id}`);
        expect(message?.getAttribute('role')).withContext(input.id).toBe('alert');
      }
    });

    it('announces the failure banner with role="alert"', async () => {
      await render('en');
      const el = fixture.nativeElement as HTMLElement;
      fixture.componentInstance.loginForm.setValue({ email: 'awa@example.com', password: 'wrong' });
      fixture.componentInstance.handleSubmit();
      httpMock.expectOne(`${environment.apiUrl}/auth`).flush({}, { status: 401, statusText: 'Unauthorized' });
      fixture.detectChanges();
      await fixture.whenStable();

      const banner = Array.from(el.querySelectorAll('[role="alert"]')).find((b) =>
        b.textContent?.includes('Incorrect email or password'),
      );
      expect(banner).toBeDefined();
    });

    it('gives the browser autocomplete hints', async () => {
      await render('en');
      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('#email')?.getAttribute('autocomplete')).toBe('username');
      expect(el.querySelector('#password')?.getAttribute('autocomplete')).toBe('current-password');
    });

    it('no longer centres a lone 64px icon', async () => {
      await render('en');

      expect((fixture.nativeElement as HTMLElement).querySelector('.w-16')).toBeNull();
    });
  });
});
