import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Meta } from '@angular/platform-browser';
import { ActivatedRoute, ActivatedRouteSnapshot, Router, provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { environment } from '../../../../environments/environment';
import { ResetPasswordComponent } from './reset-password.component';

const TOKEN = 'tK3n_9aZ-AbCdEfGhIjKlMnOpQrStUvWxYz012345678';

describe('ResetPasswordComponent', () => {
  let fixture: ComponentFixture<ResetPasswordComponent>;
  let httpMock: HttpTestingController;
  let router: Router;
  let navigate: jasmine.Spy;
  const url = `${environment.apiUrl}/auth/password-reset/confirm`;

  const el = () => fixture.nativeElement as HTMLElement;
  const text = () => el().textContent ?? '';
  const field = (id: string) => el().querySelector(`#${id}`) as HTMLInputElement;

  async function render(lang: 'fr' | 'en', fragment: string | null = `token=${TOKEN}`) {
    TestBed.inject(ActivatedRoute).snapshot = { fragment } as ActivatedRouteSnapshot;
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    fixture = TestBed.createComponent(ResetPasswordComponent);
    await settle();
  }

  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function type(id: string, value: string) {
    const input = field(id);
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function submit(password: string, confirmation = password) {
    type('password', password);
    type('confirmPassword', confirmation);
    (el().querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await settle();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ResetPasswordComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco(translocoOptions),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    navigate = spyOn(router, 'navigate').and.resolveTo(true);
  });

  afterEach(() => httpMock.verify());

  it('titles the form in French and in English', async () => {
    await render('fr');
    expect(el().querySelector('h1')?.textContent?.trim()).toBe('Nouveau mot de passe');
    fixture.destroy();

    await render('en');
    expect(el().querySelector('h1')?.textContent?.trim()).toBe('New password');
  });

  describe('the token', () => {
    it('is posted, once, exactly as read from the fragment', async () => {
      await render('en');

      await submit('Secret123');

      const req = httpMock.expectOne(url);
      expect(req.request.body).toEqual({ token: TOKEN, password: 'Secret123', confirmedPassword: 'Secret123' });
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('is stripped from the address bar and history as soon as it is read', async () => {
      await render('en');

      expect(navigate).toHaveBeenCalledTimes(1);
      const [commands, extras] = navigate.calls.mostRecent().args;
      expect(commands).toEqual([]);
      expect(extras.replaceUrl).toBeTrue();
      expect(extras.fragment).toBeUndefined();
      expect(extras.queryParamsHandling).toBe('preserve');
    });

    it('never reaches storage, the console or the page text', async () => {
      const setItem = spyOn(Storage.prototype, 'setItem').and.callThrough();
      const consoleSpies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((m) => spyOn(console, m));

      await render('en');
      await submit('Secret123');
      httpMock.expectOne(url).flush(null, { status: 204, statusText: 'No Content' });
      await settle();

      expect(setItem.calls.allArgs().flat().join(' ')).not.toContain(TOKEN);
      for (const spy of consoleSpies) {
        expect(spy.calls.allArgs().flat().join(' ')).not.toContain(TOKEN);
      }
      expect(el().innerHTML).not.toContain(TOKEN);
      expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toContain(TOKEN);
    });

    it('with no token at all, shows the invalid-link state and a way to ask for a new one', async () => {
      await render('en', null);

      expect(el().querySelector('h1')?.textContent?.trim()).toBe('Invalid link');
      expect(el().querySelector('form')).toBeNull();
      expect(el().querySelector('a[href="/en/forgot-password"]')).not.toBeNull();
      expect(navigate).not.toHaveBeenCalled();
    });

    it('with a fragment that carries no token, is just as invalid', async () => {
      await render('fr', 'other=1');

      expect(el().querySelector('h1')?.textContent?.trim()).toBe('Lien invalide');
      expect(el().querySelector('a[href="/fr/forgot-password"]')).not.toBeNull();
    });
  });

  describe('before sending', () => {
    it('blocks a password shorter than 8 characters', async () => {
      await render('en');

      await submit('short');

      httpMock.expectNone(url);
      expect(field('password').getAttribute('aria-invalid')).toBe('true');
      const message = el().querySelector(`#${field('password').getAttribute('aria-describedby')}`);
      expect(message?.getAttribute('role')).toBe('alert');
      expect(message?.textContent).toContain('at least 8 characters');
    });

    it('blocks a password of more than 72 bytes, counting multi-byte characters', async () => {
      await render('en');

      await submit('é'.repeat(40));

      httpMock.expectNone(url);
      expect(text()).toContain('72 bytes');
    });

    it('accepts exactly 72 bytes', async () => {
      await render('en');

      await submit('a'.repeat(72));

      httpMock.expectOne(url).flush(null, { status: 204, statusText: 'No Content' });
    });

    it('blocks a confirmation that differs', async () => {
      await render('en');

      await submit('Secret123', 'Secret124');

      httpMock.expectNone(url);
      expect(field('confirmPassword').getAttribute('aria-invalid')).toBe('true');
      expect(text()).toContain('Passwords do not match');
    });

    it('uses new-password autocomplete on both fields', async () => {
      await render('en');

      expect(field('password').getAttribute('autocomplete')).toBe('new-password');
      expect(field('confirmPassword').getAttribute('autocomplete')).toBe('new-password');
      expect(field('password').classList).toContain('field-input');
    });

    it('describes nothing while no error is shown', async () => {
      await render('en');

      expect(field('password').getAttribute('aria-describedby')).toBeNull();
      expect(field('password').getAttribute('aria-invalid')).toBeNull();
      expect(field('confirmPassword').getAttribute('aria-describedby')).toBeNull();
    });
  });

  describe('the answer', () => {
    it('on 204 goes to the login of the active language with the reset notice flag', async () => {
      await render('fr');
      await submit('Secret123');

      httpMock.expectOne(url).flush(null, { status: 204, statusText: 'No Content' });
      await settle();

      expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'login'], { queryParams: { reset: '1' } });
    });

    it('keeps the submit disabled while the request is pending', async () => {
      await render('en');
      await submit('Secret123');

      expect((el().querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBeTrue();
      httpMock.expectOne(url).flush(null, { status: 204, statusText: 'No Content' });
    });

    it('on a 400 about the token, shows the expired state with a new-link button, and forgets the token', async () => {
      await render('en');
      await submit('Secret123');

      httpMock.expectOne(url).flush({ token: 'Lien invalide ou expiré' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      expect(el().querySelector('h1')?.textContent?.trim()).toBe('Link expired');
      expect(el().querySelector('form')).toBeNull();
      expect(el().querySelector('a[href="/en/forgot-password"]')?.textContent).toContain('Request a new link');
      expect(text()).not.toContain('Lien invalide');
    });

    it('on a 400 about the password, shows our own message under the field and links it', async () => {
      await render('en');
      await submit('Secret123');

      httpMock
        .expectOne(url)
        .flush({ password: 'Texte API brut' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      expect(field('password').getAttribute('aria-invalid')).toBe('true');
      const message = el().querySelector(`#${field('password').getAttribute('aria-describedby')}`);
      expect(message?.textContent).toContain('at least 8 characters');
      expect(text()).not.toContain('Texte API brut');
    });

    it('on a 400 about the confirmation, flags that field', async () => {
      await render('en');
      await submit('Secret123');

      httpMock
        .expectOne(url)
        .flush({ confirmedPassword: 'Texte API brut' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      expect(field('confirmPassword').getAttribute('aria-invalid')).toBe('true');
      expect(field('password').getAttribute('aria-invalid')).toBeNull();
      expect(text()).toContain('Passwords do not match');
      expect(text()).not.toContain('Texte API brut');
    });

    it('on any other failure shows a generic alert and keeps the form', async () => {
      await render('en');
      await submit('Secret123');

      httpMock.expectOne(url).flush(null, { status: 500, statusText: 'Server Error' });
      await settle();

      expect(el().querySelector('form')).not.toBeNull();
      expect(el().querySelector('[role="alert"]')?.textContent).toContain('Something went wrong');
      expect((el().querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBeFalse();
    });
  });

  describe('focus', () => {
    it('moves to the heading when the state changes', async () => {
      await render('en');
      await submit('Secret123');
      httpMock.expectOne(url).flush({ token: 'x' }, { status: 400, statusText: 'Bad Request' });
      await settle();
      await new Promise((resolve) => setTimeout(resolve));

      expect(document.activeElement).toBe(el().querySelector('h1'));
      expect(el().querySelector('h1')?.getAttribute('tabindex')).toBe('-1');
    });
  });

  describe('metadata', () => {
    it('asks not to be indexed and not to leak a referrer, then lets go on destroy', async () => {
      const meta = TestBed.inject(Meta);
      await render('en');

      expect(meta.getTag('name="robots"')?.content).toBe('noindex,nofollow');
      expect(meta.getTag('name="referrer"')?.content).toBe('no-referrer');

      fixture.destroy();
      expect(meta.getTag('name="robots"')).toBeNull();
      expect(meta.getTag('name="referrer"')).toBeNull();
    });
  });
});
