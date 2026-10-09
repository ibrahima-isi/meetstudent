import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TagService } from './tag.service';
import { API_URL } from './api-config';

const api = 'http://api.test/api/v1';

describe('TagService', () => {
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
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('lists all tags from /tags (a plain array, not a page)', () => {
    let result: unknown;
    TestBed.inject(TagService)
      .list()
      .subscribe((t) => (result = t));

    const req = backend.expectOne(`${api}/tags`);
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 1, name: 'PUBLIC' }]);

    expect(result).toEqual([{ id: 1, name: 'PUBLIC' }]);
  });

  it('creates a tag with POST /tags and only its name', () => {
    let result: unknown;
    TestBed.inject(TagService)
      .create('MASTER')
      .subscribe((t) => (result = t));

    const req = backend.expectOne(`${api}/tags`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'MASTER' });
    req.flush({ id: 5, name: 'MASTER' });

    expect(result).toEqual({ id: 5, name: 'MASTER' });
  });

  it('deletes a tag with DELETE /tags/{id}', () => {
    let done = false;
    TestBed.inject(TagService)
      .delete(5)
      .subscribe(() => (done = true));

    const req = backend.expectOne(`${api}/tags/5`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(done).toBeTrue();
  });
});
