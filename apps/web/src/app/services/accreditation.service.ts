import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { Accreditation } from '@models/entities';

@Injectable({
  providedIn: 'root'
})
export class AccreditationService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/accreditations`;

  private accreditationsSignal = signal<Accreditation[]>([]);
  readonly accreditations = this.accreditationsSignal.asReadonly();

  getAccreditations(): Observable<Accreditation[]> {
    return this.http.get<Accreditation[]>(this.apiUrl).pipe(
      tap(accs => this.accreditationsSignal.set(accs))
    );
  }

  getSchoolAccreditations(schoolId: number): Observable<Accreditation[]> {
    return this.http.get<Accreditation[]>(`${this.apiUrl}/school/${schoolId}`).pipe(
      tap(accs => {
        // We could merge or replace depending on use case, here we'll just set it
        this.accreditationsSignal.set(accs);
      })
    );
  }
}
