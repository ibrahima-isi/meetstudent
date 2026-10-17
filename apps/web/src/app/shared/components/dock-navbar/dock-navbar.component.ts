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
  viewChildren,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { ChevronDown, LucideAngularModule, Menu, X } from 'lucide-angular';
import { filter } from 'rxjs';
import { AuthService } from '@services/auth.service';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { WishlistService } from '@services/wishlist.service';
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
    '(document:keydown)': 'onKeyDown()',
    '(focusin)': 'onFocusIn()',
    '(focusout)': 'onFocusOut($event)',
  },
  styles: [
    `
      :host { display: contents; }
      /* The bar measures itself, not the window: the full menu shows only where it fits. */
      .wrap { container-type: inline-size; position: fixed; inset: 0 0 auto 0; z-index: 50; display: flex; flex-direction: column; align-items: center; padding: 0.75rem 1rem 0; pointer-events: none; }
      .dock {
        position: relative; pointer-events: auto; display: flex; align-items: center; gap: 0.5rem; width: 100%; min-width: max-content; max-width: 72rem; height: 3.5rem;
        padding: 0 0.5rem 0 1rem; border-radius: 9999px; background: var(--glass); border: 1px solid var(--glass-border);
        backdrop-filter: blur(16px) saturate(1.6); box-shadow: 0 8px 32px -12px rgb(0 0 0 / 0.25);
        transition: transform 0.35s cubic-bezier(0.2, 0.7, 0.2, 1), opacity 0.25s;
      }
      @supports not (backdrop-filter: blur(1px)) { .dock { background: var(--card); } }
      .dock[data-hidden='true'] { transform: translateY(-160%); opacity: 0; }
      .dock > *, .actions > * { flex-shrink: 0; }
      .brand, .links a, .dock .btn { white-space: nowrap; }
      .brand { display: inline-flex; align-items: center; gap: 0.5rem; font-weight: 600; letter-spacing: -0.02em; color: var(--foreground); text-decoration: none; }
      .logo { display: inline-grid; place-items: center; width: 1.75rem; height: 1.75rem; border-radius: 0.5rem; background: var(--brand); color: var(--brand-foreground); font-size: 0.9rem; }
      .links { display: none; margin-left: 1rem; gap: 0.25rem; }
      .links a, .panel a.item { padding: 0.45rem 0.8rem; border-radius: 9999px; font-size: 0.9rem; color: var(--muted-foreground); text-decoration: none; transition: background-color 0.15s, color 0.15s; }
      .links a { padding: 0.45rem 0.55rem; }
      .links a:hover, .panel a.item:hover { background: var(--accent); color: var(--foreground); }
      .discover { position: relative; display: inline-flex; }
      .discover-trigger { display: inline-flex; align-items: center; gap: 0.25rem; border: 0; background: transparent; cursor: pointer; padding: 0.45rem 0.55rem; border-radius: 9999px; font: inherit; font-size: 0.9rem; color: var(--muted-foreground); transition: background-color 0.15s, color 0.15s; }
      .discover-trigger:hover, .discover-trigger[aria-expanded='true'] { background: var(--accent); color: var(--foreground); }
      .discover-trigger:focus-visible, .discover-panel a:focus-visible { outline: 2px solid var(--brand); outline-offset: 2px; }
      .discover-panel { position: absolute; top: calc(100% + 0.9rem); left: 0; z-index: 1; min-width: 13rem; max-width: calc(100vw - 2rem); display: flex; flex-direction: column; gap: 0.15rem; padding: 0.5rem; border-radius: 1rem; background: var(--card); border: 1px solid var(--border-strong); box-shadow: 0 16px 48px -16px rgb(0 0 0 / 0.35); }
      /* Scoped under .links so it outranks the bar's own ".links a" rules, container queries included. */
      .links .discover-panel a { display: block; padding: 0.55rem 0.9rem; border-radius: 0.75rem; font-size: 0.95rem; color: var(--foreground); text-decoration: none; white-space: nowrap; }
      .links .discover-panel a:hover { background: var(--accent); }
      .forum-soon { display: inline-flex; align-items: center; gap: 0.4rem; padding: 0.45rem 0.8rem; font-size: 0.9rem; color: var(--muted-foreground); opacity: 0.7; cursor: not-allowed; user-select: none; }
      .panel .forum-soon { font-size: 1rem; padding: 0.75rem 1rem; }
      .soon-badge { padding: 0.05rem 0.45rem; border-radius: 9999px; background: var(--brand-soft); color: var(--brand-soft-foreground); font-size: 0.7rem; font-weight: 600; }
      /* Narrower bars shrink the badge to a dot (the entry's title and aria-label still say "coming soon"), never hide it: a greyed entry needs a cue. */
      @container (max-width: 89.99rem) { .links { gap: 0.1rem; } .discover-trigger { padding-inline: 0.35rem; } .links a, .links .forum-soon { padding-inline: 0.35rem; } .links .forum-soon { gap: 0.25rem; } .links .soon-badge { width: 0.4rem; height: 0.4rem; padding: 0; overflow: hidden; font-size: 0; background: var(--brand); } }
      .actions { display: flex; align-items: center; gap: 0.25rem; margin-left: auto; }
      .wide-only { display: none; }
      @container (max-width: 39.99rem) { .collapse-xs { display: none; } }
      .avatar { border: 0; cursor: pointer; display: inline-grid; place-items: center; width: 2.25rem; height: 2.25rem; border-radius: 9999px; background: var(--brand-soft); color: var(--brand-soft-foreground); font-size: 0.8rem; font-weight: 600; }
      .avatar:focus-visible, .account-panel .item:focus-visible { outline: 2px solid var(--brand); outline-offset: 2px; }
      .account-panel { position: absolute; top: calc(100% + 0.5rem); right: 0; z-index: 1; min-width: 12rem; display: flex; flex-direction: column; gap: 0.25rem; padding: 0.5rem; border-radius: 1rem; background: var(--card); border: 1px solid var(--border-strong); box-shadow: 0 16px 48px -16px rgb(0 0 0 / 0.35); }
      .account-panel .item { display: block; width: 100%; text-align: left; border: 0; background: transparent; cursor: pointer; padding: 0.6rem 0.9rem; border-radius: 0.75rem; font-size: 0.95rem; color: var(--foreground); text-decoration: none; }
      .account-panel .item:hover { background: var(--accent); }
      .hint { padding: 0.5rem 0.9rem 0; font-size: 0.8rem; color: var(--muted-foreground); }
      .account-panel .hint { margin-top: 0.25rem; border-top: 1px solid var(--border); }
      .settings { display: flex; align-items: flex-start; justify-content: space-between; gap: 0.5rem; padding: 0.5rem 0.25rem; border-top: 1px solid var(--border); border-bottom: 1px solid var(--border); margin: 0.25rem 0; }
      .panel { pointer-events: auto; width: 100%; max-width: 72rem; max-height: calc(100dvh - 5.5rem); overflow-y: auto; margin-top: 0.5rem; display: flex; flex-direction: column; gap: 0.25rem; padding: 0.75rem; border-radius: 1.5rem; background: var(--card); border: 1px solid var(--border-strong); box-shadow: 0 16px 48px -16px rgb(0 0 0 / 0.35); }
      .brand:focus-visible, .links a:focus-visible, .panel a.item:focus-visible { outline: 2px solid var(--brand); outline-offset: 2px; }
      .panel a.item { font-size: 1rem; padding: 0.75rem 1rem; }
      @container (min-width: 73rem) { .links { display: flex; } .wide-only { display: inline-flex; } .menu-btn, .panel { display: none; } }
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
          <a [routerLink]="['/', lang(), 'rankings', 'schools']">{{ t('nav.rankings') }}</a>
          <div class="discover" (focusout)="onDiscoverFocusOut($event)">
            <button
              type="button"
              class="discover-trigger"
              [attr.aria-expanded]="discoverOpen()"
              [attr.aria-controls]="discoverOpen() ? 'discover-menu' : null"
              (click)="toggleDiscover()"
            >
              {{ t('nav.discover') }}
              <lucide-icon [img]="ChevronDown" class="h-4 w-4" aria-hidden="true" />
            </button>
            @if (discoverOpen()) {
              <div class="discover-panel" id="discover-menu" (click)="discoverOpen.set(false)">
                <a [routerLink]="['/', lang()]" fragment="how-it-works">{{ t('nav.howItWorks') }}</a>
                <a [routerLink]="['/', lang(), 'programs']">{{ t('nav.discoverPrograms') }}</a>
                <a [routerLink]="['/', lang(), 'courses']">{{ t('nav.discoverCourses') }}</a>
                <a [routerLink]="['/', lang(), 'accreditations']">{{ t('nav.discoverAccreditations') }}</a>
              </div>
            }
          </div>
          <a [routerLink]="['/', lang()]" fragment="reviews">{{ t('nav.reviews') }}</a>
          <a [routerLink]="['/', lang(), 'about']">{{ t('nav.about') }}</a>
          <a [routerLink]="['/', lang(), 'contact']">{{ t('nav.contact') }}</a>
          <span class="forum-soon" data-testid="forum-soon" role="link" aria-disabled="true" tabindex="-1" [attr.title]="t('nav.comingSoon')" [attr.aria-label]="t('nav.forum') + ' – ' + t('nav.comingSoon')">{{ t('nav.forum') }} <span class="soon-badge">{{ t('nav.comingSoon') }}</span></span>
        </div>
        <div class="actions">
          <span class="wide-only switchers">
            <app-language-switcher [compact]="true" (openChange)="onLanguageOpen($event)" />
            <app-theme-toggle />
          </span>
          @if (signedIn()) {
            <a class="btn btn-primary wide-only" [routerLink]="['/', lang(), 'home']">{{ t('nav.mySpace') }}</a>
            @if (canWishlist()) {
              <app-wishlist-cart [compact]="true" (openChange)="onWishlistOpen($event)" />
            }
            <button
              type="button"
              class="avatar account-trigger"
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
            <button
              type="button"
              class="btn btn-primary wide-only account-trigger"
              [attr.aria-expanded]="accountOpen()"
              [attr.aria-controls]="accountOpen() ? 'account-menu' : null"
              (click)="toggleAccount()"
            >
              {{ t('nav.login') }}
              <lucide-icon [img]="ChevronDown" class="h-4 w-4" aria-hidden="true" />
            </button>
            @if (accountOpen()) {
              <nav class="account-panel" id="account-menu" [attr.aria-label]="t('nav.account')">
                <a class="item" [routerLink]="['/', lang(), 'login']">{{ t('nav.login') }}</a>
                <p class="hint">{{ t('nav.noAccount') }}</p>
                <a class="item" [routerLink]="['/', lang(), 'register']">{{ t('nav.register') }}</a>
              </nav>
            }
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
          <a class="item" [routerLink]="['/', lang(), 'rankings', 'schools']">{{ t('nav.rankings') }}</a>
          <a class="item" [routerLink]="['/', lang()]" fragment="how-it-works">{{ t('nav.discover') }}</a>
          <a class="item" [routerLink]="['/', lang(), 'programs']">{{ t('nav.discoverPrograms') }}</a>
          <a class="item" [routerLink]="['/', lang(), 'courses']">{{ t('nav.discoverCourses') }}</a>
          <a class="item" [routerLink]="['/', lang(), 'accreditations']">{{ t('nav.discoverAccreditations') }}</a>
          <a class="item" [routerLink]="['/', lang()]" fragment="reviews">{{ t('nav.reviews') }}</a>
          <a class="item" [routerLink]="['/', lang(), 'about']">{{ t('nav.about') }}</a>
          <a class="item" [routerLink]="['/', lang(), 'contact']">{{ t('nav.contact') }}</a>
          <span class="forum-soon item" data-testid="forum-soon" role="link" aria-disabled="true" tabindex="-1" [attr.aria-label]="t('nav.forum') + ' – ' + t('nav.comingSoon')">{{ t('nav.forum') }} <span class="soon-badge">{{ t('nav.comingSoon') }}</span></span>
          <div class="settings"><app-language-switcher (openChange)="onLanguageOpen($event)" /><app-theme-toggle /></div>
          @if (signedIn()) {
            <a class="btn btn-primary btn-lg" [routerLink]="['/', lang(), 'home']">{{ t('nav.mySpace') }}</a>
            <a class="item" [routerLink]="['/', lang(), 'profile']">{{ t('nav.profile') }}</a>
            <button type="button" class="btn btn-secondary btn-lg" (click)="logout()">{{ t('nav.logout') }}</button>
          } @else {
            <a class="btn btn-primary btn-lg" [routerLink]="['/', lang(), 'login']">{{ t('nav.login') }}</a>
            <p class="hint">{{ t('nav.noAccount') }}</p>
            <a class="btn btn-secondary btn-lg" [routerLink]="['/', lang(), 'register']">{{ t('nav.register') }}</a>
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
  private readonly wishlist = inject(WishlistService);
  private readonly locale = inject(LocaleService);
  /** Resolved on logout only: the anonymous bar (and its host specs) need no HTTP stack. The wishlist resets itself when the session user goes away. */
  private readonly injector = inject(Injector);
  private readonly cart = viewChild(WishlistCartComponent);
  private readonly languages = viewChildren(LanguageSwitcherComponent);

  protected readonly Menu = Menu;
  protected readonly X = X;
  protected readonly ChevronDown = ChevronDown;
  protected readonly lang = this.locale.active;
  protected readonly menuOpen = signal(false);
  protected readonly accountOpen = signal(false);
  protected readonly discoverOpen = signal(false);
  protected readonly wishlistOpen = signal(false);
  protected readonly languageOpen = signal(false);
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
  protected readonly canWishlist = this.wishlist.available;
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
        this.discoverOpen.set(false);
        this.wishlistOpen.set(false);
        this.closeLanguages();
      });
    effect(() => {
      // The account menu belongs to whoever is browsing: it does not survive a change of session.
      this.signedIn();
      this.wishlistOpen.set(false);
      this.accountOpen.set(false);
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

  /** A listener expression must not evaluate to `false`: Angular would cancel the key's default action. */
  protected onKeyDown(): void {
    this.mouseDriven = false;
  }

  protected onPointerDown(event: Event): void {
    this.mouseDriven = true;
    if (!this.host.contains(event.target as Node | null)) {
      this.accountOpen.set(false);
      this.discoverOpen.set(false);
    }
  }

  /** Keyboard focus leaving the button and its links closes the panel; mouse presses are handled by `onPointerDown`. */
  protected onDiscoverFocusOut(event: FocusEvent): void {
    const group = event.currentTarget as HTMLElement;
    if (!this.mouseDriven && !group.contains(event.relatedTarget as Node | null)) {
      this.discoverOpen.set(false);
    }
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
    this.accountOpen.set(false);
    this.discoverOpen.set(false);
    this.cart()?.close();
    this.closeLanguages();
    this.visible.set(true);
  }

  protected toggleAccount(): void {
    this.accountOpen.update((open) => !open);
    this.menuOpen.set(false);
    this.discoverOpen.set(false);
    this.cart()?.close();
    this.closeLanguages();
    this.visible.set(true);
  }

  protected toggleDiscover(): void {
    this.discoverOpen.update((open) => !open);
    this.menuOpen.set(false);
    this.accountOpen.set(false);
    this.cart()?.close();
    this.closeLanguages();
    this.visible.set(true);
  }

  protected onWishlistOpen(open: boolean): void {
    this.wishlistOpen.set(open);
    if (open) {
      this.accountOpen.set(false);
      this.menuOpen.set(false);
      this.discoverOpen.set(false);
      this.closeLanguages();
      this.visible.set(true);
    }
  }

  /** The bar's own list closes the other overlays; the one inside the menu panel lives in it. */
  protected onLanguageOpen(open: boolean): void {
    this.languageOpen.set(this.languages().some((switcher) => switcher.open()));
    if (open && !this.menuOpen()) {
      this.accountOpen.set(false);
      this.cart()?.close();
    }
    if (open) {
      this.discoverOpen.set(false);
      this.visible.set(true);
    }
  }

  private closeLanguages(): void {
    this.languages().forEach((switcher) => switcher.close());
  }

  protected logout(): void {
    this.injector.get(AuthService).logout();
    this.accountOpen.set(false);
    this.discoverOpen.set(false);
    this.menuOpen.set(false);
    void this.router.navigate(['/', this.lang()]);
  }

  protected closeOverlays(): void {
    const active = this.document.activeElement;
    if (this.host.querySelector('#dock-menu')?.contains(active)) {
      this.host.querySelector<HTMLElement>('.menu-btn')?.focus();
    } else if (this.host.querySelector('#account-menu')?.contains(active)) {
      this.host.querySelector<HTMLElement>('.dock .account-trigger')?.focus();
    } else if (this.host.querySelector('#discover-menu')?.contains(active)) {
      this.host.querySelector<HTMLElement>('.discover-trigger')?.focus();
    }
    this.menuOpen.set(false);
    this.accountOpen.set(false);
    this.discoverOpen.set(false);
  }

  private update(scrollY: number): void {
    this.visible.set(
      nextDockVisible({
        scrollY,
        previousScrollY: this.anchorY,
        pointerY: this.pointerY,
        focusWithin: this.focusWithin(),
        menuOpen: this.menuOpen() || this.accountOpen() || this.discoverOpen() || this.wishlistOpen() || this.languageOpen(),
        visible: this.visible(),
      }),
    );
  }

  private scrollY(): number {
    return this.document.defaultView?.scrollY ?? 0;
  }
}
