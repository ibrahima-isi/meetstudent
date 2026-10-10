import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { LocaleService } from '@services/locale.service';

@Component({
  selector: 'app-site-footer',
  imports: [TranslocoDirective, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <footer class="border-t border-border bg-card" *transloco="let t">
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
                class="text-muted-foreground hover:text-foreground"
                [routerLink]="['/', lang(), 'schools']"
                >{{ t('nav.schools') }}</a
              >
            </li>
          </ul>
        </nav>
        <nav [attr.aria-label]="t('lp.footer.account')">
          <p class="text-sm font-semibold text-foreground">{{ t('lp.footer.account') }}</p>
          <ul role="list" class="mt-3 space-y-2 text-sm">
            <li>
              <a
                class="text-muted-foreground hover:text-foreground"
                [routerLink]="['/', lang(), 'login']"
                >{{ t('nav.login') }}</a
              >
            </li>
            <li>
              <a
                class="text-muted-foreground hover:text-foreground"
                [routerLink]="['/', lang(), 'register']"
                >{{ t('nav.register') }}</a
              >
            </li>
          </ul>
        </nav>
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
  protected readonly lang = inject(LocaleService).active;
  protected readonly year = new Date().getFullYear();
}
