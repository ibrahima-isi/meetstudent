import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, Subject, catchError, forkJoin, map, merge, of, switchMap, tap, throwError } from 'rxjs';
import { pluralKey } from '@i18n/plural';
import { School } from '@models/entities';
import { LocaleService } from '@services/locale.service';
import { SchoolService } from '@services/school.service';
import { ErrorStateComponent } from '@shared/components/error-state/error-state.component';
import { roundRating } from '@shared/format-rating';

const MIN_SCHOOLS = 2;
const MAX_SCHOOLS = 3;

type LoadResult =
  | { kind: 'invalid' }
  | { kind: 'error' }
  | { kind: 'loaded'; schools: School[] }
  | { kind: 'notFound'; remaining: number[] };

/**
 * Public side-by-side comparison of 2 to 3 schools, picked on the catalogue and
 * carried in `?ids=`. Ranking: average rating descending, unrated last, then
 * name, then id. Only what the API sends is shown; the rest says so.
 */
@Component({
  selector: 'app-schools-compare-page',
  imports: [RouterLink, TranslocoDirective, ErrorStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './schools-compare-page.component.html',
})
export class SchoolsComparePageComponent {
  private readonly schoolService = inject(SchoolService);
  private readonly route = inject(ActivatedRoute);
  private readonly locale = inject(LocaleService);

  private readonly params = toSignal(this.route.queryParamMap, { initialValue: this.route.snapshot.queryParamMap });
  private readonly retry$ = new Subject<void>();

  /** Whole positive ids without repeats; unreadable parts are ignored. */
  protected readonly ids = computed(() => {
    const parsed = (this.params().get('ids') ?? '')
      .split(',')
      .map((part) => part.trim())
      .filter((part) => /^\d+$/.test(part))
      .map(Number)
      .filter((id) => id > 0);
    return [...new Set(parsed)];
  });
  protected readonly validSelection = computed(
    () => this.ids().length >= MIN_SCHOOLS && this.ids().length <= MAX_SCHOOLS,
  );

  protected readonly status = signal<'loading' | 'loaded' | 'error' | 'notFound'>('loading');
  private readonly loaded = signal<School[]>([]);
  /** After a 404, the ids that still exist, in URL order. */
  protected readonly remaining = signal<number[]>([]);

  /** Best rated first, unrated last; ties by name, then by id. */
  protected readonly schools = computed(() =>
    [...this.loaded()].sort((a, b) => {
      const rateA = this.rateOf(a);
      const rateB = this.rateOf(b);
      if (rateA !== rateB) {
        if (rateA === null) return 1;
        if (rateB === null) return -1;
        return rateB - rateA;
      }
      return a.name.localeCompare(b.name, this.locale.active()) || (a.id ?? 0) - (b.id ?? 0);
    }),
  );

  constructor() {
    // switchMap drops a slow answer to ids that are no longer in the URL.
    merge(toObservable(this.ids), this.retry$.pipe(map(() => this.ids())))
      .pipe(
        tap(() => this.status.set('loading')),
        switchMap((ids): Observable<LoadResult> => {
          if (ids.length < MIN_SCHOOLS || ids.length > MAX_SCHOOLS) return of({ kind: 'invalid' });
          // A 404 is an answer (that school is gone), not an outage: retrying cannot fix it.
          const reads = ids.map((id) =>
            this.schoolService.getSchool(id).pipe(
              catchError((error: unknown) =>
                error instanceof HttpErrorResponse && error.status === 404 ? of(null) : throwError(() => error),
              ),
            ),
          );
          return forkJoin(reads).pipe(
            map((found): LoadResult => {
              const schools = found.filter((school): school is School => school !== null);
              return schools.length === ids.length
                ? { kind: 'loaded', schools }
                : { kind: 'notFound', remaining: ids.filter((_, index) => found[index] !== null) };
            }),
            catchError(() => of<LoadResult>({ kind: 'error' })),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((result) => {
        this.loaded.set(result.kind === 'loaded' ? result.schools : []);
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

  private rateOf(school: School): number | null {
    return typeof school.rating === 'number' && Number.isFinite(school.rating) ? school.rating : null;
  }

  protected readonly roundRating = roundRating;

  protected rated(school: School): boolean {
    return this.rateOf(school) !== null;
  }

  /** Null when the API sent no programme list: unknown, not 0. */
  protected programCount(school: School): number | null {
    return Array.isArray(school.programs) ? school.programs.length : null;
  }

  protected place(school: School): string {
    return [school.address?.city, school.address?.country].filter((part) => !!part).join(', ');
  }

  protected lang(): string {
    return this.locale.active();
  }

  protected plural(base: string, count: number): string {
    return pluralKey(base, count, this.locale.active());
  }
}
