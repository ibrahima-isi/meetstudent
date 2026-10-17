import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** What an admin sets in the back office for the About and Contact pages. */
export interface SiteSettings {
  contactEmail: string;
  aboutFr: string;
  aboutEn: string;
  /** Social links: full https URLs, null when the admin has not set them. */
  whatsappUrl?: string | null;
  telegramUrl?: string | null;
  discordUrl?: string | null;
  facebookUrl?: string | null;
  twitterUrl?: string | null;
  instagramUrl?: string | null;
  tiktokUrl?: string | null;
}

@Injectable({ providedIn: 'root' })
export class SiteSettingsService {
  private readonly http = inject(HttpClient);

  get(): Observable<SiteSettings> {
    return this.http.get<SiteSettings>(`${environment.apiUrl}/site-settings`);
  }
}
