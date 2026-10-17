import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { ContactPageComponent } from './contact-page.component';

describe('ContactPageComponent', () => {
  let fixture: ComponentFixture<ContactPageComponent>;
  let backend: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const settle = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco(translocoOptions),
      ],
    });
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ContactPageComponent);
    await settle();
  });

  afterEach(() => backend.verify());

  it('has a title and shows the configured address as a mail link', async () => {
    expect(el().querySelector('h1')?.textContent).toContain('Contact');
    backend.expectOne('/api/v1/site-settings').flush({ contactEmail: 'hello@example.sn', aboutFr: 'x', aboutEn: 'y' });
    await settle();

    const link = el().querySelector('a[data-contact-email]') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('mailto:hello@example.sn');
    expect(link.textContent?.trim()).toBe('hello@example.sn');
  });

  it('shows the error state with a retry when the address cannot be read, never a made-up one', async () => {
    backend.expectOne('/api/v1/site-settings').flush(null, { status: 500, statusText: 'err' });
    await settle();
    expect(el().querySelector('[role=alert]')).toBeTruthy();
    expect(el().querySelector('a[data-contact-email]')).toBeNull();

    (el().querySelector('[role=alert] button') as HTMLButtonElement).click();
    backend.expectOne('/api/v1/site-settings').flush({ contactEmail: 'hello@example.sn', aboutFr: 'x', aboutEn: 'y' });
    await settle();
    expect(el().querySelector('a[data-contact-email]')).toBeTruthy();
  });

  describe('with a school to apply to', () => {
    const flush = () => backend.expectOne('/api/v1/site-settings').flush({ contactEmail: 'hello@example.sn', aboutFr: 'x', aboutEn: 'y' });

    it('pre-fills the subject of the mail with the school name', async () => {
      fixture.componentRef.setInput('school', 'Test School');
      flush();
      await settle();

      const href = (el().querySelector('a[data-contact-email]') as HTMLAnchorElement).getAttribute('href')!;
      expect(href.startsWith('mailto:hello@example.sn?subject=')).toBeTrue();
      expect(decodeURIComponent(href)).toContain('Test School');
    });

    it('makes up nothing when no school is given', async () => {
      flush();
      await settle();

      expect((el().querySelector('a[data-contact-email]') as HTMLAnchorElement).getAttribute('href')).toBe('mailto:hello@example.sn');
    });
  });
});
