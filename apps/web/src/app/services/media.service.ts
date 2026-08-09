import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { Media, MediaCategory } from '@models/entities';

/**
 * Media access.
 *
 * Two distinct paths, because the backend serves public and private files
 * differently:
 *
 * - PUBLIC (school logos/covers, course & program photos): `publicUrl` is a
 *   server-root-relative path served statically. Resolve it with `resolveUrl()`
 *   and bind it straight into `<img src>`.
 * - PRIVATE (diplomas, certificates, bulletins, presentation videos):
 *   `publicUrl` is null. Content comes from `GET /api/v1/media/{id}`, which
 *   enforces owner-or-admin. An `<img src>` pointing there sends no
 *   Authorization header and gets a 403, so the bytes must be fetched through
 *   HttpClient (the JWT interceptor adds the token) and wrapped in an object URL.
 */
@Injectable({
  providedIn: 'root'
})
export class MediaService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/media`;

  /**
   * Absolute URL for a public media, or null when there is nothing to show.
   * Absolute inputs are passed through so seeded/mock data keeps working.
   */
  resolveUrl(media?: Media | null): string | null {
    const path = media?.publicUrl;
    if (!path) return null;
    if (/^https?:\/\//i.test(path)) return path;
    return `${environment.serverUrl}${path.startsWith('/') ? '' : '/'}${path}`;
  }

  /** Upload a file. `idempotencyKey` makes retries safe (deduped per owner). */
  upload(file: File, category: MediaCategory, idempotencyKey?: string): Observable<Media> {
    const form = new FormData();
    form.append('file', file);

    return this.http.post<Media>(this.apiUrl, form, {
      params: { category },
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}
    });
  }

  /**
   * Object URL for a private media's content. The caller owns the returned URL
   * and MUST call `URL.revokeObjectURL()` when done, or the blob leaks.
   */
  blobUrl(mediaId: number): Observable<string> {
    return this.http
      .get(`${this.apiUrl}/${mediaId}`, { responseType: 'blob' })
      .pipe(map(blob => URL.createObjectURL(blob)));
  }

  /** The authenticated user's own media, with verification status. */
  mine(): Observable<Media[]> {
    return this.http.get<Media[]>(`${this.apiUrl}/mine`);
  }

  delete(mediaId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${mediaId}`);
  }
}
