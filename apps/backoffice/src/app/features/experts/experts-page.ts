import { ChangeDetectionStrategy, Component, ElementRef, Injector, afterNextRender, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ExpertAccount, ExpertStatus } from '@models/expert';
import { ExpertService } from '@services/expert.service';
import { Alert } from '@shared/components/alert/alert';
import { fieldErrorsFrom, messageFrom } from '@shared/utils/http-errors';

const PAGE_SIZE = 10;
const MAX_REASON = 255;
const DATE_FORMAT = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long', timeStyle: 'short' });
const TABS: readonly { status: ExpertStatus; label: string; empty: string }[] = [
  { status: 'PENDING', label: 'En attente', empty: 'Aucun expert en attente pour le moment.' },
  { status: 'APPROVED', label: 'Approuvés', empty: 'Aucun expert approuvé.' },
  { status: 'REJECTED', label: 'Rejetés', empty: 'Aucun expert rejeté.' },
];

/**
 * Approval queue of Expert (teacher) accounts. Names, specialty and reason are user input: they are only ever
 * bound as text (interpolation), never through innerHTML.
 */
@Component({
  selector: 'app-experts-page',
  imports: [Alert],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="mb-1 text-2xl font-semibold text-foreground">Experts</h1>
    <p class="mb-4 text-sm text-muted-foreground">
      Comptes enseignants à valider, du plus récent au plus ancien. Un expert en attente ou rejeté se connecte
      normalement mais ne peut pas noter les filières et les cours.
    </p>

    <div class="space-y-4">
      <div role="tablist" aria-label="Statut des experts" class="flex gap-2 border-b border-border" (keydown)="onTabKey($event)">
        @for (tab of tabs; track tab.status) {
          <button
            type="button"
            role="tab"
            [id]="'experts-tab-' + tab.status"
            aria-controls="experts-panel"
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

      <div id="experts-panel" role="tabpanel" [attr.aria-labelledby]="'experts-tab-' + status()" class="space-y-4">
      <app-alert [message]="error()" />
      @if (loadFailed()) {
        <button type="button" data-retry (click)="load(page())" class="btn btn-secondary h-9 px-3">Réessayer</button>
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
        <div class="overflow-x-auto rounded-lg border border-border bg-card">
          <table class="w-full text-left text-sm">
            <caption class="sr-only">Experts</caption>
            <thead class="border-b border-border bg-muted text-xs uppercase text-muted-foreground">
              <tr>
                <th scope="col" class="px-4 py-2">Nom</th>
                <th scope="col" class="px-4 py-2">E-mail</th>
                <th scope="col" class="px-4 py-2">Spécialité</th>
                <th scope="col" class="px-4 py-2">Inscription</th>
                <th scope="col" class="px-4 py-2 text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-border">
              @for (item of items(); track item.id) {
                <tr data-expert [attr.data-id]="item.id" class="align-top">
                  <td class="px-4 py-2 text-foreground">
                    <span class="break-words">{{ fullName(item) }}</span>
                    @if (item.reason) {
                      <p data-reason-text class="mt-1 max-w-prose whitespace-pre-wrap break-words text-xs text-muted-foreground">Motif : {{ item.reason }}</p>
                    }
                  </td>
                  <td class="px-4 py-2 text-foreground"><span class="break-all">{{ item.email }}</span></td>
                  <td class="px-4 py-2 text-muted-foreground"><span class="break-words">{{ item.qualification }}</span></td>
                  <td class="whitespace-nowrap px-4 py-2 text-muted-foreground">
                    <time [attr.datetime]="item.registeredAt">{{ formatDate(item.registeredAt) }}</time>
                  </td>
                  <td class="px-4 py-2 text-right">
                    @if (decision()?.id === item.id) {
                      <div class="flex flex-col items-end gap-2">
                        @if (decision()!.kind === 'approve') {
                          <span class="max-w-xs text-xs text-foreground">Approuver cet expert ? Il pourra noter les filières et les cours.</span>
                        } @else {
                          <label class="flex w-64 flex-col items-start gap-1 text-xs text-foreground">
                            Motif (facultatif)
                            <textarea
                              data-reason
                              rows="3"
                              [attr.aria-describedby]="'reason-hint-' + item.id"
                              [attr.aria-invalid]="reasonInvalid()"
                              [attr.maxlength]="maxReason"
                              [value]="reason()"
                              (input)="reason.set($any($event.target).value)"
                              class="field-input w-full"
                            ></textarea>
                            <span [id]="'reason-hint-' + item.id" class="text-muted-foreground">255 caractères max.</span>
                          </label>
                        }
                        <span class="flex gap-1">
                          @if (decision()!.kind === 'approve') {
                            <button type="button" data-confirm-approve [disabled]="deciding()" (click)="confirm(item)" class="btn btn-primary h-8 px-3 text-xs">
                              Confirmer
                            </button>
                          } @else {
                            <button
                              type="button"
                              data-confirm-reject
                              [disabled]="deciding()"
                              (click)="confirm(item)"
                              class="btn bg-destructive text-destructive-foreground hover:opacity-90 h-8 px-3 text-xs"
                            >
                              Confirmer le rejet
                            </button>
                          }
                          <button type="button" data-cancel (click)="cancelAndRestoreFocus()" class="btn btn-secondary h-8 px-3 text-xs">Annuler</button>
                        </span>
                      </div>
                    } @else {
                      <span class="flex justify-end gap-1">
                        @if (item.status !== 'APPROVED') {
                          <button
                            type="button"
                            data-approve
                            [attr.aria-label]="'Approuver ' + fullName(item)"
                            (click)="start(item, 'approve')"
                            class="btn btn-secondary h-8 px-3 text-xs"
                          >
                            Approuver
                          </button>
                        }
                        @if (item.status !== 'REJECTED') {
                          <button
                            type="button"
                            data-reject
                            [attr.aria-label]="'Rejeter ' + fullName(item)"
                            (click)="start(item, 'reject')"
                            class="btn btn-secondary border-destructive text-destructive hover:bg-destructive/10 h-8 px-3 text-xs"
                          >
                            Rejeter
                          </button>
                        }
                      </span>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else if (!loading() && !loadFailed()) {
        <p class="rounded-lg border border-dashed border-border-strong bg-card px-4 py-8 text-center text-sm text-muted-foreground">
          {{ emptyText() }}
        </p>
      }

      <nav class="flex items-center justify-between text-sm text-muted-foreground" aria-label="Pagination">
        <span>Page {{ page() + 1 }} / {{ totalPages() || 1 }} · {{ total() }} expert(s)</span>
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
    </div>
  `,
})
export class ExpertsPage {
  private readonly service = inject(ExpertService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly tabs = TABS;
  protected readonly maxReason = MAX_REASON;

  readonly status = signal<ExpertStatus>('PENDING');
  readonly items = signal<ExpertAccount[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly loadFailed = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  /** The row being decided on, with the kind of decision awaiting confirmation. */
  readonly decision = signal<{ id: number; kind: 'approve' | 'reject' } | null>(null);
  readonly reason = signal('');
  readonly deciding = signal(false);
  /** True after the API refused the reason: drives aria-invalid. */
  readonly reasonInvalid = signal(false);

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

  select(status: ExpertStatus) {
    if (status === this.status()) return;
    this.status.set(status);
    this.notice.set('');
    this.load(0);
  }

  load(page: number) {
    const seq = ++this.requestSeq;
    this.loading.set(true);
    this.loadFailed.set(false);
    this.error.set('');
    this.cancel();
    this.service.list(this.status(), page, PAGE_SIZE).subscribe({
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
        this.error.set(messageFrom(err, 'Impossible de charger les experts.'));
      },
    });
  }

  goTo(page: number) {
    this.notice.set('');
    this.load(page);
  }

  fullName(item: ExpertAccount): string {
    return [item.firstname, item.lastname].filter((p) => !!p).join(' ') || item.email;
  }

  formatDate(iso: string): string {
    const date = new Date(iso);
    return Number.isNaN(date.getTime()) ? iso : DATE_FORMAT.format(date);
  }

  start(item: ExpertAccount, kind: 'approve' | 'reject') {
    this.error.set('');
    this.reason.set('');
    this.reasonInvalid.set(false);
    this.decision.set({ id: item.id, kind });
    this.focusAfterRender(kind === 'approve' ? '[data-confirm-approve]' : '[data-reason]');
  }

  cancel() {
    this.decision.set(null);
    this.reason.set('');
    this.reasonInvalid.set(false);
  }

  cancelAndRestoreFocus() {
    const current = this.decision();
    this.cancel();
    if (current) {
      this.focusAfterRender(`tr[data-id="${current.id}"] [data-${current.kind === 'approve' ? 'approve' : 'reject'}]`);
    }
  }

  confirm(item: ExpertAccount) {
    const current = this.decision();
    if (this.deciding() || !current || current.id !== item.id) return;
    this.deciding.set(true);
    this.error.set('');
    const call = current.kind === 'approve' ? this.service.approve(item.id) : this.service.reject(item.id, this.reason());
    call.subscribe({
      next: () => {
        this.deciding.set(false);
        this.notice.set(current.kind === 'approve' ? 'Expert approuvé.' : 'Expert rejeté.');
        this.focusAfterRender('[data-notice]');
        // Deciding the only row of the last page would otherwise leave an empty page.
        this.load(this.items().length === 1 && this.page() > 0 ? this.page() - 1 : this.page());
      },
      error: (err: unknown) => {
        this.deciding.set(false);
        const httpStatus = err instanceof HttpErrorResponse ? err.status : -1;
        if (httpStatus === 404 || httpStatus === 409) {
          this.load(this.page());
          this.error.set(httpStatus === 404 ? 'Compte introuvable : il a peut-être été supprimé.' : "Ce compte n'est pas un expert.");
          return;
        }
        // A refused reason stays editable.
        const reasonError = fieldErrorsFrom(err)?.['reason'];
        this.reasonInvalid.set(!!reasonError);
        this.error.set(reasonError ?? messageFrom(err, "La décision n'a pas pu être enregistrée."));
      },
    });
  }
}
