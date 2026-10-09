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
  it('shows its own failure text, never the API message', async () => {
    await render('en');
    const component = fixture.componentInstance;
    component.loginForm.setValue({ email: 'awa@example.com', password: 'wrong' });

    component.handleSubmit();
    httpMock
      .expectOne(`${environment.apiUrl}/auth`)
      .flush({ message: 'Bad credentials from Spring' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(text()).toContain('Login failed. Please try again.');
    expect(text()).not.toContain('Bad credentials from Spring');
  });

  it('names the OAuth provider inside the translated sentence', async () => {
    jasmine.clock().install();
    try {
      await render('fr');
      fixture.componentInstance.handleOAuthLogin('google');
      jasmine.clock().tick(1500);
      fixture.detectChanges();

      expect(text()).toContain('Connecté avec Google !');
    } finally {
      jasmine.clock().uninstall();
    }
  });

  describe('after registration', () => {
    function arriveFromRegistration(registered: boolean) {
      TestBed.inject(ActivatedRoute).snapshot = {
        queryParamMap: convertToParamMap(registered ? { registered: '1' } : {}),
      } as ActivatedRouteSnapshot;
    }

    it('welcomes a new account in French', async () => {
      arriveFromRegistration(true);
      await render('fr');

      expect(text()).toContain('Compte créé');
    });

    it('welcomes a new account in English', async () => {
      arriveFromRegistration(true);
      await render('en');

      expect(text()).toContain('Account created');
    });

    it('shows no notice on a plain visit', async () => {
      arriveFromRegistration(false);
      await render('en');

      expect(text()).not.toContain('Account created');
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
});
