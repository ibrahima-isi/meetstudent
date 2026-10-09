import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom, Observable, of, Subject, throwError } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { SchoolService } from '@services/school.service';
import { TokenService } from '@services/token.service';
import { WishlistService } from '@services/wishlist.service';
import { HomePageComponent } from './home-page.component';

describe('HomePageComponent translations', () => {
  let fixture: ComponentFixture<HomePageComponent>;

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  async function render(lang: 'fr' | 'en') {
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    fixture = TestBed.createComponent(HomePageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    const schoolService = jasmine.createSpyObj('SchoolService', ['getSchools', 'schools']);
    schoolService.getSchools.and.returnValue(of({ content: [] }));
    schoolService.schools.and.returnValue([]);

    TestBed.configureTestingModule({
      imports: [HomePageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: SchoolService, useValue: schoolService },
        {
          provide: TokenService,
          useValue: { isAuthenticated: signal(true), user: signal(null), clear: () => {} },
        },
      ],
    });
  });

  it('renders in French, the source language', async () => {
    await render('fr');

    expect(text()).toContain('Déconnexion');
    expect(text()).toContain('Souhaits');
    expect(text()).toContain('0 établissement trouvé');
  });

  it('renders in English when English is active', async () => {
    await render('en');

    expect(text()).toContain('Log out');
    expect(text()).toContain('Wishlist');
    expect(text()).toContain('0 schools found');
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
});


describe('HomePageComponent data states', () => {
  let fixture: ComponentFixture<HomePageComponent>;
  let getSchools: jasmine.Spy;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const q = (sel: string) => (fixture.nativeElement as HTMLElement).querySelector(sel);

  async function render(response: Observable<unknown>) {
    getSchools = jasmine.createSpy('getSchools').and.returnValue(response);
    TestBed.configureTestingModule({
      imports: [HomePageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: SchoolService, useValue: { getSchools, schools: () => [] } },
        { provide: TokenService, useValue: { isAuthenticated: signal(true), user: signal(null), clear: () => {} } },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use('en'));
    fixture = TestBed.createComponent(HomePageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  }

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


describe('HomePageComponent wishlist', () => {
  let fixture: ComponentFixture<HomePageComponent>;
  const school = { id: 4, name: 'Real School', address: { location: 'L', city: 'Dakar', country: 'SN' } };
  let saved: ReturnType<typeof signal<unknown[]>>;
  let error: ReturnType<typeof signal<boolean>>;
  let toggle: jasmine.Spy;
  const q = (sel: string) => (fixture.nativeElement as HTMLElement).querySelector(sel);

  beforeEach(async () => {
    saved = signal<unknown[]>([]);
    error = signal(false);
    toggle = jasmine.createSpy('toggle');
    TestBed.configureTestingModule({
      imports: [HomePageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: SchoolService, useValue: { getSchools: () => of({ content: [school] }), schools: () => [] } },
        { provide: TokenService, useValue: { isAuthenticated: signal(true), user: signal(null), clear: () => {} } },
        {
          provide: WishlistService,
          useValue: {
            schools: saved,
            error,
            status: signal('loaded'),
            load: () => {},
            toggle,
            has: (id: number) => (saved() as { id: number }[]).some((s) => s.id === id),
            isPending: () => false,
            dismissError: () => {},
          },
        },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use('en'));
    fixture = TestBed.createComponent(HomePageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('puts an unpressed add-to-wishlist button on each card and toggles on click', () => {
    const button = q('[data-testid="wishlist-toggle"]') as HTMLButtonElement;

    expect(button.getAttribute('aria-pressed')).toBe('false');
    expect(button.getAttribute('aria-label')).toBe('Add to wishlist');
    button.click();

    expect(toggle).toHaveBeenCalledWith(jasmine.objectContaining({ id: 4 }));
  });

  it('shows the pressed, remove state for a wishlisted school', async () => {
    saved.set([school]);
    fixture.detectChanges();
    await fixture.whenStable();
    const button = q('[data-testid="wishlist-toggle"]') as HTMLButtonElement;

    expect(button.getAttribute('aria-pressed')).toBe('true');
    expect(button.getAttribute('aria-label')).toBe('Remove from wishlist');
  });

  it('does not open the school when the heart is clicked', () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigate');
    (q('[data-testid="wishlist-toggle"]') as HTMLButtonElement).click();

    expect(navigate).not.toHaveBeenCalled();
  });

  it('announces a failed update', async () => {
    error.set(true);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(q('[data-testid="wishlist-error"]')?.textContent).toContain('could not be updated');
  });
});

describe('HomePageComponent API search', () => {
  let fixture: ComponentFixture<HomePageComponent>;
  let getSchools: jasmine.Spy;
  let searchSchools: jasmine.Spy;
  let searchSchoolsByName: jasmine.Spy;

  const school = (id: number, name: string, city = 'Dakar') => ({ id, name, address: { city, location: 'L' } });
  const page = (content: unknown[], number = 0, last = true, totalElements = content.length) => ({
    content,
    number,
    last,
    totalElements,
  });
  const el = () => fixture.nativeElement as HTMLElement;
  const text = () => el().textContent ?? '';

  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function type(value: string): void {
    const input = el().querySelector('input[type="text"]') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function pick(selector: string, value: string): void {
    const select = el().querySelector(selector) as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate();
  });

  afterEach(() => jasmine.clock().uninstall());

  async function create(): Promise<void> {
    getSchools = jasmine.createSpy('getSchools').and.returnValue(of(page([school(1, 'Alpha'), school(2, 'Beta', 'Thiès')])));
    searchSchools = jasmine.createSpy('searchSchools').and.returnValue(of(page([])));
    searchSchoolsByName = jasmine.createSpy('searchSchoolsByName').and.returnValue(of(page([])));
    TestBed.configureTestingModule({
      imports: [HomePageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: SchoolService, useValue: { getSchools, searchSchools, searchSchoolsByName, schools: () => [] } },
        { provide: TokenService, useValue: { isAuthenticated: signal(true), user: signal(null), clear: () => {} } },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use('en'));
    fixture = TestBed.createComponent(HomePageComponent);
    await settle();
  }

  it('loads the first page from the API with a page size, not 50', async () => {
    await create();

    expect(getSchools).toHaveBeenCalledTimes(1);
    expect(getSchools.calls.mostRecent().args[0]).toBe(0);
    expect(getSchools.calls.mostRecent().args[1]).toBeLessThanOrEqual(24);
  });

  it('typing triggers exactly one debounced API search with the term', async () => {
    await create();

    type('a');
    type('al');
    type('alp');
    jasmine.clock().tick(200);
    expect(searchSchoolsByName).not.toHaveBeenCalled();
    jasmine.clock().tick(150);
    await settle();

    expect(searchSchoolsByName).toHaveBeenCalledTimes(1);
    expect(searchSchoolsByName.calls.mostRecent().args[0]).toBe('alp');
    expect(searchSchoolsByName.calls.mostRecent().args[1]).toBe(0);
  });

  it('clearing the term goes back to the plain listing', async () => {
    await create();
    type('alp');
    jasmine.clock().tick(400);
    await settle();
    getSchools.calls.reset();

    type('');
    jasmine.clock().tick(400);
    await settle();

    expect(getSchools).toHaveBeenCalledTimes(1);
  });

  it('a city filter is sent to /search as a server-side param, without debounce', async () => {
    await create();
    el().querySelector<HTMLButtonElement>('[data-testid="filters-toggle"]')!.click();
    fixture.detectChanges();

    pick('[data-testid="city-filter"]', 'Thiès');
    await settle();

    expect(searchSchools).toHaveBeenCalledTimes(1);
    expect(searchSchools.calls.mostRecent().args[0]).toBe('Thiès');
    expect(searchSchools.calls.mostRecent().args[4]).toBe(0);
  });

  it('keeps every city choice available after a city filter narrows the results', async () => {
    await create();
    el().querySelector<HTMLButtonElement>('[data-testid="filters-toggle"]')!.click();
    fixture.detectChanges();
    searchSchools.and.returnValue(of(page([school(2, 'Beta', 'Thiès')])));

    pick('[data-testid="city-filter"]', 'Thiès');
    await settle();

    const options = Array.from(el().querySelectorAll('[data-testid="city-filter"] option')).map((o) => o.textContent?.trim());
    expect(options).toContain('Dakar');
  });

  it('offers no sort by available places, since no capacity data exists', async () => {
    await create();

    const options = Array.from(el().querySelectorAll<HTMLOptionElement>('[data-testid="sort-select"] option'));
    expect(options.map((o) => o.value)).toEqual(['name', 'city']);
  });

  it('sorting by city is sent to the API as a sort param', async () => {
    await create();

    pick('[data-testid="sort-select"]', 'city');
    await settle();

    const args = getSchools.calls.mostRecent().args;
    expect(args[3]).toBe('address.city,asc');
  });

  it('combines the term with the city: name search server-side, city on the loaded page', async () => {
    await create();
    el().querySelector<HTMLButtonElement>('[data-testid="filters-toggle"]')!.click();
    fixture.detectChanges();
    searchSchoolsByName.and.returnValue(of(page([school(1, 'Alpha', 'Dakar'), school(2, 'Alpine', 'Thiès')])));
    type('alp');
    jasmine.clock().tick(400);
    await settle();

    pick('[data-testid="city-filter"]', 'Thiès');
    await settle();

    expect(searchSchoolsByName).toHaveBeenCalled();
    expect(text()).toContain('Alpine');
    expect(text()).not.toContain('Alpha');
  });

  it('load more appends the next page and hides the button on the last page', async () => {
    await create();
    getSchools.and.returnValue(of(page([school(1, 'Alpha')], 0, false, 2)));
    pick('[data-testid="sort-select"]', 'city');
    await settle();
    getSchools.and.returnValue(of(page([school(2, 'Beta')], 1, true, 2)));

    el().querySelector<HTMLButtonElement>('[data-testid="load-more"]')!.click();
    await settle();

    expect(getSchools.calls.mostRecent().args[0]).toBe(1);
    expect(text()).toContain('Alpha');
    expect(text()).toContain('Beta');
    expect(el().querySelector('[data-testid="load-more"]')).toBeNull();
  });

  it('ignores an out-of-order response from a superseded search', async () => {
    await create();
    const first = new Subject<unknown>();
    const second = new Subject<unknown>();
    searchSchoolsByName.and.returnValues(first, second);

    type('al');
    jasmine.clock().tick(400);
    await settle();
    type('alp');
    jasmine.clock().tick(400);
    await settle();
    second.next(page([school(2, 'Alpine')]));
    second.complete();
    await settle();
    first.next(page([school(1, 'Stale')]));
    first.complete();
    await settle();

    expect(text()).toContain('Alpine');
    expect(text()).not.toContain('Stale');
  });

  it('drops a pending load-more when the criteria change', async () => {
    await create();
    getSchools.and.returnValue(of(page([school(1, 'Alpha')], 0, false, 5)));
    pick('[data-testid="sort-select"]', 'city');
    await settle();
    const more = new Subject<unknown>();
    getSchools.and.returnValue(more);
    el().querySelector<HTMLButtonElement>('[data-testid="load-more"]')!.click();
    await settle();

    searchSchoolsByName.and.returnValue(of(page([school(9, 'Fresh')])));
    type('fre');
    jasmine.clock().tick(400);
    await settle();
    more.next(page([school(3, 'Stale')], 1));
    await settle();

    expect(text()).toContain('Fresh');
    expect(text()).not.toContain('Stale');
  });

  it('shows the error state with retry when a search fails, then recovers', async () => {
    await create();
    searchSchoolsByName.and.returnValue(throwError(() => new Error('down')));
    type('alp');
    jasmine.clock().tick(400);
    await settle();

    expect(el().querySelector('app-error-state')).not.toBeNull();

    searchSchoolsByName.and.returnValue(of(page([school(1, 'Alpha')])));
    el().querySelector<HTMLButtonElement>('app-error-state button')!.click();
    await settle();

    expect(el().querySelector('app-error-state')).toBeNull();
    expect(text()).toContain('Alpha');
    expect(searchSchoolsByName.calls.mostRecent().args[0]).toBe('alp');
  });

  it('keeps the loaded schools and offers a retry when load more fails', async () => {
    await create();
    getSchools.and.returnValue(of(page([school(1, 'Alpha')], 0, false, 2)));
    pick('[data-testid="sort-select"]', 'city');
    await settle();
    getSchools.and.returnValue(throwError(() => new Error('down')));

    el().querySelector<HTMLButtonElement>('[data-testid="load-more"]')!.click();
    await settle();

    expect(text()).toContain('Alpha');
    expect(el().querySelector('[data-testid="load-more-error"]')).not.toBeNull();
    expect(el().querySelector('app-error-state')).toBeNull();
  });

  it('shows the no-results message when a search matches nothing', async () => {
    await create();
    type('zzz');
    jasmine.clock().tick(400);
    await settle();

    expect(text()).toContain('No school matches these criteria');
  });
});
