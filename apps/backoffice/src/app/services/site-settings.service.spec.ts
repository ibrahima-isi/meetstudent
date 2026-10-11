import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SiteSettingsService } from './site-settings.service';
import { API_URL } from './api-config';
import { SiteSettings } from '@models/site-settings';

const api = 'http://api.test/api/v1';
const settings = { contactEmail: 'contact@meetstudent.com', aboutFr: 'Bonjour', aboutEn: 'Hello' };

describe('SiteSettingsService', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: api },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('reads the settings from /site-settings', () => {
    let result: SiteSettings | undefined;
    TestBed.inject(SiteSettingsService)
      .get()
      .subscribe((s) => (result = s));

    const req = backend.expectOne(`${api}/site-settings`);
    expect(req.request.method).toBe('GET');
    req.flush(settings);

    expect(result).toEqual(settings);
  });

  it('sends only the changed fields with PATCH /site-settings', () => {
    let result: SiteSettings | undefined;
    TestBed.inject(SiteSettingsService)
      .update({ aboutEn: 'Hi' })
      .subscribe((s) => (result = s));

    const req = backend.expectOne(`${api}/site-settings`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ aboutEn: 'Hi' });
    req.flush({ ...settings, aboutEn: 'Hi' });

    expect(result).toEqual({ ...settings, aboutEn: 'Hi' });
  });
});
