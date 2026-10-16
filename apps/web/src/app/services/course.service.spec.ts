import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { CourseService } from './course.service';

describe('CourseService', () => {
  let service: CourseService;
  let httpMock: HttpTestingController;
  const emptyPage = { content: [], last: true, totalPages: 0, totalElements: 0, size: 12, number: 0 };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideZonelessChangeDetection()],
    });
    service = TestBed.inject(CourseService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lists a page of courses and keeps the program the API sent', () => {
    service.listCourses(1, 12).subscribe(page => {
      expect(page.content[0].programId).toBe(4);
      expect(page.content[0].programName).toBe('Data');
      expect(page.content[1].programName).toBeUndefined();
    });
    const req = httpMock.expectOne(r => r.url.endsWith('/courses'));
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('12');
    req.flush({
      ...emptyPage,
      content: [
        { id: 1, name: 'A', programId: 4, programName: 'Data' },
        { id: 2, name: 'B' },
      ],
    });
  });

  it('resolves the photo of each listed course', () => {
    service.listCourses().subscribe(page => {
      expect(page.content[0].photoImageUrl).toContain('/uploads/public/c.png');
    });
    httpMock
      .expectOne(r => r.url.endsWith('/courses'))
      .flush({ ...emptyPage, content: [{ id: 1, name: 'A', photo: { publicUrl: '/uploads/public/c.png' } }] });
  });

  it('searches by trimmed name with the path segment encoded', () => {
    service.searchCoursesByName('  génie civil  ', 1, 12).subscribe();
    const req = httpMock.expectOne(r => r.url.includes('/courses/name/'));
    expect(req.request.url.endsWith('/courses/name/g%C3%A9nie%20civil')).toBeTrue();
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('12');
    req.flush(emptyPage);
  });

  // The server refuses or mangles these in a path segment, so the request would only ever fail.
  for (const term of ['génie / civil', 'a\\b', '100%', 'a;b', '.', '..']) {
    it(`answers an empty page, without calling the API, for the unroutable term ${JSON.stringify(term)}`, () => {
      let result: { content: unknown[]; last: boolean; totalElements: number } | undefined;
      service.searchCoursesByName(term, 0, 12).subscribe(page => (result = page));
      httpMock.expectNone(() => true);
      expect(result?.content).toEqual([]);
      expect(result?.last).toBeTrue();
      expect(result?.totalElements).toBe(0);
    });
  }

  it('lists all courses when the search term is blank', () => {
    service.searchCoursesByName('   ', 0, 12).subscribe();
    const req = httpMock.expectOne(r => r.url.endsWith('/courses'));
    expect(req.request.params.get('size')).toBe('12');
    req.flush(emptyPage);
  });
});
