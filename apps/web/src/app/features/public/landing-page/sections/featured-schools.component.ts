import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { LucideAngularModule, MapPin, Star } from 'lucide-angular';
import { School } from '@models/entities';
import { LocaleService } from '@services/locale.service';
import { SchoolService } from '@services/school.service';
import { RevealDirective } from '@shared/directives/reveal.directive';
import { roundRating } from '@shared/format-rating';
import { ErrorStateComponent } from '@shared/components/error-state/error-state.component';
import { ImageWithFallbackComponent } from '@shared/components/image-with-fallback/image-with-fallback.component';

const FEATURED_COUNT = 6;

@Component({
  selector: 'app-featured-schools',
  imports: [
    TranslocoDirective,
    RouterLink,
    LucideAngularModule,
    RevealDirective,
    ErrorStateComponent,
    ImageWithFallbackComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  styles: [
    `
      .school-link:focus-visible {
        outline: 2px solid var(--brand);
        outline-offset: 2px;
        border-radius: inherit;
      }
      @media (hover: hover) {
        .school-card-inner {
          transition:
            transform 0.25s ease,
            box-shadow 0.25s ease;
        }
        .school-link:hover .school-card-inner,
        .school-link:focus-visible .school-card-inner {
          transform: translateY(-4px);
          box-shadow: var(--shadow-glow);
        }
      }
      @media (prefers-reduced-motion: reduce) {
        .school-card-inner,
        .school-link:hover .school-card-inner,
        .school-link:focus-visible .school-card-inner {
          transform: none;
          transition: none;
        }
      }
    `,
  ],
  template: `
    <ng-container *transloco="let t">
      @if (status() !== 'loaded' || schools().length > 0) {
        <section class="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
          <div class="flex flex-wrap items-end justify-between gap-4">
            <div>
              <h2 class="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
                {{ t('lp.featured.title') }}
              </h2>
              <p class="mt-2 text-muted-foreground">{{ t('lp.featured.subtitle') }}</p>
            </div>
            <a class="btn btn-secondary" [routerLink]="['/', lang(), 'schools']">
              {{ t('lp.featured.all') }}
            </a>
          </div>

          @switch (status()) {
            @case ('loading') {
              <div class="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
                @for (n of skeletons; track n) {
                  <div
                    data-testid="skeleton-card"
                    aria-hidden="true"
                    class="animate-pulse overflow-hidden rounded-2xl border border-border bg-card"
                  >
                    <div class="h-44 w-full bg-muted"></div>
                    <div class="space-y-3 p-5">
                      <div class="h-5 w-3/4 rounded bg-muted"></div>
                      <div class="h-4 w-1/2 rounded bg-muted"></div>
                    </div>
                  </div>
                }
              </div>
            }
            @case ('error') {
              <app-error-state [message]="t('lp.featured.error')" (retry)="load()" />
            }
            @default {
              <ul role="list" class="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
                @for (school of schools(); track school.id) {
                  <li appReveal>
                    <a
                      class="school-link block rounded-2xl"
                      [routerLink]="['/', lang(), 'schools', school.id]"
                    >
                      <div
                        class="school-card-inner overflow-hidden rounded-2xl border border-border bg-card"
                      >
                        @if (coverOf(school); as cover) {
                          <app-image-with-fallback
                            [src]="cover"
                            [alt]="''"
                            class="block h-44 w-full object-cover"
                          />
                        } @else {
                          <div
                            data-testid="cover-placeholder"
                            aria-hidden="true"
                            class="h-44 w-full bg-brand-soft"
                          ></div>
                        }
                        <div class="p-5">
                          <h3
                            class="line-clamp-2 break-words text-lg font-semibold text-foreground"
                          >
                            {{ school.name }}
                          </h3>
                          <div
                            class="mt-3 flex min-w-0 items-center justify-between gap-3 text-sm text-muted-foreground"
                          >
                            @if (cityOf(school); as city) {
                              <span data-testid="city" class="flex min-w-0 items-center gap-1.5">
                                <lucide-icon [img]="MapPin" class="h-4 w-4" aria-hidden="true" />
                                {{ city }}
                              </span>
                            }
                            @if (hasRating(school)) {
                              <span
                                data-testid="rating"
                                class="ml-auto flex items-center gap-1 font-medium text-foreground"
                              >
                                <lucide-icon
                                  [img]="Star"
                                  class="h-4 w-4 fill-yellow-400 text-yellow-400"
                                  aria-hidden="true"
                                />
                                <span aria-hidden="true">{{ roundRating(school.rating) }}</span>
                                <span class="sr-only">{{
                                  t('lp.featured.rating', { value: roundRating(school.rating) })
                                }}</span>
                              </span>
                            }
                          </div>
                        </div>
                      </div>
                    </a>
                  </li>
                }
              </ul>
            }
          }
        </section>
      }
    </ng-container>
  `,
})
export class FeaturedSchoolsComponent implements OnInit {
  private readonly schoolService = inject(SchoolService);
  private readonly locale = inject(LocaleService);

  protected readonly lang = this.locale.active;
  protected readonly status = signal<'loading' | 'loaded' | 'error'>('loading');
  protected readonly schools = signal<School[]>([]);
  protected readonly skeletons = [1, 2, 3];
  protected readonly MapPin = MapPin;
  protected readonly Star = Star;
  protected readonly roundRating = roundRating;

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.status.set('loading');
    this.schoolService.getSchools(0, FEATURED_COUNT).subscribe({
      next: (page) => {
        this.schools.set((page.content ?? []).filter((school) => school.id !== undefined));
        this.status.set('loaded');
      },
      error: () => this.status.set('error'),
    });
  }

  protected coverOf(school: School): string | null {
    return typeof school.coverImageUrl === 'string' && school.coverImageUrl !== ''
      ? school.coverImageUrl
      : null;
  }

  protected cityOf(school: School): string | null {
    const city = (school.address as Partial<School['address']> | undefined)?.city;
    return typeof city === 'string' && city.trim() !== '' ? city : null;
  }

  protected hasRating(school: School): boolean {
    return typeof school.rating === 'number' && Number.isFinite(school.rating) && school.rating > 0;
  }
}
