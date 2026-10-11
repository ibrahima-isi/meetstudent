import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { LucideAngularModule, Mail, X } from 'lucide-angular';
import { TranslocoDirective } from '@jsverse/transloco';
import { AuthService } from '@services/auth.service';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';

type Feedback = 'sent' | 'limited' | 'alreadyVerified' | 'failed';

const DISMISSED_KEY = 'email_verification_banner_dismissed';

/**
 * Reminds a signed-in user that their address is not confirmed yet, and
 * offers to send the email again. Optional by design: the account works
 * without it, so the banner can be dismissed for the rest of the session.
 */
@Component({
  selector: 'app-email-verification-banner',
  imports: [LucideAngularModule, TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ng-container *transloco="let t">
      @if (visible()) {
        <section
          data-testid="email-verification-banner"
          [attr.aria-label]="t('emailBanner.title')"
          class="mt-[4.75rem] mx-4 sm:mx-auto max-w-6xl rounded-xl border border-amber-200 bg-amber-50 dark:bg-amber-950/40 dark:border-amber-900"
        >
          <div class="px-4 py-3 flex flex-wrap items-center gap-x-4 gap-y-2">
            <lucide-icon [img]="Mail" class="w-5 h-5 text-amber-700 dark:text-amber-300 flex-shrink-0" aria-hidden="true"></lucide-icon>
            <div class="flex-1 min-w-[16rem]">
              <p class="font-medium text-amber-900 dark:text-amber-100">{{ t('emailBanner.title') }}</p>
              <p class="text-sm text-amber-800 dark:text-amber-200">{{ t('emailBanner.text') }}</p>
              <p role="status" class="text-sm font-medium text-amber-900 dark:text-amber-100">
                @if (feedback(); as key) {
                  {{ t('emailBanner.' + key) }}
                }
              </p>
            </div>
            @if (feedback() !== 'alreadyVerified') {
              <button type="button" (click)="resend()" [disabled]="pending()" class="btn btn-primary">
                {{ t(pending() ? 'emailBanner.resending' : 'emailBanner.resend') }}
              </button>
            }
            <button type="button" (click)="dismiss()" class="btn btn-ghost" [attr.aria-label]="t('emailBanner.dismiss')">
              <lucide-icon [img]="X" class="w-5 h-5" aria-hidden="true"></lucide-icon>
              <span class="sr-only">{{ t('emailBanner.dismiss') }}</span>
            </button>
          </div>
        </section>
      }
    </ng-container>
  `,
})
export class EmailVerificationBannerComponent {
  private readonly auth = inject(AuthService);
  private readonly tokens = inject(TokenService);
  private readonly locale = inject(LocaleService);
  private readonly document = inject(DOCUMENT);

  protected readonly Mail = Mail;
  protected readonly X = X;

  readonly feedback = signal<Feedback | null>(null);
  readonly pending = signal(false);
  private readonly userId = computed(() => this.tokens.user()?.id);
  /** Dismissals made in this page; the ones from earlier pages of the session are in sessionStorage. */
  private readonly dismissedIds = signal<ReadonlySet<number | undefined>>(new Set());
  private readonly dismissed = computed(() => {
    const id = this.userId();
    return this.dismissedIds().has(id) || readDismissed(id);
  });
  private readonly emailEnabled = signal(false);
  private asked = false;

  /** Signed in with an address still to confirm; whether mail can be sent is a separate question. */
  private readonly pendingConfirmation = computed(
    () => this.tokens.isAuthenticated() && this.tokens.user()?.emailVerified === false,
  );

  protected readonly visible = computed(
    () =>
      !this.dismissed() &&
      this.tokens.isAuthenticated() &&
      ((this.pendingConfirmation() && this.emailEnabled()) || this.feedback() === 'alreadyVerified'),
  );

  constructor() {
    // Another account on the same tab starts afresh.
    let previous = this.userId();
    effect(() => {
      const id = this.userId();
      if (id !== previous) {
        previous = id;
        untracked(() => this.feedback.set(null));
      }
    });

    effect(() => {
      if (this.pendingConfirmation() && !this.dismissed() && !this.asked) {
        this.asked = true;
        this.auth.getCapabilities().subscribe((c) => this.emailEnabled.set(c.emailEnabled));
      }
    });
  }

  protected resend(): void {
    this.pending.set(true);
    this.feedback.set(null);
    this.auth.resendEmailVerification(this.locale.active()).subscribe({
      next: () => {
        this.pending.set(false);
        this.feedback.set('sent');
      },
      error: (err: unknown) => {
        this.pending.set(false);
        const status = err instanceof HttpErrorResponse ? err.status : 0;
        if (status === 409) {
          this.feedback.set('alreadyVerified');
          this.auth.refreshCurrentUser().subscribe({ error: () => undefined });
        } else {
          this.feedback.set(status === 429 ? 'limited' : 'failed');
        }
      },
    });
  }

  protected dismiss(): void {
    const id = this.userId();
    this.dismissedIds.update((ids) => new Set(ids).add(id));
    try {
      sessionStorage.setItem(dismissedKey(id), '1');
    } catch {
      // Private mode or blocked storage: dismissed for this page only.
    }
    // The button that had focus is about to disappear.
    this.document.getElementById('main')?.focus();
  }
}

/** Per account, so one person's dismissal never hides another's reminder. */
function dismissedKey(id: number | undefined): string {
  return `${DISMISSED_KEY}:${id ?? ''}`;
}

function readDismissed(id: number | undefined): boolean {
  try {
    return typeof sessionStorage !== 'undefined' && sessionStorage.getItem(dismissedKey(id)) === '1';
  } catch {
    return false;
  }
}
