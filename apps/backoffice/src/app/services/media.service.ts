import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Media, Page, VerificationStatus } from '@models/entities';
import { API_URL } from './api-config';

@Injectable({ providedIn: 'root' })
export class MediaService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = inject(API_URL);

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
}
