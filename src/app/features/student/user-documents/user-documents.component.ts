import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MediaService } from '@services/media.service';
import { Media, MediaCategory, VerificationStatus } from '@models/entities';

const PERSONAL_DOCUMENT_CATEGORIES: MediaCategory[] = [
  'DIPLOMA',
  'CERTIFICATE',
  'BULLETIN',
  'PRESENTATION_VIDEO'
];

@Component({
  selector: 'app-user-documents',
  imports: [CommonModule, FormsModule],
  templateUrl: './user-documents.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UserDocumentsComponent implements OnInit, OnDestroy {
  private mediaService = inject(MediaService);

  private allMedia = signal<Media[]>([]);
  documents = computed(() =>
    this.allMedia().filter(m => PERSONAL_DOCUMENT_CATEGORIES.includes(m.category))
  );

  loading = signal(false);
  error = signal('');
  pendingDeleteId = signal<number | null>(null);

  selectedCategory = signal<MediaCategory>('DIPLOMA');
  uploading = signal(false);
  readonly uploadableCategories: MediaCategory[] = [
    'DIPLOMA',
    'CERTIFICATE',
    'BULLETIN',
    'PRESENTATION_VIDEO'
  ];

  private readonly MAX_UPLOAD_BYTES = 10485760;
  private readonly ALLOWED_EXTENSIONS = ['pdf', 'jpg', 'jpeg', 'png', 'webp', 'mp4', 'webm', 'mov'];

  private objectUrls: string[] = [];

  ngOnInit(): void {
    this.reload();
  }

  ngOnDestroy(): void {
    for (const url of this.objectUrls) {
      URL.revokeObjectURL(url);
    }
    this.objectUrls = [];
  }

  reload(): void {
    this.loading.set(true);
    this.error.set('');

    this.mediaService.mine().subscribe({
      next: media => {
        this.allMedia.set(media);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger vos documents. Veuillez réessayer.');
        this.loading.set(false);
      }
    });
  }

  open(media: Media): void {
    // Must be opened synchronously, in direct response to the click — Safari
    // blocks window.open unconditionally once we're past an async HTTP round
    // trip, and Chrome blocks it once transient activation lapses. We open a
    // blank window now and point it at the blob once it arrives.
    const win = window.open('', '_blank');
    if (!win) {
      this.error.set(
        "Votre navigateur a bloqué l'ouverture de ce document. Autorisez les fenêtres pop-up pour ce site puis réessayez."
      );
      return;
    }

    this.mediaService.blobUrl(media.id).subscribe({
      next: url => {
        this.objectUrls.push(url);
        win.location.href = url;
      },
      error: () => {
        win.close();
        this.error.set("Impossible d'ouvrir ce document. Veuillez réessayer.");
      }
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }

    // Reset immediately so a rejected or failed upload can be retried with
    // the exact same file (the browser otherwise treats an unchanged value
    // as "no change" and won't fire another `change` event).
    input.value = '';

    if (file.size > this.MAX_UPLOAD_BYTES) {
      this.error.set('Le fichier dépasse la taille maximale autorisée de 10 Mo.');
      return;
    }

    const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
    if (!this.ALLOWED_EXTENSIONS.includes(extension)) {
      this.error.set("Ce type de fichier n'est pas autorisé.");
      return;
    }

    this.error.set('');
    this.uploading.set(true);

    this.mediaService.upload(file, this.selectedCategory(), crypto.randomUUID()).subscribe({
      next: () => {
        this.uploading.set(false);
        this.reload();
      },
      error: () => {
        this.error.set("Impossible d'envoyer ce document. Veuillez réessayer.");
        this.uploading.set(false);
      }
    });
  }

  /** Step 1 of the destructive delete: arm the inline confirmation for one row. */
  requestDelete(mediaId: number): void {
    this.pendingDeleteId.set(mediaId);
  }

  /** Backs out of a pending delete without calling the API. */
  cancelDelete(): void {
    this.pendingDeleteId.set(null);
  }

  /** Step 2: only called once the user has confirmed in the template. */
  confirmDelete(): void {
    const mediaId = this.pendingDeleteId();
    if (mediaId === null) {
      return;
    }

    this.mediaService.delete(mediaId).subscribe({
      next: () => {
        this.pendingDeleteId.set(null);
        this.reload();
      },
      error: () => {
        this.pendingDeleteId.set(null);
        this.error.set('Impossible de supprimer ce document. Veuillez réessayer.');
      }
    });
  }

  statusLabel(status: VerificationStatus | null): string {
    switch (status) {
      case 'PENDING':
        return 'En attente';
      case 'VERIFIED':
        return 'Vérifié';
      case 'REJECTED':
        return 'Rejeté';
      default:
        return '';
    }
  }

  categoryLabel(category: MediaCategory): string {
    switch (category) {
      case 'DIPLOMA':
        return 'Diplôme';
      case 'CERTIFICATE':
        return 'Certificat';
      case 'BULLETIN':
        return 'Bulletin';
      case 'PRESENTATION_VIDEO':
        return 'Vidéo de présentation';
      default:
        return category;
    }
  }
}
