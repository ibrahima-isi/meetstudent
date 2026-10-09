import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
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
  /** 0-100 while an upload is in flight, null otherwise. */
  uploadProgress = signal<number | null>(null);
  readonly uploadableCategories: MediaCategory[] = [
    'DIPLOMA',
    'CERTIFICATE',
    'BULLETIN',
    'PRESENTATION_VIDEO'
  ];

  private readonly MAX_UPLOAD_BYTES = 10485760;
  /** Mirrors the server's extension -> MIME allow-list (MediaService.java). */
  private readonly ALLOWED_MIME_BY_EXTENSION: Record<string, string> = {
    jpg: 'image/jpeg',
    jpeg: 'image/jpeg',
    png: 'image/png',
    webp: 'image/webp',
    pdf: 'application/pdf',
    mp4: 'video/mp4',
    webm: 'video/webm',
    mov: 'video/quicktime'
  };

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

    const invalid = this.validate(file);
    if (invalid) {
      this.error.set(invalid);
      return;
    }

    this.error.set('');
    this.uploading.set(true);
    this.uploadProgress.set(0);

    this.mediaService.uploadWithProgress(file, this.selectedCategory(), crypto.randomUUID()).subscribe({
      next: event => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          this.uploadProgress.set(Math.round((100 * event.loaded) / event.total));
        } else if (event.type === HttpEventType.Response) {
          this.uploading.set(false);
          this.uploadProgress.set(null);
          this.reload();
        }
      },
      error: (err: unknown) => {
        this.error.set(this.uploadErrorKey(err));
        this.uploading.set(false);
        this.uploadProgress.set(null);
      }
    });
  }

  /** Client-side mirror of the server's checks; returns an error key or ''. */
  private validate(file: File): string {
    if (file.size === 0) {
      return 'documents.errors.empty';
    }
    if (file.size > this.MAX_UPLOAD_BYTES) {
      return 'documents.errors.tooLarge';
    }
    const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
    const expectedMime = this.ALLOWED_MIME_BY_EXTENSION[extension];
    const mime = file.type.split(';')[0].trim().toLowerCase();
    if (!expectedMime || mime !== expectedMime) {
      return 'documents.errors.typeNotAllowed';
    }
    return '';
  }

  private uploadErrorKey(err: unknown): string {
    switch (err instanceof HttpErrorResponse ? err.status : -1) {
      case 413:
        return 'documents.errors.tooLarge';
      case 415:
        return 'documents.errors.typeNotAllowed';
      case 400:
      case 422:
        return 'documents.errors.rejectedByServer';
      case 403:
        return 'documents.errors.forbidden';
      case 0:
        return 'documents.errors.network';
      default:
        return 'documents.errors.uploadFailed';
    }
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
