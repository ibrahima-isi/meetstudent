import { Component, provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { LandingHeroComponent } from './landing-hero.component';

@Component({ template: '' })
class BlankComponent {}

describe('LandingHeroComponent', () => {
  let fixture: ComponentFixture<LandingHeroComponent>;
  let navigate: jasmine.Spy;
  let reducedMotion: boolean;

  const root = () => fixture.nativeElement as HTMLElement;
  const input = () => root().querySelector('input[type="search"]') as HTMLInputElement;
  const form = () => root().querySelector('form') as HTMLFormElement;
  const stage = () => root().querySelector('.stage') as HTMLElement;
  const px = () => Number(stage().style.getPropertyValue('--px'));
  const py = () => Number(stage().style.getPropertyValue('--py'));

  const submit = (text: string) => {
    input().value = text;
    input().dispatchEvent(new Event('input'));
    form().dispatchEvent(new Event('submit', { cancelable: true }));
  };
  const pointer = (type: string, init: PointerEventInit) => {
    root().dispatchEvent(new PointerEvent(type, { bubbles: true, ...init }));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    reducedMotion = false;
    const nativeMatchMedia = window.matchMedia.bind(window);
    spyOn(window, 'matchMedia').and.callFake((query: string) =>
      query.includes('prefers-reduced-motion') ? ({ matches: reducedMotion } as MediaQueryList) : nativeMatchMedia(query),
    );

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: LocaleService, useValue: { active: signal('fr') } },
      ],
    });
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);

    fixture = TestBed.createComponent(LandingHeroComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('fits a 375px screen: nothing is clipped on the right', () => {
    const wrapper = document.createElement('div');
    wrapper.style.width = '375px';
    document.body.append(wrapper);
    wrapper.append(root());
    try {
      const column = root().querySelector('.grid')!.firstElementChild as HTMLElement;
      const subtitle = root().querySelector('h1 + p') as HTMLElement;
      const button = root().querySelector('form button') as HTMLElement;
      const limit = wrapper.getBoundingClientRect().left + 375;

      for (const [name, el] of Object.entries({ column, h1: root().querySelector('h1')!, subtitle, button })) {
        expect(el.getBoundingClientRect().right).withContext(name).toBeLessThanOrEqual(limit);
      }
      expect(form().getBoundingClientRect().width).toBeLessThanOrEqual(341);
    } finally {
      wrapper.remove();
    }
  });

  it('has a short placeholder that fits a phone, in French and English', async () => {
    expect(input().placeholder).toBe('École, ville…');

    const transloco = TestBed.inject(TranslocoService);
    await firstValueFrom(transloco.load('en'));
    transloco.setActiveLang('en');
    await fixture.whenStable();
    fixture.detectChanges();

    expect(input().placeholder).toBe('School, city…');
  });

  it('has exactly one h1, with the hero title', () => {
    const headings = root().querySelectorAll('h1');
    expect(headings.length).toBe(1);
    expect(headings[0].textContent?.trim()).toBe("Trouvez l'école qui vous ressemble");
  });

  it('sends the search to the catalogue with the term as a query parameter', () => {
    submit('Dakar');
    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'schools'], { queryParams: { q: 'Dakar' } });
  });

  it('trims the term', () => {
    submit('  Dakar  ');
    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'schools'], { queryParams: { q: 'Dakar' } });
  });

  it('opens the plain catalogue for a blank search', () => {
    submit('   ');
    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'schools'], { queryParams: {} });
  });

  it('keeps reserved characters as data (a&b=c#d), the router encodes them', () => {
    submit('a&b=c#d');
    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'schools'], { queryParams: { q: 'a&b=c#d' } });
  });

  it('accepts non-Latin text and caps the field at 100 characters', () => {
    expect(input().getAttribute('maxlength')).toBe('100');
    submit('Université Cheikh Anta Diop — été');
    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'schools'], {
      queryParams: { q: 'Université Cheikh Anta Diop — été' },
    });
  });

  it('does not let a trailing space survive the length cut', () => {
    submit('x'.repeat(99) + ' y');
    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'schools'], { queryParams: { q: 'x'.repeat(99) } });
  });

  it('prevents the native form submission', () => {
    const event = new Event('submit', { cancelable: true });
    form().dispatchEvent(event);
    expect(event.defaultPrevented).toBeTrue();
  });

  it('submits with the Enter key through the form submit button', () => {
    const button = form().querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(button).toBeTruthy();
    input().value = 'Thiès';
    input().dispatchEvent(new Event('input'));
    // Clicking the submit button is what Enter in the field triggers natively (implicit submission).
    button.click();
    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'schools'], { queryParams: { q: 'Thiès' } });
  });

  it('links the two calls to action to the catalogue and to sign-up', () => {
    const explore = root().querySelector('a[href="/fr/schools"]') as HTMLAnchorElement;
    const register = root().querySelector('a[href="/fr/register"]') as HTMLAnchorElement;
    expect(explore).toBeTruthy();
    expect(explore.textContent).toContain('Explorer les établissements');
    expect(register).toBeTruthy();
    expect(register.textContent).toContain('Créer un compte gratuit');
    expect(register.classList).toContain('btn-secondary');
  });

  it('labels the search field for screen readers', () => {
    const label = root().querySelector(`label[for="${input().id}"]`);
    expect(input().id).toBeTruthy();
    expect(label).toBeTruthy();
    expect(label!.textContent?.trim()).toBe('Rechercher un établissement');
  });

  it('hides the decorative stage from assistive technology and shows no school data', () => {
    const wrap = root().querySelector('.stage-wrap') as HTMLElement;
    expect(wrap.getAttribute('aria-hidden')).toBe('true');
    expect(wrap.textContent?.trim()).toBe('');
  });

  it('keeps the animation delays out of inline style attributes', () => {
    const inline = Array.from(root().querySelectorAll('[style]')).filter((el) =>
      (el.getAttribute('style') ?? '').includes('animation-delay'),
    );
    expect(inline.length).toBe(0);
    expect(root().querySelectorAll('.stage .card.animate-float').length).toBe(3);
  });

  it('tilts the stage with the mouse and resets on leave', () => {
    const rect = root().getBoundingClientRect();
    pointer('pointermove', { pointerType: 'mouse', clientX: rect.right, clientY: rect.top + rect.height / 2 });
    expect(px()).toBeGreaterThan(0);
    pointer('pointerleave', { pointerType: 'mouse' });
    expect(px()).toBe(0);
    expect(py()).toBe(0);
  });

  it('does not tilt for touch or pen pointers', () => {
    const rect = root().getBoundingClientRect();
    pointer('pointermove', { pointerType: 'touch', clientX: rect.right, clientY: rect.bottom });
    pointer('pointermove', { pointerType: 'pen', clientX: rect.right, clientY: rect.bottom });
    expect(px()).toBe(0);
    expect(py()).toBe(0);
  });

  it('does not tilt when the visitor prefers reduced motion', () => {
    reducedMotion = true;
    const rect = root().getBoundingClientRect();
    pointer('pointermove', { pointerType: 'mouse', clientX: rect.right, clientY: rect.bottom });
    expect(px()).toBe(0);
    expect(py()).toBe(0);
  });
});

