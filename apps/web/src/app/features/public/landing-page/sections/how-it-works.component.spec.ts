import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { SchoolsPageComponent } from '../../schools-page/schools-page.component';
import { ProgramsPageComponent } from '../../programs-page/programs-page.component';
import { CoursesPageComponent } from '../../courses-page/courses-page.component';
import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { HowItWorksComponent } from './how-it-works.component';

describe('HowItWorksComponent', () => {
  let fixture: ComponentFixture<HowItWorksComponent>;

  const root = () => fixture.nativeElement as HTMLElement;
  const steps = () => Array.from(root().querySelectorAll('li'));
  const headings = () => steps().map((li) => li.querySelector('h3')?.textContent?.trim());

  async function render(lang: 'fr' | 'en') {
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    fixture = TestBed.createComponent(HowItWorksComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideRouter([]), provideTransloco(translocoOptions)],
    });
  });

  it('submits the French school search through the accessible form', async () => {
    await render('fr');
    const field = root().querySelector('input[type=search]') as HTMLInputElement;
    expect(field).not.toBeNull();
    if (!field) return;
    expect(root().querySelector(`label[for="${field.id}"]`)?.textContent).toContain('Nom à rechercher');
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    field.value = '  Dakar  ';
    field.dispatchEvent(new Event('input'));
    (root().querySelector('button[type=submit]') as HTMLButtonElement).click();
    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'schools'], { queryParams: { q: 'Dakar' } });
  });

  it('is an anchor target; the global scroll-padding clears the navbar', async () => {
    await render('fr');
    const section = root().querySelector('section#how-it-works');
    expect(section).not.toBeNull();
    expect(section!.classList).not.toContain('scroll-mt-24');
  });

  it('lists the three steps in order, in French', async () => {
    await render('fr');
    expect(headings()).toEqual(['Cherchez', 'Comparez', 'Décidez']);
  });

  it('lists the three steps in order, in English', async () => {
    await render('en');
    expect(headings()).toEqual(['Search', 'Compare', 'Decide']);
  });

  it('uses one h2 for the section, an h3 per step and no h1', async () => {
    await render('fr');
    expect(root().querySelectorAll('h1').length).toBe(0);
    expect(root().querySelectorAll('h2').length).toBe(1);
    expect(root().querySelector('h2')!.textContent!.trim()).toBe('Comment ça marche');
    expect(root().querySelectorAll('h3').length).toBe(3);
  });

  it('is an ordered list of three items with numbered, aria-hidden badges', async () => {
    await render('fr');
    const list = root().querySelector('ol')!;
    expect(list).not.toBeNull();
    expect(list.querySelectorAll(':scope > li').length).toBe(3);
    const badges = steps().map((li) => li.querySelector('.badge')!);
    expect(badges.map((b) => b?.textContent?.trim())).toEqual(['1', '2', '3']);
    expect(badges.every((b) => b.getAttribute('aria-hidden') === 'true')).toBeTrue();
  });

  it('hides the decorative icons from assistive technology', async () => {
    await render('fr');
    const icons = Array.from(root().querySelectorAll('lucide-icon'));
    expect(icons.length).toBe(3);
    expect(icons.every((i) => i.getAttribute('aria-hidden') === 'true')).toBeTrue();
  });

  it('reveals each card on scroll', async () => {
    await render('fr');
    expect(steps().length).toBe(3);
    expect(steps().every((li) => li.classList.contains('reveal'))).toBeTrue();
  });

  it('keeps the reveal fade on the li and puts the tilt on an inner card', async () => {
    await render('fr');
    for (const li of steps()) {
      expect(getComputedStyle(li).transitionProperty).toContain('opacity');
      const card = li.querySelector('.step-card');
      expect(card).not.toBeNull();
      expect(card!.parentElement).toBe(li);
      expect(li.classList).not.toContain('step-card');
    }
  });

  it('gives the ordered list an explicit list role', async () => {
    await render('fr');
    expect(root().querySelector('ol')!.getAttribute('role')).toBe('list');
  });
});


