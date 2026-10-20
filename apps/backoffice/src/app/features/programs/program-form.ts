import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Observable, catchError, map, of, switchMap, tap, throwError } from 'rxjs';
import { PROGRAM_LEVELS, Program, ProgramInput, ProgramLevel } from '@models/program';
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

/** Empty, or a whole number of 0 or more (6 digits max, like the API bound). */
const optionalCount = (control: AbstractControl<string>): ValidationErrors | null =>
  /^(\d{1,6})?$/.test(control.value.trim()) ? null : { count: true };

/** Empty, or an amount of 0 or more with at most 2 decimals (the API stores cents). */
const optionalAmount = (control: AbstractControl<string>): ValidationErrors | null =>
  /^(\d{1,8}([.,]\d{1,2})?)?$/.test(control.value.trim()) ? null : { amount: true };

const LEVEL_LABELS: Record<ProgramLevel, string> = {
  BTS_DUT: 'BTS/DUT',
  LICENCE: 'Licence',
  MASTER: 'Master',
  DOCTORAT: 'Doctorat',
  AUTRE: 'Autre',
};

/** An upload that failed, as opposed to the program save itself. */
class UploadFailed {
  constructor(readonly cause: unknown) {}
}

type FieldName =
  | 'name'
  | 'code'
  | 'duration'
  | 'schoolId'
  | 'description'
  | 'availableSeats'
  | 'annualFee'
  | 'level';

