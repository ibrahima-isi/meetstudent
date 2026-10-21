import { HttpErrorResponse } from '@angular/common/http';

export type ForumField = 'title' | 'body' | 'schoolId' | 'reason';
const FIELDS: ForumField[] = ['title', 'body', 'schoolId', 'reason'];

/** What a failed forum call means for the visitor; the pages choose the words. */
export type ForumFailure =
  | { kind: 'fields'; fields: Partial<Record<ForumField, true>> }
  | { kind: 'rateLimited'; seconds: number | null }
  | { kind: 'locked' }
  | { kind: 'forbidden' }
  | { kind: 'notFound' }
  | { kind: 'signedOut' }
  | { kind: 'failed' };

const MAX_WAIT_SECONDS = 3600;

function retryAfter(err: HttpErrorResponse): number | null {
  const raw = err.headers?.get('Retry-After')?.trim() ?? '';
  if (!/^\d{1,9}$/.test(raw)) return null;
  return Math.min(Number(raw), MAX_WAIT_SECONDS);
}

/** 400 `{field: message}`, 401, 403, 404, 409 (locked thread), 429 + `Retry-After`; anything else is a failure. */
export function classifyForumError(err: unknown): ForumFailure {
  if (!(err instanceof HttpErrorResponse)) return { kind: 'failed' };
  switch (err.status) {
    case 400: {
      const body = err.error;
      if (typeof body !== 'object' || body === null) return { kind: 'failed' };
      const fields = Object.fromEntries(FIELDS.filter((f) => f in body).map((f) => [f, true as const]));
      return Object.keys(fields).length > 0 ? { kind: 'fields', fields } : { kind: 'failed' };
    }
    case 401:
      return { kind: 'signedOut' };
    case 403:
      return { kind: 'forbidden' };
    case 404:
      return { kind: 'notFound' };
    case 409:
      return { kind: 'locked' };
    case 429:
      return { kind: 'rateLimited', seconds: retryAfter(err) };
    default:
      return { kind: 'failed' };
  }
}
