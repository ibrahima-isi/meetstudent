import { Component, output, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { LucideAngularModule, Mail, Lock, User as UserIcon, AlertCircle, UserPlus, GraduationCap, MapPin, Users, BookOpen } from 'lucide-angular';
import { AuthService } from '../../../services/auth.service';

const SENEGAL_SPECIALTIES = [
  'Mathématiques', 'Physique-Chimie', 'Sciences de la Vie et de la Terre (SVT)',
  'Français', 'Anglais', 'Histoire-Géographie', 'Philosophie',
  'Éducation Physique et Sportive (EPS)', 'Sciences Économiques et Sociales (SES)',
  'Informatique', 'Arts Plastiques', 'Musique', 'Arabe', 'Espagnol', 'Allemand',
  'Sciences Physiques', 'Biologie', 'Chimie', 'Lettres Modernes', 'Lettres Classiques',
];

@Component({
  selector: 'app-register-form',
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule],
  templateUrl: './register-form.component.html'
})
export class RegisterFormComponent {
  onSwitchToLogin = output<void>();
  onRegisterSuccess = output<string>();

  private fb = inject(FormBuilder);
  private authService = inject(AuthService);

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

  handleNextStep() {
    this.error.set('');

    if (!this.userType()) {
      this.error.set('Please select a user type.');
      return;
    }

    if (this.step1Form.invalid) {
      this.step1Form.markAllAsTouched();
      this.error.set('Please complete all required fields.');
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
      this.error.set('Passwords do not match.');
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
    const userData = {
      firstname: this.step1Form.value.firstname,
      lastname: this.step1Form.value.lastname,
      email,
      password,
      confirmedPassword: confirmPassword
    };

    this.authService.register(userData).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.onRegisterSuccess.emit(email);
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Registration failed.');
        this.isLoading.set(false);
      }
    });
  }
}
