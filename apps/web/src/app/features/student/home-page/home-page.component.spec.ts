import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom, Observable, of, Subject, throwError } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { SchoolService } from '@services/school.service';
import { TokenService } from '@services/token.service';
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
