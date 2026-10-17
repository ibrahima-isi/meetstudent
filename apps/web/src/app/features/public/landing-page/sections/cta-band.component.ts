import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { LocaleService } from '@services/locale.service';
import { injectHydratedSession } from '@shared/hydrated-session';
import { MeshBackgroundComponent } from '@shared/components/mesh-background/mesh-background.component';

@Component({
  selector: 'app-cta-band',
  imports: [TranslocoDirective, RouterLink, MeshBackgroundComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': 'hostClass()' },
  styles: [
    `
      /* Pre-hydration only: the server always renders the band, the index.html script flags a stored session. */
      :host-context(.has-session):not(.hydrated) { display: none; }
    `,
  ],
  template: `
    @if (!signedIn()) {
      <section
        class="relative isolate mx-auto max-w-7xl overflow-hidden rounded-3xl border border-border px-8 py-16 text-center"
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
    }
  `,
})
export class CtaBandComponent {
  protected readonly lang = inject(LocaleService).active;

  /** The server always renders the band; see `injectHydratedSession`. */
  private readonly session = injectHydratedSession();
  protected readonly hydrated = this.session.hydrated;
  protected readonly signedIn = this.session.signedIn;
  /** No spacing at all for a signed-in user, so the hidden band leaves no gap. */
  protected readonly hostClass = computed(() => {
    const spacing = this.signedIn() ? '' : 'block px-4 py-20 sm:px-6 lg:px-8';
    return this.hydrated() ? `${spacing} hydrated`.trim() : spacing;
  });
}
