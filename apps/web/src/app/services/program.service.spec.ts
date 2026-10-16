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
    service.searchProgramsByName('  génie / civil  ', 1, 12).subscribe();
    const req = httpMock.expectOne(r => r.url.includes('/programs/name/'));
    expect(req.request.url.endsWith('/programs/name/g%C3%A9nie%20%2F%20civil')).toBeTrue();
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('12');
    req.flush(emptyPage);
  });

  it('lists all programs when the search term is blank', () => {
    service.searchProgramsByName('   ', 0, 12).subscribe();
    const req = httpMock.expectOne(r => r.url.endsWith('/programs'));
    expect(req.request.params.get('size')).toBe('12');
    req.flush(emptyPage);
  });
});
