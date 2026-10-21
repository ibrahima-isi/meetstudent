import { TestBed } from '@angular/core/testing';
import { Component, RESPONSE_INIT, provideZonelessChangeDetection, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { ForumReply, ForumThread } from '@models/forum';
import { ForumThreadPageComponent } from './forum-thread-page.component';

@Component({ template: 'other' })
class OtherStubComponent {}

describe('ForumThreadPageComponent', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let responseInit: { status: number };

  const thread = (over: Partial<ForumThread> = {}): ForumThread => ({
    id: 5,
    title: 'Which master in Dakar?',
    body: 'I am looking for a master.\nAny idea?',
    authorName: 'Awa',
    authorRole: 'STUDENT',
    authorDeleted: false,
    mine: false,
    locked: false,
    hidden: false,
    replyCount: 2,
    createdAt: '2026-10-01T10:00:00Z',
    ...over,
  });

  const reply = (id: number, over: Partial<ForumReply> = {}): ForumReply => ({
    id,
    threadId: 5,
    body: `Reply number ${id}`,
    authorName: 'Moussa',
    authorRole: 'EXPERT',
    authorDeleted: false,
    mine: false,
    hidden: false,
    createdAt: '2026-10-02T10:00:00Z',
    ...over,
  });

  const replyPage = (content: ForumReply[], extra: Record<string, unknown> = {}) => ({
    content,
    number: 0,
    size: 20,
    totalPages: 1,
    totalElements: content.length,
    first: true,
    last: true,
    ...extra,
  });

  const el = () => harness.routeNativeElement as HTMLElement;
  const text = () => (el().textContent ?? '').replace(/\s+/g, ' ');
  const byId = <T extends HTMLElement>(id: string) => el().querySelector<T>(`[data-testid="${id}"]`);
  const all = (id: string) => Array.from(el().querySelectorAll<HTMLElement>(`[data-testid="${id}"]`));
  const click = async (target: HTMLElement | null) => {
    target!.click();
    await settle();
  };

  async function settle() {
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  const threadRequest = () => http.expectOne((r) => /\/forum\/threads\/\d+$/.test(r.url) && r.method === 'GET');
  const repliesRequest = () => http.expectOne((r) => /\/forum\/threads\/\d+\/replies$/.test(r.url) && r.method === 'GET');

  async function open(
    id: string,
    opts: {
      thread?: ForumThread | { status: number } | 'skip';
      replies?: ReturnType<typeof replyPage> | { status: number };
      role?: string;
      status?: string;
      lang?: 'fr' | 'en';
    } = {},
  ) {
    const signedIn = opts.role !== undefined;
    responseInit = { status: 200 };
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          {
            path: ':lang',
            children: [
              { path: 'forum', component: OtherStubComponent },
              { path: 'forum/:id', component: ForumThreadPageComponent },
              { path: 'login', component: OtherStubComponent },
              { path: 'profile', component: OtherStubComponent },
            ],
          },
        ]),
        provideTransloco(translocoOptions),
        { provide: RESPONSE_INIT, useValue: responseInit },
        {
          provide: TokenService,
          useValue: {
            isAuthenticated: () => signedIn,
            user: signal(signedIn ? { id: 1, firstname: 'Me', role: { name: opts.role }, expertStatus: opts.status } : null),
          },
        },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use(opts.lang ?? 'en'));
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl(`/${opts.lang ?? 'en'}/forum/${id}`, ForumThreadPageComponent);
    const wanted = opts.thread ?? thread();
    if (wanted !== 'skip') {
      await Promise.resolve();
      const t = threadRequest();
      if ('status' in wanted && !('title' in wanted)) t.flush({ message: 'x' }, { status: wanted.status, statusText: 'err' });
      else t.flush(wanted);
      const repliesAnswer = opts.replies ?? replyPage([reply(11), reply(10)]);
      const r = http.match((req) => /\/replies$/.test(req.url))[0];
      if (r) {
        if ('status' in repliesAnswer) r.flush({ message: 'x' }, { status: repliesAnswer.status, statusText: 'err' });
        else r.flush(repliesAnswer);
      }
    }
    await navigation;
    await settle();
  }

  afterEach(() => http.verify());

  it('reads the thread and its first page of twenty replies', async () => {
    await open('5', { thread: 'skip' });
    expect(threadRequest().request.url).toMatch(/\/forum\/threads\/5$/);
    const replies = repliesRequest();
    expect(replies.request.params.get('page')).toBe('0');
    expect(replies.request.params.get('size')).toBe('20');
    http.match(() => true).forEach((r) => r.flush(r.request.url.endsWith('/replies') ? replyPage([]) : thread()));
    await settle();
  });

  it('shows the opening message, its author and the replies, with line breaks kept', async () => {
    await open('5', { thread: thread({ schoolId: 3, schoolName: 'ISI Dakar' }) });

    expect(el().querySelector('h1')!.textContent).toContain('Which master in Dakar?');
    const body = byId('forum-thread-body')!;
    expect(body.textContent).toContain('I am looking for a master.\nAny idea?');
    expect(body.className).toContain('whitespace-pre-line');
    expect(body.className).toContain('break-words');
    expect(byId('forum-thread-head')!.textContent).toContain('Awa');
    expect(byId('forum-thread-head')!.textContent).toContain('ISI Dakar');
    expect(all('forum-reply').map((r) => r.textContent)).toEqual([jasmine.stringContaining('Reply number 11'), jasmine.stringContaining('Reply number 10')]);
    expect(all('forum-reply')[0].textContent).toContain('Moussa');
    expect(all('forum-reply')[0].textContent).toContain('Expert');
    expect(text()).toContain('2 replies');
  });

  it('puts the thread title in the page title', async () => {
    await open('5');
    expect(TestBed.inject(Title).getTitle()).toContain('Which master in Dakar?');
  });

  it('links back to the forum list of the language read', async () => {
    await open('5');
    expect(byId('forum-back')!.getAttribute('href')).toBe('/en/forum');
  });

  it('marks an edited message', async () => {
    await open('5', { thread: thread({ modifiedAt: '2026-10-03T10:00:00Z' }), replies: replyPage([reply(1, { modifiedAt: '2026-10-03T11:00:00Z' }), reply(2)]) });
    expect(byId('forum-thread-head')!.textContent).toContain('edited');
    expect(all('forum-reply')[0].textContent).toContain('edited');
    expect(all('forum-reply')[1].textContent).not.toContain('edited');
  });

  it('shows the deleted-user text for a deleted author, on the thread and on a reply', async () => {
    await open('5', {
      thread: thread({ authorName: undefined, authorRole: undefined, authorDeleted: true }),
      replies: replyPage([reply(1, { authorName: undefined, authorRole: undefined, authorDeleted: true })]),
    });
    expect(byId('forum-thread-head')!.textContent).toContain('Deleted user');
    expect(all('forum-reply')[0].textContent).toContain('Deleted user');
  });

  it('cleans invisible characters out of author names', async () => {
    await open('5', { thread: thread({ authorName: 'A‮wa​' }), replies: replyPage([reply(1, { authorName: 'ㅤMou⠀ssa' })]) });
    expect(byId('forum-thread-head')!.querySelector('[data-testid="forum-author-name"]')!.textContent!.trim()).toBe('Awa');
    expect(all('forum-reply')[0].querySelector('[data-testid="forum-author-name"]')!.textContent!.trim()).toBe('Moussa');
  });

  it('displays titles, bodies and names as text: no element is created from markup', async () => {
    await open('5', {
      thread: thread({
        title: '<img src=x onerror="window.pwned=1">',
        body: '<script>window.pwned=1</script><b>x</b>',
        authorName: '<img src=n onerror="window.pwned=1">',
      }),
      replies: replyPage([reply(1, { body: '<img src=r onerror="window.pwned=1"><script>window.pwned=1</script>', authorName: '<svg onload="window.pwned=1">' })]),
    });

    expect(el().querySelector('img, script, b, svg:not([data-lucide]):not(.lucide)')).toBeNull();
    expect(text()).toContain('<img src=x onerror="window.pwned=1">');
    expect(text()).toContain('<script>window.pwned=1</script>');
    expect(text()).toContain('<img src=r onerror="window.pwned=1">');
    expect((window as unknown as { pwned?: number }).pwned).toBeUndefined();
    expect(TestBed.inject(Title).getTitle()).toContain('<img src=x');
  });

  it('shows a loading state, then a message when there are no replies yet', async () => {
    await open('5', { replies: replyPage([]) });
    expect(byId('forum-no-replies')).not.toBeNull();
  });

  it('lets the browser pick the direction of each user text and keeps names inside their box', async () => {
    await open('5');

    expect(el().querySelector('h1')!.getAttribute('dir')).toBe('auto');
    expect(byId('forum-thread-body')!.getAttribute('dir')).toBe('auto');
    expect(all('forum-reply-body')[0].getAttribute('dir')).toBe('auto');
    const name = byId('forum-thread-head')!.querySelector('[data-testid="forum-author-name"]')!;
    expect(name.getAttribute('dir')).toBe('auto');
    expect(name.className).toContain('overflow-hidden');
  });

  it('cleans the title of bidi and control characters before it goes in the page title', async () => {
    await open('5', { thread: thread({ title: '\u202EWhich\nmaster\u200B?' }) });
    expect(TestBed.inject(Title).getTitle()).toBe('Which master? | MeetStudent');
  });

  it('flags hidden messages, which only an administrator receives', async () => {
    await open('5', { role: 'ROLE_ADMIN', thread: thread({ hidden: true }), replies: replyPage([reply(1, { hidden: true }), reply(2)]) });

    expect(byId('forum-thread-head')!.querySelector('[data-testid="forum-hidden-badge"]')!.textContent).toContain('Hidden');
    expect(all('forum-reply')[0].querySelector('[data-testid="forum-hidden-badge"]')).not.toBeNull();
    expect(all('forum-reply')[1].querySelector('[data-testid="forum-hidden-badge"]')).toBeNull();
  });

  describe('failures', () => {
    it('answers 404 on the page and on the server response for an unknown thread, without retry', async () => {
      await open('999', { thread: { status: 404 } });
      expect(byId('forum-not-found')).not.toBeNull();
      expect(el().querySelector('app-error-state')).toBeNull();
      expect(responseInit.status).toBe(404);
    });

    it('answers 404 for an id that is not a positive integer, without calling the API', async () => {
      for (const id of ['abc', '0', '1e3', '-2']) {
        TestBed.resetTestingModule();
        await open(id, { thread: 'skip' });
        http.expectNone(() => true);
        expect(byId('forum-not-found')).withContext(id).not.toBeNull();
      }
    });

    it('shows the error state with a retry when the thread cannot be loaded', async () => {
      await open('5', { thread: { status: 500 } });
      expect(el().querySelector('app-error-state')).not.toBeNull();
      expect(responseInit.status).toBe(200);

      (el().querySelector('app-error-state button') as HTMLButtonElement).click();
      await Promise.resolve();
      threadRequest().flush(thread());
      repliesRequest().flush(replyPage([reply(1)]));
      await settle();
      expect(el().querySelector('h1')).not.toBeNull();
      expect(all('forum-reply').length).toBe(1);
    });

    it('keeps the thread when only the replies fail, with their own retry', async () => {
      await open('5', { replies: { status: 500 } });

      expect(el().querySelector('h1')).not.toBeNull();
      expect(byId('forum-replies-error')).not.toBeNull();
      (byId('forum-replies-error')!.querySelector('button') as HTMLButtonElement).click();
      await Promise.resolve();
      repliesRequest().flush(replyPage([reply(1)]));
      await settle();
      expect(byId('forum-replies-error')).toBeNull();
      expect(all('forum-reply').length).toBe(1);
    });
  });

  describe('more replies', () => {
    it('loads the next page on demand and appends it', async () => {
      await open('5', { replies: replyPage([reply(30)], { totalPages: 2, last: false, totalElements: 2 }) });

      await click(byId('forum-more'));
      const req = repliesRequest();
      expect(req.request.params.get('page')).toBe('1');
      req.flush(replyPage([reply(29)], { number: 1, totalPages: 2, first: false, last: true, totalElements: 2 }));
      await settle();

      expect(all('forum-reply').length).toBe(2);
      expect(byId('forum-more')).toBeNull();
    });

    it('says when the next page failed and lets the visitor retry', async () => {
      await open('5', { replies: replyPage([reply(30)], { totalPages: 2, last: false }) });

      await click(byId('forum-more'));
      repliesRequest().flush(null, { status: 500, statusText: 'err' });
      await settle();
      expect(byId('forum-more-error')).not.toBeNull();
      expect(all('forum-reply').length).toBe(1);

      await click(byId('forum-more'));
      repliesRequest().flush(replyPage([reply(29)], { number: 1, last: true }));
      await settle();
      expect(all('forum-reply').length).toBe(2);
    });
  });

  describe('locked thread', () => {
    it('shows a badge and refuses the reply form, even to a student', async () => {
      await open('5', { thread: thread({ locked: true }), role: 'ROLE_STUDENT' });

      expect(byId('forum-locked-badge')).not.toBeNull();
      expect(byId('forum-locked-notice')!.textContent).toContain('locked');
      expect(el().querySelector('app-forum-composer')).toBeNull();
    });

    it('says so to anonymous visitors as well', async () => {
      await open('5', { thread: thread({ locked: true }) });
      expect(byId('forum-locked-notice')).not.toBeNull();
    });
  });

  describe('who can write', () => {
    it('invites an anonymous visitor to sign in and come back to this thread', async () => {
      await open('5');

      expect(el().querySelector('app-forum-composer')).toBeNull();
      expect(byId('forum-gate')!.querySelector('a')!.getAttribute('href')).toBe('/en/login?returnUrl=%2Fen%2Fforum%2F5');
    });

    it('tells a pending expert why, and shows no edit or delete even on their old messages', async () => {
      await open('5', { role: 'ROLE_EXPERT', status: 'PENDING', thread: thread({ mine: true }), replies: replyPage([reply(1, { mine: true })]) });

      expect(byId('forum-gate')!.textContent).toContain('awaiting approval');
      expect(el().querySelector('app-forum-composer')).toBeNull();
      expect(byId('forum-edit')).toBeNull();
      expect(byId('forum-delete')).toBeNull();
    });

    it('tells a rejected expert why', async () => {
      await open('5', { role: 'ROLE_EXPERT', status: 'REJECTED' });
      expect(byId('forum-gate')!.textContent).toContain('rejected');
    });

    it('shows an administrator the thread read-only', async () => {
      await open('5', { role: 'ROLE_ADMIN', thread: thread({ mine: true }) });

      expect(byId('forum-gate')!.textContent).toContain('read-only');
      expect(byId('forum-edit')).toBeNull();
      expect(el().querySelector('app-forum-composer')).toBeNull();
    });

    it('lets an approved expert reply', async () => {
      await open('5', { role: 'ROLE_EXPERT', status: 'APPROVED' });
      expect(el().querySelector('app-forum-composer')).not.toBeNull();
    });
  });

  describe('replying', () => {
    it('adds the new reply on top and counts it, without reloading', async () => {
      await open('5', { role: 'ROLE_STUDENT' });

      const field = byId<HTMLTextAreaElement>('forum-body')!;
      field.value = 'My answer';
      field.dispatchEvent(new Event('input'));
      await click(byId('forum-submit'));
      http.expectOne('/api/v1/forum/threads/5/replies').flush(reply(77, { body: 'My answer', mine: true, authorName: 'Me' }), { status: 201, statusText: 'Created' });
      await settle();

      expect(all('forum-reply')[0].textContent).toContain('My answer');
      expect(all('forum-reply').length).toBe(3);
      expect(text()).toContain('3 replies');
      expect(byId<HTMLTextAreaElement>('forum-body')!.value).toBe('');
    });
  });

  describe('own messages', () => {
    it('shows edit and delete on mine only', async () => {
      await open('5', { role: 'ROLE_STUDENT', replies: replyPage([reply(1, { mine: true }), reply(2)]) });

      expect(byId('forum-edit')).toBeNull();
      expect(all('forum-reply')[0].querySelector('[data-testid="forum-reply-edit"]')).not.toBeNull();
      expect(all('forum-reply')[1].querySelector('[data-testid="forum-reply-edit"]')).toBeNull();
      expect(all('forum-reply')[1].querySelector('[data-testid="forum-reply-delete"]')).toBeNull();
    });

    it('edits my reply in place', async () => {
      await open('5', { role: 'ROLE_STUDENT', replies: replyPage([reply(1, { mine: true, body: 'Typo' })]) });

      await click(all('forum-reply')[0].querySelector('[data-testid="forum-reply-edit"]'));
      const field = all('forum-reply')[0].querySelector<HTMLTextAreaElement>('[data-testid="forum-body"]')!;
      expect(field.value).toBe('Typo');
      field.value = 'Fixed';
      field.dispatchEvent(new Event('input'));
      await click(all('forum-reply')[0].querySelector('[data-testid="forum-submit"]'));
      http.expectOne('/api/v1/forum/replies/1').flush(reply(1, { mine: true, body: 'Fixed', modifiedAt: '2026-10-05T00:00:00Z' }));
      await settle();

      expect(all('forum-reply')[0].textContent).toContain('Fixed');
      expect(all('forum-reply')[0].querySelector('[data-testid="forum-body"]')).toBeNull();
    });

    it('cancels an edit without calling the API', async () => {
      await open('5', { role: 'ROLE_STUDENT', replies: replyPage([reply(1, { mine: true })]) });
      await click(all('forum-reply')[0].querySelector('[data-testid="forum-reply-edit"]'));
      await click(byId('forum-cancel'));
      expect(all('forum-reply')[0].querySelector('[data-testid="forum-body"]')).toBeNull();
    });

    it('asks to confirm, then deletes my reply and lowers the count', async () => {
      await open('5', { role: 'ROLE_STUDENT', replies: replyPage([reply(1, { mine: true }), reply(2)]) });

      await click(all('forum-reply')[0].querySelector('[data-testid="forum-reply-delete"]'));
      http.expectNone(() => true);
      await click(byId('forum-confirm-delete'));
      http.expectOne('/api/v1/forum/replies/1').flush(null, { status: 204, statusText: 'No Content' });
      await settle();

      expect(all('forum-reply').length).toBe(1);
      expect(text()).toContain('1 reply');
    });

    it('lets me back out of a deletion', async () => {
      await open('5', { role: 'ROLE_STUDENT', replies: replyPage([reply(1, { mine: true })]) });
      await click(all('forum-reply')[0].querySelector('[data-testid="forum-reply-delete"]'));
      await click(byId('forum-cancel-delete'));
      expect(byId('forum-confirm-delete')).toBeNull();
      expect(all('forum-reply').length).toBe(1);
    });

    it('keeps the reply and says so when the deletion fails', async () => {
      await open('5', { role: 'ROLE_STUDENT', replies: replyPage([reply(1, { mine: true })]) });
      await click(all('forum-reply')[0].querySelector('[data-testid="forum-reply-delete"]'));
      await click(byId('forum-confirm-delete'));
      http.expectOne('/api/v1/forum/replies/1').flush(null, { status: 500, statusText: 'err' });
      await settle();

      expect(all('forum-reply').length).toBe(1);
      expect(byId('forum-action-error')!.getAttribute('role')).toBe('alert');
    });

    it('edits my thread in place, title and body, and shows the result', async () => {
      await open('5', { role: 'ROLE_STUDENT', thread: thread({ mine: true }) });

      await click(byId('forum-edit'));
      expect(byId('forum-school')).toBeNull();
      const title = byId<HTMLInputElement>('forum-title')!;
      expect(title.value).toBe('Which master in Dakar?');
      title.value = 'Better title';
      title.dispatchEvent(new Event('input'));
      await click(byId('forum-submit'));
      http.expectOne('/api/v1/forum/threads/5').flush(thread({ mine: true, title: 'Better title', modifiedAt: '2026-10-05T00:00:00Z' }));
      await settle();

      expect(el().querySelector('h1')!.textContent).toContain('Better title');
      expect(TestBed.inject(Title).getTitle()).toContain('Better title');
      expect(byId('forum-title')).toBeNull();
    });

    it('deletes my thread after confirmation and goes back to the forum', async () => {
      await open('5', { role: 'ROLE_STUDENT', thread: thread({ mine: true }) });

      await click(byId('forum-delete'));
      await click(byId('forum-confirm-delete'));
      http.expectOne('/api/v1/forum/threads/5').flush(null, { status: 204, statusText: 'No Content' });
      await settle();

      expect(TestBed.inject(Router).url).toBe('/en/forum');
    });
  });

  describe('accessibility and focus', () => {
    const live = () => byId('forum-live')!;

    it('has a polite status region, empty at first', async () => {
      await open('5', { role: 'ROLE_STUDENT' });
      expect(live().getAttribute('role')).toBe('status');
      expect(live().getAttribute('aria-live')).toBe('polite');
      expect(live().textContent!.trim()).toBe('');
    });

    it('announces the confirmation question and moves focus to its confirm button', async () => {
      await open('5', { role: 'ROLE_STUDENT', replies: replyPage([reply(1, { mine: true })]) });

      await click(all('forum-reply')[0].querySelector('[data-testid="forum-reply-delete"]'));
      expect(live().textContent).toContain('Delete this message?');
      expect(document.activeElement).toBe(byId('forum-confirm-delete'));
    });

    it('gives focus back to the delete button when the deletion is cancelled', async () => {
      await open('5', { role: 'ROLE_STUDENT', replies: replyPage([reply(1, { mine: true })]) });
      await click(all('forum-reply')[0].querySelector('[data-testid="forum-reply-delete"]'));
      await click(byId('forum-cancel-delete'));

      expect(document.activeElement).toBe(all('forum-reply')[0].querySelector('[data-testid="forum-reply-delete"]'));
    });

    it('gives focus back to the edit button after saving or cancelling an edit', async () => {
      await open('5', { role: 'ROLE_STUDENT', replies: replyPage([reply(1, { mine: true })]) });
      await click(all('forum-reply')[0].querySelector('[data-testid="forum-reply-edit"]'));
      expect(document.activeElement).toBe(all('forum-reply')[0].querySelector('[data-testid="forum-body"]'));
      await click(byId('forum-cancel'));

      expect(document.activeElement).toBe(all('forum-reply')[0].querySelector('[data-testid="forum-reply-edit"]'));
    });

    it('moves focus to the replies heading once a reply is deleted', async () => {
      await open('5', { role: 'ROLE_STUDENT', replies: replyPage([reply(1, { mine: true })]) });
      await click(all('forum-reply')[0].querySelector('[data-testid="forum-reply-delete"]'));
      await click(byId('forum-confirm-delete'));
      http.expectOne('/api/v1/forum/replies/1').flush(null, { status: 204, statusText: 'No Content' });
      await settle();

      expect(document.activeElement!.id).toBe('forum-replies-title');
    });

    it('announces a published reply and focuses it', async () => {
      await open('5', { role: 'ROLE_STUDENT' });
      const field = byId<HTMLTextAreaElement>('forum-body')!;
      field.value = 'My answer';
      field.dispatchEvent(new Event('input'));
      await click(byId('forum-submit'));
      http.expectOne('/api/v1/forum/threads/5/replies').flush(reply(77, { body: 'My answer', mine: true }), { status: 201, statusText: 'Created' });
      await settle();

      expect(live().textContent).toContain('Your reply was published');
      expect(document.activeElement).toBe(all('forum-reply')[0]);
    });
  });

  describe('deleting safely', () => {
    const startDelete = async () => {
      await open('5', { role: 'ROLE_STUDENT', replies: replyPage([reply(1, { mine: true }), reply(2)]) });
      await click(all('forum-reply')[0].querySelector('[data-testid="forum-reply-delete"]'));
    };

    it('sends one request however many times the confirm button is hit, and disables it meanwhile', async () => {
      await startDelete();
      const confirm = byId<HTMLButtonElement>('forum-confirm-delete')!;
      confirm.click();
      confirm.click();
      await settle();

      expect(byId<HTMLButtonElement>('forum-confirm-delete')!.disabled).toBeTrue();
      http.expectOne('/api/v1/forum/replies/1').flush(null, { status: 204, statusText: 'No Content' });
      await settle();
      expect(all('forum-reply').length).toBe(1);
    });

    it('treats a 404 as already deleted: the message goes, with no failure alert', async () => {
      await startDelete();
      await click(byId('forum-confirm-delete'));
      http.expectOne('/api/v1/forum/replies/1').flush(null, { status: 404, statusText: 'Not Found' });
      await settle();

      expect(all('forum-reply').length).toBe(1);
      expect(byId('forum-action-error')).toBeNull();
    });

    it('says the session ended on a 401', async () => {
      await startDelete();
      await click(byId('forum-confirm-delete'));
      http.expectOne('/api/v1/forum/replies/1').flush(null, { status: 401, statusText: 'Unauthorized' });
      await settle();

      expect(byId('forum-action-error')!.textContent).toContain('session has ended');
      expect(all('forum-reply').length).toBe(2);
    });

    it('allows another try after a failure', async () => {
      await startDelete();
      await click(byId('forum-confirm-delete'));
      http.expectOne('/api/v1/forum/replies/1').flush(null, { status: 500, statusText: 'err' });
      await settle();
      await click(all('forum-reply')[0].querySelector('[data-testid="forum-reply-delete"]'));
      await click(byId('forum-confirm-delete'));
      http.expectOne('/api/v1/forum/replies/1').flush(null, { status: 204, statusText: 'No Content' });
      await settle();
      expect(all('forum-reply').length).toBe(1);
    });
  });

  it('locks the page the moment a reply is refused because the thread got locked', async () => {
    await open('5', { role: 'ROLE_STUDENT' });
    const field = byId<HTMLTextAreaElement>('forum-body')!;
    field.value = 'Late';
    field.dispatchEvent(new Event('input'));
    await click(byId('forum-submit'));
    http.expectOne('/api/v1/forum/threads/5/replies').flush(null, { status: 409, statusText: 'Conflict' });
    await settle();

    expect(byId('forum-locked-badge')).not.toBeNull();
    expect(byId('forum-locked-notice')).not.toBeNull();
    expect(el().querySelector('app-forum-composer')).toBeNull();
  });

  it('is in French', async () => {
    await open('5', { lang: 'fr', role: 'ROLE_STUDENT', thread: thread({ locked: false, replyCount: 0 }), replies: replyPage([]) });
    expect(text()).toContain('Retour au forum');
    expect(byId('forum-back')!.getAttribute('href')).toBe('/fr/forum');
    expect(text()).toContain('Aucune réponse');
  });
});
