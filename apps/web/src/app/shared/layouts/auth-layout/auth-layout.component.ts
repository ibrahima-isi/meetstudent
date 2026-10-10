import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { ArrowLeft, LucideAngularModule } from 'lucide-angular';
import { LocaleService } from '@services/locale.service';
import { AuthBrandPanelComponent } from './auth-brand-panel.component';

/**
 * Split-screen shell around login and register: brand panel on the left
 * (large screens only), the routed form on the right.
 */
@Component({
  selector: 'app-auth-layout',
  imports: [RouterOutlet, RouterLink, TranslocoDirective, LucideAngularModule, AuthBrandPanelComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="grid min-h-screen lg:grid-cols-2" *transloco="let t">
      <div class="hidden lg:flex"><app-auth-brand-panel class="flex-1" /></div>
      <div class="flex flex-col justify-center px-4 pb-10 pt-24 sm:px-8 lg:px-16">
        <div class="mx-auto w-full max-w-md">
          <a class="mb-6 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground" [routerLink]="['/', lang()]">
            <lucide-icon [img]="ArrowLeft" class="h-4 w-4" aria-hidden="true" /> {{ t('nav.backHome') }}
          </a>
          <router-outlet />
        </div>
      </div>
    </div>
  `,
})
export class AuthLayoutComponent {
  protected readonly lang = inject(LocaleService).active;
  protected readonly ArrowLeft = ArrowLeft;
}
