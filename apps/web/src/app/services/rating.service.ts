import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { SchoolRate, ProgramRate, CourseRate } from '@models/entities';

@Injectable({
  providedIn: 'root'
})
export class RatingService {
  private http = inject(HttpClient);
  private schoolRateUrl = `${environment.apiUrl}/school-rates`;
  private programRateUrl = `${environment.apiUrl}/program-rates`;
  private courseRateUrl = `${environment.apiUrl}/course-rates`;

  private lastSchoolRateSignal = signal<SchoolRate | null>(null);
  readonly lastSchoolRate = this.lastSchoolRateSignal.asReadonly();

  private lastProgramRateSignal = signal<ProgramRate | null>(null);
  readonly lastProgramRate = this.lastProgramRateSignal.asReadonly();

  private lastCourseRateSignal = signal<CourseRate | null>(null);
  readonly lastCourseRate = this.lastCourseRateSignal.asReadonly();

  rateSchool(schoolId: number, userId: number, note: number, comment: string = ''): Observable<SchoolRate> {
    return this.http.post<SchoolRate>(this.schoolRateUrl, { schoolId, userId, note, comment }).pipe(
      tap(rate => this.lastSchoolRateSignal.set(rate))
    );
  }

  rateProgram(programId: number, userId: number, note: number, comment: string = ''): Observable<ProgramRate> {
    return this.http.post<ProgramRate>(this.programRateUrl, { programId, userId, note, comment }).pipe(
      tap(rate => this.lastProgramRateSignal.set(rate))
    );
  }

  rateCourse(courseId: number, userId: number, note: number, comment: string = ''): Observable<CourseRate> {
    return this.http.post<CourseRate>(this.courseRateUrl, { courseId, userId, note, comment }).pipe(
      tap(rate => this.lastCourseRateSignal.set(rate))
    );
  }
}
