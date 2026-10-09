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
  PENDING: 'bg-amber-100 text-amber-800',
  VERIFIED: 'bg-green-100 text-green-800',
  REJECTED: 'bg-red-100 text-red-800',
};

@Component({
  selector: 'app-moderation-page',
  imports: [Alert],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="text-2xl font-semibold text-slate-900">Modération</h1>
    <p class="mt-1 text-sm text-slate-500">Documents envoyés par les utilisateurs.</p>

    <div role="tablist" aria-label="Statut" class="mt-4 flex gap-1 border-b border-slate-200">
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
              ? 'border-indigo-600 text-indigo-700'
              : 'border-transparent text-slate-600 hover:text-slate-900'
          "
        >
          {{ tab.label }}
        </button>
      }
    </div>

    <div class="mt-4 space-y-3">
      <app-alert [message]="actionError()" />

      @if (loading()) {
        <p data-loading class="text-sm text-slate-500">Chargement…</p>
      } @else if (loadError()) {
        <app-alert [message]="loadError()" />
        <button
          type="button"
          data-retry
          (click)="load()"
          class="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium hover:bg-slate-100"
        >
          Réessayer
        </button>
      } @else if (items().length === 0) {
        <p data-empty class="rounded-md border border-dashed border-slate-300 p-6 text-center text-sm text-slate-500">
          Aucun document {{ emptyLabel() }}.
        </p>
      } @else {
        <ul class="space-y-3">
          @for (item of items(); track item.id) {
            <li data-row class="rounded-lg border border-slate-200 bg-white p-4">
              <div class="flex flex-wrap items-start justify-between gap-3">
                <div class="min-w-0">
                  <p class="truncate font-medium text-slate-900">{{ item.originalFilename || 'Sans nom' }}</p>
                  <p class="mt-0.5 text-sm text-slate-600">
                    {{ categoryLabel(item.category) }} · {{ typeLabel(item.contentType) }} · {{ sizeLabel(item.sizeBytes) }}
                  </p>
                  @if (item.rejectionReason) {
                    <p class="mt-1 text-sm text-red-700">Motif : {{ item.rejectionReason }}</p>
                  }
                </div>
                <div class="flex items-center gap-2">
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
                    class="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium hover:bg-slate-100 disabled:opacity-50"
                  >
                    {{ viewingId() === item.id ? 'Ouverture…' : 'Voir' }}
                  </button>
                  @if (status() === 'PENDING' && confirming()?.id !== item.id) {
                    <button
                      type="button"
                      data-approve
                      (click)="ask(item.id, 'VERIFIED')"
                      class="rounded-md bg-green-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-green-700"
                    >
                      Approuver
                    </button>
                    <button
                      type="button"
                      data-reject
                      (click)="ask(item.id, 'REJECTED')"
                      class="rounded-md bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700"
                    >
                      Rejeter
                    </button>
                  }
                </div>
              </div>

              @if (confirming(); as c) {
                @if (c.id === item.id) {
                  <div class="mt-3 space-y-2 rounded-md bg-slate-50 p-3">
                    <p class="text-sm font-medium text-slate-800">
                      {{ c.decision === 'VERIFIED' ? 'Approuver ce document ?' : 'Rejeter ce document ?' }}
                    </p>
                    @if (c.decision === 'REJECTED') {
                      <label class="block text-sm text-slate-700">
                        Motif du rejet (visible par l'utilisateur)
                        <textarea
                          data-reason
                          rows="2"
                          maxlength="500"
                          [value]="reason()"
                          (input)="onReason($event)"
                          class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
                        ></textarea>
                      </label>
                      @if (reasonError()) {
                        <p data-reason-error class="text-sm text-red-700">Un motif est obligatoire pour rejeter.</p>
                      }
                    }
                    <div class="flex gap-2">
                      <button
                        type="button"
                        data-confirm
                        (click)="confirm()"
                        class="rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700"
                      >
                        Confirmer
                      </button>
                      <button
                        type="button"
                        data-cancel
                        (click)="cancel()"
                        class="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium hover:bg-slate-100"
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
          <nav aria-label="Pagination" class="flex items-center justify-between pt-2 text-sm text-slate-600">
            <button
              type="button"
              data-prev
              [disabled]="page() === 0"
              (click)="goTo(page() - 1)"
              class="rounded-md border border-slate-300 px-3 py-1.5 font-medium hover:bg-slate-100 disabled:opacity-50"
            >
              Précédent
            </button>
            <span>Page {{ page() + 1 }} sur {{ totalPages() }} · {{ total() }} documents</span>
            <button
              type="button"
              data-next
              [disabled]="page() + 1 >= totalPages()"
              (click)="goTo(page() + 1)"
              class="rounded-md border border-slate-300 px-3 py-1.5 font-medium hover:bg-slate-100 disabled:opacity-50"
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
