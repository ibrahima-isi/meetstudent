import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '@models/entities';
import { ContactMessage } from '@models/message';
import { API_URL } from './api-config';

@Injectable({ providedIn: 'root' })
export class MessageService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/admin/messages`;

  /** One page of contact messages; the API fixes the order (newest first). */
  list(page: number, size: number): Observable<Page<ContactMessage>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<ContactMessage>>(this.base, { params });
  }

  /** Asks the API to send the notification mail again: 202, 404, 409 (already sending) or 503 (mail disabled). */
  resend(id: number): Observable<{ status: string }> {
    return this.http.post<{ status: string }>(`${this.base}/${id}/resend`, null);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
