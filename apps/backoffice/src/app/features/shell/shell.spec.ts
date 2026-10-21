import { Component, provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { API_URL, WEB_URL } from '@services/api-config';
import { TokenService } from '@services/token.service';
import { Shell } from './shell';

@Component({ template: '' })
class Blank {}

describe('Shell (responsive menu)', () => {
  let fixture: ComponentFixture<Shell>;
  let router: Router;

  const el = () => fixture.nativeElement as HTMLElement;
  const toggle = () => el().querySelector<HTMLButtonElement>('[data-menu-toggle]')!;
  const sidebar = () => el().querySelector<HTMLElement>('#main-sidebar')!;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: 'http://api.test/api/v1' },
        provideRouter([
          { path: 'moderation', component: Blank },
          { path: 'schools', component: Blank },
        ]),
      ],
    });
    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('has a hamburger button that is only shown below md and controls the sidebar', () => {
    expect(toggle()).not.toBeNull();
    expect(toggle().classList).toContain('md:hidden');
    expect(toggle().getAttribute('aria-controls')).toBe('main-sidebar');
    expect(toggle().getAttribute('aria-expanded')).toBe('false');
    expect(sidebar().classList).toContain('md:block');
  });

  it('keeps the sidebar hidden below md until the toggle is clicked', () => {
    expect(sidebar().classList).toContain('hidden');
    toggle().click();
    fixture.detectChanges();
    expect(toggle().getAttribute('aria-expanded')).toBe('true');
    expect(sidebar().classList).not.toContain('hidden');
    expect(el().querySelector('[data-backdrop]')).not.toBeNull();
    toggle().click();
    fixture.detectChanges();
    expect(toggle().getAttribute('aria-expanded')).toBe('false');
    expect(el().querySelector('[data-backdrop]')).toBeNull();
  });

  it('lists a Messages entry in the sidebar', () => {
    const link = Array.from(sidebar().querySelectorAll('a')).find((a) => a.textContent?.trim() === 'Messages');
    expect(link).toBeDefined();
    expect(link!.getAttribute('href')).toBe('/messages');
  });

  it('lists an active Forum entry in the sidebar, no longer "Bientôt"', () => {
    const link = Array.from(sidebar().querySelectorAll('a')).find((a) => a.textContent?.trim() === 'Forum');
    expect(link).toBeDefined();
    expect(link!.getAttribute('href')).toBe('/forum');
    expect(el().querySelector('[data-forum]')).toBeNull();
    expect(el().textContent).not.toContain('Bientôt');
  });

  it('lists an Experts entry in the sidebar', () => {
    const link = Array.from(sidebar().querySelectorAll('a')).find((a) => a.textContent?.trim() === 'Experts');
    expect(link).toBeDefined();
    expect(link!.getAttribute('href')).toBe('/experts');
  });

  it('closes on Escape and gives focus back to the toggle', () => {
    toggle().click();
    fixture.detectChanges();
    el().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(toggle().getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(toggle());
  });

  it('closes on navigation', async () => {
    toggle().click();
    fixture.detectChanges();
    await router.navigateByUrl('/schools');
    fixture.detectChanges();
    expect(toggle().getAttribute('aria-expanded')).toBe('false');
  });

  it('closes when the backdrop is clicked', () => {
    toggle().click();
    fixture.detectChanges();
    el().querySelector<HTMLElement>('[data-backdrop]')!.click();
    fixture.detectChanges();
    expect(toggle().getAttribute('aria-expanded')).toBe('false');
  });

  describe('theme toggle', () => {
    const themeToggle = () => el().querySelector<HTMLButtonElement>('[data-theme-toggle]')!;
    const root = document.documentElement;

    afterEach(() => {
      localStorage.removeItem('theme');
      root.classList.remove('dark');
    });

    it('is a labelled button in the header', () => {
      expect(themeToggle()).not.toBeNull();
      expect(themeToggle().getAttribute('aria-label')).toBe('Basculer le thème');
    });

    it('flips the dark class on <html>, reflects it in aria-pressed and persists', () => {
      const before = root.classList.contains('dark');
      themeToggle().click();
      fixture.detectChanges();
      expect(root.classList.contains('dark')).toBe(!before);
      expect(themeToggle().getAttribute('aria-pressed')).toBe(String(!before));
      expect(localStorage.getItem('theme')).toBe(before ? 'light' : 'dark');
      themeToggle().click();
      fixture.detectChanges();
      expect(root.classList.contains('dark')).toBe(before);
    });
  });

  describe('navbar', () => {
    const navbar = () => el().querySelector<HTMLElement>('header')!;

    it('shows the product name, theme toggle and logout inside the navbar', () => {
      expect(navbar().textContent).toContain('MeetStudent');
      expect(navbar().querySelector('[data-theme-toggle]')).not.toBeNull();
      expect(navbar().querySelector('[data-logout]')).not.toBeNull();
      expect(el().querySelectorAll('[data-theme-toggle]').length).toBe(1);
    });

    afterEach(() => localStorage.removeItem('auth_user'));

    it('shows the signed-in admin identity', () => {
      TestBed.inject(TokenService).setUser({ firstname: 'Awa', lastname: 'Diop' } as never);
      fixture.detectChanges();
      expect(navbar().textContent).toContain('Awa Diop');
    });

    it('logs out and goes back to the login screen', async () => {
      const spy = spyOn(router, 'navigateByUrl').and.resolveTo(true);
      el().querySelector<HTMLButtonElement>('[data-logout]')!.click();
      expect(spy).toHaveBeenCalledWith('/login');
    });
  });

  describe('footer', () => {
    const footer = () => el().querySelector<HTMLElement>('footer')!;

    it('shows the version, a link to the public site and the copyright', () => {
      expect(footer().textContent).toMatch(/v\d+\.\d+\.\d+/);
      expect(footer().textContent).toContain('© MeetStudent');
      const link = footer().querySelector<HTMLAnchorElement>('a[data-public-site]')!;
      expect(link.getAttribute('href')).toBe(TestBed.inject(WEB_URL));
    });
  });

  it('uses the shared design tokens instead of hard-coded palette classes', () => {
    expect(el().innerHTML).not.toMatch(/(bg|text|border)-(slate|white|indigo)/);
    expect(el().querySelector('[data-logout]')!.classList).toContain('btn');
  });
});
