import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProgramService } from './program.service';
import { API_URL } from './api-config';
import { ProgramInput } from '@models/program';

const api = 'http://api.test/api/v1';

describe('ProgramService', () => {
  let service: ProgramService;
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
    service = TestBed.inject(ProgramService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  const input: ProgramInput = { code: 'MST', name: 'Master', duration: 2, schoolId: 3 };

  it('lists programs with page, size and a name sort', () => {
    service.list(2, 10).subscribe();
    const req = backend.expectOne((r) => r.url === `${api}/programs`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('sort')).toBe('name,asc');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 2, size: 10 });
  });

  it('searches by name through the name endpoint, url-encoded and trimmed', () => {
    service.list(0, 10, '  Méd / Droit ').subscribe();
    const req = backend.expectOne((r) => r.url === `${api}/programs/name/${encodeURIComponent('Méd / Droit')}`);
    expect(req.request.params.get('sort')).toBe('name,asc');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });
  });

  it('treats a blank search as no search', () => {
    service.list(0, 10, '   ').subscribe();
    backend.expectOne((r) => r.url === `${api}/programs`).flush({ content: [] });
  });

  it('creates with POST', () => {
    service.create(input).subscribe();
    const req = backend.expectOne(`${api}/programs`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(input);
    req.flush({ id: 1, ...input });
  });

  it('updates with PUT on the program id', () => {
    service.update(5, input).subscribe();
    const req = backend.expectOne(`${api}/programs/5`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(input);
    req.flush({ id: 5, ...input });
  });

  it('deletes with DELETE on the program id', () => {
    service.delete(5).subscribe();
    const req = backend.expectOne(`${api}/programs/5`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ id: 5, name: 'Master' });
  });

  it('reads the accreditations linked to a program', () => {
    let result: unknown;
    service.accreditationsOf(5).subscribe((r) => (result = r));
    const req = backend.expectOne(`${api}/programs/5/accreditations`);
    expect(req.request.method).toBe('GET');
    req.flush([{ programId: 5, accreditationId: 2, startsAt: 2020, endsAt: 2025 }]);
    expect(result).toEqual([{ programId: 5, accreditationId: 2, startsAt: 2020, endsAt: 2025 }]);
  });

  it('links an accreditation with its years as query parameters and an empty body', () => {
    service.link(5, 2, 2020, 2025).subscribe();
    const req = backend.expectOne((r) => r.url === `${api}/programs/5/accreditations/2`);
    expect(req.request.method).toBe('POST');
    expect(req.request.params.get('startsAt')).toBe('2020');
    expect(req.request.params.get('endsAt')).toBe('2025');
    req.flush({ programId: 5, accreditationId: 2, startsAt: 2020, endsAt: 2025 });
  });

  it('unlinks an accreditation with DELETE', () => {
    service.unlink(5, 2).subscribe();
    const req = backend.expectOne(`${api}/programs/5/accreditations/2`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });
});
