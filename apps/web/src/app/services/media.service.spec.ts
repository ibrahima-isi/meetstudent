import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { MediaService } from './media.service';
import { environment } from '../../environments/environment';
import { Media } from '@models/entities';

function media(partial: Partial<Media>): Media {
  return {
    id: 1,
    category: 'SCHOOL_LOGO',
    visibility: 'PUBLIC',
    verificationStatus: null,
    rejectionReason: null,
    originalFilename: 'logo.png',
    contentType: 'image/png',
    sizeBytes: 10,
    publicUrl: '/uploads/public/logo.png',
    ...partial
  };
}

describe('MediaService', () => {
  let service: MediaService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        MediaService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideZonelessChangeDetection()
      ],
    });
    service = TestBed.inject(MediaService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  describe('resolveUrl', () => {
    // publicUrl is relative to the SERVER ROOT, not to /api/v1 — prefixing
    // apiUrl would produce a broken .../api/v1/uploads/... path.
    it('prefixes a relative publicUrl with serverUrl, not apiUrl', () => {
      const url = service.resolveUrl(media({ publicUrl: '/uploads/public/logo.png' }));

      expect(url).toBe(`${environment.serverUrl}/uploads/public/logo.png`);
      expect(url).not.toContain('/api/v1');
    });

    it('passes absolute URLs through unchanged', () => {
      const external = 'https://images.example.com/photo.jpg';
      expect(service.resolveUrl(media({ publicUrl: external }))).toBe(external);
    });

    // Opposite: private media has no publicUrl and must not yield a URL —
    // returning one would produce an <img src> that 403s.
    it('returns null for private media (no publicUrl)', () => {
      const priv = media({ visibility: 'PRIVATE', category: 'DIPLOMA', publicUrl: null });
      expect(service.resolveUrl(priv)).toBeNull();
    });

    it('returns null for missing media', () => {
      expect(service.resolveUrl(null)).toBeNull();
      expect(service.resolveUrl(undefined)).toBeNull();
    });
  });

  describe('upload', () => {
    const file = new File(['x'], 'logo.png', { type: 'image/png' });

    it('posts multipart with the category and sends Idempotency-Key when given', () => {
      service.upload(file, 'SCHOOL_LOGO', 'key-1').subscribe();

      const req = httpMock.expectOne(r => r.url === `${environment.apiUrl}/media`);
      expect(req.request.method).toBe('POST');
      expect(req.request.params.get('category')).toBe('SCHOOL_LOGO');
      expect(req.request.headers.get('Idempotency-Key')).toBe('key-1');
      expect(req.request.body instanceof FormData).toBeTrue();
      req.flush(media({}));
    });

    // Opposite: no key supplied -> header must be absent, not empty/undefined.
    it('omits Idempotency-Key when none is given', () => {
      service.upload(file, 'SCHOOL_LOGO').subscribe();

      const req = httpMock.expectOne(r => r.url === `${environment.apiUrl}/media`);
      expect(req.request.headers.has('Idempotency-Key')).toBeFalse();
      req.flush(media({}));
    });
  });

  describe('blobUrl', () => {
    it('requests the media content as a blob', () => {
      service.blobUrl(42).subscribe();

      const req = httpMock.expectOne(`${environment.apiUrl}/media/42`);
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');
      req.flush(new Blob(['x']));
    });
  });
});
