import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom, of, Subject, throwError } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { ProgramService } from '@services/program.service';
import { ProgramsPageComponent } from './programs-page.component';

describe('ProgramsPageComponent', () => {
  let harness: RouterTestingHarness;
  let router: Router;
  let getPrograms: jasmine.Spy;
  let searchProgramsByName: jasmine.Spy;

  const program = (id: number, name: string, extra: Record<string, unknown> = {}) => ({ id, name, ...extra });
  const page = (content: unknown[], number = 0, last = true, totalElements = content.length) => ({
    content,
    number,
    last,
    totalElements,
  });
  const full = program(1, 'Computer Science', {
    code: 'CS',
    duration: 3,
    rating: 4.26,
    schoolId: 7,
    schoolName: 'ISI Dakar',
    photo: { publicUrl: '/uploads/public/cs.png' },
  });
  const bare = program(2, 'Bare Program');

  const el = () => harness.routeNativeElement as HTMLElement;
  const text = () => el().textContent ?? '';
  const q = (sel: string) => el().querySelector(sel);
  const input = () => q('input[type="text"]') as HTMLInputElement;

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  async function open(url: string, lang: 'fr' | 'en' = 'en') {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'programs', component: ProgramsPageComponent }]),
        provideTransloco(translocoOptions),
        { provide: ProgramService, useValue: { getPrograms, searchProgramsByName } },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl(url, ProgramsPageComponent);
    await settle();
    return component;
  }

  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate();
    getPrograms = jasmine.createSpy('getPrograms').and.returnValue(of(page([full, bare])));
    searchProgramsByName = jasmine
      .createSpy('searchProgramsByName')
      .and.returnValue(of(page([program(3, 'Data Science')])));
  });

  afterEach(() => jasmine.clock().uninstall());

  it('shows a loading state while the first page is pending', async () => {
    getPrograms.and.returnValue(new Subject<never>());
    await open('/programs');

    expect(q('[aria-busy="true"]')).not.toBeNull();
  });

  it('lists the first page of 12 without searching by name', async () => {
    await open('/programs');

    expect(getPrograms.calls.mostRecent().args.slice(0, 2)).toEqual([0, 12]);
    expect(searchProgramsByName).not.toHaveBeenCalled();
    expect(text()).toContain('Computer Science');
    expect(text()).toContain('2 programmes found');
  });

  it('links each card to the public page of the program, in the current language', async () => {
    await open('/programs');
    const card = q('[data-testid="program-card"]') as HTMLElement;

    const link = card.querySelector('a[data-testid="program-link"]');
    expect(link?.getAttribute('href')).toBe('/en/programs/1');
    expect(link?.textContent).toContain('Computer Science');
  });

  it('shows name, code, duration, one-decimal rate, school link and photo of a complete program', async () => {
    await open('/programs');
    const card = q('[data-testid="program-card"]') as HTMLElement;

    expect(card.textContent).toContain('Computer Science');
    expect(card.textContent).toContain('CS');
    expect(card.textContent).toContain('3 years');
    expect(card.textContent).toContain('4.3');
    const link = card.querySelector('a[href="/en/schools/7"]');
    expect(link?.textContent).toContain('ISI Dakar');
    // The file does not exist in the test, so the image may already have fallen back: the requested URL stays on the element.
    const img = card.querySelector('img');
    expect((img?.getAttribute('data-original-url') ?? img?.getAttribute('src')) ?? '').toContain('/uploads/public/cs.png');
    expect(card.textContent).not.toContain('Not provided');
  });

  it('marks absent fields "Not provided" and shows no rate or invented value', async () => {
    await open('/programs');
    const card = (el().querySelectorAll('[data-testid="program-card"]')[1]) as HTMLElement;

    expect(card.textContent).toContain('Bare Program');
    expect(card.querySelectorAll('[data-testid="not-provided"]').length).toBe(3);
    expect(card.textContent).toContain('Not provided');
    expect(card.querySelector('[data-testid="program-rate"]')).toBeNull();
    expect(card.textContent).not.toContain('Licence');
    expect(card.querySelector('a[href*="/schools/"]')).toBeNull();
  });

  it('shows a translated empty message when there are no programs', async () => {
    getPrograms.and.returnValue(of(page([])));
    await open('/programs');

    expect(text()).toContain('No programmes are listed yet');
  });

  it('says nothing matches when a search finds no program', async () => {
    searchProgramsByName.and.returnValue(of(page([])));
    await open('/programs?q=zzz');

    expect(text()).toContain('No programme matches this search');
  });

  it('shows the error state, never fake programs, then recovers on retry', async () => {
    getPrograms.and.returnValue(throwError(() => new Error('down')));
    await open('/programs');
    expect(q('app-error-state')).not.toBeNull();
    expect(q('[data-testid="program-card"]')).toBeNull();

    getPrograms.and.returnValue(of(page([full])));
    (q('app-error-state button') as HTMLButtonElement).click();
    await settle();

    expect(getPrograms).toHaveBeenCalledTimes(2);
    expect(q('app-error-state')).toBeNull();
    expect(text()).toContain('Computer Science');
  });

  it('?q=data searches by name once, prefills the input and lists the answer', async () => {
    const component = await open('/programs?q=data');

    expect(searchProgramsByName).toHaveBeenCalledTimes(1);
    expect(searchProgramsByName).toHaveBeenCalledWith('data', 0, 12);
    expect(getPrograms).not.toHaveBeenCalled();
    expect(component.searchQuery()).toBe('data');
    expect(input().value).toBe('data');
    expect(text()).toContain('Data Science');
  });

  it('typing waits 300ms, then writes q to the URL and searches', async () => {
    await open('/programs');
    const navigate = spyOn(router, 'navigate').and.callThrough();

    for (const v of ['d', 'da', 'dat']) {
      input().value = v;
      input().dispatchEvent(new Event('input'));
    }
    jasmine.clock().tick(299);
    await settle();
    expect(navigate).not.toHaveBeenCalled();

    jasmine.clock().tick(1);
    await settle();
    expect(router.url).toBe('/programs?q=dat');
    expect(searchProgramsByName).toHaveBeenCalledOnceWith('dat', 0, 12);
  });

  it('loads the next page, appends it and hides the button on the last page', async () => {
    getPrograms.and.callFake((n: number) =>
      of(n === 0 ? page([full], 0, false, 2) : page([bare], 1, true, 2)),
    );
    await open('/programs');

    (q('[data-testid="load-more"]') as HTMLButtonElement).click();
    await settle();

    expect(getPrograms.calls.mostRecent().args.slice(0, 2)).toEqual([1, 12]);
    expect(el().querySelectorAll('[data-testid="program-card"]').length).toBe(2);
    expect(q('[data-testid="load-more"]')).toBeNull();
  });

  it('keeps the search icon inside the input padding, clear of the typed text', async () => {
    await open('/programs');
    const icon = input().parentElement!.querySelector('svg')!.getBoundingClientRect();
    const box = input().getBoundingClientRect();
    const textStart = box.left + parseFloat(getComputedStyle(input()).paddingLeft);

    expect(icon.left).toBeGreaterThanOrEqual(box.left);
    expect(icon.right).toBeLessThanOrEqual(textStart);
  });

  it('renders in French, the source language', async () => {
    await open('/programs', 'fr');

    expect(text()).toContain('2 formations trouvées');
    expect(text()).toContain('Non renseigné');
    expect(text()).toContain('3 ans');
  });

  describe('comparison selection', () => {
    const boxes = () => Array.from(el().querySelectorAll<HTMLInputElement>('input[type="checkbox"]'));
    const box = (name: string) => el().querySelector<HTMLInputElement>(`input[aria-label="Select ${name} to compare"]`);
    const bar = () => q('[data-testid="compare-bar"]');
    const compareButton = () => q('[data-testid="compare-action"]') as HTMLButtonElement | null;

    async function openWithFour(url: string) {
      const content = [1, 2, 3, 4].map((id) => program(id, ['Alpha', 'Beta', 'Gamma', 'Delta'][id - 1]));
      getPrograms.and.returnValue(of(page(content)));
      searchProgramsByName.and.returnValue(of(page(content)));
      TestBed.configureTestingModule({
        providers: [
          provideZonelessChangeDetection(),
          provideHttpClient(),
          provideHttpClientTesting(),
          provideRouter([
            { path: 'programs', component: ProgramsPageComponent },
            { path: 'en/programs/compare', children: [] },
          ]),
          provideTransloco(translocoOptions),
          { provide: ProgramService, useValue: { getPrograms, searchProgramsByName } },
        ],
      });
      await firstValueFrom(TestBed.inject(LocaleService).use('en'));
      router = TestBed.inject(Router);
      harness = await RouterTestingHarness.create();
      await harness.navigateByUrl(url, ProgramsPageComponent);
      await settle();
    }

    async function check(name: string): Promise<void> {
      box(name)!.click();
      await settle();
    }

    it('gives every card a select checkbox with an accessible label', async () => {
      await openWithFour('/programs');

      expect(boxes().length).toBe(4);
      expect(box('Alpha')).not.toBeNull();
    });

    it('shows no bar until something is selected', async () => {
      await openWithFour('/programs');

      expect(bar()).toBeNull();
    });

    it('announces the count and the limit in a live region that exists before the first pick', async () => {
      await openWithFour('/programs');
      const live = q('[data-testid="compare-status"]');
      expect(live?.getAttribute('role')).toBe('status');
      expect(live?.textContent?.trim()).toBe('');

      await check('Alpha');
      expect(q('[data-testid="compare-status"]')).toBe(live);
      expect(live?.textContent).toContain('1 programme selected');

      await check('Beta');
      await check('Gamma');
      await check('Delta');
      expect(live?.textContent).toContain('You can compare up to 3 programmes');
    });

    it('keeps Compare disabled with one program and enables it at two, mirroring the selection in the URL next to ?q=', async () => {
      await openWithFour('/programs?q=a');
      await check('Alpha');

      expect(bar()?.textContent).toContain('1 programme selected');
      expect(compareButton()?.disabled).toBeTrue();

      await check('Beta');

      expect(bar()?.textContent).toContain('2 programmes selected');
      expect(compareButton()?.disabled).toBeFalse();
      const params = router.parseUrl(router.url).queryParams;
      expect(params['compare']).toBe('1,2');
      expect(params['q']).toBe('a');
    });

    it('refuses a 4th program with a message and leaves the box unchecked', async () => {
      await openWithFour('/programs');
      await check('Alpha');
      await check('Beta');
      await check('Gamma');
      await check('Delta');

      expect(box('Delta')!.checked).toBeFalse();
      expect(router.parseUrl(router.url).queryParams['compare']).toBe('1,2,3');
      expect(q('[data-testid="compare-limit"]')?.textContent).toContain('You can compare up to 3 programmes');
    });

    it('restores the selection from ?compare= on load, and drops a program when unchecked', async () => {
      await openWithFour('/programs?compare=1,2');

      expect(box('Alpha')!.checked).toBeTrue();
      expect(box('Beta')!.checked).toBeTrue();
      expect(box('Gamma')!.checked).toBeFalse();

      await check('Alpha');

      expect(router.parseUrl(router.url).queryParams['compare']).toBe('2');
    });

    it('ignores invalid and surplus ids in ?compare=', async () => {
      await openWithFour('/programs?compare=1,x,1,-2,2,3,4');

      expect(bar()?.textContent).toContain('3 programmes selected');
      expect(box('Delta')!.checked).toBeFalse();
    });

    it('clears the selection', async () => {
      await openWithFour('/programs?compare=1,2');

      (Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.includes('Clear selection')) as HTMLButtonElement).click();
      await settle();

      expect(router.parseUrl(router.url).queryParams['compare']).toBeUndefined();
      expect(bar()).toBeNull();
    });

    it('goes to the comparison page with the selected ids', async () => {
      await openWithFour('/programs?compare=2,3');

      compareButton()!.click();
      await settle();

      expect(router.url).toBe('/en/programs/compare?ids=2,3');
    });
  });
});
