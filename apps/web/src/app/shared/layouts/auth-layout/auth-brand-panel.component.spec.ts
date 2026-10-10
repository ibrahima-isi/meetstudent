import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { AuthBrandPanelComponent } from './auth-brand-panel.component';

describe('AuthBrandPanelComponent', () => {
  let root: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideTransloco(translocoOptions)],
    });
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));
    const fixture: ComponentFixture<AuthBrandPanelComponent> = TestBed.createComponent(AuthBrandPanelComponent);
    fixture.detectChanges();
    root = fixture.nativeElement;
  });

  it('shows the title and the text', () => {
    expect(root.querySelector('h2')?.textContent).toContain('Votre avenir commence ici');
    expect(root.textContent).toContain('Comparez, enregistrez et préparez votre dossier');
  });

  it('labels the quote as an example', () => {
    const quote = root.querySelector('blockquote');
    expect(quote?.textContent).toContain("J'ai pu comparer trois écoles en une soirée.");
    expect(root.textContent).toContain('Exemple');
  });

  it('hides the decorative cards from assistive technology', () => {
    const cards = root.querySelectorAll('[data-decor]');
    expect(cards.length).toBe(3);
    cards.forEach((card) => expect(card.getAttribute('aria-hidden')).toBe('true'));
  });
});
