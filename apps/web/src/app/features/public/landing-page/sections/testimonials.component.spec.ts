import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { TestimonialsComponent } from './testimonials.component';

describe('TestimonialsComponent', () => {
  let fixture: ComponentFixture<TestimonialsComponent>;

  const root = () => fixture.nativeElement as HTMLElement;
  const figures = () => Array.from(root().querySelectorAll('figure'));

  async function render(lang: 'fr' | 'en') {
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    fixture = TestBed.createComponent(TestimonialsComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideTransloco(translocoOptions)],
    });
  });

  it('is an anchor target on a section that clears the fixed navbar', async () => {
    await render('fr');
    const section = root().querySelector('section#reviews');
    expect(section).not.toBeNull();
    expect(section!.classList).toContain('scroll-mt-24');
  });

  it('renders three figures with a blockquote and a figcaption each', async () => {
    await render('fr');
    expect(figures().length).toBe(3);
    for (const figure of figures()) {
      expect(figure.querySelector('blockquote')!.textContent!.trim().length).toBeGreaterThan(0);
      expect(figure.querySelector('figcaption')!.textContent!.trim().length).toBeGreaterThan(0);
    }
  });

  it('labels every testimonial as an example, in French', async () => {
    await render('fr');
    expect(figures().length).toBe(3);
    for (const figure of figures()) {
      expect(figure.textContent).toContain('Exemple');
    }
  });

  it('labels every testimonial as an example, in English', async () => {
    await render('en');
    expect(figures().length).toBe(3);
    for (const figure of figures()) {
      expect(figure.textContent).toContain('Example');
    }
  });

  it('shows the example badge visibly on every card, not hidden or screen-reader only', async () => {
    await render('fr');
    expect(figures().length).toBe(3);
    for (const figure of figures()) {
      const badge = Array.from(figure.querySelectorAll<HTMLElement>('span')).find(
        (s) => s.textContent!.trim() === 'Exemple',
      );
      expect(badge).withContext('badge span').toBeDefined();
      expect(badge!.classList).not.toContain('sr-only');
      expect(badge!.hasAttribute('hidden')).toBeFalse();
      expect(badge!.closest('[aria-hidden="true"]')).toBeNull();
      const style = getComputedStyle(badge!);
      expect(style.display).not.toBe('none');
      expect(style.visibility).toBe('visible');
      expect(badge!.getBoundingClientRect().width).toBeGreaterThan(0);
    }
  });

  it('shows the note that these are examples', async () => {
    await render('fr');
    expect(root().textContent).toContain('Exemples en attendant les premiers avis vérifiés.');
  });

  it('shows the note in English', async () => {
    await render('en');
    expect(root().textContent).toContain('Examples until the first verified reviews arrive.');
  });

  it('shows a role and city line only, never an invented personal name', async () => {
    await render('fr');
    const captions = figures().map((f) => f.querySelector('figcaption')!.textContent!.trim());
    expect(captions).toEqual([
      'Étudiante en licence · Dakar',
      'Élève de terminale · Thiès',
      'Étudiant en master · Saint-Louis',
    ]);
  });

  it('uses one h2 and no h1', async () => {
    await render('fr');
    expect(root().querySelectorAll('h1').length).toBe(0);
    expect(root().querySelectorAll('h2').length).toBe(1);
    expect(root().querySelector('h2')!.textContent!.trim()).toBe("Exemples d'avis");
  });

  it('titles the section as example reviews in English', async () => {
    await render('en');
    expect(root().querySelector('h2')!.textContent!.trim()).toBe('Example reviews');
  });

  it('keeps the section in the DOM with its anchor id', async () => {
    await render('en');
    expect(root().querySelector('section')!.id).toBe('reviews');
  });

  it('is a list of three items with an explicit role', async () => {
    await render('fr');
    const list = root().querySelector('ul')!;
    expect(list.getAttribute('role')).toBe('list');
    expect(list.querySelectorAll(':scope > li').length).toBe(3);
  });

  it('reveals each li and keeps the hover lift on an inner wrapper', async () => {
    await render('fr');
    const items = Array.from(root().querySelectorAll<HTMLElement>('ul > li'));
    expect(items.length).toBe(3);
    for (const li of items) {
      expect(li.classList).toContain('reveal');
      expect(getComputedStyle(li).transitionProperty).toContain('opacity');
      const card = li.querySelector('figure.testimonial-card');
      expect(card).not.toBeNull();
      expect(card!.parentElement).toBe(li);
      expect(li.classList).not.toContain('testimonial-card');
    }
  });

  it('hides decorative icons from assistive technology', async () => {
    await render('fr');
    const icons = Array.from(root().querySelectorAll('lucide-icon'));
    expect(icons.length).toBeGreaterThan(0);
    expect(icons.every((i) => i.getAttribute('aria-hidden') === 'true')).toBeTrue();
  });
});
