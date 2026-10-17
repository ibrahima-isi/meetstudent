import { EnvironmentInjector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { appConfig } from './app.config';

describe('appConfig', () => {
  const root = document.documentElement;
  let listeners: ((event: MediaQueryListEvent) => void)[];
  let originalMatchMedia: typeof window.matchMedia;

  beforeEach(() => {
    listeners = [];
    originalMatchMedia = window.matchMedia;
    window.matchMedia = ((query: string) => ({
      matches: false,
      media: query,
      addEventListener: (_: string, listener: (event: MediaQueryListEvent) => void) => listeners.push(listener),
      removeEventListener: () => undefined,
    })) as unknown as typeof window.matchMedia;
    localStorage.removeItem('theme');
    root.classList.remove('dark');
  });

  afterEach(() => {
    window.matchMedia = originalMatchMedia;
    localStorage.removeItem('theme');
    root.classList.remove('dark');
    root.style.colorScheme = '';
  });

  it('starts the theme at boot, so pages without the shell (login, 404) follow live OS changes', () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });
    TestBed.inject(EnvironmentInjector); // builds the root injector, which runs its initializers

    expect(listeners.length).withContext('the OS theme is watched without any page asking for it').toBe(1);
    listeners[0]({ matches: true } as MediaQueryListEvent);
    expect(root.classList).toContain('dark');
  });
});
