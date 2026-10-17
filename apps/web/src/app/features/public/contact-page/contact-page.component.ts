import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { LucideAngularModule, Mail } from 'lucide-angular';
import { ErrorStateComponent } from '@shared/components/error-state/error-state.component';
import { SiteSettingsService } from '@services/site-settings.service';

/** The contact address an admin set in the back office, as a mail link. */
@Component({
  selector: 'app-contact-page',
  imports: [TranslocoDirective, LucideAngularModule, ErrorStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <section class="mx-auto max-w-3xl px-4 pb-20 pt-32 sm:px-6" *transloco="let t">
      <h1 class="text-4xl font-semibold tracking-tight text-foreground">{{ t('contact.title') }}</h1>
      <p class="mt-4 text-lg text-muted-foreground">{{ t('contact.intro') }}</p>
      @switch (status()) {
        @case ('loading') {
          <p class="mt-8 text-muted-foreground" role="status">{{ t('common.loading') }}</p>
        }
        @case ('error') {
          <app-error-state [message]="t('contact.error')" (retry)="load()" />
        }
        @default {
          <div class="mt-8 flex items-center gap-4 rounded-2xl border border-border bg-card p-6">
            <span class="inline-grid h-11 w-11 shrink-0 place-items-center rounded-full bg-brand-soft text-brand-soft-foreground">
              <lucide-icon [img]="Mail" class="h-5 w-5" aria-hidden="true" />
            </span>
            <div class="min-w-0">
              <p class="text-sm text-muted-foreground">{{ t('contact.emailLabel') }}</p>
              <a data-contact-email class="break-all text-lg font-medium text-brand hover:underline" [href]="mailHref(t('contact.applySubject', { school: school() }))">{{ email() }}</a>
            </div>
          </div>
        }
      }
    </section>
  `,
})
export class ContactPageComponent {
  private readonly service = inject(SiteSettingsService);

  protected readonly Mail = Mail;
  protected readonly status = signal<'loading' | 'loaded' | 'error'>('loading');
  protected readonly email = signal('');
  /** From `?school=` (the apply link of a school page); empty when absent. */
  readonly school = input<string>();
  private readonly subjectWanted = computed(() => !!this.school()?.trim());

  /** A bare mail link, plus a subject only when a school was named. */
  protected mailHref(subject: string): string {
    const base = 'mailto:' + this.email();
    return this.subjectWanted() ? `${base}?subject=${encodeURIComponent(subject)}` : base;
  }

  constructor() {
    this.load();
  }

  protected load(): void {
    this.status.set('loading');
    this.service.get().subscribe({
      next: (settings) => {
        this.email.set(settings.contactEmail);
        this.status.set('loaded');
      },
      error: () => this.status.set('error'),
    });
  }
}
