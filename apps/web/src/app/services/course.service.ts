import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
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
