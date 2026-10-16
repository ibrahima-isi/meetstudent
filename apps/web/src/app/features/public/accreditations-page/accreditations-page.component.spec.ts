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
import { AccreditationService } from '@services/accreditation.service';
import { AccreditationsPageComponent } from './accreditations-page.component';

describe('AccreditationsPageComponent', () => {
  let harness: RouterTestingHarness;
  let router: Router;
  let listAccreditations: jasmine.Spy;
  let searchAccreditationsByName: jasmine.Spy;

  const accreditation = (id: number, name: string, extra: Record<string, unknown> = {}) => ({ id, name, ...extra });
  const page = (content: unknown[], number = 0, last = true, totalElements = content.length) => ({
    content,
    number,
    last,
    totalElements,
  });
  const full = accreditation(1, 'AACSB International', {
    code: 'AACSB',
    description: 'Business schools quality label',
  });
  const bare = accreditation(2, 'Bare Accreditation', { code: 'BARE' });
  const blank = accreditation(3, 'Blank Accreditation', { code: 'BLANK', description: '   ' });

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
        provideRouter([{ path: 'accreditations', component: AccreditationsPageComponent }]),
        provideTransloco(translocoOptions),
        { provide: AccreditationService, useValue: { listAccreditations, searchAccreditationsByName } },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl(url, AccreditationsPageComponent);
    await settle();
    return component;
  }

  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate();
    listAccreditations = jasmine.createSpy('listAccreditations').and.returnValue(of(page([full, bare, blank])));
    searchAccreditationsByName = jasmine
      .createSpy('searchAccreditationsByName')
      .and.returnValue(of(page([accreditation(3, 'Databases Label')])));
  });

  afterEach(() => jasmine.clock().uninstall());

  it('shows a loading state while the first page is pending', async () => {
    listAccreditations.and.returnValue(new Subject<never>());
    await open('/accreditations');

    expect(q('[aria-busy="true"]')).not.toBeNull();
  });

  it('lists the first page of 12 without searching by name', async () => {
    await open('/accreditations');

    expect(listAccreditations.calls.mostRecent().args.slice(0, 2)).toEqual([0, 12]);
    expect(searchAccreditationsByName).not.toHaveBeenCalled();
    expect(text()).toContain('AACSB International');
    expect(text()).toContain('3 accreditations found');
  });

  it('shows name, code and description of a complete accreditation, with no rating, image or link', async () => {
    await open('/accreditations');
    const card = q('[data-testid="accreditation-card"]') as HTMLElement;

    expect(card.textContent).toContain('AACSB International');
    expect(card.textContent).toContain('AACSB');
    expect(card.textContent).toContain('Business schools quality label');
    expect(card.textContent).not.toContain('Not provided');
    expect(card.querySelector('a')).toBeNull();
    expect(card.querySelector('img')).toBeNull();
    expect(card.querySelector('lucide-icon[data-testid="star"], [data-testid*="rate"]')).toBeNull();
  });

  it('marks an absent or blank description "Not provided" and invents nothing', async () => {
    await open('/accreditations');
    const cards = el().querySelectorAll('[data-testid="accreditation-card"]');

    for (const card of [cards[1], cards[2]] as HTMLElement[]) {
      expect(card.querySelectorAll('[data-testid="not-provided"]').length).toBe(1);
      expect(card.textContent).toContain('Not provided');
    }
    expect(cards[1].textContent).toContain('Bare Accreditation');
    expect(cards[1].textContent).toContain('BARE');
    expect(cards[1].querySelector('a')).toBeNull();
  });

  it('shows a translated empty message when there are no accreditations', async () => {
    listAccreditations.and.returnValue(of(page([])));
    await open('/accreditations');

    expect(text()).toContain('No accreditations are listed yet');
  });

  it('says nothing matches when a search finds no accreditation', async () => {
    searchAccreditationsByName.and.returnValue(of(page([])));
    await open('/accreditations?q=zzz');

    expect(text()).toContain('No accreditation matches this search');
  });

  it('shows the error state, never fake accreditations, then recovers on retry', async () => {
    listAccreditations.and.returnValue(throwError(() => new Error('down')));
    await open('/accreditations');
    expect(q('app-error-state')).not.toBeNull();
    expect(q('[data-testid="accreditation-card"]')).toBeNull();

    listAccreditations.and.returnValue(of(page([full])));
    (q('app-error-state button') as HTMLButtonElement).click();
    await settle();

    expect(listAccreditations).toHaveBeenCalledTimes(2);
    expect(q('app-error-state')).toBeNull();
    expect(text()).toContain('AACSB International');
  });

  it('?q=data searches by name once, prefills the input and lists the answer', async () => {
    const component = await open('/accreditations?q=data');

    expect(searchAccreditationsByName).toHaveBeenCalledTimes(1);
    expect(searchAccreditationsByName).toHaveBeenCalledWith('data', 0, 12);
    expect(listAccreditations).not.toHaveBeenCalled();
    expect(component.searchQuery()).toBe('data');
    expect(input().value).toBe('data');
    expect(text()).toContain('Databases Label');
  });

  it('typing waits 300ms, then writes q to the URL and searches', async () => {
    await open('/accreditations');
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
    expect(router.url).toBe('/accreditations?q=dat');
    expect(searchAccreditationsByName).toHaveBeenCalledOnceWith('dat', 0, 12);
  });

  it('loads the next page, appends it and hides the button on the last page', async () => {
    listAccreditations.and.callFake((n: number) =>
      of(n === 0 ? page([full], 0, false, 2) : page([bare], 1, true, 2)),
    );
    await open('/accreditations');

    (q('[data-testid="load-more"]') as HTMLButtonElement).click();
    await settle();

    expect(listAccreditations.calls.mostRecent().args.slice(0, 2)).toEqual([1, 12]);
    expect(el().querySelectorAll('[data-testid="accreditation-card"]').length).toBe(2);
    expect(q('[data-testid="load-more"]')).toBeNull();
  });

  it('keeps the loaded accreditations when the next page fails, then appends it on retry', async () => {
    let nextPageFails = true;
    listAccreditations.and.callFake((n: number) => {
      if (n === 0) return of(page([full], 0, false, 2));
      return nextPageFails ? throwError(() => new Error('down')) : of(page([bare], 1, true, 2));
    });
    await open('/accreditations');

    (q('[data-testid="load-more"]') as HTMLButtonElement).click();
    await settle();
    expect(q('[data-testid="load-more-error"]')?.textContent).toContain('Retry');
    expect(el().querySelectorAll('[data-testid="accreditation-card"]').length).toBe(1);
    expect(q('app-error-state')).toBeNull();

    nextPageFails = false;
    (q('[data-testid="load-more-error"] button') as HTMLButtonElement).click();
    await settle();

    expect(listAccreditations.calls.mostRecent().args.slice(0, 2)).toEqual([1, 12]);
    expect(el().querySelectorAll('[data-testid="accreditation-card"]').length).toBe(2);
    expect(q('[data-testid="load-more-error"]')).toBeNull();
  });

  it('keeps the search icon inside the input padding, clear of the typed text', async () => {
    await open('/accreditations');
    const icon = input().parentElement!.querySelector('svg')!.getBoundingClientRect();
    const box = input().getBoundingClientRect();
    const textStart = box.left + parseFloat(getComputedStyle(input()).paddingLeft);

    expect(icon.left).toBeGreaterThanOrEqual(box.left);
    expect(icon.right).toBeLessThanOrEqual(textStart);
  });

  it('renders in French, the source language', async () => {
    await open('/accreditations', 'fr');

    expect(text()).toContain('3 accréditations trouvées');
    expect(text()).toContain('Non renseigné');
    expect(text()).toContain('AACSB');
  });
});
