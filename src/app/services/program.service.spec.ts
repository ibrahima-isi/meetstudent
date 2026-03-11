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
      expect(page.content[0].level).toBe('Licence');
      expect(page.content[0].rating).toBe(0);
      expect(service.programs().length).toBe(1);
    });

    const req = httpMock.expectOne(request => 
      request.url.includes('/programs') && request.params.get('page') === '0'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });
});
