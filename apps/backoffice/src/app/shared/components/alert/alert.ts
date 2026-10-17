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
        [class]="kind() === 'error' ? 'border-destructive/40 bg-destructive/10 text-destructive' : 'border-border bg-brand-soft text-brand-soft-foreground'"
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
