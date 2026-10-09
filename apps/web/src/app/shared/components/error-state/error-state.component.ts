import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';

/**
 * The one error surface of the app: a message and a retry button. Pages show
 * it in place of the data they could not load and re-run their fetch on
 * `retry`; they never fall back to placeholder data.
 */
@Component({
  selector: 'app-error-state',
  imports: [TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      *transloco="let t"
      role="alert"
      class="max-w-md mx-auto text-center py-12 px-4"
    >
      <p class="text-foreground font-bold text-lg mb-1">{{ t('common.errorGeneric') }}</p>
      @if (message(); as detail) {
        <p class="text-muted-foreground mb-6">{{ detail }}</p>
      }
      <button
        type="button"
        (click)="retry.emit()"
        class="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors font-medium cursor-pointer"
      >
        {{ t('common.retry') }}
      </button>
    </div>
  `,
})
export class ErrorStateComponent {
  /** Names what failed, already translated by the caller. */
  readonly message = input<string>();
  readonly retry = output<void>();
}
