import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '@models/entities';
import { ExpertAccount, ExpertStatus } from '@models/expert';
import { API_URL } from './api-config';

@Injectable({ providedIn: 'root' })
export class ExpertService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/admin/experts`;

  /** One page of experts of a status; the API fixes the order (newest registration first). */
  list(status: ExpertStatus, page: number, size: number): Observable<Page<ExpertAccount>> {
    const params = new HttpParams().set('status', status).set('page', page).set('size', size);
    return this.http.get<Page<ExpertAccount>>(this.base, { params });
  }

  /** Also re-approves a rejected expert. 404 unknown account, 409 not an expert. */
  approve(id: number): Observable<ExpertAccount> {
    return this.http.post<ExpertAccount>(`${this.base}/${id}/approve`, null);
  }

  /** The reason is optional; a blank one is not sent. */
  reject(id: number, reason: string): Observable<ExpertAccount> {
    const trimmed = reason.trim();
    return this.http.post<ExpertAccount>(`${this.base}/${id}/reject`, trimmed ? { reason: trimmed } : null);
  }
}
