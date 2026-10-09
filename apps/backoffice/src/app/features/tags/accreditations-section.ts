import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Accreditation } from '@models/accreditation';
import { AccreditationService } from '@services/accreditation.service';
import { Alert } from '@shared/components/alert/alert';
import { messageFrom } from '@shared/utils/http-errors';
import { AccreditationForm } from './accreditation-form';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-accreditations-section',
  imports: [Alert, AccreditationForm],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-4">
      @if (creating()) {
        <app-accreditation-form (saved)="onSaved()" (cancelled)="creating.set(false)" />
      } @else {
        <div>
          <button
            type="button"
            data-new
            (click)="startCreate()"
            class="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
          >
            Nouvelle accréditation
          </button>
        </div>
      }

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

      @if (items().length) {
        <div class="overflow-x-auto rounded-lg border border-slate-200 bg-white">
          <table class="w-full text-left text-sm">
            <thead class="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th scope="col" class="px-4 py-2">Nom</th>
                <th scope="col" class="px-4 py-2">Code</th>
                <th scope="col" class="px-4 py-2">Description</th>
                <th scope="col" class="px-4 py-2 text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              @for (item of items(); track item.id) {
                @if (editingId() === item.id) {
                  <tr data-editing>
                    <td colspan="4" class="p-3">
                      <app-accreditation-form [accreditation]="item" (saved)="onSaved()" (cancelled)="editingId.set(null)" />
                    </td>
                  </tr>
                } @else {
                  <tr data-accreditation>
                    <td class="px-4 py-2 font-medium text-slate-900">{{ item.name }}</td>
                    <td class="px-4 py-2 text-slate-600">{{ item.code }}</td>
                    <td class="px-4 py-2 text-slate-600">{{ item.description }}</td>
                    <td class="whitespace-nowrap px-4 py-2 text-right">
                      @if (pendingDeleteId() === item.id) {
                        <div class="flex flex-col items-end gap-1">
                          <span class="max-w-xs whitespace-normal text-xs text-slate-700">
                            Supprimer cette accréditation ? Si elle est associée à des programmes, la suppression sera refusée : retirez-la d'abord de ces programmes.
                          </span>
                          <span class="flex gap-1">
                            <button
                              type="button"
                              data-confirm-delete
                              [disabled]="deleting()"
                              (click)="confirmDelete(item)"
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
                          (click)="startEdit(item)"
                          class="rounded-md border border-slate-300 px-3 py-1 text-xs font-medium hover:bg-slate-100"
                        >
                          Modifier
                        </button>
                        <button
                          type="button"
                          data-delete
                          (click)="pendingDeleteId.set(item.id)"
                          class="ml-1 rounded-md border border-red-300 px-3 py-1 text-xs font-medium text-red-700 hover:bg-red-50"
                        >
                          Supprimer
                        </button>
                      }
                    </td>
                  </tr>
                }
              }
            </tbody>
          </table>
        </div>
      } @else if (!loading() && !loadFailed()) {
        <p class="rounded-lg border border-dashed border-slate-300 bg-white px-4 py-8 text-center text-sm text-slate-500">
          Aucune accréditation pour le moment.
        </p>
      }

      <nav class="flex items-center justify-between text-sm text-slate-600" aria-label="Pagination">
        <span>Page {{ page() + 1 }} / {{ totalPages() || 1 }} · {{ total() }} accréditation(s)</span>
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
  `,
})
export class AccreditationsSection {
  private readonly service = inject(AccreditationService);

  readonly items = signal<Accreditation[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly loadFailed = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly creating = signal(false);
  readonly editingId = signal<number | null>(null);
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
    this.service.list(page, PAGE_SIZE).subscribe({
      next: (res) => {
        if (seq !== this.requestSeq) return;
        this.items.set(res.content);
        this.page.set(res.number);
        this.totalPages.set(res.totalPages);
        this.total.set(res.totalElements);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        if (seq !== this.requestSeq) return;
        this.loading.set(false);
        this.loadFailed.set(true);
        this.error.set(messageFrom(err, 'Impossible de charger les accréditations.'));
      },
    });
  }

  goTo(page: number) {
    this.notice.set('');
    this.load(page);
  }

  startCreate() {
    this.editingId.set(null);
    this.creating.set(true);
  }

  startEdit(item: Accreditation) {
    this.creating.set(false);
    this.editingId.set(item.id);
  }

  onSaved() {
    this.creating.set(false);
    this.editingId.set(null);
    this.notice.set('Accréditation enregistrée.');
    this.load(this.page());
  }

  confirmDelete(item: Accreditation) {
    if (this.deleting()) return;
    this.deleting.set(true);
    this.error.set('');
    this.service.delete(item.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.notice.set('Accréditation supprimée.');
        // Deleting the only row of the last page would otherwise leave an empty page.
        this.load(this.items().length === 1 && this.page() > 0 ? this.page() - 1 : this.page());
      },
      error: (err: unknown) => {
        this.deleting.set(false);
        this.pendingDeleteId.set(null);
        const refused = err instanceof HttpErrorResponse && (err.status === 409 || err.status === 500);
        this.error.set(
          refused
            ? 'Suppression impossible : cette accréditation est probablement encore associée à des programmes. Retirez-la de ces programmes puis réessayez.'
            : messageFrom(err, 'La suppression a échoué.'),
        );
      },
    });
  }
}
