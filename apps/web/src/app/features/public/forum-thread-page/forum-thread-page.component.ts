import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, Injector, RESPONSE_INIT, afterNextRender, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { ArrowLeft, Lock, LucideAngularModule, Pencil, Trash2 } from 'lucide-angular';
import { EMPTY, Observable, Subject, Subscription, catchError, combineLatest, map, of, startWith, switchMap, tap } from 'rxjs';
import { pluralKey } from '@i18n/plural';
import { ForumReply, ForumThread } from '@models/forum';
import { ForumService } from '@services/forum.service';
import { LocaleService } from '@services/locale.service';
import { cleanTitle } from '@shared/display-name';
import { PageTitleService } from '@shared/page-title';
import { ErrorStateComponent } from '@shared/components/error-state/error-state.component';
import { MeshBackgroundComponent } from '@shared/components/mesh-background/mesh-background.component';
import { injectForumWriteState } from '../forum-shared/forum-access';
import { ForumAuthorComponent } from '../forum-shared/forum-author.component';
import { ForumComposerComponent } from '../forum-shared/forum-composer.component';
import { classifyForumError } from '../forum-shared/forum-errors';
import { ForumGateComponent } from '../forum-shared/forum-gate.component';
import { ForumReportComponent } from '../forum-shared/forum-report.component';

const REPLIES_PAGE_SIZE = 20;

type Confirming = { kind: 'thread' | 'reply'; id: number };

/**
 * One discussion: the opening message and its replies. Reading is public and server-rendered (404 for an unknown
 * thread). Students and approved experts reply, and edit or delete their own messages (`mine`, set by the server).
 * Every text is interpolated, never bound as HTML, and `whitespace-pre-line` keeps its line breaks.
 */
