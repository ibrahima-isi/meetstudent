import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTransloco } from '@jsverse/transloco';
import { NEVER, Observable, firstValueFrom, of, throwError } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { CourseService } from '@services/course.service';
import { routes } from '../../../app.routes';
import { CoursesComparePageComponent } from './courses-compare-page.component';

type Fake = {
  id: number;
  name: string;
  code?: string;
  averageRate?: number | null;
  programId?: number;
  programName?: string | null;
  // Things the page must never show.
  level?: string;
  description?: string;
  schoolName?: string;
};

describe('CoursesComparePageComponent', () => {
  let harness: RouterTestingHarness;
  let getCourse: jasmine.Spy;
  let data: Record<number, Observable<Fake>>;

  const el = () => harness.routeNativeElement as HTMLElement;
  const text = () => el().textContent ?? '';
  const q = (sel: string) => el().querySelector(sel);
  const columns = () => Array.from(el().querySelectorAll('thead th[scope="col"]')).slice(1).map((th) => th.textContent?.replace(/\s+/g, ' ').trim());
  const row = (label: string) =>
    Array.from(el().querySelectorAll('tbody tr')).find((tr) => tr.children[0]?.textContent?.trim() === label);
  const cells = (label: string) => Array.from(row(label)?.querySelectorAll('td') ?? []).map((td) => td.textContent?.replace(/\s+/g, ' ').trim());

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  async function open(url: string, lang: 'en' | 'fr' = 'en') {
    getCourse = jasmine.createSpy('getCourse').and.callFake((id: number) => data[id]);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'compare', component: CoursesComparePageComponent }]),
        provideTransloco(translocoOptions),
        { provide: CourseService, useValue: { getCourse } },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, CoursesComparePageComponent);
    await settle();
  }

  beforeEach(() => {
    data = {
      1: of({ id: 1, name: 'Mid', code: 'MID', averageRate: 3, programId: 7, programName: 'Computer Science' }),
      2: of({ id: 2, name: 'Zeta' }),
      3: of({ id: 3, name: 'Top', code: 'TOP', averageRate: 4.26, programName: 'Maths' }),
      4: of({ id: 4, name: 'Alpha' }),
    };
  });

  it('ranks by rating descending, unrated last, then name, then id', async () => {
    await open('/compare?ids=2,1,3');
    expect(columns()).toEqual(['Top', 'Mid', 'Zeta']);

    TestBed.resetTestingModule();
    data[5] = of({ id: 5, name: 'Alpha' });
    await open('/compare?ids=5,4,2');
    expect(columns()).toEqual(['Alpha', 'Alpha', 'Zeta']);
    expect(getCourse.calls.allArgs()).toEqual([[5], [4], [2]]);
  });

  it('puts an explicit 0 rating ahead of the unrated', async () => {
    data[2] = of({ id: 2, name: 'Zeta', averageRate: 0 });
    data[4] = of({ id: 4, name: 'Alpha', averageRate: null });
    await open('/compare?ids=4,2');

    expect(columns()).toEqual(['Zeta', 'Alpha']);
    expect(cells('Average rating')).toEqual(['0', 'Not rated']);
  });

  it('loads each course with the existing service', async () => {
    await open('/compare?ids=1,3');

    expect(getCourse.calls.allArgs()).toEqual([[1], [3]]);
  });

  it('shows name, code, program and rounded rating', async () => {
    await open('/compare?ids=1,3');

    expect(columns()).toEqual(['Top', 'Mid']);
    expect(cells('Code')).toEqual(['TOP', 'MID']);
    expect(cells('Programme')).toEqual(['Maths', 'Computer Science']);
    expect(cells('Average rating')).toEqual(['4.3', '3']);
  });

  it('never links the program or the course name: no public page exists', async () => {
    await open('/compare?ids=1,3');

    expect(q('tbody a')).toBeNull();
    expect(q('thead a')).toBeNull();
  });

  it('says Not provided / Not rated instead of inventing data', async () => {
    data[4] = of({ id: 4, name: 'Alpha', programName: null, averageRate: null });
    await open('/compare?ids=1,2');

    expect(cells('Code')[1]).toBe('Not provided');
    expect(cells('Programme')[1]).toBe('Not provided');
    expect(cells('Average rating')[1]).toBe('Not rated');

    TestBed.resetTestingModule();
    await open('/compare?ids=1,4');
    expect(cells('Programme')[1]).toBe('Not provided');
    expect(cells('Average rating')[1]).toBe('Not rated');
  });

  it('never renders level, description or school', async () => {
    data[1] = of({ id: 1, name: 'Mid', level: 'Master', description: 'A long description', schoolName: 'Hidden School' });
    await open('/compare?ids=1,3');

    for (const hidden of ['Master', 'A long description', 'Hidden School', 'Level', 'Description', 'School']) {
      expect(text()).withContext(hidden).not.toContain(hidden);
    }
  });

  it('lets a keyboard reach the horizontally scrolling table on a phone', async () => {
    await open('/compare?ids=1,3');

    const region = q('table')?.parentElement;
    expect(region?.getAttribute('tabindex')).toBe('0');
    expect(region?.getAttribute('role')).toBe('region');
    expect(region?.getAttribute('aria-label')).toBe('Compare courses');
  });

  it('links back to the catalogue with the same selection, to change it', async () => {
    await open('/compare?ids=3,1');

    expect(q('a[href="/en/courses?compare=3,1"]')).not.toBeNull();
  });

  it('says a course no longer exists on a 404, without a pointless retry, and offers the rest of the selection', async () => {
    data[2] = throwError(() => new HttpErrorResponse({ status: 404 }));
    await open('/compare?ids=1,2,3');

    expect(text()).toContain('One of these courses no longer exists');
    expect(q('app-error-state')).toBeNull();
    expect(q('table')).toBeNull();
    expect(q('a[href="/en/courses?compare=1,3"]')).not.toBeNull();
  });

  for (const ids of ['1', '1,2,3,4', 'a,b', '', '1,1']) {
    it(`asks for 2 to 3 courses and links back when ids="${ids}", without loading anything`, async () => {
      await open(`/compare?ids=${ids}`);

      expect(text()).toContain('Choose between 2 and 3 courses');
      expect(q('a[href="/en/courses"]')).not.toBeNull();
      expect(q('table')).toBeNull();
      expect(getCourse).not.toHaveBeenCalled();
    });
  }

  it('shows a loading state while the courses are pending', async () => {
    data[1] = NEVER;
    await open('/compare?ids=1,3');

    expect(q('[aria-busy="true"]')).not.toBeNull();
    expect(q('table')).toBeNull();
  });

  it('shows the error state and retries', async () => {
    data[3] = throwError(() => new Error('down'));
    await open('/compare?ids=1,3');
    expect(q('app-error-state')).not.toBeNull();

    data[3] = of({ id: 3, name: 'Top' });
    (q('app-error-state button') as HTMLButtonElement).click();
    await settle();

    expect(q('app-error-state')).toBeNull();
    expect(text()).toContain('Top');
  });

  it('renders in French', async () => {
    await open('/compare?ids=1,2', 'fr');

    expect(text()).toContain('Non noté');
    expect(text()).toContain('Non renseigné');
    expect(cells('Formation')).toEqual(['Computer Science', 'Non renseigné']);
    expect(cells('Note moyenne').length).toBe(2);
  });

  it('is declared before any courses/:id route and stays public', async () => {
    TestBed.resetTestingModule();
    getCourse = jasmine.createSpy('getCourse').and.callFake((id: number) => data[id]);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(routes),
        provideTransloco(translocoOptions),
        { provide: CourseService, useValue: { getCourse } },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use('en'));
    const router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/en/courses/compare?ids=1,3');
    await settle();

    expect(router.url).toBe('/en/courses/compare?ids=1,3');
    expect(getCourse.calls.allArgs()).toEqual([[1], [3]]);
  });
});
