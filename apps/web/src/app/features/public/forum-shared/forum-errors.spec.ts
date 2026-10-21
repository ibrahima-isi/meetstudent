import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { classifyForumError } from './forum-errors';

const failure = (status: number, error: unknown = null, headers?: Record<string, string>) =>
  new HttpErrorResponse({ status, error, headers: new HttpHeaders(headers) });

describe('classifyForumError', () => {
  it('maps a 400 {field: message} to the fields the server refused', () => {
    expect(classifyForumError(failure(400, { title: 'Titre vide', body: 'x' }))).toEqual({
      kind: 'fields',
      fields: { title: true, body: true },
    });
  });

  it('ignores unknown fields of a 400, and a 400 without any known field is a plain failure', () => {
    expect(classifyForumError(failure(400, { schoolId: 'inconnu', other: 'x' }))).toEqual({ kind: 'fields', fields: { schoolId: true } });
    expect(classifyForumError(failure(400, { other: 'x' }))).toEqual({ kind: 'failed' });
    expect(classifyForumError(failure(400, 'oops'))).toEqual({ kind: 'failed' });
  });

  it('reads Retry-After in seconds on a 429', () => {
    expect(classifyForumError(failure(429, null, { 'Retry-After': '42' }))).toEqual({ kind: 'rateLimited', seconds: 42 });
  });

  it('keeps a 429 without a usable Retry-After, and never trusts a silly value', () => {
    expect(classifyForumError(failure(429))).toEqual({ kind: 'rateLimited', seconds: null });
    expect(classifyForumError(failure(429, null, { 'Retry-After': 'soon' }))).toEqual({ kind: 'rateLimited', seconds: null });
    expect(classifyForumError(failure(429, null, { 'Retry-After': '-3' }))).toEqual({ kind: 'rateLimited', seconds: null });
    expect(classifyForumError(failure(429, null, { 'Retry-After': '999999' }))).toEqual({ kind: 'rateLimited', seconds: 3600 });
  });

  it('maps 409 to a locked thread, 403 to forbidden, 404 to not found and 401 to signed out', () => {
    expect(classifyForumError(failure(409))).toEqual({ kind: 'locked' });
    expect(classifyForumError(failure(403))).toEqual({ kind: 'forbidden' });
    expect(classifyForumError(failure(404))).toEqual({ kind: 'notFound' });
    expect(classifyForumError(failure(401))).toEqual({ kind: 'signedOut' });
  });

  it('treats everything else (offline, 5xx, not an HTTP error) as a failure', () => {
    expect(classifyForumError(failure(0))).toEqual({ kind: 'failed' });
    expect(classifyForumError(failure(500))).toEqual({ kind: 'failed' });
    expect(classifyForumError(new Error('x'))).toEqual({ kind: 'failed' });
  });
});