@Component({
  selector: 'app-forum-thread-page',
  imports: [
    RouterLink,
    TranslocoDirective,
    LucideAngularModule,
    ErrorStateComponent,
    MeshBackgroundComponent,
    ForumAuthorComponent,
    ForumComposerComponent,
    ForumGateComponent,
    ForumReportComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'relative isolate block overflow-hidden' },
  templateUrl: './forum-thread-page.component.html',
})
export class ForumThreadPageComponent {
  private readonly forum = inject(ForumService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly locale = inject(LocaleService);
  private readonly pageTitle = inject(PageTitleService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly responseInit = inject(RESPONSE_INIT, { optional: true });
  private readonly transloco = inject(TranslocoService);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly ArrowLeft = ArrowLeft;
  protected readonly Lock = Lock;
  protected readonly Pencil = Pencil;
  protected readonly Trash2 = Trash2;

  protected readonly lang = computed(() => this.locale.active());
  protected readonly writeState = injectForumWriteState();
  protected readonly canWrite = computed(() => this.writeState() === 'writer');
  /** The API lets any signed-in student or expert report, a pending or rejected expert included; not an administrator. */
  protected readonly canReport = computed(() => ['writer', 'pending', 'rejected'].includes(this.writeState()));

  protected readonly thread = signal<ForumThread | null>(null);
  protected readonly status = signal<'loading' | 'loaded' | 'error' | 'notFound'>('loading');

  protected readonly replies = signal<ForumReply[]>([]);
  protected readonly repliesStatus = signal<'loading' | 'loaded' | 'error'>('loading');
  private readonly nextPage = signal(0);
  protected readonly hasMore = signal(false);
  protected readonly loadingMore = signal(false);
  protected readonly moreFailed = signal(false);

  protected readonly editingThread = signal(false);
  protected readonly editingReplyId = signal<number | null>(null);
  protected readonly confirming = signal<Confirming | null>(null);
  protected readonly actionError = signal<'failed' | 'forbidden' | 'signedOut' | null>(null);
  protected readonly actionErrorKey = computed(() => {
    const error = this.actionError();
    return error === null ? null : error === 'signedOut' ? 'forum.errors.signedOut' : `forum.actions.${error}`;
  });
  protected readonly deleting = signal(false);
  /** Read out by screen readers (polite): the delete question, a published reply. */
  protected readonly liveMessage = signal('');

  private readonly retry$ = new Subject<void>();
  private threadId = 0;
  private repliesSub: Subscription | null = null;

  constructor() {
    combineLatest([this.route.paramMap.pipe(map((params) => params.get('id') ?? '')), this.retry$.pipe(startWith(undefined))])
      .pipe(
        tap(() => {
          this.status.set('loading');
          this.repliesSub?.unsubscribe();
          this.resetState();
        }),
        switchMap(([id]) => {
          // Digits only: Number() would also accept "1e3" or "0x10".
          const numeric = /^\d{1,10}$/.test(id) ? Number(id) : 0;
          if (!Number.isSafeInteger(numeric) || numeric <= 0) {
            this.markNotFound();
            return EMPTY;
          }
          this.threadId = numeric;
          this.loadReplies(0);
          return this.forum.getThread(numeric).pipe(
            catchError((error: { status?: number }) => {
              if (error?.status === 404) {
                this.markNotFound();
                return EMPTY;
              }
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((thread) => {
        if (thread === null) {
          this.thread.set(null);
          this.status.set('error');
          return;
        }
        this.thread.set(thread);
        this.pageTitle.set(cleanTitle(thread.title));
        this.status.set('loaded');
      });

    this.destroyRef.onDestroy(() => this.repliesSub?.unsubscribe());
  }

  /** Retry action of the error state. */
  protected load(): void {
    this.retry$.next();
  }

  protected retryReplies(): void {
    this.loadReplies(0);
  }

  protected loadMore(): void {
    this.loadReplies(this.nextPage());
  }

  private resetState(): void {
    this.replies.set([]);
    this.repliesStatus.set('loading');
    this.hasMore.set(false);
    this.moreFailed.set(false);
    this.editingThread.set(false);
    this.editingReplyId.set(null);
    this.confirming.set(null);
    this.actionError.set(null);
  }

  private loadReplies(page: number): void {
    this.repliesSub?.unsubscribe();
    const first = page === 0;
    if (first) this.repliesStatus.set('loading');
    else {
      this.loadingMore.set(true);
      this.moreFailed.set(false);
    }
    this.repliesSub = this.forum.listReplies(this.threadId, { page, size: REPLIES_PAGE_SIZE }).subscribe({
      next: (result) => {
        this.loadingMore.set(false);
        this.replies.update((current) => {
          if (first) return result.content;
          const known = new Set(current.map((r) => r.id));
          return [...current, ...result.content.filter((r) => !known.has(r.id))];
        });
        this.nextPage.set(page + 1);
        this.hasMore.set(!result.last && page + 1 < (result.totalPages ?? 0));
        this.repliesStatus.set('loaded');
      },
      error: () => {
        this.loadingMore.set(false);
        if (first) this.repliesStatus.set('error');
        else this.moreFailed.set(true);
      },
    });
  }

  /** Unknown thread: no retry, and HTTP 404 when server-rendered (the Response is built from RESPONSE_INIT after rendering). */
  private markNotFound(): void {
    if (this.responseInit) {
      this.responseInit.status = 404;
    }
    this.thread.set(null);
    this.status.set('notFound');
  }

  protected repliesKey(count: number): string {
    return pluralKey('forum.list.replies', count, this.locale.active());
  }

  // ---- own messages -------------------------------------------------------------------------

  /** After a change that removes the focused control, put the focus on a logical neighbour. */
  private focus(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), { injector: this.injector });
  }

  protected threadSaved(saved: ForumThread): void {
    this.thread.set(saved);
    this.pageTitle.set(cleanTitle(saved.title));
    this.editingThread.set(false);
    this.focus('[data-testid="forum-edit"]');
  }

  protected cancelThreadEdit(): void {
    this.editingThread.set(false);
    this.focus('[data-testid="forum-edit"]');
  }

  protected replyCreated(reply: ForumReply): void {
    this.replies.update((list) => [reply, ...list]);
    this.thread.update((t) => (t ? { ...t, replyCount: t.replyCount + 1 } : t));
    this.liveMessage.set(this.transloco.translate('forum.thread.replyPosted'));
    this.focus(`[data-reply-id="${reply.id}"]`);
  }

  protected replySaved(saved: ForumReply): void {
    this.replies.update((list) => list.map((r) => (r.id === saved.id ? saved : r)));
    this.editingReplyId.set(null);
    this.focus(`[data-edit-for="reply-${saved.id}"]`);
  }

  protected cancelReplyEdit(id: number): void {
    this.editingReplyId.set(null);
    this.focus(`[data-edit-for="reply-${id}"]`);
  }

  /** The thread was locked while the visitor was typing: the form goes away, the notice replaces it. */
  protected lockedMeanwhile(): void {
    this.thread.update((t) => (t ? { ...t, locked: true } : t));
  }

  protected ask(kind: Confirming['kind'], id: number): void {
    this.actionError.set(null);
    this.confirming.set({ kind, id });
    this.liveMessage.set(this.transloco.translate('forum.actions.deleteQuestion'));
    this.focus('[data-testid="forum-confirm-delete"]');
  }

  protected cancelDelete(): void {
    const target = this.confirming();
    this.confirming.set(null);
    this.liveMessage.set('');
    if (target) this.focus(`[data-delete-for="${target.kind}-${target.id}"]`);
  }

  protected isConfirming(kind: Confirming['kind'], id: number): boolean {
    const current = this.confirming();
    return current?.kind === kind && current.id === id;
  }

  protected confirmDelete(): void {
    const target = this.confirming();
    if (!target || this.deleting()) return;
    this.deleting.set(true);
    const call: Observable<void> = target.kind === 'thread' ? this.forum.deleteThread(target.id) : this.forum.deleteReply(target.id);
    call.subscribe({
      next: () => this.deleted(target),
      error: (err: unknown) => {
        this.deleting.set(false);
        const failure = classifyForumError(err).kind;
        // Already gone (deleted from another tab, or by a moderator): the goal is reached.
        if (failure === 'notFound') {
          this.deleted(target);
          return;
        }
        this.confirming.set(null);
        this.actionError.set(failure === 'forbidden' ? 'forbidden' : failure === 'signedOut' ? 'signedOut' : 'failed');
        this.focus(`[data-delete-for="${target.kind}-${target.id}"]`);
      },
    });
  }

  private deleted(target: Confirming): void {
    this.deleting.set(false);
    this.confirming.set(null);
    this.liveMessage.set('');
    if (target.kind === 'thread') {
      void this.router.navigate(['/', this.locale.active(), 'forum']);
      return;
    }
    this.replies.update((list) => list.filter((r) => r.id !== target.id));
    this.thread.update((t) => (t ? { ...t, replyCount: Math.max(0, t.replyCount - 1) } : t));
    this.focus('#forum-replies-title');
  }
}
