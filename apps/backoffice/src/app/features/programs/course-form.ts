import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, catchError, map, of, switchMap, tap, throwError } from 'rxjs';
import { Course, CourseInput } from '@models/course';
import { CourseService } from '@services/course.service';
import { MediaService } from '@services/media.service';
import { Alert } from '@shared/components/alert/alert';
import { ImageField } from '@shared/components/image-field/image-field';
import { applyFieldErrors, notBlank } from '@shared/utils/form-errors';
import { fieldErrorsFrom, messageFrom } from '@shared/utils/http-errors';

/** An upload that failed, as opposed to the course save itself. */
class UploadFailed {
  constructor(readonly cause: unknown) {}
}

@Component({
  selector: 'app-course-form',
  imports: [ReactiveFormsModule, Alert, ImageField],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="max-w-2xl space-y-6">
      <h2 class="text-xl font-semibold text-slate-900">{{ course() ? 'Modifier le cours' : 'Nouveau cours' }}</h2>

      <app-alert [message]="error()" />

      <div class="grid gap-4 md:grid-cols-6">
        <div class="space-y-1 md:col-span-4">
          <label for="name" class="text-sm font-medium text-slate-700">Nom *</label>
          <input
            id="name"
            type="text"
            formControlName="name"
            [attr.aria-invalid]="errorOf('name') ? 'true' : null"
            class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 aria-invalid:border-red-500"
          />
          @if (errorOf('name'); as message) {
            <p class="text-xs text-red-700" data-error="name">{{ message }}</p>
          }
        </div>
        <div class="space-y-1 md:col-span-2">
          <label for="code" class="text-sm font-medium text-slate-700">Code (5 caractères max., unique)</label>
          <input
            id="code"
            type="text"
            formControlName="code"
            [attr.aria-invalid]="errorOf('code') ? 'true' : null"
            class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 aria-invalid:border-red-500"
          />
          @if (errorOf('code'); as message) {
            <p class="text-xs text-red-700" data-error="code">{{ message }}</p>
          }
        </div>
      </div>

      <app-image-field
        fieldId="photo"
        label="Photo"
        [existing]="!!course()?.photoMediaId"
        (fileChange)="onFile($event)"
      />

      <div class="flex gap-3">
        <button
          type="submit"
          [disabled]="saving()"
          class="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-60"
        >
          {{ saving() ? 'Enregistrement…' : 'Enregistrer' }}
        </button>
        <button
          type="button"
          data-cancel
          (click)="cancelled.emit()"
          class="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium hover:bg-slate-100"
        >
          Annuler
        </button>
      </div>
    </form>
  `,
})
export class CourseForm implements OnInit {
  private readonly courses = inject(CourseService);
  private readonly media = inject(MediaService);

  /** The course to edit; null to create one. */
  readonly course = input<Course | null>(null);
  readonly programId = input.required<number>();
  readonly saved = output<Course>();
  readonly cancelled = output<void>();

  /** Limits mirror the columns of the `courses` table. */
  readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [notBlank, Validators.maxLength(50)]],
    code: ['', Validators.maxLength(5)],
  });

  readonly saving = signal(false);
  readonly error = signal('');

  private photo: File | null = null;
  /** Set once the photo was uploaded, so a retry after a failed save does not upload it again. */
  private uploadedId: number | null = null;

  ngOnInit() {
    const course = this.course();
    if (course) this.form.setValue({ name: course.name ?? '', code: course.code ?? '' });
  }

  onFile(file: File | null) {
    this.photo = file;
    this.uploadedId = null;
  }

  errorOf(name: 'name' | 'code'): string {
    const control = this.form.controls[name];
    if (!control.touched || !control.errors) return '';
    const errors = control.errors;
    if (typeof errors['server'] === 'string') return errors['server'];
    if (errors['required']) return 'Le nom est obligatoire.';
    if (errors['maxlength']) return `${errors['maxlength'].requiredLength} caractères maximum.`;
    return 'Valeur invalide.';
  }

  submit() {
    if (this.saving()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set('');

    this.attach()
      .pipe(
        catchError((err: unknown) => throwError(() => new UploadFailed(err))),
        switchMap((photoId) => this.persist(photoId)),
      )
      .subscribe({
        next: (course) => {
          this.saving.set(false);
          this.saved.emit(course);
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.showError(err);
        },
      });
  }

  /** Uploads the pending photo (once) and yields its media id, or null to keep the current one. */
  private attach(): Observable<number | null> {
    if (this.uploadedId !== null) return of(this.uploadedId);
    if (!this.photo) return of(null);
    return this.media.upload(this.photo, 'COURSE_PHOTO').pipe(
      tap((m) => (this.uploadedId = m.id)),
      map((m) => m.id),
    );
  }

  private persist(photoId: number | null): Observable<Course> {
    const v = this.form.getRawValue();
    const code = v.code.trim();
    const input: CourseInput = {
      ...(code ? { code } : {}),
      name: v.name.trim(),
      programId: this.programId(),
      ...(photoId !== null ? { photoMediaId: photoId } : {}),
    };
    const course = this.course();
    return course ? this.courses.update(course.id, input) : this.courses.create(input);
  }

  private showError(err: unknown) {
    if (err instanceof UploadFailed) {
      this.error.set(messageFrom(err.cause, "L'envoi de l'image a échoué."));
      return;
    }
    const fields = fieldErrorsFrom(err);
    this.error.set(
      fields ? applyFieldErrors(this.form, fields) : messageFrom(err, "L'enregistrement a échoué (le code doit être unique)."),
    );
  }
}
