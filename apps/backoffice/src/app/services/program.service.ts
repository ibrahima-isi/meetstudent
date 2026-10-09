import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '@models/entities';
import { Program, ProgramAccreditation, ProgramInput } from '@models/program';
import { API_URL } from './api-config';

@Injectable({ providedIn: 'root' })
export class ProgramService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/programs`;

  /** One page of programs by name; a non-blank `search` uses the name-search endpoint. */
  list(page: number, size: number, search = ''): Observable<Page<Program>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', 'name,asc');
    const term = search.trim();
    const url = term ? `${this.base}/name/${encodeURIComponent(term)}` : this.base;
    return this.http.get<Page<Program>>(url, { params });
  }

  create(input: ProgramInput): Observable<Program> {
    return this.http.post<Program>(this.base, input);
  }

  update(id: number, input: ProgramInput): Observable<Program> {
    return this.http.put<Program>(`${this.base}/${id}`, input);
  }

  delete(id: number): Observable<Program> {
    return this.http.delete<Program>(`${this.base}/${id}`);
  }

  accreditationsOf(programId: number): Observable<ProgramAccreditation[]> {
    return this.http.get<ProgramAccreditation[]>(`${this.base}/${programId}/accreditations`);
  }

  /** The API takes the validity years as query parameters, both required. */
  link(programId: number, accreditationId: number, startsAt: number, endsAt: number): Observable<ProgramAccreditation> {
    const params = new HttpParams().set('startsAt', startsAt).set('endsAt', endsAt);
    return this.http.post<ProgramAccreditation>(`${this.base}/${programId}/accreditations/${accreditationId}`, null, { params });
  }

  unlink(programId: number, accreditationId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${programId}/accreditations/${accreditationId}`);
  }
}
