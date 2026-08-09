import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';
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
      rating: (program as any).averageRate || program.rating || 0,
      reviewCount: program.reviewCount || 0,
      level: program.level || 'Licence',
      description: program.description || 'Aucune description disponible',
      accreditations: program.accreditations || [],
      photoImageUrl: this.mediaService.resolveUrl(program.photo) ?? program.photoImageUrl
    };
  }

  getProgram(id: number): Observable<Program> {
    return this.http.get<Program>(`${this.apiUrl}/${id}`).pipe(
      map(p => this.mapProgramFields(p))
    );
  }

  searchProgramsByName(name: string, page: number = 0, size: number = 10): Observable<Page<Program>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<Page<Program>>(`${this.apiUrl}/name/${name}`, { params }).pipe(
      map(res => ({
        ...res,
        content: res.content.map(p => this.mapProgramFields(p))
      }))
    );
  }
}
