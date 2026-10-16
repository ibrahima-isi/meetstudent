import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, of, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { Program, Page } from '@models/entities';
import { MediaService } from './media.service';

@Injectable({
  providedIn: 'root'
})
export class ProgramService {
  private http = inject(HttpClient);
  private mediaService = inject(MediaService);
  private apiUrl = `${environment.apiUrl}/programs`;

  private programsSignal = signal<Program[]>([]);
  readonly programs = this.programsSignal.asReadonly();

  getPrograms(page: number = 0, size: number = 10, sortRate?: string): Observable<Page<Program>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    
    if (sortRate) {
      params = params.set('sortRate', sortRate);
    }

    return this.http.get<Page<Program>>(this.apiUrl, { params }).pipe(
      map(res => ({
        ...res,
        content: res.content.map(p => this.mapProgramFields(p))
      })),
      tap(res => {
        if (res.content && res.content.length > 0) {
          this.programsSignal.set(res.content);
        }
      })
    );
  }

  private mapProgramFields(program: Program): Program {
    return {
      ...program,
      // Nothing is invented: no rate, review count, level or description unless the API sent one.
      rating: program.averageRate ?? program.rating ?? undefined,
      accreditations: program.accreditations || [],
      photoImageUrl: this.mediaService.resolveUrl(program.photo) ?? program.photoImageUrl
    };
  }

  getProgram(id: number): Observable<Program> {
    return this.http.get<Program>(`${this.apiUrl}/${id}`).pipe(
      map(p => this.mapProgramFields(p))
    );
  }

  /**
   * The term travels as a path segment, like the school search: `/` and `\` are
   * refused by the server before routing, `%` and `;` are mangled, `.`/`..` are
   * resolved away. Such a term would fail forever, so it answers an empty page.
   */
  searchProgramsByName(name: string, page: number = 0, size: number = 10): Observable<Page<Program>> {
    const term = name.trim();
    if (!term) {
      return this.getPrograms(page, size);
    }
    if (/^\.{1,2}$/.test(term) || /[/\\%;]/.test(term)) {
      return of(this.emptyPage(size));
    }
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<Page<Program>>(`${this.apiUrl}/name/${encodeURIComponent(term)}`, { params }).pipe(
      map(res => ({
        ...res,
        content: res.content.map(p => this.mapProgramFields(p))
      }))
    );
  }

  private emptyPage(size: number): Page<Program> {
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