@Component({
  selector: 'app-program-form',
  imports: [ReactiveFormsModule, Alert, ImageField, ProgramAccreditations],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="max-w-3xl space-y-6">
      <h2 class="text-xl font-semibold text-foreground">{{ program() ? 'Modifier la filière' : 'Nouvelle filière' }}</h2>

      <app-alert [message]="error()" />

      <div class="grid gap-4 md:grid-cols-6">
        <div class="space-y-1 md:col-span-4">
          <label for="name" class="text-sm font-medium text-foreground">Nom *</label>
          <input
            id="name"
            type="text"
            formControlName="name"
            [attr.aria-invalid]="errorOf('name') ? 'true' : null"
            class="field-input "
          />
          @if (errorOf('name'); as message) {
            <p class="text-xs text-destructive" data-error="name">{{ message }}</p>
          }
        </div>
        <div class="space-y-1 md:col-span-2">
          <label for="code" class="text-sm font-medium text-foreground">Code (5 caractères max., unique)</label>
          <input
            id="code"
            type="text"
            formControlName="code"
            [attr.aria-invalid]="errorOf('code') ? 'true' : null"
            class="field-input "
          />
          @if (errorOf('code'); as message) {
            <p class="text-xs text-destructive" data-error="code">{{ message }}</p>
          }
        </div>
        <div class="space-y-1 md:col-span-2">
          <label for="duration" class="text-sm font-medium text-foreground">Durée (années)</label>
          <input
            id="duration"
            type="text"
            inputmode="numeric"
            formControlName="duration"
            [attr.aria-invalid]="errorOf('duration') ? 'true' : null"
            class="field-input "
          />
          @if (errorOf('duration'); as message) {
            <p class="text-xs text-destructive" data-error="duration">{{ message }}</p>
          }
        </div>
        <div class="space-y-1 md:col-span-4">
          <label for="schoolId" class="text-sm font-medium text-foreground">École</label>
          <select
            id="schoolId"
            formControlName="schoolId"
            class="field-input "
          >
            <option value="">Aucune</option>
            @for (school of knownSchools(); track school.id) {
              <option [value]="school.id">{{ school.name }}</option>
            }
          </select>
          @if (errorOf('schoolId'); as message) {
            <p class="text-xs text-destructive" data-error="schoolId">{{ message }}</p>
          }
        </div>
      </div>

      <div class="grid gap-4 md:grid-cols-6">
        <div class="space-y-1 md:col-span-6">
          <label for="description" class="text-sm font-medium text-foreground">Description (1000 caractères max.)</label>
          <textarea
            id="description"
            rows="4"
            formControlName="description"
            [attr.aria-invalid]="errorOf('description') ? 'true' : null"
            class="field-input"
          ></textarea>
          <p class="text-xs text-muted-foreground" data-hint>Laisser vide efface la description.</p>
          @if (errorOf('description'); as message) {
            <p class="text-xs text-destructive" data-error="description">{{ message }}</p>
          }
        </div>
        <div class="space-y-1 md:col-span-2">
          <label for="level" class="text-sm font-medium text-foreground">Niveau</label>
          <select id="level" formControlName="level" class="field-input">
            <option value="">Non précisé</option>
            @for (level of levels; track level) {
              <option [value]="level">{{ levelLabels[level] }}</option>
            }
          </select>
          <p class="text-xs text-muted-foreground" data-hint>Laisser vide conserve la valeur actuelle.</p>
          @if (errorOf('level'); as message) {
            <p class="text-xs text-destructive" data-error="level">{{ message }}</p>
          }
        </div>
        <div class="space-y-1 md:col-span-2">
          <label for="availableSeats" class="text-sm font-medium text-foreground">Places disponibles</label>
          <input
            id="availableSeats"
            type="text"
            inputmode="numeric"
            formControlName="availableSeats"
            [attr.aria-invalid]="errorOf('availableSeats') ? 'true' : null"
            class="field-input"
          />
          <p class="text-xs text-muted-foreground" data-hint>Laisser vide conserve la valeur actuelle.</p>
          @if (errorOf('availableSeats'); as message) {
            <p class="text-xs text-destructive" data-error="availableSeats">{{ message }}</p>
          }
        </div>
        <div class="space-y-1 md:col-span-2">
          <label for="annualFee" class="text-sm font-medium text-foreground">Frais annuels (€)</label>
          <input
            id="annualFee"
            type="text"
            inputmode="decimal"
            formControlName="annualFee"
            [attr.aria-invalid]="errorOf('annualFee') ? 'true' : null"
            class="field-input"
          />
          <p class="text-xs text-muted-foreground" data-hint>Laisser vide conserve la valeur actuelle.</p>
          @if (errorOf('annualFee'); as message) {
            <p class="text-xs text-destructive" data-error="annualFee">{{ message }}</p>
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
        <p class="text-sm text-muted-foreground">Enregistrez la filière pour lui associer des accréditations.</p>
      }

      <div class="flex gap-3">
        <button
          type="submit"
          [disabled]="saving()"
          class="btn btn-primary"
        >
          {{ saving() ? 'Enregistrement…' : 'Enregistrer' }}
        </button>
        <button
          type="button"
          data-cancel
          (click)="cancelled.emit()"
          class="btn btn-secondary"
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
    description: ['', Validators.maxLength(1000)],
    availableSeats: ['', optionalCount],
    annualFee: ['', optionalAmount],
    level: [''],
  });

  readonly levels = PROGRAM_LEVELS;
  readonly levelLabels = LEVEL_LABELS;

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
        description: program.description ?? '',
        availableSeats: program.availableSeats != null ? String(program.availableSeats) : '',
        annualFee: program.annualFee != null ? String(program.annualFee) : '',
        level: program.level ?? '',
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
    if (errors['count']) return 'Indiquez un nombre entier (0 ou plus).';
    if (errors['amount']) return 'Indiquez un montant en euros (0 ou plus, 2 décimales max.).';
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
    const description = v.description.trim();
    const seats = v.availableSeats.trim();
    const fee = v.annualFee.trim().replace(',', '.');
    const input: ProgramInput = {
      ...(code ? { code } : {}),
      name: v.name.trim(),
      ...(duration ? { duration: Number(duration) } : {}),
      // On edit an empty description is sent: the API reads '' as "clear" (null keeps).
      ...(description || this.program() ? { description } : {}),
      ...(seats ? { availableSeats: Number(seats) } : {}),
      ...(fee ? { annualFee: Number(fee) } : {}),
      ...(v.level ? { level: v.level as ProgramLevel } : {}),
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
      fields ? applyFieldErrors(this.form, fields)
        : err instanceof HttpErrorResponse && err.status === 409
          ? 'Enregistrement impossible : ce code existe déjà.'
          : messageFrom(err, "L'enregistrement a échoué."),
    );
  }
}
