import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { TokenService } from '@services/token.service';
import { LocaleService } from '@services/locale.service';
import { WishlistCartComponent } from './wishlist-cart.component';

describe('WishlistCartComponent', () => {
  let fixture: ComponentFixture<WishlistCartComponent>;
  let component: WishlistCartComponent;
  let authenticated: ReturnType<typeof signal<boolean>>;

  beforeEach(() => {
    authenticated = signal(false);

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
        { provide: LocaleService, useValue: { active: signal('fr' as const) } },
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
});
