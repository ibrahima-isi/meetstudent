import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { ForumWriteState } from './forum-access';
import { ForumGateComponent } from './forum-gate.component';

describe('ForumGateComponent', () => {
  let fixture: ComponentFixture<ForumGateComponent>;

  async function mount(state: ForumWriteState, lang: 'fr' | 'en' = 'en') {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideRouter([]), provideTransloco(translocoOptions)],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    fixture = TestBed.createComponent(ForumGateComponent);
    fixture.componentRef.setInput('state', state);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const gate = () => (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('[data-testid="forum-gate"]');

  it('says why an unverified account cannot write, with a link to the profile where the link can be resent', async () => {
    await mount('unverified');

    expect(gate()!.textContent).toContain('Confirm your email address');
    const link = gate()!.querySelector('a')!;
    expect(link.getAttribute('href')).toBe('/en/profile');
    expect(link.textContent).toContain('Go to my profile');
  });

  it('is translated in French, with the French profile link', async () => {
    await mount('unverified', 'fr');

    expect(gate()!.textContent).toContain('Confirmez votre adresse e-mail');
    expect(gate()!.querySelector('a')!.getAttribute('href')).toBe('/fr/profile');
  });

  it('shows nothing to a writer', async () => {
    await mount('writer');
    expect(gate()).toBeNull();
  });

  it('keeps the other reasons apart from the verification one', async () => {
    await mount('pending');
    expect(gate()!.textContent).not.toContain('Confirm your email address');
  });
});
