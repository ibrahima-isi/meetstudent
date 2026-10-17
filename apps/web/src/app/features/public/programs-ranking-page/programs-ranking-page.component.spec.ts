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
import { ProgramService } from '@services/program.service';
import { ProgramsRankingPageComponent } from './programs-ranking-page.component';

type Fake = { id: number; name: string; rating?: number; schoolName?: string | null; duration?: number | null; code?: string };

/** A server page: only the fields the page reads. */
const page = (content: Fake[], number: number, last: boolean, totalElements: number) => ({
  content,
  number,
  last,
  totalElements,
});

const programs = (from: number, to: number): Fake[] =>
  Array.from({ length: to - from + 1 }, (_, i) => ({ id: from + i, name: `Program ${from + i}`, rating: 5 - (from + i) / 100 }));

describe('ProgramsRankingPageComponent', () => {
  let harness: RouterTestingHarness;
  let getPrograms: jasmine.Spy;

  const el = () => harness.routeNativeElement as HTMLElement;
  const text = () => el().textContent ?? '';
  const q = (sel: string) => el().querySelector(sel);
  const rows = () => Array.from(el().querySelectorAll('[data-testid="rank-row"]'));
  const names = () => rows().map((r) => r.querySelector('[data-testid="rank-name"]')?.textContent?.trim());
  const ranks = () => rows().map((r) => r.querySelector('[data-testid="rank"]')?.textContent?.trim());
  const button = (testId: string) => q(`[data-testid="${testId}"]`) as HTMLButtonElement | null;

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  async function open(answer: (page: number, size: number, sortRate?: string) => Observable<unknown>, lang: 'en' | 'fr' = 'en') {
    getPrograms = jasmine.createSpy('getPrograms').and.callFake(answer);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'ranking', component: ProgramsRankingPageComponent }]),
        provideTransloco(translocoOptions),
        { provide: ProgramService, useValue: { getPrograms } },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/ranking', ProgramsRankingPageComponent);
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

    expect(getPrograms).toHaveBeenCalledOnceWith(0, 10, 'most');
    expect(names()).toEqual(['Zed', 'Alpha', 'Mid']);
    expect(ranks()).toEqual(['1', '2', '3']);
  });

  it('shows the program name as plain text, with no link, since no public program page exists', async () => {
    await open(() => of(page([{ id: 7, name: 'Zed', rating: 3 }], 0, true, 1)), 'fr');

    expect(rows()[0].querySelector('a')).toBeNull();
    expect(q('[data-testid="rank-name"]')!.textContent?.trim()).toBe('Zed');
  });

  it('shows the school as plain text, the duration and the code only when provided, "Not provided" otherwise', async () => {
    await open(() =>
      of(
        page(
          [
            { id: 1, name: 'A', rating: 4, schoolName: 'Uni One', duration: 3, code: 'LIC' },
            { id: 2, name: 'B', rating: 3, schoolName: null, duration: null },
            { id: 3, name: 'C', schoolName: 'Uni Two', duration: 1 },
          ],
          0,
          true,
          3,
        ),
      ),
    );
    const [a, b, c] = rows();

    expect(a.textContent).toContain('Uni One');
    expect(a.textContent).toContain('3 years');
    expect(a.textContent).toContain('LIC');
    expect(a.textContent).not.toContain('Not provided');
    expect(b.textContent?.match(/Not provided/g)?.length).toBe(2);
    expect(b.textContent).not.toMatch(/Code/);
    expect(c.textContent).toContain('1 year');
    expect(c.textContent).not.toContain('1 years');
    expect(a.querySelector('a')).toBeNull();
  });

  it('shows the rounded average, "Not rated" without one, and never a zero for an unrated school', async () => {
    await open(() =>
      of(page([{ id: 1, name: 'A', rating: 4.26 }, { id: 2, name: 'B' }, { id: 3, name: 'C', rating: 0 }], 0, true, 3)),
    );
    const [a, b, c] = rows();

    expect(a.textContent).toContain('4.3');
    expect(a.textContent).not.toMatch(/reviews?\)/);
    expect(b.textContent).toContain('Not rated');
    expect(b.textContent).not.toMatch(/\b0\b/);
    expect(c.textContent).not.toContain('Not rated');
  });

  it('counts the real total, not the page, with the language plural', async () => {
    await open(() => of(page(programs(1, 10), 0, false, 23)));
    expect(text()).toContain('23 programmes ranked');
    expect(rows().length).toBe(10);

    TestBed.resetTestingModule();
    await open(() => of(page([{ id: 1, name: 'Solo', rating: 4 }], 0, true, 1)), 'fr');
    expect(text()).toContain('1 formation classée');
    expect(text()).not.toContain('formations classées');
  });

  it('offers neither button when everything fits in one page, even under ten results', async () => {
    await open(() => of(page(programs(1, 4), 0, true, 4)));

    expect(rows().length).toBe(4);
    expect(button('rank-more')).toBeNull();
    expect(button('rank-all')).toBeNull();
  });

  it('"See more" asks the next server page and appends it, ranks continuing', async () => {
    await open((p) => of(p === 0 ? page(programs(1, 10), 0, false, 14) : page(programs(11, 14), 1, true, 14)));

    await click('rank-more');

    expect(getPrograms.calls.allArgs()).toEqual([[0, 10, 'most'], [1, 10, 'most']]);
    expect(rows().length).toBe(14);
    expect(ranks()[10]).toBe('11');
    expect(ranks()[13]).toBe('14');
    expect(button('rank-more')).toBeNull();
    expect(button('rank-all')).toBeNull();
  });

  it('drops a program the next page repeats because the ranking moved meanwhile, keeping the first rank', async () => {
    await open((p) => of(p === 0 ? page(programs(1, 10), 0, false, 12) : page([...programs(10, 10), ...programs(11, 12)], 1, true, 12)));

    await click('rank-more');

    expect(names()).toEqual(programs(1, 12).map((s) => s.name));
    expect(ranks()).toEqual(Array.from({ length: 12 }, (_, i) => String(i + 1)));
  });

  it('"See all" chains the server pages up to the last one, showing a busy state meanwhile', async () => {
    const pending = new Subject<unknown>();
    await open((p) => {
      if (p === 0) return of(page(programs(1, 10), 0, false, 25));
      if (p === 1) return of(page(programs(11, 20), 1, false, 25));
      return pending;
    });

    await click('rank-all');

    expect(rows().length).toBe(20);
    expect(q('[data-testid="rank-busy"]')!.textContent).toContain('Loading');
    expect(button('rank-all')!.disabled).toBeTrue();
    expect(button('rank-more')!.disabled).toBeTrue();

    pending.next(page(programs(21, 25), 2, true, 25));
    pending.complete();
    await settle();

    expect(getPrograms.calls.allArgs().map((a) => a[0])).toEqual([0, 1, 2]);
    expect(rows().length).toBe(25);
    expect(q('[data-testid="rank-busy"]')!.textContent?.trim()).toBe('');
    expect(button('rank-all')).toBeNull();
  });

  it('keeps what "See all" already loaded when a page fails, then retry resumes from the failed page', async () => {
    let fail = true;
    await open((p) => {
      if (p === 0) return of(page(programs(1, 10), 0, false, 25));
      if (p === 1) return of(page(programs(11, 20), 1, false, 25));
      return fail ? throwError(() => new Error('x')) : of(page(programs(21, 25), 2, true, 25));
    });

    await click('rank-all');

    expect(rows().length).toBe(20);
    expect(q('[data-testid="rank-more-error"]')).not.toBeNull();
    expect(q('[data-testid="rank-busy"]')!.textContent?.trim()).toBe('');

    fail = false;
    await click('rank-retry');

    expect(getPrograms.calls.allArgs().map((a) => a[0])).toEqual([0, 1, 2, 2]);
    expect(rows().length).toBe(25);
    expect(q('[data-testid="rank-more-error"]')).toBeNull();
  });

  it('keeps the loaded ranking when "See more" fails, and retries that page', async () => {
    let fail = true;
    await open((p) => (p === 0 ? of(page(programs(1, 10), 0, false, 14)) : fail ? throwError(() => new Error('x')) : of(page(programs(11, 14), 1, true, 14))));

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
    await open(() => (fail ? throwError(() => new Error('x')) : of(page(programs(1, 2), 0, true, 2))));

    expect(q('app-error-state')).not.toBeNull();
    expect(rows().length).toBe(0);
    expect(q('[data-testid="rank-empty"]')).toBeNull();

    fail = false;
    (q('app-error-state button') as HTMLButtonElement).click();
    await settle();

    expect(q('app-error-state')).toBeNull();
    expect(rows().length).toBe(2);
  });

  it('names the rank and the average as hidden text, without hiding the value behind an aria-label', async () => {
    await open(() => of(page([{ id: 1, name: 'A', rating: 4.26 }], 0, true, 1)));
    const row = rows()[0];

    expect(row.querySelector('[aria-label]')).toBeNull();
    const hidden = Array.from(row.querySelectorAll('.sr-only')).map((s) => s.textContent?.trim());
    expect(hidden).toContain('Rank 1');
    expect(hidden).toContain('Average rating');
    expect(row.textContent).toContain('4.3');
  });

  it('announces the continuation through a status region that is always there, never marked busy', async () => {
    const pending = new Subject<unknown>();
    await open((p) => (p === 0 ? of(page(programs(1, 10), 0, false, 14)) : pending));
    const region = () => q('[data-testid="rank-busy"]');

    expect(region()?.getAttribute('role')).toBe('status');
    expect(region()?.textContent?.trim()).toBe('');

    await click('rank-more');
    expect(region()?.textContent).toContain('Loading');
    expect(region()?.hasAttribute('aria-busy')).toBeFalse();

    pending.next(page(programs(11, 14), 1, true, 14));
    pending.complete();
    await settle();
    expect(region()?.textContent?.trim()).toBe('');
  });

  it('moves the focus to the first program "See more" added, so the keyboard does not fall back to the page top', async () => {
    await open((p) => of(page(programs(p * 10 + 1, p * 10 + 10), p, false, 40)));

    button('rank-more')!.focus();
    await click('rank-more');

    expect(document.activeElement).toBe(rows()[10].querySelector('[data-testid="rank-name"]'));
  });

  it('moves the focus to the first program "See all" added once it is done', async () => {
    await open((p) => of(page(programs(p * 10 + 1, p * 10 + 10), p, p === 2, 30)));

    button('rank-all')!.focus();
    await click('rank-all');

    expect(rows().length).toBe(30);
    expect(document.activeElement).toBe(rows()[10].querySelector('[data-testid="rank-name"]'));
  });

  it('moves the focus to the retry button when a continuation fails', async () => {
    await open((p) => (p === 0 ? of(page(programs(1, 10), 0, false, 14)) : throwError(() => new Error('x'))));

    button('rank-more')!.focus();
    await click('rank-more');

    expect(document.activeElement).toBe(button('rank-retry'));
  });

  it('offers the switch between the two rankings as links, the programmes one being the current page', async () => {
    await open(() => of(page(programs(1, 2), 0, true, 2)));
    const links = Array.from(el().querySelectorAll('[data-testid="ranking-switch"] a'));

    expect(links.map((l) => l.textContent?.trim())).toEqual(['Schools', 'Programmes']);
    expect(links.map((l) => l.getAttribute('href'))).toEqual(['/en/rankings/schools', '/en/rankings/programs']);
    expect(links.map((l) => l.getAttribute('aria-current'))).toEqual([null, 'page']);
    expect(q('[data-testid="ranking-switch"] [role="tab"]')).toBeNull();
  });

  it('keeps the switch visible while loading and when the ranking fails', async () => {
    await open(() => NEVER);
    expect(q('[data-testid="ranking-switch"]')).not.toBeNull();

    TestBed.resetTestingModule();
    await open(() => throwError(() => new Error('x')));
    expect(q('[data-testid="ranking-switch"]')).not.toBeNull();
  });

  it('wraps the switch instead of overflowing at 375 px', async () => {
    await open(() => of(page(programs(1, 2), 0, true, 2)), 'fr');
    const host = el().querySelector('[data-testid="ranking-switch"]') as HTMLElement;
    const style = getComputedStyle(host);

    expect(style.flexWrap).toBe('wrap');
  });

  it('speaks French', async () => {
    await open(() => of(page(programs(1, 10), 0, false, 12)), 'fr');

    expect(text()).toContain('Classement des formations');
    expect(text()).toContain('12 formations classées');
    expect(Array.from(el().querySelectorAll('[data-testid="ranking-switch"] a')).map((l) => l.textContent?.trim())).toEqual(['Établissements', 'Formations']);
    expect(button('rank-more')!.textContent).toContain('Voir plus');
    expect(button('rank-all')!.textContent).toContain('Voir tout');
  });
});
