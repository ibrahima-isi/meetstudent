import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom, of, Subject, throwError } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { CourseService } from '@services/course.service';
import { CoursesPageComponent } from './courses-page.component';

describe('CoursesPageComponent', () => {
  let harness: RouterTestingHarness;
  let router: Router;
  let listCourses: jasmine.Spy;
  let searchCoursesByName: jasmine.Spy;

  const course = (id: number, name: string, extra: Record<string, unknown> = {}) => ({ id, name, ...extra });
  const page = (content: unknown[], number = 0, last = true, totalElements = content.length) => ({
    content,
    number,
    last,
    totalElements,
  });
  const full = course(1, 'Algorithms', {
    code: 'ALG1',
    averageRate: 4.26,
    programId: 7,
    programName: 'Computer Science',
    photo: { publicUrl: '/uploads/public/alg.png' },
  });
  const bare = course(2, 'Bare Course');

  const el = () => harness.routeNativeElement as HTMLElement;
  const text = () => el().textContent ?? '';
  const q = (sel: string) => el().querySelector(sel);
  const input = () => q('input[type="text"]') as HTMLInputElement;

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  async function open(url: string, lang: 'fr' | 'en' = 'en') {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'courses', component: CoursesPageComponent }]),
        provideTransloco(translocoOptions),
        { provide: CourseService, useValue: { listCourses, searchCoursesByName } },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl(url, CoursesPageComponent);
    await settle();
    return component;
  }

  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate();
    listCourses = jasmine.createSpy('listCourses').and.returnValue(of(page([full, bare])));
    searchCoursesByName = jasmine
      .createSpy('searchCoursesByName')
      .and.returnValue(of(page([course(3, 'Databases')])));
  });

  afterEach(() => jasmine.clock().uninstall());

  it('shows a loading state while the first page is pending', async () => {
    listCourses.and.returnValue(new Subject<never>());
    await open('/courses');

    expect(q('[aria-busy="true"]')).not.toBeNull();
  });

  it('lists the first page of 12 without searching by name', async () => {
    await open('/courses');

    expect(listCourses.calls.mostRecent().args.slice(0, 2)).toEqual([0, 12]);
    expect(searchCoursesByName).not.toHaveBeenCalled();
    expect(text()).toContain('Algorithms');
    expect(text()).toContain('2 courses found');
  });

  it('shows name, code, one-decimal rate, program name as plain text and photo of a complete course', async () => {
    await open('/courses');
    const card = q('[data-testid="course-card"]') as HTMLElement;

    expect(card.textContent).toContain('Algorithms');
    expect(card.textContent).toContain('ALG1');
    expect(card.textContent).toContain('4.3');
    expect(card.textContent).toContain('Computer Science');
    // No public program page exists: the program is text, never a dead link.
    expect(card.querySelector('a')).toBeNull();
    const img = card.querySelector('img');
    expect((img?.getAttribute('data-original-url') ?? img?.getAttribute('src')) ?? '').toContain('/uploads/public/alg.png');
    expect(card.textContent).not.toContain('Not provided');
  });

  it('marks absent fields "Not provided" and shows no rate or invented value', async () => {
    await open('/courses');
    const card = (el().querySelectorAll('[data-testid="course-card"]')[1]) as HTMLElement;

    expect(card.textContent).toContain('Bare Course');
    expect(card.querySelectorAll('[data-testid="not-provided"]').length).toBe(2);
    expect(card.textContent).toContain('Not provided');
    expect(card.querySelector('[data-testid="course-rate"]')).toBeNull();
    expect(card.querySelector('a')).toBeNull();
  });

  it('shows a translated empty message when there are no courses', async () => {
    listCourses.and.returnValue(of(page([])));
    await open('/courses');

    expect(text()).toContain('No courses are listed yet');
  });

  it('says nothing matches when a search finds no course', async () => {
    searchCoursesByName.and.returnValue(of(page([])));
    await open('/courses?q=zzz');

    expect(text()).toContain('No course matches this search');
  });

  it('shows the error state, never fake courses, then recovers on retry', async () => {
    listCourses.and.returnValue(throwError(() => new Error('down')));
    await open('/courses');
    expect(q('app-error-state')).not.toBeNull();
    expect(q('[data-testid="course-card"]')).toBeNull();

    listCourses.and.returnValue(of(page([full])));
    (q('app-error-state button') as HTMLButtonElement).click();
    await settle();

    expect(listCourses).toHaveBeenCalledTimes(2);
    expect(q('app-error-state')).toBeNull();
    expect(text()).toContain('Algorithms');
  });

  it('?q=data searches by name once, prefills the input and lists the answer', async () => {
    const component = await open('/courses?q=data');

    expect(searchCoursesByName).toHaveBeenCalledTimes(1);
    expect(searchCoursesByName).toHaveBeenCalledWith('data', 0, 12);
    expect(listCourses).not.toHaveBeenCalled();
    expect(component.searchQuery()).toBe('data');
    expect(input().value).toBe('data');
    expect(text()).toContain('Databases');
  });

  it('typing waits 300ms, then writes q to the URL and searches', async () => {
    await open('/courses');
    const navigate = spyOn(router, 'navigate').and.callThrough();

    for (const v of ['d', 'da', 'dat']) {
      input().value = v;
      input().dispatchEvent(new Event('input'));
    }
    jasmine.clock().tick(299);
    await settle();
    expect(navigate).not.toHaveBeenCalled();

    jasmine.clock().tick(1);
    await settle();
    expect(router.url).toBe('/courses?q=dat');
    expect(searchCoursesByName).toHaveBeenCalledOnceWith('dat', 0, 12);
  });

  it('loads the next page, appends it and hides the button on the last page', async () => {
    listCourses.and.callFake((n: number) =>
      of(n === 0 ? page([full], 0, false, 2) : page([bare], 1, true, 2)),
    );
    await open('/courses');

    (q('[data-testid="load-more"]') as HTMLButtonElement).click();
    await settle();

    expect(listCourses.calls.mostRecent().args.slice(0, 2)).toEqual([1, 12]);
    expect(el().querySelectorAll('[data-testid="course-card"]').length).toBe(2);
    expect(q('[data-testid="load-more"]')).toBeNull();
  });

  it('keeps the search icon inside the input padding, clear of the typed text', async () => {
    await open('/courses');
    const icon = input().parentElement!.querySelector('svg')!.getBoundingClientRect();
    const box = input().getBoundingClientRect();
    const textStart = box.left + parseFloat(getComputedStyle(input()).paddingLeft);

    expect(icon.left).toBeGreaterThanOrEqual(box.left);
    expect(icon.right).toBeLessThanOrEqual(textStart);
  });

  it('renders in French, the source language', async () => {
    await open('/courses', 'fr');

    expect(text()).toContain('2 cours trouvés');
    expect(text()).toContain('Non renseigné');
    expect(text()).toContain('ALG1');
  });
});
