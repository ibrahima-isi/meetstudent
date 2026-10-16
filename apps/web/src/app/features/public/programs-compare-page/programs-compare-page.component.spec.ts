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
import { ProgramService } from '@services/program.service';
import { routes } from '../../../app.routes';
import { ProgramsComparePageComponent } from './programs-compare-page.component';

type Fake = {
  id: number;
  name: string;
  code?: string;
  rating?: number;
  duration?: number | null;
  schoolId?: number;
  schoolName?: string | null;
  schoolCode?: string | null;
  courses?: unknown[];
  // Things the page must never show.
  level?: string;
  description?: string;
  capacity?: number;
  reviewCount?: number;
  accreditations?: { id: number; name: string }[];
};

describe('ProgramsComparePageComponent', () => {
  let harness: RouterTestingHarness;
  let getProgram: jasmine.Spy;
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
    getProgram = jasmine.createSpy('getProgram').and.callFake((id: number) => data[id]);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'compare', component: ProgramsComparePageComponent }]),
        provideTransloco(translocoOptions),
        { provide: ProgramService, useValue: { getProgram } },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, ProgramsComparePageComponent);
    await settle();
  }

  beforeEach(() => {
    data = {
      1: of({ id: 1, name: 'Mid', code: 'MID', rating: 3, duration: 1, schoolId: 7, schoolName: 'ISI', schoolCode: 'I1', courses: [{}, {}] }),
      2: of({ id: 2, name: 'Zeta' }),
      3: of({ id: 3, name: 'Top', code: 'TOP', rating: 4.26, duration: 3, schoolId: 8, schoolName: 'UCAD', courses: [{}] }),
      4: of({ id: 4, name: 'Alpha' }),
    };
  });

  it('ranks by rating descending, unrated last, then name, then id', async () => {
    await open('/compare?ids=2,1,3');
    expect(columns().map((c) => c?.split(' ')[0])).toEqual(['Top', 'Mid', 'Zeta']);

    TestBed.resetTestingModule();
    data[5] = of({ id: 5, name: 'Alpha' });
    await open('/compare?ids=5,4,2');
    expect(columns().map((c) => c?.split(' ')[0])).toEqual(['Alpha', 'Alpha', 'Zeta']);
    expect(getProgram.calls.allArgs()).toEqual([[5], [4], [2]]);
  });

  it('loads each program with the existing service', async () => {
    await open('/compare?ids=1,3');

    expect(getProgram.calls.allArgs()).toEqual([[1], [3]]);
  });

  it('shows name and code, school, duration, rounded rating and courses count', async () => {
    await open('/compare?ids=1,3');

    expect(columns()).toEqual(['Top TOP', 'Mid MID']);
    expect(cells('School')).toEqual(['UCAD', 'ISI (I1)']);
    expect(cells('Duration')).toEqual(['3 years', '1 year']);
    expect(cells('Average rating')).toEqual(['4.3', '3']);
    expect(cells('Courses')).toEqual(['1 course', '2 courses']);
  });

  it('links the school only when its id exists, and never links the program name', async () => {
    data[2] = of({ id: 2, name: 'Zeta', schoolName: 'Orphan School' });
    await open('/compare?ids=1,2');

    expect(q('tbody a[href="/en/schools/7"]')?.textContent?.trim()).toBe('ISI');
    expect(text()).toContain('Orphan School');
    expect(Array.from(el().querySelectorAll('tbody a')).map((a) => a.getAttribute('href'))).toEqual(['/en/schools/7']);
    expect(q('thead a')).toBeNull();
  });

  it('says Not provided / Not rated instead of inventing data', async () => {
    await open('/compare?ids=1,2');

    expect(cells('School')[1]).toBe('Not provided');
    expect(cells('Duration')[1]).toBe('Not provided');
    expect(cells('Average rating')[1]).toBe('Not rated');
    // No course list from the API is unknown, not a false 0.
    expect(cells('Courses')[1]).toBe('Not provided');
  });

  it('counts an empty course list as 0 courses', async () => {
    data[2] = of({ id: 2, name: 'Zeta', courses: [] });
    await open('/compare?ids=1,2');

    expect(cells('Courses')).toEqual(['2 courses', '0 courses']);
  });

  it('treats a null school name as not provided', async () => {
    data[2] = of({ id: 2, name: 'Zeta', schoolId: 9, schoolName: null, duration: null });
    await open('/compare?ids=1,2');

    expect(cells('School')[1]).toBe('Not provided');
    expect(cells('Duration')[1]).toBe('Not provided');
  });

  it('never renders level, description, capacity, review count or accreditations', async () => {
    data[1] = of({
      id: 1, name: 'Mid', level: 'Master', description: 'A long description', capacity: 40, reviewCount: 12,
      accreditations: [{ id: 1, name: 'CAMES' }],
    });
    await open('/compare?ids=1,3');

    for (const hidden of ['Master', 'A long description', '40', '12', 'CAMES', 'Level', 'Description', 'Capacity', 'Accreditation']) {
      expect(text()).withContext(hidden).not.toContain(hidden);
    }
  });

  it('lets a keyboard reach the horizontally scrolling table on a phone', async () => {
    await open('/compare?ids=1,3');

    const region = q('table')?.parentElement;
    expect(region?.getAttribute('tabindex')).toBe('0');
    expect(region?.getAttribute('role')).toBe('region');
    expect(region?.getAttribute('aria-label')).toBe('Compare programmes');
  });

  it('links back to the catalogue with the same selection, to change it', async () => {
    await open('/compare?ids=3,1');

    expect(q('a[href="/en/programs?compare=3,1"]')).not.toBeNull();
  });

  it('says a program no longer exists on a 404, without a pointless retry, and offers the rest of the selection', async () => {
    data[2] = throwError(() => new HttpErrorResponse({ status: 404 }));
    await open('/compare?ids=1,2,3');

    expect(text()).toContain('One of these programmes no longer exists');
    expect(q('app-error-state')).toBeNull();
    expect(q('table')).toBeNull();
    expect(q('a[href="/en/programs?compare=1,3"]')).not.toBeNull();
  });

  for (const ids of ['1', '1,2,3,4', 'a,b', '', '1,1']) {
    it(`asks for 2 to 3 programmes and links back when ids="${ids}", without loading anything`, async () => {
      await open(`/compare?ids=${ids}`);

      expect(text()).toContain('Choose between 2 and 3 programmes');
      expect(q('a[href="/en/programs"]')).not.toBeNull();
      expect(q('table')).toBeNull();
      expect(getProgram).not.toHaveBeenCalled();
    });
  }

  it('shows a loading state while the programs are pending', async () => {
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
    expect(text()).toContain('2 cours');
    expect(q('a[href="/fr/schools/7"]')).not.toBeNull();
  });

  it('is declared before any programs/:id route and stays public', async () => {
    TestBed.resetTestingModule();
    getProgram = jasmine.createSpy('getProgram').and.callFake((id: number) => data[id]);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(routes),
        provideTransloco(translocoOptions),
        { provide: ProgramService, useValue: { getProgram } },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use('en'));
    const router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/en/programs/compare?ids=1,3');
    await settle();

    expect(router.url).toBe('/en/programs/compare?ids=1,3');
    expect(getProgram.calls.allArgs()).toEqual([[1], [3]]);
  });
});
