import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, provideZonelessChangeDetection, signal } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { LanguageSwitcherComponent } from './language-switcher.component';

/** The switcher only reads `router.url`; what renders there is irrelevant. */
@Component({ template: '' })
class BlankComponent {}

describe('LanguageSwitcherComponent', () => {
  let fixture: ComponentFixture<LanguageSwitcherComponent>;
  let remember: jasmine.Spy;
  let active: ReturnType<typeof signal<'fr' | 'en'>>;

  function buttons(): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('#language-menu button'));
  }

  function trigger(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('button.lang-trigger');
  }

  async function open() {
    trigger().click();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function buttonFor(locale: string): HTMLButtonElement {
    const found = buttons().find((b) => b.getAttribute('lang') === locale);
    if (!found) {
      throw new Error(`no control for ${locale}; got ${buttons().length} buttons`);
    }
    return found;
  }

  async function render(url: string) {
    await TestBed.inject(Router).navigateByUrl(url);
    fixture = TestBed.createComponent(LanguageSwitcherComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    active = signal<'fr' | 'en'>('fr');
    remember = jasmine.createSpy('remember');

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([{ path: '**', component: BlankComponent }]),
        provideTransloco(translocoOptions),
        { provide: LocaleService, useValue: { active, remember } },
      ],
    });

    // *transloco renders nothing until the dynamic import resolves.
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));
  });

  it('shows one button, closed, naming the language being read', async () => {
    await render('/fr/login');

    expect(fixture.nativeElement.querySelectorAll('button').length).toBe(1);
    expect(trigger().textContent?.trim()).toBe('Français');
    expect(trigger().getAttribute('aria-expanded')).toBe('false');
    expect(trigger().getAttribute('aria-controls')).toBeNull();
    expect(trigger().getAttribute('aria-label')).toBe('Langue : Français');
  });

  it('opens the list of languages, each under its own name', async () => {
    await render('/fr/login');
    await open();

    expect(trigger().getAttribute('aria-expanded')).toBe('true');
    expect(trigger().getAttribute('aria-controls')).toBe('language-menu');
    expect(buttons().length).toBe(2);
    expect(buttonFor('fr').textContent?.trim()).toBe('Français');
    expect(buttonFor('en').textContent?.trim()).toBe('English');
  });

  it('marks the active language for assistive technology', async () => {
    await render('/fr/login');
    await open();

    expect(buttonFor('fr').getAttribute('aria-current')).toBe('true');
    expect(buttonFor('en').getAttribute('aria-current')).toBeNull();
  });

  it('remembers the choice, because this one is explicit and not a guess', async () => {
    await render('/fr/login');
    await open();

    buttonFor('en').click();

    expect(remember).toHaveBeenCalledWith('en');
  });

  it('lands on the same page in the other language, state intact, and closes', async () => {
    await render('/fr/schools/7?tab=programs#rates');
    const router = TestBed.inject(Router);
    await open();

    buttonFor('en').click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(router.url).toBe('/en/schools/7?tab=programs#rates');
    expect(buttons().length).toBe(0);
  });

  it('does not navigate to the language already active, and closes', async () => {
    await render('/fr/login');
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl');
    await open();

    buttonFor('fr').click();
    fixture.detectChanges();

    expect(navigate).not.toHaveBeenCalled();
    expect(remember).not.toHaveBeenCalled();
    expect(buttons().length).toBe(0);
  });

  it('shows the short code in compact mode', async () => {
    await render('/fr/login');
    fixture.componentRef.setInput('compact', true);
    fixture.detectChanges();

    expect(trigger().textContent?.trim()).toBe('FR');
    expect(trigger().getAttribute('aria-label')).toBe('Langue : Français');
  });

  it('closes on Escape and gives focus back to the button', async () => {
    await render('/fr/login');
    await open();
    buttonFor('en').focus();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(buttons().length).toBe(0);
    expect(document.activeElement).toBe(trigger());
  });

  it('closes on a press outside, not on a press inside', async () => {
    await render('/fr/login');
    await open();

    trigger().dispatchEvent(new Event('pointerdown', { bubbles: true }));
    fixture.detectChanges();
    expect(buttons().length).toBe(2);

    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    fixture.detectChanges();
    expect(buttons().length).toBe(0);
  });

  it('reports opening and closing, and can be closed from outside', async () => {
    await render('/fr/login');
    const changes: boolean[] = [];
    fixture.componentInstance.openChange.subscribe((v: boolean) => changes.push(v));

    await open();
    fixture.componentInstance.close();
    fixture.detectChanges();

    expect(changes).toEqual([true, false]);
    expect(buttons().length).toBe(0);
  });
});
