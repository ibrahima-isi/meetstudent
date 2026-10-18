import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, map, of, throwError } from 'rxjs';
import { API_URL } from './api-config';

const API_STATUSES = ['SENT', 'LOGGED', 'DISABLED', 'AUTH_FAILED', 'CONNECTION_FAILED', 'TLS_FAILED', 'REJECTED', 'FAILED'] as const;
export type MailTestStatus = (typeof API_STATUSES)[number] | 'COOLDOWN';

export interface MailTestResult {
  status: MailTestStatus;
  /** Seconds to wait (429 only), when the API said so and the value is sane. */
  retryAfter?: number;
}

const MAX_RETRY_AFTER = 3600;

/** Only a known status is ever exposed: whatever else the body holds is dropped. */
function statusOf(body: unknown): MailTestStatus {
  const status = body && typeof body === 'object' ? (body as { status?: unknown }).status : undefined;
  return API_STATUSES.find((s) => s === status) ?? 'FAILED';
}

function retryAfterOf(err: HttpErrorResponse): number | undefined {
  const raw = err.headers?.get('Retry-After')?.trim() ?? '';
  if (!/^\d{1,7}$/.test(raw)) return undefined;
  const seconds = Number(raw);
  return seconds >= 1 && seconds <= MAX_RETRY_AFTER ? seconds : undefined;
}

/** SMTP self-test: sends a mail to the signed-in admin's own address (ADMIN only). */
@Injectable({ providedIn: 'root' })
export class MailTestService {
  private readonly http = inject(HttpClient);
  private readonly url = `${inject(API_URL)}/admin/mail/test`;

  /**
   * 200, 502 and 503 carry the outcome and 429 is the cooldown: all are answers, not errors.
   * Anything else (401, 403, 500, network) is rethrown for the caller to report.
   */
  send(): Observable<MailTestResult> {
    return this.http.post<unknown>(this.url, null).pipe(
      map((body): MailTestResult => ({ status: statusOf(body) })),
      catchError((err: unknown) => {
        if (err instanceof HttpErrorResponse) {
          if (err.status === 502 || err.status === 503) return of<MailTestResult>({ status: statusOf(err.error) });
          if (err.status === 429) {
            const retryAfter = retryAfterOf(err);
            return of<MailTestResult>(retryAfter ? { status: 'COOLDOWN', retryAfter } : { status: 'COOLDOWN' });
          }
        }
        return throwError(() => err);
      }),
    );
  }
}
