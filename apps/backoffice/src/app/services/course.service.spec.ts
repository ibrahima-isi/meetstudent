import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseService } from './course.service';
import { API_URL } from './api-config';
import { CourseInput } from '@models/course';

const api = 'http://api.test/api/v1';

describe('CourseService', () => {
  let service: CourseService;
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
    service = TestBed.inject(CourseService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  const input: CourseInput = { code: 'ALG', name: 'Algèbre', programId: 4 };

  it('creates with POST', () => {
    service.create(input).subscribe();
    const req = backend.expectOne(`${api}/courses`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(input);
    req.flush({ id: 1, ...input });
  });

  it('updates with PUT on the course id', () => {
    service.update(9, input).subscribe();
    const req = backend.expectOne(`${api}/courses/9`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(input);
    req.flush({ id: 9, ...input });
  });

  it('deletes with DELETE on the course id', () => {
    service.delete(9).subscribe();
    const req = backend.expectOne(`${api}/courses/9`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ id: 9, name: 'Algèbre' });
  });
});
