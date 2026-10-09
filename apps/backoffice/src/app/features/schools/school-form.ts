import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, input, output, signal, WritableSignal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Observable, catchError, forkJoin, map, of, switchMap, tap, throwError } from 'rxjs';
import { Media } from '@models/entities';
import { School, SchoolInput, SchoolMediaCategory, Tag } from '@models/school';
import { MediaService, validateImage } from '@services/media.service';
import { SchoolService } from '@services/school.service';
import { TagService } from '@services/tag.service';
import { Alert } from '@shared/components/alert/alert';
import { fieldErrorsFrom, messageFrom } from '@shared/utils/http-errors';

type SlotName = 'logo' | 'cover';

interface ImageSlot {
  file: File | null;
  previewUrl: string | null;
  error: string;
  /** Set once the file was uploaded, so a retry after a failed save does not upload it again. */
  uploadedId: number | null;
}

const emptySlot = (): ImageSlot => ({ file: null, previewUrl: null, error: '', uploadedId: null });

const CATEGORY: Record<SlotName, SchoolMediaCategory> = { logo: 'SCHOOL_LOGO', cover: 'SCHOOL_COVER' };

/** API field paths (as the server reports them) that differ from the control names. */
const FIELD_ALIASES: Record<string, string> = {
  'address.location': 'location',
  'address.city': 'city',
  'address.country': 'country',
};

/** `Validators.required` lets whitespace through; the name must not be blank. */
const notBlank = (control: AbstractControl<string>): ValidationErrors | null =>
  control.value.trim() ? null : { required: true };

/** An upload that failed, as opposed to the school save itself. */
class UploadFailed {
  constructor(readonly cause: unknown) {}
}

interface Field {
  name: 'name' | 'code' | 'location' | 'city' | 'country';
  label: string;
  /** Tailwind column span on the md+ grid. */
  span: string;
}

