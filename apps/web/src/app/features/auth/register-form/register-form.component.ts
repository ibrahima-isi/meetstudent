import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideAngularModule, Mail, Lock, User as UserIcon, AlertCircle, UserPlus, GraduationCap, MapPin, Users, BookOpen } from 'lucide-angular';
import { TranslocoDirective } from '@jsverse/transloco';
import { AuthService, RegisterPayload } from '../../../services/auth.service';
import { LocaleService } from '@services/locale.service';

const SENEGAL_SPECIALTIES = [
  'Mathématiques', 'Physique-Chimie', 'Sciences de la Vie et de la Terre (SVT)',
  'Français', 'Anglais', 'Histoire-Géographie', 'Philosophie',
  'Éducation Physique et Sportive (EPS)', 'Sciences Économiques et Sociales (SES)',
  'Informatique', 'Arts Plastiques', 'Musique', 'Arabe', 'Espagnol', 'Allemand',
  'Sciences Physiques', 'Biologie', 'Chimie', 'Lettres Modernes', 'Lettres Classiques',
];

@Component({
  selector: 'app-register-form',
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule, TranslocoDirective],
  templateUrl: './register-form.component.html'
})
export class RegisterFormComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);

  readonly Mail = Mail;
  readonly Lock = Lock;
  readonly UserIcon = UserIcon;
  readonly AlertCircle = AlertCircle;
  readonly UserPlus = UserPlus;
  readonly GraduationCap = GraduationCap;
  readonly MapPin = MapPin;
  readonly Users = Users;
  readonly BookOpen = BookOpen;

  step = signal(1);
  userType = signal<'student' | 'teacher' | ''>('');
  filteredSpecialties = signal<string[]>([]);
  showSuggestions = signal(false);
  /** A translation key, not a sentence, so a language switch re-renders it. */
  error = signal('');
  isLoading = signal(false);

  step1Form: FormGroup;
  step2Form: FormGroup;

  constructor() {
    this.step1Form = this.fb.group({
      firstname: ['', Validators.required],
      lastname: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      bacType: [''],
      collegeLevel: [''],
      specialty: [''],
      town: ['', Validators.required]
    });

    this.step2Form = this.fb.group({
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', Validators.required],
      terms: [false, Validators.requiredTrue]
    });

    this.step1Form.get('specialty')?.valueChanges.subscribe(value => {
      this.handleSpecialtyChange(value || '');
    });
  }

  setUserType(type: 'student' | 'teacher') {
    this.userType.set(type);
    if (type === 'student') {
      this.step1Form.get('bacType')?.setValidators(Validators.required);
      this.step1Form.get('collegeLevel')?.setValidators(Validators.required);
      this.step1Form.get('specialty')?.clearValidators();
    } else {
      this.step1Form.get('specialty')?.setValidators(Validators.required);
      this.step1Form.get('bacType')?.clearValidators();
      this.step1Form.get('collegeLevel')?.clearValidators();
    }
    this.step1Form.get('bacType')?.updateValueAndValidity();
    this.step1Form.get('collegeLevel')?.updateValueAndValidity();
    this.step1Form.get('specialty')?.updateValueAndValidity();
  }

  handleSpecialtyChange(value: string) {
    if (value.trim()) {
      const filtered = SENEGAL_SPECIALTIES.filter(spec =>
        spec.toLowerCase().includes(value.toLowerCase())
      );
      this.filteredSpecialties.set(filtered);
      this.showSuggestions.set(true);
    } else {
      this.filteredSpecialties.set([]);
      this.showSuggestions.set(false);
    }
  }

  selectSpecialty(spec: string) {
    this.step1Form.get('specialty')?.setValue(spec);
    this.showSuggestions.set(false);
    this.filteredSpecialties.set([]);
  }

  /** Navigations stay in the language the visitor is reading. */
  protected goTo(...segments: (string | number)[]): void {
    void this.router.navigate(['/', this.locale.active(), ...segments]);
  }

  handleNextStep() {
    this.error.set('');

    if (!this.userType()) {
      this.error.set('auth.register.userTypeRequired');
      return;
    }

    if (this.step1Form.invalid) {
      this.step1Form.markAllAsTouched();
      this.error.set('auth.register.incomplete');
      return;
    }

    this.step.set(2);
  }

  handleSubmit() {
    this.error.set('');
    
    if (this.step2Form.invalid) {
      this.step2Form.markAllAsTouched();
      return;
    }

    const { password, confirmPassword } = this.step2Form.value;

    if (password !== confirmPassword) {
      this.error.set('auth.register.passwordMismatch');
      return;
    }

    this.isLoading.set(true);

    const email = this.step1Form.value.email;
    // Payload must match the backend RegisterRequest exactly:
    // firstname, lastname, email, password, confirmedPassword, birthday, qualification.
    // `role` is deliberately NOT sent — registration always creates a STUDENT, and
    // role changes go through PATCH /users/{id}/role (admin-only).
    // The step1 fields bacType / collegeLevel / specialty / town have no backend
    // counterpart and are dropped rather than silently posted and ignored.
    const userData: RegisterPayload = {
      firstname: this.step1Form.value.firstname,
      lastname: this.step1Form.value.lastname,
      email,
      password,
      confirmedPassword: confirmPassword
    };

    this.authService.register(userData).subscribe({
      next: () => {
        this.isLoading.set(false);
        // No email verification yet (the backend has none): the account is
        // usable, so go straight to login with a flag for the success notice.
        void this.router.navigate(['/', this.locale.active(), 'login'], {
          queryParams: { registered: '1' },
        });
      },
      // The API's message is for logs only; the user reads the front's own text.
      error: () => {
        this.error.set('auth.register.failed');
        this.isLoading.set(false);
      }
    });
  }
}
