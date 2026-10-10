import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { LucideAngularModule, Quote } from 'lucide-angular';
import { RevealDirective } from '@shared/directives/reveal.directive';

/**
 * Illustrative quotes, not real reviews: every card carries the "Example"
 * badge and the section states it, until verified reviews exist.
 */
@Component({
  selector: 'app-testimonials',
  imports: [TranslocoDirective, LucideAngularModule, RevealDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  styles: [
    `
      @media (hover: hover) {
        .testimonial-card {
          transition:
            transform 0.25s ease,
            box-shadow 0.25s ease;
        }
        .testimonial-card:hover {
          transform: translateY(-4px);
          box-shadow: var(--shadow-glow);
        }
      }
      @media (prefers-reduced-motion: reduce) {
        .testimonial-card,
        .testimonial-card:hover {
          transform: none;
          transition: none;
        }
      }
    `,
  ],
  template: `
    <section id="reviews" class="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
      <ng-container *transloco="let t">
        <div class="text-center">
          <h2 class="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
            {{ t('lp.reviews.title') }}
          </h2>
          <p class="mx-auto mt-3 max-w-xl text-muted-foreground">{{ t('lp.reviews.note') }}</p>
        </div>
        <ul role="list" class="mt-12 grid gap-6 md:grid-cols-3">
          @for (key of keys; track key) {
            <li appReveal>
              <figure class="testimonial-card h-full rounded-2xl border border-border bg-card p-6">
                <div class="flex items-center justify-between">
                  <lucide-icon
                    [img]="Quote"
                    class="h-6 w-6 text-brand"
                    aria-hidden="true"
                  />
                  <span
                    class="rounded-full bg-brand-soft px-2.5 py-0.5 text-xs font-medium text-brand-soft-foreground"
                    >{{ t('lp.reviews.example') }}</span
                  >
                </div>
                <blockquote class="mt-4 text-foreground">
                  {{ t('lp.reviews.items.' + key + '.quote') }}
                </blockquote>
                <figcaption class="mt-4 text-sm text-muted-foreground">
                  {{ t('lp.reviews.items.' + key + '.who') }}
                </figcaption>
              </figure>
            </li>
          }
        </ul>
      </ng-container>
    </section>
  `,
})
export class TestimonialsComponent {
  protected readonly Quote = Quote;
  protected readonly keys = ['a', 'b', 'c'] as const;
}
