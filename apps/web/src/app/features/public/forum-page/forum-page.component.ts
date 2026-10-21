import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { LucideAngularModule, MessageSquare, Lock, Plus } from 'lucide-angular';
import { Subject, catchError, combineLatest, map, of, startWith, switchMap, tap } from 'rxjs';
import { pluralKey } from '@i18n/plural';
import { ForumThread } from '@models/forum';
import { ForumService } from '@services/forum.service';
import { LocaleService } from '@services/locale.service';
import { SchoolService } from '@services/school.service';
import { ErrorStateComponent } from '@shared/components/error-state/error-state.component';
import { MeshBackgroundComponent } from '@shared/components/mesh-background/mesh-background.component';
import { injectForumWriteState } from '../forum-shared/forum-access';
import { ForumAuthorComponent } from '../forum-shared/forum-author.component';
import { ComposerSchool, ForumComposerComponent } from '../forum-shared/forum-composer.component';
import { ForumGateComponent } from '../forum-shared/forum-gate.component';

const PAGE_SIZE = 10;
/** The API caps a page at 100: enough for a school picker. */
const SCHOOLS_LIMIT = 100;

const positiveInt = (raw: string | null): number | undefined => (raw !== null && /^\d{1,9}$/.test(raw) && Number(raw) > 0 ? Number(raw) : undefined);

/**
 * The forum: public list of discussions (newest first, paginated, filterable by school). Reading is server-rendered;
 * the thread form only appears for students and approved experts, once the session is known in the browser.
 * Titles, excerpts and names are interpolated as text.
 */
@Component({
  selector: 'app-forum-page',
  imports: [
    RouterLink,
    TranslocoDirective,
    LucideAngularModule,
    ErrorStateComponent,
    MeshBackgroundComponent,
    ForumAuthorComponent,
    ForumComposerComponent,
    ForumGateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'relative isolate block' },
  templateUrl: './forum-page.component.html',
})
export class ForumPageComponent {
  private readonly forum = inject(ForumService);
  private readonly schoolService = inject(SchoolService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);

  protected readonly MessageSquare = MessageSquare;
  protected readonly Lock = Lock;
  protected readonly Plus = Plus;

  protected readonly lang = computed(() => this.locale.active());
  protected readonly writeState = injectForumWriteState();

  protected readonly threads = signal<ForumThread[]>([]);
  protected readonly status = signal<'loading' | 'loaded' | 'error'>('loading');
  protected readonly pageNumber = signal(0);
  protected readonly totalPages = signal(1);
  protected readonly schoolId = signal<number | undefined>(undefined);
  protected readonly schools = signal<ComposerSchool[]>([]);
  protected readonly totalElements = signal(0);
  private readonly schoolsLoaded = signal(false);
  /** The filtered school when it is not among the (at most 100) schools listed. */
  private readonly extraSchool = signal<ComposerSchool | null>(null);
  protected readonly filterOptions = computed(() => {
    const extra = this.extraSchool();
    return extra && !this.schools().some((s) => s.id === extra.id) ? [...this.schools(), extra] : this.schools();
  });
  /** A page number beyond the last one (an old link, a shrunk forum): empty page but threads exist. */
  protected readonly outOfRange = computed(
    () => this.status() === 'loaded' && this.threads().length === 0 && this.totalElements() > 0 && this.pageNumber() >= this.totalPages(),
  );
  protected readonly lastPageParams = computed(() => ({ page: this.totalPages() > 1 ? this.totalPages() - 1 : null }));
  protected readonly composing = signal(false);

  protected readonly hasPrev = computed(() => this.pageNumber() > 0);
  protected readonly hasNext = computed(() => this.pageNumber() + 1 < this.totalPages());
  protected readonly prevParams = computed(() => ({ page: this.pageNumber() > 1 ? this.pageNumber() - 1 : null }));
  protected readonly nextParams = computed(() => ({ page: this.pageNumber() + 1 }));

  private readonly retry$ = new Subject<void>();

  constructor() {
    combineLatest([
      this.route.queryParamMap.pipe(
        map((params) => ({
          page: /^\d{1,6}$/.test(params.get('page') ?? '') ? Number(params.get('page')) : 0,
          school: positiveInt(params.get('school')),
        })),
      ),
      this.retry$.pipe(startWith(undefined)),
    ])
      .pipe(
        map(([query]) => query),
        tap(({ page, school }) => {
          this.status.set('loading');
          this.pageNumber.set(page);
          this.schoolId.set(school);
        }),
        switchMap(({ page, school }) =>
          this.forum.listThreads({ page, size: PAGE_SIZE, schoolId: school }).pipe(catchError(() => of(null))),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((result) => {
        if (result === null) {
          this.threads.set([]);
          this.status.set('error');
          return;
        }
        this.threads.set(result.content);
        this.totalPages.set(Math.max(1, result.totalPages ?? 1));
        this.totalElements.set(result.totalElements ?? result.content.length);
        this.status.set('loaded');
      });

    this.schoolService
      .getSchools(0, SCHOOLS_LIMIT)
      .pipe(
        catchError(() => of(null)),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        if (page === null) return;
        this.schoolsLoaded.set(true);
        this.schools.set(
          page.content
            .map((s) => ({ id: s.id as number, name: s.name }))
            .sort((a, b) => a.name.localeCompare(b.name, this.locale.active())),
        );
      });
  }

  private readonly fetchExtraSchool = effect(() => {
    const id = this.schoolId();
    if (!this.schoolsLoaded() || id === undefined) return;
    if (this.schools().some((s) => s.id === id) || this.extraSchool()?.id === id) return;
    untracked(() =>
      this.schoolService.getSchool(id).subscribe({
        next: (school) => this.extraSchool.set({ id, name: school.name }),
        error: () => this.extraSchool.set({ id, name: `#${id}` }),
      }),
    );
  });

  protected load(): void {
    this.retry$.next();
  }

  protected repliesKey(count: number): string {
    return pluralKey('forum.list.replies', count, this.locale.active());
  }

  /** The school picker changes the URL, which restarts the search from the first page. */
  protected filter(event: Event): void {
    const value = positiveInt((event.target as HTMLSelectElement).value);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { school: value ?? null, page: null }, queryParamsHandling: 'merge' });
  }

  protected created(thread: ForumThread): void {
    void this.router.navigate(['/', this.locale.active(), 'forum', thread.id]);
  }
}
