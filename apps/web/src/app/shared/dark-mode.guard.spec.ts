import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal, Type } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom, of } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { SchoolService } from '@services/school.service';
import { TokenService } from '@services/token.service';
import { WishlistService } from '@services/wishlist.service';
import { LoginFormComponent } from '../features/auth/login-form/login-form.component';
import { RegisterFormComponent } from '../features/auth/register-form/register-form.component';
import { EmailVerificationComponent } from '../features/auth/email-verification/email-verification.component';
import { LandingPageComponent } from '../features/public/landing-page/landing-page.component';
import { AuthLayoutComponent } from './layouts/auth-layout/auth-layout.component';
import { ErrorStateComponent } from './components/error-state/error-state.component';
import { ImageWithFallbackComponent } from './components/image-with-fallback/image-with-fallback.component';
import { LanguageSwitcherComponent } from './components/language-switcher/language-switcher.component';
import { NotFoundComponent } from './components/not-found/not-found.component';
import { StarRatingComponent } from './components/star-rating/star-rating.component';
import { ThemeToggleComponent } from './components/theme-toggle/theme-toggle.component';
import { WishlistCartComponent } from './components/wishlist-cart/wishlist-cart.component';

/**
 * Guard for the dark/light sweep: a raw palette class that only works on one
 * background (`bg-white`, `text-gray-900`, `border-gray-200`, `bg-red-50`...)
 * must either be replaced by a semantic token (`bg-card`, `text-foreground`,
 * `border-border`...) or be paired with a `dark:` class of the same kind and
 * the same variant (`hover:`, `focus:`...). Colours that read on both
 * backgrounds (brand buttons with white text, overlays) are listed below.
 */
const PALETTE = /^(bg|text|border|ring|divide|placeholder|from|to|via|fill|stroke)-(white|black|(?:gray|slate|zinc|neutral|stone|red|green|blue|indigo|purple|yellow|amber)-\d+)(\/\d+)?$/;

const BOTH_MODES = new Set([
  'bg-indigo-600',
  'hover:bg-indigo-700',
  'bg-indigo-600/90',
  'bg-gray-800/80',
  'text-white',
  'from-indigo-600',
  'to-purple-700',
  'text-indigo-100',
  'fill-yellow-400',
  'text-yellow-400',
  'focus:ring-indigo-500',
]);

function lightOnlyClasses(root: HTMLElement): string[] {
  const offenders = new Set<string>();
  for (const element of Array.from(root.querySelectorAll('*'))) {
    const classes = (element.getAttribute('class') ?? '').split(/\s+/).filter(Boolean);
    for (const cls of classes) {
      if (cls.startsWith('dark:') || BOTH_MODES.has(cls) || !PALETTE.test(cls.split(':').pop()!)) {
        continue;
      }
      const parts = cls.split(':');
      const utility = parts.pop()!;
      const variants = parts.length ? parts.join(':') + ':' : '';
      const kind = utility.split('-')[0];
      const paired = classes.some((other) => other.startsWith(`dark:${variants}${kind}-`));
      if (!paired) {
        offenders.add(cls);
      }
    }
  }
  return [...offenders].sort();
}

describe('dark mode: no light-only palette classes', () => {
  const school = {
    id: 1,
    name: 'Harvard',
    description: 'd',
    type: 'Univ',
    rating: 4,
    reviewCount: 2,
    coverImageUrl: '',
    address: { city: 'Cambridge', location: 'MA' },
    tags: [1, 2, 3].map((id) => ({ id, name: `tag${id}` })),
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        {
          provide: SchoolService,
          useValue: { getSchools: () => of({ content: [school] }), schools: () => [school] },
        },
        { provide: TokenService, useValue: { isAuthenticated: signal(true), user: signal(null) } },
        {
          provide: WishlistService,
          useValue: {
            schools: signal([school]),
            status: signal('loaded'),
            load: () => undefined,
            isPending: () => false,
            toggle: () => undefined,
          },
        },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use('en'));
  });

  async function render<T>(type: Type<T>, setup?: (component: T, fixture: ComponentFixture<T>) => void): Promise<HTMLElement> {
    const fixture: ComponentFixture<T> = TestBed.createComponent(type);
    setup?.(fixture.componentInstance, fixture);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const cases: [string, Type<unknown>, ((component: any, fixture: ComponentFixture<any>) => void)?][] = [
    ['auth layout', AuthLayoutComponent],
    ['login form', LoginFormComponent],
    [
      'login form with error and success',
      LoginFormComponent,
      (c) => {
        c.error.set('auth.login.failed');
        c.success.set({ key: 'auth.login.registered', params: {} });
        c.loginForm.markAllAsTouched();
      },
    ],
    ['register step 1 (student)', RegisterFormComponent, (c) => c.userType.set('student')],
    [
      'register step 1 (teacher, suggestions)',
      RegisterFormComponent,
      (c) => {
        c.userType.set('teacher');
        c.error.set('auth.register.failed');
      },
    ],
    ['register step 2', RegisterFormComponent, (c) => c.step.set(2)],
    [
      'email verification',
      EmailVerificationComponent,
      (c) => c.error.set('auth.verify.invalid'),
    ],
    [
      'landing page with schools',
      LandingPageComponent,
      (c) => {
        c.schools.set([school]);
        c.status.set('loaded');
        c.showFilters.set(true);
      },
    ],
    ['landing page loading', LandingPageComponent, (c) => c.status.set('loading')],
    ['landing page error', LandingPageComponent, (c) => c.status.set('error')],
    ['error state', ErrorStateComponent, undefined],
    [
      'image with fallback',
      ImageWithFallbackComponent,
      (c, f) => {
        f.componentRef.setInput('src', 'broken.png');
        c.didError.set(true);
      },
    ],
    ['language switcher', LanguageSwitcherComponent],
    ['not found', NotFoundComponent],
    ['theme toggle', ThemeToggleComponent],
    ['wishlist cart', WishlistCartComponent, (c) => c.isOpen.set(true)],
  ];

  for (const [name, type, setup] of cases) {
    it(`${name} has none`, async () => {
      const root = await render(type, setup);
      expect(lightOnlyClasses(root)).toEqual([]);
    });
  }

  it('star rating has none', async () => {
    const root = await render(StarRatingComponent, (c, f) => {
      f.componentRef.setInput('itemId', 1);
      f.componentRef.setInput('itemType', 'school');
      c.rating.set(3);
    });
    expect(lightOnlyClasses(root)).toEqual([]);
  });

  it('puts the theme toggle on the auth layout and the landing page', async () => {
    for (const type of [AuthLayoutComponent, LandingPageComponent]) {
      const root = await render(type as Type<unknown>);
      expect(root.querySelector('app-theme-toggle button')).withContext(type.name).not.toBeNull();
    }
  });
});
