import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { Course } from '@models/entities';
import { MediaService } from './media.service';

@Injectable({
  providedIn: 'root'
})
export class CourseService {
  private http = inject(HttpClient);
  private mediaService = inject(MediaService);
  private apiUrl = `${environment.apiUrl}/courses`;

  private coursesSignal = signal<Course[]>([]);
  readonly courses = this.coursesSignal.asReadonly();

  getCoursesByProgram(programId: number): Observable<Course[]> {
    return this.http.get<Course[]>(`${this.apiUrl}/program/${programId}`).pipe(
      map(courses => courses.map(c => this.mapCourseFields(c))),
      tap(courses => {
        if (courses && courses.length > 0) {
          this.coursesSignal.set(courses);
        }
      })
    );
  }

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
}
