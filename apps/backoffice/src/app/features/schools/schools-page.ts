import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { School } from '@models/school';
import { MediaService } from '@services/media.service';
import { SchoolService } from '@services/school.service';
import { Alert } from '@shared/components/alert/alert';
import { messageFrom } from '@shared/utils/http-errors';
import { SchoolForm } from './school-form';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-schools-page',
  imports: [ReactiveFormsModule, Alert, SchoolForm],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (editing(); as target) {
      <app-school-form [school]="target.school" (saved)="onSaved()" (cancelled)="editing.set(null)" />
    } @else {
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h1 class="text-2xl font-semibold text-slate-900">Écoles</h1>
        <button
          type="button"
          data-new
          (click)="editing.set({ school: null })"
          class="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
        >
          Nouvelle école
        </button>
      </div>

      <form data-search (submit)="$event.preventDefault(); applySearch()" class="mb-4 flex flex-wrap gap-2" role="search">
        <label for="search" class="sr-only">Rechercher une école par nom</label>
        <input
          id="search"
          type="search"
          [formControl]="search"
          placeholder="Rechercher par nom"
          class="w-full max-w-xs rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
        />
        <button type="submit" class="rounded-md border border-slate-300 px-3 py-2 text-sm font-medium hover:bg-slate-100">
          Rechercher
        </button>
        @if (query()) {
          <button
            type="button"
            data-clear-search
            (click)="clearSearch()"
            class="rounded-md px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100"
          >
            Effacer
          </button>
        }
      </form>

      <div class="space-y-3">
        <app-alert [message]="error()" />
        @if (loadFailed()) {
          <button
            type="button"
            data-retry
            (click)="load(page())"
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

        @if (schools().length) {
          <div class="overflow-x-auto rounded-lg border border-slate-200 bg-white">
            <table class="w-full text-left text-sm">
              <thead class="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th scope="col" class="px-4 py-2"><span class="sr-only">Logo</span></th>
                  <th scope="col" class="px-4 py-2">Nom</th>
                  <th scope="col" class="px-4 py-2">Code</th>
                  <th scope="col" class="px-4 py-2">Ville / pays</th>
                  <th scope="col" class="px-4 py-2">Tags</th>
                  <th scope="col" class="px-4 py-2 text-right">Actions</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                @for (school of schools(); track school.id) {
                  <tr data-school>
                    <td class="px-4 py-2">
                      @if (logoUrl(school); as src) {
                        <img [src]="src" alt="" class="size-10 rounded bg-slate-100 object-contain" />
                      } @else {
                        <div class="size-10 rounded bg-slate-100"></div>
                      }
                    </td>
                    <td class="px-4 py-2 font-medium text-slate-900">{{ school.name }}</td>
                    <td class="px-4 py-2 text-slate-600">{{ school.code }}</td>
                    <td class="px-4 py-2 text-slate-600">{{ place(school) }}</td>
                    <td class="px-4 py-2">
                      <div class="flex flex-wrap gap-1">
                        @for (tag of school.tags ?? []; track tag.id) {
                          <span class="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-700">{{ tag.name }}</span>
                        }
                      </div>
                    </td>
                    <td class="whitespace-nowrap px-4 py-2 text-right">
                      @if (pendingDeleteId() === school.id) {
                        <div class="flex flex-col items-end gap-1">
                          <span class="text-xs text-slate-700">Supprimer cette école ?</span>
                          <span class="flex gap-1">
                            <button
                              type="button"
                              data-confirm-delete
                              [disabled]="deleting()"
                              (click)="confirmDelete(school)"
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
                          data-edit
                          (click)="editing.set({ school })"
                          class="rounded-md border border-slate-300 px-3 py-1 text-xs font-medium hover:bg-slate-100"
                        >
                          Modifier
                        </button>
                        <button
                          type="button"
                          data-delete
                          (click)="askDelete(school)"
                          class="ml-1 rounded-md border border-red-300 px-3 py-1 text-xs font-medium text-red-700 hover:bg-red-50"
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
            {{ query() ? 'Aucun résultat pour « ' + query() + ' ».' : 'Aucune école pour le moment.' }}
          </p>
        }

        <nav class="flex items-center justify-between text-sm text-slate-600" aria-label="Pagination">
          <span>Page {{ page() + 1 }} / {{ totalPages() || 1 }} · {{ total() }} école(s)</span>
          <span class="flex gap-2">
            <button
              type="button"
              data-prev
              [disabled]="loading() || page() <= 0"
              (click)="goTo(page() - 1)"
              class="rounded-md border border-slate-300 px-3 py-1.5 font-medium hover:bg-slate-100 disabled:opacity-50"
            >
              Précédent
            </button>
            <button
              type="button"
              data-next
              [disabled]="loading() || page() + 1 >= totalPages()"
              (click)="goTo(page() + 1)"
              class="rounded-md border border-slate-300 px-3 py-1.5 font-medium hover:bg-slate-100 disabled:opacity-50"
            >
              Suivant
            </button>
          </span>
        </nav>
      </div>
    }
  `,
})
export class SchoolsPage {
  private readonly service = inject(SchoolService);
  private readonly media = inject(MediaService);

  readonly search = new FormControl('', { nonNullable: true });
  /** The search term currently applied to the list. */
  readonly query = signal('');
  readonly schools = signal<School[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly loadFailed = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  /** `{ school: null }` is the create form; null shows the list. */
  readonly editing = signal<{ school: School | null } | null>(null);
  readonly pendingDeleteId = signal<number | null>(null);
  readonly deleting = signal(false);

  /** Guards against a slow response overwriting a newer one. */
  private requestSeq = 0;

  constructor() {
    this.load(0);
  }

  load(page: number) {
    const seq = ++this.requestSeq;
    this.loading.set(true);
    this.loadFailed.set(false);
    this.error.set('');
    this.pendingDeleteId.set(null);
    this.service.list(page, PAGE_SIZE, this.query()).subscribe({
      next: (res) => {
        if (seq !== this.requestSeq) return;
        this.schools.set(res.content);
        this.page.set(res.number);
        this.totalPages.set(res.totalPages);
        this.total.set(res.totalElements);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        if (seq !== this.requestSeq) return;
        this.loading.set(false);
        this.loadFailed.set(true);
        this.error.set(messageFrom(err, 'Impossible de charger les écoles.'));
      },
    });
  }

  goTo(page: number) {
    this.notice.set('');
    this.load(page);
  }

  applySearch() {
    this.notice.set('');
    this.query.set(this.search.value.trim());
    this.load(0);
  }

  clearSearch() {
    this.search.setValue('');
    this.applySearch();
  }

  onSaved() {
    this.editing.set(null);
    this.notice.set('École enregistrée.');
    this.load(this.page());
  }

  askDelete(school: School) {
    this.pendingDeleteId.set(school.id);
  }

  confirmDelete(school: School) {
    if (this.deleting()) return;
    this.deleting.set(true);
    this.error.set('');
    this.service.delete(school.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.notice.set('École supprimée.');
        // Deleting the only school of the last page would otherwise leave an empty page.
        this.load(this.schools().length === 1 && this.page() > 0 ? this.page() - 1 : this.page());
      },
      error: (err: unknown) => {
        this.deleting.set(false);
        this.pendingDeleteId.set(null);
        this.error.set(messageFrom(err, 'La suppression a échoué.'));
      },
    });
  }

  logoUrl(school: School): string | null {
    return this.media.publicUrl(school.logo);
  }

  place(school: School): string {
    return [school.address?.city, school.address?.country].filter(Boolean).join(', ');
  }
}
