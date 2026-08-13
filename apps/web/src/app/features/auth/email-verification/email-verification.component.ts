import { Component, computed, signal, inject, OnDestroy, ElementRef, ViewChildren, QueryList } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideAngularModule, Mail, CheckCircle, AlertCircle, RefreshCw } from 'lucide-angular';
import { LocaleService } from '@services/locale.service';
import { RegistrationFlowService } from '@services/registration-flow.service';

@Component({
  selector: 'app-email-verification',
  imports: [CommonModule, FormsModule, LucideAngularModule],
  templateUrl: './email-verification.component.html'
})
export class EmailVerificationComponent implements OnDestroy {
  private readonly registrationFlow = inject(RegistrationFlowService);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);

  /**
   * The address comes from the register step, which is in memory only. A reload
   * or a direct hit on /verify therefore arrives with nothing, and the screen
   * asks for the address instead of verifying a blank one.
   */
  readonly needsEmail = computed(() => this.registrationFlow.pendingEmail() === '');
  typedEmail = signal('');
  readonly email = computed(() => this.registrationFlow.pendingEmail() || this.typedEmail());

  code = signal<string[]>(['', '', '', '', '', '']);
  error = signal('');
  isLoading = signal(false);
  canResend = signal(false);
  countdown = signal(60);

  @ViewChildren('codeInput') codeInputs!: QueryList<ElementRef>;

  readonly Mail = Mail;
  readonly CheckCircle = CheckCircle;
  readonly AlertCircle = AlertCircle;
  readonly RefreshCw = RefreshCw;

  private timer: any;

  constructor() {
    this.startTimer();
  }

  ngOnDestroy() {
    this.clearTimer();
  }

  startTimer() {
    this.clearTimer();
    this.countdown.set(60);
    this.canResend.set(false);
    this.timer = setInterval(() => {
      const current = this.countdown();
      if (current > 0) {
        this.countdown.set(current - 1);
      } else {
        this.canResend.set(true);
        this.clearTimer();
      }
    }, 1000);
  }

  clearTimer() {
    if (this.timer) {
      clearInterval(this.timer);
    }
  }

  handleCodeChange(index: number, event: Event) {
    const inputElement = event.target as HTMLInputElement;
    const value = inputElement.value;
    
    if (value.length > 1) {
      inputElement.value = value.charAt(value.length - 1);
      return;
    }
    
    const newCode = [...this.code()];
    newCode[index] = value;
    this.code.set(newCode);
    this.error.set('');

    if (value && index < 5) {
      const nextInput = this.codeInputs.get(index + 1)?.nativeElement;
      nextInput?.focus();
    }

    if (newCode.every(digit => digit !== '') && index === 5) {
      this.handleVerify(newCode.join(''));
    }
  }

  handleKeyDown(index: number, event: KeyboardEvent) {
    if (event.key === 'Backspace' && !this.code()[index] && index > 0) {
      const prevInput = this.codeInputs.get(index - 1)?.nativeElement;
      prevInput?.focus();
    }
  }

  handlePaste(event: ClipboardEvent) {
    event.preventDefault();
    const clipboardData = event.clipboardData?.getData('text') || '';
    const pastedData = clipboardData.slice(0, 6);
    const newCode = pastedData.split('').concat(Array(6).fill('')).slice(0, 6);
    this.code.set(newCode);
    
    newCode.forEach((val, i) => {
       const input = this.codeInputs.get(i)?.nativeElement;
       if (input) input.value = val;
    });
    
    if (newCode.every(digit => digit !== '')) {
      this.handleVerify(newCode.join(''));
    }
  }

  handleVerify(verificationCode: string) {
    this.isLoading.set(true);
    this.error.set('');

    setTimeout(() => {
      const userData = localStorage.getItem(`user_${this.email()}`);
      if (!userData) {
        this.error.set('User not found. Please try registering again.');
        this.isLoading.set(false);
        return;
      }

      const user = JSON.parse(userData);
      
      if (verificationCode === user.verificationCode) {
        user.verified = true;
        localStorage.setItem(`user_${this.email()}`, JSON.stringify(user));
        this.isLoading.set(false);
        
        setTimeout(() => {
          this.backToLogin();
        }, 1500);
      } else {
        this.error.set('Invalid verification code. Please try again.');
        this.code.set(['', '', '', '', '', '']);
        this.isLoading.set(false);
        this.codeInputs.get(0)?.nativeElement?.focus();
      }
    }, 1000);
  }

  /**
   * Both exits lead here: a verified address and an abandoned one. Either way
   * the pending address is spent, so it is cleared before leaving — otherwise
   * a later visit to /verify would silently reuse it.
   */
  protected backToLogin(): void {
    this.registrationFlow.clear();
    void this.router.navigate(['/', this.locale.active(), 'login']);
  }

  handleResend() {
    if (!this.canResend()) return;
    
    this.startTimer();
    this.code.set(['', '', '', '', '', '']);
    this.error.set('');
    
    const userData = localStorage.getItem(`user_${this.email()}`);
    if (userData) {
      const user = JSON.parse(userData);
      user.verificationCode = Math.floor(100000 + Math.random() * 900000).toString();
      localStorage.setItem(`user_${this.email()}`, JSON.stringify(user));
    }
  }

  get verificationCode(): string {
    const userData = localStorage.getItem(`user_${this.email()}`);
    return userData ? JSON.parse(userData).verificationCode : '------';
  }
}
