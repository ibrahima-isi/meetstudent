import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';
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

  private schoolsSignal = signal<School[]>([]);
  readonly schools = this.schoolsSignal.asReadonly();

  getSchools(page: number = 0, size: number = 10, sortRate?: string): Observable<Page<School>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    
    if (sortRate) {
      params = params.set('sortRate', sortRate);
    }

    return this.http.get<Page<School>>(this.apiUrl, { params }).pipe(
      map(res => ({
        ...res,
        content: res.content.map(school => this.mapSchoolFields(school))
      })),
      tap(res => {
        if (res.content && res.content.length > 0) {
          this.schoolsSignal.set(res.content);
        }
      })
    );
  }

  private mapSchoolFields(school: School): School {
    return {
      ...school,
      rating: (school as any).averageRate || school.rating || 0,
      reviewCount: school.reviewCount || 0,
      type: school.type || 'Établissement',
      description: school.description || 'Aucune description disponible',
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

  searchSchools(city?: string, country?: string, tag?: string, program?: string, page: number = 0, size: number = 10): Observable<Page<School>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    
    if (city) params = params.set('city', city);
    if (country) params = params.set('country', country);
    if (tag) params = params.set('tag', tag);
    if (program) params = params.set('program', program);

    return this.http.get<Page<School>>(`${this.apiUrl}/search`, { params }).pipe(
      map(res => ({
        ...res,
        content: res.content.map(school => this.mapSchoolFields(school))
      }))
    );
  }
}
