import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { MeshBackgroundComponent } from '@shared/components/mesh-background/mesh-background.component';

@Component({
  selector: 'app-cta-band',
  imports: [TranslocoDirective, RouterLink, MeshBackgroundComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': 'hostClass()' },
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
  private readonly token = inject(TokenService);
  protected readonly lang = inject(LocaleService).active;

  /**
   * The session lives in localStorage, which the server cannot read: the server
   * always renders the band. Reading the token before the first client render
   * would break hydration, so `hydrated` flips after it (same rule as the
   * dock navbar, including the pure client bootstrap exception).
   */
  private readonly hydrated = signal(
    isPlatformBrowser(inject(PLATFORM_ID)) &&
      !inject(DOCUMENT).querySelector('[ng-server-context]'),
  );
  protected readonly signedIn = computed(() => this.hydrated() && this.token.isAuthenticated());
  /** No spacing at all for a signed-in user, so the hidden band leaves no gap. */
  protected readonly hostClass = computed(() =>
    this.signedIn() ? '' : 'block px-4 py-20 sm:px-6 lg:px-8',
  );

  constructor() {
    afterNextRender(() => this.hydrated.set(true));
  }
}
