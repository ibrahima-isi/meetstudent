import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Accreditation } from '@models/accreditation';
import { ProgramAccreditation } from '@models/program';
import { AccreditationService } from '@services/accreditation.service';
import { ProgramService } from '@services/program.service';
import { Alert } from '@shared/components/alert/alert';

const YEAR = /^\d{4}$/;
const ACCREDITATIONS_SIZE = 200;

/** Accreditations of one (already saved) program: each link carries its validity years. */
@Component({
  selector: 'app-program-accreditations',
  host: { class: 'block' },
  imports: [ReactiveFormsModule, Alert],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fieldset class="space-y-3">
      <legend class="text-sm font-medium text-slate-700">Accréditations</legend>
      <app-alert [message]="error()" />

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
          <label for="accreditation" class="block text-sm text-slate-700">Accréditation</label>
          <select
            id="accreditation"
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
          <label for="startsAt" class="block text-sm text-slate-700">Début (année)</label>
          <input
            id="startsAt"
            type="text"
            inputmode="numeric"
            placeholder="2020"
            formControlName="startsAt"
            class="w-24 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>
        <div class="space-y-1">
          <label for="endsAt" class="block text-sm text-slate-700">Fin (année)</label>
          <input
            id="endsAt"
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
export class ProgramAccreditations implements OnInit {
  private readonly service = inject(ProgramService);
  private readonly accreditations = inject(AccreditationService);

  readonly programId = input.required<number>();

  readonly form = inject(FormBuilder).nonNullable.group({
    accreditationId: [''],
    startsAt: [''],
    endsAt: [''],
  });

  readonly links = signal<ProgramAccreditation[]>([]);
  private readonly known = signal<Accreditation[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly linkError = signal('');

  readonly available = computed(() => {
    const taken = new Set(this.links().map((l) => l.accreditationId));
    return this.known().filter((a) => !taken.has(a.id));
  });

  ngOnInit() {
    const failed = () => this.error.set('Impossible de charger les accréditations.');
    this.service.accreditationsOf(this.programId()).subscribe({
      next: (links) => {
        this.links.set(links);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        failed();
      },
    });
    // The endpoint is paginated: one large page feeds the select.
    this.accreditations.list(0, ACCREDITATIONS_SIZE).subscribe({ next: (res) => this.known.set(res.content), error: failed });
  }

  nameOf(link: ProgramAccreditation): string {
    return link.accreditation?.name ?? this.known().find((a) => a.id === link.accreditationId)?.name ?? `n° ${link.accreditationId}`;
  }

  link() {
    if (this.busy()) return;
    const { accreditationId, startsAt, endsAt } = this.form.getRawValue();
    const problem = this.validate(accreditationId, startsAt.trim(), endsAt.trim());
    this.linkError.set(problem);
    if (problem) return;

    this.busy.set(true);
    this.error.set('');
    this.service.link(this.programId(), Number(accreditationId), Number(startsAt), Number(endsAt)).subscribe({
      next: (created) => {
        this.busy.set(false);
        this.links.update((list) => [...list, created]);
        this.form.reset();
      },
      error: () => {
        this.busy.set(false);
        this.error.set("L'association a échoué.");
      },
    });
  }

  unlink(link: ProgramAccreditation) {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.service.unlink(this.programId(), link.accreditationId).subscribe({
      next: () => {
        this.busy.set(false);
        this.links.update((list) => list.filter((l) => l.accreditationId !== link.accreditationId));
      },
      error: () => {
        this.busy.set(false);
        this.error.set("La suppression de l'association a échoué.");
      },
    });
  }

  private validate(accreditationId: string, startsAt: string, endsAt: string): string {
    if (!accreditationId) return 'Choisissez une accréditation.';
    if (!YEAR.test(startsAt) || !YEAR.test(endsAt)) return 'Indiquez les années de début et de fin (4 chiffres).';
    if (Number(endsAt) < Number(startsAt)) return 'La fin doit être après (ou égale à) le début.';
    return '';
  }
}
