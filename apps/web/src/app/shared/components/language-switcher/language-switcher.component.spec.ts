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
    return Array.from(fixture.nativeElement.querySelectorAll('button'));
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

  it('offers both languages under their own names', async () => {
    await render('/fr/login');

    expect(buttons().length).toBe(2);
    expect(buttonFor('fr').textContent?.trim()).toBe('Français');
    expect(buttonFor('en').textContent?.trim()).toBe('English');
  });

  it('marks the active language for assistive technology', async () => {
    await render('/fr/login');

    expect(buttonFor('fr').getAttribute('aria-current')).toBe('true');
    expect(buttonFor('en').getAttribute('aria-current')).toBeNull();
  });

  it('remembers the choice, because this one is explicit and not a guess', async () => {
    await render('/fr/login');

    buttonFor('en').click();

    expect(remember).toHaveBeenCalledWith('en');
  });

  it('lands on the same page in the other language, state intact', async () => {
    await render('/fr/schools/7?tab=programs#rates');
    const router = TestBed.inject(Router);

    buttonFor('en').click();
    await fixture.whenStable();

    expect(router.url).toBe('/en/schools/7?tab=programs#rates');
  });

  it('does not navigate to the language already active', async () => {
    await render('/fr/login');
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl');

    buttonFor('fr').click();

    expect(navigate).not.toHaveBeenCalled();
    expect(remember).not.toHaveBeenCalled();
  });
});
