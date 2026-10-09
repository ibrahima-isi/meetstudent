import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Media, Page, VerificationStatus } from '@models/entities';
import { ProgramMediaCategory } from '@models/program';
import { SchoolMediaCategory } from '@models/school';
import { API_URL, SERVER_URL } from './api-config';

/** Same limit as the API (`spring.servlet.multipart.max-file-size`). */
export const MAX_IMAGE_BYTES = 10 * 1024 * 1024;

/** Image subset of the API's extension -> MIME allow-list (MediaService.java). */
const IMAGE_MIME_BY_EXTENSION: Record<string, string> = {
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp',
};

/** Client-side mirror of the server's checks; returns a French message, or '' when valid. */
export function validateImage(file: File): string {
  if (file.size === 0) return 'Le fichier est vide.';
  if (file.size > MAX_IMAGE_BYTES) return 'Le fichier dépasse 10 Mo.';
  const extension = file.name.includes('.') ? (file.name.split('.').pop()?.toLowerCase() ?? '') : '';
  const mime = file.type.split(';')[0].trim().toLowerCase();
  const expected = IMAGE_MIME_BY_EXTENSION[extension];
  if (!expected || mime !== expected) return 'Type de fichier non autorisé (JPEG, PNG ou WebP).';
  return '';
}

@Injectable({ providedIn: 'root' })
export class MediaService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);
  private readonly serverUrl = inject(SERVER_URL);

  /** Moderation queue: media with the given verification status. */
  list(status: VerificationStatus, page: number, size: number): Observable<Page<Media>> {
    const params = new HttpParams().set('status', status).set('page', page).set('size', size);
    return this.http.get<Page<Media>>(`${this.apiUrl}/media`, { params });
  }

  verify(id: number, status: 'VERIFIED' | 'REJECTED', reason?: string): Observable<Media> {
    const body = reason === undefined ? { status } : { status, reason };
    return this.http.patch<Media>(`${this.apiUrl}/media/${id}/verification`, body);
  }

  /** Private files need the bearer header, so they are fetched as a blob. */
  download(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/media/${id}`, { responseType: 'blob' });
  }

  upload(file: File, category: SchoolMediaCategory | ProgramMediaCategory): Observable<Media> {
    const body = new FormData();
    body.append('category', category);
    body.append('file', file);
    return this.http.post<Media>(`${this.apiUrl}/media`, body);
  }

  /** Browser-loadable url of a public media; `publicUrl` is relative to the server root. */
  publicUrl(media: { publicUrl?: string | null } | null | undefined): string | null {
    const path = media?.publicUrl;
    if (!path) return null;
    if (/^https?:\/\//i.test(path)) return path;
    return `${this.serverUrl}${path.startsWith('/') ? '' : '/'}${path}`;
  }
}
