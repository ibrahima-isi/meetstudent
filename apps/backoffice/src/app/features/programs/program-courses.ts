import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { Course } from '@models/course';
import { Program } from '@models/program';
import { CourseService } from '@services/course.service';
import { Alert } from '@shared/components/alert/alert';
import { messageFrom } from '@shared/utils/http-errors';
import { CourseForm } from './course-form';

/** The courses of one program (the API embeds them in the program), with create / edit / delete. */
@Component({
  selector: 'app-program-courses',
  imports: [Alert, CourseForm],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (editing(); as target) {
      <app-course-form [course]="target.course" [programId]="program().id" (saved)="onSaved()" (cancelled)="editing.set(null)" />
    } @else {
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <button type="button" data-back (click)="back.emit()" class="text-sm text-indigo-700 hover:underline">
            &larr; Filières
          </button>
          <h2 class="text-2xl font-semibold text-slate-900">Cours de « {{ program().name }} »</h2>
        </div>
        <button
          type="button"
          data-new
          (click)="editing.set({ course: null })"
          class="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
        >
          Nouveau cours
        </button>
      </div>

      <div class="space-y-3">
        <app-alert [message]="error()" />
        @if (notice()) {
          <div role="status" data-notice class="rounded-md border border-emerald-300 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
            {{ notice() }}
          </div>
        }

        @if (courses().length) {
          <div class="overflow-x-auto rounded-lg border border-slate-200 bg-white">
            <table class="w-full text-left text-sm">
              <thead class="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th scope="col" class="px-4 py-2">Nom</th>
                  <th scope="col" class="px-4 py-2">Code</th>
                  <th scope="col" class="px-4 py-2">Photo</th>
                  <th scope="col" class="px-4 py-2 text-right">Actions</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                @for (course of courses(); track course.id) {
                  <tr data-course>
                    <td class="px-4 py-2 font-medium text-slate-900">{{ course.name }}</td>
                    <td class="px-4 py-2 text-slate-600">{{ course.code }}</td>
                    <td class="px-4 py-2 text-slate-600">{{ course.photoMediaId ? 'Oui' : '—' }}</td>
                    <td class="whitespace-nowrap px-4 py-2 text-right">
                      @if (pendingDeleteId() === course.id) {
                        <div class="flex flex-col items-end gap-1">
                          <span class="text-xs text-slate-700">Supprimer ce cours ?</span>
                          <span class="flex gap-1">
                            <button
                              type="button"
                              data-confirm-delete
                              [disabled]="deleting()"
                              (click)="confirmDelete(course)"
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
                          (click)="editing.set({ course })"
                          class="rounded-md border border-slate-300 px-3 py-1 text-xs font-medium hover:bg-slate-100"
                        >
                          Modifier
                        </button>
                        <button
                          type="button"
                          data-delete
                          (click)="pendingDeleteId.set(course.id)"
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
        } @else {
          <p class="rounded-lg border border-dashed border-slate-300 bg-white px-4 py-8 text-center text-sm text-slate-500">
            Aucun cours pour cette filière.
          </p>
        }
      </div>
    }
  `,
})
export class ProgramCourses {
  private readonly service = inject(CourseService);

  readonly program = input.required<Program>();
  /** The user wants the programs list back. */
  readonly back = output<void>();
  /** A course was saved or deleted: the page must reload the programs to refresh `program`. */
  readonly changed = output<void>();

  readonly courses = computed<Course[]>(() =>
    [...(this.program().courses ?? [])].sort((a, b) => a.name.localeCompare(b.name, 'fr')),
  );

  readonly error = signal('');
  readonly notice = signal('');
  /** `{ course: null }` is the create form; null shows the list. */
  readonly editing = signal<{ course: Course | null } | null>(null);
  readonly pendingDeleteId = signal<number | null>(null);
  readonly deleting = signal(false);

  onSaved() {
    this.editing.set(null);
    this.notice.set('Cours enregistré.');
    this.changed.emit();
  }

  confirmDelete(course: Course) {
    if (this.deleting()) return;
    this.deleting.set(true);
    this.error.set('');
    this.notice.set('');
    this.service.delete(course.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.pendingDeleteId.set(null);
        this.notice.set('Cours supprimé.');
        this.changed.emit();
      },
      error: (err: unknown) => {
        this.deleting.set(false);
        this.pendingDeleteId.set(null);
        this.error.set(messageFrom(err, 'La suppression a échoué.'));
      },
    });
  }
}
