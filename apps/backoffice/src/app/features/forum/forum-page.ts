import { ChangeDetectionStrategy, Component, ElementRef, Injector, afterNextRender, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ForumReport, ForumReportStatus } from '@models/forum';
import { ForumAdminService } from '@services/forum-admin.service';
import { Alert } from '@shared/components/alert/alert';
import { messageFrom } from '@shared/utils/http-errors';

const PAGE_SIZE = 10;
const DATE_FORMAT = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long', timeStyle: 'short' });
const TABS: readonly { status: ForumReportStatus; label: string; empty: string }[] = [
  { status: 'OPEN', label: 'Ouverts', empty: 'Aucun signalement ouvert pour le moment.' },
  { status: 'HANDLED', label: 'Traités', empty: 'Aucun signalement traité.' },
  { status: 'DISMISSED', label: 'Rejetés', empty: 'Aucun signalement rejeté.' },
];

type ActionKind = 'hide' | 'unhide' | 'lock' | 'unlock' | 'handle' | 'dismiss' | 'delete';

const NOTICES: Record<ActionKind, string> = {
  hide: 'Message masqué.',
  unhide: 'Message de nouveau visible.',
  lock: 'Fil verrouillé.',
  unlock: 'Fil déverrouillé.',
  handle: 'Signalement clos.',
  dismiss: 'Signalement rejeté.',
  delete: 'Message supprimé définitivement.',
};

/** The button that takes the focus back after the reload that follows an action that keeps the row. */
const FOCUS_AFTER: Partial<Record<ActionKind, string>> = { hide: 'unhide', unhide: 'hide', lock: 'lock', unlock: 'unlock' };

/** Actions after which the report leaves the current tab or disappears. */
const REMOVES_ROW: readonly ActionKind[] = ['handle', 'dismiss', 'delete'];

/**
 * Moderation queue of the forum. Titles, excerpts, reasons and first names are user input: they are only ever bound
 * as text (interpolation), never through innerHTML. The text of a message is never editable here.
 */
