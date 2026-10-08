import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MediaService } from '@services/media.service';
import { Media, MediaCategory, VerificationStatus } from '@models/entities';
import { TranslocoPipe } from '@jsverse/transloco';

const PERSONAL_DOCUMENT_CATEGORIES: MediaCategory[] = [
  'DIPLOMA',
  'CERTIFICATE',
  'BULLETIN',
  'PRESENTATION_VIDEO'
];

@Component({
  selector: 'app-user-documents',
  // The pipe, not *transloco: the structural directive would hide the upload
  // controls until the bundle loads, inside a page that is already showing.
  imports: [CommonModule, FormsModule, TranslocoPipe],
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
  /** A translation key, not a sentence, so a language switch re-renders it. */
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
        this.error.set('documents.errors.loadFailed');
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
      this.error.set('documents.errors.popupBlocked');
      return;
    }

    this.mediaService.blobUrl(media.id).subscribe({
      next: url => {
        this.objectUrls.push(url);
        win.location.href = url;
      },
      error: () => {
        win.close();
        this.error.set('documents.errors.openFailed');
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
      this.error.set('documents.errors.tooLarge');
      return;
    }

    const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
    if (!this.ALLOWED_EXTENSIONS.includes(extension)) {
      this.error.set('documents.errors.typeNotAllowed');
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
        this.error.set('documents.errors.uploadFailed');
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
        this.error.set('documents.errors.deleteFailed');
      }
    });
  }

  /** The translation key for a status; '' when the document has none yet. */
  statusLabel(status: VerificationStatus | null): string {
    return status ? `documents.status.${status}` : '';
  }

  /**
   * The translation key for a personal-document category. Any other category
   * comes back as-is: it has no copy, and its raw name beats a missing key.
   */
  categoryLabel(category: MediaCategory): string {
    return PERSONAL_DOCUMENT_CATEGORIES.includes(category)
      ? `documents.category.${category}`
      : category;
  }
}
