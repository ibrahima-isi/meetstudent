import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { SchoolService } from './school.service';
import { environment } from '../../environments/environment';
import { School, Page } from '@models/entities';
import { provideZonelessChangeDetection } from '@angular/core';

describe('SchoolService', () => {
  let service: SchoolService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        SchoolService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideZonelessChangeDetection()
      ],
    });
    service = TestBed.inject(SchoolService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch schools and map fields', () => {
    const mockPage: Page<School> = {
      content: [
        { 
          id: 1, 
          name: 'Test School', 
          address: { location: 'Loc', city: 'City', country: 'Country' } 
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

    service.getSchools().subscribe(page => {
      expect(page.content.length).toBe(1);
      expect(page.content[0].type).toBe('Établissement');
      expect(page.content[0].rating).toBe(0);
      expect(service.schools().length).toBe(1);
    });

    const req = httpMock.expectOne(request => 
      request.url.includes('/schools') && request.params.get('page') === '0'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('should fetch a single school and map fields', () => {
    const mockSchool: School = {
      id: 1,
      name: 'Test School',
      address: { location: 'Loc', city: 'City', country: 'Country' }
    } as any;

    service.getSchool(1).subscribe(school => {
      expect(school.id).toBe(1);
      expect(school.type).toBe('Établissement');
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/schools/1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockSchool);
  });
});