@Component({
  selector: 'app-forum-page',
  imports: [Alert],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="mb-1 text-2xl font-semibold text-foreground">Forum</h1>
    <p class="mb-4 text-sm text-muted-foreground">
      Signalements des membres, du plus récent au plus ancien. Vous pouvez masquer, verrouiller ou supprimer un
      message, mais jamais modifier son texte.
    </p>

    <div class="space-y-4">
      <div role="tablist" aria-label="Statut des signalements" class="flex gap-2 border-b border-border" (keydown)="onTabKey($event)">
        @for (tab of tabs; track tab.status) {
          <button
            type="button"
            role="tab"
            [id]="'forum-tab-' + tab.status"
            aria-controls="forum-panel"
            [tabindex]="status() === tab.status ? 0 : -1"
            [attr.data-tab]="tab.status"
            [attr.aria-selected]="status() === tab.status"
            (click)="select(tab.status)"
            class="-mb-px border-b-2 px-3 py-2 text-sm font-medium"
            [class]="status() === tab.status ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'"
          >
            {{ tab.label }}
          </button>
        }
      </div>

      <div id="forum-panel" role="tabpanel" [attr.aria-labelledby]="'forum-tab-' + status()" class="space-y-4">
        <app-alert [message]="error()" />
        @if (loadFailed()) {
          <button type="button" data-retry (click)="load(requestedPage())" class="btn btn-secondary h-9 px-3">Réessayer</button>
        }
        <div
          role="status"
          data-notice
          tabindex="-1"
          [class]="notice() ? 'rounded-md border border-emerald-300 bg-emerald-50 px-3 py-2 text-sm text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950 dark:text-emerald-200' : 'sr-only'"
        >{{ notice() }}</div>
        @if (loading()) {
          <p class="text-sm text-muted-foreground">Chargement…</p>
        }

        @if (items().length) {
          <ul class="space-y-3">
            @for (item of items(); track item.id) {
              <li data-report [attr.data-id]="item.id" class="space-y-3 rounded-lg border border-border bg-card p-4">
                <div class="flex flex-wrap items-center gap-2 text-xs">
                  <span class="rounded-md border border-border px-2 py-0.5 font-medium text-foreground">{{ item.targetType === 'THREAD' ? 'Fil' : 'Réponse' }}</span>
                  @if (item.targetHidden) {
                    <span data-hidden-badge class="rounded-md border border-border-strong bg-muted px-2 py-0.5 font-medium text-foreground">Masqué</span>
                  }
                  <span class="text-muted-foreground">
                    Signalé par <span data-reporter class="font-medium text-foreground break-words"><bdi>{{ item.reporterName || 'un compte supprimé' }}</bdi></span>
                    le <time [attr.datetime]="item.createdAt">{{ formatDate(item.createdAt) }}</time>
                  </span>
                  @if (item.handledAt) {
                    <span class="text-muted-foreground">· clos le <time data-handled-at [attr.datetime]="item.handledAt">{{ formatDate(item.handledAt) }}</time></span>
                  }
                </div>

                <p class="text-sm font-medium text-foreground break-words">{{ item.targetType === 'THREAD' ? 'Fil' : 'Fil de la réponse' }} : <bdi>{{ item.threadTitle }}</bdi></p>
                <p class="text-sm text-foreground break-words"><span class="text-muted-foreground">Motif :</span> <span data-reason class="whitespace-pre-line"><bdi>{{ item.reason }}</bdi></span></p>

                @if (isModified(item)) {
                  <p
                    data-modified-warning
                    role="note"
                    class="rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200"
                  >
                    @if (excerptChanged(item)) {
                      Ce message a été modifié depuis le signalement : comparez les deux extraits.
                    } @else {
                      Ce message a été modifié depuis le signalement, mais les extraits sont identiques : le changement porte sur le titre du fil ou sur la suite du texte.
                    }
                  </p>
                }

                <div class="grid gap-3 md:grid-cols-2">
                  <div>
                    <p class="mb-1 text-xs font-medium uppercase text-muted-foreground">Extrait signalé</p>
                    <p data-reported-excerpt dir="auto" class="max-w-prose whitespace-pre-line break-words rounded-md bg-muted px-3 py-2 text-sm text-foreground">{{ item.reportedExcerpt }}</p>
                  </div>
                  <div>
                    <p class="mb-1 text-xs font-medium uppercase text-muted-foreground">Extrait actuel</p>
                    <p data-current-excerpt dir="auto" class="max-w-prose whitespace-pre-line break-words rounded-md bg-muted px-3 py-2 text-sm text-foreground">{{ item.excerpt }}</p>
                  </div>
                </div>

                @if (confirmDeleteId() === item.id) {
                  <div class="flex flex-col items-start gap-2 rounded-md border border-destructive p-3">
                    <p data-delete-warning class="max-w-prose text-sm text-foreground">
                      Supprimer définitivement {{ item.targetType === 'THREAD' ? 'ce fil, ses réponses' : 'cette réponse' }} et les signalements associés ? Cette action est irréversible.
                    </p>
                    <span class="flex gap-2">
                      <button
                        type="button"
                        data-confirm-delete
                        [disabled]="acting()"
                        (click)="confirmDelete(item)"
                        class="btn bg-destructive text-destructive-foreground hover:opacity-90 h-8 px-3 text-xs"
                      >
                        Confirmer la suppression
                      </button>
                      <button type="button" data-cancel (click)="cancelDelete(item)" class="btn btn-secondary h-8 px-3 text-xs">Annuler</button>
                    </span>
                  </div>
                } @else {
                  <div class="flex flex-wrap gap-2">
                    @if (item.targetHidden) {
                      <button type="button" data-unhide [disabled]="busy()" [attr.aria-label]="'Démasquer ' + label(item)" (click)="act(item, 'unhide')" class="btn btn-secondary h-8 px-3 text-xs">Démasquer</button>
                    } @else {
                      <button type="button" data-hide [disabled]="busy()" [attr.aria-label]="'Masquer ' + label(item)" (click)="act(item, 'hide')" class="btn btn-secondary h-8 px-3 text-xs">Masquer</button>
                    }
                    @if (item.targetType === 'THREAD') {
                      <button type="button" data-lock [disabled]="busy()" [attr.aria-label]="'Verrouiller ' + label(item)" (click)="act(item, 'lock')" class="btn btn-secondary h-8 px-3 text-xs">Verrouiller</button>
                      <button type="button" data-unlock [disabled]="busy()" [attr.aria-label]="'Déverrouiller ' + label(item)" (click)="act(item, 'unlock')" class="btn btn-secondary h-8 px-3 text-xs">Déverrouiller</button>
                    }
                    <button
                      type="button"
                      data-delete
                      [disabled]="busy()"
                      [attr.aria-label]="'Supprimer ' + label(item)"
                      (click)="askDelete(item)"
                      class="btn btn-secondary border-destructive text-destructive hover:bg-destructive/10 h-8 px-3 text-xs"
                    >
                      Supprimer
                    </button>
                    @if (item.status === 'OPEN') {
                      <button type="button" data-handle [disabled]="busy()" [attr.aria-label]="'Clore le signalement sur ' + label(item)" (click)="act(item, 'handle')" class="btn btn-primary h-8 px-3 text-xs">Clore</button>
                      <button type="button" data-dismiss [disabled]="busy()" [attr.aria-label]="'Rejeter le signalement sur ' + label(item)" (click)="act(item, 'dismiss')" class="btn btn-secondary h-8 px-3 text-xs">Rejeter</button>
                    }
                  </div>
                }
              </li>
            }
          </ul>
        } @else if (!loading() && !loadFailed()) {
          <p class="rounded-lg border border-dashed border-border-strong bg-card px-4 py-8 text-center text-sm text-muted-foreground">
            {{ emptyText() }}
          </p>
        }

        <nav class="flex items-center justify-between text-sm text-muted-foreground" aria-label="Pagination">
          <span>Page {{ page() + 1 }} / {{ totalPages() || 1 }} · {{ total() }} signalement(s)</span>
          <span class="flex gap-2">
            <button type="button" data-prev [disabled]="busy() || page() <= 0" (click)="goTo(page() - 1)" class="btn btn-secondary h-9 px-3 disabled:opacity-50">
              Précédent
            </button>
            <button type="button" data-next [disabled]="busy() || page() + 1 >= totalPages()" (click)="goTo(page() + 1)" class="btn btn-secondary h-9 px-3 disabled:opacity-50">
              Suivant
            </button>
          </span>
        </nav>
      </div>
    </div>
  `,
})
export class ForumPage {
  private readonly service = inject(ForumAdminService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly tabs = TABS;

  readonly status = signal<ForumReportStatus>('OPEN');
  readonly items = signal<ForumReport[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly loadFailed = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  /** The report whose target is awaiting a deletion confirmation. */
  readonly confirmDeleteId = signal<number | null>(null);
  /** One moderation call at a time: also blocks a double click. */
  readonly acting = signal(false);
  /** The page last asked for (Réessayer asks for this one, not for the last page that loaded). */
  readonly requestedPage = signal(0);
  /** Row actions and pagination wait for a pending load or action. */
  readonly busy = () => this.loading() || this.acting();
  /** Button to focus once the reload that follows an action has rendered. */
  private pendingFocus: string | null = null;

  /** Guards against a slow response overwriting a newer one. */
  private requestSeq = 0;

  constructor() {
    this.load(0);
  }

  emptyText(): string {
    return TABS.find((t) => t.status === this.status())!.empty;
  }

  private focusAfterRender(selector: string) {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), { injector: this.injector });
  }

  /** Arrow keys move between the tabs (and select them), Home/End jump to the ends. */
  onTabKey(event: KeyboardEvent) {
    const index = TABS.findIndex((t) => t.status === this.status());
    let next = -1;
    if (event.key === 'ArrowRight') next = (index + 1) % TABS.length;
    else if (event.key === 'ArrowLeft') next = (index - 1 + TABS.length) % TABS.length;
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = TABS.length - 1;
    if (next < 0) return;
    event.preventDefault();
    this.select(TABS[next].status);
    this.focusAfterRender(`[data-tab="${TABS[next].status}"]`);
  }

  select(status: ForumReportStatus) {
    if (status === this.status() || this.acting()) return;
    this.status.set(status);
    this.notice.set('');
    this.clearRows();
    this.load(0);
  }

  /** Rows of another tab (or of a failed load) must not stay clickable. */
  private clearRows() {
    this.items.set([]);
    this.page.set(0);
    this.totalPages.set(0);
    this.total.set(0);
  }

  load(page: number) {
    const seq = ++this.requestSeq;
    this.requestedPage.set(page);
    this.loading.set(true);
    this.loadFailed.set(false);
    this.error.set('');
    this.confirmDeleteId.set(null);
    this.service.reports(this.status(), page, PAGE_SIZE).subscribe({
      next: (res) => {
        if (seq !== this.requestSeq) return;
        // The page count shrank under us (rows removed elsewhere): fall back to the last page that exists.
        if (!res.content.length && res.totalPages > 0 && res.number >= res.totalPages) {
          this.load(res.totalPages - 1);
          return;
        }
        this.items.set(res.content);
        this.page.set(res.number);
        this.totalPages.set(res.totalPages);
        this.total.set(res.totalElements);
        this.loading.set(false);
        if (this.pendingFocus) this.focusAfterRender(this.pendingFocus);
        this.pendingFocus = null;
      },
      error: (err: unknown) => {
        if (seq !== this.requestSeq) return;
        this.pendingFocus = null;
        this.clearRows();
        this.loading.set(false);
        this.loadFailed.set(true);
        this.error.set(messageFrom(err, 'Impossible de charger les signalements.'));
      },
    });
  }

  goTo(page: number) {
    this.notice.set('');
    this.load(page);
  }

  formatDate(iso: string): string {
    const date = new Date(iso);
    return Number.isNaN(date.getTime()) ? iso : DATE_FORMAT.format(date);
  }

  /** Short name of the target, for the accessible names of the buttons. */
  label(item: ForumReport): string {
    return item.targetType === 'THREAD' ? `le fil « ${item.threadTitle} »` : `la réponse du fil « ${item.threadTitle} »`;
  }

  /** The excerpt itself differs from the one reported (else only the title or the rest of the text changed). */
  excerptChanged(item: ForumReport): boolean {
    return item.reportedExcerpt != null && item.reportedExcerpt !== item.excerpt;
  }

  /** The author edited the message after the report was made (later date, or a different text). */
  isModified(item: ForumReport): boolean {
    const modified = item.targetModifiedAt ? Date.parse(item.targetModifiedAt) : NaN;
    const created = Date.parse(item.createdAt);
    if (!Number.isNaN(modified) && !Number.isNaN(created) && modified > created) return true;
    return this.excerptChanged(item);
  }

  askDelete(item: ForumReport) {
    this.error.set('');
    this.confirmDeleteId.set(item.id);
    this.focusAfterRender('[data-cancel]');
  }

  cancelDelete(item: ForumReport) {
    this.confirmDeleteId.set(null);
    this.focusAfterRender(`[data-report][data-id="${item.id}"] [data-delete]`);
  }

  confirmDelete(item: ForumReport) {
    if (this.confirmDeleteId() !== item.id) return;
    this.act(item, 'delete');
  }

  act(item: ForumReport, kind: ActionKind) {
    if (this.busy()) return;
    this.acting.set(true);
    this.error.set('');
    this.notice.set('');
    this.call(item, kind).subscribe({
      next: () => {
        this.acting.set(false);
        this.notice.set(NOTICES[kind]);
        // The notice is announced by its status region. A removed row leaves nothing to focus but the notice; for the
        // other actions the focus goes back to the row's button once the reload has rendered it.
        const removed = REMOVES_ROW.includes(kind);
        if (removed) this.focusAfterRender('[data-notice]');
        else this.pendingFocus = `[data-report][data-id="${item.id}"] [data-${FOCUS_AFTER[kind]}]`;
        // Reload the page asked for at the time of the answer. Removing the only row of the last page would otherwise
        // leave an empty page.
        const target = this.requestedPage();
        this.load(removed && this.items().length === 1 && target > 0 ? target - 1 : target);
      },
      error: (err: unknown) => {
        this.acting.set(false);
        if (err instanceof HttpErrorResponse && err.status === 404) {
          // Already deleted elsewhere: show the queue as it is now.
          const target = this.requestedPage();
          this.load(target);
          this.error.set(messageFrom(err, ''));
          return;
        }
        // 401/403 have their own French message. The admin endpoints are idempotent and not rate limited, so a 409
        // or a 429 is not expected: it reads as the generic failure.
        this.error.set(messageFrom(err, "L'action n'a pas pu être enregistrée."));
      },
    });
  }

  private call(item: ForumReport, kind: ActionKind): Observable<unknown> {
    const thread = item.targetType === 'THREAD';
    const id = item.targetId;
    switch (kind) {
      case 'handle':
        return this.service.handle(item.id);
      case 'dismiss':
        return this.service.dismiss(item.id);
      case 'hide':
        return thread ? this.service.hideThread(id) : this.service.hideReply(id);
      case 'unhide':
        return thread ? this.service.unhideThread(id) : this.service.unhideReply(id);
      case 'lock':
        return this.service.lockThread(item.threadId);
      case 'unlock':
        return this.service.unlockThread(item.threadId);
      case 'delete':
        return thread ? this.service.deleteThread(id) : this.service.deleteReply(id);
    }
  }
}
