import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAX_IMAGE_BYTES, MediaService, validateImage } from './media.service';
import { API_URL, SERVER_URL } from './api-config';

const api = 'http://api.test/api/v1';
const file = (name: string, type: string, size = 10) =>
  new File([new Uint8Array(size)], name, { type });

describe('validateImage', () => {
  it('accepts jpg, jpeg, png and webp with a matching MIME type', () => {
    expect(validateImage(file('a.jpg', 'image/jpeg'))).toBe('');
    expect(validateImage(file('a.JPEG', 'image/jpeg'))).toBe('');
    expect(validateImage(file('a.png', 'image/png'))).toBe('');
    expect(validateImage(file('a.webp', 'image/webp'))).toBe('');
  });

  it('rejects an empty file', () => {
    expect(validateImage(file('a.png', 'image/png', 0))).toContain('vide');
  });

  it('rejects a file over 10 MB but accepts exactly 10 MB', () => {
    expect(MAX_IMAGE_BYTES).toBe(10 * 1024 * 1024);
    expect(validateImage(file('a.png', 'image/png', MAX_IMAGE_BYTES))).toBe('');
    expect(validateImage(file('a.png', 'image/png', MAX_IMAGE_BYTES + 1))).toContain('10 Mo');
  });

  it('rejects other extensions, PDFs and a MIME type that does not match', () => {
    expect(validateImage(file('a.gif', 'image/gif'))).toContain('JPEG, PNG ou WebP');
    expect(validateImage(file('a.pdf', 'application/pdf'))).toContain('JPEG, PNG ou WebP');
    expect(validateImage(file('a.png', 'image/jpeg'))).toContain('JPEG, PNG ou WebP');
    expect(validateImage(file('noext', 'image/png'))).toContain('JPEG, PNG ou WebP');
  });
});

describe('MediaService', () => {
  let service: MediaService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: api },
        { provide: SERVER_URL, useValue: 'http://srv.test' },
      ],
    });
    service = TestBed.inject(MediaService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('lists media by status with paging params', () => {
    let result: unknown;
    service.list('VERIFIED', 2, 10).subscribe((r) => (result = r));
    const req = backend.expectOne((r) => r.url === `${api}/media`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('status')).toBe('VERIFIED');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    const page = { content: [], totalElements: 0, totalPages: 0, number: 2, size: 10 };
    req.flush(page);
    expect(result).toEqual(page);
  });

  it('verifies with status and reason', () => {
    service.verify(5, 'REJECTED', 'Illisible').subscribe();
    const req = backend.expectOne(`${api}/media/5/verification`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'REJECTED', reason: 'Illisible' });
    req.flush({});
  });

  it('omits the reason when approving', () => {
    service.verify(5, 'VERIFIED').subscribe();
    const req = backend.expectOne(`${api}/media/5/verification`);
    expect(req.request.body).toEqual({ status: 'VERIFIED' });
    req.flush({});
  });

  it('downloads the file as a blob through HttpClient', () => {
    let blob: Blob | undefined;
    service.download(7).subscribe((b) => (blob = b));
    const req = backend.expectOne(`${api}/media/7`);
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['x'], { type: 'application/pdf' }));
    expect(blob?.type).toBe('application/pdf');
  });

  it('uploads multipart with category and file', () => {
    const f = file('logo.png', 'image/png');
    let id = 0;
    service.upload(f, 'SCHOOL_LOGO').subscribe((m) => (id = m.id));

    const req = backend.expectOne(`${api}/media`);
    expect(req.request.method).toBe('POST');
    const body = req.request.body as FormData;
    expect(body.get('category')).toBe('SCHOOL_LOGO');
    expect((body.get('file') as File).name).toBe('logo.png');
    req.flush({ id: 5, publicUrl: '/uploads/public/x.png' });
    expect(id).toBe(5);
  });

  it('resolves publicUrl against the server root, not the API root', () => {
    expect(service.publicUrl({ publicUrl: '/uploads/public/x.png' })).toBe(
      'http://srv.test/uploads/public/x.png',
    );
    expect(service.publicUrl({ publicUrl: 'uploads/public/x.png' })).toBe(
      'http://srv.test/uploads/public/x.png',
    );
  });

  it('keeps an absolute publicUrl and returns null without one', () => {
    expect(service.publicUrl({ publicUrl: 'https://cdn.test/x.png' })).toBe('https://cdn.test/x.png');
    expect(service.publicUrl({ publicUrl: null })).toBeNull();
    expect(service.publicUrl(null)).toBeNull();
  });
});
