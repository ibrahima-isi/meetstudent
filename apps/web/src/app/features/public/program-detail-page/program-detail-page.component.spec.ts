import { TestBed } from '@angular/core/testing';
import { Provider, provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { WishlistService } from '@services/wishlist.service';
import { ProgramDetailPageComponent } from './program-detail-page.component';

describe('ProgramDetailPageComponent', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;

  const full = {
    id: 5,
    name: 'Computer Science',
    schoolId: 7,
    schoolName: 'ISI Dakar',
    description: 'Learn algorithms.\nThen build things.',
    level: 'MASTER',
    annualFee: 1500,
    availableSeats: 12,
    duration: 2,
    courses: [
      { id: 1, name: 'Algorithms', description: 'Sorting and graphs' },
      { id: 2, name: 'Databases' },
    ],
  };

  const el = () => harness.routeNativeElement as HTMLElement;
  const text = () => (el().textContent ?? '').replace(/\s+/g, ' ');
  const q = (sel: string) => el().querySelector(sel);
  const byId = (id: string) => q(`[data-testid="${id}"]`) as HTMLElement | null;
  const flat = (s: string | null | undefined) => (s ?? '').replace(/[\s  ]+/g, ' ').trim();

  async function settle(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  async function open(id: string, answer: unknown | 'skip' | { status: number }, lang: 'fr' | 'en' = 'en', extra: Provider[] = []) {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'programs/:id', component: ProgramDetailPageComponent }]),
        provideTransloco(translocoOptions),
        { provide: TokenService, useValue: { isAuthenticated: () => false, user: signal(null) } },
        ...extra,
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl(`/programs/${id}`, ProgramDetailPageComponent);
    if (answer !== 'skip') {
      await Promise.resolve();
      respond(answer);
    }
    await navigation;
    await settle();
  }

  function respond(answer: unknown) {
    const req = http.expectOne((r) => /\/programs\/\d+$/.test(r.url));
    if (answer && typeof answer === 'object' && 'status' in (answer as object)) {
      req.flush({ message: 'x' }, { status: (answer as { status: number }).status, statusText: 'err' });
    } else {
      req.flush(answer as object);
    }
  }

  afterEach(() => http?.verify());

  describe('wishlist heart', () => {
    let load: jasmine.Spy;
    const wishlistStub = (available: boolean, signedIn = true): Provider[] => {
      load = jasmine.createSpy('load');
      return [
        { provide: TokenService, useValue: { user: () => (signedIn ? { id: 9 } : null), isAuthenticated: () => signedIn } },
        {
          provide: WishlistService,
          useValue: {
            available: () => available,
            load,
            hasProgram: () => false,
            isProgramPending: () => false,
            toggleProgram: () => undefined,
          },
        },
      ];
    };
    const heart = () => byId('program-wishlist-toggle');

    it('puts the heart in the header and loads the list once for a student', async () => {
      await open('5', full, 'en', wishlistStub(true));

      expect(heart()).not.toBeNull();
      expect(heart()?.closest('.bg-gradient-to-br')).not.toBeNull();
      expect(load).toHaveBeenCalledTimes(1);
    });

    it('shows no heart and does not load the list for an expert', async () => {
      await open('5', full, 'en', wishlistStub(false));

      expect(heart()).toBeNull();
      expect(load).not.toHaveBeenCalled();
    });

    it('keeps the heart for an anonymous visitor, who is invited to sign in', async () => {
      await open('5', full, 'en', wishlistStub(false, false));

      expect(heart()).not.toBeNull();
      expect(load).not.toHaveBeenCalled();
    });
  });

  it('shows name, school link, description, level, fee, seats and courses with their description', async () => {
    await open('5', full);

    expect(byId('program-name')?.textContent).toContain('Computer Science');
    expect(q('a[href="/en/schools/7"]')?.textContent).toContain('ISI Dakar');
    expect(byId('program-description')?.textContent).toContain('Learn algorithms.');
    expect(flat(byId('program-level')?.textContent)).toContain('Master');
    expect(flat(byId('program-fee')?.textContent)).toContain('€1,500');
    expect(flat(byId('program-seats')?.textContent)).toContain('12 places available');
    const courses = Array.from(el().querySelectorAll('[data-testid="course-item"]'));
    expect(courses.length).toBe(2);
    expect(courses[0].textContent).toContain('Algorithms');
    expect(courses[0].textContent).toContain('Sorting and graphs');
    expect(courses[1].textContent).toContain('Databases');
  });

  it('renders in French: translated level, euro format of the language and plural', async () => {
    await open('5', { ...full, level: 'LICENCE', availableSeats: 1 }, 'fr');

    expect(flat(byId('program-level')?.textContent)).toContain('Licence');
    expect(flat(byId('program-fee')?.textContent)).toMatch(/1 500(,00)? €/);
    expect(flat(byId('program-seats')?.textContent)).toContain('1 place disponible');
  });

  it('shows only the name for a program without optional fields: no placeholder, no empty block', async () => {
    await open('5', { id: 5, name: 'Bare' });

    expect(byId('program-name')?.textContent).toContain('Bare');
    for (const id of ['program-description', 'program-level', 'program-fee', 'program-seats', 'program-courses', 'program-school']) {
      expect(byId(id)).withContext(id).toBeNull();
    }
    expect(text()).not.toContain('Not provided');
  });

  it('shows 0 seats as full', async () => {
    await open('5', { ...full, availableSeats: 0 });
    expect(flat(byId('program-seats')?.textContent)).toContain('Full');
  });

  it('keeps the description as plain text: markup and URLs create no element', async () => {
    const payload = '<script>alert(1)</script><img src=x onerror="alert(2)"> https://evil.example/x';
    await open('5', {
      ...full,
      description: payload,
      courses: [{ id: 1, name: 'C', description: payload }],
    });

    for (const box of [byId('program-description')!, byId('course-item')!]) {
      expect(box.textContent).toContain('<script>alert(1)</script>');
      expect(box.textContent).toContain('<img src=x onerror="alert(2)">');
      expect(box.querySelector('script, img, a')).toBeNull();
    }
    expect(byId('program-description')?.classList.contains('whitespace-pre-line')).toBeTrue();
    expect(byId('program-description')?.children.length).toBe(0);
  });

  it('shows the shared error state when the program does not exist, and retries', async () => {
    await open('5', { status: 404 });

    expect(q('[role="alert"]')).not.toBeNull();
    expect(byId('program-name')).toBeNull();

    (q('[role="alert"] button') as HTMLButtonElement).click();
    respond(full);
    await settle();

    expect(q('[role="alert"]')).toBeNull();
    expect(byId('program-name')?.textContent).toContain('Computer Science');
  });

  it('shows the error state when the API fails', async () => {
    await open('5', { status: 500 });

    expect(q('[role="alert"]')).not.toBeNull();
  });

  for (const bad of ['1e3', '0x10', '0', '12345678901', '1.5']) {
    it(`refuses the id "${bad}" without calling the API`, async () => {
      await open(bad, 'skip');

      expect(q('[role="alert"]')).not.toBeNull();
      http.expectNone(() => true);
    });
  }

  it('shows the error state without calling the API when the id is not a number', async () => {
    await open('abc', 'skip');

    expect(q('[role="alert"]')).not.toBeNull();
    http.expectNone(() => true);
  });

  it('shows a loading state while the program is pending', async () => {
    await open('5', 'skip');

    expect(q('[aria-busy="true"]')).not.toBeNull();
    respond(full);
  });
});
