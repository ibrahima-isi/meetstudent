import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { LocaleService } from '@services/locale.service';
import { TranslocoDirective } from '@jsverse/transloco';
import { BadgeCheck, LucideAngularModule, Scale, Search } from 'lucide-angular';
import { RevealDirective } from '@shared/directives/reveal.directive';

@Component({
  selector: 'app-how-it-works',
  imports: [ReactiveFormsModule, TranslocoDirective, LucideAngularModule, RevealDirective, RouterLink],
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
            <form class="mt-5 flex flex-col gap-3" role="search" [attr.aria-label]="t('lp.steps.search.title')" (submit)="search($event, searchField)">
              <label for="discovery-type" class="text-sm font-medium text-foreground">{{ t('lp.steps.search.typeLabel') }}</label>
              <select id="discovery-type" class="field-input w-full min-w-0" [formControl]="resource">
                <option value="schools">{{ t('lp.steps.search.schools') }}</option>
                <option value="programs">{{ t('lp.steps.search.programs') }}</option>
                <option value="courses">{{ t('lp.steps.search.courses') }}</option>
              </select>
              <label for="discovery-query" class="text-sm font-medium text-foreground">{{ t('lp.steps.search.queryLabel') }}</label>
              <input #searchField id="discovery-query" type="search" class="field-input w-full min-w-0"
                [formControl]="query" (input)="updateSearchError(searchField.value)"
                [attr.aria-invalid]="searchError() ? 'true' : null"
                [attr.aria-describedby]="searchError() ? 'discovery-query-error' : null" />
              @if (searchError()) {
                <p id="discovery-query-error" role="alert" class="text-sm text-destructive">{{ t('lp.steps.search.unsupportedQuery') }}</p>
              }
              <button type="submit" class="btn btn-primary">{{ t('lp.steps.search.title') }}</button>
            </form>
          </div>
        </li>
        <li appReveal>
          <a
            class="step-card block rounded-2xl border border-border bg-card p-6 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
            [routerLink]="['/', lang(), 'schools']"
          >
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
          </a>
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
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  protected readonly lang = this.locale.active;
  protected readonly resource = new FormControl<'schools' | 'programs' | 'courses'>('schools', { nonNullable: true });
  protected readonly query = new FormControl('', { nonNullable: true });

  protected readonly searchError = signal(false);

  protected updateSearchError(value: string): void {
    if (this.searchError()) this.searchError.set(this.unsupportedQuery(value));
  }

  // These terms cannot travel through the existing name-search path contract.
  private unsupportedQuery(value: string): boolean {
    const term = value.trim();
    return /^\.{1,2}$/.test(term) || /[/\\%;]/.test(term);
  }

  protected search(event: Event, field: HTMLInputElement): void {
    event.preventDefault();
    const q = this.query.value.trim();
    this.searchError.set(this.unsupportedQuery(q));
    if (this.searchError()) {
      field.focus();
      return;
    }
    void this.router.navigate(['/', this.locale.active(), this.resource.value], { queryParams: q ? { q } : {} });
  }

  protected readonly Search = Search;
  protected readonly Scale = Scale;
  protected readonly BadgeCheck = BadgeCheck;
}
