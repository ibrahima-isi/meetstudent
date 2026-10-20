import { School } from '@models/entities';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { TokenService } from '@services/token.service';
import { LocaleService } from '@services/locale.service';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom, of, throwError } from 'rxjs';
import { WishlistService } from '@services/wishlist.service';
import { translocoOptions } from '@i18n/transloco.config';
import { WishlistCartComponent } from './wishlist-cart.component';

describe('WishlistCartComponent', () => {
  let fixture: ComponentFixture<WishlistCartComponent>;
  let component: WishlistCartComponent;
  let authenticated: ReturnType<typeof signal<boolean>>;
  let locale: ReturnType<typeof signal<'fr' | 'en'>>;

  beforeEach(() => {
    authenticated = signal(false);
    locale = signal<'fr' | 'en'>('fr');

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: TokenService,
          useValue: { isAuthenticated: authenticated, user: signal(null) },
        },
        { provide: LocaleService, useValue: { active: locale } },
        provideTransloco(translocoOptions),
      ],
    });

    // No `setInput`: the header drops <app-wishlist-cart /> in with no
    // bindings, so the component has to answer the authentication question
    // itself rather than being told.
    fixture = TestBed.createComponent(WishlistCartComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders without any input from its host', () => {
    expect(component).toBeTruthy();
    expect(component.isAuthenticated()).toBeFalse();
  });

  it('sends an anonymous visitor to the localised login instead of opening', () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigate');

    component.handleCartClick();

    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'login']);
    expect(component.isOpen()).toBeFalse();
  });

  it('opens the panel for a signed-in visitor and navigates nowhere', () => {
    authenticated.set(true);
    const navigate = spyOn(TestBed.inject(Router), 'navigate');

    component.handleCartClick();

    expect(component.isOpen()).toBeTrue();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('counts schools by the plural rule of the active language', async () => {
    locale.set('en');
    const transloco = TestBed.inject(TranslocoService);
    await firstValueFrom(transloco.load('en'));
    transloco.setActiveLang('en');
    authenticated.set(true);
    component.isOpen.set(true);
    fixture.detectChanges();
    await fixture.whenStable();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Wishlist');
    expect(text).toContain('My wishlist');
    // English puts zero with many; French would read "0 établissement".
    expect(text).toContain('0 schools');
  });
  it('emits openChange when the panel opens and closes', () => {
    authenticated.set(true);
    const emitted: boolean[] = [];
    component.openChange.subscribe((open: boolean) => emitted.push(open));

    component.handleCartClick();
    fixture.detectChanges();
    component.handleCartClick();
    fixture.detectChanges();

    expect(emitted).toEqual([true, false]);
  });

  it('hides the text label but keeps the accessible name when compact', async () => {
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));
    fixture.detectChanges();
    const button = (fixture.nativeElement as HTMLElement).querySelector('button') as HTMLButtonElement;
    expect(button.querySelector('span')).not.toBeNull();

    fixture.componentRef.setInput('compact', true);
    fixture.detectChanges();

    expect(button.querySelector('span')).toBeNull();
    expect(button.getAttribute('aria-label')).toBeTruthy();
  });

  it('closes and emits false on a press outside, but not on a press inside', async () => {
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));
    authenticated.set(true);
    component.handleCartClick();
    fixture.detectChanges();
    const emitted: boolean[] = [];
    component.openChange.subscribe((open: boolean) => emitted.push(open));

    (fixture.nativeElement as HTMLElement).querySelector('button')!.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
    expect(component.isOpen()).toBeTrue();

    document.body.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
    expect(component.isOpen()).toBeFalse();
    expect(emitted).toEqual([false]);
  });

  it('closes on Escape, giving focus back to the cart button when it was inside the panel', async () => {
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));
    authenticated.set(true);
    component.handleCartClick();
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    (root.querySelector('#wishlist-panel button') as HTMLElement).focus();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(component.isOpen()).toBeFalse();
    expect(document.activeElement).toBe(root.querySelector('button'));
  });

  it('exposes its state on the button and names the close button', async () => {
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));
    authenticated.set(true);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const trigger = root.querySelector('button') as HTMLButtonElement;
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    expect(trigger.getAttribute('aria-controls')).toBeNull();

    component.handleCartClick();
    fixture.detectChanges();

    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    expect(trigger.getAttribute('aria-controls')).toBe('wishlist-panel');
    expect(root.querySelector('#wishlist-panel')).toBeTruthy();
    expect(root.querySelector('#wishlist-panel button')!.getAttribute('aria-label')).toBe('Fermer');
  });

  it('closes when the visitor navigates', async () => {
    authenticated.set(true);
    component.handleCartClick();
    fixture.detectChanges();

    await TestBed.inject(Router).navigateByUrl('/');

    expect(component.isOpen()).toBeFalse();
  });

  it('emits false when it is destroyed while open', () => {
    authenticated.set(true);
    component.handleCartClick();
    const emitted: boolean[] = [];
    component.openChange.subscribe((open: boolean) => emitted.push(open));

    fixture.destroy();

    expect(emitted).toEqual([false]);
  });

  it('places the compact panel inside its host bar, right-aligned and never wider than it', async () => {
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));
    authenticated.set(true);
    fixture.componentRef.setInput('compact', true);
    component.handleCartClick();
    fixture.detectChanges();

    const panel = (fixture.nativeElement as HTMLElement).querySelector('#wishlist-panel') as HTMLElement;
    expect(panel.classList).toContain('right-0');
    expect(panel.classList).toContain('w-[min(24rem,100%)]');
    expect(panel.classList).not.toContain('left-0');
  });
});

