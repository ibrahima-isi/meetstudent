import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Inline alert for errors and notices. Renders nothing when `message` is empty. */
@Component({
  selector: 'app-alert',
  host: { class: 'block' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (message()) {
      <div
        role="alert"
        class="rounded-md border px-3 py-2 text-sm"
        [class]="kind() === 'error' ? 'border-red-300 bg-red-50 text-red-800' : 'border-sky-300 bg-sky-50 text-sky-800'"
      >
        {{ message() }}
      </div>
    }
  `,
})
export class Alert {
  readonly message = input('');
  readonly kind = input<'error' | 'info'>('error');
}
