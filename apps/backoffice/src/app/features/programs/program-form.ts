import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Observable, catchError, map, of, switchMap, tap, throwError } from 'rxjs';
import { Program, ProgramInput } from '@models/program';
import { School } from '@models/school';
import { MediaService } from '@services/media.service';
import { ProgramService } from '@services/program.service';
import { Alert } from '@shared/components/alert/alert';
import { ImageField } from '@shared/components/image-field/image-field';
import { applyFieldErrors, notBlank } from '@shared/utils/form-errors';
import { fieldErrorsFrom, messageFrom } from '@shared/utils/http-errors';
import { ProgramAccreditations } from './program-accreditations';

/** Empty, or a positive whole number of years. */
const optionalYears = (control: AbstractControl<string>): ValidationErrors | null =>
  /^([1-9]\d*)?$/.test(control.value.trim()) ? null : { years: true };

/** An upload that failed, as opposed to the program save itself. */
class UploadFailed {
  constructor(readonly cause: unknown) {}
}

type FieldName = 'name' | 'code' | 'duration' | 'schoolId';

@Component({
  selector: 'app-program-form',
  imports: [ReactiveFormsModule, Alert, ImageField, ProgramAccreditations],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="max-w-3xl space-y-6">
      <h2 class="text-xl font-semibold text-slate-900">{{ program() ? 'Modifier la filière' : 'Nouvelle filière' }}</h2>

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
        <div class="space-y-1 md:col-span-2">
          <label for="duration" class="text-sm font-medium text-slate-700">Durée (années)</label>
          <input
            id="duration"
            type="text"
            inputmode="numeric"
            formControlName="duration"
            [attr.aria-invalid]="errorOf('duration') ? 'true' : null"
            class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 aria-invalid:border-red-500"
          />
          @if (errorOf('duration'); as message) {
            <p class="text-xs text-red-700" data-error="duration">{{ message }}</p>
          }
        </div>
        <div class="space-y-1 md:col-span-4">
          <label for="schoolId" class="text-sm font-medium text-slate-700">École</label>
          <select
            id="schoolId"
            formControlName="schoolId"
            class="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          >
            <option value="">Aucune</option>
            @for (school of knownSchools(); track school.id) {
              <option [value]="school.id">{{ school.name }}</option>
            }
          </select>
          @if (errorOf('schoolId'); as message) {
            <p class="text-xs text-red-700" data-error="schoolId">{{ message }}</p>
          }
        </div>
      </div>

      <app-image-field
        fieldId="photo"
        label="Photo"
        [current]="currentPhoto()"
        [existing]="!!program()?.photoMediaId"
        (fileChange)="onFile($event)"
      />

      @if (program(); as existing) {
        <app-program-accreditations [programId]="existing.id" />
      } @else {
        <p class="text-sm text-slate-500">Enregistrez la filière pour lui associer des accréditations.</p>
      }

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
export class ProgramForm implements OnInit {
  private readonly programs = inject(ProgramService);
  private readonly media = inject(MediaService);

  /** The program to edit; null to create one. */
  readonly program = input<Program | null>(null);
  readonly schools = input<School[]>([]);
  readonly saved = output<Program>();
  readonly cancelled = output<void>();

  /** Limits mirror the columns of the `programs` table. */
  readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [notBlank, Validators.maxLength(50)]],
    code: ['', Validators.maxLength(5)],
    duration: ['', optionalYears],
    schoolId: [''],
  });

  readonly saving = signal(false);
  readonly error = signal('');

  /** The listed schools, plus the program's own so that the select never silently drops it. */
  readonly knownSchools = computed<Pick<School, 'id' | 'name'>[]>(() => {
    const known: Pick<School, 'id' | 'name'>[] = [...this.schools()];
    const id = this.program()?.schoolId;
    if (id && !known.some((s) => s.id === id)) known.push({ id, name: `École n° ${id}` });
    return known;
  });

  readonly currentPhoto = computed(() => this.media.publicUrl(this.program()?.photo));

  private photo: File | null = null;
  /** Set once the photo was uploaded, so a retry after a failed save does not upload it again. */
  private uploadedId: number | null = null;

  ngOnInit() {
    const program = this.program();
    if (program) {
      this.form.setValue({
        name: program.name ?? '',
        code: program.code ?? '',
        duration: program.duration ? String(program.duration) : '',
        schoolId: program.schoolId ? String(program.schoolId) : '',
      });
    }
  }

  onFile(file: File | null) {
    this.photo = file;
    this.uploadedId = null;
  }

  errorOf(name: FieldName): string {
    const control = this.form.controls[name];
    if (!control.touched || !control.errors) return '';
    const errors = control.errors;
    if (typeof errors['server'] === 'string') return errors['server'];
    if (errors['required']) return 'Le nom est obligatoire.';
    if (errors['maxlength']) return `${errors['maxlength'].requiredLength} caractères maximum.`;
    if (errors['years']) return "Indiquez un nombre entier d'années (1 ou plus).";
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
        next: (program) => {
          this.saving.set(false);
          this.saved.emit(program);
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
    return this.media.upload(this.photo, 'PROGRAM_PHOTO').pipe(
      tap((m) => (this.uploadedId = m.id)),
      map((m) => m.id),
    );
  }

  private persist(photoId: number | null): Observable<Program> {
    const v = this.form.getRawValue();
    const code = v.code.trim();
    const duration = v.duration.trim();
    const input: ProgramInput = {
      ...(code ? { code } : {}),
      name: v.name.trim(),
      ...(duration ? { duration: Number(duration) } : {}),
      ...(v.schoolId ? { schoolId: Number(v.schoolId) } : {}),
      ...(photoId !== null ? { photoMediaId: photoId } : {}),
    };
    const program = this.program();
    return program ? this.programs.update(program.id, input) : this.programs.create(input);
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
