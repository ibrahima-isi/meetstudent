import { ChangeDetectionStrategy, Component, RESPONSE_INIT, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { LocaleService } from '@services/locale.service';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink, TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="min-h-screen flex flex-col items-center justify-center gap-4 p-4 text-center"
         *transloco="let t">
      <p class="text-6xl font-bold text-indigo-600 dark:text-indigo-400">404</p>
      <h1 class="text-2xl font-semibold">{{ t('notFound.title') }}</h1>
      <p class="text-muted-foreground">{{ t('notFound.message') }}</p>
      <a
        [routerLink]="['/', locale.active()]"
        class="mt-2 rounded-lg bg-indigo-600 px-5 py-2.5 text-white hover:bg-indigo-700"
      >
        {{ t('notFound.backHome') }}
      </a>
    </div>
  `,
})
export class NotFoundComponent {
  protected readonly locale = inject(LocaleService);

  constructor() {
    // `@angular/ssr` provides RESPONSE_INIT only under RenderMode.Server and
    // builds the Response from it after rendering, so this sets the HTTP status.
    // Absent in the browser and in tests: nothing to patch.
    const responseInit = inject(RESPONSE_INIT, { optional: true });
    if (responseInit) {
      responseInit.status = 404;
    }
  }
}
