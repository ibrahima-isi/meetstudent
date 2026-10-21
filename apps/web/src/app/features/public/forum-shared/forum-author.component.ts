import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { ForumAuthorRole } from '@models/forum';
import { LocaleService } from '@services/locale.service';
import { cleanDisplayName } from '@shared/display-name';

/**
 * Who wrote a message and when. The name comes from an account and is cleaned of hidden characters; a deleted
 * account shows a translated text (the API sends `authorDeleted`, never a name). Everything is interpolated.
 */
@Component({
  selector: 'app-forum-author',
  imports: [TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted-foreground' },
  template: `
    <ng-container *transloco="let t">
      <span data-testid="forum-author-name" dir="auto" class="min-w-0 max-w-full overflow-hidden break-words font-medium text-foreground">
        @if (deleted()) {
          {{ t('forum.author.deleted') }}
        } @else if (cleaned(); as shown) {
          {{ shown }}
        } @else {
          {{ t('forum.author.unknown') }}
        }
      </span>
      @if (role() === 'STUDENT') {
        <span class="rounded-full bg-brand-soft px-2 py-0.5 text-xs font-medium text-brand-soft-foreground">{{ t('forum.author.student') }}</span>
      } @else if (role() === 'EXPERT') {
        <span class="rounded-full bg-brand-soft px-2 py-0.5 text-xs font-medium text-brand-soft-foreground">{{ t('forum.author.expert') }}</span>
      }
      <span aria-hidden="true">·</span>
      <time [attr.datetime]="createdAt()">{{ date() }}</time>
      @if (hidden()) {
        <span data-testid="forum-hidden-badge" class="rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">{{ t('forum.author.hidden') }}</span>
      }
      @if (modifiedAt()) {
        <span>({{ t('forum.author.edited') }})</span>
      }
    </ng-container>
  `,
})
export class ForumAuthorComponent {
  private readonly locale = inject(LocaleService);

  readonly name = input<string | null | undefined>();
  readonly role = input<ForumAuthorRole | null | undefined>();
  readonly deleted = input(false);
  readonly createdAt = input.required<string>();
  readonly modifiedAt = input<string | null | undefined>();
  /** True only for an administrator, who is the only one to receive hidden messages. */
  readonly hidden = input(false);

  protected readonly cleaned = computed(() => cleanDisplayName(this.name()));

  /** UTC on purpose: the server and the browser must print the same day. */
  protected readonly date = computed(() => {
    const time = Date.parse(this.createdAt());
    if (Number.isNaN(time)) return '';
    return new Intl.DateTimeFormat(this.locale.active(), { dateStyle: 'medium', timeZone: 'UTC' }).format(time);
  });
}
