import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { LanguageSwitcherComponent } from '@shared/components/language-switcher/language-switcher.component';
import { ThemeToggleComponent } from '@shared/components/theme-toggle/theme-toggle.component';

/**
 * The gradient shell around login, register and verify. It was the same block
 * written three times in `app.html`; as a layout route it is written once and
 * the child route decides what fills it.
 */
@Component({
  selector: 'app-auth-layout',
  imports: [RouterOutlet, LanguageSwitcherComponent, ThemeToggleComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 flex items-center justify-center p-4"
    >
      <div class="w-full max-w-md">
        <div class="mb-3 flex items-center justify-end gap-2">
          <app-language-switcher />
          <app-theme-toggle />
        </div>
        <router-outlet />
      </div>
    </div>
  `,
})
export class AuthLayoutComponent {}
