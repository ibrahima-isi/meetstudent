import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { Alert } from '@shared/components/alert/alert';
import { MediaService } from '@services/media.service';
import { Media, MediaCategory, VerificationStatus } from '@models/entities';

const PAGE_SIZE = 20;
/** Long enough for the new tab to load the blob, short enough not to leak it. */
const REVOKE_DELAY_MS = 60_000;

type Decision = 'VERIFIED' | 'REJECTED';

/** Same vocabulary as the user-documents panel in apps/web. */
const STATUS_LABELS: Record<VerificationStatus, string> = {
  PENDING: 'En attente',
  VERIFIED: 'Vérifié',
  REJECTED: 'Rejeté',
};

const CATEGORY_LABELS: Record<MediaCategory, string> = {
  DIPLOMA: 'Diplôme',
  CERTIFICATE: 'Certificat',
  BULLETIN: 'Bulletin',
  PRESENTATION_VIDEO: 'Vidéo de présentation',
  SCHOOL_LOGO: "Logo d'école",
  SCHOOL_COVER: "Couverture d'école",
  COURSE_PHOTO: 'Photo de cours',
  PROGRAM_PHOTO: 'Photo de programme',
  USER_PHOTO: "Photo d'utilisateur",
};

const STATUS_CLASSES: Record<VerificationStatus, string> = {
  PENDING: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-200',
  VERIFIED: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-200',
  REJECTED: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-200',
};

