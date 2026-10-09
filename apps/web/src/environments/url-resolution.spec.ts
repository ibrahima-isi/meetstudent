import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MediaService } from '@services/media.service';
import { SchoolService } from '@services/school.service';
import { Media } from '@models/entities';
import { environment } from './environment';
import { applyServerEnvironment } from './server-environment';

/**
 * Single-origin deployment: the reverse proxy sends `/api/*` and
 * `/uploads/public/*` to the API, so the BROWSER bundle must use relative URLs.
 * The SSR process has no page origin to be relative to, so it gets absolute
 * container URLs from `API_URL` / `SERVER_URL` through `applyServerEnvironment`.
 */
describe('URL resolution', () => {
  const buildTime = { apiUrl: environment.apiUrl, serverUrl: environment.serverUrl };

  afterEach(() => {
    environment.apiUrl = buildTime.apiUrl;
    environment.serverUrl = buildTime.serverUrl;
  });

  function setup() {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    });
    return {
      media: TestBed.inject(MediaService),
      schools: TestBed.inject(SchoolService),
      http: TestBed.inject(HttpTestingController),
    };
  }

  const publicMedia: Media = {
    id: 1,
    category: 'SCHOOL_LOGO',
    visibility: 'PUBLIC',
    verificationStatus: null,
    rejectionReason: null,
    originalFilename: 'logo.png',
    contentType: 'image/png',
    sizeBytes: 1,
    publicUrl: '/uploads/public/logo.png',
  };

  describe('browser (build-time environment)', () => {
    it('never points at localhost', () => {
      expect(environment.apiUrl).not.toContain('localhost');
      expect(environment.serverUrl).not.toContain('localhost');
      expect(environment.mediaBaseUrl).not.toContain('localhost');
    });

    it('calls the API on a path relative to the page origin', () => {
      expect(environment.apiUrl).toBe('/api/v1');
    });

    it('resolves public media against the page origin', () => {
      expect(environment.mediaBaseUrl).toBe('');
      expect(setup().media.resolveUrl(publicMedia)).toBe('/uploads/public/logo.png');
    });

    it('adds the missing leading slash to a bare publicUrl', () => {
      const url = setup().media.resolveUrl({ ...publicMedia, publicUrl: 'uploads/public/logo.png' });

      expect(url).toBe('/uploads/public/logo.png');
    });
  });

  describe('server (API_URL / SERVER_URL applied)', () => {
    beforeEach(() => {
      applyServerEnvironment(environment, {
        API_URL: 'http://api:8080/api/v1',
        SERVER_URL: 'http://api:8080',
      });
    });

    it('requests the container API, not the request origin', () => {
      const { schools, http } = setup();

      schools.getSchool(1).subscribe();

      const req = http.expectOne('http://api:8080/api/v1/schools/1');
      req.flush({});
      http.verify();
    });

    // The URL is serialised into the server-rendered HTML and the transfer
    // state, then loaded by the visitor's browser: an in-network address such
    // as http://api:8080 would be a broken image there.
    it('still resolves public media for the browser, never to the container address', () => {
      expect(setup().media.resolveUrl(publicMedia)).toBe('/uploads/public/logo.png');
    });
  });
});
