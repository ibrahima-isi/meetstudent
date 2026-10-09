import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { TokenService } from '@services/token.service';

describe('routes', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
  });
  afterEach(() => localStorage.clear());

  const signInAdmin = () => {
    const tokens = TestBed.inject(TokenService);
    tokens.setTokens('a', 'r');
    tokens.setUser({ id: 1, firstname: 'Ada', lastname: 'Admin', email: 'a@x.io', role: { name: 'ROLE_ADMIN' } });
  };

  it('redirects anonymous visitors to the login', async () => {
    await RouterTestingHarness.create('/schools');
    expect(TestBed.inject(Router).url).toContain('/login');
  });

  it('shows the shell and a placeholder to an admin, defaulting to moderation', async () => {
    signInAdmin();
    const harness = await RouterTestingHarness.create('/');
    expect(TestBed.inject(Router).url).toBe('/moderation');
    const el = harness.routeNativeElement as HTMLElement;
    expect(el.querySelector('nav')).toBeTruthy();
    expect(el.querySelector('h1')?.textContent).toContain('Modération');
    expect(el.textContent).toContain('Ada Admin');
  });

  it('has a page for each section', async () => {
    signInAdmin();
    const expected: Record<string, string> = {
      '/schools': 'Écoles',
      '/programs': 'Filières et cours',
      '/tags': 'Tags et accréditations',
    };
    const harness = await RouterTestingHarness.create();
    for (const [url, title] of Object.entries(expected)) {
      await harness.navigateByUrl(url);
      expect((harness.routeNativeElement as HTMLElement).querySelector('h1')?.textContent).toContain(title);
    }
  });

  it('logs out from the shell', async () => {
    signInAdmin();
    const harness = await RouterTestingHarness.create('/moderation');
    (harness.routeNativeElement as HTMLElement).querySelector<HTMLButtonElement>('button[data-logout]')!.click();
    await harness.fixture.whenStable();

    expect(TestBed.inject(TokenService).isAuthenticated()).toBeFalse();
    expect(TestBed.inject(Router).url).toContain('/login');
  });

  it('renders a 404 page for unknown urls', async () => {
    const harness = await RouterTestingHarness.create('/nope');
    expect((harness.routeNativeElement as HTMLElement).textContent).toContain('Page introuvable');
  });
});
