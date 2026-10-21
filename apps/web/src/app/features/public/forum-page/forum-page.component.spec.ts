import { TestBed } from '@angular/core/testing';
import { Component, provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { TokenService } from '@services/token.service';
import { ForumThread } from '@models/forum';
import { ForumPageComponent } from './forum-page.component';

@Component({ template: 'thread' })
class ThreadStubComponent {}

describe('ForumPageComponent', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let user: ReturnType<typeof signal<Record<string, unknown> | null>>;

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

  const page = (content: ForumThread[], extra: Record<string, unknown> = {}) => ({
    content,
    number: 0,
    size: 10,
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

  async function settle() {
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  const threadsRequest = () => http.expectOne((r) => r.url === '/api/v1/forum/threads' && r.method === 'GET');
  const schoolsRequest = () => http.expectOne((r) => r.url === '/api/v1/schools');

  async function open(
    url: string,
    opts: { answer?: unknown | 'skip'; role?: string; status?: string; lang?: 'fr' | 'en'; signedIn?: boolean; schools?: unknown[] } = {},
  ): Promise<void> {
    const signedIn = opts.signedIn ?? opts.role !== undefined;
    user = signal(signedIn ? { id: 1, firstname: 'Me', role: { name: opts.role ?? 'ROLE_STUDENT' }, expertStatus: opts.status } : null);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          {
            path: ':lang',
            children: [
              { path: 'forum', component: ForumPageComponent },
              { path: 'forum/:id', component: ThreadStubComponent },
              { path: 'login', component: ThreadStubComponent },
              { path: 'profile', component: ThreadStubComponent },
            ],
          },
        ]),
        provideTransloco(translocoOptions),
        { provide: TokenService, useValue: { isAuthenticated: () => signedIn, user } },
      ],
    });
    await firstValueFrom(TestBed.inject(LocaleService).use(opts.lang ?? 'en'));
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl(`/${opts.lang ?? 'en'}${url}`, ForumPageComponent);
    if (opts.answer !== 'skip') {
      await Promise.resolve();
      answerThreads(threadsRequest(), opts.answer ?? page([thread()]));
      schoolsRequest().flush(page((opts.schools ?? [{ id: 3, name: 'ISI Dakar' }, { id: 4, name: 'UCAD' }]) as never));
    }
    await navigation;
    await settle();
  }

  function answerThreads(req: TestRequest, answer: unknown) {
    if (answer && typeof answer === 'object' && 'status' in (answer as object)) {
      req.flush({ message: 'x' }, { status: (answer as { status: number }).status, statusText: 'err' });
    } else {
      req.flush(answer as object);
    }
  }

  afterEach(() => http.verify());

  it('asks for the newest threads, ten per page, with no school filter by default', async () => {
    await open('/forum', { answer: 'skip' });
    const req = threadsRequest();
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.has('schoolId')).toBeFalse();
    req.flush(page([]));
    schoolsRequest().flush(page([]));
    await settle();
  });

  it('shows a loading state before the answer', async () => {
    await open('/forum', { answer: 'skip' });
    expect(byId('forum-loading')).not.toBeNull();
    threadsRequest().flush(page([]));
    schoolsRequest().flush(page([]));
    await settle();
    expect(byId('forum-loading')).toBeNull();
  });

  it('lists each thread with title, excerpt, author, role, school, reply count and a link to it', async () => {
    await open('/forum', { answer: page([thread({ schoolId: 3, schoolName: 'ISI Dakar' })]) });

    const row = all('forum-thread')[0];
    expect(row.textContent).toContain('Which master in Dakar?');
    expect(row.textContent).toContain('I am looking for a master.');
    expect(row.textContent).toContain('Awa');
    expect(row.textContent).toContain('Student');
    expect(row.textContent).toContain('ISI Dakar');
    expect(row.textContent).toContain('2 replies');
    expect(row.querySelector('a')!.getAttribute('href')).toBe('/en/forum/5');
  });

  it('uses the singular for one reply and shows a locked badge', async () => {
    await open('/forum', { answer: page([thread({ replyCount: 1, locked: true })]) });
    expect(text()).toContain('1 reply');
    expect(byId('forum-locked-badge')).not.toBeNull();
  });

  it('shows the deleted-user text, from a translation key, when the author is gone', async () => {
    await open('/forum', { answer: page([thread({ authorName: undefined, authorRole: undefined, authorDeleted: true })]) });
    expect(all('forum-thread')[0].textContent).toContain('Deleted user');
  });

  it('is in French too', async () => {
    await open('/forum', { lang: 'fr', answer: page([thread({ authorName: undefined, authorRole: undefined, authorDeleted: true, replyCount: 0 })]) });
    expect(text()).toContain('Utilisateur supprimé');
    expect(text()).toContain('0 réponse');
    expect(byId('forum-thread-link')!.getAttribute('href')).toBe('/fr/forum/5');
  });

  it('strips hidden characters from an author name before showing it, keeping accents and CJK', async () => {
    await open('/forum', {
      answer: page([
        thread({ id: 1, authorName: '‮Aï​ssaㅤ' }),
        thread({ id: 2, authorName: '山田' }),
        thread({ id: 3, authorName: '​ㅤ' }),
      ]),
    });

    const authors = all('forum-author-name').map((n) => n.textContent!.trim());
    expect(authors).toEqual(['Aïssa', '山田', 'Member']);
  });

  it('shows the empty state, and a different one when a school filter is on', async () => {
    await open('/forum', { answer: page([]) });
    expect(byId('forum-empty')!.textContent).toContain('No discussion yet');
  });

  it('shows the empty state of a filter, naming no school that does not exist', async () => {
    await open('/forum?school=3', { answer: page([]) });
    expect(byId('forum-empty-filtered')).not.toBeNull();
  });

  it('shows an error state with a retry that asks again, never fake threads', async () => {
    await open('/forum', { answer: { status: 500 } });

    expect(el().querySelector('app-error-state')).not.toBeNull();
    expect(all('forum-thread').length).toBe(0);
    (el().querySelector('app-error-state button') as HTMLButtonElement).click();
    answerThreads(threadsRequest(), page([thread()]));
    await settle();

    expect(el().querySelector('app-error-state')).toBeNull();
    expect(all('forum-thread').length).toBe(1);
  });

  it('displays titles, excerpts and names as text: no element is created from markup', async () => {
    await open('/forum', {
      answer: page([
        thread({
          title: '<img src=x onerror="window.pwned=1">',
          body: '<script>window.pwned=1</script><b>bold</b>',
          authorName: '<img src=y onerror="window.pwned=1">',
        }),
      ]),
    });

    expect(el().querySelector('img')).toBeNull();
    expect(el().querySelector('script')).toBeNull();
    expect(el().querySelector('b')).toBeNull();
    expect(text()).toContain('<img src=x onerror="window.pwned=1">');
    expect(text()).toContain('<script>window.pwned=1</script>');
    expect((window as unknown as { pwned?: number }).pwned).toBeUndefined();
  });

  it('breaks long words and keeps line breaks in the excerpt so nothing overflows a 375 px screen', async () => {
    await open('/forum', { answer: page([thread({ title: 'x'.repeat(200) })]) });

    const row = all('forum-thread')[0];
    expect(row.querySelector('h2, h3')!.className).toContain('break-words');
    expect(row.querySelector('[data-testid="forum-excerpt"]')!.className).toMatch(/whitespace-pre-line/);
    expect(row.querySelector('[data-testid="forum-excerpt"]')!.className).toContain('break-words');
  });

  it('lets the browser pick the direction of each user text and keeps a long name inside its box', async () => {
    await open('/forum', { answer: page([thread()]) });

    const row = all('forum-thread')[0];
    expect(row.querySelector('h2')!.getAttribute('dir')).toBe('auto');
    expect(row.querySelector('[data-testid="forum-excerpt"]')!.getAttribute('dir')).toBe('auto');
    const name = row.querySelector('[data-testid="forum-author-name"]')!;
    expect(name.getAttribute('dir')).toBe('auto');
    expect(name.className).toContain('overflow-hidden');
  });

  it('flags a hidden thread, which only an administrator receives', async () => {
    await open('/forum', { role: 'ROLE_ADMIN', answer: page([thread({ hidden: true }), thread({ id: 6 })]) });

    expect(all('forum-hidden-badge').length).toBe(1);
    expect(all('forum-thread')[0].querySelector('[data-testid="forum-hidden-badge"]')!.textContent).toContain('Hidden');
  });

  it('does not say the forum is empty on a page past the end: it offers the last page', async () => {
    await open('/forum?page=50', { answer: 'skip' });
    const req = threadsRequest();
    expect(req.request.params.get('page')).toBe('50');
    req.flush(page([], { number: 50, totalPages: 3, totalElements: 25, first: false, last: true }));
    schoolsRequest().flush(page([]));
    await settle();

    expect(byId('forum-empty')).toBeNull();
    expect(byId('forum-empty-filtered')).toBeNull();
    expect(byId('forum-out-of-range')).not.toBeNull();
    expect(byId('forum-last-page')!.getAttribute('href')).toBe('/en/forum?page=2');
  });

  describe('pagination', () => {
    it('shows page numbers and next/previous that move through the URL', async () => {
      await open('/forum', { answer: page([thread()], { totalPages: 3, totalElements: 25, first: true, last: false }) });

      expect(byId('forum-page-info')!.textContent).toContain('1');
      expect(byId('forum-page-info')!.textContent).toContain('3');
      expect(byId('forum-prev')!.getAttribute('aria-disabled')).toBe('true');
      const next = byId<HTMLAnchorElement>('forum-next')!;
      expect(next.getAttribute('href')).toBe('/en/forum?page=1');

      next.click();
      await Promise.resolve();
      const req = threadsRequest();
      expect(req.request.params.get('page')).toBe('1');
      req.flush(page([thread({ id: 6 })], { number: 1, totalPages: 3, first: false, last: false }));
      await settle();
      expect(byId('forum-prev')!.getAttribute('aria-disabled')).toBe('false');
    });

    it('opens on the page named in the URL, and falls back to the first for junk', async () => {
      await open('/forum?page=2', { answer: 'skip' });
      const req = threadsRequest();
      expect(req.request.params.get('page')).toBe('2');
      req.flush(page([thread()], { number: 2, totalPages: 3, first: false, last: true }));
      schoolsRequest().flush(page([]));
      await settle();
      expect(byId('forum-next')!.getAttribute('aria-disabled')).toBe('true');
    });

    it('ignores a page parameter that is not a number', async () => {
      await open('/forum?page=-4abc', { answer: 'skip' });
      const req = threadsRequest();
      expect(req.request.params.get('page')).toBe('0');
      req.flush(page([]));
      schoolsRequest().flush(page([]));
      await settle();
    });

    it('hides the pager when everything fits on one page', async () => {
      await open('/forum');
      expect(byId('forum-next')).toBeNull();
    });
  });

  describe('school filter', () => {
    it('lists the schools in a select and filters threads by the one picked, back to page one', async () => {
      await open('/forum?page=1', { answer: page([thread()], { number: 1, totalPages: 2, first: false }) });

      const select = byId<HTMLSelectElement>('forum-filter')!;
      expect(Array.from(select.options).map((o) => o.textContent!.trim())).toEqual(['All schools', 'ISI Dakar', 'UCAD']);
      select.value = '4';
      select.dispatchEvent(new Event('change'));
      await Promise.resolve();

      const req = threadsRequest();
      expect(req.request.params.get('schoolId')).toBe('4');
      expect(req.request.params.get('page')).toBe('0');
      req.flush(page([]));
      await settle();
      expect(TestBed.inject(Router).url).toBe('/en/forum?school=4');
    });

    it('opens already filtered when the URL names a school', async () => {
      await open('/forum?school=3', { answer: 'skip' });
      const req = threadsRequest();
      expect(req.request.params.get('schoolId')).toBe('3');
      req.flush(page([]));
      schoolsRequest().flush(page([{ id: 3, name: 'ISI Dakar' }] as never));
      await settle();
      expect(byId<HTMLSelectElement>('forum-filter')!.value).toBe('3');
    });

    it('adds the filtered school to the select when it is not among the schools listed, never showing "All"', async () => {
      await open('/forum?school=999', { answer: 'skip' });
      threadsRequest().flush(page([]));
      schoolsRequest().flush(page([{ id: 3, name: 'ISI Dakar' }] as never));
      await settle();
      http.expectOne('/api/v1/schools/999').flush({ id: 999, name: 'Far School' });
      await settle();

      const select = byId<HTMLSelectElement>('forum-filter')!;
      expect(select.value).toBe('999');
      expect(select.selectedOptions[0].textContent!.trim()).toBe('Far School');
    });

    it('falls back to a numbered option when that school cannot be read', async () => {
      await open('/forum?school=999', { answer: 'skip' });
      threadsRequest().flush(page([]));
      schoolsRequest().flush(page([{ id: 3, name: 'ISI Dakar' }] as never));
      await settle();
      http.expectOne('/api/v1/schools/999').flush(null, { status: 404, statusText: 'x' });
      await settle();

      expect(byId<HTMLSelectElement>('forum-filter')!.value).toBe('999');
    });

    it('ignores a school parameter that is not a number', async () => {
      await open('/forum?school=abc', { answer: 'skip' });
      expect(threadsRequest().request.params.has('schoolId')).toBeFalse();
      http.match(() => true).forEach((r) => r.flush(page([])));
      await settle();
    });

    it('still lists the threads when the schools could not be loaded, without the filter', async () => {
      await open('/forum', { answer: 'skip' });
      threadsRequest().flush(page([thread()]));
      schoolsRequest().flush(null, { status: 500, statusText: 'err' });
      await settle();

      expect(all('forum-thread').length).toBe(1);
      expect(byId('forum-filter')).toBeNull();
    });
  });

  describe('who can write', () => {
    it('invites an anonymous visitor to sign in, coming back here, and offers no form', async () => {
      await open('/forum');

      const gate = byId('forum-gate')!;
      expect(gate.textContent).toContain('Sign in');
      const link = gate.querySelector('a')!;
      expect(link.getAttribute('href')).toBe('/en/login?returnUrl=%2Fen%2Fforum');
      expect(byId('forum-new')).toBeNull();
    });

    it('keeps the sign-in return address in step with the page being read', async () => {
      await open('/forum', { answer: page([thread()], { totalPages: 3, first: true, last: false }) });

      byId<HTMLAnchorElement>('forum-next')!.click();
      await Promise.resolve();
      threadsRequest().flush(page([thread()], { number: 1, totalPages: 3, first: false, last: false }));
      await settle();

      expect(byId('forum-gate')!.querySelector('a')!.getAttribute('href')).toBe('/en/login?returnUrl=%2Fen%2Fforum%3Fpage%3D1');
    });

    it('lets a student open the thread form, which creates a thread then goes to it', async () => {
      await open('/forum', { role: 'ROLE_STUDENT' });

      expect(byId('forum-gate')).toBeNull();
      expect(el().querySelector('app-forum-composer')).toBeNull();
      byId<HTMLButtonElement>('forum-new')!.click();
      await settle();
      expect(byId('forum-school')!.querySelectorAll('option').length).toBe(3);

      const type = (id: string, value: string) => {
        const f = byId<HTMLInputElement>(id)!;
        f.value = value;
        f.dispatchEvent(new Event('input'));
      };
      type('forum-title', 'New one');
      type('forum-body', 'Body');
      byId<HTMLButtonElement>('forum-submit')!.click();
      await Promise.resolve();
      http.expectOne('/api/v1/forum/threads').flush(thread({ id: 42 }), { status: 201, statusText: 'Created' });
      await settle();

      expect(TestBed.inject(Router).url).toBe('/en/forum/42');
    });

    it('lets an approved expert write too', async () => {
      await open('/forum', { role: 'ROLE_EXPERT', status: 'APPROVED' });
      expect(byId('forum-new')).not.toBeNull();
    });

    it('tells a pending expert why they cannot write, with a link to their status', async () => {
      await open('/forum', { role: 'ROLE_EXPERT', status: 'PENDING' });

      expect(byId('forum-new')).toBeNull();
      const gate = byId('forum-gate')!;
      expect(gate.textContent).toContain('awaiting approval');
      expect(gate.querySelector('a')!.getAttribute('href')).toBe('/en/profile');
    });

    it('tells a rejected expert why they cannot write', async () => {
      await open('/forum', { role: 'ROLE_EXPERT', status: 'REJECTED' });

      expect(byId('forum-new')).toBeNull();
      expect(byId('forum-gate')!.textContent).toContain('rejected');
    });

    it('shows an administrator the forum read-only', async () => {
      await open('/forum', { role: 'ROLE_ADMIN' });

      expect(byId('forum-new')).toBeNull();
      expect(byId('forum-gate')!.textContent).toContain('read-only');
      expect(all('forum-thread').length).toBe(1);
    });
  });
});
