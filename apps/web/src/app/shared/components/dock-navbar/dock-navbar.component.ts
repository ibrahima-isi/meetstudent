import { DOCUMENT } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { LucideAngularModule, Menu, X } from 'lucide-angular';
import { filter } from 'rxjs';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { LanguageSwitcherComponent } from '../language-switcher/language-switcher.component';
import { ThemeToggleComponent } from '../theme-toggle/theme-toggle.component';
import { nextDockVisible, SCROLL_DELTA_PX } from './dock-visibility';

/**
 * The floating pill used by every public page. It hides on scroll down and comes
 * back on scroll up, when the pointer nears the top edge, while focus is inside
 * it, or while the mobile menu is open (see `nextDockVisible`).
 *
 * Scroll and pointer positions are only read inside event handlers, never at
 * construction, so nothing here touches `window` during SSR. Slow scrolling is
 * measured against `anchorY`, the position of the last decision, so many small
 * steps add up instead of each being dismissed as jitter.
 */
@Component({
  selector: 'app-dock-navbar',
  imports: [RouterLink, TranslocoDirective, LucideAngularModule, LanguageSwitcherComponent, ThemeToggleComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(window:scroll)': 'onScroll()',
    '(document:pointermove)': 'onPointer($event)',
    '(window:mouseout)': 'onMouseOut($event)',
    '(window:blur)': 'forgetPointer()',
    '(document:keydown.escape)': 'closeMenu()',
    '(pointerdown)': 'mouseDriven = true',
    '(keydown)': 'mouseDriven = false',
    '(focusin)': 'onFocusIn()',
    '(focusout)': 'onFocusOut($event)',
  },
  styles: [
    `
      :host { display: contents; }
      .wrap { position: fixed; inset: 0 0 auto 0; z-index: 50; display: flex; flex-direction: column; align-items: center; padding: 0.75rem 1rem 0; pointer-events: none; }
      .dock {
        pointer-events: auto; display: flex; align-items: center; gap: 0.5rem; width: 100%; max-width: 64rem; height: 3.5rem;
        padding: 0 0.5rem 0 1rem; border-radius: 9999px; background: var(--glass); border: 1px solid var(--glass-border);
        backdrop-filter: blur(16px) saturate(1.6); box-shadow: 0 8px 32px -12px rgb(0 0 0 / 0.25);
        transition: transform 0.35s cubic-bezier(0.2, 0.7, 0.2, 1), opacity 0.25s;
      }
      @supports not (backdrop-filter: blur(1px)) { .dock { background: var(--card); } }
      .dock[data-hidden='true'] { transform: translateY(-160%); opacity: 0; }
      .brand { display: inline-flex; align-items: center; gap: 0.5rem; font-weight: 600; letter-spacing: -0.02em; color: var(--foreground); text-decoration: none; }
      .logo { display: inline-grid; place-items: center; width: 1.75rem; height: 1.75rem; border-radius: 0.5rem; background: var(--brand); color: var(--brand-foreground); font-size: 0.9rem; }
      .links { display: none; margin-left: 1rem; gap: 0.25rem; }
      .links a, .panel a.item { padding: 0.45rem 0.8rem; border-radius: 9999px; font-size: 0.9rem; color: var(--muted-foreground); text-decoration: none; transition: background-color 0.15s, color 0.15s; }
      .links a:hover, .panel a.item:hover { background: var(--accent); color: var(--foreground); }
      .actions { display: flex; align-items: center; gap: 0.25rem; margin-left: auto; }
      .desktop-only { display: none; }
      .avatar { display: inline-grid; place-items: center; width: 2.25rem; height: 2.25rem; border-radius: 9999px; background: var(--brand-soft); color: var(--brand-soft-foreground); font-size: 0.8rem; font-weight: 600; }
      .panel { pointer-events: auto; width: 100%; max-width: 64rem; margin-top: 0.5rem; display: flex; flex-direction: column; gap: 0.25rem; padding: 0.75rem; border-radius: 1.5rem; background: var(--card); border: 1px solid var(--border-strong); box-shadow: 0 16px 48px -16px rgb(0 0 0 / 0.35); }
      .panel a.item { font-size: 1rem; padding: 0.75rem 1rem; }
      @media (min-width: 48rem) { .links { display: flex; } .desktop-only { display: inline-flex; } .menu-btn, .panel { display: none; } }
      @media (prefers-reduced-motion: reduce) { .dock { transition: none; } }
    `,
  ],
  template: `
    <header class="wrap" *transloco="let t">
      <nav class="dock" [attr.data-hidden]="!visible()" [attr.aria-label]="t('nav.primary')">
        <a class="brand" [routerLink]="['/', lang()]" [attr.aria-label]="t('nav.brandHome')">
          <span class="logo" aria-hidden="true">M</span>
          <span>MeetStudent</span>
        </a>
        <div class="links">
          <a [routerLink]="['/', lang(), 'schools']">{{ t('nav.schools') }}</a>
          <a [routerLink]="['/', lang()]" fragment="how-it-works">{{ t('nav.howItWorks') }}</a>
          <a [routerLink]="['/', lang()]" fragment="reviews">{{ t('nav.reviews') }}</a>
        </div>
        <div class="actions">
          <app-language-switcher [compact]="true" />
          <app-theme-toggle />
          @if (signedIn()) {
            <a class="btn btn-primary desktop-only" [routerLink]="['/', lang(), 'home']">{{ t('nav.mySpace') }}</a>
            <span class="avatar" aria-hidden="true">{{ initials() }}</span>
          } @else {
            <a class="btn btn-ghost desktop-only" [routerLink]="['/', lang(), 'login']">{{ t('nav.login') }}</a>
            <a class="btn btn-primary desktop-only" [routerLink]="['/', lang(), 'register']">{{ t('nav.register') }}</a>
          }
          <button
            type="button"
            class="btn btn-ghost menu-btn"
            aria-controls="dock-menu"
            [attr.aria-expanded]="menuOpen()"
            [attr.aria-label]="t(menuOpen() ? 'nav.closeMenu' : 'nav.openMenu')"
            (click)="toggleMenu()"
          >
            <lucide-icon [img]="menuOpen() ? X : Menu" class="h-5 w-5" aria-hidden="true" />
          </button>
        </div>
      </nav>
      @if (menuOpen()) {
        <div class="panel" id="dock-menu">
          <a class="item" [routerLink]="['/', lang(), 'schools']">{{ t('nav.schools') }}</a>
          <a class="item" [routerLink]="['/', lang()]" fragment="how-it-works">{{ t('nav.howItWorks') }}</a>
          <a class="item" [routerLink]="['/', lang()]" fragment="reviews">{{ t('nav.reviews') }}</a>
          @if (signedIn()) {
            <a class="btn btn-primary btn-lg" [routerLink]="['/', lang(), 'home']">{{ t('nav.mySpace') }}</a>
          } @else {
            <a class="btn btn-secondary btn-lg" [routerLink]="['/', lang(), 'login']">{{ t('nav.login') }}</a>
            <a class="btn btn-primary btn-lg" [routerLink]="['/', lang(), 'register']">{{ t('nav.register') }}</a>
          }
        </div>
      }
    </header>
  `,
})
export class DockNavbarComponent {
  private readonly document = inject(DOCUMENT);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly router = inject(Router);
  private readonly token = inject(TokenService);
  private readonly locale = inject(LocaleService);

