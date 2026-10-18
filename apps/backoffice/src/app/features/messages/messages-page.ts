import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ContactMessage, MailStatus } from '@models/message';
import { MessageService } from '@services/message.service';
import { Alert } from '@shared/components/alert/alert';
import { messageFrom } from '@shared/utils/http-errors';

const PAGE_SIZE = 10;
/** One bounded re-check of the list after a resend, once the send had time to finish. */
const RECHECK_DELAY_MS = 2000;
const MAIL_STATUSES: readonly MailStatus[] = ['UNKNOWN', 'PENDING', 'SENT', 'FAILED', 'SKIPPED'];
const DATE_FORMAT = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long', timeStyle: 'short' });

/**
 * Messages received through the public contact form. Sender, subject and text are user input:
 * they are only ever bound as text (interpolation), the text keeps its line breaks through CSS
 * (`whitespace-pre-wrap`), never through innerHTML.
 */
@Component({
  selector: 'app-messages-page',
  imports: [Alert],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="mb-1 text-2xl font-semibold text-foreground">Messages</h1>
    <p class="mb-4 text-sm text-muted-foreground">Reçus par le formulaire de contact du site, du plus récent au plus ancien.</p>

    <div class="space-y-4">
      <app-alert [message]="error()" />
      @if (loadFailed()) {
        <button type="button" data-retry (click)="load(page())" class="btn btn-secondary h-9 px-3">Réessayer</button>
      }
      @if (notice()) {
        <div role="status" data-notice class="rounded-md border border-emerald-300 bg-emerald-50 px-3 py-2 text-sm text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950 dark:text-emerald-200">
          {{ notice() }}
        </div>
      }
      @if (loading()) {
        <p class="text-sm text-muted-foreground">Chargement…</p>
      }

      @if (items().length) {
        <div class="overflow-x-auto rounded-lg border border-border bg-card">
          <table class="w-full text-left text-sm">
            <caption class="sr-only">Messages reçus</caption>
            <thead class="border-b border-border bg-muted text-xs uppercase text-muted-foreground">
              <tr>
                <th scope="col" class="px-4 py-2">Expéditeur</th>
                <th scope="col" class="px-4 py-2">Message</th>
                <th scope="col" class="px-4 py-2">Date</th>
                <th scope="col" class="px-4 py-2">Mail</th>
                <th scope="col" class="px-4 py-2 text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-border">
              @for (item of items(); track item.id) {
                <tr data-message class="align-top">
                  <td class="px-4 py-2 text-foreground"><span class="break-all">{{ item.email }}</span></td>
                  <td class="px-4 py-2">
                    <p class="font-medium text-foreground break-words">{{ item.subject }}</p>
                    <p data-body class="mt-1 max-w-prose whitespace-pre-wrap break-words text-muted-foreground">{{ item.message }}</p>
                  </td>
                  <td class="whitespace-nowrap px-4 py-2 text-muted-foreground">
                    <time [attr.datetime]="item.createdAt">{{ formatDate(item.createdAt) }}</time>
                  </td>
                  <td class="px-4 py-2">
                    <div class="flex flex-col items-start gap-1">
                      <span [attr.data-mail-status]="statusOf(item)" class="text-xs font-medium" [class]="badgeClass(item)">
                        <span aria-hidden="true">{{ badgeIcon(item) }}</span> {{ mailLabel(item) }}
                      </span>
                      <button
                        type="button"
                        data-resend
                        [attr.aria-label]="'Renvoyer le mail du message « ' + item.subject + ' »'"
                        [disabled]="resendingId() !== null"
                        (click)="resend(item)"
                        class="btn btn-secondary h-8 px-3 text-xs"
                      >
                        Renvoyer le mail
                      </button>
                    </div>
                  </td>
                  <td class="whitespace-nowrap px-4 py-2 text-right">
                    @if (pendingDeleteId() === item.id) {
                      <div class="flex flex-col items-end gap-1">
                        <span class="max-w-xs whitespace-normal text-xs text-foreground">Supprimer ce message ? Cette action est définitive.</span>
                        <span class="flex gap-1">
                          <button
                            type="button"
                            data-confirm-delete
                            [disabled]="deleting()"
                            (click)="confirmDelete(item)"
                            class="btn bg-destructive text-destructive-foreground hover:opacity-90 h-8 px-3 text-xs"
                          >
                            Confirmer
                          </button>
                          <button type="button" data-cancel-delete (click)="pendingDeleteId.set(null)" class="btn btn-secondary h-8 px-3 text-xs">
                            Annuler
                          </button>
                        </span>
                      </div>
                    } @else {
                      <button
                        type="button"
                        data-delete
                        [attr.aria-label]="'Supprimer le message « ' + item.subject + ' »'"
                        (click)="pendingDeleteId.set(item.id)"
                        class="btn btn-secondary border-destructive text-destructive hover:bg-destructive/10 h-8 px-3 text-xs"
                      >
                        Supprimer
                      </button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else if (!loading() && !loadFailed()) {
        <p class="rounded-lg border border-dashed border-border-strong bg-card px-4 py-8 text-center text-sm text-muted-foreground">
          Aucun message pour le moment.
        </p>
      }

      <nav class="flex items-center justify-between text-sm text-muted-foreground" aria-label="Pagination">
        <span>Page {{ page() + 1 }} / {{ totalPages() || 1 }} · {{ total() }} message(s)</span>
        <span class="flex gap-2">
          <button type="button" data-prev [disabled]="loading() || page() <= 0" (click)="goTo(page() - 1)" class="btn btn-secondary h-9 px-3 disabled:opacity-50">
            Précédent
          </button>
          <button type="button" data-next [disabled]="loading() || page() + 1 >= totalPages()" (click)="goTo(page() + 1)" class="btn btn-secondary h-9 px-3 disabled:opacity-50">
            Suivant
          </button>
        </span>
      </nav>
    </div>
  `,
})
export class MessagesPage {
  private readonly service = inject(MessageService);
  private readonly destroyRef = inject(DestroyRef);
  private recheckTimer: ReturnType<typeof setTimeout> | null = null;

  readonly items = signal<ContactMessage[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly loadFailed = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly pendingDeleteId = signal<number | null>(null);
  readonly deleting = signal(false);
  /** The message whose mail is being re-sent; one at a time, which also blocks a double click. */
  readonly resendingId = signal<number | null>(null);

  /** Guards against a slow response overwriting a newer one. */
  private requestSeq = 0;

  constructor() {
    this.destroyRef.onDestroy(() => this.clearRecheck());
    this.load(0);
  }

  load(page: number) {
    const seq = ++this.requestSeq;
    this.loading.set(true);
    this.loadFailed.set(false);
    this.error.set('');
    this.pendingDeleteId.set(null);
    this.service.list(page, PAGE_SIZE).subscribe({
      next: (res) => {
        if (seq !== this.requestSeq) return;
        this.items.set(res.content);
        this.page.set(res.number);
        this.totalPages.set(res.totalPages);
        this.total.set(res.totalElements);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        if (seq !== this.requestSeq) return;
        this.loading.set(false);
        this.loadFailed.set(true);
        this.error.set(messageFrom(err, 'Impossible de charger les messages.'));
      },
    });
  }

  formatDate(iso: string): string {
    const date = new Date(iso);
    return Number.isNaN(date.getTime()) ? iso : DATE_FORMAT.format(date);
  }

  /** The status the page shows: anything the API sends outside the known set reads as UNKNOWN. */
  statusOf(item: ContactMessage): MailStatus {
    return MAIL_STATUSES.includes(item.mailStatus) ? item.mailStatus : 'UNKNOWN';
  }

  mailLabel(item: ContactMessage): string {
    switch (this.statusOf(item)) {
      case 'SENT':
        return item.mailSentAt ? `Mail envoyé le ${this.formatDate(item.mailSentAt)}` : 'Mail envoyé';
      case 'PENDING':
        return 'Envoi en cours…';
      case 'FAILED': {
        const n = item.mailAttempts;
        return n > 0 ? `Échec de l'envoi après ${n} tentative${n > 1 ? 's' : ''}` : "Échec de l'envoi";
      }
      case 'SKIPPED':
        return 'Mail désactivé';
      default:
        return 'Statut inconnu';
    }
  }

  badgeIcon(item: ContactMessage): string {
    switch (this.statusOf(item)) {
      case 'SENT':
        return '✓';
      case 'FAILED':
        return '✕';
      case 'PENDING':
        return '…';
      default:
        return '–';
    }
  }

  badgeClass(item: ContactMessage): string {
    const base = 'inline-block rounded-md border px-2 py-0.5 ';
    switch (this.statusOf(item)) {
      case 'SENT':
        return base + 'border-emerald-300 bg-emerald-50 text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950 dark:text-emerald-200';
      case 'FAILED':
        return base + 'border-red-300 bg-red-50 text-red-800 dark:border-red-800 dark:bg-red-950 dark:text-red-200';
      case 'PENDING':
        return base + 'border-amber-300 bg-amber-50 text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200';
      default:
        return base + 'border-border bg-muted text-muted-foreground';
    }
  }

  resend(item: ContactMessage) {
    if (this.resendingId() !== null) return;
    this.resendingId.set(item.id);
    this.error.set('');
    this.notice.set('');
    this.service.resend(item.id).subscribe({
      next: () => {
        this.resendingId.set(null);
        this.notice.set('Renvoi du mail demandé.');
        this.load(this.page());
        this.scheduleRecheck();
      },
      error: (err: unknown) => {
        this.resendingId.set(null);
        const status = err instanceof HttpErrorResponse ? err.status : -1;
        if (status === 404) {
          this.load(this.page());
          this.error.set('Message introuvable');
        } else if (status === 409) {
          this.error.set('Un envoi est déjà en cours, réessayez dans quelques minutes');
        } else if (status === 503) {
          this.error.set('Le service e-mail est désactivé sur ce serveur');
        } else {
          this.error.set(messageFrom(err, 'Le renvoi du mail a échoué.'));
        }
      },
    });
  }

  private scheduleRecheck() {
    this.clearRecheck();
    this.recheckTimer = setTimeout(() => {
      this.recheckTimer = null;
      this.load(this.page());
    }, RECHECK_DELAY_MS);
  }

  private clearRecheck() {
    if (this.recheckTimer !== null) clearTimeout(this.recheckTimer);
    this.recheckTimer = null;
  }

  goTo(page: number) {
    this.notice.set('');
    this.load(page);
  }

  confirmDelete(item: ContactMessage) {
    if (this.deleting()) return;
    this.deleting.set(true);
    this.error.set('');
    this.service.delete(item.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.notice.set('Message supprimé.');
        // Deleting the only row of the last page would otherwise leave an empty page.
        this.load(this.items().length === 1 && this.page() > 0 ? this.page() - 1 : this.page());
      },
      error: (err: unknown) => {
        this.deleting.set(false);
        this.pendingDeleteId.set(null);
        this.error.set(messageFrom(err, 'La suppression a échoué.'));
      },
    });
  }
}
