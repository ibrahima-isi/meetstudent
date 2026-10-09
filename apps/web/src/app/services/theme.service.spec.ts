import { TestBed } from '@angular/core/testing';
import { DOCUMENT } from '@angular/common';
import { PLATFORM_ID, provideZonelessChangeDetection } from '@angular/core';
import { ThemeService } from './theme.service';

type Listener = (event: { matches: boolean }) => void;

interface Harness {
  root: HTMLElement;
  store: Map<string, string>;
  setSystemDark: (dark: boolean) => void;
}

function setup(
  options: {
    stored?: string;
    systemDark?: boolean;
    platform?: 'browser' | 'server';
    storageThrows?: boolean;
    noMatchMedia?: boolean;
  } = {},
): Harness {
  const root = document.createElement('html');
  const store = new Map<string, string>();
  if (options.stored) {
    store.set('theme', options.stored);
  }

  let systemDark = options.systemDark ?? false;
  const listeners: Listener[] = [];

  const localStorage = options.storageThrows
    ? {
        getItem: () => {
          throw new Error('denied');
        },
        setItem: () => {
          throw new Error('denied');
        },
      }
    : {
        getItem: (key: string) => store.get(key) ?? null,
        setItem: (key: string, value: string) => void store.set(key, value),
      };

  const defaultView = {
    localStorage,
    matchMedia: options.noMatchMedia
      ? undefined
      : () => ({
          get matches() {
            return systemDark;
          },
          addEventListener: (_: string, l: Listener) => void listeners.push(l),
          removeEventListener: () => undefined,
        }),
  };

  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      { provide: PLATFORM_ID, useValue: options.platform ?? 'browser' },
      { provide: DOCUMENT, useValue: { documentElement: root, defaultView } },
    ],
  });

  return {
    root,
    store,
    setSystemDark: (dark) => {
      systemDark = dark;
      listeners.forEach((l) => l({ matches: dark }));
    },
  };
}

describe('ThemeService', () => {
  it('defaults to following the system', () => {
    setup();

    expect(TestBed.inject(ThemeService).choice()).toBe('system');
  });

  it('resolves "system" from prefers-color-scheme', () => {
    setup({ systemDark: true });

    expect(TestBed.inject(ThemeService).resolved()).toBe('dark');
  });

  it('resolves to light when the system is light', () => {
    setup({ systemDark: false });

    expect(TestBed.inject(ThemeService).resolved()).toBe('light');
  });

  it('reads a stored choice', () => {
    setup({ stored: 'dark', systemDark: false });
    const service = TestBed.inject(ThemeService);

    expect(service.choice()).toBe('dark');
    expect(service.resolved()).toBe('dark');
  });

  it('ignores a stored value it does not know', () => {
    setup({ stored: 'purple' });

    expect(TestBed.inject(ThemeService).choice()).toBe('system');
  });

  it('persists the choice', () => {
    const h = setup();

    TestBed.inject(ThemeService).set('light');

    expect(h.store.get('theme')).toBe('light');
  });

  it('survives storage throwing on read and write', () => {
    setup({ storageThrows: true });
    const service = TestBed.inject(ThemeService);

    expect(service.choice()).toBe('system');
    expect(() => service.set('dark')).not.toThrow();
    expect(service.choice()).toBe('dark');
  });

  it('survives a browser without matchMedia', () => {
    setup({ noMatchMedia: true });

    expect(TestBed.inject(ThemeService).resolved()).toBe('light');
  });

  it('adds and removes the dark class on <html>, and sets color-scheme', () => {
    const h = setup();
    const service = TestBed.inject(ThemeService);

    service.set('dark');
    expect(h.root.classList.contains('dark')).toBeTrue();
    expect(h.root.style.colorScheme).toBe('dark');

    service.set('light');
    expect(h.root.classList.contains('dark')).toBeFalse();
    expect(h.root.style.colorScheme).toBe('light');
  });

  it('applies the initial theme on construction', () => {
    const h = setup({ stored: 'dark' });
    TestBed.inject(ThemeService);

    expect(h.root.classList.contains('dark')).toBeTrue();
  });

  it('follows system changes while on "system"', () => {
    const h = setup({ systemDark: false });
    const service = TestBed.inject(ThemeService);

    h.setSystemDark(true);

    expect(service.resolved()).toBe('dark');
    expect(h.root.classList.contains('dark')).toBeTrue();
  });

  it('ignores system changes once the choice is explicit', () => {
    const h = setup({ stored: 'light', systemDark: false });
    const service = TestBed.inject(ThemeService);

    h.setSystemDark(true);

    expect(service.resolved()).toBe('light');
    expect(h.root.classList.contains('dark')).toBeFalse();
  });

  it('touches nothing on the server', () => {
    const h = setup({ platform: 'server', systemDark: true });
    const service = TestBed.inject(ThemeService);

    service.set('dark');

    expect(h.root.classList.contains('dark')).toBeFalse();
    expect(h.store.has('theme')).toBeFalse();
    expect(service.choice()).toBe('dark');
  });
});
