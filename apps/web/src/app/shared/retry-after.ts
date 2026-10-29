import { HttpErrorResponse } from '@angular/common/http';

/** Longer waits are not a password lock-out (the limiter stops at 15 min; a daily mail cap lasts hours). */
const MAX_WAIT_SECONDS = 3600;

export function isTooManyRequests(err: unknown): boolean {
  return err instanceof HttpErrorResponse && err.status === 429;
}

/**
 * The wait announced by a `Retry-After` header (seconds), in whole minutes rounded up (at least 1, at most 60),
 * or null when the header is missing, not a plain number of seconds, or longer than an hour.
 */
export function retryAfterMinutes(err: unknown): number | null {
  if (!(err instanceof HttpErrorResponse)) return null;
  const raw = err.headers?.get('Retry-After')?.trim() ?? '';
  if (!/^\d{1,9}$/.test(raw)) return null;
  const seconds = Number(raw);
  if (seconds > MAX_WAIT_SECONDS) return null;
  return Math.max(1, Math.ceil(seconds / 60));
}
