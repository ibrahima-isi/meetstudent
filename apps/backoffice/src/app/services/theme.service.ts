import { DOCUMENT } from '@angular/common';
import { computed, inject, Injectable, signal } from '@angular/core';

export type ThemeChoice = 'light' | 'dark' | 'system';
export type ResolvedTheme = 'light' | 'dark';

/** Also read by the inline script in index.html: keep the two in sync. */
export const THEME_STORAGE_KEY = 'theme';

const DARK_QUERY = '(prefers-color-scheme: dark)';

function isChoice(value: unknown): value is ThemeChoice {
  return value === 'light' || value === 'dark' || value === 'system';
}

/**
 * Owns the admin's theme choice and mirrors the resolved theme onto <html>
 * (`dark` class for Tailwind's `dark:` variant, plus `color-scheme`).
 * Same contract as the web app's ThemeService (copied, no shared dependency).
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);

  private readonly selected = signal<ThemeChoice>('system');
  private readonly systemDark = signal(false);

  readonly choice = this.selected.asReadonly();
  readonly resolved = computed<ResolvedTheme>(() => {
    const choice = this.selected();
    if (choice === 'system') {
      return this.systemDark() ? 'dark' : 'light';
    }
    return choice;
  });

  constructor() {
    this.selected.set(this.readStored());
    this.watchSystem();
    this.apply();
  }

  set(choice: ThemeChoice): void {
    this.selected.set(choice);
    try {
      this.document.defaultView?.localStorage.setItem(THEME_STORAGE_KEY, choice);
    } catch {
      // Storage can be blocked or full; the choice still holds for this session.
    }
    this.apply();
  }

  /** Switches to the opposite of the currently resolved theme. */
  toggle(): void {
    this.set(this.resolved() === 'dark' ? 'light' : 'dark');
  }

  private readStored(): ThemeChoice {
    try {
      const stored = this.document.defaultView?.localStorage.getItem(THEME_STORAGE_KEY);
      return isChoice(stored) ? stored : 'system';
    } catch {
      return 'system';
    }
  }

  private watchSystem(): void {
    const query = this.document.defaultView?.matchMedia?.(DARK_QUERY);
    if (!query) {
      return;
    }

    this.systemDark.set(query.matches);
    query.addEventListener('change', (event) => {
      this.systemDark.set(event.matches);
      this.apply();
    });
  }

  private apply(): void {
    const theme = this.resolved();
    const root = this.document.documentElement;
    root.classList.toggle('dark', theme === 'dark');
    root.style.colorScheme = theme;
  }
}
