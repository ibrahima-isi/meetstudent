import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Course, CourseInput } from '@models/course';
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
}
