import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { ForumReply, ForumThread } from '@models/forum';
import { ForumComposerComponent } from './forum-composer.component';

describe('ForumComposerComponent', () => {
  let fixture: ComponentFixture<ForumComposerComponent>;
  let http: HttpTestingController;
  let savedThreads: ForumThread[];
  let savedReplies: ForumReply[];
  let cancelled: number;

  const el = () => fixture.nativeElement as HTMLElement;
  const byId = <T extends HTMLElement>(id: string) => el().querySelector<T>(`[data-testid="${id}"]`);
  const text = () => (el().textContent ?? '').replace(/\s+/g, ' ');

  async function mount(inputs: Record<string, unknown>, lang: 'fr' | 'en' = 'en') {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideTransloco(translocoOptions)],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use(lang));
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ForumComposerComponent);
    for (const [key, value] of Object.entries(inputs)) fixture.componentRef.setInput(key, value);
    savedThreads = [];
    savedReplies = [];
    cancelled = 0;
    fixture.componentInstance.savedThread.subscribe((t) => savedThreads.push(t));
    fixture.componentInstance.savedReply.subscribe((r) => savedReplies.push(r));
    fixture.componentInstance.cancelled.subscribe(() => cancelled++);
    await settle();
  }

  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function type(testid: string, value: string) {
    const field = byId<HTMLInputElement | HTMLTextAreaElement>(testid)!;
    field.value = value;
    field.dispatchEvent(new Event('input'));
    await settle();
  }

  async function submit() {
    byId<HTMLButtonElement>('forum-submit')!.click();
    await settle();
  }

  afterEach(() => http.verify());

  describe('new thread', () => {
    const schools = [{ id: 3, name: 'ISI Dakar' }, { id: 4, name: 'UCAD' }];

    beforeEach(async () => mount({ kind: 'thread', schools }));

    it('shows a title, a message and an optional school, with live counters', async () => {
      expect(byId('forum-title')).not.toBeNull();
      expect(byId('forum-body')).not.toBeNull();
      expect(byId('forum-school')!.querySelectorAll('option').length).toBe(3);
      expect(byId('forum-title-count')!.textContent).toContain('0 / 150');
      await type('forum-title', 'Hello');
      await type('forum-body', 'Abc\ndef');
      expect(byId('forum-title-count')!.textContent).toContain('5 / 150');
      expect(byId('forum-body-count')!.textContent).toContain('7 / 5000');
    });

    it('blocks an empty or blank submission and says which field is wrong, without calling the API', async () => {
      await type('forum-title', '   ');
      await submit();

      http.expectNone(() => true);
      expect(byId('forum-title-error')).not.toBeNull();
      expect(byId('forum-body-error')).not.toBeNull();
      expect(byId('forum-title')!.getAttribute('aria-invalid')).toBe('true');
    });

    it('blocks a title over 150 or a message over 5000 characters', async () => {
      await type('forum-title', 'a'.repeat(151));
      await type('forum-body', 'b'.repeat(5001));
      await submit();

      http.expectNone(() => true);
      expect(byId('forum-title-error')!.textContent).toContain('150');
      expect(byId('forum-body-error')!.textContent).toContain('5000');
    });

    it('sends the trimmed text and the chosen school, shows a busy button, then emits the created thread and resets', async () => {
      await type('forum-title', '  Which school?  ');
      await type('forum-body', ' Please help ');
      const select = byId<HTMLSelectElement>('forum-school')!;
      select.value = select.options[2].value;
      select.dispatchEvent(new Event('change'));
      await settle();
      await submit();

      const req = http.expectOne('/api/v1/forum/threads');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ title: 'Which school?', body: 'Please help', schoolId: 4 });
      expect(byId<HTMLButtonElement>('forum-submit')!.disabled).toBeTrue();
      req.flush({ id: 11, title: 'Which school?' }, { status: 201, statusText: 'Created' });
      await settle();

      expect(savedThreads.map((t) => t.id)).toEqual([11]);
      expect(byId<HTMLInputElement>('forum-title')!.value).toBe('');
      expect(byId<HTMLButtonElement>('forum-submit')!.disabled).toBeFalse();
    });

    it('omits the school when none is picked', async () => {
      await type('forum-title', 'T');
      await type('forum-body', 'B');
      await submit();

      const req = http.expectOne('/api/v1/forum/threads');
      expect(req.request.body).toEqual({ title: 'T', body: 'B' });
      req.flush({ id: 1 }, { status: 201, statusText: 'Created' });
    });

    it('shows the server 400 under the field it names and keeps what was typed', async () => {
      await type('forum-title', 'T');
      await type('forum-body', 'B');
      await submit();
      http.expectOne('/api/v1/forum/threads').flush({ body: 'Caractère interdit' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      expect(byId('forum-body-error')).not.toBeNull();
      expect(byId('forum-title-error')).toBeNull();
      expect(byId<HTMLInputElement>('forum-title')!.value).toBe('T');
      expect(savedThreads).toEqual([]);
      await type('forum-body', 'B2');
      expect(byId('forum-body-error')).toBeNull();
    });

    it('tells how long to wait on a 429, from Retry-After, and keeps the text', async () => {
      await type('forum-title', 'T');
      await type('forum-body', 'B');
      await submit();
      http.expectOne('/api/v1/forum/threads').flush(null, { status: 429, statusText: 'Too Many', headers: { 'Retry-After': '37' } });
      await settle();

      const alert = byId('forum-alert')!;
      expect(alert.getAttribute('role')).toBe('alert');
      expect(alert.textContent).toContain('37');
      expect(byId<HTMLInputElement>('forum-title')!.value).toBe('T');
    });

    it('keeps the button disabled for the delay Retry-After announced, then enables it again', async () => {
      jasmine.clock().install();
      try {
        await type('forum-title', 'T');
        await type('forum-body', 'B');
        await submit();
        http.expectOne('/api/v1/forum/threads').flush(null, { status: 429, statusText: 'Too Many', headers: { 'Retry-After': '3' } });
        await settle();
        expect(byId<HTMLButtonElement>('forum-submit')!.disabled).toBeTrue();

        jasmine.clock().tick(2000);
        await settle();
        expect(byId<HTMLButtonElement>('forum-submit')!.disabled).toBeTrue();

        jasmine.clock().tick(1100);
        await settle();
        expect(byId<HTMLButtonElement>('forum-submit')!.disabled).toBeFalse();
      } finally {
        jasmine.clock().uninstall();
      }
    });

    it('links a refused school to the select: message, aria-invalid, aria-describedby and focus', async () => {
      await type('forum-title', 'T');
      await type('forum-body', 'B');
      await submit();
      http.expectOne('/api/v1/forum/threads').flush({ schoolId: 'Établissement inconnu' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      const select = byId<HTMLSelectElement>('forum-school')!;
      const error = byId('forum-school-error')!;
      expect(error).not.toBeNull();
      expect(error.textContent).not.toContain('inconnu');
      expect(select.getAttribute('aria-invalid')).toBe('true');
      expect(select.getAttribute('aria-describedby')).toContain(error.id);
      expect(document.activeElement).toBe(select);
      select.value = select.options[1].value;
      select.dispatchEvent(new Event('change'));
      await settle();
      expect(byId('forum-school-error')).toBeNull();
    });

    it('shows a generic message under a refused text field, never the server wording', async () => {
      await type('forum-title', 'T');
      await type('forum-body', 'B');
      await submit();
      http.expectOne('/api/v1/forum/threads').flush({ title: 'Caractères de contrôle interdits' }, { status: 400, statusText: 'Bad Request' });
      await settle();

      const text = byId('forum-title-error')!.textContent!;
      expect(text).toContain('not accepted');
      expect(text).not.toContain('Caractères');
    });

    it('explains a 429 without Retry-After, a 403 and a network failure with distinct messages', async () => {
      const messages: string[] = [];
      for (const [status, headers] of [[429, {}], [403, {}], [0, {}]] as const) {
        await type('forum-title', 'T');
        await type('forum-body', 'B');
        await submit();
        http.expectOne('/api/v1/forum/threads').flush(null, { status, statusText: 'x', headers });
        await settle();
        messages.push(byId('forum-alert')!.textContent!.trim());
      }
      expect(new Set(messages).size).toBe(3);
    });
  });

  describe('new reply', () => {
    beforeEach(async () => mount({ kind: 'reply', threadId: 4 }));

    it('has no title or school field', () => {
      expect(byId('forum-title')).toBeNull();
      expect(byId('forum-school')).toBeNull();
      expect(byId('forum-body')).not.toBeNull();
    });

    it('posts the reply to its thread and emits it', async () => {
      await type('forum-body', ' Thanks ');
      await submit();

      const req = http.expectOne('/api/v1/forum/threads/4/replies');
      expect(req.request.body).toEqual({ body: 'Thanks' });
      req.flush({ id: 21, threadId: 4, body: 'Thanks' }, { status: 201, statusText: 'Created' });
      await settle();

      expect(savedReplies.map((r) => r.id)).toEqual([21]);
      expect(byId<HTMLTextAreaElement>('forum-body')!.value).toBe('');
    });

    it('tells the page when the thread turned out to be locked', async () => {
      let locked = 0;
      fixture.componentInstance.locked.subscribe(() => locked++);
      await type('forum-body', 'Late');
      await submit();
      http.expectOne('/api/v1/forum/threads/4/replies').flush(null, { status: 409, statusText: 'Conflict' });
      expect(locked).toBe(1);
    });

    it('shows the locked-thread refusal on a 409', async () => {
      await type('forum-body', 'Late');
      await submit();
      http.expectOne('/api/v1/forum/threads/4/replies').flush({ message: 'locked' }, { status: 409, statusText: 'Conflict' });
      await settle();

      expect(byId('forum-alert')!.textContent).toContain('locked');
      expect(savedReplies).toEqual([]);
    });
  });

  describe('editing', () => {
    it('prefills a thread, sends title and body only, and can be cancelled', async () => {
      await mount({ kind: 'thread', existing: { id: 9, title: 'Old', body: 'Old body' }, schools: [{ id: 3, name: 'X' }] });

      expect(byId<HTMLInputElement>('forum-title')!.value).toBe('Old');
      expect(byId<HTMLTextAreaElement>('forum-body')!.value).toBe('Old body');
      expect(byId('forum-school')).toBeNull();
      expect(document.activeElement).toBe(byId('forum-title'));
      await type('forum-title', 'New');
      await submit();

      const req = http.expectOne('/api/v1/forum/threads/9');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ title: 'New', body: 'Old body' });
      req.flush({ id: 9, title: 'New' });
      await settle();
      expect(savedThreads.map((t) => t.title)).toEqual(['New']);
      expect(byId<HTMLInputElement>('forum-title')!.value).toBe('New');

      byId<HTMLButtonElement>('forum-cancel')!.click();
      expect(cancelled).toBe(1);
    });

    it('prefills a reply and puts it with PUT', async () => {
      await mount({ kind: 'reply', existing: { id: 8, body: 'Typo' } });

      expect(byId<HTMLTextAreaElement>('forum-body')!.value).toBe('Typo');
      expect(document.activeElement).toBe(byId('forum-body'));
      await type('forum-body', 'Fixed');
      await submit();

      const req = http.expectOne('/api/v1/forum/replies/8');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ body: 'Fixed' });
      req.flush({ id: 8, body: 'Fixed' });
      await settle();
      expect(savedReplies.map((r) => r.body)).toEqual(['Fixed']);
    });
  });

  describe('a 403 on a write', () => {
    const account = (emailVerified?: boolean) =>
      localStorage.setItem('auth_user', JSON.stringify({ id: 1, firstname: 'A', lastname: 'B', email: 'a@b.c', role: { id: 1, name: 'ROLE_STUDENT' }, emailVerified }));

    beforeEach(() => localStorage.clear());
    afterEach(() => localStorage.clear());

    async function refused() {
      await type('forum-title', 'T');
      await type('forum-body', 'B');
      await submit();
      http.expectOne('/api/v1/forum/threads').flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });
      await settle();
    }

    it('says the e-mail must be confirmed, with a link to the profile, when the account is unverified', async () => {
      account(false);
      await mount({ kind: 'thread', schools: [] });
      await refused();

      const alert = byId('forum-alert')!;
      expect(alert.textContent).toContain('Confirm your email address');
      expect(alert.querySelector('a')!.getAttribute('href')).toBe('/en/profile');
    });

    it('keeps the generic refusal, without link, for a verified account', async () => {
      account(true);
      await mount({ kind: 'thread', schools: [] });
      await refused();

      const alert = byId('forum-alert')!;
      expect(alert.textContent).toContain('not allowed to write');
      expect(alert.querySelector('a')).toBeNull();
    });

    it('keeps the generic refusal when the session does not say whether the e-mail is verified', async () => {
      account(undefined);
      await mount({ kind: 'thread', schools: [] });
      await refused();

      expect(byId('forum-alert')!.textContent).toContain('not allowed to write');
    });

    it('does not turn another failure into the verification reason', async () => {
      account(false);
      await mount({ kind: 'thread', schools: [] });
      await type('forum-title', 'T');
      await type('forum-body', 'B');
      await submit();
      http.expectOne('/api/v1/forum/threads').flush(null, { status: 500, statusText: 'Server Error' });
      await settle();

      expect(byId('forum-alert')!.textContent).not.toContain('Confirm your email address');
    });
  });

  it('is translated in French', async () => {
    await mount({ kind: 'thread', schools: [] }, 'fr');
    expect(text()).toContain('Titre');
    expect(text()).toContain('0 / 150');
  });
});
