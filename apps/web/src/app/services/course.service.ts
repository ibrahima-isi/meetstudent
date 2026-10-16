import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, of } from 'rxjs';
import { environment } from '../../environments/environment';
import { Course, Page } from '@models/entities';
import { MediaService } from './media.service';

@Injectable({
  providedIn: 'root'
})
export class CourseService {
  private http = inject(HttpClient);
  private mediaService = inject(MediaService);
  private apiUrl = `${environment.apiUrl}/courses`;

  private mapCourseFields(course: Course): Course {
    return {
      ...course,
      photoImageUrl: this.mediaService.resolveUrl(course.photo) ?? course.photoImageUrl
    };
  }

  getCourse(id: number): Observable<Course> {
    return this.http.get<Course>(`${this.apiUrl}/${id}`).pipe(
      map(c => this.mapCourseFields(c))
    );
  }

  listCourses(page: number = 0, size: number = 10): Observable<Page<Course>> {
    const params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    return this.http.get<Page<Course>>(this.apiUrl, { params }).pipe(map(res => this.mapPage(res)));
  }

  /**
   * The term travels as a path segment: `/` and `\` are refused by the server
   * before routing, `%` and `;` are mangled, `.`/`..` are resolved away. Such a
   * term would fail forever, so it answers an empty page without a request.
   */
  searchCoursesByName(name: string, page: number = 0, size: number = 10): Observable<Page<Course>> {
    const term = name.trim();
    if (!term) {
      return this.listCourses(page, size);
    }
    if (/^\.{1,2}$/.test(term) || /[/\\%;]/.test(term)) {
      return of(this.emptyPage(size));
    }
    const params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    return this.http
      .get<Page<Course>>(`${this.apiUrl}/name/${encodeURIComponent(term)}`, { params })
      .pipe(map(res => this.mapPage(res)));
  }

  private mapPage(res: Page<Course>): Page<Course> {
    return { ...res, content: (res.content ?? []).map(c => this.mapCourseFields(c)) };
  }

  private emptyPage(size: number): Page<Course> {
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