describe('WishlistCartComponent data states', () => {
  let fixture: ComponentFixture<WishlistCartComponent>;
  let saved: ReturnType<typeof signal<unknown[]>>;
  let savedPrograms: ReturnType<typeof signal<unknown[]>>;
  let toggleProgram: jasmine.Spy;
  let programsError: ReturnType<typeof signal<boolean>>;
  let status: ReturnType<typeof signal<'idle' | 'loading' | 'loaded' | 'error'>>;
  let load: jasmine.Spy;
  let toggle: jasmine.Spy;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const q = (sel: string) => (fixture.nativeElement as HTMLElement).querySelector(sel);

  async function open() {
    saved = signal<unknown[]>([]);
    savedPrograms = signal<unknown[]>([]);
    toggleProgram = jasmine.createSpy('toggleProgram');
    programsError = signal(false);
    status = signal('loaded');
    load = jasmine.createSpy('load');
    toggle = jasmine.createSpy('toggle');
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: TokenService, useValue: { isAuthenticated: signal(true), user: signal({ id: 9 }) } },
        {
          provide: WishlistService,
          useValue: {
            schools: saved,
            programs: savedPrograms,
            programsError,
            status,
            load,
            toggle,
            toggleProgram,
            error: signal(false),
            has: () => true,
            isPending: () => false,
            isProgramPending: () => false,
          },
        },
        provideTransloco(translocoOptions),
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use('en'));
    fixture = TestBed.createComponent(WishlistCartComponent);
    fixture.detectChanges();
    fixture.componentInstance.handleCartClick();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('loads the wishlist from the API when the header mounts', async () => {
    await open();

    expect(load).toHaveBeenCalled();
  });

  it('lists the wishlisted schools the API returned, with a matching count', async () => {
    await open();
    saved.set([{ id: 5, name: 'Real School', address: { city: 'Dakar' } }]);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(text()).toContain('Real School');
    expect(text()).toContain('1 school');
    expect(text()).not.toContain('Licence en Mathématiques');
  });

  it('lists a wishlisted school the API sends without an address', async () => {
    await open();
    saved.set([{ id: 5, name: 'No address', address: null } as unknown as School]);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(text()).toContain('No address');
  });

  it('gives each school a "View more" link to its page, and the panel a link to the whole list', async () => {
    await open();
    saved.set([{ id: 5, name: 'Real School', address: { city: 'Dakar' }, programs: [{ id: 1 }, { id: 2 }] }]);
    fixture.detectChanges();
    await fixture.whenStable();

    const links = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('a'));
    expect(links.find((a) => /view more/i.test(a.textContent ?? ''))?.getAttribute('href')).toBe('/en/schools/5');
    expect(links.find((a) => /manage my wishlist/i.test(a.textContent ?? ''))?.getAttribute('href')).toBe('/en/profile');
    expect(text()).toContain('2 programmes');
  });

  it('removes a school through the shared service', async () => {
    await open();
    const school = { id: 5, name: 'Real School', address: { city: 'Dakar' } };
    saved.set([school]);
    fixture.detectChanges();
    await fixture.whenStable();

    (q('[data-testid="wishlist-remove"]') as HTMLButtonElement).click();

    expect(toggle).toHaveBeenCalledWith(school);
  });

  it('lists the wishlisted programmes next to the schools, and counts both', async () => {
    await open();
    saved.set([{ id: 5, name: 'Real School', address: { city: 'Dakar' } }]);
    savedPrograms.set([{ id: 11, name: 'Licence Informatique', schoolId: 5, schoolName: 'Real School' }]);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(text()).toContain('Real School');
    expect(text()).toContain('Licence Informatique');
    expect(text()).toContain('1 school');
    expect(text()).toContain('1 programme');
    expect(text()).toContain('Schools');
    expect(text()).toContain('Programmes');
    expect(q('.bg-indigo-600')?.textContent?.trim()).toBe('2');
  });

  it('is not empty when only programmes are saved, and removes one through the service', async () => {
    await open();
    const programme = { id: 11, name: 'Licence Informatique', schoolId: 5 };
    savedPrograms.set([programme]);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(text()).not.toContain('Your wishlist is empty');
    (q('[data-testid="wishlist-program-remove"]') as HTMLButtonElement).click();
    expect(toggleProgram).toHaveBeenCalledWith(programme);
  });

  it('hides the school count when only programmes are saved', async () => {
    await open();
    savedPrograms.set([{ id: 11, name: 'Licence Informatique', schoolId: 5 }]);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(text()).toContain('1 programme');
    expect(text()).not.toContain('0 schools');
  });

  it('keeps the schools and adds a quiet note when only the programmes could not be loaded', async () => {
    await open();
    saved.set([{ id: 5, name: 'Real School', address: { city: 'Dakar' } }]);
    programsError.set(true);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(text()).toContain('Real School');
    expect(text()).toContain('Your saved programmes could not be loaded.');
    expect(q('app-error-state')).toBeNull();
  });

  it('words an empty list for schools and programmes alike, in both languages', async () => {
    await open();
    expect(text()).toContain('My wishlist');
    expect(text()).toContain('Your wishlist is empty');
    expect(text()).toContain('Add schools or programmes to find them again easily');

    await firstValueFrom(TestBed.inject(LocaleService).use('fr'));
    await TestBed.inject(TranslocoService).load('fr');
    TestBed.inject(TranslocoService).setActiveLang('fr');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(text()).toContain('Ma liste de souhaits');
    expect(text()).toContain('Votre liste de souhaits est vide');
    expect(text()).toContain('Ajoutez des établissements ou des formations pour les retrouver facilement');
  });

  it('shows the shared error state with retry when the wishlist cannot be loaded', async () => {
    await open();
    status.set('error');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(q('app-error-state')).not.toBeNull();

    load.calls.reset();
    (q('app-error-state button') as HTMLButtonElement).click();

    expect(load).toHaveBeenCalledTimes(1);
  });
});
