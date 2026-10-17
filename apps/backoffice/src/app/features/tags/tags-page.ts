import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { AccreditationsSection } from './accreditations-section';
import { TagsSection } from './tags-section';

type Tab = 'tags' | 'accreditations';

@Component({
  selector: 'app-tags-page',
  imports: [TagsSection, AccreditationsSection],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="mb-4 text-2xl font-semibold text-foreground">Tags et accréditations</h1>

    <div role="tablist" class="mb-4 flex gap-1 border-b border-border">
      @for (t of tabs; track t.id) {
        <button
          type="button"
          role="tab"
          [attr.data-tab]="t.id"
          [attr.aria-selected]="tab() === t.id"
          (click)="tab.set(t.id)"
          class="-mb-px border-b-2 px-4 py-2 text-sm font-medium"
          [class]="tab() === t.id ? 'border-brand text-brand' : 'border-transparent text-muted-foreground hover:text-foreground'"
        >
          {{ t.label }}
        </button>
      }
    </div>

    @if (tab() === 'tags') {
      <app-tags-section />
    } @else {
      <app-accreditations-section />
    }
  `,
})
export class TagsPage {
  readonly tabs: { id: Tab; label: string }[] = [
    { id: 'tags', label: 'Tags' },
    { id: 'accreditations', label: 'Accréditations' },
  ];
  readonly tab = signal<Tab>('tags');
}
