import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { SchoolsPageComponent } from './schools-page.component';
import { SchoolService } from '@services/school.service';
import { firstValueFrom, Observable, of, throwError } from 'rxjs';
import { LocaleService } from '@services/locale.service';
import { School } from '@models/entities';
import { signal } from '@angular/core';
import { Subject } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTransloco } from '@jsverse/transloco';
import { translocoOptions } from '@i18n/transloco.config';

describe('SchoolsPageComponent', () => {
  let component: SchoolsPageComponent;
  let fixture: ComponentFixture<SchoolsPageComponent>;
  let schoolServiceSpy: jasmine.SpyObj<SchoolService>;

  beforeEach(async () => {
    schoolServiceSpy = jasmine.createSpyObj('SchoolService', ['getSchools']);
    schoolServiceSpy.getSchools.and.returnValue(of({ content: [] } as any));

    await TestBed.configureTestingModule({
      imports: [SchoolsPageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        // The page navigates now instead of emitting, so it reaches Router and
        // LocaleService — and LocaleService reaches Transloco.
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: SchoolService, useValue: schoolServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SchoolsPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

describe('SchoolsPageComponent translations', () => {
  let fixture: ComponentFixture<SchoolsPageComponent>;

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  async function render(lang: 'fr' | 'en') {
    // The same call the locale guard makes: it moves the active locale and
    // loads its bundle, so *transloco has something to render.
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    fixture = TestBed.createComponent(SchoolsPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    const schoolService = jasmine.createSpyObj('SchoolService', ['getSchools']);
    schoolService.getSchools.and.returnValue(of({ content: [] }));

    TestBed.configureTestingModule({
      imports: [SchoolsPageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: SchoolService, useValue: schoolService },
      ],
    });
  });

  it('renders in French, the source language', async () => {
    await render('fr');

    expect(text()).toContain('Découvrez les meilleurs établissements du Sénégal');
    // French puts zero with one.
    expect(text()).toContain('0 établissement trouvé');
  });

  it('offers no sort by available places, since no capacity data exists', async () => {
    await render('en');

    expect(fixture.nativeElement.querySelector('option[value="places"]')).toBeNull();
    expect(text()).not.toContain('available places');
  });

  it('renders in English when English is active', async () => {
    await render('en');

    expect(text()).toContain('Discover the best schools in Senegal');
    // English puts zero with many.
    expect(text()).toContain('0 schools found');
  });

  it('labels the no-filter choice in the active language, not in the data', async () => {
    await render('en');
    fixture.componentInstance.showFilters.set(true);
    fixture.detectChanges();
    await fixture.whenStable();

    const options = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('option'),
    ).map((o) => o.textContent?.trim());
    expect(options).toContain('All cities');
    expect(options).toContain('All types');
    expect(options).not.toContain('Toutes les villes');
    expect(fixture.componentInstance.activeFiltersCount()).toBe(0);
  });


  it('names an untyped, undescribed school in the active language', async () => {
    await render('en');
    fixture.componentInstance.schools.set([
      { id: 1, name: 'Bare', address: { location: 'Loc', city: 'Dakar', country: 'Senegal' } },
    ]);
    fixture.detectChanges();
    await fixture.whenStable();

    const badge = (fixture.nativeElement as HTMLElement).querySelector('span.text-indigo-600.text-sm.font-bold');
    expect(badge?.textContent?.trim()).toBe('School');
    expect(text()).toContain('No description available');
    expect(text()).not.toContain('Établissement');
  });
  it('rounds the rating to one decimal and shows no review count the API did not send', async () => {
    await render('en');
    fixture.componentInstance.schools.set([
      { id: 1, name: 'Rated', rating: 5.333333333333333, address: { location: 'Loc', city: 'Dakar', country: 'Senegal' } },
    ]);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(text()).toContain('5.3');
    expect(text()).not.toContain('5.33');
    expect(text()).not.toContain('reviews)');
  });

  it('shows the review count when the API sends one', async () => {
    await render('en');
    fixture.componentInstance.schools.set([
      { id: 1, name: 'Rated', rating: 4, reviewCount: 12, address: { location: 'Loc', city: 'Dakar', country: 'Senegal' } },
    ]);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(text()).toContain('(12 reviews)');
  });
});

describe('SchoolsPageComponent data states', () => {
  let fixture: ComponentFixture<SchoolsPageComponent>;
  let getSchools: jasmine.Spy;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const q = (sel: string) => (fixture.nativeElement as HTMLElement).querySelector(sel);

  async function render(response: Observable<unknown>) {
    getSchools = jasmine.createSpy('getSchools').and.returnValue(response);
    TestBed.configureTestingModule({
      imports: [SchoolsPageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: SchoolService, useValue: { getSchools } },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use('en'));
    fixture = TestBed.createComponent(SchoolsPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('does not render its own header: the shell owns navigation', async () => {
    await render(of({ content: [], last: true, totalElements: 0 }));

    expect(q('header')).toBeNull();
  });

  it('shows a loading state while the request is pending', async () => {
    await render(new Subject<never>());

    expect(q('[aria-busy="true"]')).not.toBeNull();
    expect(text()).not.toContain('No school matches');
  });

  it('shows an error state, never fake schools, when the API fails', async () => {
    await render(throwError(() => new Error('down')));

    expect(q('app-error-state')).not.toBeNull();
    expect(fixture.componentInstance.schools()).toEqual([]);
    expect(text()).not.toContain('Cheikh Anta Diop');
    expect(text()).not.toContain('Gaston Berger');
  });

  it('retries the fetch and recovers when the retry button is pressed', async () => {
    await render(throwError(() => new Error('down')));
    getSchools.and.returnValue(
      of({ content: [{ id: 1, name: 'Real School', address: { city: 'Dakar' } }] }),
    );

    (q('app-error-state button') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(getSchools).toHaveBeenCalledTimes(2);
    expect(q('app-error-state')).toBeNull();
    expect(text()).toContain('Real School');
  });

  it('shows a translated empty message when the API returns no schools', async () => {
    await render(of({ content: [] }));

    expect(q('app-error-state')).toBeNull();
    expect(text()).toContain('No schools are listed yet');
  });

  it('builds the city and type filters from the loaded schools', async () => {
    await render(
      of({
        content: [
          { id: 1, name: 'A', type: 'Public', address: { city: 'Thiès' } },
          { id: 2, name: 'B', type: 'Public', address: { city: 'Thiès' } },
        ],
      }),
    );
    fixture.componentInstance.showFilters.set(true);
    fixture.detectChanges();

    const options = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('option')).map((o) =>
      o.textContent?.trim(),
    );
    expect(options.filter((o) => o === 'Thiès').length).toBe(1);
    expect(options).not.toContain('Dakar');
  });
});

describe('SchoolsPageComponent server search', () => {
  let harness: RouterTestingHarness;
  let router: Router;
  let getSchools: jasmine.Spy;
  let searchSchools: jasmine.Spy;
  let searchSchoolsByName: jasmine.Spy;

  const school = (id: number, name: string, city = 'Dakar', type = 'Public') => ({
    id,
    name,
    type,
    address: { city, location: 'L', country: 'SN' },
  });
  const page = (content: unknown[], number = 0, last = true, totalElements = content.length) => ({
    content,
    number,
    last,
    totalElements,
  });
  const el = () => harness.routeNativeElement as HTMLElement;
  const text = () => el().textContent ?? '';
  const input = () => el().querySelector('input[type="text"]') as HTMLInputElement;
  const q = (sel: string) => el().querySelector(sel);

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  function type(value: string): void {
    input().value = value;
    input().dispatchEvent(new Event('input'));
    harness.detectChanges();
  }

  function pick(selector: string, value: string): void {
    const select = q(selector) as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    harness.detectChanges();
  }

  async function open(url: string, service: Partial<Record<'getSchools' | 'searchSchools' | 'searchSchoolsByName', unknown>> = {}) {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'schools', component: SchoolsPageComponent }]),
        provideTransloco(translocoOptions),
        { provide: SchoolService, useValue: { getSchools, searchSchools, searchSchoolsByName, ...service } },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use('en'));
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl(url, SchoolsPageComponent);
    await settle();
    return component;
  }

  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate();
    getSchools = jasmine.createSpy('getSchools').and.returnValue(of(page([school(1, 'Alpha'), school(2, 'Beta', 'Thiès')])));
    searchSchools = jasmine.createSpy('searchSchools').and.returnValue(of(page([])));
    searchSchoolsByName = jasmine.createSpy('searchSchoolsByName').and.returnValue(of(page([school(3, 'Dakar Tech')])));
  });

  afterEach(() => jasmine.clock().uninstall());

  it('without ?q= lists one page of 12 and never searches by name', async () => {
    await open('/schools');

    expect(getSchools).toHaveBeenCalledTimes(1);
    expect(getSchools.calls.mostRecent().args.slice(0, 2)).toEqual([0, 12]);
    expect(getSchools.calls.mostRecent().args[3]).toBe('name,asc');
    expect(searchSchoolsByName).not.toHaveBeenCalled();
    expect(text()).toContain('Alpha');
  });

  it('?q=dakar searches on the server once, with no debounce, and prefills the input', async () => {
    const component = await open('/schools?q=dakar');

    expect(searchSchoolsByName).toHaveBeenCalledTimes(1);
    expect(searchSchoolsByName).toHaveBeenCalledWith('dakar', 0, 12, 'name,asc');
    expect(getSchools).not.toHaveBeenCalled();
    expect(component.searchQuery()).toBe('dakar');
    expect(input().value).toBe('dakar');
    expect(text()).toContain('Dakar Tech');
  });

  it('does not filter the server answer again on the client', async () => {
    searchSchoolsByName.and.returnValue(of(page([school(3, 'Unrelated Name')])));
    await open('/schools?q=dakar');

    expect(text()).toContain('Unrelated Name');
  });

  it('removing ?q= restarts with the plain listing and clears the input', async () => {
    await open('/schools?q=dakar');
    await router.navigateByUrl('/schools');
    await settle();

    expect(getSchools).toHaveBeenCalledTimes(1);
    expect(input().value).toBe('');
  });

  it('navigating to ?q=lyon cancels the request in flight and drops its late answer', async () => {
    const slow = new Subject<unknown>();
    searchSchoolsByName.and.callFake((term: string) =>
      term === 'dakar' ? slow : of(page([school(9, 'Lyon School')])),
    );
    await open('/schools?q=dakar');
    expect(q('[aria-busy="true"]')).not.toBeNull();

    await router.navigateByUrl('/schools?q=lyon');
    await settle();
    slow.next(page([school(8, 'Stale Dakar School')]));
    slow.complete();
    await settle();

    expect(searchSchoolsByName).toHaveBeenCalledWith('lyon', 0, 12, 'name,asc');
    expect(text()).toContain('Lyon School');
    expect(text()).not.toContain('Stale Dakar School');
  });

  it('typing d, da, dak makes no request until 300ms, then one navigation carrying q', async () => {
    await open('/schools');
    const navigate = spyOn(router, 'navigate').and.callThrough();
    getSchools.calls.reset();

    type('d');
    type('da');
    type('dak');
    jasmine.clock().tick(299);
    await settle();
    expect(navigate).not.toHaveBeenCalled();
    expect(searchSchoolsByName).not.toHaveBeenCalled();

    jasmine.clock().tick(1);
    await settle();

    expect(navigate).toHaveBeenCalledTimes(1);
    const [commands, extras] = navigate.calls.mostRecent().args;
    expect(commands).toEqual([]);
    expect(extras).toEqual(
      jasmine.objectContaining({ queryParams: { q: 'dak' }, queryParamsHandling: 'merge', replaceUrl: true }),
    );
    expect(router.url).toBe('/schools?q=dak');
    expect(searchSchoolsByName).toHaveBeenCalledTimes(1);
    expect(searchSchoolsByName.calls.mostRecent().args[0]).toBe('dak');
  });

  it('does not overwrite what the visitor keeps typing when the URL catches up', async () => {
    await open('/schools');
    type('dak');
    jasmine.clock().tick(300);
    type('dako');
    await settle();

    expect(router.url).toBe('/schools?q=dak');
    expect(input().value).toBe('dako');
  });

  it('typing the same term again after the URL cleared it still searches', async () => {
    await open('/schools');
    type('dak');
    jasmine.clock().tick(300);
    await settle();
    await router.navigateByUrl('/schools');
    await settle();
    searchSchoolsByName.calls.reset();

    type('dak');
    jasmine.clock().tick(300);
    await settle();

    expect(searchSchoolsByName).toHaveBeenCalledTimes(1);
  });

  it('clearing the input removes q from the URL', async () => {
    await open('/schools?q=dakar');
    type('');
    jasmine.clock().tick(300);
    await settle();

    expect(router.url).toBe('/schools');
    expect(getSchools).toHaveBeenCalledTimes(1);
  });

  for (const hostile of ['<img src=x onerror=alert(1)>', 'a&b=c#d']) {
    it(`keeps a hostile ?q= as plain text: ${hostile}`, async () => {
      const component = await open('/schools?q=' + encodeURIComponent(hostile));

      expect(component.searchQuery()).toBe(hostile);
      expect(input().value).toBe(hostile);
      expect(searchSchoolsByName.calls.mostRecent().args[0]).toBe(hostile);
      expect(q('img[src="x"]')).toBeNull();
    });
  }

  it('shows "no results", not an error, for a term the path cannot carry', async () => {
    // The real service over the testing HTTP layer: the term must not reach the network.
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'schools', component: SchoolsPageComponent }]),
        provideTransloco(translocoOptions),
        SchoolService,
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use('en'));
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/schools?q=' + encodeURIComponent('a/b'), SchoolsPageComponent);
    await settle();

    expect(q('app-error-state')).toBeNull();
    expect(text()).toContain('No school matches');
    const http = TestBed.inject(HttpTestingController);
    http.expectNone((r) => r.url.includes('/schools/name'));
  });

  it('load more appends the next page and disappears on the last one', async () => {
    getSchools.and.callFake((n: number) =>
      of(n === 0 ? page([school(1, 'Alpha')], 0, false, 2) : page([school(2, 'Beta')], 1, true, 2)),
    );
    await open('/schools');
    expect(q('[data-testid="load-more"]')).not.toBeNull();

    (q('[data-testid="load-more"]') as HTMLButtonElement).click();
    await settle();

    expect(getSchools.calls.mostRecent().args.slice(0, 2)).toEqual([1, 12]);
    expect(text()).toContain('Alpha');
    expect(text()).toContain('Beta');
    expect(q('[data-testid="load-more"]')).toBeNull();
  });

  it('a failed load more keeps the cards and offers a retry that works', async () => {
    let fail = true;
    getSchools.and.callFake((n: number) =>
      n === 0
        ? of(page([school(1, 'Alpha')], 0, false, 2))
        : fail
          ? throwError(() => new Error('down'))
          : of(page([school(2, 'Beta')], 1, true, 2)),
    );
    await open('/schools');
    (q('[data-testid="load-more"]') as HTMLButtonElement).click();
    await settle();

    expect(q('[data-testid="load-more-error"]')).not.toBeNull();
    expect(q('app-error-state')).toBeNull();
    expect(text()).toContain('Alpha');

    fail = false;
    (q('[data-testid="load-more-error"] button') as HTMLButtonElement).click();
    await settle();

    expect(q('[data-testid="load-more-error"]')).toBeNull();
    expect(text()).toContain('Alpha');
    expect(text()).toContain('Beta');
  });

  it('a failed first page shows the error state and retry recovers', async () => {
    let fail = true;
    getSchools.and.callFake(() => (fail ? throwError(() => new Error('down')) : of(page([school(1, 'Alpha')]))));
    await open('/schools');
    expect(q('app-error-state')).not.toBeNull();

    fail = false;
    (q('app-error-state button') as HTMLButtonElement).click();
    await settle();

    expect(q('app-error-state')).toBeNull();
    expect(text()).toContain('Alpha');
  });

  it('a city alone is sent to /search, paged and sorted', async () => {
    const component = await open('/schools');
    component.selectedCity.set('Thiès');
    await settle();

    expect(searchSchools).toHaveBeenCalledWith('Thiès', undefined, undefined, undefined, 0, 12, 'name,asc');
  });

  it('a term wins over a city: the name endpoint is used', async () => {
    const component = await open('/schools?q=dakar');
    searchSchoolsByName.calls.reset();
    component.selectedCity.set('Thiès');
    await settle();

    expect(searchSchools).not.toHaveBeenCalled();
    expect(searchSchoolsByName).toHaveBeenCalledTimes(1);
  });

  it('changing the sort restarts at page 0 with the server sort', async () => {
    getSchools.and.callFake((n: number) => of(page([school(1, 'Alpha')], n, false, 30)));
    const component = await open('/schools');
    (q('[data-testid="load-more"]') as HTMLButtonElement).click();
    await settle();
    expect(getSchools.calls.mostRecent().args[0]).toBe(1);

    component.sortBy.set('city');
    await settle();

    expect(getSchools.calls.mostRecent().args.slice(0, 2)).toEqual([0, 12]);
    expect(getSchools.calls.mostRecent().args[3]).toBe('address.city,asc');
    expect(component.schools().length).toBe(1);
  });

  it('keeps every city seen as a choice after a city narrows the list', async () => {
    const component = await open('/schools');
    searchSchools.and.returnValue(of(page([school(2, 'Beta', 'Thiès')])));
    component.selectedCity.set('Thiès');
    component.showFilters.set(true);
    await settle();

    expect(component.cities()).toEqual(['Dakar', 'Thiès']);
  });

  it('shows the server total, not the loaded count', async () => {
    getSchools.and.returnValue(of(page([school(1, 'Alpha')], 0, false, 40)));
    await open('/schools');

    expect(text()).toContain('40 schools found');
  });

  it('filters by type on the pages already loaded', async () => {
    const component = await open('/schools');
    getSchools.calls.reset();
    component.selectedType.set('Private');
    await settle();

    expect(getSchools).not.toHaveBeenCalled();
    expect(component.sortedSchools()).toEqual([]);
  });

  it('says nothing is listed without a term, and "no results" with one', async () => {
    getSchools.and.returnValue(of(page([])));
    await open('/schools');
    expect(text()).toContain('No schools are listed yet');

    searchSchoolsByName.and.returnValue(of(page([])));
    await router.navigateByUrl('/schools?q=zzz');
    await settle();
    expect(text()).toContain('No school matches');
  });
});
