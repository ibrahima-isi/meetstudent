import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideAngularModule, Mail } from 'lucide-angular';
import { TranslocoDirective } from '@jsverse/transloco';
import { AuthService } from '@services/auth.service';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { UserService } from '@services/user.service';

type Feedback = 'requested' | 'sent' | 'limited' | 'failed' | 'unavailable' | 'cancelled' | 'alreadyVerified';
type Field = 'newEmail' | 'currentPassword';

/**
 * The account's email address on the profile: whether it is confirmed (with a way to get the
 * email again), and the two-step change. A new address is only *pending* until the link mailed
 * to it is opened; the current one keeps working meanwhile, so the section shows both.
 */
@Component({
  selector: 'app-profile-email-section',
  imports: [ReactiveFormsModule, LucideAngularModule, TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section *transloco="let t" data-testid="profile-email-section" class="space-y-4">
      <div>
        <h3 class="block text-foreground mb-2 font-medium">{{ t('profile.emailSection.title') }}</h3>
        <div class="flex flex-wrap items-center gap-3 text-foreground">
          <lucide-icon [img]="Mail" class="w-5 h-5 shrink-0 text-muted-foreground" aria-hidden="true"></lucide-icon>
          <span class="min-w-0 break-all">{{ email() }}</span>
          @if (verified()) {
            <span class="rounded-full bg-green-100 px-2.5 py-0.5 text-sm font-medium text-green-800 dark:bg-green-950/60 dark:text-green-200">{{ t('profile.emailSection.verified') }}</span>
          } @else {
            <span class="rounded-full bg-amber-100 px-2.5 py-0.5 text-sm font-medium text-amber-900 dark:bg-amber-950/60 dark:text-amber-200">{{ t('profile.emailSection.unverified') }}</span>
          }
        </div>
        @if (!verified()) {
          <p class="mt-2 text-muted-foreground">{{ t('profile.emailSection.unverifiedHint') }}</p>
          @if (emailEnabled()) {
            <button type="button" (click)="resendVerification()" [disabled]="busy()" class="btn btn-secondary mt-3">
              {{ t('profile.emailSection.resendVerification') }}
            </button>
          }
        }
      </div>

      @if (pending(); as address) {
        <div data-testid="email-pending" class="rounded-lg border border-indigo-200 bg-indigo-50 p-4 dark:border-indigo-900 dark:bg-indigo-950/40">
          <p class="font-medium text-foreground">{{ t('profile.emailSection.pendingTitle') }}</p>
          <p class="mt-1 text-muted-foreground break-words">{{ t('profile.emailSection.pendingText', { email: address }) }}</p>
          <div class="mt-3 flex flex-wrap gap-2">
            <button type="button" (click)="resendPending()" [disabled]="busy()" class="btn btn-secondary">{{ t('profile.emailSection.resendPending') }}</button>
            <button type="button" (click)="cancelPending()" [disabled]="busy()" class="btn btn-ghost">{{ t('profile.emailSection.cancelPending') }}</button>
          </div>
        </div>
      }

      @if (emailEnabled() === false) {
        <p class="text-muted-foreground">{{ t('profile.emailSection.unavailable') }}</p>
      } @else if (emailEnabled()) {
        @if (!changing()) {
          <button type="button" (click)="startChange()" class="btn btn-secondary">{{ t('profile.emailSection.change') }}</button>
        } @else {
          <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="space-y-4 rounded-lg border border-border p-4">
            <div>
              <label for="email-new" class="block text-foreground mb-2 font-medium">{{ t('profile.emailSection.newEmail') }}</label>
              <input
                id="email-new"
                type="email"
                autocomplete="email"
                formControlName="newEmail"
                data-testid="email-new"
                class="field-input"
              />
              @if (error('newEmail'); as message) {
                <p class="mt-1 text-red-600 dark:text-red-400" role="alert" data-testid="email-error-newEmail">{{ message.key ? t(message.key) : message.text }}</p>
              }
            </div>
            <div>
              <label for="email-password" class="block text-foreground mb-2 font-medium">{{ t('profile.emailSection.currentPassword') }}</label>
              <input
                id="email-password"
                type="password"
                autocomplete="current-password"
                formControlName="currentPassword"
                data-testid="email-password"
                class="field-input"
              />
              <p class="text-muted-foreground mt-1">{{ t('profile.emailSection.currentPasswordHint') }}</p>
              @if (error('currentPassword'); as message) {
                <p class="mt-1 text-red-600 dark:text-red-400" role="alert" data-testid="email-error-currentPassword">{{ message.key ? t(message.key) : message.text }}</p>
              }
            </div>
            <div class="flex flex-wrap gap-2">
              <button type="submit" [disabled]="busy()" class="btn btn-primary">
                {{ t(submitting() ? 'profile.emailSection.sending' : 'profile.emailSection.send') }}
              </button>
              <button type="button" (click)="cancelChange()" [disabled]="submitting()" class="btn btn-ghost">{{ t('profile.emailSection.cancel') }}</button>
            </div>
          </form>
        }
      }

      <p role="status" class="font-medium text-foreground">
        @if (feedback(); as key) {
          {{ t('profile.emailSection.feedback.' + key) }}
        }
      </p>
    </section>
  `,
})
export class ProfileEmailSectionComponent {
  private readonly auth = inject(AuthService);
  private readonly users = inject(UserService);
  private readonly tokens = inject(TokenService);
  private readonly locale = inject(LocaleService);

  protected readonly Mail = Mail;

  protected readonly email = computed(() => this.tokens.user()?.email ?? '');
  /** An older payload without the flag counts as confirmed, like the banner. */
  protected readonly verified = computed(() => this.tokens.user()?.emailVerified !== false);
  /** `null` until the server has answered; mail off means the address cannot be changed. */
  protected readonly emailEnabled = signal<boolean | null>(null);
  protected readonly pending = signal<string | null>(null);
  protected readonly changing = signal(false);
  protected readonly submitting = signal(false);
  protected readonly feedback = signal<Feedback | null>(null);
  /** Messages from the API's 400 body, by field. */
  private readonly serverErrors = signal<Partial<Record<Field, string>>>({});
  private readonly resending = signal(false);
  protected readonly busy = computed(() => this.submitting() || this.resending());

  protected readonly form = inject(NonNullableFormBuilder).group({
    newEmail: ['', [Validators.required, Validators.email]],
    currentPassword: ['', Validators.required],
  });

  constructor() {
    this.auth.getCapabilities().subscribe((c) => this.emailEnabled.set(c.emailEnabled));
    const id = this.tokens.user()?.id;
    if (id !== undefined) {
      this.users.getPendingEmail(id).subscribe({
        next: (address) => this.pending.set(address),
        error: () => undefined, // not knowing about a pending change is not worth an error banner
      });
    }
    // Editing a field drops the message the API attached to it.
    for (const field of ['newEmail', 'currentPassword'] as const) {
      this.form.controls[field].valueChanges.subscribe(() => {
        if (this.serverErrors()[field] !== undefined) {
          this.serverErrors.update(({ [field]: _dropped, ...rest }) => rest);
        }
      });
    }
  }

  /** The API's message for a field, else the form's own validation message (a translation key). */
  protected error(field: Field): { key?: string; text?: string } | null {
    const fromServer = this.serverErrors()[field];
    if (fromServer) {
      return { text: fromServer };
    }
    const control = this.form.controls[field];
    if (!control.touched || !control.errors) {
      return null;
    }
    if (control.errors['required']) return { key: 'profile.errors.required' };
    if (control.errors['email']) return { key: 'profile.errors.email' };
    return null;
  }

  protected startChange(): void {
    this.feedback.set(null);
    this.changing.set(true);
  }

  protected cancelChange(): void {
    this.form.reset();
    this.serverErrors.set({});
    this.changing.set(false);
  }

  protected submit(): void {
    const id = this.tokens.user()?.id;
    if (this.submitting() || id === undefined) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { newEmail, currentPassword } = this.form.getRawValue();
    this.feedback.set(null);
    this.serverErrors.set({});
    this.submitting.set(true);
    this.users.requestEmailChange(id, newEmail.trim(), currentPassword, this.locale.active()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.pending.set(newEmail.trim());
        this.form.reset();
        this.changing.set(false);
        this.feedback.set('requested');
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        const status = err instanceof HttpErrorResponse ? err.status : 0;
        if (status === 400 && this.applyFieldErrors((err as HttpErrorResponse).error)) {
          return;
        }
        this.feedback.set(status === 429 ? 'limited' : status === 503 ? 'unavailable' : 'failed');
      },
    });
  }

  protected resendPending(): void {
    const id = this.tokens.user()?.id;
    if (id === undefined) {
      return;
    }
    this.resending.set(true);
    this.feedback.set(null);
    this.users.resendEmailChange(id, this.locale.active()).subscribe({
      next: () => this.done('sent'),
      error: (err: unknown) => {
        const status = err instanceof HttpErrorResponse ? err.status : 0;
        if (status === 409) {
          this.pending.set(null); // already confirmed or cancelled elsewhere
        }
        this.done(status === 409 ? null : status === 429 ? 'limited' : status === 503 ? 'unavailable' : 'failed');
      },
    });
  }

  protected cancelPending(): void {
    const id = this.tokens.user()?.id;
    if (id === undefined) {
      return;
    }
    this.resending.set(true);
    this.feedback.set(null);
    this.users.cancelEmailChange(id).subscribe({
      next: () => {
        this.pending.set(null);
        this.done('cancelled');
      },
      error: () => this.done('failed'),
    });
  }

  protected resendVerification(): void {
    this.resending.set(true);
    this.feedback.set(null);
    this.auth.resendEmailVerification(this.locale.active()).subscribe({
      next: () => this.done('sent'),
      error: (err: unknown) => {
        const status = err instanceof HttpErrorResponse ? err.status : 0;
        if (status === 409) {
          this.auth.refreshCurrentUser().subscribe({ error: () => undefined });
          this.done('alreadyVerified');
        } else {
          this.done(status === 429 ? 'limited' : status === 503 ? 'unavailable' : 'failed');
        }
      },
    });
  }

  private done(feedback: Feedback | null): void {
    this.resending.set(false);
    this.feedback.set(feedback);
  }

  /** Returns whether any message landed; anything else falls back to the generic failure. */
  private applyFieldErrors(body: unknown): boolean {
    if (!body || typeof body !== 'object') {
      return false;
    }
    const messages: Partial<Record<Field, string>> = {};
    for (const field of ['newEmail', 'currentPassword'] as const) {
      const message = (body as Record<string, unknown>)[field];
      if (typeof message === 'string') {
        messages[field] = message;
      }
    }
    if (Object.keys(messages).length === 0) {
      return false;
    }
    this.serverErrors.set(messages);
    return true;
  }
}
