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
      <label for="tag-name" class="text-sm font-medium text-foreground">Nouveau tag</label>
      <div class="flex flex-wrap gap-2">
        <input
          id="tag-name"
          type="text"
          [formControl]="name"
          [attr.aria-invalid]="nameError() ? 'true' : null"
          class="field-input max-w-xs "
        />
        <button
          type="submit"
          [disabled]="saving()"
          class="btn btn-primary"
        >
          Ajouter
        </button>
      </div>
      @if (nameError()) {
        <p class="text-xs text-destructive" data-error="name">{{ nameError() }}</p>
      }
      <p class="text-xs text-muted-foreground">Un tag ne peut pas être renommé : supprimez-le puis recréez-le.</p>
    </form>

    <div class="space-y-4">
      <app-alert [message]="error()" />
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
      @if (notice()) {
        <div role="status" data-notice class="rounded-md border border-emerald-300 bg-emerald-50 px-3 py-2 text-sm text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950 dark:text-emerald-200">
          {{ notice() }}
        </div>
      }
      @if (loading()) {
        <p class="text-sm text-muted-foreground">Chargement…</p>
      }

      @if (tags().length) {
        <div class="overflow-x-auto rounded-lg border border-border bg-card">
          <table class="w-full text-left text-sm">
            <thead class="border-b border-border bg-muted text-xs uppercase text-muted-foreground">
              <tr>
                <th scope="col" class="px-4 py-2">Nom</th>
                <th scope="col" class="px-4 py-2 text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-border">
              @for (tag of tags(); track tag.id) {
                <tr data-tag>
                  <td class="px-4 py-2 font-medium text-foreground">{{ tag.name }}</td>
                  <td class="px-4 py-2 text-right">
                    @if (pendingDeleteId() === tag.id) {
                      <div class="flex flex-col items-end gap-1">
                        <span class="max-w-xs text-xs text-foreground">
                          Supprimer ce tag ? S'il est utilisé par des écoles, la suppression sera refusée : retirez-le d'abord de ces écoles.
                        </span>
                        <span class="flex gap-1">
                          <button
                            type="button"
                            data-confirm-delete
                            [disabled]="deleting()"
                            (click)="confirmDelete(tag)"
                            class="btn bg-destructive text-destructive-foreground hover:opacity-90 h-8 px-3 text-xs"
                          >
                            Confirmer
                          </button>
                          <button
                            type="button"
                            data-cancel-delete
                            (click)="pendingDeleteId.set(null)"
                            class="btn btn-secondary h-8 px-3 text-xs"
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
                        class="btn btn-secondary border-destructive text-destructive hover:bg-destructive/10 h-8 px-3 text-xs"
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
        <p class="rounded-lg border border-dashed border-border-strong bg-card px-4 py-8 text-center text-sm text-muted-foreground">
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
        } else if (err instanceof HttpErrorResponse && err.status === 409) {
          this.error.set('Création impossible : ce tag existe déjà.');
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
        const refused = err instanceof HttpErrorResponse && err.status === 409;
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
