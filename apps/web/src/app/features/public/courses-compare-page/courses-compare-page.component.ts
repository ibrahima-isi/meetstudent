import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { Observable, Subject, catchError, forkJoin, map, merge, of, switchMap, tap, throwError } from 'rxjs';
import { Course } from '@models/entities';
import { LocaleService } from '@services/locale.service';
import { CourseService } from '@services/course.service';
import { ErrorStateComponent } from '@shared/components/error-state/error-state.component';
import { byRatingThenNameThenId, parseIdList } from '@shared/compare-selection';
import { roundRating } from '@shared/format-rating';

const MIN_COURSES = 2;
const MAX_COURSES = 3;

type LoadResult =
  | { kind: 'invalid' }
  | { kind: 'error' }
  | { kind: 'loaded'; courses: Course[] }
  | { kind: 'notFound'; remaining: number[] };

/**
 * Public side-by-side comparison of 2 to 3 courses, picked on the catalogue
 * and carried in `?ids=`. Ranking: average rating descending, unrated last,
 * then name, then id. Only what the API sends is shown; the rest says so.
 * Level, description and school are never shown: the API has none for a course.
 */
@Component({
  selector: 'app-courses-compare-page',
  imports: [RouterLink, TranslocoDirective, ErrorStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './courses-compare-page.component.html',
})
export class CoursesComparePageComponent {
  private readonly courseService = inject(CourseService);
  private readonly route = inject(ActivatedRoute);
  private readonly locale = inject(LocaleService);

  private readonly params = toSignal(this.route.queryParamMap, { initialValue: this.route.snapshot.queryParamMap });
  private readonly retry$ = new Subject<void>();

  /** Whole positive ids without repeats; unreadable parts are ignored. */
  protected readonly ids = computed(() => parseIdList(this.params().get('ids')));
  protected readonly validSelection = computed(
    () => this.ids().length >= MIN_COURSES && this.ids().length <= MAX_COURSES,
  );

  protected readonly status = signal<'loading' | 'loaded' | 'error' | 'notFound'>('loading');
  private readonly loaded = signal<Course[]>([]);
  /** After a 404, the ids that still exist, in URL order. */
  protected readonly remaining = signal<number[]>([]);

  /** Best rated first, unrated last; ties by name, then by id. */
  protected readonly courses = computed(() =>
    [...this.loaded()].sort(byRatingThenNameThenId((course) => this.rateOf(course), this.locale.active())),
  );

  constructor() {
    // switchMap drops a slow answer to ids that are no longer in the URL.
    merge(toObservable(this.ids), this.retry$.pipe(map(() => this.ids())))
      .pipe(
        tap(() => this.status.set('loading')),
        switchMap((ids): Observable<LoadResult> => {
          if (ids.length < MIN_COURSES || ids.length > MAX_COURSES) return of({ kind: 'invalid' });
          // A 404 is an answer (that course is gone), not an outage: retrying cannot fix it.
          const reads = ids.map((id) =>
            this.courseService.getCourse(id).pipe(
              catchError((error: unknown) =>
                error instanceof HttpErrorResponse && error.status === 404 ? of(null) : throwError(() => error),
              ),
            ),
          );
          return forkJoin(reads).pipe(
            map((found): LoadResult => {
              const courses = found.filter((course): course is Course => course !== null);
              return courses.length === ids.length
                ? { kind: 'loaded', courses }
                : { kind: 'notFound', remaining: ids.filter((_, index) => found[index] !== null) };
            }),
            catchError(() => of<LoadResult>({ kind: 'error' })),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((result) => {
        this.loaded.set(result.kind === 'loaded' ? result.courses : []);
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

  /** Null when the API sent no rating: unrated, never 0. */
  protected rateOf(course: Course): number | null {
    return typeof course.averageRate === 'number' && Number.isFinite(course.averageRate) ? course.averageRate : null;
  }

  protected readonly roundRating = roundRating;

  protected lang(): string {
    return this.locale.active();
  }
}
