import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SiteSettings } from '@models/site-settings';
import { API_URL } from './api-config';

@Injectable({ providedIn: 'root' })
export class SiteSettingsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/site-settings`;

  get(): Observable<SiteSettings> {
    return this.http.get<SiteSettings>(this.base);
  }

  /** The API leaves absent fields unchanged, so send only what changed. */
  update(changes: Partial<SiteSettings>): Observable<SiteSettings> {
    return this.http.patch<SiteSettings>(this.base, changes);
  }
}
