import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '@models/entities';
import { Accreditation, AccreditationInput } from '@models/accreditation';
import { API_URL } from './api-config';

@Injectable({ providedIn: 'root' })
export class AccreditationService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/accreditations`;

  /** One page of accreditations, sorted by name. */
  list(page: number, size: number): Observable<Page<Accreditation>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', 'name,asc');
    return this.http.get<Page<Accreditation>>(this.base, { params });
  }

  create(input: AccreditationInput): Observable<Accreditation> {
    return this.http.post<Accreditation>(this.base, input);
  }

  update(id: number, input: AccreditationInput): Observable<Accreditation> {
    return this.http.put<Accreditation>(`${this.base}/${id}`, input);
  }

  delete(id: number): Observable<Accreditation> {
    return this.http.delete<Accreditation>(`${this.base}/${id}`);
  }
}
