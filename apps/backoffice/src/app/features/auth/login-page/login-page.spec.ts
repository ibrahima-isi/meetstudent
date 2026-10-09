import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { LoginPage } from './login-page';
import { TokenService } from '@services/token.service';
import { API_URL } from '@services/api-config';

const api = 'http://api.test/api/v1';

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  let backend: HttpTestingController;
  let router: Router;
  let el: HTMLElement;

  const setup = async (query: Record<string, string> = {}) => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: api },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: new Map(Object.entries(query)) } } },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture = TestBed.createComponent(LoginPage);
    el = fixture.nativeElement;
    fixture.detectChanges();
  };

  const submit = async (email = 'a@x.io', password = 'pw') => {
    const set = (id: string, v: string) => {
      const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = v;
      input.dispatchEvent(new Event('input'));
    };
    set('email', email);
    set('password', password);
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  };

  const alertText = () => el.querySelector('[role="alert"]')?.textContent ?? '';

  afterEach(() => {
    backend.verify();
    localStorage.clear();
  });

  it('does not submit an empty form', async () => {
    await setup();
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    backend.expectNone(`${api}/auth`);
    expect(alertText()).toBe('');
  });

  it('shows an error on bad credentials', async () => {
    await setup();
    await submit();
    backend.expectOne(`${api}/auth`).flush(null, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    expect(alertText()).toContain('Identifiants incorrects');
  });

  it('shows a network error when the API is unreachable', async () => {
    await setup();
    await submit();
    backend.expectOne(`${api}/auth`).error(new ProgressEvent('error'), { status: 0 });
    fixture.detectChanges();

    expect(alertText()).toContain('Impossible de joindre le serveur');
  });

  it('refuses a non-admin with a clear message and logs them out', async () => {
    await setup();
    await submit();
    backend.expectOne(`${api}/auth`).flush({ accessToken: 'a', refreshToken: 'r' });
    backend.expectOne(`${api}/users/email/a%40x.io`).flush({
      id: 2, firstname: 'S', lastname: 'T', email: 'a@x.io', role: { name: 'ROLE_STUDENT' },
    });
    fixture.detectChanges();

    expect(alertText()).toContain('réservé aux administrateurs');
    expect(TestBed.inject(TokenService).isAuthenticated()).toBeFalse();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('sends an admin to the returnUrl', async () => {
    await setup({ returnUrl: '/schools' });
    await submit();
    backend.expectOne(`${api}/auth`).flush({ accessToken: 'a', refreshToken: 'r' });
    backend.expectOne(`${api}/users/email/a%40x.io`).flush({
      id: 1, firstname: 'A', lastname: 'B', email: 'a@x.io', role: { name: 'ROLE_ADMIN' },
    });

    expect(router.navigateByUrl).toHaveBeenCalledWith('/schools');
  });

  it('ignores an external returnUrl', async () => {
    await setup({ returnUrl: '//evil.example' });
    await submit();
    backend.expectOne(`${api}/auth`).flush({ accessToken: 'a', refreshToken: 'r' });
    backend.expectOne(`${api}/users/email/a%40x.io`).flush({
      id: 1, firstname: 'A', lastname: 'B', email: 'a@x.io', role: { name: 'ROLE_ADMIN' },
    });

    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('explains a forbidden redirect from the guard', async () => {
    await setup({ reason: 'forbidden' });
    expect(alertText()).toContain('réservé aux administrateurs');
  });
});
