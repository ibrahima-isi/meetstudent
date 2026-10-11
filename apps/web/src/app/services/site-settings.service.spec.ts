import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SiteSettings, SiteSettingsService } from './site-settings.service';

describe('SiteSettingsService', () => {
  it('reads the public site settings', () => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    });
    const backend = TestBed.inject(HttpTestingController);
    let result: SiteSettings | undefined;

    TestBed.inject(SiteSettingsService)
      .get()
      .subscribe((s) => (result = s));

    const req = backend.expectOne('/api/v1/site-settings');
    expect(req.request.method).toBe('GET');
    req.flush({ contactEmail: 'c@example.sn', aboutFr: 'Bonjour', aboutEn: 'Hello' });
    backend.verify();

    expect(result).toEqual({ contactEmail: 'c@example.sn', aboutFr: 'Bonjour', aboutEn: 'Hello' });
  });
});
