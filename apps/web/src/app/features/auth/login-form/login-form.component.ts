import { Component, output, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { LucideAngularModule, Mail, Lock, LogIn, AlertCircle, CheckCircle } from 'lucide-angular';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-login-form',
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule],
  templateUrl: './login-form.component.html'
})
export class LoginFormComponent {
  onSwitchToRegister = output<void>();
  onLoginSuccess = output<void>();

  private fb = inject(FormBuilder);
  private authService = inject(AuthService);

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
        setTimeout(() => this.onLoginSuccess.emit(), 1000);
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Login failed. Please try again.');
        this.isLoading.set(false);
      }
    });
  }

  handleOAuthLogin(provider: 'google' | 'microsoft') {
    this.error.set('');
    this.success.set('');
    this.isLoading.set(true);

    // Simulate OAuth for now as requested
    setTimeout(() => {
      this.success.set(`Successfully logged in with ${provider === 'google' ? 'Google' : 'Microsoft'}!`);
      this.isLoading.set(false);
      setTimeout(() => this.onLoginSuccess.emit(), 1000);
    }, 1500);
  }
}
