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

  it('can send an empty string to erase a social link', () => {
    TestBed.inject(SiteSettingsService)
      .update({ whatsappUrl: '', tiktokUrl: 'https://tiktok.com/@x' })
      .subscribe();

    const req = backend.expectOne(`${api}/site-settings`);
    expect(req.request.body).toEqual({ whatsappUrl: '', tiktokUrl: 'https://tiktok.com/@x' });
    req.flush({ ...settings, whatsappUrl: null, tiktokUrl: 'https://tiktok.com/@x' });
  });

  it('exposes the seven optional social links on the model', () => {
    const withLinks: SiteSettings = {
      ...settings,
      whatsappUrl: null,
      telegramUrl: 'https://t.me/x',
      discordUrl: null,
      facebookUrl: null,
      twitterUrl: null,
      instagramUrl: null,
      tiktokUrl: null,
    };
    expect(withLinks.telegramUrl).toBe('https://t.me/x');
  });
});
