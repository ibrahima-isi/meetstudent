import { HttpErrorResponse } from '@angular/common/http';

const MAX_WAIT_SECONDS = 3600;

export function isTooManyRequests(err: unknown): boolean {
  return err instanceof HttpErrorResponse && err.status === 429;
}

/**
 * The wait announced by a `Retry-After` header (seconds), in whole minutes rounded up (at least 1, at most 60),
 * or null when the header is missing or not a plain number of seconds.
 */
export function retryAfterMinutes(err: unknown): number | null {
  if (!(err instanceof HttpErrorResponse)) return null;
  const raw = err.headers?.get('Retry-After')?.trim() ?? '';
  if (!/^\d{1,9}$/.test(raw)) return null;
  const seconds = Math.min(Number(raw), MAX_WAIT_SECONDS);
  return Math.max(1, Math.ceil(seconds / 60));
}
