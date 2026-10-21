import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { ForumReportComponent } from './forum-report.component';

describe('ForumReportComponent', () => {
  let fixture: ComponentFixture<ForumReportComponent>;
  let http: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const byId = <T extends HTMLElement>(id: string) => el().querySelector<T>(`[data-testid="${id}"]`);
  const text = () => (el().textContent ?? '').replace(/\s+/g, ' ');
  const url = (kind: 'thread' | 'reply' = 'thread', id = 5) => `/api/v1/forum/${kind === 'thread' ? 'threads' : 'replies'}/${id}/report`;

  async function mount(inputs: { kind: 'thread' | 'reply'; targetId: number } = { kind: 'thread', targetId: 5 }, lang: 'fr' | 'en' = 'en') {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), provideTransloco(translocoOptions)],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ForumReportComponent);
    for (const [key, value] of Object.entries(inputs)) fixture.componentRef.setInput(key, value);
    await settle();
  }

  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function click(id: string) {
    byId(id)!.click();
    await settle();
  }

  async function type(value: string) {
    const field = byId<HTMLTextAreaElement>('forum-report-reason')!;
    field.value = value;
    field.dispatchEvent(new Event('input'));
    await settle();
  }

  async function open() {
    await click('forum-report-open');
  }

  async function send(value = 'Spam') {
    await open();
    await type(value);
    await click('forum-report-submit');
  }

  afterEach(() => http.verify());

  it('shows only a Report button at first', async () => {
    await mount();
    expect(byId('forum-report-open')!.textContent).toContain('Report');
    expect(byId('forum-report-open')!.getAttribute('aria-expanded')).toBe('false');
    expect(byId('forum-report-open')!.hasAttribute('aria-controls')).toBeFalse();
    expect(byId('forum-report-form')).toBeNull();
  });

  it('points aria-controls at the form only while it is open', async () => {
    await mount();
    await open();
    expect(byId('forum-report-open')!.getAttribute('aria-controls')).toBe(byId('forum-report-form')!.id);
  });

  it('counts and limits the trimmed text, as the API does: 255 characters and a trailing space are accepted', async () => {
    await mount();
    await open();
    await type('x'.repeat(255) + ' ');
    expect(byId('forum-report-count')!.textContent).toContain('255 / 255');
    await click('forum-report-submit');
    expect(byId('forum-report-error')).toBeNull();
    const request = http.expectOne(url());
    expect(request.request.body).toEqual({ reason: 'x'.repeat(255) });
    request.flush(null, { status: 201, statusText: 'Created' });
    await settle();
  });

  it('cancels the request and touches nothing when the component is destroyed while sending', async () => {
    await mount();
    await send();
    const request = http.expectOne(url());
    fixture.destroy();
    expect(request.cancelled).toBeTrue();
  });

  it('opens a labelled reason field with a live counter and moves the focus into it', async () => {
    await mount();
    await open();

    const field = byId<HTMLTextAreaElement>('forum-report-reason')!;
    expect(byId('forum-report-open')!.getAttribute('aria-expanded')).toBe('true');
    expect(el().querySelector(`label[for="${field.id}"]`)).not.toBeNull();
    expect(byId('forum-report-count')!.textContent).toContain('0 / 255');
    await type('Insults');
    expect(byId('forum-report-count')!.textContent).toContain('7 / 255');
    await settle();
    expect(document.activeElement).toBe(field);
  });

  it('closes without calling the API and gives the focus back to the Report button', async () => {
    await mount();
    await open();
    await click('forum-report-cancel');
    await settle();

    expect(byId('forum-report-form')).toBeNull();
    expect(document.activeElement).toBe(byId('forum-report-open'));
  });

  it('refuses a blank or too long reason without calling the API', async () => {
    await mount();
    await open();
    await type('   ');
    await click('forum-report-submit');
    expect(byId('forum-report-error')!.textContent).toContain('required');
    expect(byId('forum-report-reason')!.getAttribute('aria-invalid')).toBe('true');

    await type('x'.repeat(256));
    await click('forum-report-submit');
    expect(byId('forum-report-error')!.textContent).toContain('255');
  });

  it('refuses control characters without calling the API', async () => {
    await mount();
    await open();
    await type('bad\u0000reason');
    await click('forum-report-submit');
    expect(byId('forum-report-error')!.textContent).toContain('not accepted');
  });

  it('reports a thread with the trimmed reason, then confirms and takes the focus back', async () => {
    await mount();
    await send('  Spam  ');

    const request = http.expectOne(url('thread', 5));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ reason: 'Spam' });
    request.flush(null, { status: 201, statusText: 'Created' });
    await settle();

    expect(byId('forum-report-form')).toBeNull();
    expect(byId('forum-report-live')!.getAttribute('role')).toBe('status');
    expect(byId('forum-report-live')!.textContent).toContain('Thank you');
    expect(byId('forum-report-open')!.getAttribute('aria-disabled')).toBe('true');
    expect(byId('forum-report-open')!.textContent).toContain('Reported');
    expect(document.activeElement).toBe(byId('forum-report-open'));
  });

  it('reports a reply on the reply endpoint', async () => {
    await mount({ kind: 'reply', targetId: 11 });
    await send();
    http.expectOne(url('reply', 11)).flush(null, { status: 201, statusText: 'Created' });
    await settle();
    expect(byId('forum-report-live')!.textContent).toContain('Thank you');
  });

  it('does nothing when the reported button is clicked again', async () => {
    await mount();
    await send();
    http.expectOne(url()).flush(null, { status: 201, statusText: 'Created' });
    await settle();
    await click('forum-report-open');
    expect(byId('forum-report-form')).toBeNull();
  });

  it('sends one request however many times submit is hit, and disables the button meanwhile', async () => {
    await mount();
    await send();
    const submit = byId<HTMLButtonElement>('forum-report-submit')!;
    expect(submit.disabled).toBeTrue();
    expect(submit.getAttribute('aria-busy')).toBe('true');
    submit.click();
    byId('forum-report-form')!.dispatchEvent(new Event('submit'));
    await settle();
    http.expectOne(url()).flush(null, { status: 201, statusText: 'Created' });
    await settle();
  });

  describe('failures', () => {
    async function fail(status: number, headers: Record<string, string> = {}, body: object | null = { message: 'x' }) {
      await send();
      http.expectOne(url()).flush(body, { status, statusText: 'err', headers });
      await settle();
    }

    it('explains a 409: already reported, or the visitor\'s own message, and marks it as reported', async () => {
      await mount();
      await fail(409);
      expect(byId('forum-report-live')!.textContent).toContain('already reported');
      expect(byId('forum-report-live')!.textContent).toContain('your own');
      expect(byId('forum-report-form')).toBeNull();
      expect(byId('forum-report-open')!.getAttribute('aria-disabled')).toBe('true');
      expect(document.activeElement).toBe(byId('forum-report-live'));
    });

    it('shows the server refusal under the reason field on a 400', async () => {
      await mount();
      await fail(400, {}, { reason: 'Not allowed' });
      expect(byId('forum-report-error')!.textContent).toContain('not accepted');
      expect(byId('forum-report-reason')!.getAttribute('aria-invalid')).toBe('true');
      expect(byId('forum-report-form')).not.toBeNull();
      // Editing the field clears the refusal.
      await type('Another reason');
      expect(byId('forum-report-error')).toBeNull();
    });

    it('tells how long to wait on a 429 and keeps the text, then lets the visitor retry', async () => {
      jasmine.clock().install();
      try {
        await mount();
        await send('Spam');
        http.expectOne(url()).flush(null, { status: 429, statusText: 'Too Many', headers: { 'Retry-After': '3' } });
        await settle();

        expect(byId('forum-report-alert')!.textContent).toContain('3 seconds');
        expect(byId<HTMLTextAreaElement>('forum-report-reason')!.value).toBe('Spam');
        expect(byId<HTMLButtonElement>('forum-report-submit')!.disabled).toBeTrue();
        jasmine.clock().tick(3100);
        await settle();
        expect(byId<HTMLButtonElement>('forum-report-submit')!.disabled).toBeFalse();
        expect(byId('forum-report-alert')).toBeNull();
      } finally {
        jasmine.clock().uninstall();
      }
    });

    it('sends nothing while the delay runs, even if the form is submitted by other means', async () => {
      jasmine.clock().install();
      try {
        await mount();
        await send('Spam');
        http.expectOne(url()).flush(null, { status: 429, statusText: 'Too Many', headers: { 'Retry-After': '30' } });
        await settle();
        byId('forum-report-form')!.dispatchEvent(new Event('submit'));
        await settle();
        http.expectNone(url());
      } finally {
        jasmine.clock().uninstall();
      }
    });

    it('says to wait a minute on a 429 without Retry-After', async () => {
      await mount();
      await fail(429);
      expect(byId('forum-report-alert')!.textContent).toContain('a minute');
    });

    it('says the session ended on a 401', async () => {
      await mount();
      await fail(401);
      expect(byId('forum-report-alert')!.textContent).toContain('session has ended');
    });

    it('says the account cannot report on a 403', async () => {
      await mount();
      await fail(403);
      expect(byId('forum-report-alert')!.textContent).toContain('not allowed');
    });

    it('says the message no longer exists on a 404 and offers no more reporting', async () => {
      await mount();
      await fail(404);
      expect(byId('forum-report-live')!.textContent).toContain('no longer exists');
      expect(byId('forum-report-form')).toBeNull();
      expect(byId('forum-report-open')).toBeNull();
      expect(document.activeElement).toBe(byId('forum-report-live'));
    });

    it('keeps the form and the text on any other failure, and allows another try', async () => {
      await mount();
      await fail(500);
      expect(byId('forum-report-alert')!.textContent).toContain('could not be sent');
      expect(byId<HTMLTextAreaElement>('forum-report-reason')!.value).toBe('Spam');
      await click('forum-report-submit');
      http.expectOne(url()).flush(null, { status: 201, statusText: 'Created' });
      await settle();
      expect(byId('forum-report-live')!.textContent).toContain('Thank you');
    });
  });

  it('treats the reason as plain text: markup is sent as typed and creates no element', async () => {
    await mount();
    const markup = '<img src=x onerror="window.__reportXss = 1"><script>window.__reportXss = 2</script>';
    await send(markup);
    const request = http.expectOne(url());
    expect(request.request.body).toEqual({ reason: markup });
    request.flush(null, { status: 201, statusText: 'Created' });
    await settle();
    expect(el().querySelector('img, script')).toBeNull();
    expect((window as unknown as Record<string, unknown>)['__reportXss']).toBeUndefined();
  });

  it('is in French', async () => {
    await mount({ kind: 'thread', targetId: 5 }, 'fr');
    expect(byId('forum-report-open')!.textContent).toContain('Signaler');
    await open();
    expect(text()).toContain('Motif');
    expect(byId('forum-report-submit')!.textContent).toContain('Envoyer le signalement');
  });
});
