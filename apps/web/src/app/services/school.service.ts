import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, of } from 'rxjs';
import { environment } from '../../environments/environment';
import { School, Page } from '@models/entities';
import { MediaService } from './media.service';

@Injectable({
  providedIn: 'root'
})
export class SchoolService {
  private http = inject(HttpClient);
  private mediaService = inject(MediaService);
  private apiUrl = `${environment.apiUrl}/schools`;

  getSchools(page: number = 0, size: number = 10, sortRate?: string, sort?: string): Observable<Page<School>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    
    if (sortRate) {
      params = params.set('sortRate', sortRate);
    }
    params = this.withSort(params, sort);

    return this.http.get<Page<School>>(this.apiUrl, { params }).pipe(
      map(res => ({
        ...res,
        content: res.content.map(school => this.mapSchoolFields(school))
      }))
    );
  }

  private mapSchoolFields(school: School): School {
    return {
      ...school,
      rating: (school as any).averageRate || school.rating || 0,
      // No default for type or description: the service stays locale-free and
      // the template shows a fallback in the active language.
      accreditations: school.accreditations || [],
      // Resolve media FKs to absolute URLs so templates bind one plain field.
      // `??` preserves any URL already set by seeded/mock data.
      logoImageUrl: this.mediaService.resolveUrl(school.logo) ?? school.logoImageUrl,
      coverImageUrl: this.mediaService.resolveUrl(school.cover) ?? school.coverImageUrl
    };
  }

  getSchool(id: number): Observable<School> {
    return this.http.get<School>(`${this.apiUrl}/${id}`).pipe(
      map(school => this.mapSchoolFields(school))
    );
  }

  searchSchools(city?: string, country?: string, tag?: string, program?: string, page: number = 0, size: number = 10, sort?: string): Observable<Page<School>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    
    if (city) params = params.set('city', city);
    if (country) params = params.set('country', country);
    if (tag) params = params.set('tag', tag);
    if (program) params = params.set('program', program);
    params = this.withSort(params, sort);

    return this.http.get<Page<School>>(`${this.apiUrl}/search`, { params }).pipe(
      map(res => ({
        ...res,
        content: res.content.map(school => this.mapSchoolFields(school))
      }))
    );
  }

  /**
   * Name contains `name`, case-insensitive, paged by the API (`/schools/name/{name}`).
   * The term travels as a path variable, and a few characters cannot: `/` and `\`
   * are decoded or refused by the server before routing, `%` and `;` are mangled by
   * it. No school name carries them in the search sense the visitor means, so the
   * answer is an empty page without bothering the API.
   */
  searchSchoolsByName(name: string, page: number = 0, size: number = 10, sort?: string): Observable<Page<School>> {
    const term = name.trim();
    const encoded = this.encodeTerm(term);
    if (encoded === null) {
      return of(this.emptyPage(size));
    }
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    params = this.withSort(params, sort);

    return this.http.get<Page<School>>(`${this.apiUrl}/name/${encoded}`, { params }).pipe(
      map(res => ({
        ...res,
        content: res.content.map(school => this.mapSchoolFields(school))
      }))
    );
  }

  /** School names are at most 50 characters; anything longer cannot match. */
  private static readonly MAX_TERM_LENGTH = 50;

  /**
   * The path-safe form of a term, or null when it cannot be searched: empty
   * dot segments (collapsed by URL parsing), characters the path cannot carry,
   * over-long terms, and lone surrogates (encodeURIComponent throws on them).
   */
  private encodeTerm(term: string): string | null {
    if (term.length > SchoolService.MAX_TERM_LENGTH || /^\.{1,2}$/.test(term) || /[/\\%;]/.test(term)) {
      return null;
    }
    try {
      return encodeURIComponent(term);
    } catch {
      return null;
    }
  }

  /** Sort plus `id` as a tiebreaker, so paging over equal keys neither repeats nor skips a school. */
  private withSort(params: HttpParams, sort?: string): HttpParams {
    if (!sort) return params;
    const withSort = params.append('sort', sort);
    return sort.startsWith('id,') ? withSort : withSort.append('sort', 'id,asc');
  }

  private emptyPage(size: number): Page<School> {
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
