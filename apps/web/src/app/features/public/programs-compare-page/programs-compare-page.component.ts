import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { Observable, Subject, catchError, forkJoin, map, merge, of, switchMap, tap, throwError } from 'rxjs';
import { pluralKey } from '@i18n/plural';
import { Program } from '@models/entities';
import { LocaleService } from '@services/locale.service';
import { ProgramService } from '@services/program.service';
import { ErrorStateComponent } from '@shared/components/error-state/error-state.component';
import { byRatingThenNameThenId, parseIdList } from '@shared/compare-selection';
import { roundRating } from '@shared/format-rating';

const MIN_PROGRAMS = 2;
const MAX_PROGRAMS = 3;

type LoadResult =
  | { kind: 'invalid' }
  | { kind: 'error' }
  | { kind: 'loaded'; programs: Program[] }
  | { kind: 'notFound'; remaining: number[] };

/**
 * Public side-by-side comparison of 2 to 3 programmes, picked on the catalogue
 * and carried in `?ids=`. Ranking: average rating descending, unrated last,
 * then name, then id. Only what the API sends is shown; the rest says so.
 * Level, description, capacity, review count and accreditations are never
 * shown: the API has none (or, for accreditations, the service fills an empty
 * list that would falsely read as "none").
 */
@Component({
  selector: 'app-programs-compare-page',
  imports: [RouterLink, TranslocoDirective, ErrorStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './programs-compare-page.component.html',
})
export class ProgramsComparePageComponent {
  private readonly programService = inject(ProgramService);
  private readonly route = inject(ActivatedRoute);
  private readonly locale = inject(LocaleService);

  private readonly params = toSignal(this.route.queryParamMap, { initialValue: this.route.snapshot.queryParamMap });
  private readonly retry$ = new Subject<void>();

  /** Whole positive ids without repeats; unreadable parts are ignored. */
  protected readonly ids = computed(() => parseIdList(this.params().get('ids')));
  protected readonly validSelection = computed(
    () => this.ids().length >= MIN_PROGRAMS && this.ids().length <= MAX_PROGRAMS,
  );

  protected readonly status = signal<'loading' | 'loaded' | 'error' | 'notFound'>('loading');
  private readonly loaded = signal<Program[]>([]);
  /** After a 404, the ids that still exist, in URL order. */
  protected readonly remaining = signal<number[]>([]);

  /** Best rated first, unrated last; ties by name, then by id. */
  protected readonly programs = computed(() =>
    [...this.loaded()].sort(byRatingThenNameThenId((program) => this.rateOf(program), this.locale.active())),
  );

  constructor() {
    // switchMap drops a slow answer to ids that are no longer in the URL.
    merge(toObservable(this.ids), this.retry$.pipe(map(() => this.ids())))
      .pipe(
        tap(() => this.status.set('loading')),
        switchMap((ids): Observable<LoadResult> => {
          if (ids.length < MIN_PROGRAMS || ids.length > MAX_PROGRAMS) return of({ kind: 'invalid' });
          // A 404 is an answer (that programme is gone), not an outage: retrying cannot fix it.
          const reads = ids.map((id) =>
            this.programService.getProgram(id).pipe(
              catchError((error: unknown) =>
                error instanceof HttpErrorResponse && error.status === 404 ? of(null) : throwError(() => error),
              ),
            ),
          );
          return forkJoin(reads).pipe(
            map((found): LoadResult => {
              const programs = found.filter((program): program is Program => program !== null);
              return programs.length === ids.length
                ? { kind: 'loaded', programs }
                : { kind: 'notFound', remaining: ids.filter((_, index) => found[index] !== null) };
            }),
            catchError(() => of<LoadResult>({ kind: 'error' })),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((result) => {
        this.loaded.set(result.kind === 'loaded' ? result.programs : []);
        this.remaining.set(result.kind === 'notFound' ? result.remaining : []);
        this.status.set(result.kind === 'error' || result.kind === 'notFound' ? result.kind : 'loaded');
      });
  }

  /** The catalogue with the given ids preselected, so the visitor can change the selection. */
  protected catalogueParams(ids: number[]): { compare: string } | null {
    return ids.length > 0 ? { compare: ids.join(',') } : null;
  }

  protected load(): void {
    this.retry$.next();
  }

  private rateOf(program: Program): number | null {
    return typeof program.rating === 'number' && Number.isFinite(program.rating) ? program.rating : null;
  }

  protected readonly roundRating = roundRating;

  protected rated(program: Program): boolean {
    return this.rateOf(program) !== null;
  }

  /** Null when the API sent no duration: unknown. */
  protected duration(program: Program): number | null {
    return typeof program.duration === 'number' && program.duration > 0 ? program.duration : null;
  }

  /** Null when the API sent no course list: unknown, not 0. */
  protected courseCount(program: Program): number | null {
    return Array.isArray(program.courses) ? program.courses.length : null;
  }

  protected lang(): string {
    return this.locale.active();
  }

  protected plural(base: string, count: number): string {
    return pluralKey(base, count, this.locale.active());
  }
}
