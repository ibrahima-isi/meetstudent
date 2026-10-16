import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ProgramService } from './program.service';
import { environment } from '../../environments/environment';
import { Program, Page } from '@models/entities';
import { provideZonelessChangeDetection } from '@angular/core';

describe('ProgramService', () => {
  let service: ProgramService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ProgramService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideZonelessChangeDetection()
      ],
    });
    service = TestBed.inject(ProgramService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch programs and map fields', () => {
    const mockPage: Page<Program> = {
      content: [
        { 
          id: 1, 
          name: 'Test Program', 
          duration: 3
        } as any
      ],
      pageable: {} as any,
      last: true,
      totalPages: 1,
      totalElements: 1,
      size: 10,
      number: 0,
      sort: {} as any,
      first: true,
      numberOfElements: 1,
      empty: false
    };

    service.getPrograms().subscribe(page => {
      expect(page.content.length).toBe(1);
      // Nothing is invented: what the API did not send stays absent.
      expect(page.content[0].level).toBeUndefined();
      expect(page.content[0].description).toBeUndefined();
      expect(page.content[0].rating).toBeUndefined();
      expect(page.content[0].reviewCount).toBeUndefined();
      expect(service.programs().length).toBe(1);
    });

    const req = httpMock.expectOne(request => 
      request.url.includes('/programs') && request.params.get('page') === '0'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  const emptyPage = { content: [], last: true, totalPages: 0, totalElements: 0, size: 10, number: 0 };

  it('keeps the average rate and the school the API sent', () => {
    service.getPrograms().subscribe(page => {
      expect(page.content[0].rating).toBe(4.5);
      expect(page.content[0].schoolName).toBe('ISI');
      expect(page.content[0].schoolCode).toBeUndefined();
    });
    httpMock.expectOne(r => r.url.endsWith('/programs'))
      .flush({ ...emptyPage, content: [{ id: 1, name: 'P', averageRate: 4.5, schoolId: 2, schoolName: 'ISI' }] });
  });

  it('searches by trimmed name with the path segment encoded', () => {
    service.searchProgramsByName('  génie civil  ', 1, 12).subscribe();
    const req = httpMock.expectOne(r => r.url.includes('/programs/name/'));
    expect(req.request.url.endsWith('/programs/name/g%C3%A9nie%20civil')).toBeTrue();
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('12');
    req.flush(emptyPage);
  });

  // The server refuses or mangles these in a path segment, so the request would only ever fail.
  for (const term of ['génie / civil', 'a\\b', '100%', 'a;b', '.', '..']) {
    it(`answers an empty page, without calling the API, for the unroutable term ${JSON.stringify(term)}`, () => {
      let result: { content: unknown[]; last: boolean; totalElements: number } | undefined;
      service.searchProgramsByName(term, 0, 12).subscribe(page => (result = page));
      httpMock.expectNone(() => true);
      expect(result?.content).toEqual([]);
      expect(result?.last).toBeTrue();
      expect(result?.totalElements).toBe(0);
    });
  }

  it('lists all programs when the search term is blank', () => {
    service.searchProgramsByName('   ', 0, 12).subscribe();
    const req = httpMock.expectOne(r => r.url.endsWith('/programs'));
    expect(req.request.params.get('size')).toBe('12');
    req.flush(emptyPage);
  });

  describe('getProgram (read contract used by the comparison)', () => {
    it('reads GET /programs/{id} and maps averageRate to rating', () => {
      let result: Program | undefined;
      service.getProgram(7).subscribe(p => (result = p));
      const req = httpMock.expectOne(`${environment.apiUrl}/programs/7`);
      expect(req.request.method).toBe('GET');
      req.flush({ id: 7, name: 'P', averageRate: 4.2 });
      expect(result?.rating).toBe(4.2);
    });

    for (const body of [{ averageRate: null }, {}]) {
      it(`leaves rating undefined, never 0, when averageRate is ${JSON.stringify(body)}`, () => {
        let result: Program | undefined;
        service.getProgram(7).subscribe(p => (result = p));
        httpMock.expectOne(`${environment.apiUrl}/programs/7`).flush({ id: 7, name: 'P', ...body });
        expect(result?.rating).toBeUndefined();
      });
    }

    it('passes the school and the duration through', () => {
      let result: Program | undefined;
      service.getProgram(7).subscribe(p => (result = p));
      httpMock.expectOne(`${environment.apiUrl}/programs/7`)
        .flush({ id: 7, name: 'P', schoolId: 2, schoolName: 'ISI', schoolCode: 'ISI', duration: 3 });
      expect(result?.schoolId).toBe(2);
      expect(result?.schoolName).toBe('ISI');
      expect(result?.schoolCode).toBe('ISI');
      expect(result?.duration).toBe(3);
    });

    it('does not invent a duration when the API sends null or none', () => {
      const results: Program[] = [];
      service.getProgram(7).subscribe(p => results.push(p));
      httpMock.expectOne(`${environment.apiUrl}/programs/7`).flush({ id: 7, name: 'P', duration: null });
      service.getProgram(8).subscribe(p => results.push(p));
      httpMock.expectOne(`${environment.apiUrl}/programs/8`).flush({ id: 8, name: 'Q' });
      expect(results[0].duration ?? undefined).toBeUndefined();
      expect(results[1].duration).toBeUndefined();
    });

    it('resolves the photo into photoImageUrl through MediaService.resolveUrl', () => {
      let result: Program | undefined;
      service.getProgram(7).subscribe(p => (result = p));
      httpMock.expectOne(`${environment.apiUrl}/programs/7`)
        .flush({ id: 7, name: 'P', photo: { id: 1, publicUrl: '/uploads/public/program-photo/a.png' } });
      expect(result?.photoImageUrl).toBe(`${environment.mediaBaseUrl}/uploads/public/program-photo/a.png`);
    });

    it('surfaces a 404 with an empty body as an error to the subscriber', () => {
      let status: number | undefined;
      let emitted = false;
      service.getProgram(999).subscribe({ next: () => (emitted = true), error: e => (status = e.status) });
      httpMock.expectOne(`${environment.apiUrl}/programs/999`)
        .flush(null, { status: 404, statusText: 'Not Found' });
      expect(emitted).toBeFalse();
      expect(status).toBe(404);
    });

    it('returns accreditations as [] because the API sends none', () => {
      let result: Program | undefined;
      service.getProgram(7).subscribe(p => (result = p));
      httpMock.expectOne(`${environment.apiUrl}/programs/7`).flush({ id: 7, name: 'P' });
      expect(result?.accreditations).toEqual([]);
    });
  });
});