@Component({
  selector: 'app-school-form',
  imports: [ReactiveFormsModule, Alert],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="max-w-3xl space-y-6">
      <h2 class="text-xl font-semibold text-slate-900">
        {{ school() ? "Modifier l'école" : 'Nouvelle école' }}
      </h2>

      <app-alert [message]="error()" />

      <div class="grid gap-4 md:grid-cols-6">
        @for (f of fields; track f.name) {
          <div [class]="'space-y-1 ' + f.span">
            <label [for]="f.name" class="text-sm font-medium text-slate-700">{{ f.label }}</label>
            <input
              [id]="f.name"
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

      <fieldset class="space-y-2">
        <legend class="text-sm font-medium text-slate-700">Tags</legend>
        @if (tagsLoading()) {
          <p class="text-sm text-slate-500">Chargement des tags…</p>
        } @else if (!knownTags().length) {
          <p class="text-sm text-slate-500">Aucun tag disponible.</p>
        }
        <div class="flex flex-wrap gap-2">
          @for (tag of knownTags(); track tag.id) {
            <label class="flex cursor-pointer items-center gap-2 rounded-full border border-slate-300 bg-white px-3 py-1 text-sm has-[:checked]:border-indigo-500 has-[:checked]:bg-indigo-50">
              <input type="checkbox" data-tag [checked]="isSelected(tag)" (change)="toggleTag(tag)" />
              {{ tag.name }}
            </label>
          }
        </div>
      </fieldset>

      <div class="grid gap-6 md:grid-cols-2">
        @for (slot of slotNames; track slot) {
          <div class="space-y-2">
            <label [for]="slot + '-file'" class="text-sm font-medium text-slate-700">
              {{ slot === 'logo' ? 'Logo' : 'Image de couverture' }}
            </label>
            @if (preview(slot); as src) {
              <img
                [src]="src"
                [attr.data-preview]="slot"
                alt=""
                class="h-28 w-full rounded-md border border-slate-200 bg-slate-100 object-contain"
              />
            }
            <input
              [id]="slot + '-file'"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              (change)="onFile(slot, $event)"
              class="block w-full text-sm text-slate-600 file:mr-3 file:rounded-md file:border file:border-slate-300 file:bg-white file:px-3 file:py-1.5 file:text-sm file:font-medium hover:file:bg-slate-100"
            />
            <p class="text-xs text-slate-500">JPEG, PNG ou WebP, 10 Mo maximum.</p>
            @if (slots[slot]().error; as message) {
              <p class="text-xs text-red-700" [attr.data-error]="slot">{{ message }}</p>
            }
          </div>
        }
      </div>

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
export class SchoolForm implements OnInit, OnDestroy {
  private readonly schools = inject(SchoolService);
  private readonly tagService = inject(TagService);
  private readonly media = inject(MediaService);

  /** The school to edit; null to create one. */
  readonly school = input<School | null>(null);
  readonly saved = output<School>();
  readonly cancelled = output<void>();

  readonly fields: Field[] = [
    { name: 'name', label: 'Nom *', span: 'md:col-span-4' },
    { name: 'code', label: 'Code (5 caractères max., unique)', span: 'md:col-span-2' },
    { name: 'location', label: 'Adresse', span: 'md:col-span-6' },
    { name: 'city', label: 'Ville', span: 'md:col-span-3' },
    { name: 'country', label: 'Pays', span: 'md:col-span-3' },
  ];
  readonly slotNames: SlotName[] = ['logo', 'cover'];

  /** Limits mirror the columns of the `schools` table. */
  readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [notBlank, Validators.maxLength(50)]],
    code: ['', Validators.maxLength(5)],
    location: ['', Validators.maxLength(255)],
    city: ['', Validators.maxLength(255)],
    country: ['', Validators.maxLength(255)],
  });

  readonly saving = signal(false);
  readonly error = signal('');
  readonly tagsLoading = signal(true);
  private readonly loadedTags = signal<Tag[]>([]);
  private readonly selectedTagIds = signal<number[]>([]);

  readonly slots: Record<SlotName, WritableSignal<ImageSlot>> = {
    logo: signal(emptySlot()),
    cover: signal(emptySlot()),
  };

  /** Loaded tags, plus the school's own so that saving never drops one the list failed to return. */
  readonly knownTags = computed(() => {
    const known = [...this.loadedTags()];
    for (const tag of this.school()?.tags ?? []) {
      if (!known.some((t) => t.id === tag.id)) known.push(tag);
    }
    return known;
  });

  private readonly previews: Record<SlotName, () => string | null> = {
    logo: computed(() => this.slots.logo().previewUrl ?? this.media.publicUrl(this.school()?.logo)),
    cover: computed(() => this.slots.cover().previewUrl ?? this.media.publicUrl(this.school()?.cover)),
  };

  ngOnInit() {
    const school = this.school();
    if (school) {
      this.form.setValue({
        name: school.name ?? '',
        code: school.code ?? '',
        location: school.address?.location ?? '',
        city: school.address?.city ?? '',
        country: school.address?.country ?? '',
      });
      this.selectedTagIds.set((school.tags ?? []).map((t) => t.id).filter((id): id is number => id !== undefined));
    }
    this.tagService.list().subscribe({
      next: (tags) => {
        this.loadedTags.set(tags);
        this.tagsLoading.set(false);
      },
      error: () => {
        this.tagsLoading.set(false);
        this.error.set('Impossible de charger la liste des tags.');
      },
    });
  }

  ngOnDestroy() {
    for (const name of this.slotNames) this.revoke(this.slots[name]().previewUrl);
  }

  preview(slot: SlotName): string | null {
    return this.previews[slot]();
  }

  isSelected(tag: Tag): boolean {
    return tag.id !== undefined && this.selectedTagIds().includes(tag.id);
  }

  toggleTag(tag: Tag) {
    const id = tag.id;
    if (id === undefined) return;
    this.selectedTagIds.update((ids) => (ids.includes(id) ? ids.filter((i) => i !== id) : [...ids, id]));
  }

  onFile(slot: SlotName, event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.revoke(this.slots[slot]().previewUrl);
    if (!file) {
      this.slots[slot].set(emptySlot());
      return;
    }
    const error = validateImage(file);
    if (error) {
      input.value = '';
      this.slots[slot].set({ ...emptySlot(), error });
      return;
    }
    this.slots[slot].set({ file, previewUrl: URL.createObjectURL(file), error: '', uploadedId: null });
  }

  errorOf(name: Field['name']): string {
    const control = this.form.controls[name];
    if (!control.touched || !control.errors) return '';
    const errors = control.errors;
    if (typeof errors['server'] === 'string') return errors['server'];
    if (errors['required']) return name === 'name' ? 'Le nom est obligatoire.' : 'Ce champ est obligatoire.';
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

    forkJoin([this.attach('logo'), this.attach('cover')])
      .pipe(
        catchError((err: unknown) => throwError(() => new UploadFailed(err))),
        switchMap(([logoId, coverId]) => this.persist(logoId, coverId)),
      )
      .subscribe({
        next: (school) => {
          this.saving.set(false);
          this.saved.emit(school);
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.showError(err);
        },
      });
  }

  /** Uploads the slot's pending file (once) and yields the media id to attach, or null to keep the current one. */
  private attach(name: SlotName): Observable<number | null> {
    const slot = this.slots[name];
    const { file, uploadedId } = slot();
    if (uploadedId !== null) return of(uploadedId);
    if (!file) return of(null);
    return this.media.upload(file, CATEGORY[name]).pipe(
      tap((m: Media) => slot.update((s) => ({ ...s, uploadedId: m.id }))),
      map((m) => m.id),
    );
  }

  private persist(logoId: number | null, coverId: number | null): Observable<School> {
    const v = this.form.getRawValue();
    const code = v.code.trim();
    const selected = this.selectedTagIds();
    const input: SchoolInput = {
      ...(code ? { code } : {}),
      name: v.name.trim(),
      address: { location: v.location.trim(), city: v.city.trim(), country: v.country.trim() },
      tags: this.knownTags()
        .filter((t) => t.id !== undefined && selected.includes(t.id))
        .map((t) => ({ id: t.id, name: t.name })),
      ...(logoId !== null ? { logoMediaId: logoId } : {}),
      ...(coverId !== null ? { coverMediaId: coverId } : {}),
    };
    const school = this.school();
    return school ? this.schools.update(school.id, input) : this.schools.create(input);
  }

  private showError(err: unknown) {
    if (err instanceof UploadFailed) {
      this.error.set(messageFrom(err.cause, "L'envoi de l'image a échoué."));
      return;
    }
    const fields = fieldErrorsFrom(err);
    if (!fields) {
      this.error.set(messageFrom(err, "L'enregistrement a échoué."));
      return;
    }
    const unmapped: string[] = [];
    for (const [key, message] of Object.entries(fields)) {
      const control = this.form.get(FIELD_ALIASES[key] ?? key);
      if (control) {
        control.setErrors({ server: message });
        control.markAsTouched();
      } else {
        unmapped.push(message);
      }
    }
    this.error.set(unmapped.join(' '));
  }

  private revoke(url: string | null) {
    if (url) URL.revokeObjectURL(url);
  }
}
