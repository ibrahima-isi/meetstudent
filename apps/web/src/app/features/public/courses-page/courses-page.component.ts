import { ChangeDetectionStrategy, Component, DestroyRef, Injector, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { LucideAngularModule, GraduationCap, Search, Star } from 'lucide-angular';
import { Observable, Subject, catchError, debounceTime, filter, map, merge, of, switchMap, tap } from 'rxjs';
import { pluralKey } from '@i18n/plural';
import { Page, Course } from '@models/entities';
import { LocaleService } from '@services/locale.service';
import { MediaService } from '@services/media.service';
import { CourseService } from '@services/course.service';
import { ErrorStateComponent } from '@shared/components/error-state/error-state.component';
import { ImageWithFallbackComponent } from '@shared/components/image-with-fallback/image-with-fallback.component';
import { parseIdList } from '@shared/compare-selection';
import { roundRating } from '@shared/format-rating';

/** Quiet time after the last keystroke before the URL (and so the API) is updated. */
const SEARCH_DEBOUNCE_MS = 300;
const PAGE_SIZE = 12;
/** A comparison holds 2 to 3 courses. */
const COMPARE_MIN = 2;
const COMPARE_MAX = 3;

@Component({
  selector: 'app-courses-page',
  imports: [FormsModule, LucideAngularModule, TranslocoDirective, ErrorStateComponent, ImageWithFallbackComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './courses-page.component.html',
})
export class CoursesPageComponent implements OnInit {
  private readonly courseService = inject(CourseService);
  private readonly mediaService = inject(MediaService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly locale = inject(LocaleService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  protected readonly Search = Search;
  protected readonly Star = Star;
  protected readonly GraduationCap = GraduationCap;

  protected readonly courses = signal<Course[]>([]);
  /** What the input shows right now; the API only hears the `term` the URL carries. */
  readonly searchQuery = signal('');
  /** 'loading' until the first answer; 'error' shows the retry surface, never placeholder data. */
  protected readonly status = signal<'loading' | 'loaded' | 'error'>('loading');
  protected readonly skeletons = [0, 1, 2];

  /** Trimmed `?q=`: the URL is the source of truth for the search. */
  private readonly term = signal('');
  private readonly pageNumber = signal(0);
  private readonly lastPage = signal(true);
  private readonly totalElements = signal<number | undefined>(undefined);
  protected readonly loadingMore = signal(false);
  protected readonly loadMoreFailed = signal(false);
  protected readonly hasMore = computed(() => !this.lastPage());
  protected readonly resultCount = computed(() => this.totalElements() ?? this.courses().length);

  /** Ids picked for comparison; the `?compare=` URL parameter is the source of truth. */
  protected readonly selectedIds = signal<number[]>([]);
  protected readonly limitReached = signal(false);
  protected readonly canCompare = computed(
    () => this.selectedIds().length >= COMPARE_MIN && this.selectedIds().length <= COMPARE_MAX,
  );

  private readonly typed$ = new Subject<string>();
  private readonly pageRequests$ = new Subject<number>();
  /** The term this page last wrote to the URL, so its echo does not overwrite the input. */
  private pushedTerm: string | null = null;

  constructor() {
    this.typed$
      .pipe(
        debounceTime(SEARCH_DEBOUNCE_MS),
        map((v) => v.trim()),
        filter((v) => v !== this.term()),
        takeUntilDestroyed(),
      )
      .subscribe((term) => {
        this.pushedTerm = term;
        void this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { q: term || null },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        });
      });

    // One pipeline for first load, term change, retry and load more. switchMap
    // cancels the request in flight, so a slow answer to an old search never
    // overwrites a newer one.
    merge(toObservable(this.term, { injector: this.injector }).pipe(map(() => 0)), this.pageRequests$)
      .pipe(
        tap((page) => this.begin(page)),
        switchMap((page) =>
          this.fetch(this.term(), page).pipe(
            map((result) => ({ page, result })),
            catchError(() => of({ page, result: null })),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ page, result }) => this.finish(page, result));
  }

  ngOnInit(): void {
    this.applyUrlTerm(this.route.snapshot.queryParamMap.get('q'));
    this.selectedIds.set(parseIdList(this.route.snapshot.queryParamMap.get('compare'), COMPARE_MAX));
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.applyUrlTerm(params.get('q'));
      this.selectedIds.set(parseIdList(params.get('compare'), COMPARE_MAX));
    });
  }

  protected isSelected(course: Course): boolean {
    return course.id !== undefined && this.selectedIds().includes(course.id);
  }

  protected toggleSelection(course: Course, event: Event): void {
    const id = course.id;
    if (id === undefined) return;
    const current = this.selectedIds();
    let next: number[];
    if (current.includes(id)) {
      next = current.filter((other) => other !== id);
      this.limitReached.set(false);
    } else if (current.length >= COMPARE_MAX) {
      // The box was ticked by the click; the URL stays as it is, so untick it again.
      (event.target as HTMLInputElement).checked = false;
      this.limitReached.set(true);
      return;
    } else {
      next = [...current, id];
      this.limitReached.set(false);
    }
    this.writeSelection(next);
  }

  protected clearSelection(): void {
    this.limitReached.set(false);
    this.writeSelection([]);
  }

  private writeSelection(ids: number[]): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { compare: ids.length > 0 ? ids.join(',') : null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected compare(): void {
    if (!this.canCompare()) return;
    void this.router.navigate(['/', this.locale.active(), 'courses', 'compare'], {
      queryParams: { ids: this.selectedIds().join(',') },
    });
  }

  private applyUrlTerm(raw: string | null): void {
    const value = raw ?? '';
    // The echo of what the visitor just typed must not fight their next keystrokes.
    if (value.trim() !== this.pushedTerm) {
      this.searchQuery.set(value);
    }
    this.pushedTerm = null;
    this.term.set(value.trim());
  }

  protected onSearchInput(value: string): void {
    this.searchQuery.set(value);
    this.typed$.next(value);
  }

  /** Retry action of the error state: refetches the first page. */
  protected load(): void {
    this.pageRequests$.next(0);
  }

  protected loadMore(): void {
    this.pageRequests$.next(this.pageNumber() + 1);
  }

  private fetch(term: string, page: number): Observable<Page<Course>> {
    return term
      ? this.courseService.searchCoursesByName(term, page, PAGE_SIZE)
      : this.courseService.listCourses(page, PAGE_SIZE);
  }

  private begin(page: number): void {
    if (page === 0) {
      this.status.set('loading');
      this.loadingMore.set(false);
    } else {
      this.loadingMore.set(true);
    }
    this.loadMoreFailed.set(false);
  }

  private finish(page: number, result: Page<Course> | null): void {
    this.loadingMore.set(false);
    if (result === null) {
      if (page === 0) {
        this.courses.set([]);
        this.lastPage.set(true);
        this.totalElements.set(undefined);
        this.status.set('error');
      } else {
        this.loadMoreFailed.set(true);
      }
      return;
    }
    const content = result.content ?? [];
    this.courses.update((current) => {
      if (page === 0) return content;
      const seen = new Set(current.map((c) => c.id));
      return [...current, ...content.filter((c) => !seen.has(c.id))];
    });
    this.pageNumber.set(page);
    this.lastPage.set(result.last ?? true);
    this.totalElements.set(result.totalElements);
    this.status.set('loaded');
  }

  protected readonly hasTerm = computed(() => this.term() !== '');
  protected readonly roundRating = roundRating;

  protected photoOf(course: Course): string {
    return this.mediaService.resolveUrl(course.photo) ?? course.photoImageUrl ?? '';
  }

  /** The key for a count, by the plural rule of the language being read. */
  protected plural(base: string, count: number): string {
    return pluralKey(base, count, this.locale.active());
  }
}
