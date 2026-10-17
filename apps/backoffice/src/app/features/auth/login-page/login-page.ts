import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService, NotAdminError } from '@services/auth.service';
import { WEB_URL } from '@services/api-config';
import { Alert } from '@shared/components/alert/alert';

const NOT_ADMIN_MESSAGE = 'Accès réservé aux administrateurs.';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, Alert],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="flex min-h-screen items-center justify-center bg-muted px-4">
      <form
        [formGroup]="form"
        (ngSubmit)="submit()"
        class="w-full max-w-sm space-y-4 rounded-xl bg-card p-8 shadow"
      >
        <div>
          <h1 class="text-xl font-semibold text-foreground">MeetStudent</h1>
          <p class="text-sm text-muted-foreground">Espace d'administration</p>
        </div>

        <app-alert [message]="error()" />

        <div class="space-y-1">
          <label for="email" class="text-sm font-medium text-foreground">Adresse e-mail</label>
          <input
            id="email"
            type="email"
            formControlName="email"
            autocomplete="username"
            class="field-input "
          />
        </div>
        <div class="space-y-1">
          <label for="password" class="text-sm font-medium text-foreground">Mot de passe</label>
          <input
            id="password"
            type="password"
            formControlName="password"
            autocomplete="current-password"
            class="field-input "
          />
        </div>

        <a
          [href]="recoveryUrl"
          class="inline-flex min-h-11 items-center rounded text-sm font-medium text-brand hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
        >Mot de passe oublié ?</a>

        <button
          type="submit"
          [disabled]="loading()"
          class="btn btn-primary w-full"
        >
          {{ loading() ? 'Connexion…' : 'Se connecter' }}
        </button>
      </form>
    </main>
  `,
})
export class LoginPage {
  readonly recoveryUrl = `${inject(WEB_URL)}/fr/forgot-password`;
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly query = inject(ActivatedRoute).snapshot.queryParamMap;

  readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  readonly loading = signal(false);
  readonly error = signal(
    this.query.get('reason') === 'expired'
      ? 'Votre session a expiré. Reconnectez-vous pour continuer.'
      : this.query.get('reason') === 'forbidden' ? NOT_ADMIN_MESSAGE : '',
  );

  submit() {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, password } = this.form.getRawValue();
    this.loading.set(true);
    this.error.set('');

    this.auth.login(email, password).subscribe({
      next: () => this.router.navigateByUrl(this.returnUrl()),
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(this.messageFor(err));
      },
    });
  }

  /** Only same-app paths: `//host` and absolute URLs would be an open redirect. */
  private returnUrl(): string {
    const url = this.query.get('returnUrl');
    return url && url.startsWith('/') && !url.startsWith('//') ? url : '/';
  }

  private messageFor(err: unknown): string {
    if (err instanceof NotAdminError) return NOT_ADMIN_MESSAGE;
    if (err instanceof HttpErrorResponse && (err.status === 401 || err.status === 403)) {
      return 'Identifiants incorrects.';
    }
    return 'Impossible de joindre le serveur. Réessayez plus tard.';
  }
}
