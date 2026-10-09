import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Program } from '@models/program';
import { School } from '@models/school';
import { MediaService } from '@services/media.service';
import { ProgramService } from '@services/program.service';
import { SchoolService } from '@services/school.service';
import { Alert } from '@shared/components/alert/alert';
import { messageFrom } from '@shared/utils/http-errors';
import { ProgramCourses } from './program-courses';
import { ProgramForm } from './program-form';

const PAGE_SIZE = 10;
/** The schools endpoint is paginated: one large page feeds the names and the form's select. */
const SCHOOLS_SIZE = 200;

@Component({
  selector: 'app-programs-page',
  imports: [ReactiveFormsModule, Alert, ProgramForm, ProgramCourses],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (editing(); as target) {
      <app-program-form [program]="target.program" [schools]="schools()" (saved)="onSaved()" (cancelled)="editing.set(null)" />
    } @else if (coursesProgram(); as target) {
      <app-program-courses [program]="target" (back)="coursesProgramId.set(null)" (changed)="load(page())" />
    } @else {
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h1 class="text-2xl font-semibold text-slate-900">Filières et cours</h1>
        <button
          type="button"
          data-new
          (click)="editing.set({ program: null })"
          class="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
        >
          Nouvelle filière
        </button>
      </div>

      <form data-search (submit)="$event.preventDefault(); applySearch()" class="mb-4 flex flex-wrap gap-2" role="search">
        <label for="search" class="sr-only">Rechercher une filière par nom</label>
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

        @if (programs().length) {
          <div class="overflow-x-auto rounded-lg border border-slate-200 bg-white">
            <table class="w-full text-left text-sm">
              <thead class="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th scope="col" class="px-4 py-2"><span class="sr-only">Photo</span></th>
                  <th scope="col" class="px-4 py-2">Nom</th>
                  <th scope="col" class="px-4 py-2">Code</th>
                  <th scope="col" class="px-4 py-2">École</th>
                  <th scope="col" class="px-4 py-2">Durée</th>
                  <th scope="col" class="px-4 py-2">Cours</th>
                  <th scope="col" class="px-4 py-2 text-right">Actions</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                @for (program of programs(); track program.id) {
                  <tr data-program>
                    <td class="px-4 py-2">
                      @if (photoUrl(program); as src) {
                        <img [src]="src" alt="" class="size-10 rounded bg-slate-100 object-cover" />
                      } @else {
                        <div class="size-10 rounded bg-slate-100"></div>
                      }
                    </td>
                    <td class="px-4 py-2 font-medium text-slate-900">{{ program.name }}</td>
                    <td class="px-4 py-2 text-slate-600">{{ program.code }}</td>
                    <td class="px-4 py-2 text-slate-600">{{ schoolName(program) }}</td>
                    <td class="px-4 py-2 text-slate-600">{{ years(program) }}</td>
                    <td class="px-4 py-2">
                      <button
                        type="button"
                        data-courses
                        (click)="coursesProgramId.set(program.id)"
                        class="rounded-md border border-slate-300 px-3 py-1 text-xs font-medium hover:bg-slate-100"
                      >
                        Cours ({{ courseCount(program) }})
                      </button>
                    </td>
                    <td class="whitespace-nowrap px-4 py-2 text-right">
                      @if (pendingDeleteId() === program.id) {
                        <div class="flex flex-col items-end gap-1">
                          <span class="text-xs text-slate-700">{{ deletePrompt(program) }}</span>
                          <span class="flex gap-1">
                            <button
                              type="button"
                              data-confirm-delete
                              [disabled]="deleting()"
                              (click)="confirmDelete(program)"
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
                          (click)="editing.set({ program })"
                          class="rounded-md border border-slate-300 px-3 py-1 text-xs font-medium hover:bg-slate-100"
                        >
                          Modifier
                        </button>
                        <button
                          type="button"
                          data-delete
                          (click)="pendingDeleteId.set(program.id)"
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
            {{ query() ? 'Aucun résultat pour « ' + query() + ' ».' : 'Aucune filière pour le moment.' }}
          </p>
        }

        <nav class="flex items-center justify-between text-sm text-slate-600" aria-label="Pagination">
          <span>Page {{ page() + 1 }} / {{ totalPages() || 1 }} · {{ total() }} filière(s)</span>
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
export class ProgramsPage {
  private readonly service = inject(ProgramService);
  private readonly schoolService = inject(SchoolService);
  private readonly media = inject(MediaService);

  readonly search = new FormControl('', { nonNullable: true });
  /** The search term currently applied to the list. */
  readonly query = signal('');
  readonly programs = signal<Program[]>([]);
  readonly schools = signal<School[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly loadFailed = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  /** `{ program: null }` is the create form; null shows the list. */
  readonly editing = signal<{ program: Program | null } | null>(null);
  /** The program whose courses are shown, if any. */
  readonly coursesProgramId = signal<number | null>(null);
  readonly coursesProgram = computed(() => this.programs().find((p) => p.id === this.coursesProgramId()) ?? null);
  readonly pendingDeleteId = signal<number | null>(null);
  readonly deleting = signal(false);

  private readonly schoolNames = computed(() => new Map(this.schools().map((s) => [s.id, s.name])));

  /** Guards against a slow response overwriting a newer one. */
  private requestSeq = 0;

  constructor() {
    this.schoolService.list(0, SCHOOLS_SIZE).subscribe({
      next: (res) => this.schools.set(res.content),
      // Names fall back to the school number and the form's select is just shorter.
      error: () => undefined,
    });
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
        this.programs.set(res.content);
        this.page.set(res.number);
        this.totalPages.set(res.totalPages);
        this.total.set(res.totalElements);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        if (seq !== this.requestSeq) return;
        this.loading.set(false);
        this.loadFailed.set(true);
        this.error.set(messageFrom(err, 'Impossible de charger les filières.'));
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
    this.notice.set('Filière enregistrée.');
    this.load(this.page());
  }

  confirmDelete(program: Program) {
    if (this.deleting()) return;
    this.deleting.set(true);
    this.error.set('');
    this.service.delete(program.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.notice.set('Filière supprimée.');
        // Deleting the only program of the last page would otherwise leave an empty page.
        this.load(this.programs().length === 1 && this.page() > 0 ? this.page() - 1 : this.page());
      },
      error: (err: unknown) => {
        this.deleting.set(false);
        this.pendingDeleteId.set(null);
        this.error.set(messageFrom(err, 'La suppression a échoué.'));
      },
    });
  }

  photoUrl(program: Program): string | null {
    return this.media.publicUrl(program.photo);
  }

  schoolName(program: Program): string {
    const id = program.schoolId;
    return id ? (this.schoolNames().get(id) ?? `École n° ${id}`) : '';
  }

  years(program: Program): string {
    const n = program.duration;
    return n ? `${n} ${n > 1 ? 'ans' : 'an'}` : '';
  }

  courseCount(program: Program): number {
    return program.courses?.length ?? 0;
  }

  /** Deleting a program deletes its courses with it (cascade), so the prompt says so. */
  deletePrompt(program: Program): string {
    const n = this.courseCount(program);
    return n ? `Supprimer cette filière et ses ${n} cours ?` : 'Supprimer cette filière ?';
  }
}
