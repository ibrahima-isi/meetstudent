import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, X } from 'lucide-angular';
import { TranslocoDirective } from '@jsverse/transloco';
import { pluralKey } from '@i18n/plural';
import { Program } from '@models/entities';
import { LocaleService } from '@services/locale.service';

/**
 * One wishlisted program: its name, its school and its length, with a link to the school page.
 * Used by the cart panel and by the profile; removing is the parent's call, so the item only
 * reports the click.
 */
@Component({
  selector: 'app-wishlist-program-item',
  imports: [RouterLink, LucideAngularModule, TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <article *transloco="let t" class="rounded-xl border border-border bg-card p-3 transition-shadow hover:shadow-md">
      <div class="flex items-start justify-between gap-2">
        <h4 class="line-clamp-2 break-words text-sm font-semibold text-foreground">{{ program().name }}</h4>
        <button
          type="button"
          data-testid="wishlist-program-remove"
          [attr.aria-label]="t('wishlist.removeAction')"
          [disabled]="removing()"
          (click)="removed.emit(program())"
          class="-mr-1 -mt-1 shrink-0 cursor-pointer rounded-lg p-1.5 text-muted-foreground transition-colors hover:bg-accent hover:text-destructive disabled:cursor-not-allowed disabled:opacity-50"
        >
          <lucide-icon [img]="X" class="h-4 w-4" aria-hidden="true"></lucide-icon>
        </button>
      </div>

      <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
        @if (program().schoolName) {
          <span>{{ program().schoolName }}</span>
        }
        @if (years() > 0) {
          <span>{{ t(plural('schoolDetail.years', years()), { count: years() }) }}</span>
        }
      </div>

      @if (program().schoolId; as schoolId) {
        <a [routerLink]="['/', lang(), 'schools', schoolId]" class="btn btn-secondary mt-3 h-8 px-3 text-sm">
          {{ t('wishlist.viewMore') }}
        </a>
      }
    </article>
  `,
})
export class WishlistProgramItemComponent {
  private readonly locale = inject(LocaleService);

  readonly program = input.required<Program>();
  /** True while the server is applying a change to this program, so the button cannot be pressed twice. */
  readonly removing = input(false);
  readonly removed = output<Program>();

  protected readonly X = X;
  protected readonly lang = computed(() => this.locale.active());
  protected readonly years = computed(() => this.program().duration ?? 0);

  /** The key for a count, by the plural rule of the language being read. */
  protected plural(base: string, count: number): string {
    return pluralKey(base, count, this.locale.active());
  }
}
