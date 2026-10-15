import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Course, CourseAccreditation, CourseInput } from '@models/course';
import { Page } from '@models/entities';
import { API_URL } from './api-config';

@Injectable({ providedIn: 'root' })
export class CourseService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/courses`;

  create(input: CourseInput): Observable<Course> {
    return this.http.post<Course>(this.base, input);
  }

  update(id: number, input: CourseInput): Observable<Course> {
    return this.http.put<Course>(`${this.base}/${id}`, input);
  }

  delete(id: number): Observable<Course> {
    return this.http.delete<Course>(`${this.base}/${id}`);
  }

  accreditationsOf(courseId: number, page = 0): Observable<Page<CourseAccreditation>> {
    return this.http.get<Page<CourseAccreditation>>(`${this.base}/${courseId}/accreditations`, {
      params: { page, size: 200 },
    });
  }

  link(courseId: number, accreditationId: number, startsAt: number, endsAt: number): Observable<CourseAccreditation> {
    return this.http.post<CourseAccreditation>(`${this.base}/${courseId}/accreditations/${accreditationId}`, null, {
      params: { startsAt, endsAt },
    });
  }

  unlink(courseId: number, accreditationId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${courseId}/accreditations/${accreditationId}`);
  }
}
