import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TagService } from './tag.service';
import { API_URL } from './api-config';

const api = 'http://api.test/api/v1';

describe('TagService', () => {
  it('lists all tags from /tags (a plain array, not a page)', () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: api },
      ],
    });
    const backend = TestBed.inject(HttpTestingController);
    let result: unknown;
    TestBed.inject(TagService)
      .list()
      .subscribe((t) => (result = t));

    const req = backend.expectOne(`${api}/tags`);
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 1, name: 'PUBLIC' }]);

    expect(result).toEqual([{ id: 1, name: 'PUBLIC' }]);
    backend.verify();
  });
});
