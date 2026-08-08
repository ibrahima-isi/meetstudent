import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
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
  imports: [CommonModule, FormsModule, LucideAngularModule],
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
    this.mediaService.blobUrl(media.id).subscribe({
      next: url => {
        this.objectUrls.push(url);
        window.open(url, '_blank');
      },
      error: () => {
        this.error.set("Impossible d'ouvrir ce document. Veuillez réessayer.");
      }
    });
  }

  onFileSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) {
      return;
    }

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

  remove(media: Media): void {
    this.mediaService.delete(media.id).subscribe({
      next: () => {
        this.reload();
      },
      error: () => {
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
