import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ForumReply, ForumReplyPage, ForumThread, ForumThreadPage, ForumThreadRequest } from '@models/forum';
import { environment } from '../../environments/environment';

export interface ForumPageRequest {
  page: number;
  size: number;
}

/**
 * The forum API. Reads are public; writes need a student or an approved expert. Errors are left as they
 * come (400 `{field: message}`, 403, 404, 409 locked thread, 429 with `Retry-After`) for the pages to map.
 */
@Injectable({ providedIn: 'root' })
export class ForumService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/forum`;

  listThreads(opts: ForumPageRequest & { schoolId?: number }): Observable<ForumThreadPage> {
    let params = new HttpParams().set('page', opts.page).set('size', opts.size);
    if (opts.schoolId !== undefined) params = params.set('schoolId', opts.schoolId);
    return this.http.get<ForumThreadPage>(`${this.url}/threads`, { params });
  }

  getThread(id: number): Observable<ForumThread> {
    return this.http.get<ForumThread>(`${this.url}/threads/${id}`);
  }

  listReplies(threadId: number, opts: ForumPageRequest): Observable<ForumReplyPage> {
    const params = new HttpParams().set('page', opts.page).set('size', opts.size);
    return this.http.get<ForumReplyPage>(`${this.url}/threads/${threadId}/replies`, { params });
  }

  createThread(request: ForumThreadRequest): Observable<ForumThread> {
    return this.http.post<ForumThread>(`${this.url}/threads`, request);
  }

  /** The school of a thread never changes, so it is not sent. */
  updateThread(id: number, request: ForumThreadRequest): Observable<ForumThread> {
    return this.http.put<ForumThread>(`${this.url}/threads/${id}`, { title: request.title, body: request.body });
  }

  deleteThread(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/threads/${id}`);
  }

  createReply(threadId: number, body: string): Observable<ForumReply> {
    return this.http.post<ForumReply>(`${this.url}/threads/${threadId}/replies`, { body });
  }

  updateReply(id: number, body: string): Observable<ForumReply> {
    return this.http.put<ForumReply>(`${this.url}/replies/${id}`, { body });
  }

  deleteReply(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/replies/${id}`);
  }
}
