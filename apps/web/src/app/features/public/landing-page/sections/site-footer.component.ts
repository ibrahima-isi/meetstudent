import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  inject,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { AuthService } from '@services/auth.service';
import { LocaleService } from '@services/locale.service';
import { injectHydratedSession } from '@shared/hydrated-session';

@Component({
  selector: 'app-site-footer',
  imports: [TranslocoDirective, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <footer role="contentinfo" class="border-t border-border bg-card" *transloco="let t">
      <div class="mx-auto grid max-w-7xl gap-10 px-4 py-12 sm:px-6 md:grid-cols-3 lg:px-8">
        <div>
          <a
            data-testid="footer-brand"
            class="text-lg font-semibold tracking-tight text-foreground"
            [routerLink]="['/', lang()]"
            >MeetStudent</a
          >
          <p class="mt-3 max-w-xs text-sm text-muted-foreground">{{ t('lp.footer.tagline') }}</p>
        </div>
        <nav [attr.aria-label]="t('lp.footer.product')">
          <p class="text-sm font-semibold text-foreground">{{ t('lp.footer.product') }}</p>
          <ul role="list" class="mt-3 space-y-2 text-sm">
            <li>
              <a
                class="inline-flex min-h-11 min-w-11 items-center rounded-sm text-muted-foreground hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
                [routerLink]="['/', lang(), 'schools']"
                >{{ t('nav.schools') }}</a
              >
            </li>
            <li>
              <a
                class="inline-flex min-h-11 min-w-11 items-center rounded-sm text-muted-foreground hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
                [routerLink]="['/', lang(), 'programs']"
                >{{ t('pageTitle.programs') }}</a
              >
            </li>
            <li>
              <a
                class="inline-flex min-h-11 min-w-11 items-center rounded-sm text-muted-foreground hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
                [routerLink]="['/', lang(), 'courses']"
                >{{ t('pageTitle.courses') }}</a
              >
            </li>
            <li>
              <a
                class="inline-flex min-h-11 min-w-11 items-center rounded-sm text-muted-foreground hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
                [routerLink]="['/', lang(), 'accreditations']"
                >{{ t('pageTitle.accreditations') }}</a
              >
            </li>
          </ul>
        </nav>
        @if (hydrated()) {
          <nav [attr.aria-label]="t('lp.footer.account')">
            <p class="text-sm font-semibold text-foreground">{{ t('lp.footer.account') }}</p>
            <ul role="list" class="mt-3 flex flex-wrap items-center gap-3 text-sm">
              @if (signedIn()) {
                <li>
                  <a class="btn btn-secondary" [routerLink]="['/', lang(), 'profile']">{{ t('nav.profile') }}</a>
                </li>
                <li>
                  <button type="button" class="btn btn-primary" (click)="logout()">{{ t('nav.logout') }}</button>
                </li>
              } @else {
                <li>
                  <a class="btn btn-secondary" [routerLink]="['/', lang(), 'login']">{{ t('nav.login') }}</a>
                </li>
                <li>
                  <a class="btn btn-primary" [routerLink]="['/', lang(), 'register']">{{ t('nav.register') }}</a>
                </li>
              }
            </ul>
          </nav>
        }
      </div>
      <div class="border-t border-border">
        <p class="mx-auto max-w-7xl px-4 py-6 text-sm text-muted-foreground sm:px-6 lg:px-8">
          © {{ year }} MeetStudent. {{ t('lp.footer.rights') }}
        </p>
      </div>
    </footer>
  `,
})
export class SiteFooterComponent {
  private readonly router = inject(Router);
  /** Resolved on logout only, so the anonymous footer needs no HTTP stack. */
  private readonly injector = inject(Injector);
  protected readonly lang = inject(LocaleService).active;

  /**
   * Server-rendered pages leave the account column out until the browser has hydrated, so a
   * signed-in user never sees Log in / Sign up flash; see `injectHydratedSession`.
   */
  private readonly session = injectHydratedSession();
  protected readonly hydrated = this.session.hydrated;
  protected readonly signedIn = this.session.signedIn;
  protected readonly year = new Date().getFullYear();

  protected logout(): void {
    this.injector.get(AuthService).logout();
    void this.router.navigate(['/', this.lang()]);
  }
}
