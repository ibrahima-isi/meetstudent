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
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Meta } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideAngularModule, AlertCircle, Lock } from 'lucide-angular';
import { TranslocoDirective } from '@jsverse/transloco';
import { AuthService } from '@services/auth.service';
import { LocaleService } from '@services/locale.service';

/** bcrypt, on the server, only reads this many bytes. */
const MAX_PASSWORD_BYTES = 72;

const maxBytes = (control: AbstractControl<string>): ValidationErrors | null =>
  new TextEncoder().encode(control.value ?? '').length > MAX_PASSWORD_BYTES ? { maxBytes: true } : null;

type State = 'form' | 'invalid' | 'expired';

/** The reset link keeps its token in the URL fragment, which no server or log ever receives. */
function tokenFrom(fragment: string | null): string | null {
  return (fragment && new URLSearchParams(fragment).get('token')) || null;
}

/**
 * Chooses the new password. The token is read once from the fragment into
 * memory, removed from the address bar and history right after the first
 * render, and never written anywhere else.
 */
@Component({
  selector: 'app-reset-password',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reset-password.component.html',
})
export class ResetPasswordComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly meta = inject(Meta);
  private readonly injector = inject(Injector);
  protected readonly locale = inject(LocaleService);

  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');

  /** Memory only. */
  readonly token = signal<string | null>(null);
  readonly state = signal<State>('invalid');
  readonly error = signal('');
  readonly pending = signal(false);
  private readonly serverErrors = signal<{ password?: boolean; confirmedPassword?: boolean }>({});

  readonly form = inject(NonNullableFormBuilder).group({
    password: ['', [Validators.required, Validators.minLength(8), maxBytes]],
    confirmPassword: ['', [Validators.required]],
  });

  protected readonly AlertCircle = AlertCircle;
  protected readonly Lock = Lock;

  constructor() {
    this.meta.updateTag({ name: 'robots', content: 'noindex,nofollow' });
    this.meta.updateTag({ name: 'referrer', content: 'no-referrer' });
    inject(DestroyRef).onDestroy(() => {
      this.meta.removeTag('name="robots"');
      this.meta.removeTag('name="referrer"');
    });

    const route = inject(ActivatedRoute);
    const fragment = route.snapshot.fragment;
    const token = tokenFrom(fragment);
    this.token.set(token);
    this.state.set(token ? 'form' : 'invalid');

    if (fragment) {
      // After the first render, not now: navigating while the router is still
      // activating this very route would cancel the navigation in flight.
      afterNextRender(
        () => {
          void this.router.navigate([], {
            relativeTo: route,
            queryParamsHandling: 'preserve',
            replaceUrl: true,
          });
        },
        { injector: this.injector },
      );
    }

    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.serverErrors.set({}));
  }

  /** Translation key of the message under the new-password field, if one is due. */
  protected passwordMessage(): string | null {
    const control = this.form.controls.password;
    if (this.serverErrors().password) {
      return 'auth.reset.passwordInvalid';
    }
    if (!control.touched) {
      return null;
    }
    if (control.hasError('maxBytes')) {
      return 'auth.reset.passwordTooLong';
    }
    return control.invalid ? 'auth.reset.passwordTooShort' : null;
  }

  protected confirmationMessage(): string | null {
    const { password, confirmPassword } = this.form.controls;
    const mismatch = confirmPassword.touched && password.value !== confirmPassword.value;
    return this.serverErrors().confirmedPassword || mismatch ? 'auth.register.passwordMismatch' : null;
  }

  protected handleSubmit(): void {
    this.form.markAllAsTouched();
    const token = this.token();
    if (!token || this.form.invalid || this.confirmationMessage()) {
      return;
    }

    this.error.set('');
    this.pending.set(true);
    const { password, confirmPassword } = this.form.getRawValue();

    this.auth.confirmPasswordReset(token, password, confirmPassword).subscribe({
      next: () => {
        this.token.set(null);
        this.pending.set(false);
        void this.router.navigate(['/', this.locale.active(), 'login'], { queryParams: { reset: '1' } });
      },
      error: (err: unknown) => {
        this.pending.set(false);
        this.fail(err);
      },
    });
  }

  private fail(err: unknown): void {
    const body = err instanceof HttpErrorResponse && err.status === 400 ? err.error : null;
    if (body && typeof body === 'object' && 'token' in body) {
      this.token.set(null);
      this.form.reset();
      this.state.set('expired');
      afterNextRender(() => this.heading()?.nativeElement.focus(), { injector: this.injector });
    } else if (body && typeof body === 'object' && ('password' in body || 'confirmedPassword' in body)) {
      this.serverErrors.set({ password: 'password' in body, confirmedPassword: 'confirmedPassword' in body });
    } else {
      this.error.set('auth.reset.failed');
    }
  }
}
