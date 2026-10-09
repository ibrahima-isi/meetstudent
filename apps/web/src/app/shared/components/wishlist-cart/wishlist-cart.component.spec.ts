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
});

describe('WishlistCartComponent data states', () => {
  let fixture: ComponentFixture<WishlistCartComponent>;
  let saved: ReturnType<typeof signal<unknown[]>>;
  let status: ReturnType<typeof signal<'idle' | 'loading' | 'loaded' | 'error'>>;
  let load: jasmine.Spy;
  let toggle: jasmine.Spy;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const q = (sel: string) => (fixture.nativeElement as HTMLElement).querySelector(sel);

  async function open() {
    saved = signal<unknown[]>([]);
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
          useValue: { schools: saved, status, load, toggle, error: signal(false), has: () => true, isPending: () => false },
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

  it('removes a school through the shared service', async () => {
    await open();
    const school = { id: 5, name: 'Real School', address: { city: 'Dakar' } };
    saved.set([school]);
    fixture.detectChanges();
    await fixture.whenStable();

    (q('[data-testid="wishlist-remove"]') as HTMLButtonElement).click();

    expect(toggle).toHaveBeenCalledWith(school);
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
