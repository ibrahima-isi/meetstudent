import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MediaService } from './media.service';
import { API_URL } from './api-config';

const api = 'http://api.test/api/v1';

describe('MediaService', () => {
  let service: MediaService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: api },
      ],
    });
    service = TestBed.inject(MediaService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('lists media by status with paging params', () => {
    let result: unknown;
    service.list('VERIFIED', 2, 10).subscribe((r) => (result = r));
    const req = backend.expectOne((r) => r.url === `${api}/media`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('status')).toBe('VERIFIED');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    const page = { content: [], totalElements: 0, totalPages: 0, number: 2, size: 10 };
    req.flush(page);
    expect(result).toEqual(page);
  });

  it('verifies with status and reason', () => {
    service.verify(5, 'REJECTED', 'Illisible').subscribe();
    const req = backend.expectOne(`${api}/media/5/verification`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'REJECTED', reason: 'Illisible' });
    req.flush({});
  });

  it('omits the reason when approving', () => {
    service.verify(5, 'VERIFIED').subscribe();
    const req = backend.expectOne(`${api}/media/5/verification`);
    expect(req.request.body).toEqual({ status: 'VERIFIED' });
    req.flush({});
  });

  it('downloads the file as a blob through HttpClient', () => {
    let blob: Blob | undefined;
    service.download(7).subscribe((b) => (blob = b));
    const req = backend.expectOne(`${api}/media/7`);
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['x'], { type: 'application/pdf' }));
    expect(blob?.type).toBe('application/pdf');
  });
});
