import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Accreditation } from '@models/accreditation';
import { CourseAccreditation } from '@models/course';
import { ProgramAccreditation } from '@models/program';
import { Observable, map } from 'rxjs';
import { AccreditationService } from '@services/accreditation.service';
import { CourseService } from '@services/course.service';
import { ProgramService } from '@services/program.service';
import { Alert } from '@shared/components/alert/alert';

const YEAR = /^\d{4}$/;
const ACCREDITATIONS_SIZE = 200;
type AccreditationLink = CourseAccreditation | ProgramAccreditation;

/** Shared editor for direct program and course accreditation links. */
@Component({
  selector: 'app-accreditation-links',
  host: { class: 'block' },
  imports: [ReactiveFormsModule, Alert],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fieldset class="space-y-3">
      <legend class="text-sm font-medium text-slate-700">Accréditations{{ kind() === 'course' ? ' du cours' : '' }}</legend>
      <app-alert [message]="error()" />
      @if (notice()) {
        <div role="status" data-notice class="rounded-md border border-emerald-300 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
          {{ notice() }}
        </div>
      }

      @if (links().length) {
        <ul class="divide-y divide-slate-100 rounded-md border border-slate-200 bg-white text-sm">
          @for (link of links(); track link.accreditationId) {
            <li data-link-row class="flex items-center justify-between gap-3 px-3 py-2">
              <span>
                <span class="font-medium text-slate-900">{{ nameOf(link) }}</span>
                <span class="ml-2 text-slate-500">{{ link.startsAt }} – {{ link.endsAt }}</span>
              </span>
              <button
                type="button"
                data-unlink
                [disabled]="busy()"
                (click)="unlink(link)"
                class="rounded-md border border-red-300 px-3 py-1 text-xs font-medium text-red-700 hover:bg-red-50 disabled:opacity-60"
              >
                Retirer
              </button>
            </li>
          }
        </ul>
      } @else if (!loading()) {
        <p class="text-sm text-slate-500">Aucune accréditation associée.</p>
      }

      <div [formGroup]="form" class="flex flex-wrap items-end gap-3" (keydown.enter)="$event.preventDefault(); link()">
        <div class="space-y-1">
          <label [attr.for]="fieldId('accreditation')" class="block text-sm text-slate-700">Accréditation</label>
          <select
            [id]="fieldId('accreditation')"
            formControlName="accreditationId"
            class="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          >
            <option value="">Choisir…</option>
            @for (a of available(); track a.id) {
              <option [value]="a.id">{{ a.name }}</option>
            }
          </select>
        </div>
        <div class="space-y-1">
          <label [attr.for]="fieldId('startsAt')" class="block text-sm text-slate-700">Début (année)</label>
          <input
            [id]="fieldId('startsAt')"
            type="text"
            inputmode="numeric"
            placeholder="2020"
            formControlName="startsAt"
            class="w-24 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>
        <div class="space-y-1">
          <label [attr.for]="fieldId('endsAt')" class="block text-sm text-slate-700">Fin (année)</label>
          <input
            [id]="fieldId('endsAt')"
            type="text"
            inputmode="numeric"
            placeholder="2025"
            formControlName="endsAt"
            class="w-24 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>
        <button
          type="button"
          data-link
          [disabled]="busy()"
          (click)="link()"
          class="rounded-md border border-slate-300 px-3 py-2 text-sm font-medium hover:bg-slate-100 disabled:opacity-60"
        >
          Associer
        </button>
      </div>
      @if (linkError()) {
        <p class="text-xs text-red-700" data-link-error>{{ linkError() }}</p>
      }
    </fieldset>
  `,
})
export class AccreditationLinks implements OnInit {
  private readonly courses = inject(CourseService);
  private readonly programs = inject(ProgramService);
  private readonly accreditations = inject(AccreditationService);

  readonly kind = input.required<'course' | 'program'>();
  readonly resourceId = input.required<number>();

  readonly form = inject(FormBuilder).nonNullable.group({
    accreditationId: [''],
    startsAt: [''],
    endsAt: [''],
  });

  readonly links = signal<AccreditationLink[]>([]);
  private readonly known = signal<Accreditation[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly linkError = signal('');

  readonly available = computed(() => {
    const taken = new Set(this.links().map((l) => l.accreditationId));
    return this.known().filter((a) => !taken.has(a.id));
  });

  ngOnInit() {
    const failed = () => this.error.set('Impossible de charger les accréditations.');
    this.loadLinks().subscribe({
      next: (links) => {
        this.links.set(links);
        for (const link of links) {
          if (link.accreditation) this.remember(link.accreditation);
        }
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        failed();
      },
    });
    // The endpoint is paginated: one large page feeds the select.
    this.accreditations.list(0, ACCREDITATIONS_SIZE).subscribe({
      next: (res) => res.content.forEach((accreditation) => this.remember(accreditation)),
      error: failed,
    });
  }

  nameOf(link: AccreditationLink): string {
    return link.accreditation?.name ?? this.known().find((a) => a.id === link.accreditationId)?.name ?? `n° ${link.accreditationId}`;
  }

  private remember(accreditation: Accreditation) {
    this.known.update((items) => [...items.filter((item) => item.id !== accreditation.id), accreditation]);
  }

  link() {
    if (this.busy()) return;
    const { accreditationId, startsAt, endsAt } = this.form.getRawValue();
    const problem = this.validate(accreditationId, startsAt.trim(), endsAt.trim());
    this.linkError.set(problem);
    if (problem) return;

    this.busy.set(true);
    this.error.set('');
    this.notice.set('');
    this.linkResource(Number(accreditationId), Number(startsAt), Number(endsAt)).subscribe({
      next: (created) => {
        this.busy.set(false);
        this.links.update((list) => [...list, created]);
        this.form.reset();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.error.set(err instanceof HttpErrorResponse && err.status === 409
          ? `Cette accréditation est déjà associée au ${this.kind() === 'course' ? 'cours' : 'programme'}.`
          : "L'association a échoué.");
      },
    });
  }

  unlink(link: AccreditationLink) {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.notice.set('');
    this.unlinkResource(link.accreditationId).subscribe({
      next: () => {
        this.busy.set(false);
        this.links.update((list) => list.filter((l) => l.accreditationId !== link.accreditationId));
        this.notice.set('Lien retiré. L’accréditation reste disponible.');
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.error.set(err instanceof HttpErrorResponse && err.status === 404
          ? 'Association introuvable. Actualisez la page puis réessayez.'
          : "La suppression de l'association a échoué.");
      },
    });
  }

  fieldId(field: string): string {
    return this.kind() === 'course' ? `course-${field}` : field;
  }

  private loadLinks(): Observable<AccreditationLink[]> {
    return this.kind() === 'course'
      ? this.courses.accreditationsOf(this.resourceId()).pipe(map((page) => page.content))
      : this.programs.accreditationsOf(this.resourceId());
  }

  private linkResource(accreditationId: number, startsAt: number, endsAt: number): Observable<AccreditationLink> {
    return this.kind() === 'course'
      ? this.courses.link(this.resourceId(), accreditationId, startsAt, endsAt)
      : this.programs.link(this.resourceId(), accreditationId, startsAt, endsAt);
  }

  private unlinkResource(accreditationId: number): Observable<void> {
    return this.kind() === 'course'
      ? this.courses.unlink(this.resourceId(), accreditationId)
      : this.programs.unlink(this.resourceId(), accreditationId);
  }

  private validate(accreditationId: string, startsAt: string, endsAt: string): string {
    if (!accreditationId) return 'Choisissez une accréditation.';
    if (!YEAR.test(startsAt) || !YEAR.test(endsAt)) return 'Indiquez les années de début et de fin (4 chiffres).';
    if (Number(endsAt) < Number(startsAt)) return 'La fin doit être après (ou égale à) le début.';
    return '';
  }
}
