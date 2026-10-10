import { Component, provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { AuthService } from '@services/auth.service';
import { TokenService } from '@services/token.service';
import { WishlistService } from '@services/wishlist.service';
import { REVEAL_ZONE_PX } from './dock-visibility';
import { DockNavbarComponent } from './dock-navbar.component';

@Component({ template: '' })
class BlankComponent {}

describe('DockNavbarComponent', () => {
  let fixture: ComponentFixture<DockNavbarComponent>;
  let authenticated: ReturnType<typeof signal<boolean>>;
  let scrollY: number;
  let logout: jasmine.Spy;

  const root = () => fixture.nativeElement as HTMLElement;
  const nav = () => root().querySelector('nav') as HTMLElement;
  const link = (href: string) => root().querySelector(`a[href="${href}"]`);
  const isHidden = () => nav().getAttribute('data-hidden') === 'true';
  const scrollTo = (y: number) => {
    scrollY = y;
    window.dispatchEvent(new Event('scroll'));
    fixture.detectChanges();
  };
  const pointer = (clientY: number, pointerType = 'mouse') => {
    document.dispatchEvent(new PointerEvent('pointermove', { clientY, pointerType }));
    fixture.detectChanges();
  };
  const languageButton = () => root().querySelector('app-language-switcher button') as HTMLElement;

  async function render(): Promise<void> {
    fixture = TestBed.createComponent(DockNavbarComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  /** Leaves the bar hidden, with the scroll anchor at 900. */
  const hide = () => {
    scrollTo(600);
    scrollTo(900);
  };

  afterEach(() => document.body.removeAttribute('ng-server-context'));

  beforeEach(async () => {
    authenticated = signal(false);
    scrollY = 0;
    logout = jasmine.createSpy('logout');
    spyOnProperty(window, 'scrollY', 'get').and.callFake(() => scrollY);

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([{ path: '**', component: BlankComponent }]),
        provideTransloco(translocoOptions),
        { provide: AuthService, useValue: { logout } },
        {
          provide: WishlistService,
          useValue: { schools: signal([]), status: signal('idle'), error: signal(false), load: () => undefined, toggle: () => undefined, isPending: () => false, has: () => false },
        },
        { provide: LocaleService, useValue: { active: signal('fr'), remember: () => undefined } },
        {
          provide: TokenService,
          useValue: {
            isAuthenticated: authenticated,
            user: signal({ firstname: 'Ada', lastname: 'Lovelace' }),
          },
        },
      ],
    });
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));
  });

  it('links the brand to the home page of the current language', async () => {
    await render();
    const brand = link('/fr');
    expect(brand).toBeTruthy();
    expect(brand!.getAttribute('aria-label')).toBe("MeetStudent, retour à l'accueil");
  });

  it('offers the public sections', async () => {
    await render();
    expect(link('/fr/schools')).toBeTruthy();
    expect(link('/fr#how-it-works')).toBeTruthy();
    expect(link('/fr#reviews')).toBeTruthy();
  });

  it('offers log in and sign up to an anonymous visitor', async () => {
    await render();
    expect(link('/fr/login')).toBeTruthy();
    expect(link('/fr/register')).toBeTruthy();
    expect(link('/fr/home')).toBeNull();
  });

  it('shows "My space" instead once the visitor is signed in', async () => {
    authenticated.set(true);
    await render();
    expect(link('/fr/home')).toBeTruthy();
    expect(link('/fr/register')).toBeNull();
  });

  it('renders the anonymous markup first on a server-rendered document, so hydration matches the server', () => {
    authenticated.set(true);
    document.body.setAttribute('ng-server-context', 'ssr');
    fixture = TestBed.createComponent(DockNavbarComponent);
    expect((fixture.componentInstance as unknown as { signedIn(): boolean }).signedIn()).toBeFalse();
  });

  it('renders the signed-in bar at once on a client-only document (no server markup to match)', () => {
    authenticated.set(true);
    fixture = TestBed.createComponent(DockNavbarComponent);
    fixture.detectChanges();

    expect((fixture.componentInstance as unknown as { signedIn(): boolean }).signedIn()).toBeTrue();
    expect(root().querySelector('button.avatar')).toBeTruthy();
  });

  it('starts visible without reading the scroll position at construction', () => {
    const getter = Object.getOwnPropertyDescriptor(window, 'scrollY')!.get as jasmine.Spy;
    getter.calls.reset();
    fixture = TestBed.createComponent(DockNavbarComponent);
    expect(getter).not.toHaveBeenCalled();
    expect((fixture.componentInstance as unknown as { visible(): boolean }).visible()).toBeTrue();
  });

  it('hides on scroll down and returns on scroll up', async () => {
    await render();
    hide();
    expect(isHidden()).toBeTrue();
    expect(getComputedStyle(nav()).transform).not.toBe('none');

    scrollTo(700);
    expect(isHidden()).toBeFalse();
  });

  describe('slow scrolling', () => {
    /** Visible, with the scroll anchor at 600. */
    const settleAt600 = () => {
      scrollTo(700);
      scrollTo(600);
      expect(isHidden()).toBeFalse();
    };

    it('hides after five slow +3px steps', async () => {
      await render();
      settleAt600();

      for (const y of [603, 606, 609, 612, 615]) {
        scrollTo(y);
      }

      expect(isHidden()).toBeTrue();
    });

    it('shows again after five slow -3px steps from a hidden state', async () => {
      await render();
      settleAt600();
      for (const y of [603, 606, 609, 612, 615]) {
        scrollTo(y);
      }
      expect(isHidden()).toBeTrue();

      for (const y of [612, 609, 606, 603, 600]) {
        scrollTo(y);
      }

      expect(isHidden()).toBeFalse();
    });

    it('ignores a single 3px jitter', async () => {
      await render();
      settleAt600();

      scrollTo(603);

      expect(isHidden()).toBeFalse();
    });
  });

  describe('pointer', () => {
    it('returns when a mouse reaches the top edge', async () => {
      await render();
      hide();
      expect(isHidden()).toBeTrue();

      pointer(REVEAL_ZONE_PX - 10);

      expect(isHidden()).toBeFalse();
    });

    it('ignores touch pointers, so a drag cannot pin the bar', async () => {
      await render();
      hide();

      pointer(10, 'touch');

      expect(isHidden()).toBeTrue();
    });

    it('forgets the pointer once it leaves the window', async () => {
      await render();
      hide();
      pointer(10);
      expect(isHidden()).toBeFalse();

      document.dispatchEvent(new MouseEvent('mouseout', { bubbles: true, relatedTarget: null }));
      scrollTo(1000);

      expect(isHidden()).toBeTrue();
    });

    it('keeps the pointer when the mouse only moves between elements', async () => {
      await render();
      hide();
      pointer(10);

      document.dispatchEvent(new MouseEvent('mouseout', { bubbles: true, relatedTarget: document.body }));
      scrollTo(1000);

      expect(isHidden()).toBeFalse();
    });

    it('forgets the pointer when the window loses focus', async () => {
      await render();
      hide();
      pointer(10);

      window.dispatchEvent(new Event('blur'));
      scrollTo(1000);

      expect(isHidden()).toBeTrue();
    });
  });

  describe('focus', () => {
    it('stays visible while keyboard focus is inside the bar', async () => {
      await render();
      languageButton().dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
      languageButton().dispatchEvent(new FocusEvent('focusin', { bubbles: true }));

      scrollTo(600);
      scrollTo(900);

      expect(isHidden()).toBeFalse();
    });

    it('does not pin the bar for focus caused by a mouse press', async () => {
      await render();
      languageButton().dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
      languageButton().dispatchEvent(new FocusEvent('focusin', { bubbles: true }));

      scrollTo(600);
      scrollTo(900);

      expect(isHidden()).toBeTrue();
    });

    it('reveals a hidden bar when keyboard focus arrives, without any scroll', async () => {
      await render();
      hide();
      expect(isHidden()).toBeTrue();

      document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
      (link('/fr/schools') as HTMLElement).dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
      fixture.detectChanges();

      expect(isHidden()).toBeFalse();
    });

    it('does not reveal a hidden bar for focus caused by a mouse press', async () => {
      await render();
      hide();

      document.body.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
      (link('/fr/schools') as HTMLElement).dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
      fixture.detectChanges();

      expect(isHidden()).toBeTrue();
    });

    it('counts Tab from outside the bar as keyboard focus after an earlier mouse press', async () => {
      await render();
      languageButton().dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
      document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
      (link('/fr/schools') as HTMLElement).dispatchEvent(new FocusEvent('focusin', { bubbles: true }));

      scrollTo(600);
      scrollTo(900);

      expect(isHidden()).toBeFalse();
    });

    it('releases the pin when focus leaves the bar', async () => {
      await render();
      languageButton().dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
      languageButton().dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
      languageButton().dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: null }));

      scrollTo(600);
      scrollTo(900);

      expect(isHidden()).toBeTrue();
    });
  });

  it('opens and closes the mobile menu with the button and Escape, exposing the state', async () => {
    await render();
    const button = root().querySelector('button.menu-btn') as HTMLButtonElement;
    expect(button.getAttribute('aria-expanded')).toBe('false');

    button.click();
    fixture.detectChanges();
    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(root().querySelector('#dock-menu')).toBeTruthy();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(root().querySelector('#dock-menu')).toBeNull();
  });

  it('only references the menu panel while it exists', async () => {
    await render();
    const button = root().querySelector('button.menu-btn') as HTMLButtonElement;
    expect(button.getAttribute('aria-controls')).toBeNull();

    button.click();
    fixture.detectChanges();

    expect(button.getAttribute('aria-controls')).toBe('dock-menu');
  });

  it('puts every mobile menu link inside a labelled navigation landmark', async () => {
    await render();
    (root().querySelector('button.menu-btn') as HTMLButtonElement).click();
    fixture.detectChanges();

    const links = Array.from(root().querySelectorAll('#dock-menu a'));
    expect(links.length).toBeGreaterThan(0);
    for (const anchor of links) {
      expect(anchor.closest('nav[aria-label]')).withContext(anchor.textContent ?? '').not.toBeNull();
    }
  });

  it('moves focus to the menu button when Escape closes the menu from inside the panel', async () => {
    await render();
    const button = root().querySelector('button.menu-btn') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();
    (root().querySelector('#dock-menu a') as HTMLElement).focus();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(document.activeElement).toBe(button);
  });

  it('keeps the bar visible while the menu is open, even when the page scrolls down', async () => {
    await render();
    (root().querySelector('button.menu-btn') as HTMLButtonElement).click();
    hide();
    expect(isHidden()).toBeFalse();
  });

  it('closes the menu when the visitor navigates', async () => {
    await render();
    (root().querySelector('button.menu-btn') as HTMLButtonElement).click();
    fixture.detectChanges();

    await TestBed.inject(Router).navigateByUrl('/fr/schools');
    fixture.detectChanges();

    expect(root().querySelector('#dock-menu')).toBeNull();
  });

  describe('account menu', () => {
    const avatar = () => root().querySelector('button.avatar') as HTMLButtonElement;
    const menu = () => root().querySelector('#account-menu') as HTMLElement | null;
    const openMenu = async () => {
      authenticated.set(true);
      await render();
      avatar().click();
      fixture.detectChanges();
    };

    it('shows the avatar as a closed disclosure button with no menu', async () => {
      authenticated.set(true);
      await render();

      expect(avatar()).toBeTruthy();
      expect(avatar().getAttribute('aria-expanded')).toBe('false');
      expect(avatar().hasAttribute('aria-haspopup')).toBeFalse();
      expect(avatar().getAttribute('aria-label')).toBe('Compte');
      expect(menu()).toBeNull();
    });

    it('opens on click with Profile and Log out', async () => {
      await openMenu();

      expect(avatar().getAttribute('aria-expanded')).toBe('true');
      expect(avatar().getAttribute('aria-controls')).toBe('account-menu');
      expect(menu()!.querySelector('a')!.getAttribute('href')).toBe('/fr/profile');
      expect(menu()!.querySelector('button')!.textContent).toContain('Se déconnecter');
    });

    it('closes on Escape and refocuses the avatar when focus was inside', async () => {
      await openMenu();
      (menu()!.querySelector('a') as HTMLElement).focus();

      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      fixture.detectChanges();

      expect(menu()).toBeNull();
      expect(document.activeElement).toBe(avatar());
    });

    it('closes on a press outside the bar but not inside it', async () => {
      await openMenu();

      menu()!.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
      fixture.detectChanges();
      expect(menu()).not.toBeNull();

      document.body.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
      fixture.detectChanges();
      expect(menu()).toBeNull();
    });

    it('closes when focus leaves the bar, not when it moves inside', async () => {
      await openMenu();
      const profile = menu()!.querySelector('a') as HTMLElement;

      avatar().dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: profile }));
      fixture.detectChanges();
      expect(menu()).not.toBeNull();

      profile.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: document.body }));
      fixture.detectChanges();
      expect(menu()).toBeNull();
    });

    it('keeps the bar visible while open, even after a scroll down', async () => {
      await openMenu();
      hide();
      expect(isHidden()).toBeFalse();
    });

    it('logs out, closes and goes to the home page of the language', async () => {
      await openMenu();
      const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);

      (menu()!.querySelector('button') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(logout).toHaveBeenCalledTimes(1);
      expect(navigate).toHaveBeenCalledWith(['/', 'fr']);
      expect(menu()).toBeNull();
    });

    it('closes when the visitor navigates', async () => {
      await openMenu();

      await TestBed.inject(Router).navigateByUrl('/fr/profile');
      fixture.detectChanges();

      expect(menu()).toBeNull();
    });

    it('closes when the mobile menu opens', async () => {
      await openMenu();

      (root().querySelector('button.menu-btn') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(menu()).toBeNull();
      expect(root().querySelector('#dock-menu')).toBeTruthy();
    });

    it('offers Profile and Log out in the mobile panel when signed in', async () => {
      authenticated.set(true);
      await render();
      (root().querySelector('button.menu-btn') as HTMLButtonElement).click();
      fixture.detectChanges();

      const panel = root().querySelector('#dock-menu') as HTMLElement;
      expect(panel.querySelector('a[href="/fr/profile"]')).toBeTruthy();
      expect(Array.from(panel.querySelectorAll('button')).some((b) => b.textContent!.includes('Se déconnecter'))).toBeTrue();
    });

    it('mounts the compact wishlist button next to the avatar, signed in only', async () => {
      await render();
      expect(root().querySelector('app-wishlist-cart')).toBeNull();
      expect(root().querySelector('button.avatar')).toBeNull();

      authenticated.set(true);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(root().querySelector('app-wishlist-cart')).toBeTruthy();
    });

    it('keeps the bar visible while the wishlist popover is open', async () => {
      authenticated.set(true);
      await render();
      (root().querySelector('app-wishlist-cart button') as HTMLButtonElement).click();
      fixture.detectChanges();

      hide();

      expect(isHidden()).toBeFalse();
    });

    it('keeps the menu open when a mouse press inside is followed by a focusout to nothing', async () => {
      await openMenu();
      const profile = menu()!.querySelector('a') as HTMLElement;

      profile.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
      profile.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: null }));
      fixture.detectChanges();

      expect(menu()).not.toBeNull();
    });

    it('puts the compact wishlist button, without a label, right before the avatar', async () => {
      authenticated.set(true);
      await render();

      const cart = root().querySelector('app-wishlist-cart') as HTMLElement;
      expect(cart.nextElementSibling).toBe(avatar());
      expect(cart.querySelector('button span:not([class*="absolute"])')).toBeNull();
    });

    it('lets the account menu follow the avatar in tab order', async () => {
      await openMenu();
      expect(avatar().nextElementSibling).toBe(menu());
    });

    it('anchors the popovers to the bar so they stay inside the viewport', async () => {
      authenticated.set(true);
      await render();
      expect(getComputedStyle(nav()).position).toBe('relative');
    });

    it('collapses the brand text and moves the switchers into the mobile menu below sm when signed in', async () => {
      authenticated.set(true);
      await render();
      expect(root().querySelector('.brand-text')!.classList).toContain('collapse-xs');
      expect(root().querySelector('.switchers')!.classList).toContain('hide-xs');

      (root().querySelector('button.menu-btn') as HTMLButtonElement).click();
      fixture.detectChanges();
      const panel = root().querySelector('#dock-menu') as HTMLElement;
      expect(panel.querySelector('app-language-switcher')).toBeTruthy();
      expect(panel.querySelector('app-theme-toggle')).toBeTruthy();
    });

    it('keeps the anonymous bar as it was: brand text and switchers always shown, none in the panel', async () => {
      await render();
      expect(root().querySelector('.brand-text')!.classList).not.toContain('collapse-xs');
      expect(root().querySelector('.switchers')!.classList).not.toContain('hide-xs');
      (root().querySelector('button.menu-btn') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(root().querySelector('#dock-menu app-theme-toggle')).toBeNull();
    });

    describe('one overlay at a time', () => {
      const cartButton = () => root().querySelector('app-wishlist-cart button') as HTMLButtonElement;
      const popover = () => root().querySelector('#wishlist-panel');

      it('opening the account menu closes the wishlist popover', async () => {
        authenticated.set(true);
        await render();
        cartButton().click();
        fixture.detectChanges();
        expect(popover()).toBeTruthy();

        avatar().click();
        fixture.detectChanges();

        expect(popover()).toBeNull();
        expect(menu()).toBeTruthy();
      });

      it('opening the wishlist popover closes the account menu', async () => {
        await openMenu();

        cartButton().click();
        fixture.detectChanges();

        expect(menu()).toBeNull();
        expect(popover()).toBeTruthy();
      });

      it('opening the mobile menu closes the wishlist popover', async () => {
        authenticated.set(true);
        await render();
        cartButton().click();
        fixture.detectChanges();

        (root().querySelector('button.menu-btn') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(popover()).toBeNull();
      });

      it('opening the wishlist popover closes the mobile menu', async () => {
        authenticated.set(true);
        await render();
        (root().querySelector('button.menu-btn') as HTMLButtonElement).click();
        fixture.detectChanges();

        cartButton().click();
        fixture.detectChanges();

        expect(root().querySelector('#dock-menu')).toBeNull();
      });

      it('stops pinning the bar once the popover is closed by navigating', async () => {
        authenticated.set(true);
        await render();
        cartButton().click();
        fixture.detectChanges();

        await TestBed.inject(Router).navigateByUrl('/fr/schools');
        fixture.detectChanges();
        hide();

        expect(isHidden()).toBeTrue();
      });

      it('stops pinning the bar when the visitor signs out while the popover is open', async () => {
        authenticated.set(true);
        await render();
        cartButton().click();
        fixture.detectChanges();

        authenticated.set(false);
        fixture.detectChanges();
        await fixture.whenStable();
        hide();

        expect(isHidden()).toBeTrue();
      });
    });
  });
});
