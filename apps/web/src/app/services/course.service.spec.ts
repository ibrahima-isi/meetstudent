import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { CourseService } from './course.service';
import { Course } from '@models/entities';
import { environment } from '../../environments/environment';

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
  describe('getCourse (read contract used by the comparison)', () => {
    const url = `${environment.apiUrl}/courses/5`;

    it('reads GET /courses/{id} and keeps the average rate', () => {
      let result: Course | undefined;
      service.getCourse(5).subscribe(c => (result = c));
      const req = httpMock.expectOne(url);
      expect(req.request.method).toBe('GET');
      req.flush({ id: 5, name: 'C', averageRate: 4.2 });
      expect(result?.averageRate).toBe(4.2);
    });

    for (const body of [{ averageRate: null }, {}]) {
      it(`never invents a 0 when averageRate is ${JSON.stringify(body)}`, () => {
        let result: Course | undefined;
        service.getCourse(5).subscribe(c => (result = c));
        httpMock.expectOne(url).flush({ id: 5, name: 'C', ...body });
        expect(result?.averageRate ?? null).toBeNull();
      });
    }

    it('keeps a null programName as null', () => {
      let result: Course | undefined;
      service.getCourse(5).subscribe(c => (result = c));
      httpMock.expectOne(url).flush({ id: 5, name: 'C', programName: null });
      expect(result?.programName).toBeNull();
    });

    it('resolves the photo into photoImageUrl', () => {
      let result: Course | undefined;
      service.getCourse(5).subscribe(c => (result = c));
      httpMock.expectOne(url).flush({ id: 5, name: 'C', photo: { id: 1, publicUrl: '/uploads/public/course-photo/a.png' } });
      expect(result?.photoImageUrl).toBe(`${environment.mediaBaseUrl}/uploads/public/course-photo/a.png`);
    });

    it('surfaces a 404 as an error to the subscriber', () => {
      let status: number | undefined;
      service.getCourse(5).subscribe({ error: e => (status = e.status) });
      httpMock.expectOne(url).flush(null, { status: 404, statusText: 'Not Found' });
      expect(status).toBe(404);
    });
  });
});
