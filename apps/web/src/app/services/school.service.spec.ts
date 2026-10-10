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
      // The service stays locale-free: no French default; the template supplies one.
      expect(page.content[0].type).toBeUndefined();
      expect(page.content[0].description).toBeUndefined();
      expect(page.content[0].rating).toBe(0);
      // The API sends no review count: the service must not invent a 0.
      expect(page.content[0].reviewCount).toBeUndefined();
      // No shared state: callers own their results.
      expect('schools' in service).toBeFalse();
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
      expect(school.type).toBeUndefined();
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/schools/1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockSchool);
  });

  // Opposite of the rule: a value the API does send is passed through untouched.
  it('keeps the type and description the API sends', () => {
    const mockSchool = {
      id: 2,
      name: 'UCAD',
      type: 'Université Publique',
      description: 'Fondée en 1957',
      address: { location: 'Loc', city: 'Dakar', country: 'Senegal' },
    } as School;

    service.getSchool(2).subscribe((school) => {
      expect(school.type).toBe('Université Publique');
      expect(school.description).toBe('Fondée en 1957');
    });

    httpMock.expectOne((request) => request.url.endsWith('/schools/2')).flush(mockSchool);
  });

  describe('server-side search', () => {
    const emptyPage = { content: [], last: true, totalElements: 0, number: 0 };

    it('passes the sort to the listing', () => {
      service.getSchools(1, 12, undefined, 'name,asc').subscribe();
      const req = httpMock.expectOne((r) => r.url.endsWith('/schools'));
      expect(req.request.params.get('sort')).toBe('name,asc');
      expect(req.request.params.get('page')).toBe('1');
      req.flush(emptyPage);
    });

    it('sends the city filter, paging and sort to /search', () => {
      service.searchSchools('Dakar', undefined, undefined, undefined, 2, 12, 'address.city,asc').subscribe();
      const req = httpMock.expectOne((r) => r.url.endsWith('/schools/search'));
      expect(req.request.params.get('city')).toBe('Dakar');
      expect(req.request.params.has('country')).toBeFalse();
      expect(req.request.params.get('page')).toBe('2');
      expect(req.request.params.get('size')).toBe('12');
      expect(req.request.params.get('sort')).toBe('address.city,asc');
      req.flush(emptyPage);
    });

    it('searches by name through /name/{name}, encoded, and maps the fields', () => {
      let rating: number | undefined;
      service.searchSchoolsByName('école & co', 0, 12, 'name,asc').subscribe((p) => (rating = p.content[0].rating));
      const req = httpMock.expectOne((r) => r.url.endsWith(`/schools/name/${encodeURIComponent('école & co')}`));
      expect(req.request.params.get('sort')).toBe('name,asc');
      req.flush({ ...emptyPage, content: [{ id: 1, name: 'X', averageRate: 4, address: { city: 'D' } }] });
      expect(rating).toBe(4);
    });

    it('encodes reserved characters that survive the path', () => {
      service.searchSchoolsByName('a&b=c#d', 0, 12).subscribe();
      const req = httpMock.expectOne((r) => r.url.endsWith('/schools/name/a%26b%3Dc%23d'));
      req.flush(emptyPage);
    });

    for (const term of ['a/b', 'a\\b', '100%', 'a;b']) {
      it(`answers an empty page without a request for "${term}"`, () => {
        let page: Page<School> | undefined;
        service.searchSchoolsByName(term, 0, 12).subscribe((p) => (page = p));
        httpMock.expectNone((r) => r.url.includes('/schools/name'));
        expect(page?.content).toEqual([]);
        expect(page?.last).toBeTrue();
        expect(page?.totalElements).toBe(0);
        expect(page?.empty).toBeTrue();
        expect(page?.number).toBe(0);
      });
    }

    it('trims before checking and sending', () => {
      service.searchSchoolsByName('  dakar ', 0, 12).subscribe();
      httpMock.expectOne((r) => r.url.endsWith('/schools/name/dakar')).flush(emptyPage);
    });

    it('sends a long non-Latin term as one request without throwing', () => {
      const term = 'é'.repeat(250) + '漢'.repeat(250);
      service.searchSchoolsByName(term, 0, 12).subscribe();
      const req = httpMock.expectOne((r) => r.url.endsWith('/schools/name/' + encodeURIComponent(term)));
      expect(req.request.method).toBe('GET');
      req.flush(emptyPage);
    });
  });
});
