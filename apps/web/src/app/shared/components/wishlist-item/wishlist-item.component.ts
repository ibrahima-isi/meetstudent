import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, MapPin, Star, X } from 'lucide-angular';
import { TranslocoDirective } from '@jsverse/transloco';
import { pluralKey } from '@i18n/plural';
import { School } from '@models/entities';
import { LocaleService } from '@services/locale.service';
import { MediaService } from '@services/media.service';
import { roundRating } from '@shared/format-rating';

/** At most this many tags are shown: the row is a summary, the school page has the rest. */
const MAX_TAGS = 2;

/**
 * One wishlisted school, detailed enough to recognise it without opening it (logo or initials, city,
 * kind of school, number of programmes, rating) with a "View more" link to its page. Used by the cart
 * panel and by the profile; removing is the parent's call, so the item only reports the click.
 */
@Component({
  selector: 'app-wishlist-item',
  imports: [RouterLink, LucideAngularModule, TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <article *transloco="let t" class="flex gap-3 rounded-xl border border-border bg-card p-3 transition-shadow hover:shadow-md">
      @if (logoUrl(); as logo) {
        <img [src]="logo" alt="" class="h-12 w-12 shrink-0 rounded-lg border border-border bg-card object-contain" />
      } @else {
        <div
          data-testid="school-monogram"
          aria-hidden="true"
          class="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-brand-soft text-sm font-semibold text-brand-soft-foreground"
        >{{ monogram() }}</div>
      }

      <div class="min-w-0 flex-1">
        <div class="flex items-start justify-between gap-2">
          <h4 class="line-clamp-2 break-words text-sm font-semibold text-foreground">{{ school().name }}</h4>
          <button
            type="button"
            data-testid="wishlist-remove"
            [attr.aria-label]="t('wishlist.removeAction')"
            [disabled]="removing()"
            (click)="removed.emit(school())"
            class="-mr-1 -mt-1 shrink-0 cursor-pointer rounded-lg p-1.5 text-muted-foreground transition-colors hover:bg-accent hover:text-destructive disabled:cursor-not-allowed disabled:opacity-50"
          >
            <lucide-icon [img]="X" class="h-4 w-4" aria-hidden="true"></lucide-icon>
          </button>
        </div>

        <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
          @if (city(); as city) {
            <span class="flex items-center gap-1">
              <lucide-icon [img]="MapPin" class="h-3.5 w-3.5" aria-hidden="true"></lucide-icon>{{ city }}
            </span>
          }
          @if (programCount() > 0) {
            <span>{{ t(plural('wishlist.programmes', programCount()), { count: programCount() }) }}</span>
          }
          @if (rating() > 0) {
            <span class="flex items-center gap-1 font-medium text-foreground">
              <lucide-icon [img]="Star" class="h-3.5 w-3.5 fill-yellow-400 text-yellow-400" aria-hidden="true"></lucide-icon>{{ rating() }}
            </span>
          }
        </div>

        @if (tags().length > 0) {
          <ul class="mt-2 flex flex-wrap gap-1.5" role="list">
            @for (tag of tags(); track tag) {
              <li class="rounded-full bg-brand-soft px-2 py-0.5 text-xs font-medium text-brand-soft-foreground">{{ tag }}</li>
            }
          </ul>
        }

        <a [routerLink]="['/', lang(), 'schools', school().id]" class="btn btn-secondary mt-3 h-8 px-3 text-sm">
          {{ t('wishlist.viewMore') }}
        </a>
      </div>
    </article>
  `,
})
export class WishlistItemComponent {
  private readonly media = inject(MediaService);
  private readonly locale = inject(LocaleService);

  readonly school = input.required<School>();
  /** True while the server is applying a change to this school, so the button cannot be pressed twice. */
  readonly removing = input(false);
  readonly removed = output<School>();

  protected readonly MapPin = MapPin;
  protected readonly Star = Star;
  protected readonly X = X;

  protected readonly lang = computed(() => this.locale.active());
  protected readonly logoUrl = computed(() => this.media.resolveUrl(this.school().logo));
  protected readonly city = computed(() => this.school().address?.city || null);
  protected readonly tags = computed(() => (this.school().tags ?? []).slice(0, MAX_TAGS).map((t) => t.name));
  protected readonly programCount = computed(() => this.school().programs?.length ?? 0);
  protected readonly rating = computed(() => roundRating(this.school().rating));
  protected readonly monogram = computed(() =>
    this.school()
      .name.split(/[\s'’-]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((word) => word[0].toUpperCase())
      .join(''),
  );

  /** The key for a count, by the plural rule of the language being read. */
  protected plural(base: string, count: number): string {
    return pluralKey(base, count, this.locale.active());
  }
}
