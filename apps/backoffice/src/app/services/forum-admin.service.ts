import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '@models/entities';
import { ForumModerated, ForumReport, ForumReportStatus } from '@models/forum';
import { API_URL } from './api-config';

/**
 * Forum moderation (`/admin/forum`, ADMIN only). Hide, unhide, lock, unlock and closing a report are idempotent; a
 * second delete answers 404. The text of a message is never edited from here.
 */
@Injectable({ providedIn: 'root' })
export class ForumAdminService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/admin/forum`;

  /** One page of reports of a status; the API fixes the order (newest first). */
  reports(status: ForumReportStatus, page: number, size: number): Observable<Page<ForumReport>> {
    const params = new HttpParams().set('status', status).set('page', page).set('size', size);
    return this.http.get<Page<ForumReport>>(`${this.base}/reports`, { params });
  }

  handle(id: number): Observable<ForumReport> {
    return this.http.post<ForumReport>(`${this.base}/reports/${id}/handle`, null);
  }

  dismiss(id: number): Observable<ForumReport> {
    return this.http.post<ForumReport>(`${this.base}/reports/${id}/dismiss`, null);
  }

  hideThread(id: number): Observable<ForumModerated> {
    return this.post(`threads/${id}/hide`);
  }

  unhideThread(id: number): Observable<ForumModerated> {
    return this.post(`threads/${id}/unhide`);
  }

  lockThread(id: number): Observable<ForumModerated> {
    return this.post(`threads/${id}/lock`);
  }

  unlockThread(id: number): Observable<ForumModerated> {
    return this.post(`threads/${id}/unlock`);
  }

  hideReply(id: number): Observable<ForumModerated> {
    return this.post(`replies/${id}/hide`);
  }

  unhideReply(id: number): Observable<ForumModerated> {
    return this.post(`replies/${id}/unhide`);
  }

  /** Final: removes the replies and every report on them. */
  deleteThread(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/threads/${id}`);
  }

  /** Final: removes every report on the reply. */
  deleteReply(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/replies/${id}`);
  }

  private post(path: string): Observable<ForumModerated> {
    return this.http.post<ForumModerated>(`${this.base}/${path}`, null);
  }
}