@Component({
  selector: 'app-moderation-page',
  imports: [Alert],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="text-2xl font-semibold text-foreground">Modération</h1>
    <p class="mt-1 text-sm text-muted-foreground">Documents envoyés par les utilisateurs.</p>

    <div role="tablist" aria-label="Statut" class="mt-4 flex gap-1 border-b border-border">
      @for (tab of tabs; track tab.status) {
        <button
          type="button"
          role="tab"
          [attr.data-tab]="tab.status"
          [attr.aria-selected]="status() === tab.status"
          (click)="selectStatus(tab.status)"
          class="-mb-px border-b-2 px-4 py-2 text-sm font-medium"
          [class]="
            status() === tab.status
              ? 'border-brand text-brand'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          "
        >
          {{ tab.label }}
        </button>
      }
    </div>

    <div class="mt-4 space-y-3">
      <app-alert [message]="actionError()" />

      @if (loading()) {
        <p data-loading class="text-sm text-muted-foreground">Chargement…</p>
      } @else if (loadError()) {
        <app-alert [message]="loadError()" />
        <button
          type="button"
          data-retry
          (click)="load()"
          class="btn btn-secondary h-9 px-3"
        >
          Réessayer
        </button>
      } @else if (items().length === 0) {
        <p data-empty class="rounded-md border border-dashed border-border-strong p-6 text-center text-sm text-muted-foreground">
          Aucun document {{ emptyLabel() }}.
        </p>
      } @else {
        <ul class="space-y-3">
          @for (item of items(); track item.id) {
            <li data-row class="rounded-lg border border-border bg-card p-4">
              <div class="flex flex-wrap items-start justify-between gap-3">
                <div class="min-w-0">
                  <p class="truncate font-medium text-foreground">{{ item.originalFilename || 'Sans nom' }}</p>
                  <p class="mt-0.5 text-sm text-muted-foreground">
                    {{ categoryLabel(item.category) }} · {{ typeLabel(item.contentType) }} · {{ sizeLabel(item.sizeBytes) }}
                  </p>
                  @if (item.rejectionReason) {
                    <p class="mt-1 text-sm text-destructive">Motif : {{ item.rejectionReason }}</p>
                  }
                </div>
                <div class="flex flex-wrap items-center gap-2">
                  @if (item.verificationStatus; as s) {
                    <span class="rounded-full px-2.5 py-0.5 text-xs font-medium" [class]="statusClass(s)">
                      {{ statusLabel(s) }}
                    </span>
                  }
                  <button
                    type="button"
                    data-view
                    [disabled]="viewingId() === item.id"
                    (click)="view(item)"
                    class="btn btn-secondary h-9 px-3 disabled:opacity-50"
                  >
                    {{ viewingId() === item.id ? 'Ouverture…' : 'Voir' }}
                  </button>
                  @if (status() === 'PENDING' && confirming()?.id !== item.id) {
                    <button
                      type="button"
                      data-approve
                      (click)="ask(item.id, 'VERIFIED')"
                      class="btn h-9 px-3 bg-green-700 text-green-50 hover:bg-green-800 dark:bg-green-600 dark:text-white dark:hover:bg-green-500"
                    >
                      Approuver
                    </button>
                    <button
                      type="button"
                      data-reject
                      (click)="ask(item.id, 'REJECTED')"
                      class="btn bg-destructive text-destructive-foreground hover:opacity-90 h-9 px-3"
                    >
                      Rejeter
                    </button>
                  }
                </div>
              </div>

              @if (confirming(); as c) {
                @if (c.id === item.id) {
                  <div class="mt-3 space-y-2 rounded-md bg-muted p-3">
                    <p class="text-sm font-medium text-foreground">
                      {{ c.decision === 'VERIFIED' ? 'Approuver ce document ?' : 'Rejeter ce document ?' }}
                    </p>
                    @if (c.decision === 'REJECTED') {
                      <label class="block text-sm text-foreground">
                        Motif du rejet (visible par l'utilisateur)
                        <textarea
                          data-reason
                          rows="2"
                          maxlength="500"
                          [value]="reason()"
                          (input)="onReason($event)"
                          class="field-input mt-1"
                        ></textarea>
                      </label>
                      @if (reasonError()) {
                        <p data-reason-error class="text-sm text-destructive">Un motif est obligatoire pour rejeter.</p>
                      }
                    }
                    <div class="flex gap-2">
                      <button
                        type="button"
                        data-confirm
                        (click)="confirm()"
                        class="btn btn-primary h-9 px-3"
                      >
                        Confirmer
                      </button>
                      <button
                        type="button"
                        data-cancel
                        (click)="cancel()"
                        class="btn btn-secondary h-9 px-3"
                      >
                        Annuler
                      </button>
                    </div>
                  </div>
                }
              }
            </li>
          }
        </ul>

        @if (totalPages() > 1) {
          <nav aria-label="Pagination" class="flex items-center justify-between pt-2 text-sm text-muted-foreground">
            <button
              type="button"
              data-prev
              [disabled]="page() === 0"
              (click)="goTo(page() - 1)"
              class="btn btn-secondary h-9 px-3 disabled:opacity-50"
            >
              Précédent
            </button>
            <span>Page {{ page() + 1 }} sur {{ totalPages() }} · {{ total() }} documents</span>
            <button
              type="button"
              data-next
              [disabled]="page() + 1 >= totalPages()"
              (click)="goTo(page() + 1)"
              class="btn btn-secondary h-9 px-3 disabled:opacity-50"
            >
              Suivant
            </button>
          </nav>
        }
      }
    </div>
  `,
})
export class ModerationPage {
  private readonly media = inject(MediaService);
  private readonly destroyRef = inject(DestroyRef);
  private listing?: Subscription;
  private readonly timers = new Set<ReturnType<typeof setTimeout>>();

  protected readonly tabs: { status: VerificationStatus; label: string }[] = [
    { status: 'PENDING', label: STATUS_LABELS.PENDING },
    { status: 'VERIFIED', label: STATUS_LABELS.VERIFIED },
    { status: 'REJECTED', label: STATUS_LABELS.REJECTED },
  ];

  protected readonly status = signal<VerificationStatus>('PENDING');
  protected readonly items = signal<Media[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);
  protected readonly loading = signal(true);
  protected readonly loadError = signal('');
  protected readonly actionError = signal('');
  protected readonly viewingId = signal<number | null>(null);
  protected readonly confirming = signal<{ id: number; decision: Decision } | null>(null);
  protected readonly reason = signal('');
  protected readonly reasonError = signal(false);
  protected readonly emptyLabel = computed(() => STATUS_LABELS[this.status()].toLowerCase());

  constructor() {
    this.load();
    this.destroyRef.onDestroy(() => {
      this.listing?.unsubscribe();
      this.timers.forEach((t) => clearTimeout(t));
    });
  }

  protected statusLabel = (s: VerificationStatus) => STATUS_LABELS[s];
  protected statusClass = (s: VerificationStatus) => STATUS_CLASSES[s];
  protected categoryLabel = (c: MediaCategory) => CATEGORY_LABELS[c] ?? c;

  protected typeLabel(contentType: string | null): string {
    return contentType ?? 'Inconnu';
  }

  protected sizeLabel(bytes: number | null): string {
    if (bytes === null) return '—';
    if (bytes < 1024) return `${bytes} o`;
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} Ko`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
  }

  protected selectStatus(status: VerificationStatus): void {
    if (status === this.status()) return;
    this.status.set(status);
    this.page.set(0);
    this.cancel();
    this.actionError.set('');
    this.load();
  }

  protected goTo(page: number): void {
    this.page.set(page);
    this.cancel();
    this.load();
  }

  protected load(): void {
    this.listing?.unsubscribe();
    this.loading.set(true);
    this.loadError.set('');
    this.listing = this.media.list(this.status(), this.page(), PAGE_SIZE).subscribe({
      next: (result) => {
        this.items.set(result.content);
        this.totalPages.set(result.totalPages);
        this.total.set(result.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set('Impossible de charger les documents.');
        this.loading.set(false);
      },
    });
  }

  protected view(item: Media): void {
    this.actionError.set('');
    this.viewingId.set(item.id);
    this.media.download(item.id).subscribe({
      next: (blob) => {
        this.viewingId.set(null);
        const url = URL.createObjectURL(blob);
        if (window.open(url, '_blank')) {
          this.timers.add(setTimeout(() => URL.revokeObjectURL(url), REVOKE_DELAY_MS));
        } else {
          URL.revokeObjectURL(url);
          this.actionError.set("Le navigateur a bloqué l'ouverture du fichier. Autorisez les fenêtres pour ce site.");
        }
      },
      error: () => {
        this.viewingId.set(null);
        this.actionError.set('Impossible de récupérer ce fichier.');
      },
    });
  }

  protected ask(id: number, decision: Decision): void {
    this.confirming.set({ id, decision });
    this.reason.set('');
    this.reasonError.set(false);
  }

  protected cancel(): void {
    this.confirming.set(null);
    this.reasonError.set(false);
  }

  protected onReason(event: Event): void {
    this.reason.set((event.target as HTMLTextAreaElement).value);
    this.reasonError.set(false);
  }

  protected confirm(): void {
    const pending = this.confirming();
    if (!pending) return;
    const reason = this.reason().trim();
    if (pending.decision === 'REJECTED' && !reason) {
      this.reasonError.set(true);
      return;
    }

    const index = this.items().findIndex((m) => m.id === pending.id);
    const removed = this.items()[index];
    this.actionError.set('');
    this.cancel();
    this.items.update((list) => list.filter((m) => m.id !== pending.id));

    const call =
      pending.decision === 'VERIFIED'
        ? this.media.verify(pending.id, 'VERIFIED')
        : this.media.verify(pending.id, 'REJECTED', reason);
    call.subscribe({
      error: () => {
        this.items.update((list) => {
          const copy = [...list];
          copy.splice(Math.min(index, copy.length), 0, removed);
          return copy;
        });
        this.actionError.set("L'action a échoué. Le document a été remis dans la liste, réessayez.");
      },
    });
  }
}
