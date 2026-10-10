import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * The gradient shell around login and register. It was the same block
 * written twice in `app.html`; as a layout route it is written once and
 * the child route decides what fills it.
 */
@Component({
  selector: 'app-auth-layout',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 dark:from-gray-950 dark:to-indigo-950 flex items-center justify-center px-4 pb-8 pt-24"
    >
      <div class="w-full max-w-md">
        <router-outlet />
      </div>
    </div>
  `,
})
export class AuthLayoutComponent {}
