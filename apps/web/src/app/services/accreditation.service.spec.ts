import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { AccreditationService } from './accreditation.service';

describe('AccreditationService', () => {
  let service: AccreditationService;
  let httpMock: HttpTestingController;
  const emptyPage = { content: [], last: true, totalPages: 0, totalElements: 0, size: 12, number: 0 };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideZonelessChangeDetection()],
    });
    service = TestBed.inject(AccreditationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lists a page of accreditations as the API sent them, inventing nothing', () => {
    service.listAccreditations(1, 12).subscribe(page => {
      expect(page.content[0].code).toBe('AACSB');
      expect(page.content[0].description).toBe('Business schools');
      expect(page.content[1].description).toBeUndefined();
      expect(Object.keys(page.content[1]).sort()).toEqual(['id', 'name']);
      expect(page.totalElements).toBe(2);
    });
    const req = httpMock.expectOne(r => r.url.endsWith('/accreditations'));
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('12');
    req.flush({
      ...emptyPage,
      totalElements: 2,
      content: [
        { id: 1, name: 'A', code: 'AACSB', description: 'Business schools' },
        { id: 2, name: 'B' },
      ],
    });
  });

  it('searches by trimmed name with the path segment encoded', () => {
    service.searchAccreditationsByName('  génie civil  ', 1, 12).subscribe();
    const req = httpMock.expectOne(r => r.url.includes('/accreditations/name/'));
    expect(req.request.url.endsWith('/accreditations/name/g%C3%A9nie%20civil')).toBeTrue();
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('12');
    req.flush(emptyPage);
  });

  // The server refuses or mangles these in a path segment, so the request would only ever fail.
  for (const term of ['génie / civil', 'a\\b', '100%', 'a;b', '.', '..']) {
    it(`answers an empty page, without calling the API, for the unroutable term ${JSON.stringify(term)}`, () => {
      let result: { content: unknown[]; last: boolean; totalElements: number } | undefined;
      service.searchAccreditationsByName(term, 0, 12).subscribe(page => (result = page));
      httpMock.expectNone(() => true);
      expect(result?.content).toEqual([]);
      expect(result?.last).toBeTrue();
      expect(result?.totalElements).toBe(0);
    });
  }

  it('lists all accreditations when the search term is blank', () => {
    service.searchAccreditationsByName('   ', 0, 12).subscribe();
    const req = httpMock.expectOne(r => r.url.endsWith('/accreditations'));
    expect(req.request.params.get('size')).toBe('12');
    req.flush(emptyPage);
  });
});
