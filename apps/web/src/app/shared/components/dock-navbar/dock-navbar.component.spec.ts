import { Component, provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { REVEAL_ZONE_PX } from './dock-visibility';
import { DockNavbarComponent } from './dock-navbar.component';

@Component({ template: '' })
class BlankComponent {}

describe('DockNavbarComponent', () => {
  let fixture: ComponentFixture<DockNavbarComponent>;
  let authenticated: ReturnType<typeof signal<boolean>>;
  let scrollY: number;

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

  beforeEach(async () => {
    authenticated = signal(false);
    scrollY = 0;
    spyOnProperty(window, 'scrollY', 'get').and.callFake(() => scrollY);

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([{ path: '**', component: BlankComponent }]),
        provideTransloco(translocoOptions),
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

  it('renders the anonymous markup first even for a signed-in visitor, so hydration matches the server', () => {
    authenticated.set(true);
    fixture = TestBed.createComponent(DockNavbarComponent);
    expect((fixture.componentInstance as unknown as { signedIn(): boolean }).signedIn()).toBeFalse();
  });

  it('starts visible without reading the window at construction', () => {
    fixture = TestBed.createComponent(DockNavbarComponent);
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
    const button = root().querySelector('button[aria-controls="dock-menu"]') as HTMLButtonElement;
    expect(button.getAttribute('aria-expanded')).toBe('false');

    button.click();
    fixture.detectChanges();
    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(root().querySelector('#dock-menu')).toBeTruthy();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(root().querySelector('#dock-menu')).toBeNull();
  });

  it('keeps the bar visible while the menu is open, even when the page scrolls down', async () => {
    await render();
    (root().querySelector('button[aria-controls="dock-menu"]') as HTMLButtonElement).click();
    hide();
    expect(isHidden()).toBeFalse();
  });

  it('closes the menu when the visitor navigates', async () => {
    await render();
    (root().querySelector('button[aria-controls="dock-menu"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    await TestBed.inject(Router).navigateByUrl('/fr/schools');
    fixture.detectChanges();

    expect(root().querySelector('#dock-menu')).toBeNull();
  });
});
