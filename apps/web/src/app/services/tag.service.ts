import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { Tag } from '@models/entities';

@Injectable({
  providedIn: 'root'
})
export class TagService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/tags`;

  private tagsSignal = signal<Tag[]>([]);
  readonly tags = this.tagsSignal.asReadonly();

  getTags(): Observable<Tag[]> {
    return this.http.get<Tag[]>(this.apiUrl).pipe(
      tap(tags => this.tagsSignal.set(tags))
    );
  }

  createTag(name: string): Observable<Tag> {
    return this.http.post<Tag>(this.apiUrl, { name }).pipe(
      tap(tag => this.tagsSignal.update(tags => [...tags, tag]))
    );
  }
}
