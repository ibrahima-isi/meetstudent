import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ForumAdminService } from './forum-admin.service';
import { API_URL } from './api-config';

const api = 'http://api.test/api/v1';

describe('ForumAdminService', () => {
  let service: ForumAdminService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: api }],
    });
    service = TestBed.inject(ForumAdminService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('lists the reports of a status with the page and size', () => {
    service.reports('OPEN', 2, 10).subscribe();
    const req = backend.expectOne((r) => r.url === `${api}/admin/forum/reports`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('status')).toBe('OPEN');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    req.flush({ content: [] });
  });

  it('closes a report as handled or dismissed with an empty POST', () => {
    service.handle(4).subscribe();
    const handle = backend.expectOne(`${api}/admin/forum/reports/4/handle`);
    expect(handle.request.method).toBe('POST');
    expect(handle.request.body).toBeNull();
    handle.flush({});
    service.dismiss(5).subscribe();
    backend.expectOne(`${api}/admin/forum/reports/5/dismiss`).flush({});
  });

  for (const action of ['hide', 'unhide', 'lock', 'unlock'] as const) {
    it(`${action}s a thread with a POST`, () => {
      service[`${action}Thread`](7).subscribe();
      const req = backend.expectOne(`${api}/admin/forum/threads/7/${action}`);
      expect(req.request.method).toBe('POST');
      req.flush({});
    });
  }

  for (const action of ['hide', 'unhide'] as const) {
    it(`${action}s a reply with a POST`, () => {
      service[`${action}Reply`](8).subscribe();
      const req = backend.expectOne(`${api}/admin/forum/replies/8/${action}`);
      expect(req.request.method).toBe('POST');
      req.flush({});
    });
  }

  it('deletes a thread and a reply with DELETE', () => {
    service.deleteThread(7).subscribe();
    const t = backend.expectOne(`${api}/admin/forum/threads/7`);
    expect(t.request.method).toBe('DELETE');
    t.flush(null, { status: 204, statusText: 'No Content' });
    service.deleteReply(8).subscribe();
    const r = backend.expectOne(`${api}/admin/forum/replies/8`);
    expect(r.request.method).toBe('DELETE');
    r.flush(null, { status: 204, statusText: 'No Content' });
  });
});
