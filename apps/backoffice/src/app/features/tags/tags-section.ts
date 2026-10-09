import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Tag } from '@models/school';
import { TagService } from '@services/tag.service';
import { Alert } from '@shared/components/alert/alert';
import { fieldErrorsFrom, messageFrom } from '@shared/utils/http-errors';

const NAME_MAX = 255;

@Component({
  selector: 'app-tags-section',
  imports: [ReactiveFormsModule, Alert],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form data-create (submit)="$event.preventDefault(); create()" novalidate class="mb-4 space-y-1">
      <label for="tag-name" class="text-sm font-medium text-slate-700">Nouveau tag</label>
      <div class="flex flex-wrap gap-2">
        <input
          id="tag-name"
          type="text"
          [formControl]="name"
          [attr.aria-invalid]="nameError() ? 'true' : null"
          class="w-full max-w-xs rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 aria-invalid:border-red-500"
        />
        <button
          type="submit"
          [disabled]="saving()"
          class="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-60"
        >
          Ajouter
        </button>
      </div>
      @if (nameError()) {
        <p class="text-xs text-red-700" data-error="name">{{ nameError() }}</p>
      }
      <p class="text-xs text-slate-500">Un tag ne peut pas être renommé : supprimez-le puis recréez-le.</p>
    </form>

    <div class="space-y-4">
      <app-alert [message]="error()" />
      @if (loadFailed()) {
        <button
          type="button"
          data-retry
          (click)="load()"
          class="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium hover:bg-slate-100"
        >
          Réessayer
        </button>
      }
      @if (notice()) {
        <div role="status" data-notice class="rounded-md border border-emerald-300 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
          {{ notice() }}
        </div>
      }
      @if (loading()) {
        <p class="text-sm text-slate-500">Chargement…</p>
      }

      @if (tags().length) {
        <div class="overflow-x-auto rounded-lg border border-slate-200 bg-white">
          <table class="w-full text-left text-sm">
            <thead class="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th scope="col" class="px-4 py-2">Nom</th>
                <th scope="col" class="px-4 py-2 text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              @for (tag of tags(); track tag.id) {
                <tr data-tag>
                  <td class="px-4 py-2 font-medium text-slate-900">{{ tag.name }}</td>
                  <td class="px-4 py-2 text-right">
                    @if (pendingDeleteId() === tag.id) {
                      <div class="flex flex-col items-end gap-1">
                        <span class="max-w-xs text-xs text-slate-700">
                          Supprimer ce tag ? S'il est utilisé par des écoles, la suppression sera refusée : retirez-le d'abord de ces écoles.
                        </span>
                        <span class="flex gap-1">
                          <button
                            type="button"
                            data-confirm-delete
                            [disabled]="deleting()"
                            (click)="confirmDelete(tag)"
                            class="rounded-md bg-red-600 px-3 py-1 text-xs font-medium text-white hover:bg-red-700 disabled:opacity-60"
                          >
                            Confirmer
                          </button>
                          <button
                            type="button"
                            data-cancel-delete
                            (click)="pendingDeleteId.set(null)"
                            class="rounded-md border border-slate-300 px-3 py-1 text-xs font-medium hover:bg-slate-100"
                          >
                            Annuler
                          </button>
                        </span>
                      </div>
                    } @else {
                      <button
                        type="button"
                        data-delete
                        (click)="pendingDeleteId.set(tag.id ?? null)"
                        class="rounded-md border border-red-300 px-3 py-1 text-xs font-medium text-red-700 hover:bg-red-50"
                      >
                        Supprimer
                      </button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else if (!loading() && !loadFailed()) {
        <p class="rounded-lg border border-dashed border-slate-300 bg-white px-4 py-8 text-center text-sm text-slate-500">
          Aucun tag pour le moment.
        </p>
      }
    </div>
  `,
})
export class TagsSection {
  private readonly service = inject(TagService);

  readonly name = new FormControl('', { nonNullable: true });
  readonly tags = signal<Tag[]>([]);
  readonly loading = signal(false);
  readonly loadFailed = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly nameError = signal('');
  readonly saving = signal(false);
  readonly pendingDeleteId = signal<number | null>(null);
  readonly deleting = signal(false);

  /** Guards against a slow response overwriting a newer one. */
  private requestSeq = 0;

  constructor() {
    this.load();
  }

  load() {
    const seq = ++this.requestSeq;
    this.loading.set(true);
    this.loadFailed.set(false);
    this.error.set('');
    this.pendingDeleteId.set(null);
    this.service.list().subscribe({
      next: (tags) => {
        if (seq !== this.requestSeq) return;
        this.tags.set(tags);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        if (seq !== this.requestSeq) return;
        this.loading.set(false);
        this.loadFailed.set(true);
        this.error.set(messageFrom(err, 'Impossible de charger les tags.'));
      },
    });
  }

  create() {
    if (this.saving()) return;
    const value = this.name.value.trim();
    this.notice.set('');
    this.error.set('');
    this.nameError.set(this.validate(value));
    if (this.nameError()) return;

    this.saving.set(true);
    this.service.create(value).subscribe({
      next: () => {
        this.saving.set(false);
        this.name.setValue('');
        this.notice.set('Tag ajouté.');
        this.load();
      },
      error: (err: unknown) => {
        this.saving.set(false);
        const fields = fieldErrorsFrom(err);
        if (fields) {
          this.nameError.set(fields['name'] ?? Object.values(fields)[0]);
        } else if (err instanceof HttpErrorResponse && err.status === 500) {
          // The API has no dedicated duplicate answer: a unique-name violation surfaces as a 500.
          this.error.set("Création impossible : ce tag existe peut-être déjà.");
        } else {
          this.error.set(messageFrom(err, "L'ajout a échoué."));
        }
      },
    });
  }

  confirmDelete(tag: Tag) {
    if (this.deleting() || tag.id === undefined) return;
    this.deleting.set(true);
    this.error.set('');
    this.service.delete(tag.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.notice.set('Tag supprimé.');
        this.load();
      },
      error: (err: unknown) => {
        this.deleting.set(false);
        this.pendingDeleteId.set(null);
        const refused = err instanceof HttpErrorResponse && (err.status === 409 || err.status === 500);
        this.error.set(
          refused
            ? 'Suppression impossible : ce tag est probablement encore utilisé par des écoles. Retirez-le de ces écoles puis réessayez.'
            : messageFrom(err, 'La suppression a échoué.'),
        );
      },
    });
  }

  private validate(value: string): string {
    if (!value) return 'Le nom est obligatoire.';
    if (value.length > NAME_MAX) return `Le nom ne doit pas dépasser ${NAME_MAX} caractères.`;
    if (this.tags().some((t) => t.name.toLowerCase() === value.toLowerCase())) return 'Ce tag existe déjà.';
    return '';
  }
}
