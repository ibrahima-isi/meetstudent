import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '@models/entities';
import { School, SchoolInput } from '@models/school';
import { API_URL } from './api-config';

@Injectable({ providedIn: 'root' })
export class SchoolService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/schools`;

  /** One page of schools by name; a non-blank `search` uses the name-search endpoint. */
  list(page: number, size: number, search = ''): Observable<Page<School>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', 'name,asc');
    const term = search.trim();
    const url = term ? `${this.base}/name/${encodeURIComponent(term)}` : this.base;
    return this.http.get<Page<School>>(url, { params });
  }

  create(input: SchoolInput): Observable<School> {
    return this.http.post<School>(this.base, input);
  }

  update(id: number, input: SchoolInput): Observable<School> {
    return this.http.put<School>(`${this.base}/${id}`, input);
  }

  delete(id: number): Observable<School> {
    return this.http.delete<School>(`${this.base}/${id}`);
  }
}
