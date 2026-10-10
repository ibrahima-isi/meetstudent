import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { BadgeCheck, LucideAngularModule, Scale, Search } from 'lucide-angular';
import { RevealDirective } from '@shared/directives/reveal.directive';

@Component({
  selector: 'app-how-it-works',
  imports: [TranslocoDirective, LucideAngularModule, RevealDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  styles: [
    `
      @media (hover: hover) {
        .step-card {
          transform: perspective(900px) rotateX(3deg);
          transition:
            transform 0.3s ease,
            box-shadow 0.3s ease;
        }
        .step-card:hover {
          transform: perspective(900px) rotateX(0) translateY(-4px);
          box-shadow: var(--shadow-glow);
        }
      }
      @media (prefers-reduced-motion: reduce) {
        .step-card,
        .step-card:hover {
          transform: none;
          transition: none;
        }
      }
    `,
  ],
  template: `
    <section
      id="how-it-works"
      class="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8"
      *transloco="let t"
    >
      <div class="text-center">
        <h2 class="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
          {{ t('lp.steps.title') }}
        </h2>
        <p class="mx-auto mt-3 max-w-xl text-muted-foreground">{{ t('lp.steps.subtitle') }}</p>
      </div>
      <ol role="list" class="mt-12 grid gap-6 md:grid-cols-3">
        <li appReveal>
          <div class="step-card rounded-2xl border border-border bg-card p-6">
            <div class="flex items-center justify-between">
              <span
                class="chip flex h-12 w-12 items-center justify-center rounded-xl bg-brand-soft text-brand-soft-foreground"
              >
                <lucide-icon [img]="Search" class="h-6 w-6" aria-hidden="true" />
              </span>
              <span
                class="badge flex h-8 w-8 items-center justify-center rounded-full bg-brand text-sm font-semibold text-brand-foreground"
                aria-hidden="true"
                >1</span
              >
            </div>
            <h3 class="mt-5 text-lg font-semibold text-foreground">
              {{ t('lp.steps.search.title') }}
            </h3>
            <p class="mt-2 text-muted-foreground">{{ t('lp.steps.search.text') }}</p>
          </div>
        </li>
        <li appReveal>
          <div class="step-card rounded-2xl border border-border bg-card p-6">
            <div class="flex items-center justify-between">
              <span
                class="chip flex h-12 w-12 items-center justify-center rounded-xl bg-brand-soft text-brand-soft-foreground"
              >
                <lucide-icon [img]="Scale" class="h-6 w-6" aria-hidden="true" />
              </span>
              <span
                class="badge flex h-8 w-8 items-center justify-center rounded-full bg-brand text-sm font-semibold text-brand-foreground"
                aria-hidden="true"
                >2</span
              >
            </div>
            <h3 class="mt-5 text-lg font-semibold text-foreground">
              {{ t('lp.steps.compare.title') }}
            </h3>
            <p class="mt-2 text-muted-foreground">{{ t('lp.steps.compare.text') }}</p>
          </div>
        </li>
        <li appReveal>
          <div class="step-card rounded-2xl border border-border bg-card p-6">
            <div class="flex items-center justify-between">
              <span
                class="chip flex h-12 w-12 items-center justify-center rounded-xl bg-brand-soft text-brand-soft-foreground"
              >
                <lucide-icon [img]="BadgeCheck" class="h-6 w-6" aria-hidden="true" />
              </span>
              <span
                class="badge flex h-8 w-8 items-center justify-center rounded-full bg-brand text-sm font-semibold text-brand-foreground"
                aria-hidden="true"
                >3</span
              >
            </div>
            <h3 class="mt-5 text-lg font-semibold text-foreground">
              {{ t('lp.steps.choose.title') }}
            </h3>
            <p class="mt-2 text-muted-foreground">{{ t('lp.steps.choose.text') }}</p>
          </div>
        </li>
      </ol>
    </section>
  `,
})
export class HowItWorksComponent {
  protected readonly Search = Search;
  protected readonly Scale = Scale;
  protected readonly BadgeCheck = BadgeCheck;
}
