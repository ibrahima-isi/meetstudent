import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
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

    const hrefFor = async (value: unknown) => {
      fixture.componentRef.setInput('school', value);
      flush();
      await settle();
      return (el().querySelector('a[data-contact-email]') as HTMLAnchorElement).getAttribute('href')!;
    };
    const subjectOf = (href: string) => decodeURIComponent(href.split('?subject=')[1]);

    it('ignores a repeated ?school= (an array) instead of throwing', async () => {
      expect(await hrefFor(['A', 'B'])).toBe('mailto:hello@example.sn');
    });

    it('encodes & and strips CR/LF so no header can be injected', async () => {
      const href = await hrefFor('A&B\r\nBcc: x@y.z');
      expect(href).not.toMatch(/[\r\n]/);
      expect(href).not.toContain('%0D');
      expect(href).not.toContain('%0A');
      expect(href.split('?subject=')[1]).toContain('%26');
      expect(href).not.toContain('&');
    });

    it('trims the name', async () => {
      expect(subjectOf(await hrefFor('  Test School  '))).toMatch(/Test School$/);
    });

    it('ignores a blank name', async () => {
      expect(await hrefFor('   ')).toBe('mailto:hello@example.sn');
    });

    it('cuts a very long name', async () => {
      const subject = subjectOf(await hrefFor('x'.repeat(500)));
      expect(subject.length).toBeLessThan(200);
      expect(subject).not.toContain('x'.repeat(101));
    });

    it('makes up nothing when no school is given', async () => {
      flush();
      await settle();

      expect((el().querySelector('a[data-contact-email]') as HTMLAnchorElement).getAttribute('href')).toBe('mailto:hello@example.sn');
    });
  });

  describe('the message form', () => {
    const settings = { contactEmail: 'hello@example.sn', aboutFr: 'x', aboutEn: 'y' };
    const flushSettings = (body: object = settings) => backend.expectOne('/api/v1/site-settings').flush(body);
    const field = <T extends HTMLElement>(name: string) => el().querySelector(`[name=${name}]`) as T;
    const type = async (name: string, value: string) => {
      const control = field<HTMLInputElement | HTMLTextAreaElement>(name);
      control.value = value;
      control.dispatchEvent(new Event('input'));
      await settle();
    };
    const blur = async (name: string) => {
      field(name).dispatchEvent(new Event('blur'));
      await settle();
    };
    const form = () => el().querySelector('form') as HTMLFormElement;

    it('keeps the field icons clear of the typed text (lucide copies the classes onto the svg, so the offset must not be applied twice)', async () => {
      flushSettings();
      await settle();
      for (const name of ['email', 'subject']) {
        const input = field<HTMLInputElement>(name);
        const svg = input.parentElement!.querySelector('svg')!.getBoundingClientRect();
        const box = input.getBoundingClientRect();
        const textStart = box.left + parseFloat(getComputedStyle(input).paddingLeft);
        expect(svg.left - box.left).withContext(`${name}: icon starts near the left edge`).toBeLessThanOrEqual(20);
        expect(textStart - svg.right).withContext(`${name}: gap between icon and text`).toBeGreaterThanOrEqual(8);
      }
    });

    const submit = async () => {
      form().dispatchEvent(new Event('submit'));
      await settle();
    };
    const button = () => el().querySelector('form button[type=submit]') as HTMLButtonElement;
    const fillValid = async () => {
      await type('email', 'ada@example.sn');
      await type('subject', 'Hello');
      await type('message', 'A question about schools.');
    };
    const errorOf = (name: string) => {
      const control = field(name);
      const id = control.getAttribute('aria-describedby')?.split(' ').find((part) => part.endsWith('-error'));
      return id ? el().querySelector(`#${id}`) : null;
    };
    const post = () => backend.expectOne('/api/v1/contact');

    it('has labelled fields with the right types, and a message limited to 5000 characters', async () => {
      flushSettings();
      await settle();

      for (const name of ['email', 'subject', 'message']) {
        const control = field(name);
        expect(control).toBeTruthy();
        const label = el().querySelector(`label[for="${control.id}"]`);
        expect(label?.textContent?.trim()).toBeTruthy();
      }
      expect(field('email').getAttribute('type')).toBe('email');
      expect(field('email').getAttribute('autocomplete')).toBe('email');
      expect(field('message').tagName).toBe('TEXTAREA');
      expect(field('message').getAttribute('maxlength')).toBe('5000');
      expect(button().disabled).toBeFalse();
    });

    it('works even when the site settings cannot be read', async () => {
      backend.expectOne('/api/v1/site-settings').flush(null, { status: 500, statusText: 'err' });
      await settle();
      expect(form()).toBeTruthy();

      await fillValid();
      await submit();
      post().flush(null, { status: 202, statusText: 'Accepted' });
      await settle();
      expect(el().querySelector('[role=status][data-contact-sent]')).toBeTruthy();
    });

    it('has an invisible honeypot named website, outside the tab order and the accessibility tree, but still rendered', async () => {
      flushSettings();
      await settle();

      const trap = field<HTMLInputElement>('website');
      expect(trap.getAttribute('tabindex')).toBe('-1');
      expect(trap.getAttribute('autocomplete')).toBe('off');
      const box = trap.closest('[aria-hidden=true]') as HTMLElement;
      expect(box).toBeTruthy();
      const style = getComputedStyle(box);
      expect(style.display).not.toBe('none');
      expect(style.visibility).not.toBe('hidden');
      expect(box.getBoundingClientRect().right).toBeLessThan(0);
    });

    it('shows required errors after a failed submit, tied to the fields, and sends nothing', async () => {
      flushSettings();
      await settle();
      expect(errorOf('email')).toBeNull();

      await submit();

      for (const name of ['email', 'subject', 'message']) {
        expect(field(name).getAttribute('aria-invalid')).toBe('true');
        expect(errorOf(name)?.textContent?.trim()).toBe('Ce champ est obligatoire.');
      }
      backend.expectNone('/api/v1/contact');
    });

    it('shows a field error only once the field is touched', async () => {
      flushSettings();
      await settle();

      await type('email', 'not-an-email');
      expect(errorOf('email')).toBeNull();
      await blur('email');
      expect(field('email').getAttribute('aria-invalid')).toBe('true');
      expect(errorOf('email')?.textContent).toContain('adresse e-mail valide');

      await type('email', 'ok@example.sn');
      expect(field('email').getAttribute('aria-invalid')).toBeNull();
      expect(errorOf('email')).toBeNull();
    });

    it('mirrors the server limits and refuses blank or control-character text', async () => {
      flushSettings();
      await settle();

      await type('email', 'a'.repeat(250) + '@b.sn');
      await type('subject', 'x'.repeat(151));
      await type('message', 'y'.repeat(5001));
      await submit();
      expect(errorOf('email')?.textContent).toContain('254');
      expect(errorOf('subject')?.textContent).toContain('150');
      expect(errorOf('message')?.textContent).toContain('5000');

      await type('email', 'ok@example.sn');
      await type('subject', '   ');
      await type('message', 'bad\u0007text');
      await submit();
      expect(errorOf('subject')?.textContent?.trim()).toBe('Ce champ est obligatoire.');
      expect(errorOf('message')?.textContent).toContain('non acceptée');
      backend.expectNone('/api/v1/contact');
    });

    it('lets tab and line breaks through in the message', async () => {
      flushSettings();
      await settle();
      await fillValid();
      await type('message', 'line one\nline\ttwo');
      await submit();

      expect(post().request.body.message).toBe('line one\nline\ttwo');
    });

    it('counts the characters of the message live', async () => {
      flushSettings();
      await settle();
      expect(el().querySelector('[data-contact-count]')?.textContent).toContain('0 / 5000');

      await type('message', 'hello');
      expect(el().querySelector('[data-contact-count]')?.textContent).toContain('5 / 5000');
    });

    it('sends the trimmed message with the active language, and disables the button while sending', async () => {
      flushSettings();
      await settle();
      await type('email', '  ada@example.sn ');
      await type('subject', ' Hello ');
      await type('message', ' A question. ');
      await submit();

      expect(button().disabled).toBeTrue();
      await submit();
      const req = post();
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ email: 'ada@example.sn', subject: 'Hello', message: 'A question.', language: 'fr', website: '' });
      req.flush(null, { status: 202, statusText: 'Accepted' });
      await settle();
      expect(button().disabled).toBeFalse();
    });

    it('sends the English locale when the page is read in English', async () => {
      await firstValueFrom(TestBed.inject(LocaleService).use('en'));
      flushSettings();
      await settle();
      await fillValid();
      await submit();

      expect(post().request.body.language).toBe('en');
    });

    it('passes along whatever a bot wrote in the honeypot', async () => {
      flushSettings();
      await settle();
      await fillValid();
      await type('website', 'http://spam.example');
      await submit();

      expect(post().request.body.website).toBe('http://spam.example');
    });

    it('confirms a success with a status that takes the focus, and empties the form', async () => {
      flushSettings();
      await settle();
      await fillValid();
      await submit();
      post().flush(null, { status: 202, statusText: 'Accepted' });
      await settle();

      const status = el().querySelector('[role=status][data-contact-sent]') as HTMLElement;
      expect(status).toBeTruthy();
      expect(status.textContent).toContain('Message envoyé');
      expect(document.activeElement).toBe(status);
      for (const name of ['email', 'subject', 'message']) {
        expect(field<HTMLInputElement>(name).value).toBe('');
        expect(field(name).getAttribute('aria-invalid')).toBeNull();
      }
      expect(el().querySelector('[role=alert][data-contact-alert]')).toBeNull();
    });

    it('maps 400 field errors onto the fields and keeps what was typed', async () => {
      flushSettings();
      await settle();
      await fillValid();
      await submit();
      post().flush({ email: 'must be valid', subject: 'bad' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      expect(field('email').getAttribute('aria-invalid')).toBe('true');
      expect(errorOf('email')).toBeTruthy();
      expect(field('subject').getAttribute('aria-invalid')).toBe('true');
      expect(field('message').getAttribute('aria-invalid')).toBeNull();
      expect(field<HTMLInputElement>('email').value).toBe('ada@example.sn');
      expect(button().disabled).toBeFalse();

      await type('email', 'ada@example.org');
      expect(field('email').getAttribute('aria-invalid')).toBeNull();
      expect(field('subject').getAttribute('aria-invalid')).toBe('true');
    });

    it('answers 429 with a "try again later" alert and keeps the form', async () => {
      flushSettings();
      await settle();
      await fillValid();
      await submit();
      post().flush({ status: 429 }, { status: 429, statusText: 'Too Many Requests', headers: { 'Retry-After': '60' } });
      await settle();

      const alert = el().querySelector('[role=alert][data-contact-alert]') as HTMLElement;
      expect(alert.textContent).toContain('Trop de messages');
      expect(field<HTMLInputElement>('subject').value).toBe('Hello');
      expect(el().querySelector('[data-contact-sent]')).toBeNull();
      expect(button().disabled).toBeFalse();
    });

    for (const [label, status] of [['a network error', 0], ['a server error', 503]] as const) {
      it(`answers ${label} with a generic retry alert and keeps the typed text`, async () => {
        flushSettings();
        await settle();
        await fillValid();
        await submit();
        const req = post();
        if (status === 0) {
          req.error(new ProgressEvent('error'));
        } else {
          req.flush(null, { status, statusText: 'Unavailable' });
        }
        await settle();

        const alert = el().querySelector('[role=alert][data-contact-alert]') as HTMLElement;
        expect(alert.textContent).toContain("n'a pas pu être envoyé");
        expect(field<HTMLTextAreaElement>('message').value).toBe('A question about schools.');
        expect(button().disabled).toBeFalse();

        await submit();
        post().flush(null, { status: 202, statusText: 'Accepted' });
        await settle();
        expect(el().querySelector('[role=alert][data-contact-alert]')).toBeNull();
        expect(el().querySelector('[data-contact-sent]')).toBeTruthy();
      });
    }

    it('pre-fills the subject with the school name, cleaned up, without overwriting what the visitor typed', async () => {
      fixture.componentRef.setInput('school', '  Test\nSchool  ');
      flushSettings();
      await settle();
      expect(field<HTMLInputElement>('subject').value).toBe('Candidature : Test School');

      await type('subject', 'My own subject');
      fixture.componentRef.setInput('school', 'Other');
      await settle();
      expect(field<HTMLInputElement>('subject').value).toBe('My own subject');
    });

    it('leaves the subject empty without a usable school', async () => {
      fixture.componentRef.setInput('school', ['A', 'B']);
      flushSettings();
      await settle();
      expect(field<HTMLInputElement>('subject').value).toBe('');
    });
  });

  describe('social buttons', () => {
    it('shows the configured networks and none of the others', async () => {
      backend.expectOne('/api/v1/site-settings').flush({
        contactEmail: 'hello@example.sn',
        aboutFr: 'x',
        aboutEn: 'y',
        whatsappUrl: 'https://wa.me/1',
        telegramUrl: null,
        instagramUrl: 'https://instagram.com/ms',
      });
      await settle();

      const networks = Array.from(el().querySelectorAll('a[data-network]')).map((a) => (a as HTMLElement).dataset['network']);
      expect(networks).toEqual(['whatsapp', 'instagram']);
    });

    it('hides the block when no network is configured', async () => {
      backend.expectOne('/api/v1/site-settings').flush({ contactEmail: 'hello@example.sn', aboutFr: 'x', aboutEn: 'y' });
      await settle();

      expect(el().querySelector('a[data-network]')).toBeNull();
      expect(el().querySelector('[data-contact-social]')).toBeNull();
    });
  });
});
