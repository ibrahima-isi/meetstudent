import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { DockNavbarComponent } from '@shared/components/dock-navbar/dock-navbar.component';

/** Frame of every public page: skip link, floating navbar, and the page itself. */
@Component({
  selector: 'app-public-shell',
  imports: [RouterOutlet, TranslocoDirective, DockNavbarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
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
    <main id="main" tabindex="-1"><router-outlet /></main>
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
