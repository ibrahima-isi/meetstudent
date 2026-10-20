import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ExpertService } from './expert.service';
import { API_URL } from './api-config';

const api = 'http://api.test/api/v1';

describe('ExpertService', () => {
  let backend: HttpTestingController;
  let service: ExpertService;

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
    service = TestBed.inject(ExpertService);
  });

  afterEach(() => backend.verify());

  it('lists one page filtered by status, with no sort (the order is fixed server-side)', () => {
    let result: unknown;
    service.list('PENDING', 1, 10).subscribe((p) => (result = p));
    const req = backend.expectOne((r) => r.url === `${api}/admin/experts`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('status')).toBe('PENDING');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.has('sort')).toBeFalse();
    const body = { content: [], number: 1, totalPages: 2, totalElements: 11, size: 10 };
    req.flush(body);
    expect(result).toEqual(body);
  });

  it('approves with POST /admin/experts/{id}/approve and no body', () => {
    service.approve(7).subscribe();
    const req = backend.expectOne(`${api}/admin/experts/7/approve`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeNull();
    req.flush({});
  });

  it('rejects with the reason in a minimal body, or with no body when there is none', () => {
    service.reject(7, 'Diplôme illisible').subscribe();
    const withReason = backend.expectOne(`${api}/admin/experts/7/reject`);
    expect(withReason.request.method).toBe('POST');
    expect(withReason.request.body).toEqual({ reason: 'Diplôme illisible' });
    withReason.flush({});

    service.reject(8, '   ').subscribe();
    const without = backend.expectOne(`${api}/admin/experts/8/reject`);
    expect(without.request.body).toBeNull();
    without.flush({});
  });
});
