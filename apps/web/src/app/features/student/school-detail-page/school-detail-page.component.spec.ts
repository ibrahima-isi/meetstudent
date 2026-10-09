import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, withComponentInputBinding, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Title } from '@angular/platform-browser';
import { firstValueFrom, of, Subject, throwError } from 'rxjs';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { translocoOptions } from '@i18n/transloco.config';
import { School, Program, Course, Page } from '@models/entities';
import { ProgramService } from '@services/program.service';
import { SchoolService } from '@services/school.service';
import { TokenService } from '@services/token.service';
import { RatingService } from '@services/rating.service';
import { LocaleService } from '@services/locale.service';
import { WishlistService } from '@services/wishlist.service';
import { SchoolDetailPageComponent } from './school-detail-page.component';

describe('SchoolDetailPageComponent', () => {
  let programServiceSpy: jasmine.SpyObj<ProgramService>;
  let schoolServiceSpy: jasmine.SpyObj<SchoolService>;
  let ratingServiceSpy: jasmine.SpyObj<RatingService>;
  let authenticated: ReturnType<typeof signal<boolean>>;
  let currentUser: ReturnType<typeof signal<{ id: number; role: { name: string } } | null>>;
  let locale: ReturnType<typeof signal<'fr' | 'en'>>;
  let harness: RouterTestingHarness;
  let wishlist: {
    schools: ReturnType<typeof signal<School[]>>;
    error: ReturnType<typeof signal<boolean>>;
    load: jasmine.Spy;
    toggle: jasmine.Spy;
    has: (id: number) => boolean;
    isPending: (id: number) => boolean;
    dismissError: jasmine.Spy;
  };

  const mockSchool: School = {
    id: 7,
    name: 'Test School',
    type: 'University',
    address: { location: 'Loc', city: 'City', country: 'Country' },
    description: 'Desc',
    tags: [{ id: 1, name: 'Tag1' }],
  };

  /** A `Page` carries a dozen paging fields the component never reads. */
  function pageOf<T>(content: T[]): Page<T> {
    return { content } as Page<T>;
  }

  /**
   * Navigating for real rather than calling `setInput`: the point of the route
   * is that `/schools/:id` renders with no prior navigation, so the id has to
   * arrive through `withComponentInputBinding()` the way it does in the app.
   */
  async function renderAt(url: string): Promise<SchoolDetailPageComponent> {
    harness = await RouterTestingHarness.create();
    return harness.navigateByUrl(url, SchoolDetailPageComponent);
  }

  beforeEach(() => {
    programServiceSpy = jasmine.createSpyObj('ProgramService', ['getPrograms']);
    schoolServiceSpy = jasmine.createSpyObj('SchoolService', ['getSchool']);
    ratingServiceSpy = jasmine.createSpyObj('RatingService', [
      'rateSchool',
      'rateProgram',
      'rateCourse',
    ]);
    authenticated = signal(true);
    currentUser = signal<{ id: number; role: { name: string } } | null>({ id: 3, role: { name: 'ROLE_STUDENT' } });
    const saved = signal<School[]>([]);
    wishlist = {
      schools: saved,
      error: signal(false),
      load: jasmine.createSpy('load'),
      toggle: jasmine.createSpy('toggle'),
      has: (id: number) => saved().some((s) => s.id === id),
      isPending: () => false,
      dismissError: jasmine.createSpy('dismissError'),
    };
    locale = signal<'fr' | 'en'>('fr');

    programServiceSpy.getPrograms.and.returnValue(of(pageOf<Program>([])));
    schoolServiceSpy.getSchool.and.returnValue(of(mockSchool));

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(
          [{ path: 'schools/:id', component: SchoolDetailPageComponent }],
          withComponentInputBinding(),
        ),
        { provide: ProgramService, useValue: programServiceSpy },
        { provide: SchoolService, useValue: schoolServiceSpy },
        { provide: RatingService, useValue: ratingServiceSpy },
        { provide: TokenService, useValue: { isAuthenticated: authenticated, user: currentUser } },
        { provide: WishlistService, useValue: wishlist },
        { provide: LocaleService, useValue: { active: locale } },
        provideTransloco(translocoOptions),
      ],
    });
  });

  it('loads the school named by the URL, with no input from a parent', async () => {
    const component = await renderAt('/schools/7');

    expect(schoolServiceSpy.getSchool).toHaveBeenCalledWith(7);
    expect(component.school()?.name).toBe('Test School');
  });

  it('puts the school name in the page title', async () => {
    await renderAt('/schools/7');

    expect(TestBed.inject(Title).getTitle()).toBe('Test School | MeetStudent');
  });

  it('sends an authenticated visitor back to the localised home', async () => {
    const component = await renderAt('/schools/7');
    const navigate = spyOn(TestBed.inject(Router), 'navigate');

    component.goBack();

    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'home']);
  });

  it('sends an anonymous visitor back to the localised landing page', async () => {
    authenticated.set(false);
    const component = await renderAt('/schools/7');
    const navigate = spyOn(TestBed.inject(Router), 'navigate');

    component.goBack();

    expect(navigate).toHaveBeenCalledWith(['/', 'fr']);
  });

  it('takes the login prompt to the localised login screen', async () => {
    authenticated.set(false);
    const component = await renderAt('/schools/7');
    const navigate = spyOn(TestBed.inject(Router), 'navigate');

    component.handleLoginClick();

    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'login']);
    expect(component.showLoginPrompt()).toBeFalse();
  });

  it('loads the programs belonging to the school', async () => {
    const mockPrograms = [{ id: 1, name: 'Prog 1', school: { id: 7 } }];
    programServiceSpy.getPrograms.and.returnValue(of(pageOf(mockPrograms as Program[])));

    const component = await renderAt('/schools/7');
    component.loadPrograms();

    expect(component.programs().length).toBe(1);
    expect(component.programs()[0].name).toBe('Prog 1');
  });

  it('opens the courses modal for a program', async () => {
    const mockProgram: Program = { id: 1, name: 'Prog 1', duration: 3, courses: [{ id: 1, name: 'Course 1' } as Course] };

    const component = await renderAt('/schools/7');
    component.openCoursesModal(mockProgram);

    expect(component.showCoursesModal()).toBeTrue();
    expect(component.selectedProgram()).toEqual(mockProgram);
    // The school response embeds programs[].courses: no extra request.
    expect(component.courses()).toEqual(mockProgram.courses!);
  });

  it('shows an empty course list for a program without courses', async () => {
    const component = await renderAt('/schools/7');
    component.openCoursesModal({ id: 2, name: 'Prog 2', duration: 1 });

    expect(component.courses()).toEqual([]);
  });

  it('closes the courses modal', async () => {
    const component = await renderAt('/schools/7');
    component.showCoursesModal.set(true);

    component.closeCoursesModal();

    expect(component.showCoursesModal()).toBeFalse();
    expect(component.selectedProgram()).toBeNull();
  });

  describe('wishlist', () => {
    const button = () => harness.routeNativeElement?.querySelector('[data-testid="wishlist-toggle"]') as HTMLButtonElement | null;
    async function settle() {
      harness.detectChanges();
      await harness.fixture.whenStable();
    }
    beforeEach(async () => {
      locale.set('en');
      const transloco = TestBed.inject(TranslocoService);
      await firstValueFrom(transloco.load('en'));
      transloco.setActiveLang('en');
    });

    it('loads the wishlist from the API when an authenticated visitor opens the page', async () => {
      await renderAt('/schools/7');

      expect(wishlist.load).toHaveBeenCalled();
    });

    it('offers to add a school that is not wishlisted, and toggles it on click', async () => {
      await renderAt('/schools/7');
      await settle();

      expect(button()?.getAttribute('aria-pressed')).toBe('false');
      button()?.click();

      expect(wishlist.toggle).toHaveBeenCalledWith(jasmine.objectContaining({ id: 7 }));
    });

    it('shows the pressed state for a school already wishlisted', async () => {
      wishlist.schools.set([mockSchool]);
      await renderAt('/schools/7');
      await settle();

      expect(button()?.getAttribute('aria-pressed')).toBe('true');
    });

    it('asks an anonymous visitor to sign in instead of toggling', async () => {
      authenticated.set(false);
      const component = await renderAt('/schools/7');
      await settle();

      button()?.click();

      expect(wishlist.toggle).not.toHaveBeenCalled();
      expect(component.showLoginPrompt()).toBeTrue();
    });

    it('tells the visitor when the update failed (the service has rolled it back)', async () => {
      wishlist.error.set(true);
      await renderAt('/schools/7');
      await settle();

      expect(harness.routeNativeElement?.querySelector('[role="alert"][data-testid="wishlist-error"]')).not.toBeNull();
    });
  });

  describe('translations', () => {
    const programs = [
      { id: 1, name: 'Prog A', duration: 3, capacity: 10, enrolled: 5, school: { id: 7 } },
      { id: 2, name: 'Prog B', duration: 1, capacity: 1, enrolled: 0, school: { id: 7 } },
    ] as Program[];

    async function renderIn(lang: 'fr' | 'en'): Promise<string> {
      locale.set(lang);
      const transloco = TestBed.inject(TranslocoService);
      await firstValueFrom(transloco.load(lang));
      transloco.setActiveLang(lang);
      programServiceSpy.getPrograms.and.returnValue(of(pageOf(programs)));

      const component = await renderAt('/schools/7');
      component.loadPrograms();
      harness.detectChanges();
      await harness.fixture.whenStable();
      return harness.routeNativeElement?.textContent ?? '';
    }

    it('renders in French, with French plurals', async () => {
      const text = await renderIn('fr');

      expect(text).toContain('Retour');
      expect(text).toContain('Formations disponibles (2)');
      expect(text).toContain('3 ans');
      expect(text).toContain('1 an');
      expect(text).toContain('5 places disponibles');
      expect(text).toContain('1 place disponible');
    });

    it('offers no sort by available places, since no capacity data exists', async () => {
      await renderIn('en');

      expect(harness.routeNativeElement?.querySelector('option[value="places"]')).toBeNull();
      expect(harness.routeNativeElement?.textContent).not.toContain('Sort by available places');
    });

    it('renders in English, with English plurals', async () => {
      const text = await renderIn('en');

      expect(text).toContain('Back');
      expect(text).toContain('Available programmes (2)');
      expect(text).toContain('3 years');
      expect(text).toContain('1 year');
      expect(text).toContain('5 places available');
      expect(text).toContain('1 place available');
    });

    it('falls back to translated copy when the API sends no type or description', async () => {
      schoolServiceSpy.getSchool.and.returnValue(
        of({ id: 7, name: 'Bare', address: { location: 'Loc', city: 'City', country: 'Country' } }),
      );
      const text = await renderIn('en');

      const badge = harness.routeNativeElement?.querySelector('span.inline-block');
      expect(badge?.textContent?.trim()).toBe('School');
      expect(text).toContain('No description available');
      expect(text).not.toContain('Établissement');
    });
  });
  describe('data states', () => {
    const text = () => harness.routeNativeElement?.textContent ?? '';
    const q = (sel: string) => harness.routeNativeElement?.querySelector(sel) ?? null;

    async function settle() {
      harness.detectChanges();
      await harness.fixture.whenStable();
    }

    beforeEach(async () => {
      const transloco = TestBed.inject(TranslocoService);
      await firstValueFrom(transloco.load('en'));
      transloco.setActiveLang('en');
      locale.set('en');
    });

    it('shows a loading state while the school is being fetched', async () => {
      schoolServiceSpy.getSchool.and.returnValue(new Subject<School>());
      await renderAt('/schools/7');
      await settle();

      expect(q('[aria-busy="true"]')).not.toBeNull();
      expect(q('app-error-state')).toBeNull();
    });

    it('shows an error state with retry when the school cannot be loaded', async () => {
      schoolServiceSpy.getSchool.and.returnValue(throwError(() => new Error('down')));
      await renderAt('/schools/7');
      await settle();

      expect(q('app-error-state')).not.toBeNull();
      expect(q('[aria-busy="true"]')).toBeNull();
    });

    it('re-fetches the school when retry is pressed', async () => {
      schoolServiceSpy.getSchool.and.returnValue(throwError(() => new Error('down')));
      await renderAt('/schools/7');
      await settle();
      schoolServiceSpy.getSchool.and.returnValue(of(mockSchool));

      (q('app-error-state button') as HTMLButtonElement).click();
      await settle();

      expect(schoolServiceSpy.getSchool).toHaveBeenCalledTimes(2);
      expect(q('app-error-state')).toBeNull();
      expect(text()).toContain('Test School');
    });

    it('shows an error with retry, and no mock programmes, when programmes fail', async () => {
      programServiceSpy.getPrograms.and.returnValue(throwError(() => new Error('down')));
      const component = await renderAt('/schools/7');
      await settle();

      expect(component.programs()).toEqual([]);
      expect(q('app-error-state')).not.toBeNull();
      expect(text()).not.toContain('Licence en');

      programServiceSpy.getPrograms.and.returnValue(
        of(pageOf([{ id: 1, name: 'Real Prog', duration: 3, school: { id: 7 } }] as Program[])),
      );
      (q('app-error-state button') as HTMLButtonElement).click();
      await settle();

      expect(q('app-error-state')).toBeNull();
      expect(text()).toContain('Real Prog');
    });

    it('shows a translated empty message when the school has no programmes', async () => {
      await renderAt('/schools/7');
      await settle();

      expect(q('app-error-state')).toBeNull();
      expect(text()).toContain('No programmes are listed for this school yet');
    });
  });

  describe('rating', () => {
    const q = (sel: string) => harness.routeNativeElement?.querySelector(sel) ?? null;
    const qa = (sel: string) => harness.routeNativeElement?.querySelectorAll(sel) ?? [];
    const progs = [{ id: 5, name: 'Prog', duration: 3, school: { id: 7 } }] as Program[];
    async function settle() {
      harness.detectChanges();
      await harness.fixture.whenStable();
    }
    async function render() {
      programServiceSpy.getPrograms.and.returnValue(of(pageOf(progs)));
      const component = await renderAt('/schools/7');
      await settle();
      return component;
    }
    /** Stars of the school widget, in the sidebar. */
    const schoolStars = () => qa('[data-testid="school-rating"] app-star-rating button');

    beforeEach(async () => {
      const transloco = TestBed.inject(TranslocoService);
      await firstValueFrom(transloco.load('en'));
      transloco.setActiveLang('en');
      locale.set('en');
    });

    it('lets a student rate the school but shows no programme rating controls', async () => {
      await render();

      expect(q('[data-testid="school-rating"] app-star-rating')).not.toBeNull();
      expect(q('[data-testid="programme-rating"]')).toBeNull();
    });

    it('lets an expert rate the school and its programmes', async () => {
      currentUser.set({ id: 4, role: { name: 'ROLE_EXPERT' } });
      await render();

      expect(q('[data-testid="school-rating"] app-star-rating')).not.toBeNull();
      expect(q('[data-testid="programme-rating"] app-star-rating')).not.toBeNull();
    });

    it('offers expert-only course rating controls in the courses modal', async () => {
      currentUser.set({ id: 4, role: { name: 'ROLE_EXPERT' } });
      const component = await render();
      const withCourses = { ...progs[0], courses: [{ id: 9, name: 'C' } as Course] };
      component.openCoursesModal(withCourses);
      await settle();
      expect(q('[data-testid="course-rating"] app-star-rating')).not.toBeNull();

      component.closeCoursesModal();
      currentUser.set({ id: 3, role: { name: 'ROLE_STUDENT' } });
      component.openCoursesModal(withCourses);
      await settle();
      expect(q('[data-testid="course-rating"]')).toBeNull();
    });

    it('shows an anonymous visitor a sign-in prompt instead of any rating control', async () => {
      authenticated.set(false);
      currentUser.set(null);
      await render();

      expect(q('app-star-rating')).toBeNull();
      expect(q('[data-testid="school-rating"]')?.textContent).toContain('Sign in to leave a rating');
    });

    it('shows no rating controls to an admin, who cannot rate', async () => {
      currentUser.set({ id: 1, role: { name: 'ROLE_ADMIN' } });
      await render();

      expect(q('app-star-rating')).toBeNull();
    });

    it('submits the school rating and refreshes the aggregate from the API', async () => {
      ratingServiceSpy.rateSchool.and.returnValue(of({} as any));
      const component = await render();
      schoolServiceSpy.getSchool.and.returnValue(of({ ...mockSchool, rating: 4.5 }));

      (schoolStars()[3] as HTMLButtonElement).click();
      await settle();
      (q('[data-testid="school-rating"] textarea + button') as HTMLButtonElement).click();
      await settle();

      expect(ratingServiceSpy.rateSchool).toHaveBeenCalledWith(7, 3, 4, '');
      expect(component.school()?.rating).toBe(4.5);
      expect(q('[data-testid="school-aggregate"]')?.textContent).toContain('4.5');
      expect(programServiceSpy.getPrograms).toHaveBeenCalledTimes(1);
    });
  });

  describe('what the API does not provide', () => {
    const text = () => harness.routeNativeElement?.textContent ?? '';
    async function renderWith(school: School, programmes: Program[]) {
      schoolServiceSpy.getSchool.and.returnValue(of({ ...school, programs: programmes }));
      await renderAt('/schools/7');
      harness.detectChanges();
      await harness.fixture.whenStable();
    }

    beforeEach(async () => {
      const transloco = TestBed.inject(TranslocoService);
      await firstValueFrom(transloco.load('en'));
      transloco.setActiveLang('en');
      locale.set('en');
    });

    it('shows no availability, waiting list or intake for a programme without capacity or start date', async () => {
      await renderWith(mockSchool, [{ id: 1, name: 'Prog', duration: 3 }]);

      expect(text()).toContain('Prog');
      expect(text()).not.toContain('Full');
      expect(text()).not.toContain('Waiting list');
      expect(text()).not.toContain('Intake');
    });

    it('shows availability and intake when the programme has them', async () => {
      await renderWith(mockSchool, [{ id: 1, name: 'Prog', duration: 3, capacity: 30, enrolled: 10, startDate: '2026-09' }]);

      expect(text()).toContain('20 places available');
      expect(text()).toContain('Intake: 2026-09');
      expect(text()).not.toContain('Waiting list');
    });

    it('offers the waiting list only when the programme is known to be full', async () => {
      await renderWith(mockSchool, [{ id: 1, name: 'Prog', duration: 3, capacity: 10, enrolled: 10 }]);

      expect(text()).toContain('Full');
      expect(text()).toContain('Waiting list');
    });

    it('rounds the average to one decimal and shows no review count the API did not send', async () => {
      await renderWith({ ...mockSchool, rating: 5.333333333333333 }, []);

      expect(harness.routeNativeElement?.querySelector('[data-testid="school-aggregate"]')?.textContent?.trim()).toBe('5.3');
      expect(text()).not.toContain('verified reviews');
    });

    describe('the aggregate stars', () => {
      const filledStars = () =>
        Array.from(
          harness.routeNativeElement!.querySelectorAll('[data-testid="school-aggregate-stars"] lucide-icon') as NodeListOf<HTMLElement>,
        ).map((icon) => !icon.classList.contains('opacity-30'));

      it('are all dimmed for a school nobody has rated', async () => {
        await renderWith({ ...mockSchool, rating: 0 }, []);

        expect(filledStars()).toEqual([false, false, false, false, false]);
      });

      it('fill as many stars as the rounded average', async () => {
        await renderWith({ ...mockSchool, rating: 3.4 }, []);

        expect(filledStars()).toEqual([true, true, true, false, false]);
      });

      it('round up from the half star', async () => {
        await renderWith({ ...mockSchool, rating: 3.6 }, []);

        expect(filledStars()).toEqual([true, true, true, true, false]);
      });

      it('fill all five for a perfect average', async () => {
        await renderWith({ ...mockSchool, rating: 5 }, []);

        expect(filledStars()).toEqual([true, true, true, true, true]);
      });
    });

    it('shows the review count when the API sends one', async () => {
      await renderWith({ ...mockSchool, rating: 4, reviewCount: 3 }, []);

      expect(text()).toContain('3 verified reviews');
    });
  });
});
