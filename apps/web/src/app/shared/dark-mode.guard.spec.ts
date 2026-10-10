import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal, Type } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { NEVER, firstValueFrom, of, throwError } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { MediaService } from '@services/media.service';
import { ProgramService } from '@services/program.service';
import { SchoolService } from '@services/school.service';
import { TokenService } from '@services/token.service';
import { WishlistService } from '@services/wishlist.service';
import { LoginFormComponent } from '../features/auth/login-form/login-form.component';
import { RegisterFormComponent } from '../features/auth/register-form/register-form.component';
import { SchoolsPageComponent } from '../features/public/schools-page/schools-page.component';
import { HomePageComponent } from '../features/student/home-page/home-page.component';
import { ProfilePageComponent } from '../features/student/profile-page/profile-page.component';
import { SchoolDetailPageComponent } from '../features/student/school-detail-page/school-detail-page.component';
import { UserDocumentsComponent } from '../features/student/user-documents/user-documents.component';
import { PublicShellComponent } from './layouts/public-shell/public-shell.component';
import { AuthLayoutComponent } from './layouts/auth-layout/auth-layout.component';
import { DockNavbarComponent } from './components/dock-navbar/dock-navbar.component';
import { ErrorStateComponent } from './components/error-state/error-state.component';
import { ImageWithFallbackComponent } from './components/image-with-fallback/image-with-fallback.component';
import { LanguageSwitcherComponent } from './components/language-switcher/language-switcher.component';
import { NotFoundComponent } from './components/not-found/not-found.component';
import { StarRatingComponent } from './components/star-rating/star-rating.component';
import { MeshBackgroundComponent } from './components/mesh-background/mesh-background.component';
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
  // Overlays and tints over photos, readable on either theme.
  'bg-black/50',
  'bg-black/60',
  'from-black/60',
  'bg-white/20',
  'bg-red-600',
  'hover:bg-red-700',
  'text-indigo-300',
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

  const secondSchool = { ...school, id: 2, name: 'Stanford', tags: [] };
  const programmes = [
    { id: 10, name: 'Computer Science', level: 'Master', duration: 2, startDate: '2026-09', capacity: 30, enrolled: 12, rating: 4, description: 'p', courses: [{ id: 100, name: 'Algorithms', code: 'CS101' }] },
    { id: 11, name: 'Law', level: 'Bachelor', duration: 3, startDate: '2026-09', capacity: 20, enrolled: 20, rating: 3 },
  ];
  const detailedSchool = {
    ...school,
    address: { city: 'Cambridge', location: 'MA', country: 'USA' },
    accreditations: [{ id: 1, name: 'AACSB' }],
    programs: programmes,
  };
  const student = { id: 7, firstname: 'Ada', lastname: 'L', email: 'a@b.c', role: { name: 'ROLE_STUDENT' } };
  const expert = { ...student, role: { name: 'ROLE_EXPERT' } };
  const documents = [
    { id: 1, category: 'DIPLOMA', verificationStatus: 'PENDING', rejectionReason: null, originalFilename: 'a.pdf' },
    { id: 2, category: 'CERTIFICATE', verificationStatus: 'VERIFIED', rejectionReason: null, originalFilename: 'b.pdf' },
    { id: 3, category: 'BULLETIN', verificationStatus: 'REJECTED', rejectionReason: 'Blurry', originalFilename: 'c.pdf' },
  ];

  /** Detail page as this role; the effect that loads the school runs on the first detectChanges. */
  const detailAs = (role: typeof student | typeof expert) => (c: any, f: ComponentFixture<any>) => {
    TestBed.inject(TokenService).user.set(role as never);
    f.componentRef.setInput('id', '1');
  };
  const homeWith = (answer: unknown) => () =>
    spyOn(TestBed.inject(SchoolService), 'getSchools').and.returnValue(answer as never);
  const profileAs = (role: typeof student | typeof expert) => (c: any) => {
    c.profile.set(role);
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
          useValue: {
            getSchools: () => of({ content: [school, secondSchool], last: false, totalElements: 5 }),
            getSchool: () => of(detailedSchool),
            schools: () => [school],
          },
        },
        { provide: ProgramService, useValue: { getPrograms: () => of({ content: [] }) } },
        { provide: MediaService, useValue: { mine: () => of(documents) } },
        {
          provide: TokenService,
          useValue: { isAuthenticated: signal(true), user: signal(student), clear: () => undefined },
        },
        {
          provide: WishlistService,
          useValue: {
            schools: signal([school]),
            status: signal('loaded'),
            error: signal(true),
            load: () => undefined,
            has: (id: number) => id === 1,
            isPending: (id: number) => id === 2,
            dismissError: () => undefined,
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

  const detailSetup = (extra?: (c: any, f: ComponentFixture<any>) => void, role = student) => {
    const as = detailAs(role);
    return (c: any, f: ComponentFixture<any>) => {
      as(c, f);
      extra?.(c, f);
    };
  };

  const cases: [string, Type<unknown>, ((component: any, fixture: ComponentFixture<any>) => void)?][] = [
    ['auth layout', AuthLayoutComponent],
    ['public shell', PublicShellComponent],
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
      'schools page with schools',
      SchoolsPageComponent,
      (c) => {
        c.schools.set([school]);
        c.status.set('loaded');
        c.showFilters.set(true);
      },
    ],
    ['schools page loading', SchoolsPageComponent, (c) => c.status.set('loading')],
    ['schools page error', SchoolsPageComponent, (c) => c.status.set('error')],
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
    ['dock navbar', DockNavbarComponent],
    ['mesh background', MeshBackgroundComponent],
    ['wishlist cart', WishlistCartComponent, (c) => c.isOpen.set(true)],
    ['home with schools, filters and load more', HomePageComponent, (c) => c.showFilters.set(true)],
    ['home with a failed load more', HomePageComponent, (c) => c.loadMoreFailed.set(true)],
    ['home empty', HomePageComponent, homeWith(of({ content: [], last: true, totalElements: 0 }))],
    ['home loading', HomePageComponent, homeWith(NEVER)],
    ['home error', HomePageComponent, homeWith(throwError(() => new Error('x')))],
    ['school detail as student', SchoolDetailPageComponent, detailSetup()],
    ['school detail as expert with course modal', SchoolDetailPageComponent, detailSetup((c) => c.openCoursesModal(programmes[0]), expert)],
    ['school detail as expert, login prompt', SchoolDetailPageComponent, detailSetup((c) => c.showLoginPrompt.set(true), expert)],
    [
      'school detail loading',
      SchoolDetailPageComponent,
      detailSetup(() => spyOn(TestBed.inject(SchoolService), 'getSchool').and.returnValue(NEVER)),
    ],
    [
      'school detail error',
      SchoolDetailPageComponent,
      detailSetup(() => spyOn(TestBed.inject(SchoolService), 'getSchool').and.returnValue(throwError(() => new Error('x')))),
    ],
    [
      'school detail with programmes loading',
      SchoolDetailPageComponent,
      detailSetup(() => {
        spyOn(TestBed.inject(SchoolService), 'getSchool').and.returnValue(of({ ...detailedSchool, programs: [] }) as never);
        spyOn(TestBed.inject(ProgramService), 'getPrograms').and.returnValue(NEVER);
      }),
    ],
    [
      'school detail with programmes error',
      SchoolDetailPageComponent,
      detailSetup(() => {
        spyOn(TestBed.inject(SchoolService), 'getSchool').and.returnValue(of({ ...detailedSchool, programs: [] }) as never);
        spyOn(TestBed.inject(ProgramService), 'getPrograms').and.returnValue(throwError(() => new Error('x')));
      }),
    ],
    ['profile (student, wishlist, documents)', ProfilePageComponent, profileAs(student)],
    ['profile editing as student', ProfilePageComponent, (c) => { profileAs(student)(c); c.isEditing.set(true); }],
    ['profile editing as expert', ProfilePageComponent, (c) => { profileAs(expert)(c); c.isEditing.set(true); }],
    ['user documents with every status', UserDocumentsComponent],
    ['user documents with upload progress and error', UserDocumentsComponent, (c) => {
      c.uploadProgress.set(40);
      c.error.set('documents.errors.uploadFailed');
    }],
    ['user documents with delete confirmation', UserDocumentsComponent, (c) => c.pendingDeleteId.set(1)],
    ['user documents empty', UserDocumentsComponent, () => spyOn(TestBed.inject(MediaService), 'mine').and.returnValue(of([]))],
    ['user documents loading', UserDocumentsComponent, () => spyOn(TestBed.inject(MediaService), 'mine').and.returnValue(NEVER)],
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

  it('puts the theme toggle on every screen a visitor can reach', async () => {
    for (const type of [PublicShellComponent, HomePageComponent, ProfilePageComponent]) {
      const root = await render(type as Type<unknown>);
      expect(root.querySelector('app-theme-toggle button')).withContext(type.name).not.toBeNull();
    }
  });

  it('puts the theme toggle on the school detail page', async () => {
    const root = await render(SchoolDetailPageComponent, detailSetup());
    expect(root.querySelector('app-theme-toggle button')).not.toBeNull();
  });
});
