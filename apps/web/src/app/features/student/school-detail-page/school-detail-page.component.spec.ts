import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, withComponentInputBinding, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
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
    const harness = await RouterTestingHarness.create();
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
        { provide: LocaleService, useValue: { active: signal('fr' as const) } },
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
});
