import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SchoolService } from './school.service';
import { API_URL } from './api-config';
import { SchoolInput } from '@models/school';

const api = 'http://api.test/api/v1';

describe('SchoolService', () => {
  let service: SchoolService;
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
    service = TestBed.inject(SchoolService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  const input: SchoolInput = {
    code: 'ESS',
    name: 'Ecole',
    address: { location: '1 rue', city: 'Lyon', country: 'France' },
    tags: [{ id: 2, name: 'PUBLIC' }],
  };

  it('lists schools with page, size and a name sort', () => {
    service.list(2, 10).subscribe();
    const req = backend.expectOne((r) => r.url === `${api}/schools`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('sort')).toBe('name,asc');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 2, size: 10 });
  });

  it('searches by name through the name endpoint, url-encoded and trimmed', () => {
    service.list(0, 10, '  Éc/ole  ').subscribe();
    const req = backend.expectOne((r) => r.url === `${api}/schools/name/${encodeURIComponent('Éc/ole')}`);
    expect(req.request.params.get('page')).toBe('0');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });
  });

  it('treats a blank search as no search', () => {
    service.list(0, 10, '   ').subscribe();
    const req = backend.expectOne((r) => r.url === `${api}/schools`);
    expect(req.request.url).not.toContain('/name/');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });
  });

  it('creates with POST', () => {
    service.create(input).subscribe();
    const req = backend.expectOne(`${api}/schools`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(input);
    req.flush({ id: 1, ...input });
  });

  it('updates with PUT on the school id', () => {
    service.update(7, { ...input, logoMediaId: 9 }).subscribe();
    const req = backend.expectOne(`${api}/schools/7`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.logoMediaId).toBe(9);
    req.flush({ id: 7, ...input });
  });

  it('deletes with DELETE on the school id', () => {
    service.delete(7).subscribe();
    const req = backend.expectOne(`${api}/schools/7`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ id: 7 });
  });
});
