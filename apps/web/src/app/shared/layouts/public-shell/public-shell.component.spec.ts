import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { PublicShellComponent } from './public-shell.component';

describe('PublicShellComponent', () => {
  let root: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideTransloco(translocoOptions),
        { provide: LocaleService, useValue: { active: signal('fr'), remember: () => undefined } },
        { provide: TokenService, useValue: { isAuthenticated: signal(false), user: signal(null) } },
      ],
    });
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));
    const fixture = TestBed.createComponent(PublicShellComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    root = fixture.nativeElement;
  });

  it('frames the page with the navbar and a main landmark holding the outlet', () => {
    expect(root.querySelector('app-dock-navbar')).not.toBeNull();
    expect(root.querySelector('main#main router-outlet')).not.toBeNull();
  });

  it('offers a skip link that is hidden until focused', () => {
    const skip = root.querySelector('a[href="#main"]') as HTMLElement;
    expect(skip).not.toBeNull();
    expect(skip.textContent?.trim()).toBe('Aller au contenu');
    expect(skip.classList).toContain('sr-only');
  });

  it('moves focus to the main landmark on click without navigating', () => {
    const skip = root.querySelector('a[href="#main"]') as HTMLElement;
    const router = TestBed.inject(Router);
    const before = router.url;
    const event = new MouseEvent('click', { bubbles: true, cancelable: true });
    skip.dispatchEvent(event);

    expect(event.defaultPrevented).toBeTrue();
    expect(document.activeElement).toBe(root.querySelector('main#main'));
    expect(router.url).toBe(before);
  });
});
