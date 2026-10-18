import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MessageService } from './message.service';
import { API_URL } from './api-config';

const api = 'http://api.test/api/v1';

describe('MessageService', () => {
  let backend: HttpTestingController;
  let service: MessageService;

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
    service = TestBed.inject(MessageService);
  });

  afterEach(() => backend.verify());

  it('lists one page with GET /admin/messages and no sort (the order is fixed server-side)', () => {
    let result: unknown;
    service.list(1, 10).subscribe((p) => (result = p));

    const req = backend.expectOne((r) => r.url === `${api}/admin/messages`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.has('sort')).toBeFalse();
    const body = { content: [], number: 1, totalPages: 2, totalElements: 11, size: 10 };
    req.flush(body);
    expect(result).toEqual(body);
  });

  it('deletes with DELETE /admin/messages/{id}', () => {
    let done = false;
    service.delete(7).subscribe(() => (done = true));
    const req = backend.expectOne(`${api}/admin/messages/7`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(done).toBeTrue();
  });

  it('resends the mail with POST /admin/messages/{id}/resend and no body', () => {
    let result: unknown;
    service.resend(7).subscribe((r) => (result = r));
    const req = backend.expectOne(`${api}/admin/messages/7/resend`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeNull();
    req.flush({ status: 'accepted' }, { status: 202, statusText: 'Accepted' });
    expect(result).toEqual({ status: 'accepted' });
  });

  it('passes the resend errors (404, 409, 503) through to the caller', () => {
    const statuses: number[] = [];
    for (const status of [404, 409, 503]) {
      service.resend(7).subscribe({ error: (e: { status: number }) => statuses.push(e.status) });
      backend.expectOne(`${api}/admin/messages/7/resend`).flush({ message: 'x' }, { status, statusText: 'err' });
    }
    expect(statuses).toEqual([404, 409, 503]);
  });
});
