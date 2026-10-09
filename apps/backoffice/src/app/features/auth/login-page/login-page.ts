import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService, NotAdminError } from '@services/auth.service';
import { Alert } from '@shared/components/alert/alert';

const NOT_ADMIN_MESSAGE = 'Accès réservé aux administrateurs.';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, Alert],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="flex min-h-screen items-center justify-center bg-slate-100 px-4">
      <form
        [formGroup]="form"
        (ngSubmit)="submit()"
        class="w-full max-w-sm space-y-4 rounded-xl bg-white p-8 shadow"
      >
        <div>
          <h1 class="text-xl font-semibold text-slate-900">MeetStudent</h1>
          <p class="text-sm text-slate-500">Espace d'administration</p>
        </div>

        <app-alert [message]="error()" />

        <div class="space-y-1">
          <label for="email" class="text-sm font-medium text-slate-700">Adresse e-mail</label>
          <input
            id="email"
            type="email"
            formControlName="email"
            autocomplete="username"
            class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>
        <div class="space-y-1">
          <label for="password" class="text-sm font-medium text-slate-700">Mot de passe</label>
          <input
            id="password"
            type="password"
            formControlName="password"
            autocomplete="current-password"
            class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>

        <button
          type="submit"
          [disabled]="loading()"
          class="w-full rounded-md bg-indigo-600 px-3 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-60"
        >
          {{ loading() ? 'Connexion…' : 'Se connecter' }}
        </button>
      </form>
    </main>
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly query = inject(ActivatedRoute).snapshot.queryParamMap;

  readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  readonly loading = signal(false);
  readonly error = signal(this.query.get('reason') === 'forbidden' ? NOT_ADMIN_MESSAGE : '');

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