  protected readonly Menu = Menu;
  protected readonly X = X;
  protected readonly lang = this.locale.active;
  protected readonly menuOpen = signal(false);
  protected readonly focusWithin = signal(false);
  protected readonly visible = signal(true);

  /**
   * The session lives in localStorage, which the server cannot read, so the
   * server always renders the anonymous bar. Reading the token before the first
   * client render would produce different markup and break hydration; `hydrated`
   * flips after it.
   */
  private readonly hydrated = signal(false);
  protected readonly signedIn = computed(() => this.hydrated() && this.token.isAuthenticated());
  protected readonly initials = computed(() => {
    const user = this.token.user();
    return `${user?.firstname?.[0] ?? ''}${user?.lastname?.[0] ?? ''}`.toUpperCase() || '?';
  });

  /** Scroll position at the last visibility decision; small moves accumulate against it. */
  private anchorY = 0;
  private pointerY: number | null = null;
  /** True after a mouse press, false after a key press: only keyboard focus pins the bar. */
  protected mouseDriven = false;

  constructor() {
    afterNextRender(() => {
      this.anchorY = this.scrollY();
      this.hydrated.set(true);
    });
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.menuOpen.set(false));
  }

  protected onScroll(): void {
    const y = this.scrollY();
    this.update(y);
    if (Math.abs(y - this.anchorY) > SCROLL_DELTA_PX) {
      this.anchorY = y;
    }
  }

  protected onPointer(event: PointerEvent): void {
    if (event.pointerType !== 'mouse') {
      return;
    }
    this.pointerY = event.clientY;
    this.update(this.scrollY());
  }

  protected onMouseOut(event: MouseEvent): void {
    if (event.relatedTarget === null) {
      this.forgetPointer();
    }
  }

  protected forgetPointer(): void {
    this.pointerY = null;
  }

  protected onFocusIn(): void {
    this.focusWithin.set(!this.mouseDriven);
  }

  protected onFocusOut(event: FocusEvent): void {
    if (!this.host.contains(event.relatedTarget as Node | null)) {
      this.focusWithin.set(false);
    }
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
    this.visible.set(true);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  private update(scrollY: number): void {
    this.visible.set(
      nextDockVisible({
        scrollY,
        previousScrollY: this.anchorY,
        pointerY: this.pointerY,
        focusWithin: this.focusWithin(),
        menuOpen: this.menuOpen(),
        visible: this.visible(),
      }),
    );
  }

  private scrollY(): number {
    return this.document.defaultView?.scrollY ?? 0;
  }
}
