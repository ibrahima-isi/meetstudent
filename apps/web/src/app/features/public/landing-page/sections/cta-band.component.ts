import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { LocaleService } from '@services/locale.service';
import { MeshBackgroundComponent } from '@shared/components/mesh-background/mesh-background.component';

@Component({
  selector: 'app-cta-band',
  imports: [TranslocoDirective, RouterLink, MeshBackgroundComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block py-20' },
  template: `
    <section
      class="relative isolate mx-4 max-w-7xl overflow-hidden rounded-3xl border border-border px-8 py-16 text-center sm:mx-6 lg:mx-auto"
      *transloco="let t"
    >
      <app-mesh-background />
      <h2 class="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
        {{ t('lp.cta.title') }}
      </h2>
      <p class="mx-auto mt-4 max-w-xl text-lg text-foreground/75">{{ t('lp.cta.text') }}</p>
      <div class="mt-8 flex flex-wrap items-center justify-center gap-3">
        <a class="btn btn-primary btn-lg" [routerLink]="['/', lang(), 'register']">
          {{ t('lp.cta.primary') }}
        </a>
        <a class="btn btn-secondary btn-lg" [routerLink]="['/', lang(), 'schools']">
          {{ t('lp.cta.secondary') }}
        </a>
      </div>
    </section>
  `,
})
export class CtaBandComponent {
  protected readonly lang = inject(LocaleService).active;
}
