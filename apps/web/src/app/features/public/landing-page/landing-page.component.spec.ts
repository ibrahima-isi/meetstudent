import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { LandingPageComponent } from './landing-page.component';
import { SchoolService } from '@services/school.service';
import { firstValueFrom, Observable, of, throwError } from 'rxjs';
import { LocaleService } from '@services/locale.service';
import { School } from '@models/entities';
import { signal } from '@angular/core';
import { Subject } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { translocoOptions } from '@i18n/transloco.config';

describe('LandingPageComponent', () => {
  let component: LandingPageComponent;
  let fixture: ComponentFixture<LandingPageComponent>;
  let schoolServiceSpy: jasmine.SpyObj<SchoolService>;

  beforeEach(async () => {
    schoolServiceSpy = jasmine.createSpyObj('SchoolService', ['getSchools', 'schools']);
    schoolServiceSpy.getSchools.and.returnValue(of({ content: [] } as any));
    schoolServiceSpy.schools.and.returnValue([]);

    await TestBed.configureTestingModule({
      imports: [LandingPageComponent],
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

    fixture = TestBed.createComponent(LandingPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should filter schools by search query', () => {
    const mockSchools = [
      { id: 1, name: 'Harvard', description: 'Ivy League', address: { city: 'Cambridge' }, type: 'Univ' },
      { id: 2, name: 'MIT', description: 'Tech school', address: { city: 'Cambridge' }, type: 'Univ' }
    ];
    component.schools.set(mockSchools as any);
    
    component.searchQuery.set('Harvard');
    fixture.detectChanges();
    
    expect(component.sortedSchools().length).toBe(1);
    expect(component.sortedSchools()[0].name).toBe('Harvard');
  });

  it('should filter schools by city', () => {
    const mockSchools = [
      { id: 1, name: 'Harvard', address: { city: 'Cambridge' }, type: 'Univ' },
      { id: 2, name: 'Stanford', address: { city: 'Stanford' }, type: 'Univ' }
    ];
    component.schools.set(mockSchools as any);
    
    component.selectedCity.set('Stanford');
    fixture.detectChanges();
    
    expect(component.sortedSchools().length).toBe(1);
    expect(component.sortedSchools()[0].name).toBe('Stanford');
  });
});

describe('LandingPageComponent translations', () => {
  let fixture: ComponentFixture<LandingPageComponent>;

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  async function render(lang: 'fr' | 'en') {
    // The same call the locale guard makes: it moves the active locale and
    // loads its bundle, so *transloco has something to render.
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    fixture = TestBed.createComponent(LandingPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    const schoolService = jasmine.createSpyObj('SchoolService', ['getSchools', 'schools']);
    schoolService.getSchools.and.returnValue(of({ content: [] }));
    schoolService.schools.and.returnValue([]);

    TestBed.configureTestingModule({
      imports: [LandingPageComponent],
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

  it('sorts names with the collation of the active language', async () => {
    await render('en');
    const compare = spyOn(String.prototype, 'localeCompare').and.callThrough();
    fixture.componentInstance.schools.set([
      { id: 1, name: 'Zeta', address: { city: 'Dakar' } },
      { id: 2, name: 'Alpha', address: { city: 'Thiès' } },
    ] as School[]);

    fixture.componentInstance.sortedSchools();

    expect(compare).toHaveBeenCalledWith(jasmine.any(String), 'en');
    expect(compare).not.toHaveBeenCalledWith(jasmine.any(String), 'fr');
  });
});

describe('LandingPageComponent data states', () => {
  let fixture: ComponentFixture<LandingPageComponent>;
  let getSchools: jasmine.Spy;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const q = (sel: string) => (fixture.nativeElement as HTMLElement).querySelector(sel);

  async function render(response: Observable<unknown>) {
    getSchools = jasmine.createSpy('getSchools').and.returnValue(response);
    TestBed.configureTestingModule({
      imports: [LandingPageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: SchoolService, useValue: { getSchools, schools: () => [] } },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use('en'));
    fixture = TestBed.createComponent(LandingPageComponent);
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

