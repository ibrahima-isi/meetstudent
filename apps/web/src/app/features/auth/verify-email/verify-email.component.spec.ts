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
import { TokenService } from '@services/token.service';
import { environment } from '../../../../environments/environment';
import { VerifyEmailComponent } from './verify-email.component';

const TOKEN = 'tK3n_9aZ-AbCdEfGhIjKlMnOpQrStUvWxYz012345678';

describe('VerifyEmailComponent', () => {
  let fixture: ComponentFixture<VerifyEmailComponent>;
  let httpMock: HttpTestingController;
  let navigate: jasmine.Spy;
  const url = `${environment.apiUrl}/auth/email-verification/confirm`;

  const el = () => fixture.nativeElement as HTMLElement;
  const title = () => el().querySelector('h1')?.textContent?.trim();

  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function render(lang: 'fr' | 'en', fragment: string | null = `token=${TOKEN}`) {
    TestBed.inject(ActivatedRoute).snapshot = { fragment } as ActivatedRouteSnapshot;
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    fixture = TestBed.createComponent(VerifyEmailComponent);
    await settle();
  }

  const ok = () => ({ status: 204, statusText: 'No Content' });

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [VerifyEmailComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco(translocoOptions),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  describe('the token', () => {
    it('is posted once, exactly as read from the fragment, as soon as the page opens', async () => {
      await render('en');

      const req = httpMock.expectOne(url);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ token: TOKEN });
      req.flush(null, ok());
    });

    it('is stripped from the address bar and history as soon as it is read', async () => {
      await render('en');
      httpMock.expectOne(url).flush(null, ok());

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
      httpMock.expectOne(url).flush(null, ok());
      await settle();

      expect(setItem.calls.allArgs().flat().join(' ')).not.toContain(TOKEN);
      for (const spy of consoleSpies) {
        expect(spy.calls.allArgs().flat().join(' ')).not.toContain(TOKEN);
      }
      expect(el().innerHTML).not.toContain(TOKEN);
      expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toContain(TOKEN);
    });

    it('with no token at all, is invalid without calling the API', async () => {
      await render('en', null);

      httpMock.expectNone(url);
      expect(title()).toBe('Invalid link');
      expect(navigate).not.toHaveBeenCalled();
    });

    it('with a fragment that carries no token, is just as invalid', async () => {
      await render('fr', 'other=1');

      httpMock.expectNone(url);
      expect(title()).toBe('Lien invalide');
    });
  });

  describe('the outcome', () => {
    it('announces the verification in progress, politely', async () => {
      await render('en');

      expect(title()).toBe('Confirming your email');
      expect(el().querySelector('[role="status"]')).not.toBeNull();
      httpMock.expectOne(url).flush(null, ok());
    });

    it('confirms the address in French and in English on 204, and offers to sign in when anonymous', async () => {
      await render('fr');
      httpMock.expectOne(url).flush(null, ok());
      await settle();
      expect(title()).toBe('Adresse e-mail confirmée');
      expect(el().querySelector('a[href="/fr/login"]')).not.toBeNull();
      fixture.destroy();

      await render('en');
      httpMock.expectOne(url).flush(null, ok());
      await settle();
      expect(title()).toBe('Email confirmed');
    });

    it('moves focus to the heading once the result is known', async () => {
      await render('en');
      httpMock.expectOne(url).flush(null, ok());
      await settle();

      expect(document.activeElement).toBe(el().querySelector('h1'));
    });

    it('says the link expired on a 400 token error, and tells the user how to get a new one', async () => {
      await render('en');
      httpMock
        .expectOne(url)
        .flush({ token: 'Lien invalide ou expiré' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      expect(title()).toBe('Link expired');
      expect(el().textContent).not.toContain('Lien invalide ou expiré');
      expect(el().querySelector('a[href="/en/login"]')).not.toBeNull();
    });

    it('sends a signed-in visitor home, not to the login, when the link is expired or invalid', async () => {
      const tokens = TestBed.inject(TokenService);
      tokens.setTokens('access', 'refresh');
      await render('en');
      httpMock.expectOne(url).flush({ token: 'x' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      expect(el().querySelector('a[href="/en/login"]')).toBeNull();
      expect(el().querySelector('a[href="/en/home"]')?.textContent).toContain('Go to my space');
    });

    it('says something went wrong, with a retry, on any other failure', async () => {
      await render('en');
      httpMock.expectOne(url).flush(null, { status: 500, statusText: 'Server Error' });
      await settle();

      expect(title()).toBe('Something went wrong');
      const retry = Array.from(el().querySelectorAll('button')).find((b) => /try again/i.test(b.textContent ?? ''));
      expect(retry).toBeDefined();

      retry!.click();
      await settle();
      const again = httpMock.expectOne(url);
      expect(again.request.body).toEqual({ token: TOKEN });
      again.flush(null, ok());
    });

    it('refreshes the cached user after success when someone is signed in', async () => {
      const tokens = TestBed.inject(TokenService);
      tokens.setTokens('access', 'refresh');
      tokens.user.set({ id: 7, email: 'awa@example.com', emailVerified: false } as never);
      await render('en');

      httpMock.expectOne(url).flush(null, ok());
      const profile = httpMock.expectOne(`${environment.apiUrl}/users/id/7`);
      profile.flush({ id: 7, email: 'awa@example.com', emailVerified: true });
      await settle();

      expect(tokens.user()?.emailVerified).toBeTrue();
      expect(el().querySelector('a[href="/en/home"]')).not.toBeNull();
    });

    it('does not ask for the user when anonymous', async () => {
      await render('en');
      httpMock.expectOne(url).flush(null, ok());

      httpMock.expectNone((r) => r.url.includes('/users/'));
    });
  });

  describe('metadata', () => {
    it('asks not to be indexed nor to leak a referrer, and removes both on destroy', async () => {
      const meta = TestBed.inject(Meta);
      await render('en');
      httpMock.expectOne(url).flush(null, ok());

      expect(meta.getTag('name="robots"')?.content).toBe('noindex,nofollow');
      expect(meta.getTag('name="referrer"')?.content).toBe('no-referrer');

      fixture.destroy();
      expect(meta.getTag('name="robots"')).toBeNull();
      expect(meta.getTag('name="referrer"')).toBeNull();
    });
  });

  describe('an email change link (type=change)', () => {
    const changeUrl = `${environment.apiUrl}/auth/email-change/confirm`;
    const fragment = `token=${TOKEN}&type=change`;

    it('confirms the change instead of the verification, with the same token', async () => {
      await render('en', fragment);

      const req = httpMock.expectOne(changeUrl);
      expect(req.request.body).toEqual({ token: TOKEN });
      httpMock.expectNone(url);
      req.flush(null, ok());
    });

    it('says the address was updated, in the language being read', async () => {
      await render('en', fragment);
      httpMock.expectOne(changeUrl).flush(null, ok());
      await settle();

      expect(title()).toBe('Email address updated');
      expect(el().textContent).toContain('replaces the old one');
    });

    it('is in French too', async () => {
      await render('fr', fragment);
      httpMock.expectOne(changeUrl).flush(null, ok());
      await settle();

      expect(title()).toBe('Adresse e-mail mise à jour');
    });

    for (const lang of ['fr', 'en'] as const) {
      it(`ends the local session and invites re-login after an email change in ${lang}`, async () => {
        const tokens = TestBed.inject(TokenService);
        tokens.setTokens('access', 'refresh');
        tokens.setUser({ id: 7, email: 'old@example.com' } as never);
        await render(lang, fragment);
        httpMock.expectOne(changeUrl).flush(null, ok());
        httpMock.expectNone((r) => r.url.includes('/users/'));
        await settle();

        expect(tokens.isAuthenticated()).toBeFalse();
        expect(tokens.refreshToken()).toBeNull();
        expect(tokens.user()).toBeNull();
        expect(localStorage.getItem('auth_token')).toBeNull();
        expect(localStorage.getItem('refresh_token')).toBeNull();
        expect(localStorage.getItem('auth_user')).toBeNull();
        expect(el().querySelector(`a[href="/${lang}/login"]`)).not.toBeNull();
        expect(el().querySelector(`a[href="/${lang}/home"]`)).toBeNull();
      });
    }

    it('keeps the session on network failure and clears it only after a successful retry', async () => {
      const tokens = TestBed.inject(TokenService);
      tokens.setTokens('access', 'refresh');
      tokens.setUser({ id: 7, email: 'old@example.com' } as never);
      await render('en', fragment);
      httpMock.expectOne(changeUrl).error(new ProgressEvent('error'));
      await settle();
      expect(tokens.isAuthenticated()).toBeTrue();
      expect(tokens.user()?.email).toBe('old@example.com');
      el().querySelector<HTMLButtonElement>('button')!.click();
      await settle();
      httpMock.expectOne(changeUrl).flush(null, ok());
      await settle();
      expect(tokens.user()).toBeNull();
      expect(el().querySelector('a[href="/en/login"]')).not.toBeNull();
    });

    it('explains a dead link with the change wording, not the verification one', async () => {
      await render('en', fragment);
      httpMock.expectOne(changeUrl).flush({ token: 'Lien invalide ou expiré' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      expect(title()).toBe('Link expired');
      expect(el().textContent).toContain('ask for a new change from your profile');
    });

    it('still treats a plain verification link as a verification', async () => {
      await render('en', `token=${TOKEN}`);

      httpMock.expectOne(url).flush(null, ok());
      httpMock.expectNone(changeUrl);
    });
  });
});
