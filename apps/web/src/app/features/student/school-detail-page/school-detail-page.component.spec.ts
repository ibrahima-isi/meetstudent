import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, withComponentInputBinding, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { firstValueFrom, of, Subject, throwError } from 'rxjs';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { translocoOptions } from '@i18n/transloco.config';
import { School, Program, Course, Page } from '@models/entities';
import { ProgramService } from '@services/program.service';
import { CourseService } from '@services/course.service';
import { SchoolService } from '@services/school.service';
import { TokenService } from '@services/token.service';
import { RatingService } from '@services/rating.service';
import { LocaleService } from '@services/locale.service';
import { SchoolDetailPageComponent } from './school-detail-page.component';

describe('SchoolDetailPageComponent', () => {
  let programServiceSpy: jasmine.SpyObj<ProgramService>;
  let courseServiceSpy: jasmine.SpyObj<CourseService>;
  let schoolServiceSpy: jasmine.SpyObj<SchoolService>;
  let ratingServiceSpy: jasmine.SpyObj<RatingService>;
  let authenticated: ReturnType<typeof signal<boolean>>;
  let locale: ReturnType<typeof signal<'fr' | 'en'>>;
  let harness: RouterTestingHarness;

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
    courseServiceSpy = jasmine.createSpyObj('CourseService', ['getCoursesByProgram']);
    schoolServiceSpy = jasmine.createSpyObj('SchoolService', ['getSchool']);
    ratingServiceSpy = jasmine.createSpyObj('RatingService', [
      'rateSchool',
      'rateProgram',
      'rateCourse',
    ]);
    authenticated = signal(true);
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
        { provide: CourseService, useValue: courseServiceSpy },
        { provide: SchoolService, useValue: schoolServiceSpy },
        { provide: RatingService, useValue: ratingServiceSpy },
        { provide: TokenService, useValue: { isAuthenticated: authenticated } },
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
    const mockProgram: Program = { id: 1, name: 'Prog 1', duration: 3 };
    courseServiceSpy.getCoursesByProgram.and.returnValue(of([{ id: 1, name: 'Course 1' } as Course]));

    const component = await renderAt('/schools/7');
    component.openCoursesModal(mockProgram);

    expect(component.showCoursesModal()).toBeTrue();
    expect(component.selectedProgram()).toEqual(mockProgram);
    expect(courseServiceSpy.getCoursesByProgram).toHaveBeenCalledWith(1);
  });

  it('closes the courses modal', async () => {
    const component = await renderAt('/schools/7');
    component.showCoursesModal.set(true);

    component.closeCoursesModal();

    expect(component.showCoursesModal()).toBeFalse();
    expect(component.selectedProgram()).toBeNull();
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
});