describe('LandingHeroComponent with the real router', () => {
  let fixture: ComponentFixture<LandingHeroComponent>;
  let router: Router;

  const input = () => fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
  const submit = async (text: string) => {
    input().value = text;
    input().dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([{ path: '**', component: BlankComponent }]),
        provideTransloco(translocoOptions),
        { provide: LocaleService, useValue: { active: signal('fr') } },
      ],
    });
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));
    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(LandingHeroComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('encodes reserved characters and leaves the fragment empty', async () => {
    await submit('a&b=c#d');
    expect(router.url).toBe('/fr/schools?q=a%26b%3Dc%23d');
    expect(router.parseUrl(router.url).fragment).toBeNull();
  });

  it('truncates a 500-character term to 100 characters', async () => {
    await submit('x'.repeat(500));
    const q = router.parseUrl(router.url).queryParams['q'] as string;
    expect(q.length).toBe(100);
  });

  it('round-trips non-Latin text through the URL', async () => {
    const text = 'Université Cheikh Anta Diop — été 大学';
    await submit(text);
    expect(router.url).toContain(encodeURIComponent(text));
    expect(decodeURIComponent(router.url.split('q=')[1])).toBe(text);
  });

  it('survives a lone surrogate and sends a well-formed term', async () => {
    await expectAsync(submit('\uD83D')).toBeResolved();
    const q = router.parseUrl(router.url).queryParams['q'] as string;
    expect(q).toBe('\uFFFD');
    expect(() => encodeURIComponent(q)).not.toThrow();
  });
});
