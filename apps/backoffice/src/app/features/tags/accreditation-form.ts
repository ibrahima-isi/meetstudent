import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Accreditation, AccreditationInput } from '@models/accreditation';
import { AccreditationService } from '@services/accreditation.service';
import { Alert } from '@shared/components/alert/alert';
import { fieldErrorsFrom, messageFrom } from '@shared/utils/http-errors';

type FieldName = 'name' | 'code' | 'description';

const LIMITS: Record<FieldName, number> = { name: 50, code: 5, description: 255 };

/** `Validators.required` lets whitespace through; the name must not be blank. */
const notBlank = (control: AbstractControl<string>): ValidationErrors | null =>
  control.value.trim() ? null : { required: true };

@Component({
  selector: 'app-accreditation-form',
  imports: [ReactiveFormsModule, Alert],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form data-form [formGroup]="form" (ngSubmit)="submit()" novalidate class="space-y-3 rounded-lg border border-slate-200 bg-white p-4">
      <h2 class="text-base font-semibold text-slate-900">
        {{ accreditation() ? "Modifier l'accréditation" : 'Nouvelle accréditation' }}
      </h2>
      <app-alert [message]="error()" />
      <div class="grid gap-3 md:grid-cols-6">
        @for (f of fields; track f.name) {
          <div [class]="'space-y-1 ' + f.span">
            <label [for]="'acc-' + f.name" class="text-sm font-medium text-slate-700">{{ f.label }}</label>
            <input
              [id]="'acc-' + f.name"
              type="text"
              [formControlName]="f.name"
              [attr.aria-invalid]="errorOf(f.name) ? 'true' : null"
              class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 aria-invalid:border-red-500"
            />
            @if (errorOf(f.name); as message) {
              <p class="text-xs text-red-700" [attr.data-error]="f.name">{{ message }}</p>
            }
          </div>
        }
      </div>
      <div class="flex gap-2">
        <button
          type="submit"
          [disabled]="saving()"
          class="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-60"
        >
          Enregistrer
        </button>
        <button
          type="button"
          data-cancel-form
          (click)="cancelled.emit()"
          class="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium hover:bg-slate-100"
        >
          Annuler
        </button>
      </div>
    </form>
  `,
})
export class AccreditationForm implements OnInit {
  private readonly service = inject(AccreditationService);
  private readonly fb = inject(FormBuilder).nonNullable;

  /** The accreditation to edit; null creates a new one. */
  readonly accreditation = input<Accreditation | null>(null);
  readonly saved = output<void>();
  readonly cancelled = output<void>();

  readonly fields: { name: FieldName; label: string; span: string }[] = [
    { name: 'name', label: 'Nom', span: 'md:col-span-3' },
    { name: 'code', label: 'Code (5 caractères max)', span: 'md:col-span-3' },
    { name: 'description', label: 'Description', span: 'md:col-span-6' },
  ];

  readonly form = this.fb.group({
    name: ['', [notBlank, Validators.maxLength(LIMITS.name)]],
    code: ['', Validators.maxLength(LIMITS.code)],
    description: ['', Validators.maxLength(LIMITS.description)],
  });

  readonly error = signal('');
  readonly saving = signal(false);
  private readonly serverErrors = signal<Partial<Record<FieldName, string>>>({});
  private readonly submitted = signal(false);

  ngOnInit() {
    const a = this.accreditation();
    if (a) this.form.patchValue({ name: a.name, code: a.code ?? '', description: a.description ?? '' });
  }

  errorOf(name: FieldName): string {
    const server = this.serverErrors()[name];
    if (server) return server;
    const control = this.form.controls[name];
    if (!this.submitted() || !control.errors) return '';
    if (control.errors['required']) return 'Ce champ est obligatoire.';
    if (control.errors['maxlength']) return `Maximum ${LIMITS[name]} caractères.`;
    return '';
  }

  submit() {
    if (this.saving()) return;
    this.submitted.set(true);
    this.serverErrors.set({});
    this.error.set('');
    if (this.form.invalid) return;

    const { name, code, description } = this.form.getRawValue();
    const input: AccreditationInput = { name: name.trim() };
    if (code.trim()) input.code = code.trim();
    if (description.trim()) input.description = description.trim();

    const current = this.accreditation();
    const request = current ? this.service.update(current.id, input) : this.service.create(input);
    this.saving.set(true);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.saved.emit();
      },
      error: (err: unknown) => {
        this.saving.set(false);
        const fields = fieldErrorsFrom(err);
        if (fields) {
          const mapped: Partial<Record<FieldName, string>> = {};
          for (const f of this.fields) if (fields[f.name]) mapped[f.name] = fields[f.name];
          this.serverErrors.set(mapped);
          if (!Object.keys(mapped).length) this.error.set(Object.values(fields)[0]);
        } else if (err instanceof HttpErrorResponse && err.status === 500) {
          // The API has no dedicated duplicate answer: a unique-code violation surfaces as a 500.
          this.error.set("Enregistrement impossible : ce code existe peut-être déjà.");
        } else {
          this.error.set(messageFrom(err, "L'enregistrement a échoué."));
        }
      },
    });
  }
}
