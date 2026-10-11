import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Meta } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, AlertCircle, CheckCircle, Info, Mail } from 'lucide-angular';
import { TranslocoDirective } from '@jsverse/transloco';
import { AuthService } from '@services/auth.service';
import { LocaleService } from '@services/locale.service';

/**
 * Asks for a reset link. Whatever the server knows about the address, the
 * visitor reads the same neutral sentence: the page must not reveal whether
 * an account exists.
 */
@Component({
  selector: 'app-forgot-password',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './forgot-password.component.html',
})
export class ForgotPasswordComponent {
  private readonly auth = inject(AuthService);
  private readonly meta = inject(Meta);
  private readonly injector = inject(Injector);
  protected readonly locale = inject(LocaleService);

  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');

  readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
  });

  /** A translation key, so a language switch re-renders it. */
  readonly error = signal('');
  readonly sent = signal(false);
  readonly pending = signal(false);
  /** Null until the capabilities answer; only an explicit `false` shows the notice. */
  readonly emailEnabled = signal<boolean | null>(null);
  private readonly rejectedByServer = signal(false);

  protected readonly Mail = Mail;
  protected readonly AlertCircle = AlertCircle;
  protected readonly CheckCircle = CheckCircle;
  protected readonly Info = Info;

  constructor() {
    this.meta.updateTag({ name: 'robots', content: 'noindex,nofollow' });
    inject(DestroyRef).onDestroy(() => this.meta.removeTag('name="robots"'));

    this.auth
      .getCapabilities()
      .pipe(takeUntilDestroyed())
      .subscribe((capabilities) => this.emailEnabled.set(capabilities.emailEnabled));

    this.form.controls.email.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.rejectedByServer.set(false));
  }

  protected emailInvalid(): boolean {
    const email = this.form.controls.email;
    return this.rejectedByServer() || (email.touched && email.invalid);
  }

  protected handleSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.error.set('');
    this.pending.set(true);

    this.auth.requestPasswordReset(this.form.controls.email.value, this.locale.active()).subscribe({
      next: () => {
        this.pending.set(false);
        this.sent.set(true);
        this.focusHeading();
      },
      error: (err: unknown) => {
        this.pending.set(false);
        if (err instanceof HttpErrorResponse && err.status === 400) {
          this.rejectedByServer.set(true);
        } else {
          this.error.set('auth.forgot.failed');
        }
      },
    });
  }

  /** The form is replaced by the notice: keyboard and screen-reader users follow it. */
  private focusHeading(): void {
    afterNextRender(() => this.heading()?.nativeElement.focus(), { injector: this.injector });
  }
}
