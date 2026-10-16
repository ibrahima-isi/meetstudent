import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
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
        provideHttpClient(),
        provideHttpClientTesting(),
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

  it('puts the email-verification banner between the navbar and the page', () => {
    const banner = root.querySelector('app-email-verification-banner') as HTMLElement;
    expect(banner).not.toBeNull();
    const order = Array.from(root.children).map((c) => c.tagName.toLowerCase());
    expect(order.indexOf('app-email-verification-banner')).toBeGreaterThan(order.indexOf('app-dock-navbar'));
    expect(order.indexOf('app-email-verification-banner')).toBeLessThan(order.indexOf('main'));
  });

  it('renders exactly one common footer, after the main landmark', () => {
    expect(root.querySelectorAll('footer[role="contentinfo"]').length).toBe(1);
    const order = Array.from(root.children).map((c) => c.tagName.toLowerCase());
    expect(order.indexOf('app-site-footer')).toBeGreaterThan(order.indexOf('main'));
  });

  it('pins the footer to the bottom of the viewport when the page is short', () => {
    // The frame spans at least the viewport, and the main landmark absorbs the
    // spare height, so a short page never leaves the footer floating mid-screen.
    expect(getComputedStyle(root).minHeight).toBe(`${window.innerHeight}px`);
    root.style.height = '2000px';
    const frame = root.getBoundingClientRect();
    const footer = root.querySelector('footer[role="contentinfo"]')!.getBoundingClientRect();
    expect(Math.round(footer.bottom)).toBe(Math.round(frame.bottom));
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
