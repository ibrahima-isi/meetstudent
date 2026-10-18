import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MailTestResult, MailTestService } from './mail-test.service';
import { API_URL } from './api-config';

const api = 'http://api.test/api/v1';
const url = `${api}/admin/mail/test`;

describe('MailTestService', () => {
  let backend: HttpTestingController;
  let service: MailTestService;

  const run = (status: number, body: string | object | null, headers: Record<string, string> = {}) => {
    let result: MailTestResult | undefined;
    let error: unknown;
    service.send().subscribe({ next: (r) => (result = r), error: (e: unknown) => (error = e) });
    const req = backend.expectOne(url);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeNull();
    req.flush(body, { status, statusText: 'x', headers });
    return { result, error };
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: api },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    service = TestBed.inject(MailTestService);
  });

  afterEach(() => backend.verify());

  it('answers SENT and LOGGED on 200', () => {
    expect(run(200, { status: 'SENT' }).result).toEqual({ status: 'SENT' });
    expect(run(200, { status: 'LOGGED' }).result).toEqual({ status: 'LOGGED' });
  });

  it('turns 502 and 503 bodies into their status instead of an error', () => {
    for (const status of ['AUTH_FAILED', 'CONNECTION_FAILED', 'TLS_FAILED', 'REJECTED', 'FAILED']) {
      expect(run(502, { status }).result).toEqual({ status } as MailTestResult);
    }
    expect(run(503, { status: 'DISABLED' }).result).toEqual({ status: 'DISABLED' });
  });

  it('never trusts an unknown status: it becomes FAILED', () => {
    expect(run(200, { status: 'SENT <b>x</b>' }).result).toEqual({ status: 'FAILED' });
    expect(run(502, 'oops').result).toEqual({ status: 'FAILED' });
    expect(run(502, null).result).toEqual({ status: 'FAILED' });
  });

  it('answers COOLDOWN on 429 with the Retry-After seconds when sane', () => {
    expect(run(429, { message: 'x' }, { 'Retry-After': '8' }).result).toEqual({ status: 'COOLDOWN', retryAfter: 8 });
    expect(run(429, { message: 'x' }).result).toEqual({ status: 'COOLDOWN' });
    expect(run(429, { message: 'x' }, { 'Retry-After': 'soon' }).result).toEqual({ status: 'COOLDOWN' });
    expect(run(429, { message: 'x' }, { 'Retry-After': '0' }).result).toEqual({ status: 'COOLDOWN' });
    expect(run(429, { message: 'x' }, { 'Retry-After': '999999' }).result).toEqual({ status: 'COOLDOWN' });
  });

  it('lets other errors (401, 403, 500) reach the caller', () => {
    for (const status of [401, 403, 500]) {
      const { result, error } = run(status, { message: 'x' });
      expect(result).toBeUndefined();
      expect((error as HttpErrorResponse).status).toBe(status);
    }
  });
});
