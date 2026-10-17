import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** What the visitor writes on the Contact page. `website` is the honeypot: real visitors leave it empty. */
export interface ContactMessage {
  email: string;
  subject: string;
  message: string;
  language: 'fr' | 'en';
  website: string;
}

@Injectable({ providedIn: 'root' })
export class ContactService {
  private readonly http = inject(HttpClient);

  /**
   * 202 with an empty body on success (also when the honeypot is filled: the
   * server drops it silently). Failures are left as they are: 400 `{field: message}`,
   * 429 with `Retry-After`, status 0 offline, 5xx otherwise.
   */
  send(message: ContactMessage): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/contact`, message);
  }
}
