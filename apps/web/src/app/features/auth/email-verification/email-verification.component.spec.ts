import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { EmailVerificationComponent } from './email-verification.component';

describe('EmailVerificationComponent translations', () => {
  const email = 'awa@example.com';
  let fixture: ComponentFixture<EmailVerificationComponent>;

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  async function render(lang: 'fr' | 'en') {
    const transloco = TestBed.inject(TranslocoService);
    // *transloco renders nothing until the dynamic import resolves.
    await firstValueFrom(transloco.load(lang));
    transloco.setActiveLang(lang);
    fixture = TestBed.createComponent(EmailVerificationComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [EmailVerificationComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideTransloco(translocoOptions),
      ],
    });
  });

  afterEach(() => localStorage.removeItem(`user_${email}`));

  it('renders in French, the source language', async () => {
    await render('fr');

    expect(fixture.nativeElement.querySelector('h1').textContent.trim())
      .toBe('Vérifiez votre adresse e-mail');
    // No address in memory (direct hit): the screen asks for one.
    expect(text()).toContain("Saisissez l'adresse utilisée lors de l'inscription");
  });

  it('renders in English when English is active', async () => {
    await render('en');

    expect(fixture.nativeElement.querySelector('h1').textContent.trim())
      .toBe('Verify Your Email');
  });

  it('puts the countdown inside the translated sentence', async () => {
    await render('fr');

    expect(text()).toContain('Renvoyer dans 60 s');
  });

  it('rejects a wrong code in the active language', async () => {
    localStorage.setItem(`user_${email}`, JSON.stringify({ verificationCode: '123456' }));
    await render('fr');
    fixture.componentInstance.typedEmail.set(email);

    jasmine.clock().install();
    try {
      fixture.componentInstance.handleVerify('000000');
      jasmine.clock().tick(1000);
    } finally {
      jasmine.clock().uninstall();
    }
    fixture.detectChanges();
    await fixture.whenStable();

    expect(text()).toContain('Code de vérification invalide. Veuillez réessayer.');
  });
});
