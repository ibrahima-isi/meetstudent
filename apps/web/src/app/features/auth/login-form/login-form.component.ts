import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideAngularModule, Mail, Lock, LogIn, AlertCircle, CheckCircle } from 'lucide-angular';
import { TranslocoDirective } from '@jsverse/transloco';
import { AuthService } from '../../../services/auth.service';
import { LocaleService } from '@services/locale.service';

/** A translation key, and what to interpolate into it. */
interface Notice {
  key: string;
  params?: Record<string, string>;
}

@Component({
  selector: 'app-login-form',
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule, TranslocoDirective],
  templateUrl: './login-form.component.html'
})
export class LoginFormComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);

  loginForm: FormGroup;
  // Both hold translation keys, not sentences, so a language switch re-renders
  // them instead of leaving the language they were set in.
  error = signal('');
  success = signal<Notice | null>(null);
  isLoading = signal(false);

  readonly Mail = Mail;
  readonly Lock = Lock;
  readonly LogIn = LogIn;
  readonly AlertCircle = AlertCircle;
  readonly CheckCircle = CheckCircle;

  constructor() {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required]]
    });
  }

  handleSubmit() {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.error.set('');
    this.success.set(null);
    this.isLoading.set(true);

    const { email, password } = this.loginForm.value;

    this.authService.login(email, password).subscribe({
      next: () => {
        this.success.set({ key: 'auth.login.success' });
        this.isLoading.set(false);
        setTimeout(() => this.goTo('home'), 1000);
      },
      // The API's message is for logs only; the user reads the front's own text.
      error: () => {
        this.error.set('auth.login.failed');
        this.isLoading.set(false);
      }
    });
  }

  /** Navigations stay in the language the visitor is reading. */
  protected goTo(...segments: (string | number)[]): void {
    void this.router.navigate(['/', this.locale.active(), ...segments]);
  }

  handleOAuthLogin(provider: 'google' | 'microsoft') {
    this.error.set('');
    this.success.set(null);
    this.isLoading.set(true);

    // Simulate OAuth for now as requested
    setTimeout(() => {
      this.success.set({
        key: 'auth.login.oauthSuccess',
        params: { provider: provider === 'google' ? 'Google' : 'Microsoft' },
      });
      this.isLoading.set(false);
      setTimeout(() => this.goTo('home'), 1000);
    }, 1500);
  }
}
