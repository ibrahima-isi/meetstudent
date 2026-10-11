import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { ErrorStateComponent } from '@shared/components/error-state/error-state.component';
import { LocaleService } from '@services/locale.service';
import { SiteSettings, SiteSettingsService } from '@services/site-settings.service';

/**
 * The About text an admin wrote in the back office, in the language being read.
 * It is plain text: blank lines separate paragraphs, and it is only ever bound
 * as text, so nothing in it is interpreted as HTML.
 */
@Component({
  selector: 'app-about-page',
  imports: [TranslocoDirective, ErrorStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <section class="mx-auto max-w-3xl px-4 pb-20 pt-32 sm:px-6" *transloco="let t">
      <h1 class="text-4xl font-semibold tracking-tight text-foreground">{{ t('about.title') }}</h1>
      @switch (status()) {
        @case ('loading') {
          <p class="mt-8 text-muted-foreground" role="status">{{ t('common.loading') }}</p>
        }
        @case ('error') {
          <app-error-state [message]="t('about.error')" (retry)="load()" />
        }
        @default {
          <div data-about class="mt-8 space-y-5 text-lg leading-relaxed text-foreground/85">
            @for (paragraph of paragraphs(); track $index) {
              <p class="whitespace-pre-line">{{ paragraph }}</p>
            }
          </div>
        }
      }
    </section>
  `,
})
export class AboutPageComponent {
  private readonly service = inject(SiteSettingsService);
  private readonly locale = inject(LocaleService);

  protected readonly status = signal<'loading' | 'loaded' | 'error'>('loading');
  private readonly settings = signal<SiteSettings | null>(null);

  protected readonly paragraphs = computed(() => {
    const settings = this.settings();
    const text = settings ? (this.locale.active() === 'en' ? settings.aboutEn : settings.aboutFr) : '';
    return text
      .split(/\n\s*\n/)
      .map((paragraph) => paragraph.trim())
      .filter((paragraph) => paragraph !== '');
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.status.set('loading');
    this.service.get().subscribe({
      next: (settings) => {
        this.settings.set(settings);
        this.status.set('loaded');
      },
      error: () => this.status.set('error'),
    });
  }
}
