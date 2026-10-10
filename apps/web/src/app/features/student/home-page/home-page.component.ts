import { Component, signal, computed, inject } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Observable, Subject, catchError, debounceTime, distinctUntilChanged, map, merge, of, switchMap, tap } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LucideAngularModule, Search, MapPin, Star, Filter, ArrowUpDown, Heart } from 'lucide-angular';
import { ImageWithFallbackComponent } from '@shared/components/image-with-fallback/image-with-fallback.component';
import { StarRatingComponent } from '@shared/components/star-rating/star-rating.component';
import { ErrorStateComponent } from '@shared/components/error-state/error-state.component';
import { Page, School } from '@models/entities';
import { SchoolService } from '@services/school.service';
import { LocaleService } from '@services/locale.service';
import { TranslocoDirective } from '@jsverse/transloco';
import { pluralKey } from '@i18n/plural';
import { TokenService } from '@services/token.service';
import { WishlistService } from '@services/wishlist.service';

/** Quiet time after the last keystroke before the API is asked. */
const SEARCH_DEBOUNCE_MS = 300;
const PAGE_SIZE = 12;

@Component({
  selector: 'app-home-page',
  imports: [CommonModule, FormsModule, LucideAngularModule, ImageWithFallbackComponent, StarRatingComponent, TranslocoDirective, ErrorStateComponent],
  templateUrl: './home-page.component.html'
})
export class HomePageComponent {
  private schoolService = inject(SchoolService);
  private readonly tokenService = inject(TokenService);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  /**
   * Loaded by this page on entry too: at login the tokens are set before the
   * user, so the navbar cart's own load can run with no user id and do nothing.
   * Concurrent loads are collapsed by the service.
   */
  protected readonly wishlist = inject(WishlistService);

  readonly Search = Search;
  readonly MapPin = MapPin;
  readonly Star = Star;
  readonly Filter = Filter;
  readonly ArrowUpDown = ArrowUpDown;
  readonly Heart = Heart;

  schools = signal<School[]>([]);
  /** What the input shows right now; the API only hears the debounced `term`. */
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

  /** Trimmed search text, settled after the visitor stops typing. */
  private readonly term = signal('');
  private readonly pageNumber = signal(0);
  private readonly lastPage = signal(true);
  private readonly totalElements = signal<number | undefined>(undefined);
  protected readonly loadingMore = signal(false);
  protected readonly loadMoreFailed = signal(false);
  protected readonly hasMore = computed(() => !this.lastPage());

  private readonly typed$ = new Subject<string>();
  private readonly pageRequests$ = new Subject<number>();
  /** Every city seen so far, so narrowing by city does not remove the other choices. */
  private readonly knownCities = signal<string[]>([]);

  /** What the server is asked. Changing any of it restarts from page 0. */
  private readonly criteria = computed(
    () => ({ term: this.term(), city: this.selectedCity(), sort: this.serverSort(this.sortBy()) }),
    { equal: (a, b) => a.term === b.term && a.city === b.city && a.sort === b.sort },
  );

  constructor() {
    if (this.tokenService.isAuthenticated()) {
      this.wishlist.load();
    }

    this.typed$
      .pipe(debounceTime(SEARCH_DEBOUNCE_MS), map((v) => v.trim()), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((term) => this.term.set(term));

    // One pipeline for first load, criteria changes, retry and load more.
    // switchMap cancels the request in flight, so a slow answer to an old
    // search can never overwrite a newer one.
    merge(toObservable(this.criteria).pipe(map(() => 0)), this.pageRequests$)
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

  /** Filter choices come from the schools actually loaded, not a bundled list. */
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
   * pages loaded so far: the type (not a server field), the city when a name
   * search is active (the name endpoint takes no city).
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

  /** The key for a count, by the plural rule of the language being read. */
  protected plural(base: string, count: number): string {
    return pluralKey(base, count, this.locale.active());
  }

  /** Navigations stay in the language the visitor is reading. */
  protected goTo(...segments: (string | number)[]): void {
    void this.router.navigate(['/', this.locale.active(), ...segments]);
  }
}
