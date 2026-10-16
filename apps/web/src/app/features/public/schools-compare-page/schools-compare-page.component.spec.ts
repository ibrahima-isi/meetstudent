import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTransloco } from '@jsverse/transloco';
import { NEVER, Observable, firstValueFrom, of, throwError } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { SchoolService } from '@services/school.service';
import { Router } from '@angular/router';
import { routes } from '../../../app.routes';
import { SchoolsComparePageComponent } from './schools-compare-page.component';

type Fake = { id: number; name: string; rating?: number; address?: unknown; programs?: unknown[]; tags?: { id: number; name: string }[] };

describe('SchoolsComparePageComponent', () => {
  let harness: RouterTestingHarness;
  let getSchool: jasmine.Spy;
  let data: Record<number, Observable<Fake>>;

  const address = (city: string, country: string) => ({ location: 'L', city, country });
  const el = () => harness.routeNativeElement as HTMLElement;
  const text = () => el().textContent ?? '';
  const q = (sel: string) => el().querySelector(sel);
  const columns = () => Array.from(el().querySelectorAll('thead th[scope="col"] a')).map((a) => a.textContent?.trim());

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  async function open(url: string, lang: 'en' | 'fr' = 'en') {
    getSchool = jasmine.createSpy('getSchool').and.callFake((id: number) => data[id]);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'compare', component: SchoolsComparePageComponent }]),
        provideTransloco(translocoOptions),
        { provide: SchoolService, useValue: { getSchool } },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, SchoolsComparePageComponent);
    await settle();
  }

  beforeEach(() => {
    data = {
      1: of({ id: 1, name: 'Mid', rating: 3, address: address('Dakar', 'Senegal'), programs: [{}, {}], tags: [{ id: 9, name: 'Public' }] }),
      2: of({ id: 2, name: 'Zeta' }),
      3: of({ id: 3, name: 'Top', rating: 4.26, address: address('Thiès', 'Senegal'), programs: [{}], tags: [] }),
      4: of({ id: 4, name: 'Alpha' }),
    };
  });

  it('ranks by rating descending, unrated last, then name, then id', async () => {
    data[5] = of({ id: 5, name: 'Alpha' });
    await open('/compare?ids=2,1,3');
    expect(columns()).toEqual(['Top', 'Mid', 'Zeta']);

    TestBed.resetTestingModule();
    await open('/compare?ids=5,4,2');
    expect(columns()).toEqual(['Alpha', 'Alpha', 'Zeta']);
    expect(Array.from(el().querySelectorAll('thead th[scope="col"] a')).map((a) => a.getAttribute('href'))).toEqual([
      '/en/schools/4',
      '/en/schools/5',
      '/en/schools/2',
    ]);
  });

  it('loads each school with the existing service', async () => {
    await open('/compare?ids=1,3');

    expect(getSchool.calls.allArgs()).toEqual([[1], [3]]);
  });

  it('shows name, place, rounded rating, programme count and tags', async () => {
    await open('/compare?ids=1,3');

    expect(text()).toContain('Dakar, Senegal');
    expect(text()).toContain('4.3');
    expect(text()).toContain('2 programmes');
    expect(text()).toContain('1 programme');
    expect(text()).toContain('Public');
  });

  it('says Not provided / Not rated instead of inventing data', async () => {
    await open('/compare?ids=1,2');

    const zeta = Array.from(el().querySelectorAll('tbody tr')).map((tr) => tr.children[2]?.textContent?.trim());
    expect(zeta).toEqual(['Not provided', 'Not rated', '0 programmes', 'Not provided']);
  });

  for (const ids of ['1', '1,2,3,4', 'a,b', '', '1,1']) {
    it(`asks for 2 to 3 schools and links back when ids="${ids}", without loading anything`, async () => {
      await open(`/compare?ids=${ids}`);

      expect(text()).toContain('Choose between 2 and 3 schools');
      expect(q('a[href="/en/schools"]')).not.toBeNull();
      expect(q('table')).toBeNull();
      expect(getSchool).not.toHaveBeenCalled();
    });
  }

  it('shows a loading state while the schools are pending', async () => {
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
    expect(q('a[href="/fr/schools/1"]')).not.toBeNull();
  });

  it('is declared before the guarded schools/:id route and stays public', async () => {
    TestBed.resetTestingModule();
    getSchool = jasmine.createSpy('getSchool').and.callFake((id: number) => data[id]);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(routes),
        provideTransloco(translocoOptions),
        { provide: SchoolService, useValue: { getSchool } },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use('en'));
    const router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/en/schools/compare?ids=1,3');
    await settle();

    expect(router.url).toBe('/en/schools/compare?ids=1,3');
    expect(getSchool.calls.allArgs()).toEqual([[1], [3]]);
  });
});
