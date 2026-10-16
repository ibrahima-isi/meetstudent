import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { environment } from '../../environments/environment';
import { Accreditation, Page } from '@models/entities';

@Injectable({
  providedIn: 'root'
})
export class AccreditationService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/accreditations`;

  listAccreditations(page: number = 0, size: number = 10): Observable<Page<Accreditation>> {
    const params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    return this.http.get<Page<Accreditation>>(this.apiUrl, { params });
  }

  /**
   * The term travels as a path segment: `/` and `\` are refused by the server
   * before routing, `%` and `;` are mangled, `.`/`..` are resolved away. Such a
   * term would fail forever, so it answers an empty page without a request.
   */
  searchAccreditationsByName(name: string, page: number = 0, size: number = 10): Observable<Page<Accreditation>> {
    const term = name.trim();
    if (!term) {
      return this.listAccreditations(page, size);
    }
    if (/^\.{1,2}$/.test(term) || /[/\\%;]/.test(term)) {
      return of(this.emptyPage(size));
    }
    const params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    return this.http.get<Page<Accreditation>>(`${this.apiUrl}/name/${encodeURIComponent(term)}`, { params });
  }

  private emptyPage(size: number): Page<Accreditation> {
    const sort = { empty: true, sorted: false, unsorted: true };
    return {
      content: [],
      pageable: { sort, offset: 0, pageNumber: 0, pageSize: size, paged: true, unpaged: false },
      last: true,
      totalPages: 0,
      totalElements: 0,
      size,
      number: 0,
      sort,
      first: true,
      numberOfElements: 0,
      empty: true,
    };
  }
}
