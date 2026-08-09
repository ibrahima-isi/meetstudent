import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { RatingService } from './rating.service';
import { environment } from '../../environments/environment';
import { provideZonelessChangeDetection } from '@angular/core';

describe('RatingService', () => {
  let service: RatingService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        RatingService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideZonelessChangeDetection()
      ],
    });
    service = TestBed.inject(RatingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should rate school', () => {
    service.rateSchool(1, 1, 5, 'Comment').subscribe(rate => {
      expect(service.lastSchoolRate()).toBeTruthy();
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/school-rates`);
    expect(req.request.method).toBe('POST');
    req.flush({ id: 1, note: 5, comment: 'Comment' });
  });

  it('should rate program', () => {
    service.rateProgram(1, 1, 4).subscribe(rate => {
      expect(service.lastProgramRate()).toBeTruthy();
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/program-rates`);
    expect(req.request.method).toBe('POST');
    req.flush({ id: 1, note: 4 });
  });
});
