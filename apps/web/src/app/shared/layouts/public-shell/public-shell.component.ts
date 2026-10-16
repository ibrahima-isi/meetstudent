import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { DockNavbarComponent } from '@shared/components/dock-navbar/dock-navbar.component';
import { EmailVerificationBannerComponent } from '@shared/components/email-verification-banner/email-verification-banner.component';
import { SiteFooterComponent } from '../../../features/public/landing-page/sections/site-footer.component';

/** Frame of every page but the 404 (public and signed-in): skip link, floating navbar, the page itself, and the single common footer. */
@Component({
  selector: 'app-public-shell',
  imports: [RouterOutlet, TranslocoDirective, DockNavbarComponent, EmailVerificationBannerComponent, SiteFooterComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  // A column at least as tall as the viewport whose main landmark takes the spare
  // height: on a short page the footer sits at the bottom, not mid-screen.
  host: { class: 'flex min-h-screen flex-col' },
  template: `
    <ng-container *transloco="let t">
      <a
        href="#main"
        (click)="focusMain($event)"
        class="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[60] btn btn-primary"
        >{{ t('nav.skipToContent') }}</a
      >
    </ng-container>
    <app-dock-navbar />
    <app-email-verification-banner />
    <main id="main" tabindex="-1" class="flex-1"><router-outlet /></main>
    <app-site-footer />
  `,
})
export class PublicShellComponent {
  private readonly document = inject(DOCUMENT);

  /** `<base href="/">` would turn a bare `#main` into a full page load; focus the landmark instead. */
  protected focusMain(event: Event): void {
    event.preventDefault();
    const main = this.document.getElementById('main');
    main?.focus();
    main?.scrollIntoView?.();
  }
}
