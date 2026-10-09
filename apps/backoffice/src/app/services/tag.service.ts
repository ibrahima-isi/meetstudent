import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Tag } from '@models/school';
import { API_URL } from './api-config';

@Injectable({ providedIn: 'root' })
export class TagService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/tags`;

  /** Every tag (the endpoint is not paginated). */
  list(): Observable<Tag[]> {
    return this.http.get<Tag[]>(this.base);
  }

  create(name: string): Observable<Tag> {
    return this.http.post<Tag>(this.base, { name });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
