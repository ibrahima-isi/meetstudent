import { Component, signal, computed, inject, OnInit, Injector, DestroyRef } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Observable, Subject, catchError, debounceTime, filter, map, merge, of, switchMap, tap } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule, Search, MapPin, Star, Filter, ArrowUpDown } from 'lucide-angular';
import { ImageWithFallbackComponent } from '@shared/components/image-with-fallback/image-with-fallback.component';
import { ErrorStateComponent } from '@shared/components/error-state/error-state.component';
import { Page, School } from '@models/entities';
import { SchoolService } from '@services/school.service';
import { LocaleService } from '@services/locale.service';
import { TranslocoDirective } from '@jsverse/transloco';
import { pluralKey } from '@i18n/plural';
import { roundRating } from '@shared/format-rating';

/** Quiet time after the last keystroke before the URL (and so the API) is updated. */
const SEARCH_DEBOUNCE_MS = 300;
const PAGE_SIZE = 12;

@Component({
  selector: 'app-schools-page',
  imports: [
    CommonModule,
    FormsModule,
    LucideAngularModule,
    ImageWithFallbackComponent,
    TranslocoDirective,
    ErrorStateComponent,
  ],
  templateUrl: './schools-page.component.html'
})
export class SchoolsPageComponent implements OnInit {
  private schoolService = inject(SchoolService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly locale = inject(LocaleService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  readonly Search = Search;
  readonly MapPin = MapPin;
  readonly Star = Star;
  readonly Filter = Filter;
  readonly ArrowUpDown = ArrowUpDown;

  schools = signal<School[]>([]);
  /** What the input shows right now; the API only hears the `term` the URL carries. */
  searchQuery = signal('');
  /** '' means no filter; the template labels that choice in the active language. */
  selectedCity = signal('');
  selectedType = signal('');
  showFilters = signal(false);
  sortBy = signal<'name' | 'city'>('name');

  /** 'loading' until the first answer; 'error' shows the retry surface, never placeholder data. */
  status = signal<'loading' | 'loaded' | 'error'>('loading');
  /** Placeholder cards shown while loading. */
  protected readonly skeletons = [0, 1, 2];

  /** Trimmed `?q=`: the URL is the source of truth for the search. */
  private readonly term = signal('');
  private readonly pageNumber = signal(0);
  private readonly lastPage = signal(true);
  private readonly totalElements = signal<number | undefined>(undefined);
  protected readonly loadingMore = signal(false);
  protected readonly loadMoreFailed = signal(false);
  protected readonly hasMore = computed(() => !this.lastPage());

  private readonly typed$ = new Subject<string>();
  private readonly pageRequests$ = new Subject<number>();
  /** The term this page last wrote to the URL, so its echo does not overwrite the input. */
  private pushedTerm: string | null = null;
  /** Every city seen so far, so narrowing by city does not remove the other choices. */
  private readonly knownCities = signal<string[]>([]);

  /** What the server is asked. Changing any of it restarts from page 0. */
  private readonly criteria = computed(
    () => ({ term: this.term(), city: this.selectedCity(), sort: this.serverSort(this.sortBy()) }),
    { equal: (a, b) => a.term === b.term && a.city === b.city && a.sort === b.sort },
  );

  constructor() {
    this.typed$
      .pipe(
        debounceTime(SEARCH_DEBOUNCE_MS),
        map((v) => v.trim()),
        // Compared with the term in force, not with the previous keystroke: the
        // URL can clear it behind the input's back, and the same text must then search again.
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

    // One pipeline for first load, criteria changes, retry and load more.
    // switchMap cancels the request in flight, so a slow answer to an old
    // search can never overwrite a newer one.
    merge(toObservable(this.criteria, { injector: this.injector }).pipe(map(() => 0)), this.pageRequests$)
      .pipe(
        tap((page) => this.begin(page)),
        switchMap((page) =>
          this.fetch(this.criteria(), page).pipe(
            map((result) => ({ page, result })),
            catchError(() => of({ page, result: null })),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ page, result }) => this.finish(page, result));
  }

  ngOnInit(): void {
    // Read synchronously so the first request (SSR included) already carries the
    // term and needs no debounce.
    this.applyUrlTerm(this.route.snapshot.queryParamMap.get('q'));
    // External navigation (hero deep link, Back) moves the term later on.
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.applyUrlTerm(params.get('q'));
    });
  }

  private applyUrlTerm(raw: string | null): void {
    const value = raw ?? '';
    const term = value.trim();
    // The echo of what the visitor just typed must not fight their next keystrokes.
    if (term !== this.pushedTerm) {
      this.searchQuery.set(value);
    }
    this.pushedTerm = null;
    this.term.set(term);
  }

  protected onSearchInput(value: string): void {
    this.searchQuery.set(value);
    this.typed$.next(value);
  }

  /** Retry action of the error state: refetches the first page with the current criteria. */
  protected load(): void {
    this.pageRequests$.next(0);
  }

  protected loadMore(): void {
    this.pageRequests$.next(this.pageNumber() + 1);
  }

  private serverSort(sort: 'name' | 'city'): string {
    return sort === 'city' ? 'address.city,asc' : 'name,asc';
  }

  private fetch(criteria: { term: string; city: string; sort: string }, page: number): Observable<Page<School>> {
    const { term, city, sort } = criteria;
    if (term) {
      return this.schoolService.searchSchoolsByName(term, page, PAGE_SIZE, sort);
    }
    if (city) {
      return this.schoolService.searchSchools(city, undefined, undefined, undefined, page, PAGE_SIZE, sort);
    }
    return this.schoolService.getSchools(page, PAGE_SIZE, undefined, sort);
  }

  private begin(page: number): void {
    if (page === 0) {
      this.status.set('loading');
      this.loadingMore.set(false);
      this.loadMoreFailed.set(false);
    } else {
      this.loadingMore.set(true);
      this.loadMoreFailed.set(false);
    }
  }

  private finish(page: number, result: Page<School> | null): void {
    this.loadingMore.set(false);
    if (result === null) {
      if (page === 0) {
        this.schools.set([]);
        this.lastPage.set(true);
        this.status.set('error');
      } else {
        this.loadMoreFailed.set(true);
      }
      return;
    }
    const content = result.content ?? [];
    this.schools.update((current) => (page === 0 ? content : [...current, ...content]));
    this.pageNumber.set(page);
    this.lastPage.set(result.last ?? true);
    this.totalElements.set(result.totalElements);
    this.rememberCities(content);
    this.status.set('loaded');
  }

  private rememberCities(content: School[]): void {
    const seen = content.map((school) => school.address?.city).filter((c): c is string => !!c);
    this.knownCities.update((known) => [...new Set([...known, ...seen])]);
  }

  /** Filter choices come from the schools loaded so far, not a bundled list. */
  cities = computed(() => [...this.knownCities()].sort((a, b) => a.localeCompare(b, this.locale.active())));
  types = computed(() => this.distinct((school) => school.type));

  private distinct(pick: (school: School) => string | undefined): string[] {
    const values = this.schools().map(pick).filter((v): v is string => !!v);
    return [...new Set(values)].sort((a, b) => a.localeCompare(b, this.locale.active()));
  }

  activeFiltersCount = computed(() => {
    let count = 0;
    if (this.selectedCity() !== '') count++;
    if (this.selectedType() !== '') count++;
    return count;
  });

  protected readonly hasFilters = computed(() => this.term() !== '' || this.activeFiltersCount() > 0);

  /**
   * The API already searched and sorted. What it cannot do stays here, on the
   * pages loaded so far: the type (not a server field), and the city when a
   * name search is active (the name endpoint takes no city).
   */
  sortedSchools = computed(() => {
    const city = this.selectedCity();
    const type = this.selectedType();
    const cityOnClient = city !== '' && this.term() !== '';

    return this.schools().filter(
      (school: School) =>
        (!cityOnClient || school.address?.city === city) && (type === '' || school.type === type),
    );
  });

  /** The server's total while nothing is narrowed on the client; otherwise what is visible. */
  protected readonly resultCount = computed(() => {
    const narrowedOnClient = this.selectedType() !== '' || (this.selectedCity() !== '' && this.term() !== '');
    return narrowedOnClient ? this.sortedSchools().length : (this.totalElements() ?? this.sortedSchools().length);
  });

  /**
   * A school the API returned always carries an id; the type says otherwise
   * because the same interface is used for writes. Without one there is no
   * address to route to, so the click is dropped rather than sent to `/schools/`.
   */
  protected openSchool(school: School): void {
    if (school.id !== undefined) {
      this.goTo('schools', school.id);
    }
  }

  protected readonly roundRating = roundRating;

  /** The key for a count, by the plural rule of the language being read. */
  protected plural(base: string, count: number): string {
    return pluralKey(base, count, this.locale.active());
  }

  /** Navigations stay in the language the visitor is reading. */
  protected goTo(...segments: (string | number)[]): void {
    void this.router.navigate(['/', this.locale.active(), ...segments]);
  }
}
