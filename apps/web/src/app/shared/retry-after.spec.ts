import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { isTooManyRequests, retryAfterMinutes } from './retry-after';

const failure = (status: number, headers?: Record<string, string>) =>
  new HttpErrorResponse({ status, headers: new HttpHeaders(headers) });

describe('isTooManyRequests', () => {
  it('is true for a 429 only', () => {
    expect(isTooManyRequests(failure(429))).toBeTrue();
    expect(isTooManyRequests(failure(401))).toBeFalse();
    expect(isTooManyRequests(failure(500))).toBeFalse();
    expect(isTooManyRequests(new Error('x'))).toBeFalse();
  });
});

describe('retryAfterMinutes', () => {
  it('rounds the seconds up to whole minutes', () => {
    expect(retryAfterMinutes(failure(429, { 'Retry-After': '60' }))).toBe(1);
    expect(retryAfterMinutes(failure(429, { 'Retry-After': '61' }))).toBe(2);
    expect(retryAfterMinutes(failure(429, { 'Retry-After': '300' }))).toBe(5);
    expect(retryAfterMinutes(failure(429, { 'Retry-After': '900' }))).toBe(15);
  });

  it('never says zero minutes', () => {
    expect(retryAfterMinutes(failure(429, { 'Retry-After': '1' }))).toBe(1);
    expect(retryAfterMinutes(failure(429, { 'Retry-After': '0' }))).toBe(1);
  });

  it('returns null without a usable header, and never trusts a silly value', () => {
    expect(retryAfterMinutes(failure(429))).toBeNull();
    expect(retryAfterMinutes(failure(429, { 'Retry-After': 'soon' }))).toBeNull();
    expect(retryAfterMinutes(failure(429, { 'Retry-After': '-3' }))).toBeNull();
    expect(retryAfterMinutes(new Error('x'))).toBeNull();
  });

  it('keeps a wait of up to one hour', () => {
    expect(retryAfterMinutes(failure(429, { 'Retry-After': '120' }))).toBe(2);
    expect(retryAfterMinutes(failure(429, { 'Retry-After': '3600' }))).toBe(60);
  });

  it('returns null beyond one hour: that is not a password lock-out (e.g. a daily cap), so callers keep their generic message', () => {
    expect(retryAfterMinutes(failure(429, { 'Retry-After': '3601' }))).toBeNull();
    expect(retryAfterMinutes(failure(429, { 'Retry-After': '7200' }))).toBeNull();
    expect(retryAfterMinutes(failure(429, { 'Retry-After': '86400' }))).toBeNull();
    expect(retryAfterMinutes(failure(429, { 'Retry-After': '999999' }))).toBeNull();
  });
});
