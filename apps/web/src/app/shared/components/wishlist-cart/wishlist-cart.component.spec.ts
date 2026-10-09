import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { TokenService } from '@services/token.service';
import { LocaleService } from '@services/locale.service';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom, of, throwError } from 'rxjs';
import { ProgramService } from '@services/program.service';
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

  it('counts programmes by the plural rule of the active language', async () => {
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
    // English puts zero with many; French would read "0 formation".
    expect(text).toContain('0 programmes');
  });
});

describe('WishlistCartComponent data states', () => {
  let fixture: ComponentFixture<WishlistCartComponent>;
  let getPrograms: jasmine.Spy;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const q = (sel: string) => (fixture.nativeElement as HTMLElement).querySelector(sel);

  async function open(response: unknown) {
    localStorage.setItem('wishlist', JSON.stringify([101, 5]));
    getPrograms = jasmine.createSpy('getPrograms').and.returnValue(response);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: TokenService, useValue: { isAuthenticated: signal(true), user: signal(null) } },
        { provide: ProgramService, useValue: { getPrograms } },
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

  afterEach(() => localStorage.removeItem('wishlist'));

  it('resolves saved ids from the API, never from bundled mock programmes', async () => {
    await open(of({ content: [{ id: 5, name: 'Real Prog', level: 'Master', duration: 2 }] }));

    expect(text()).toContain('Real Prog');
    expect(text()).not.toContain('Licence en Mathématiques');
  });

  it('shows the shared error state with retry when programmes cannot be loaded', async () => {
    await open(throwError(() => new Error('down')));
    expect(q('app-error-state')).not.toBeNull();

    getPrograms.and.returnValue(of({ content: [{ id: 5, name: 'Real Prog', duration: 2 }] }));
    (q('app-error-state button') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(getPrograms).toHaveBeenCalledTimes(2);
    expect(q('app-error-state')).toBeNull();
    expect(text()).toContain('Real Prog');
  });
});
