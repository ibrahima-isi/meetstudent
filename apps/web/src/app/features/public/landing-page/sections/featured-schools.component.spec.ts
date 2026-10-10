import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom, NEVER, Observable, of, throwError } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { School } from '@models/entities';
import { LocaleService } from '@services/locale.service';
import { SchoolService } from '@services/school.service';
import { FeaturedSchoolsComponent } from './featured-schools.component';

describe('FeaturedSchoolsComponent', () => {
  let fixture: ComponentFixture<FeaturedSchoolsComponent>;
  let getSchools: jasmine.Spy;

  const root = () => fixture.nativeElement as HTMLElement;
  const cards = () => Array.from(root().querySelectorAll<HTMLAnchorElement>('a[href^="/fr/schools/"]'));
  const school = (id: number | undefined, extra: Partial<School> = {}): School =>
    ({ id, name: `School ${id}`, address: { city: 'Dakar', location: '', country: '' }, ...extra }) as School;
  const page = (content: School[]) => of({ content, totalElements: content.length });

  async function create(answer: Observable<unknown>) {
    getSchools.and.returnValue(answer);
    fixture = TestBed.createComponent(FeaturedSchoolsComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    getSchools = jasmine.createSpy('getSchools');
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: LocaleService, useValue: { active: signal('fr') } },
        { provide: SchoolService, useValue: { getSchools } },
      ],
    });
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));
  });

  it('requests the first six schools', async () => {
    await create(page([school(1)]));
    expect(getSchools).toHaveBeenCalledWith(0, 6);
  });

  it('renders each school as a real link to its detail page', async () => {
    await create(page([school(1), school(22)]));
    expect(cards().map((a) => a.getAttribute('href'))).toEqual(['/fr/schools/1', '/fr/schools/22']);
  });

  it('shows name, city and a rounded rating', async () => {
    await create(page([school(1, { name: 'Harvard', rating: 4.26 })]));
    const card = cards()[0];
    expect(card.textContent).toContain('Harvard');
    expect(card.textContent).toContain('Dakar');
    expect(card.querySelector('[data-testid="rating"]')?.textContent?.trim()).toBe('4.3');
    expect(card.querySelector('[data-testid="city"]')?.textContent?.trim()).toBe('Dakar');
  });

  it('skips a school without an id', async () => {
    await create(page([school(undefined, { name: 'Ghost' }), school(2)]));
    expect(cards().length).toBe(1);
    expect(root().textContent).not.toContain('Ghost');
  });

  it('renders a bare school without rating, city or cover, with no broken-image picture', async () => {
    await create(page([school(3, { address: undefined as never })]));
    const card = cards()[0];
    expect(card.textContent).toContain('School 3');
    expect(card.textContent).not.toContain('0.0');
    expect(card.textContent).not.toContain('NaN');
    expect(card.querySelector('[data-testid="rating"]')).toBeNull();
    expect(card.querySelector('[data-testid="city"]')).toBeNull();
    expect(card.querySelector('img')).toBeNull();
    expect(card.querySelector('[data-testid="cover-placeholder"]')).not.toBeNull();
  });

  it('renders an empty address without a city or a rating of zero', async () => {
    await create(page([school(4, { address: {} as never, rating: 0 })]));
    const card = cards()[0];
    expect(card.textContent).not.toContain('0.0');
    expect(card.querySelector('[data-testid="rating"]')).toBeNull();
    expect(card.querySelector('[data-testid="city"]')).toBeNull();
  });

  it('ignores a non-finite rating', async () => {
    await create(page([school(5, { rating: NaN })]));
    expect(cards()[0].textContent).not.toContain('NaN');
    expect(cards()[0].querySelector('[data-testid="rating"]')).toBeNull();
  });

  it('uses the cover image when the school has one', async () => {
    await create(page([school(6, { coverImageUrl: '/uploads/public/c.png' })]));
    const img = cards()[0].querySelector('img');
    expect(img?.getAttribute('src')).toBe('/uploads/public/c.png');
    expect(cards()[0].querySelector('[data-testid="cover-placeholder"]')).toBeNull();
  });

  it('shows the placeholder, not an image, for an empty cover url', async () => {
    await create(page([school(7, { coverImageUrl: '' })]));
    expect(cards()[0].querySelector('img')).toBeNull();
    expect(cards()[0].querySelector('[data-testid="cover-placeholder"]')).not.toBeNull();
  });

  it('shows three aria-hidden skeleton cards while loading', async () => {
    await create(NEVER);
    const skeletons = root().querySelectorAll('[data-testid="skeleton-card"]');
    expect(skeletons.length).toBe(3);
    expect(Array.from(skeletons).every((s) => s.getAttribute('aria-hidden') === 'true')).toBeTrue();
    expect(cards().length).toBe(0);
  });

  it('shows the error state with its message, and retry loads the cards', async () => {
    await create(throwError(() => new Error('x')));
    expect(root().querySelector('app-error-state')).not.toBeNull();
    expect(root().textContent).toContain("Les établissements n'ont pas pu être chargés.");

    getSchools.and.returnValue(page([school(9)]));
    root().querySelector<HTMLButtonElement>('app-error-state button')!.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(getSchools).toHaveBeenCalledTimes(2);
    expect(root().querySelector('app-error-state')).toBeNull();
    expect(cards().length).toBe(1);
  });

  it('renders nothing at all, heading included, for an empty list', async () => {
    await create(page([]));
    expect(root().querySelector('section')).toBeNull();
    expect(root().querySelector('h2')).toBeNull();
  });

  it('links "see all" to the catalogue as a secondary button', async () => {
    await create(page([school(1)]));
    const all = root().querySelector<HTMLAnchorElement>('a.btn.btn-secondary');
    expect(all?.getAttribute('href')).toBe('/fr/schools');
    expect(all?.textContent).toContain('Voir tous les établissements');
  });

  it('puts the reveal on the card link, not on an inner wrapper', async () => {
    await create(page([school(1)]));
    expect(cards()[0].closest('li')?.classList.contains('reveal') || cards()[0].classList.contains('reveal')).toBeTrue();
  });
});
