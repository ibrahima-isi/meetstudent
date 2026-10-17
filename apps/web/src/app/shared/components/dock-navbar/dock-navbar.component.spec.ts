import { Component, computed, provideZonelessChangeDetection, signal } from '@angular/core';
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
  let currentUser: ReturnType<typeof signal<{ firstname: string; lastname: string; role: { name: string } }>>;
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
    currentUser = signal({ firstname: 'Ada', lastname: 'Lovelace', role: { name: 'ROLE_STUDENT' } });
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
          useValue: { available: computed(() => currentUser().role.name === 'ROLE_STUDENT'), schools: signal([]), status: signal('idle'), error: signal(false), load: () => undefined, toggle: () => undefined, isPending: () => false, has: () => false },
        },
        { provide: LocaleService, useValue: { active: signal('fr'), remember: () => undefined } },
        {
          provide: TokenService,
          useValue: {
            isAuthenticated: authenticated,
            user: currentUser,
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
    expect(link('/fr#reviews')).toBeTruthy();
    expect(link('/fr/about')?.textContent?.trim()).toBe('À propos');
    expect(link('/fr/contact')?.textContent?.trim()).toBe('Contact');
  });

  describe('anonymous account button', () => {
    const trigger = () => root().querySelector('nav.dock button.account-trigger') as HTMLButtonElement;
    const menu = () => root().querySelector('#account-menu') as HTMLElement | null;

    it('shows a single "Log in" button, closed, and no sign-up link in the bar', async () => {
      await render();

      expect(trigger().textContent?.trim()).toBe('Se connecter');
      expect(trigger().getAttribute('aria-expanded')).toBe('false');
      expect(menu()).toBeNull();
      expect(root().querySelector('nav.dock a[href="/fr/login"]')).toBeNull();
      expect(root().querySelector('nav.dock a[href="/fr/register"]')).toBeNull();
      expect(link('/fr/home')).toBeNull();
    });

    it('opens a menu with log in, then sign up for those without an account', async () => {
      await render();
      trigger().click();
      fixture.detectChanges();

      expect(trigger().getAttribute('aria-expanded')).toBe('true');
      expect(trigger().getAttribute('aria-controls')).toBe('account-menu');
      const links = Array.from(menu()!.querySelectorAll('a')).map((a) => a.getAttribute('href'));
      expect(links).toEqual(['/fr/login', '/fr/register']);
      expect(menu()!.textContent).toContain('Pas encore de compte ?');
    });

    it('closes on Escape and gives focus back to the button', async () => {
      await render();
      // Wide enough for the button to be on screen, hence focusable.
      const frame = document.createElement('div');
      frame.style.cssText = 'position:relative;transform:translateZ(0);height:240px;width:1280px;';
      document.body.appendChild(frame);
      frame.appendChild(fixture.nativeElement);
      trigger().click();
      fixture.detectChanges();
      (menu()!.querySelector('a') as HTMLElement).focus();

      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      fixture.detectChanges();

      expect(menu()).toBeNull();
      expect(document.activeElement).toBe(trigger());
      document.body.appendChild(fixture.nativeElement);
      frame.remove();
    });

    it('closes when the language list opens, and the other way round', async () => {
      await render();
      trigger().click();
      fixture.detectChanges();
      languageButton().click();
      fixture.detectChanges();
      expect(menu()).toBeNull();
      expect(root().querySelector('#language-menu')).toBeTruthy();

      trigger().click();
      fixture.detectChanges();
      expect(menu()).toBeTruthy();
      expect(root().querySelector('#language-menu')).toBeNull();
    });
  });

  it('keeps the bar visible while the language list is open, even after a scroll down', async () => {
    await render();
    languageButton().click();
    fixture.detectChanges();

    hide();

    expect(isHidden()).toBeFalse();
  });

  it('shows one language button in the bar, never one per language', async () => {
    await render();
    expect(root().querySelectorAll('nav.dock app-language-switcher button').length).toBe(1);
  });

  describe('layout', () => {
    let frame: HTMLElement;
    const dock = () => root().querySelector('nav.dock') as HTMLElement;
    /** Rendered at all: an element inside a hidden parent keeps its own `display`. */
    const shown = (el: Element | null) => !!el && el.getClientRects().length > 0;

    /** A transformed ancestor is the containing block of the fixed bar, so its width is the bar's "viewport". */
    const widen = (px: number) => {
      frame.style.width = `${px}px`;
      void frame.offsetWidth;
    };

    beforeEach(() => {
      frame = document.createElement('div');
      frame.style.cssText = 'position:relative;transform:translateZ(0);height:240px;';
      document.body.appendChild(frame);
    });
    afterEach(() => frame.remove());

    const mount = async (lang: 'fr' | 'en', signedIn: boolean) => {
      authenticated.set(signedIn);
      const transloco = TestBed.inject(TranslocoService);
      await firstValueFrom(transloco.load(lang));
      transloco.setActiveLang(lang);
      await render();
      frame.appendChild(fixture.nativeElement);
    };

    const problems = (px: number): string[] => {
      widen(px);
      const bar = dock().getBoundingClientRect();
      const found: string[] = [];
      for (const child of Array.from(dock().querySelectorAll('.brand, .links a, .links .discover-trigger, .links .forum-soon, .actions > *'))) {
        const box = child.getBoundingClientRect();
        if (box.width === 0) continue;
        if (box.right > bar.right + 0.5 || box.left < bar.left - 0.5) found.push(`${px}px: ${child.className} leaves the bar`);
        if (box.height > bar.height) found.push(`${px}px: ${child.className} wraps`);
      }
      if (bar.right > frame.getBoundingClientRect().right + 0.5) found.push(`${px}px: the bar is wider than the screen`);
      return found;
    };

    for (const lang of ['fr', 'en'] as const) {
      for (const signedIn of [false, true]) {
        it(`never lets anything leave the bar or wrap, from 320 to 1600px (${lang}, ${signedIn ? 'signed in' : 'anonymous'})`, async () => {
          await mount(lang, signedIn);
          const found: string[] = [];
          for (let px = 320; px <= 1600; px += 20) found.push(...problems(px));
          expect(found).toEqual([]);
        });
      }
    }

    it('always explains the greyed Forum entry with a visible coming-soon cue', async () => {
      await mount('fr', true);
      for (let px = 1200; px <= 1600; px += 20) {
        widen(px);
        const forum = root().querySelector('.links .forum-soon');
        if (!shown(forum)) continue;
        expect(shown(forum!.querySelector('.soon-badge'))).withContext(`${px}px`).toBeTrue();
      }
    });

    it('keeps only the brand and the menu button on a phone or a tablet', async () => {
      await mount('fr', false);
      for (const px of [320, 375, 768, 1024]) {
        widen(px);
        expect(shown(root().querySelector('.menu-btn'))).withContext(`${px}`).toBeTrue();
        expect(shown(root().querySelector('.links'))).withContext(`${px}`).toBeFalse();
        expect(shown(root().querySelector('nav.dock app-language-switcher'))).withContext(`${px}`).toBeFalse();
        expect(shown(root().querySelector('nav.dock app-theme-toggle'))).withContext(`${px}`).toBeFalse();
        expect(shown(root().querySelector('nav.dock .account-trigger'))).withContext(`${px}`).toBeFalse();
      }
    });

    it('keeps the menu action buttons at their full touch height when the list has to scroll', async () => {
      for (const signedIn of [false, true]) {
        await mount('fr', signedIn);
        widen(375);
        (root().querySelector('button.menu-btn') as HTMLButtonElement).click();
        fixture.detectChanges();
        const panel = root().querySelector('#dock-menu') as HTMLElement;
        // The panel must overflow for the squeeze to matter: that is the phone case.
        expect(panel.scrollHeight).withContext('panel overflows').toBeGreaterThan(panel.clientHeight);
        const buttons = Array.from(panel.querySelectorAll<HTMLElement>('.btn'));
        expect(buttons.length).withContext(`signedIn=${signedIn}`).toBeGreaterThan(0);
        for (const b of buttons) {
          expect(b.getBoundingClientRect().height).withContext(`${b.textContent?.trim()} signedIn=${signedIn}`).toBeGreaterThanOrEqual(44);
        }
        fixture.destroy();
      }
    });

    it('shows the whole menu and no menu button on a wide screen', async () => {
      await mount('fr', false);
      widen(1280);
      expect(shown(root().querySelector('.menu-btn'))).toBeFalse();
      expect(shown(root().querySelector('.links'))).toBeTrue();
      expect(shown(root().querySelector('nav.dock .account-trigger'))).toBeTrue();
    });

    it('stops shrinking at the width of its content', async () => {
      await mount('fr', false);
      widen(120);
      const bar = dock();
      expect(bar.scrollWidth).toBeLessThanOrEqual(bar.clientWidth);
    });
  });

  it('puts the links, the language, the theme and both account actions in the menu panel', async () => {
    await render();
    (root().querySelector('button.menu-btn') as HTMLButtonElement).click();
    fixture.detectChanges();
    const panel = root().querySelector('#dock-menu') as HTMLElement;

    const hrefs = Array.from(panel.querySelectorAll('a')).map((a) => a.getAttribute('href'));
    expect(hrefs).toEqual(['/fr/schools', '/fr/rankings/schools', '/fr#how-it-works', '/fr/programs', '/fr/courses', '/fr/accreditations', '/fr#reviews', '/fr/about', '/fr/contact', '/fr/login', '/fr/register']);
    expect(panel.querySelector('app-language-switcher')).toBeTruthy();
    expect(panel.querySelector('app-theme-toggle')).toBeTruthy();
    expect(panel.textContent).toContain('Pas encore de compte ?');
  });

  it('links the schools ranking from the bar and from the menu, localised', async () => {
    await render();
    const barLink = root().querySelector('.links a[href="/fr/rankings/schools"]');
    expect(barLink?.textContent?.trim()).toBe('Classement');

    (root().querySelector('button.menu-btn') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(root().querySelector('#dock-menu a[href="/fr/rankings/schools"]')?.textContent?.trim()).toBe('Classement');
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
    it('never cancels the default action of a key press', async () => {
      await render();
      const tab = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
      const letter = new KeyboardEvent('keydown', { key: 'a', bubbles: true, cancelable: true });

      document.body.dispatchEvent(tab);
      document.body.dispatchEvent(letter);

      expect(tab.defaultPrevented).toBeFalse();
      expect(letter.defaultPrevented).toBeFalse();
    });

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

    it('hides the student wishlist cart for an expert and restores it after a role change', async () => {
      authenticated.set(true);
      currentUser.set({ firstname: 'Ada', lastname: 'Lovelace', role: { name: 'ROLE_EXPERT' } });
      await render();
      expect(root().querySelector('app-wishlist-cart')).toBeNull();

      currentUser.set({ firstname: 'Ada', lastname: 'Lovelace', role: { name: 'ROLE_STUDENT' } });
      fixture.detectChanges();
      await fixture.whenStable();
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

    it('collapses the brand text on a phone and offers the switchers in the menu panel when signed in', async () => {
      authenticated.set(true);
      await render();
      expect(root().querySelector('.brand-text')!.classList).toContain('collapse-xs');

      (root().querySelector('button.menu-btn') as HTMLButtonElement).click();
      fixture.detectChanges();
      const panel = root().querySelector('#dock-menu') as HTMLElement;
      expect(panel.querySelector('app-language-switcher')).toBeTruthy();
      expect(panel.querySelector('app-theme-toggle')).toBeTruthy();
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

  describe('Discover menu', () => {
    const trigger = () => root().querySelector('.links button.discover-trigger') as HTMLButtonElement;
    const panel = () => root().querySelector('#discover-menu') as HTMLElement | null;
    const open = () => {
      trigger().click();
      fixture.detectChanges();
    };
    const hrefs = () => Array.from(panel()!.querySelectorAll('a')).map((a) => a.getAttribute('href'));
    const labels = () => Array.from(panel()!.querySelectorAll('a')).map((a) => a.textContent?.trim());

    it('replaces the "How it works" link with a closed "Découvrir" disclosure button', async () => {
      await render();
      expect(trigger().tagName).toBe('BUTTON');
      expect(trigger().getAttribute('type')).toBe('button');
      expect(trigger().textContent?.trim()).toBe('Découvrir');
      expect(trigger().getAttribute('aria-expanded')).toBe('false');
      expect(trigger().getAttribute('aria-controls')).toBeNull();
      expect(panel()).toBeNull();
      expect(root().querySelector('.links a[href="/fr#how-it-works"]')).toBeNull();
    });

    it('opens a panel with the four destinations, localised, exposing the state', async () => {
      await render();
      open();
      expect(trigger().getAttribute('aria-expanded')).toBe('true');
      expect(trigger().getAttribute('aria-controls')).toBe('discover-menu');
      expect(panel()!.getAttribute('role')).toBeNull();
      expect(panel()!.querySelector('[role="menu"], [role="menuitem"]')).toBeNull();
      expect(hrefs()).toEqual(['/fr#how-it-works', '/fr/programs', '/fr/courses', '/fr/accreditations']);
      expect(labels()).toEqual(['Comment ça marche', 'Formations', 'Cours', 'Accréditations']);
    });

    it('is translated in English', async () => {
      const transloco = TestBed.inject(TranslocoService);
      await firstValueFrom(transloco.load('en'));
      transloco.setActiveLang('en');
      await render();
      expect(trigger().textContent?.trim()).toBe('Discover');
      open();
      expect(labels()).toEqual(['How it works', 'Programmes', 'Courses', 'Accreditations']);
    });

    it('toggles closed with a second press', async () => {
      await render();
      open();
      open();
      expect(panel()).toBeNull();
      expect(trigger().getAttribute('aria-expanded')).toBe('false');
    });

    it('closes on Escape and gives focus back to the button when focus was inside', async () => {
      await render();
      const frame = document.createElement('div');
      frame.style.cssText = 'position:relative;transform:translateZ(0);height:240px;width:1280px;';
      document.body.appendChild(frame);
      frame.appendChild(fixture.nativeElement);
      open();
      (panel()!.querySelector('a') as HTMLElement).focus();

      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      fixture.detectChanges();

      expect(panel()).toBeNull();
      expect(document.activeElement).toBe(trigger());
      document.body.appendChild(fixture.nativeElement);
      frame.remove();
    });

    it('closes on a press outside the bar but not inside it', async () => {
      await render();
      open();
      panel()!.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
      fixture.detectChanges();
      expect(panel()).not.toBeNull();

      document.body.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
      fixture.detectChanges();
      expect(panel()).toBeNull();
    });

    it('closes when the visitor navigates', async () => {
      await render();
      open();
      await TestBed.inject(Router).navigateByUrl('/fr/programs');
      fixture.detectChanges();
      expect(panel()).toBeNull();
    });

    it('keeps the bar visible while open, then lets it hide once closed', async () => {
      await render();
      open();
      hide();
      expect(isHidden()).toBeFalse();

      await TestBed.inject(Router).navigateByUrl('/fr/programs');
      fixture.detectChanges();
      hide();
      expect(isHidden()).toBeTrue();
    });

    it('keeps the panel inside the screen on a wide bar', async () => {
      await render();
      const frame = document.createElement('div');
      frame.style.cssText = 'position:relative;transform:translateZ(0);height:240px;width:1280px;';
      document.body.appendChild(frame);
      frame.appendChild(fixture.nativeElement);
      open();
      const box = panel()!.getBoundingClientRect();
      const bounds = frame.getBoundingClientRect();
      expect(box.width).toBeGreaterThan(0);
      expect(box.left).toBeGreaterThanOrEqual(bounds.left);
      expect(box.right).toBeLessThanOrEqual(bounds.right);
      document.body.appendChild(fixture.nativeElement);
      frame.remove();
    });

    describe('on a wide bar', () => {
      let frame: HTMLElement;
      const mountWide = async (px: number) => {
        await render();
        frame = document.createElement('div');
        frame.style.cssText = `position:relative;transform:translateZ(0);height:240px;width:${px}px;`;
        document.body.appendChild(frame);
        frame.appendChild(fixture.nativeElement);
      };
      afterEach(() => {
        document.body.appendChild(fixture.nativeElement);
        frame?.remove();
      });

      it('keeps the roomy padding of its links where the bar tightens its own links', async () => {
        await mountWide(1280);
        open();
        const rem = parseFloat(getComputedStyle(document.documentElement).fontSize);
        const item = panel()!.querySelector('a') as HTMLElement;
        expect(parseFloat(getComputedStyle(item).paddingLeft)).toBeCloseTo(0.9 * rem, 1);
        expect(parseFloat(getComputedStyle(item).paddingRight)).toBeCloseTo(0.9 * rem, 1);
      });

      it('closes when keyboard focus leaves it, not while it moves between the button and its links', async () => {
        await mountWide(1280);
        open();
        const first = panel()!.querySelector('a') as HTMLElement;
        first.focus();
        trigger().focus();
        fixture.detectChanges();
        expect(panel()).not.toBeNull();

        first.focus();
        (link('/fr#reviews') as HTMLElement).focus();
        fixture.detectChanges();
        expect(panel()).toBeNull();
      });
    });

    it('closes when one of its links is followed, even to the page already shown', async () => {
      await render();
      await TestBed.inject(Router).navigateByUrl('/fr#how-it-works');
      fixture.detectChanges();
      open();

      (panel()!.querySelector('a[href="/fr#how-it-works"]') as HTMLElement).click();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(panel()).toBeNull();
    });

    describe('one overlay at a time', () => {
      const avatar = () => root().querySelector('button.avatar') as HTMLButtonElement;
      const cartButton = () => root().querySelector('app-wishlist-cart button') as HTMLButtonElement;
      const menuButton = () => root().querySelector('button.menu-btn') as HTMLButtonElement;
      const accountMenu = () => root().querySelector('#account-menu');

      it('opening it closes the account menu, and the other way round', async () => {
        authenticated.set(true);
        await render();
        avatar().click();
        fixture.detectChanges();
        open();
        expect(accountMenu()).toBeNull();
        expect(panel()).toBeTruthy();

        avatar().click();
        fixture.detectChanges();
        expect(panel()).toBeNull();
        expect(accountMenu()).toBeTruthy();
      });

      it('opening it closes the language list, and the other way round', async () => {
        await render();
        languageButton().click();
        fixture.detectChanges();
        open();
        expect(root().querySelector('#language-menu')).toBeNull();
        expect(panel()).toBeTruthy();

        languageButton().click();
        fixture.detectChanges();
        expect(panel()).toBeNull();
        expect(root().querySelector('#language-menu')).toBeTruthy();
      });

      it('opening it closes the wishlist popover, and the other way round', async () => {
        authenticated.set(true);
        await render();
        cartButton().click();
        fixture.detectChanges();
        open();
        expect(root().querySelector('#wishlist-panel')).toBeNull();
        expect(panel()).toBeTruthy();

        cartButton().click();
        fixture.detectChanges();
        expect(panel()).toBeNull();
        expect(root().querySelector('#wishlist-panel')).toBeTruthy();
      });

      it('opening it closes the mobile menu, and the other way round', async () => {
        await render();
        menuButton().click();
        fixture.detectChanges();
        open();
        expect(root().querySelector('#dock-menu')).toBeNull();
        expect(panel()).toBeTruthy();

        menuButton().click();
        fixture.detectChanges();
        expect(panel()).toBeNull();
        expect(root().querySelector('#dock-menu')).toBeTruthy();
      });
    });

    it('lists "Découvrir" then the three destinations, flat, in the mobile panel', async () => {
      await render();
      (root().querySelector('button.menu-btn') as HTMLButtonElement).click();
      fixture.detectChanges();
      const items = Array.from(root().querySelectorAll('#dock-menu a')).slice(2, 6);
      expect(items.map((a) => a.getAttribute('href'))).toEqual(['/fr#how-it-works', '/fr/programs', '/fr/courses', '/fr/accreditations']);
      expect(items.map((a) => a.textContent?.trim())).toEqual(['Découvrir', 'Formations', 'Cours', 'Accréditations']);
      expect(root().querySelector('#dock-menu .discover-trigger')).toBeNull();
    });

    it('labels the mobile entries in English', async () => {
      const transloco = TestBed.inject(TranslocoService);
      await firstValueFrom(transloco.load('en'));
      transloco.setActiveLang('en');
      await render();
      (root().querySelector('button.menu-btn') as HTMLButtonElement).click();
      fixture.detectChanges();
      const items = Array.from(root().querySelectorAll('#dock-menu a')).slice(2, 6);
      expect(items.map((a) => a.textContent?.trim())).toEqual(['Discover', 'Programmes', 'Courses', 'Accreditations']);
    });
  });

  describe('Forum coming soon', () => {
    const forum = (scope: string) => root().querySelector<HTMLElement>(`${scope} [data-testid="forum-soon"]`);
    const openMenu = () => {
      (root().querySelector('button.menu-btn') as HTMLButtonElement).click();
      fixture.detectChanges();
    };

    for (const signedIn of [false, true]) {
      it(`shows a disabled Forum entry in the bar when ${signedIn ? 'signed in' : 'anonymous'}`, async () => {
        authenticated.set(signedIn);
        await render();
        const entry = forum('.links')!;
        expect(entry).not.toBeNull();
        expect(entry.textContent).toContain('Forum');
        expect(entry.textContent).toContain('Bientôt');
        expect(entry.getAttribute('aria-disabled')).toBe('true');
        expect(entry.getAttribute('tabindex')).toBe('-1');
        expect(entry.hasAttribute('href')).toBeFalse();
        expect(entry.closest('a')).toBeNull();
      });
    }

    it('shows the same disabled entry in the menu panel', async () => {
      await render();
      openMenu();
      const entry = forum('#dock-menu')!;
      expect(entry).not.toBeNull();
      expect(entry.textContent).toContain('Bientôt');
      expect(entry.getAttribute('aria-disabled')).toBe('true');
      expect(entry.getAttribute('tabindex')).toBe('-1');
    });

    it('is translated in English and leads nowhere', async () => {
      const transloco = TestBed.inject(TranslocoService);
      await firstValueFrom(transloco.load('en'));
      transloco.setActiveLang('en');
      await render();
      expect(forum('.links')!.textContent).toContain('Soon');
      expect(root().querySelector('.links a[href*="forum"]')).toBeNull();
    });
  });
});
