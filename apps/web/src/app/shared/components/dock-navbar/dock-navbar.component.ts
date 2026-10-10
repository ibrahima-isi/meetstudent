import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  Injector,
  PLATFORM_ID,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { LucideAngularModule, Menu, X } from 'lucide-angular';
import { filter } from 'rxjs';
import { AuthService } from '@services/auth.service';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { LanguageSwitcherComponent } from '../language-switcher/language-switcher.component';
import { ThemeToggleComponent } from '../theme-toggle/theme-toggle.component';
import { WishlistCartComponent } from '../wishlist-cart/wishlist-cart.component';
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
  imports: [RouterLink, TranslocoDirective, LucideAngularModule, LanguageSwitcherComponent, ThemeToggleComponent, WishlistCartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(window:scroll)': 'onScroll()',
    '(document:pointermove)': 'onPointer($event)',
    '(window:mouseout)': 'onMouseOut($event)',
    '(window:blur)': 'forgetPointer()',
    '(document:keydown.escape)': 'closeOverlays()',
    '(document:pointerdown)': 'onPointerDown($event)',
    '(document:keydown)': 'mouseDriven = false',
    '(focusin)': 'onFocusIn()',
    '(focusout)': 'onFocusOut($event)',
  },
  styles: [
    `
      :host { display: contents; }
      .wrap { position: fixed; inset: 0 0 auto 0; z-index: 50; display: flex; flex-direction: column; align-items: center; padding: 0.75rem 1rem 0; pointer-events: none; }
      .dock {
        position: relative; pointer-events: auto; display: flex; align-items: center; gap: 0.5rem; width: 100%; max-width: 64rem; height: 3.5rem;
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
      .switchers { display: contents; }
      .xs-only { display: contents; }
      @media (max-width: 39.99rem) { .collapse-xs { display: none; } .hide-xs { display: none; } }
      @media (min-width: 40rem) { .xs-only { display: none; } }
      .avatar { border: 0; cursor: pointer; display: inline-grid; place-items: center; width: 2.25rem; height: 2.25rem; border-radius: 9999px; background: var(--brand-soft); color: var(--brand-soft-foreground); font-size: 0.8rem; font-weight: 600; }
      .avatar:focus-visible, .account-panel .item:focus-visible { outline: 2px solid var(--brand); outline-offset: 2px; }
      .account-panel { position: absolute; top: calc(100% + 0.5rem); right: 0; z-index: 1; min-width: 12rem; display: flex; flex-direction: column; gap: 0.25rem; padding: 0.5rem; border-radius: 1rem; background: var(--card); border: 1px solid var(--border-strong); box-shadow: 0 16px 48px -16px rgb(0 0 0 / 0.35); }
      .account-panel .item { display: block; width: 100%; text-align: left; border: 0; background: transparent; cursor: pointer; padding: 0.6rem 0.9rem; border-radius: 0.75rem; font-size: 0.95rem; color: var(--foreground); text-decoration: none; }
      .account-panel .item:hover { background: var(--accent); }
      .panel { pointer-events: auto; width: 100%; max-width: 64rem; margin-top: 0.5rem; display: flex; flex-direction: column; gap: 0.25rem; padding: 0.75rem; border-radius: 1.5rem; background: var(--card); border: 1px solid var(--border-strong); box-shadow: 0 16px 48px -16px rgb(0 0 0 / 0.35); }
      .brand:focus-visible, .links a:focus-visible, .panel a.item:focus-visible { outline: 2px solid var(--brand); outline-offset: 2px; }
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
          <span class="brand-text" [class.collapse-xs]="signedIn()">MeetStudent</span>
        </a>
        <div class="links">
          <a [routerLink]="['/', lang(), 'schools']">{{ t('nav.schools') }}</a>
          <a [routerLink]="['/', lang()]" fragment="how-it-works">{{ t('nav.howItWorks') }}</a>
          <a [routerLink]="['/', lang()]" fragment="reviews">{{ t('nav.reviews') }}</a>
        </div>
        <div class="actions">
          <span class="switchers" [class.hide-xs]="signedIn()">
            <app-language-switcher [compact]="true" />
            <app-theme-toggle />
          </span>
          @if (signedIn()) {
            <a class="btn btn-primary desktop-only" [routerLink]="['/', lang(), 'home']">{{ t('nav.mySpace') }}</a>
            <app-wishlist-cart [compact]="true" (openChange)="onWishlistOpen($event)" />
            <button
              type="button"
              class="avatar"
              [attr.aria-expanded]="accountOpen()"
              [attr.aria-controls]="accountOpen() ? 'account-menu' : null"
              [attr.aria-label]="t('nav.account')"
              (click)="toggleAccount()"
            >
              <span aria-hidden="true">{{ initials() }}</span>
            </button>
            @if (accountOpen()) {
              <nav class="account-panel" id="account-menu" [attr.aria-label]="t('nav.account')">
                <a class="item" [routerLink]="['/', lang(), 'profile']">{{ t('nav.profile') }}</a>
                <button type="button" class="item" (click)="logout()">{{ t('nav.logout') }}</button>
              </nav>
            }
          } @else {
            <a class="btn btn-ghost desktop-only" [routerLink]="['/', lang(), 'login']">{{ t('nav.login') }}</a>
            <a class="btn btn-primary desktop-only" [routerLink]="['/', lang(), 'register']">{{ t('nav.register') }}</a>
          }
          <button
            type="button"
            class="btn btn-ghost menu-btn"
            [attr.aria-controls]="menuOpen() ? 'dock-menu' : null"
            [attr.aria-expanded]="menuOpen()"
            [attr.aria-label]="t(menuOpen() ? 'nav.closeMenu' : 'nav.openMenu')"
            (click)="toggleMenu()"
          >
            <lucide-icon [img]="menuOpen() ? X : Menu" class="h-5 w-5" aria-hidden="true" />
          </button>
        </div>
      </nav>
      @if (menuOpen()) {
        <nav class="panel" id="dock-menu" [attr.aria-label]="t('nav.primary')">
          <a class="item" [routerLink]="['/', lang(), 'schools']">{{ t('nav.schools') }}</a>
          <a class="item" [routerLink]="['/', lang()]" fragment="how-it-works">{{ t('nav.howItWorks') }}</a>
          <a class="item" [routerLink]="['/', lang()]" fragment="reviews">{{ t('nav.reviews') }}</a>
          @if (signedIn()) {
            <div class="xs-only"><app-language-switcher /><app-theme-toggle /></div>
            <a class="btn btn-primary btn-lg" [routerLink]="['/', lang(), 'home']">{{ t('nav.mySpace') }}</a>
            <a class="item" [routerLink]="['/', lang(), 'profile']">{{ t('nav.profile') }}</a>
            <button type="button" class="btn btn-secondary btn-lg" (click)="logout()">{{ t('nav.logout') }}</button>
          } @else {
            <a class="btn btn-secondary btn-lg" [routerLink]="['/', lang(), 'login']">{{ t('nav.login') }}</a>
            <a class="btn btn-primary btn-lg" [routerLink]="['/', lang(), 'register']">{{ t('nav.register') }}</a>
          }
        </nav>
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
  /** Resolved on logout only: the anonymous bar (and its host specs) need no HTTP stack. The wishlist resets itself when the session user goes away. */
  private readonly injector = inject(Injector);
  private readonly cart = viewChild(WishlistCartComponent);

  protected readonly Menu = Menu;
  protected readonly X = X;
  protected readonly lang = this.locale.active;
  protected readonly menuOpen = signal(false);
  protected readonly accountOpen = signal(false);
  protected readonly wishlistOpen = signal(false);
  protected readonly focusWithin = signal(false);
  protected readonly visible = signal(true);

  /**
   * The session lives in localStorage, which the server cannot read, so the
   * server always renders the anonymous bar. Reading the token before the first
   * client render would produce different markup and break hydration; `hydrated`
   * flips after it.
   *
   * Exception: a pure client bootstrap (RenderMode.Client routes) has no server
   * markup to match. Angular stamps `ng-server-context` on server-rendered
   * documents only, so in the browser without it the session can be read at once
   * and the signed-in bar never flashes the anonymous one.
   */
  private readonly hydrated = signal(
    isPlatformBrowser(inject(PLATFORM_ID)) && !inject(DOCUMENT).querySelector('[ng-server-context]'),
  );
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
      .subscribe(() => {
        this.menuOpen.set(false);
        this.accountOpen.set(false);
        this.wishlistOpen.set(false);
      });
    effect(() => {
      if (!this.signedIn()) {
        this.wishlistOpen.set(false);
        this.accountOpen.set(false);
      }
    });
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
    this.update(this.scrollY());
  }

  protected onFocusOut(event: FocusEvent): void {
    if (!this.host.contains(event.relatedTarget as Node | null)) {
      this.focusWithin.set(false);
      // A mouse press can blur to nothing before the click lands (Safari does not focus buttons);
      // outside presses are handled by `onPointerDown`, so only keyboard focus closes the menu here.
      if (!this.mouseDriven) {
        this.accountOpen.set(false);
      }
    }
  }

  protected onPointerDown(event: Event): void {
    this.mouseDriven = true;
    if (this.accountOpen() && !this.host.contains(event.target as Node | null)) {
      this.accountOpen.set(false);
    }
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
    this.accountOpen.set(false);
    this.cart()?.close();
    this.visible.set(true);
  }

  protected toggleAccount(): void {
    this.accountOpen.update((open) => !open);
    this.menuOpen.set(false);
    this.cart()?.close();
    this.visible.set(true);
  }

  protected onWishlistOpen(open: boolean): void {
    this.wishlistOpen.set(open);
    if (open) {
      this.accountOpen.set(false);
      this.menuOpen.set(false);
      this.visible.set(true);
    }
  }

  protected logout(): void {
    this.injector.get(AuthService).logout();
    this.accountOpen.set(false);
    this.menuOpen.set(false);
    void this.router.navigate(['/', this.lang()]);
  }

  protected closeOverlays(): void {
    const active = this.document.activeElement;
    if (this.host.querySelector('#dock-menu')?.contains(active)) {
      this.host.querySelector<HTMLElement>('.menu-btn')?.focus();
    } else if (this.host.querySelector('#account-menu')?.contains(active)) {
      this.host.querySelector<HTMLElement>('.avatar')?.focus();
    }
    this.menuOpen.set(false);
    this.accountOpen.set(false);
  }

  private update(scrollY: number): void {
    this.visible.set(
      nextDockVisible({
        scrollY,
        previousScrollY: this.anchorY,
        pointerY: this.pointerY,
        focusWithin: this.focusWithin(),
        menuOpen: this.menuOpen() || this.accountOpen() || this.wishlistOpen(),
        visible: this.visible(),
      }),
    );
  }

  private scrollY(): number {
    return this.document.defaultView?.scrollY ?? 0;
  }
}
