import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideAngularModule, Mail, Lock, LogIn, AlertCircle, CheckCircle } from 'lucide-angular';
import { AuthService } from '../../../services/auth.service';
import { LocaleService } from '@services/locale.service';

@Component({
  selector: 'app-login-form',
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule],
  templateUrl: './login-form.component.html'
})
export class LoginFormComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);

  loginForm: FormGroup;
  error = signal('');
  success = signal('');
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
    this.success.set('');
    this.isLoading.set(true);

    const { email, password } = this.loginForm.value;

    this.authService.login(email, password).subscribe({
      next: () => {
        this.success.set('Login successful! Welcome back.');
        this.isLoading.set(false);
        setTimeout(() => this.goTo('home'), 1000);
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Login failed. Please try again.');
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
    this.success.set('');
    this.isLoading.set(true);

    // Simulate OAuth for now as requested
    setTimeout(() => {
      this.success.set(`Successfully logged in with ${provider === 'google' ? 'Google' : 'Microsoft'}!`);
      this.isLoading.set(false);
      setTimeout(() => this.goTo('home'), 1000);
    }, 1500);
  }
}
