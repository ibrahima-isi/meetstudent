import { Component, signal, OnInit, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideAngularModule, ArrowLeft, User as UserIcon, Mail, Phone, MapPin, GraduationCap, Book, Save, Heart, Briefcase } from 'lucide-angular';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { ProfileUpdate, UserService } from '@services/user.service';
import { WishlistService } from '@services/wishlist.service';
import { User } from '@models/entities';
import { ROLE_EXPERT, ROLE_STUDENT } from '@models/roles';
import { UserDocumentsComponent } from '../user-documents/user-documents.component';
import { TranslocoDirective } from '@jsverse/transloco';

/** The fields of the API's 400 body (`{ field: message }`) this form can show. */
const FORM_FIELDS = ['firstname', 'lastname', 'email', 'qualification', 'password'] as const;
type FormField = (typeof FORM_FIELDS)[number];

/** A translation key, so a language switch re-renders the message. */
type SaveStatus = 'saved' | 'fieldErrors' | 'failed' | null;

@Component({
  selector: 'app-profile-page',
  imports: [CommonModule, ReactiveFormsModule, LucideAngularModule, UserDocumentsComponent, TranslocoDirective],
  templateUrl: './profile-page.component.html'
})
export class ProfilePageComponent implements OnInit {
  private tokenService = inject(TokenService);
  private readonly userService = inject(UserService);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  protected readonly wishlist = inject(WishlistService);

  readonly ArrowLeft = ArrowLeft;
  readonly UserIcon = UserIcon;
  readonly Mail = Mail;
  readonly Phone = Phone;
  readonly MapPin = MapPin;
  readonly GraduationCap = GraduationCap;
  readonly Book = Book;
  readonly Save = Save;
  readonly Heart = Heart;
  readonly Briefcase = Briefcase;

  profile = signal<Partial<User>>({
    firstname: '',
    lastname: '',
    email: '',
    role: { name: ROLE_STUDENT },
  });

  protected readonly isStudent = computed(() => this.profile().role?.name === ROLE_STUDENT);
  protected readonly isExpert = computed(() => this.profile().role?.name === ROLE_EXPERT);

  /**
   * The editable fields. `qualification` is the bac type for a student and the
   * specialty for an expert — one backend field, two inputs. `password` is a
   * new password: empty means unchanged, otherwise 8 characters or more (the
   * backend rule).
   */
  protected readonly form = inject(NonNullableFormBuilder).group({
    firstname: ['', Validators.required],
    lastname: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    qualification: [''],
    password: ['', Validators.minLength(8)],
  });

  isEditing = signal(false);
  protected readonly isSaving = signal(false);
  protected readonly saveStatus = signal<SaveStatus>(null);
  /** Messages from the API's 400 body, by field. */
  protected readonly serverErrors = signal<Partial<Record<FormField, string>>>({});

  constructor() {
    // Editing a field drops the message the API attached to it.
    for (const field of FORM_FIELDS) {
      this.form.controls[field].valueChanges.subscribe(() => {
        if (this.serverErrors()[field] !== undefined) {
          this.serverErrors.update(({ [field]: _dropped, ...rest }) => rest);
        }
      });
    }
  }

  ngOnInit() {
    const currentUser = this.tokenService.user();
    if (currentUser) {
      this.profile.set(currentUser);
      this.resetForm();
    }

    this.wishlist.load();
  }

  /** The message the API attached to a field, if any. */
  protected serverError(field: FormField): string | null {
    return this.serverErrors()[field] ?? null;
  }

  /** Why a field is invalid, as a translation key; shown once it was touched. */
  protected clientError(field: FormField): string | null {
    const control = this.form.controls[field];
    if (!control.touched || !control.errors) {
      return null;
    }
    if (control.errors['required']) return 'profile.errors.required';
    if (control.errors['email']) return 'profile.errors.email';
    if (control.errors['minlength']) return 'profile.errors.passwordMin';
    return null;
  }

  handleSave() {
    if (this.isSaving()) {
      return;
    }

    this.saveStatus.set(null);
    const id = this.profile().id;
    if (this.form.invalid || id === undefined) {
      this.form.markAllAsTouched();
      return;
    }

    const { password, ...fields } = this.form.getRawValue();
    const changes: ProfileUpdate = password ? { ...fields, password } : fields;

    this.serverErrors.set({});
    this.isSaving.set(true);
    this.userService.updateProfile(id, changes).subscribe({
      next: (saved) => {
        const updated: User = { ...(this.profile() as User), ...saved };
        this.tokenService.setUser(updated);
        this.profile.set(updated);
        this.resetForm();
        this.isEditing.set(false);
        this.isSaving.set(false);
        this.saveStatus.set('saved');
      },
      error: (err: unknown) => {
        this.isSaving.set(false);
        const mapped = err instanceof HttpErrorResponse && err.status === 400 && this.applyFieldErrors(err.error);
        this.saveStatus.set(mapped ? 'fieldErrors' : 'failed');
      },
    });
  }

  handleCancel() {
    this.resetForm();
    this.serverErrors.set({});
    this.saveStatus.set(null);
    this.isEditing.set(false);
  }

  /** Returns whether any message landed; anything else falls back to the generic failure. */
  private applyFieldErrors(body: unknown): boolean {
    if (!body || typeof body !== 'object') {
      return false;
    }

    const messages: Partial<Record<FormField, string>> = {};
    for (const field of FORM_FIELDS) {
      const message = (body as Record<string, unknown>)[field];
      if (typeof message === 'string') {
        messages[field] = message;
      }
    }

    if (Object.keys(messages).length === 0) {
      return false;
    }
    this.serverErrors.set(messages);
    return true;
  }

  private resetForm(): void {
    const { firstname, lastname, email, qualification } = this.profile();
    this.form.reset({
      firstname: firstname ?? '',
      lastname: lastname ?? '',
      email: email ?? '',
      qualification: qualification ?? '',
      password: '',
    });
  }

  /** Navigations stay in the language the visitor is reading. */
  protected goTo(...segments: (string | number)[]): void {
    void this.router.navigate(['/', this.locale.active(), ...segments]);
  }
}
