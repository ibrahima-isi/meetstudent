import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AccreditationService } from './accreditation.service';
import { API_URL } from './api-config';

const api = 'http://api.test/api/v1';

describe('AccreditationService', () => {
  let backend: HttpTestingController;
  let service: AccreditationService;

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
    service = TestBed.inject(AccreditationService);
  });

  afterEach(() => backend.verify());

  it('lists one page sorted by name', () => {
    let result: unknown;
    service.list(2, 10).subscribe((p) => (result = p));

    const req = backend.expectOne((r) => r.url === `${api}/accreditations`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('sort')).toBe('name,asc');
    const body = { content: [], number: 2, totalPages: 3, totalElements: 25, size: 10 };
    req.flush(body);
    expect(result).toEqual(body);
  });

  it('creates with POST /accreditations', () => {
    service.create({ code: 'AACSB', name: 'AACSB', description: 'US' }).subscribe();
    const req = backend.expectOne(`${api}/accreditations`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: 'AACSB', name: 'AACSB', description: 'US' });
    req.flush({ id: 1, code: 'AACSB', name: 'AACSB', description: 'US' });
  });

  it('updates with a full PUT /accreditations/{id}', () => {
    service.update(4, { name: 'EQUIS' }).subscribe();
    const req = backend.expectOne(`${api}/accreditations/4`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ name: 'EQUIS' });
    req.flush({ id: 4, name: 'EQUIS' });
  });

  it('deletes with DELETE /accreditations/{id}', () => {
    let done = false;
    service.delete(4).subscribe(() => (done = true));
    const req = backend.expectOne(`${api}/accreditations/4`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ id: 4, name: 'EQUIS' });
    expect(done).toBeTrue();
  });
});
