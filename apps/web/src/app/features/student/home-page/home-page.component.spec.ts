import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom, of } from 'rxjs';
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
