import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  const root = document.documentElement;
  let systemDark = false;
  let originalMatchMedia: typeof window.matchMedia;

  function create(): ThemeService {
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
    return TestBed.inject(ThemeService);
  }

  beforeEach(() => {
    systemDark = false;
    originalMatchMedia = window.matchMedia;
    window.matchMedia = ((query: string) => ({
      matches: systemDark,
      media: query,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    })) as unknown as typeof window.matchMedia;
    localStorage.removeItem('theme');
    root.classList.remove('dark');
    root.style.colorScheme = '';
  });

  afterEach(() => {
    window.matchMedia = originalMatchMedia;
    localStorage.removeItem('theme');
    root.classList.remove('dark');
    root.style.colorScheme = '';
  });

  it('follows the system preference when nothing is stored', () => {
    systemDark = true;
    const theme = create();
    expect(theme.resolved()).toBe('dark');
    expect(root.classList).toContain('dark');
  });

  it('stays light when the system is light and nothing is stored', () => {
    const theme = create();
    expect(theme.resolved()).toBe('light');
    expect(root.classList).not.toContain('dark');
  });

  it('restores a stored choice over the system preference', () => {
    systemDark = true;
    localStorage.setItem('theme', 'light');
    const theme = create();
    expect(theme.choice()).toBe('light');
    expect(root.classList).not.toContain('dark');
  });

  it('ignores an invalid stored value', () => {
    localStorage.setItem('theme', 'purple');
    expect(create().choice()).toBe('system');
  });

  it('set() applies the class and colour scheme and persists the choice', () => {
    const theme = create();
    theme.set('dark');
    expect(root.classList).toContain('dark');
    expect(root.style.colorScheme).toBe('dark');
    expect(localStorage.getItem('theme')).toBe('dark');
    theme.set('light');
    expect(root.classList).not.toContain('dark');
    expect(root.style.colorScheme).toBe('light');
    expect(localStorage.getItem('theme')).toBe('light');
  });

  it('toggle() flips the resolved theme', () => {
    const theme = create();
    theme.toggle();
    expect(theme.resolved()).toBe('dark');
    theme.toggle();
    expect(theme.resolved()).toBe('light');
  });

  it('still works when storage is blocked', () => {
    spyOn(Storage.prototype, 'getItem').and.throwError('denied');
    spyOn(Storage.prototype, 'setItem').and.throwError('denied');
    const theme = create();
    theme.set('dark');
    expect(theme.resolved()).toBe('dark');
    expect(root.classList).toContain('dark');
  });
});
