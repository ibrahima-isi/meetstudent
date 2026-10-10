import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
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
      providers: [provideZonelessChangeDetection(), provideTransloco(translocoOptions)],
    });
  });

  it('is an anchor target that clears the fixed navbar', async () => {
    await render('fr');
    const section = root().querySelector('section#how-it-works');
    expect(section).not.toBeNull();
    expect(section!.classList).toContain('scroll-mt-24');
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
