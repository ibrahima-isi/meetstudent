import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { SiteSettings } from '@models/site-settings';
import { SiteSettingsService } from '@services/site-settings.service';
import { Alert } from '@shared/components/alert/alert';
import { fieldErrorsFrom, messageFrom } from '@shared/utils/http-errors';

type FieldName = keyof SiteSettings;

const LIMITS: Record<FieldName, number> = { contactEmail: 255, aboutFr: 5000, aboutEn: 5000 };

/** `Validators.required` lets whitespace through; no setting may be blank. */
const notBlank = (control: AbstractControl<string>): ValidationErrors | null =>
  control.value.trim() ? null : { required: true };

/** Contact address and About text of the public site. Only the fields that changed are sent. */
@Component({
  selector: 'app-settings-page',
  imports: [ReactiveFormsModule, Alert],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="mb-1 text-2xl font-semibold text-foreground">Paramètres du site</h1>
    <p class="mb-4 text-sm text-muted-foreground">Ce que les visiteurs lisent sur les pages « À propos » et « Contact ».</p>

    <app-alert class="mb-3" [message]="error()" />
    @if (loadFailed()) {
      <button
        type="button"
        data-retry
        (click)="load()"
        class="btn btn-secondary h-9 px-3"
      >
        Réessayer
      </button>
    }
    @if (loading()) {
      <p class="text-sm text-muted-foreground">Chargement…</p>
    }

    @if (current()) {
      <form data-form [formGroup]="form" (ngSubmit)="submit()" novalidate class="max-w-3xl space-y-4 rounded-lg border border-border bg-card p-4">
        <div class="space-y-1">
          <label for="settings-contactEmail" class="text-sm font-medium text-foreground">Adresse e-mail de contact</label>
          <input
            id="settings-contactEmail"
            type="email"
            formControlName="contactEmail"
            autocomplete="off"
            [attr.aria-invalid]="errorOf('contactEmail') ? 'true' : null"
            class="field-input "
          />
          @if (errorOf('contactEmail'); as message) {
            <p class="text-xs text-destructive" data-error="contactEmail">{{ message }}</p>
          }
        </div>

        @for (f of texts; track f.name) {
          <div class="space-y-1">
            <label [for]="'settings-' + f.name" class="text-sm font-medium text-foreground">{{ f.label }}</label>
            <textarea
              [id]="'settings-' + f.name"
              rows="9"
              [formControlName]="f.name"
              [attr.lang]="f.lang"
              [attr.aria-invalid]="errorOf(f.name) ? 'true' : null"
              [attr.aria-describedby]="'settings-' + f.name + '-hint'"
              class="field-input "
            ></textarea>
            <p [id]="'settings-' + f.name + '-hint'" class="flex justify-between gap-3 text-xs text-muted-foreground">
              <span>Texte simple. Une ligne vide sépare deux paragraphes.</span>
              <span [attr.data-count]="f.name">{{ form.controls[f.name].value.length }} / {{ limits[f.name] }}</span>
            </p>
            @if (errorOf(f.name); as message) {
              <p class="text-xs text-destructive" [attr.data-error]="f.name">{{ message }}</p>
            }
          </div>
        }

        <div class="flex items-center gap-3">
          <button
            type="submit"
            [disabled]="saving()"
            class="btn btn-primary"
          >
            Enregistrer
          </button>
          @if (notice()) {
            <p role="status" class="text-sm text-foreground">{{ notice() }}</p>
          }
        </div>
      </form>
    }
  `,
})
export class SettingsPage {
  private readonly service = inject(SiteSettingsService);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly limits = LIMITS;
  readonly texts: { name: 'aboutFr' | 'aboutEn'; label: string; lang: string }[] = [
    { name: 'aboutFr', label: '« À propos » en français', lang: 'fr' },
    { name: 'aboutEn', label: '« À propos » en anglais', lang: 'en' },
  ];

  readonly form = this.fb.group({
    contactEmail: ['', [notBlank, Validators.email, Validators.maxLength(LIMITS.contactEmail)]],
    aboutFr: ['', [notBlank, Validators.maxLength(LIMITS.aboutFr)]],
    aboutEn: ['', [notBlank, Validators.maxLength(LIMITS.aboutEn)]],
  });

  /** The values last read from or confirmed by the API; null until loaded. */
  readonly current = signal<SiteSettings | null>(null);
  readonly loading = signal(false);
  readonly loadFailed = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  private readonly serverErrors = signal<Partial<Record<FieldName, string>>>({});
  private readonly submitted = signal(false);

  constructor() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.loadFailed.set(false);
    this.error.set('');
    this.service.get().subscribe({
      next: (settings) => {
        this.loading.set(false);
        this.show(settings);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.loadFailed.set(true);
        this.error.set(messageFrom(err, 'Impossible de charger les paramètres.'));
      },
    });
  }

  errorOf(name: FieldName): string {
    const server = this.serverErrors()[name];
    if (server) return server;
    const control = this.form.controls[name];
    if (!this.submitted() || !control.errors) return '';
    if (control.errors['required']) return 'Ce champ est obligatoire.';
    if (control.errors['maxlength']) return `Maximum ${LIMITS[name]} caractères.`;
    if (control.errors['email']) return 'Saisissez une adresse e-mail valide.';
    return '';
  }

  submit() {
    const current = this.current();
    if (this.saving() || !current) return;
    this.submitted.set(true);
    this.serverErrors.set({});
    this.error.set('');
    this.notice.set('');
    if (this.form.invalid) return;

    const values = this.form.getRawValue();
    const changes: Partial<SiteSettings> = {};
    for (const name of Object.keys(values) as FieldName[]) {
      const value = values[name].trim();
      if (value !== current[name]) changes[name] = value;
    }
    if (!Object.keys(changes).length) {
      this.notice.set('Aucune modification à enregistrer.');
      return;
    }

    this.saving.set(true);
    this.service.update(changes).subscribe({
      next: (settings) => {
        this.saving.set(false);
        this.show(settings);
        this.notice.set('Paramètres enregistrés.');
      },
      error: (err: unknown) => {
        this.saving.set(false);
        const fields = fieldErrorsFrom(err);
        if (fields) {
          const mapped: Partial<Record<FieldName, string>> = {};
          for (const name of Object.keys(LIMITS) as FieldName[]) if (fields[name]) mapped[name] = fields[name];
          this.serverErrors.set(mapped);
          if (!Object.keys(mapped).length) this.error.set(Object.values(fields)[0]);
        } else {
          this.error.set(messageFrom(err, "L'enregistrement a échoué."));
        }
      },
    });
  }

  private show(settings: SiteSettings) {
    this.current.set(settings);
    this.form.setValue({ contactEmail: settings.contactEmail, aboutFr: settings.aboutFr, aboutEn: settings.aboutEn });
    this.submitted.set(false);
  }
}