describe('Cherchez catalogue journeys', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  const root = () => harness.routeNativeElement as HTMLElement;
  const settle = async () => {
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  };
  const page = (content: unknown[]) => ({ content, last: true, totalElements: content.length });

  async function open(lang: 'fr' | 'en') {
    TestBed.configureTestingModule({ providers: [
      provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(),
      provideTransloco(translocoOptions),
      provideRouter([{ path: ':lang', children: [
        { path: '', component: HowItWorksComponent },
        { path: 'schools', component: SchoolsPageComponent },
        { path: 'programs', component: ProgramsPageComponent },
        { path: 'courses', component: CoursesPageComponent },
      ] }]),
    ] });
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create('/' + lang);
    await settle();
  }

  async function submit(resource: string, term: string) {
    const select = root().querySelector('select') as HTMLSelectElement;
    select.value = resource;
    select.dispatchEvent(new Event('change'));
    const field = root().querySelector('input[type=search]') as HTMLInputElement;
    field.value = term;
    field.dispatchEvent(new Event('input'));
    (root().querySelector('button[type=submit]') as HTMLButtonElement).click();
    await settle();
  }

  afterEach(() => http?.verify());

  it('explains an unsupported term without losing the selected type, query or field focus', async () => {
    await open('fr');
    await submit('courses', '100% anglais');
    expect(TestBed.inject(Router).url).toBe('/fr');
    http.expectNone(r => r.url.startsWith('/api/v1/'));
    const field = root().querySelector('input[type=search]') as HTMLInputElement;
    expect(field).not.toBeNull();
    if (!field) return;
    expect(field.value).toBe('100% anglais');
    expect((root().querySelector('select') as HTMLSelectElement).value).toBe('courses');
    expect(field.getAttribute('aria-invalid')).toBe('true');
    const messageId = field.getAttribute('aria-describedby');
    expect(messageId).toBeTruthy();
    expect(root().querySelector('#' + messageId)?.textContent).toContain('La recherche ne peut pas traiter');
    expect(document.activeElement).toBe(field);
    field.value = 'Anglais';
    field.dispatchEvent(new Event('input'));
    await settle();
    expect(field.getAttribute('aria-invalid')).toBeNull();
    expect(field.getAttribute('aria-describedby')).toBeNull();
    expect(root().querySelector('[role=alert]')).toBeNull();
    (root().querySelector('button[type=submit]') as HTMLButtonElement).click();
    await settle();
    expect(TestBed.inject(Router).url).toBe('/fr/courses?q=Anglais');
    http.expectOne(r => r.url === '/api/v1/courses/name/Anglais').flush(page([]));
    await settle();
  });

  for (const lang of ['fr', 'en'] as const) {
    for (const resource of ['schools', 'programs', 'courses']) {
      it(`${lang}: explains all unsupported path terms for ${resource} without showing false empty results`, async () => {
        await open(lang);
        for (const term of ['génie / civil', 'a\\b', '100% anglais', 'a;b', '.', '..']) {
          await submit(resource, term);
          expect(TestBed.inject(Router).url).toBe('/' + lang);
          const field = root().querySelector('input[type=search]') as HTMLInputElement;
          expect(field.value).toBe(term);
          expect(field.getAttribute('aria-invalid')).toBe('true');
          expect((root().querySelector('select') as HTMLSelectElement).value).toBe(resource);
          expect(root().querySelector('[role=alert]')?.textContent).toContain(lang === 'fr'
            ? 'La recherche ne peut pas traiter' : 'Search cannot process');
        }
        http.expectNone(r => r.url.startsWith('/api/v1/'));
      });
      it(`${lang}: submits ${resource}, displays HTTP loading/results and preserves the query`, async () => {
        await open(lang);
        const select = root().querySelector('select') as HTMLSelectElement;
        expect(root().querySelector(`label[for="${select.id}"]`)).not.toBeNull();
        expect(Array.from(select.options).map(o => o.text)).toEqual(lang === 'fr'
          ? ['Établissement', 'Formation', 'Cours'] : ['School', 'Programme', 'Course']);
        expect(root().querySelector('button')?.textContent?.trim()).toBe(lang === 'fr' ? 'Cherchez' : 'Search');
        await submit(resource, '  École & été. 2  ');
        const router = TestBed.inject(Router);
        expect(router.parseUrl(router.url).queryParams['q']).toBe('École & été. 2');
        expect(router.url.startsWith('/' + lang + '/' + resource + '?')).toBeTrue();
        expect(root().querySelector('input')?.value).toBe('École & été. 2');
        expect(root().querySelector('[aria-busy="true"]')).not.toBeNull();
        const request = http.expectOne(r => r.url === '/api/v1/' + resource + '/name/' + encodeURIComponent('École & été. 2'));
        expect(request.request.params.get('page')).toBe('0');
        expect(request.request.params.get('size')).toBe('12');
        request.flush(page([{ id: 31, name: 'École & été. 2' }]));
        await settle();
        expect(root().textContent).toContain('École & été. 2');
        expect(root().querySelector('[aria-busy="true"]')).toBeNull();
      });
    }
  }

  for (const [resource, empty] of [
    ['schools', 'Aucun établissement trouvé avec ces critères'],
    ['programs', 'Aucune formation ne correspond à cette recherche'],
    ['courses', 'Aucun cours ne correspond à cette recherche'],
  ]) {
    it(`${resource}: displays HTTP failure, retries the same search and shows an empty result`, async () => {
      await open('fr');
      await submit(resource, 'introuvable');
      const url = '/api/v1/' + resource + '/name/introuvable';
      http.expectOne(r => r.url === url).flush({}, { status: 500, statusText: 'Unavailable' });
      await settle();
      expect(root().querySelector('app-error-state')).not.toBeNull();
      (root().querySelector('app-error-state button') as HTMLButtonElement).click();
      await settle();
      expect(root().querySelector('[aria-busy="true"]')).not.toBeNull();
      http.expectOne(r => r.url === url).flush(page([]));
      await settle();
      expect(root().querySelector('app-error-state')).toBeNull();
      expect(root().textContent).toContain(empty);
    });

    it(`${resource}: opens the plain catalogue for a blank query`, async () => {
      await open('en');
      await submit(resource, '   ');
      expect(TestBed.inject(Router).url).toBe('/en/' + resource);
      http.expectOne(r => r.url === '/api/v1/' + resource).flush(page([]));
      await settle();
    });
  }
});
