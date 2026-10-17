import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTransloco } from '@jsverse/transloco';
import { NEVER, Observable, Subject, firstValueFrom, of, throwError } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { SchoolService } from '@services/school.service';
import { SchoolsRankingPageComponent } from './schools-ranking-page.component';

type Fake = { id: number; name: string; rating?: number; reviewCount?: number };

/** A server page: only the fields the page reads. */
const page = (content: Fake[], number: number, last: boolean, totalElements: number) => ({
  content,
  number,
  last,
  totalElements,
});

const schools = (from: number, to: number): Fake[] =>
  Array.from({ length: to - from + 1 }, (_, i) => ({ id: from + i, name: `School ${from + i}`, rating: 5 - (from + i) / 100 }));

describe('SchoolsRankingPageComponent', () => {
  let harness: RouterTestingHarness;
  let getSchools: jasmine.Spy;

  const el = () => harness.routeNativeElement as HTMLElement;
  const text = () => el().textContent ?? '';
  const q = (sel: string) => el().querySelector(sel);
  const rows = () => Array.from(el().querySelectorAll('[data-testid="rank-row"]'));
  const names = () => rows().map((r) => r.querySelector('a')?.textContent?.trim());
  const ranks = () => rows().map((r) => r.querySelector('[data-testid="rank"]')?.textContent?.trim());
  const button = (testId: string) => q(`[data-testid="${testId}"]`) as HTMLButtonElement | null;

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  async function open(answer: (page: number, size: number, sortRate?: string) => Observable<unknown>, lang: 'en' | 'fr' = 'en') {
    getSchools = jasmine.createSpy('getSchools').and.callFake(answer);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'ranking', component: SchoolsRankingPageComponent }]),
        provideTransloco(translocoOptions),
        { provide: SchoolService, useValue: { getSchools } },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/ranking', SchoolsRankingPageComponent);
    await settle();
  }

  async function click(testId: string): Promise<void> {
    button(testId)!.click();
    await settle();
  }

  it('asks the server for the first ten, best rated first, and never sorts them itself', async () => {
    const unsorted = [
      { id: 7, name: 'Zed', rating: 3 },
      { id: 2, name: 'Alpha', rating: 4.9 },
      { id: 9, name: 'Mid' },
    ];
    await open(() => of(page(unsorted, 0, true, 3)));

    expect(getSchools).toHaveBeenCalledOnceWith(0, 10, 'most');
    expect(names()).toEqual(['Zed', 'Alpha', 'Mid']);
    expect(ranks()).toEqual(['1', '2', '3']);
  });

  it('links each school to its page, as the catalogue does', async () => {
    await open(() => of(page([{ id: 7, name: 'Zed', rating: 3 }], 0, true, 1)), 'fr');

    expect(q('[data-testid="rank-row"] a')!.getAttribute('href')).toBe('/fr/schools/7');
  });

  it('shows the rounded average, "Not rated" without one, and never a zero for an unrated school', async () => {
    await open(() =>
      of(page([{ id: 1, name: 'A', rating: 4.26, reviewCount: 3 }, { id: 2, name: 'B' }, { id: 3, name: 'C', rating: 0 }], 0, true, 3)),
    );
    const [a, b, c] = rows();

    expect(a.textContent).toContain('4.3');
    expect(a.textContent).toContain('(3 reviews)');
    expect(b.textContent).toContain('Not rated');
    expect(b.textContent).not.toMatch(/\b0\b/);
    expect(c.textContent).not.toContain('Not rated');
  });

  it('counts the real total, not the page, with the language plural', async () => {
    await open(() => of(page(schools(1, 10), 0, false, 23)));
    expect(text()).toContain('23 schools ranked');
    expect(rows().length).toBe(10);

    TestBed.resetTestingModule();
    await open(() => of(page([{ id: 1, name: 'Solo', rating: 4 }], 0, true, 1)), 'fr');
    expect(text()).toContain('1 établissement classé');
    expect(text()).not.toContain('établissements classés');
  });

  it('offers neither button when everything fits in one page, even under ten results', async () => {
    await open(() => of(page(schools(1, 4), 0, true, 4)));

    expect(rows().length).toBe(4);
    expect(button('rank-more')).toBeNull();
    expect(button('rank-all')).toBeNull();
  });

  it('"See more" asks the next server page and appends it, ranks continuing', async () => {
    await open((p) => of(p === 0 ? page(schools(1, 10), 0, false, 14) : page(schools(11, 14), 1, true, 14)));

    await click('rank-more');

    expect(getSchools.calls.allArgs()).toEqual([[0, 10, 'most'], [1, 10, 'most']]);
    expect(rows().length).toBe(14);
    expect(ranks()[10]).toBe('11');
    expect(ranks()[13]).toBe('14');
    expect(button('rank-more')).toBeNull();
    expect(button('rank-all')).toBeNull();
  });

  it('drops a school the next page repeats because the ranking moved meanwhile, keeping the first rank', async () => {
    await open((p) => of(p === 0 ? page(schools(1, 10), 0, false, 12) : page([...schools(10, 10), ...schools(11, 12)], 1, true, 12)));

    await click('rank-more');

    expect(names()).toEqual(schools(1, 12).map((s) => s.name));
    expect(ranks()).toEqual(Array.from({ length: 12 }, (_, i) => String(i + 1)));
  });

  it('"See all" chains the server pages up to the last one, showing a busy state meanwhile', async () => {
    const pending = new Subject<unknown>();
    await open((p) => {
      if (p === 0) return of(page(schools(1, 10), 0, false, 25));
      if (p === 1) return of(page(schools(11, 20), 1, false, 25));
      return pending;
    });

    await click('rank-all');

    expect(rows().length).toBe(20);
    expect(q('[aria-busy="true"]')).not.toBeNull();
    expect(button('rank-all')!.disabled).toBeTrue();
    expect(button('rank-more')!.disabled).toBeTrue();

    pending.next(page(schools(21, 25), 2, true, 25));
    pending.complete();
    await settle();

    expect(getSchools.calls.allArgs().map((a) => a[0])).toEqual([0, 1, 2]);
    expect(rows().length).toBe(25);
    expect(q('[aria-busy="true"]')).toBeNull();
    expect(button('rank-all')).toBeNull();
  });

  it('keeps what "See all" already loaded when a page fails, then retry resumes from the failed page', async () => {
    let fail = true;
    await open((p) => {
      if (p === 0) return of(page(schools(1, 10), 0, false, 25));
      if (p === 1) return of(page(schools(11, 20), 1, false, 25));
      return fail ? throwError(() => new Error('x')) : of(page(schools(21, 25), 2, true, 25));
    });

    await click('rank-all');

    expect(rows().length).toBe(20);
    expect(q('[data-testid="rank-more-error"]')).not.toBeNull();
    expect(q('[aria-busy="true"]')).toBeNull();

    fail = false;
    await click('rank-retry');

    expect(getSchools.calls.allArgs().map((a) => a[0])).toEqual([0, 1, 2, 2]);
    expect(rows().length).toBe(25);
    expect(q('[data-testid="rank-more-error"]')).toBeNull();
  });

  it('keeps the loaded ranking when "See more" fails, and retries that page', async () => {
    let fail = true;
    await open((p) => (p === 0 ? of(page(schools(1, 10), 0, false, 14)) : fail ? throwError(() => new Error('x')) : of(page(schools(11, 14), 1, true, 14))));

    await click('rank-more');
    expect(rows().length).toBe(10);
    expect(q('[data-testid="rank-more-error"]')).not.toBeNull();

    fail = false;
    await click('rank-retry');
    expect(rows().length).toBe(14);
  });

  it('shows a loading state, then nothing but the empty message for an empty ranking', async () => {
    await open(() => NEVER);
    expect(q('[aria-busy="true"]')).not.toBeNull();
    expect(rows().length).toBe(0);

    TestBed.resetTestingModule();
    await open(() => of(page([], 0, true, 0)));
    expect(q('[data-testid="rank-empty"]')).not.toBeNull();
    expect(q('[aria-busy="true"]')).toBeNull();
    expect(button('rank-more')).toBeNull();
  });

  it('shows the shared error state when the first page fails, and retry reloads it', async () => {
    let fail = true;
    await open(() => (fail ? throwError(() => new Error('x')) : of(page(schools(1, 2), 0, true, 2))));

    expect(q('app-error-state')).not.toBeNull();
    expect(rows().length).toBe(0);
    expect(q('[data-testid="rank-empty"]')).toBeNull();

    fail = false;
    (q('app-error-state button') as HTMLButtonElement).click();
    await settle();

    expect(q('app-error-state')).toBeNull();
    expect(rows().length).toBe(2);
  });

  it('speaks French', async () => {
    await open(() => of(page(schools(1, 10), 0, false, 12)), 'fr');

    expect(text()).toContain('Classement des établissements');
    expect(button('rank-more')!.textContent).toContain('Voir plus');
    expect(button('rank-all')!.textContent).toContain('Voir tout');
  });
});
