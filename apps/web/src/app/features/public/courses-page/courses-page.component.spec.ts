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

  it('keeps the loaded courses when the next page fails, then appends it on retry', async () => {
    let nextPageFails = true;
    listCourses.and.callFake((n: number) => {
      if (n === 0) return of(page([full], 0, false, 2));
      return nextPageFails ? throwError(() => new Error('down')) : of(page([bare], 1, true, 2));
    });
    await open('/courses');

    (q('[data-testid="load-more"]') as HTMLButtonElement).click();
    await settle();
    expect(q('[data-testid="load-more-error"]')?.textContent).toContain('Retry');
    expect(el().querySelectorAll('[data-testid="course-card"]').length).toBe(1);
    expect(q('app-error-state')).toBeNull();

    nextPageFails = false;
    (q('[data-testid="load-more-error"] button') as HTMLButtonElement).click();
    await settle();

    expect(listCourses.calls.mostRecent().args.slice(0, 2)).toEqual([1, 12]);
    expect(el().querySelectorAll('[data-testid="course-card"]').length).toBe(2);
    expect(q('[data-testid="load-more-error"]')).toBeNull();
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

  describe('comparison selection', () => {
    const boxes = () => Array.from(el().querySelectorAll<HTMLInputElement>('input[type="checkbox"]'));
    const box = (name: string) => el().querySelector<HTMLInputElement>(`input[aria-label="Select ${name} to compare"]`);
    const bar = () => q('[data-testid="compare-bar"]');
    const compareButton = () => q('[data-testid="compare-action"]') as HTMLButtonElement | null;

    async function openWithFour(url: string) {
      const content = [1, 2, 3, 4].map((id) => course(id, ['Alpha', 'Beta', 'Gamma', 'Delta'][id - 1]));
      listCourses.and.returnValue(of(page(content)));
      searchCoursesByName.and.returnValue(of(page(content)));
      TestBed.configureTestingModule({
        providers: [
          provideZonelessChangeDetection(),
          provideHttpClient(),
          provideHttpClientTesting(),
          provideRouter([
            { path: 'courses', component: CoursesPageComponent },
            { path: 'en/courses/compare', children: [] },
          ]),
          provideTransloco(translocoOptions),
          { provide: CourseService, useValue: { listCourses, searchCoursesByName } },
        ],
      });
      await firstValueFrom(TestBed.inject(LocaleService).use('en'));
      router = TestBed.inject(Router);
      harness = await RouterTestingHarness.create();
      await harness.navigateByUrl(url, CoursesPageComponent);
      await settle();
    }

    async function check(name: string): Promise<void> {
      box(name)!.click();
      await settle();
    }

    it('gives every card a select checkbox with an accessible label', async () => {
      await openWithFour('/courses');

      expect(boxes().length).toBe(4);
      expect(box('Alpha')).not.toBeNull();
    });

    it('shows no bar until something is selected', async () => {
      await openWithFour('/courses');

      expect(bar()).toBeNull();
    });

    it('announces the count and the limit in a live region that exists before the first pick', async () => {
      await openWithFour('/courses');
      const live = q('[data-testid="compare-status"]');
      expect(live?.getAttribute('role')).toBe('status');
      expect(live?.textContent?.trim()).toBe('');

      await check('Alpha');
      expect(q('[data-testid="compare-status"]')).toBe(live);
      expect(live?.textContent).toContain('1 course selected');

      await check('Beta');
      await check('Gamma');
      await check('Delta');
      expect(live?.textContent).toContain('You can compare up to 3 courses');
    });

    it('keeps Compare disabled with one course and enables it at two, mirroring the selection in the URL next to ?q=', async () => {
      await openWithFour('/courses?q=a');
      await check('Alpha');

      expect(bar()?.textContent).toContain('1 course selected');
      expect(compareButton()?.disabled).toBeTrue();

      await check('Beta');

      expect(bar()?.textContent).toContain('2 courses selected');
      expect(compareButton()?.disabled).toBeFalse();
      const params = router.parseUrl(router.url).queryParams;
      expect(params['compare']).toBe('1,2');
      expect(params['q']).toBe('a');
    });

    it('refuses a 4th course with a message and leaves the box unchecked', async () => {
      await openWithFour('/courses');
      await check('Alpha');
      await check('Beta');
      await check('Gamma');
      await check('Delta');

      expect(box('Delta')!.checked).toBeFalse();
      expect(router.parseUrl(router.url).queryParams['compare']).toBe('1,2,3');
      expect(q('[data-testid="compare-limit"]')?.textContent).toContain('You can compare up to 3 courses');
    });

    it('restores the selection from ?compare= on load, and drops a course when unchecked', async () => {
      await openWithFour('/courses?compare=1,2');

      expect(box('Alpha')!.checked).toBeTrue();
      expect(box('Beta')!.checked).toBeTrue();
      expect(box('Gamma')!.checked).toBeFalse();

      await check('Alpha');

      expect(router.parseUrl(router.url).queryParams['compare']).toBe('2');
    });

    it('ignores invalid and surplus ids in ?compare=', async () => {
      await openWithFour('/courses?compare=1,x,1,-2,2,3,4');

      expect(bar()?.textContent).toContain('3 courses selected');
      expect(box('Delta')!.checked).toBeFalse();
    });

    it('clears the selection', async () => {
      await openWithFour('/courses?compare=1,2');

      (Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.includes('Clear selection')) as HTMLButtonElement).click();
      await settle();

      expect(router.parseUrl(router.url).queryParams['compare']).toBeUndefined();
      expect(bar()).toBeNull();
    });

    it('goes to the comparison page with the selected ids', async () => {
      await openWithFour('/courses?compare=2,3');

      compareButton()!.click();
      await settle();

      expect(router.url).toBe('/en/courses/compare?ids=2,3');
    });
  });
});
