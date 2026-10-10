import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { MeshBackgroundComponent } from '../../components/mesh-background/mesh-background.component';

/** The brand side of the split-screen auth layout. The layout owns the back link. */
@Component({
  selector: 'app-auth-brand-panel',
  imports: [TranslocoDirective, MeshBackgroundComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'relative isolate flex flex-col justify-between gap-10 overflow-hidden p-12' },
  template: `
    <ng-container *transloco="let t">
      <app-mesh-background />
      <span class="text-xl font-semibold text-foreground">MeetStudent</span>

      <div class="max-w-md">
        <h2 class="text-3xl font-semibold tracking-tight text-foreground">{{ t('authPanel.title') }}</h2>
        <p class="mt-3 text-muted-foreground">{{ t('authPanel.text') }}</p>
        <div class="mt-8 flex flex-col gap-3" aria-hidden="true">
          <div data-decor aria-hidden="true" class="animate-float h-12 w-56 rounded-2xl border border-glass-border bg-glass backdrop-blur"></div>
          <div data-decor aria-hidden="true" class="animate-float ml-10 h-12 w-48 rounded-2xl border border-glass-border bg-glass backdrop-blur"></div>
          <div data-decor aria-hidden="true" class="animate-float h-12 w-64 rounded-2xl border border-glass-border bg-glass backdrop-blur"></div>
        </div>
      </div>

      <figure class="max-w-md rounded-2xl border border-glass-border bg-glass p-5 backdrop-blur">
        <span class="inline-block rounded-full bg-brand px-2 py-0.5 text-xs font-medium text-brand-foreground">{{ t('authPanel.example') }}</span>
        <blockquote class="mt-3 text-foreground">{{ t('authPanel.quote') }}</blockquote>
        <figcaption class="mt-2 text-sm text-muted-foreground">{{ t('authPanel.who') }}</figcaption>
      </figure>
    </ng-container>
  `,
})
export class AuthBrandPanelComponent {}
