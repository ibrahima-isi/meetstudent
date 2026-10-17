import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { LucideAngularModule, Star } from 'lucide-angular';
import { EMPTY, Subscription, expand, map } from 'rxjs';
import { pluralKey } from '@i18n/plural';
import { Page, School } from '@models/entities';
import { LocaleService } from '@services/locale.service';
import { SchoolService } from '@services/school.service';
import { ErrorStateComponent } from '@shared/components/error-state/error-state.component';
import { roundRating } from '@shared/format-rating';

/** The top ten, and the size of every following page. */
const PAGE_SIZE = 10;

/**
 * Public ranking of the schools. The order is the server's (average rating
 * descending, unrated last, then name, then id), so the page never sorts and
 * never ranks a partial client array: "See more" and "See all" just ask the
 * next server pages. A school the next page repeats, because the ranking moved
 * between two requests, keeps its first rank.
 */
@Component({
  selector: 'app-schools-ranking-page',
  imports: [RouterLink, TranslocoDirective, LucideAngularModule, ErrorStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './schools-ranking-page.component.html',
})
export class SchoolsRankingPageComponent implements OnInit {
  private readonly schoolService = inject(SchoolService);
  private readonly locale = inject(LocaleService);

  protected readonly Star = Star;
  protected readonly roundRating = roundRating;
  protected readonly skeletons = [0, 1, 2];

  /** 'loading' until the first page; 'error' shows the retry surface, never placeholder data. */
  protected readonly status = signal<'loading' | 'loaded' | 'error'>('loading');
  protected readonly schools = signal<School[]>([]);
  protected readonly totalElements = signal<number | undefined>(undefined);
  private readonly lastPage = signal(true);
  /** The next server page to ask; only moves when a page has been received. */
  private nextPage = 0;

  /** A page after the first is being fetched (one by one for "See all"). */
  protected readonly busy = signal(false);
  protected readonly moreFailed = signal(false);
  protected readonly hasMore = computed(() => !this.lastPage());
  /** What a retry after a failed continuation repeats. */
  private lastMode: 'more' | 'all' = 'more';
  private request?: Subscription;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.request?.unsubscribe());
  }

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.request?.unsubscribe();
    this.status.set('loading');
    this.schools.set([]);
    this.nextPage = 0;
    this.moreFailed.set(false);
    this.busy.set(false);
    this.request = this.fetch(0).subscribe({
      next: (page) => this.receive(page),
      error: () => this.status.set('error'),
      complete: () => this.status.update((s) => (s === 'loading' ? 'loaded' : s)),
    });
  }

  protected loadMore(): void {
    this.continue('more');
  }

  protected loadAll(): void {
    this.continue('all');
  }

  protected retryMore(): void {
    this.continue(this.lastMode);
  }

  protected rated(school: School): boolean {
    return typeof school.rating === 'number' && Number.isFinite(school.rating);
  }

  protected lang(): string {
    return this.locale.active();
  }

  protected plural(base: string, count: number): string {
    return pluralKey(base, count, this.locale.active());
  }

  private continue(mode: 'more' | 'all'): void {
    if (this.busy() || this.lastPage()) return;
    this.lastMode = mode;
    this.busy.set(true);
    this.moreFailed.set(false);
    const first = this.fetch(this.nextPage);
    this.request?.unsubscribe();
    this.request = (mode === 'all' ? first.pipe(expand((page) => (this.isLast(page) ? EMPTY : this.fetch(this.nextPage)))) : first).subscribe({
      next: (page) => this.receive(page),
      error: () => {
        this.busy.set(false);
        this.moreFailed.set(true);
      },
      complete: () => this.busy.set(false),
    });
  }

  private fetch(page: number) {
    return this.schoolService.getSchools(page, PAGE_SIZE, 'most').pipe(map((result) => result as Page<School>));
  }

  /** An empty page ends the walk too, so a server that forgets `last` cannot loop forever. */
  private isLast(page: Page<School>): boolean {
    return page.last === true || page.content.length === 0;
  }

  private receive(page: Page<School>): void {
    const known = new Set(this.schools().map((school) => school.id));
    this.schools.update((list) => [...list, ...page.content.filter((school) => !known.has(school.id))]);
    this.totalElements.set(page.totalElements);
    this.lastPage.set(this.isLast(page));
    this.nextPage += 1;
  }
}
