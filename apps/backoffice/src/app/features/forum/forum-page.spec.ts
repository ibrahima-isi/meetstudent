import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ForumPage } from './forum-page';
import { API_URL } from '@services/api-config';
import { Page } from '@models/entities';
import { ForumReport } from '@models/forum';

const api = 'http://api.test/api/v1';
const admin = `${api}/admin/forum`;

const report = (id: number, extra: Partial<ForumReport> = {}): ForumReport => ({
  id,
  targetType: 'THREAD',
  targetId: 100 + id,
  threadId: 100 + id,
  threadTitle: `Titre ${id}`,
  excerpt: `Texte ${id}`,
  reportedExcerpt: `Texte ${id}`,
  targetModifiedAt: null,
  targetHidden: false,
  reason: `Motif ${id}`,
  status: 'OPEN',
  reporterName: `Awa${id}`,
  createdAt: '2026-03-15T12:00:00',
  handledAt: null,
  ...extra,
});

const reply = (id: number, extra: Partial<ForumReport> = {}) =>
  report(id, { targetType: 'REPLY', targetId: 500 + id, threadId: 100 + id, ...extra });

const page = (content: ForumReport[], number = 0, totalPages = 1, totalElements = content.length): Page<ForumReport> => ({
  content,
  number,
  totalPages,
  totalElements,
  size: 10,
});

describe('ForumPage', () => {
  let fixture: ComponentFixture<ForumPage>;
  let backend: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement>(sel: string) => el().querySelector<T>(sel);
  const all = (sel: string) => Array.from(el().querySelectorAll<HTMLElement>(sel));
  const settle = () => fixture.detectChanges();
  const click = (sel: string) => {
    q<HTMLButtonElement>(sel)!.click();
    settle();
  };
  const listCall = (status: string) =>
    backend.expectOne((r) => r.url === `${admin}/reports` && r.method === 'GET' && r.params.get('status') === status);
  const load = (status: string, content: ForumReport[], number = 0, totalPages = 1, total = content.length) => {
    listCall(status).flush(page(content, number, totalPages, total));
    settle();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: api }],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ForumPage);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('has a title, loads the open reports first and shows target, reason, reporter and date', () => {
    expect(q('h1')!.textContent).toContain('Forum');
    expect(el().textContent).toContain('Chargement');
    const req = listCall('OPEN');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    req.flush(page([report(1), reply(2)]));
    settle();

    expect(el().textContent).not.toContain('Chargement');
    const rows = all('[data-report]');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Titre 1');
    expect(rows[0].textContent).toContain('Motif 1');
    expect(rows[0].textContent).toContain('Awa1');
    expect(rows[0].querySelector('time')!.textContent).toMatch(/15 mars 2026/);
    expect(rows[0].textContent).toContain('Fil');
    expect(rows[1].textContent).toContain('Réponse');
  });

  it('shows the reported excerpt and the current one, as multi-line text with automatic direction', () => {
    load('OPEN', [report(1, { reportedExcerpt: 'Avant\nligne', excerpt: 'Après\nligne' })]);
    const reported = q('[data-reported-excerpt]')!;
    const current = q('[data-current-excerpt]')!;
    expect(reported.textContent).toContain('Avant');
    expect(current.textContent).toContain('Après');
    for (const node of [reported, current]) {
      expect(node.getAttribute('dir')).toBe('auto');
      expect(node.className).toContain('whitespace-pre-line');
    }
  });

  it('warns when the message was modified since the report (date)', () => {
    load('OPEN', [report(1, { targetModifiedAt: '2026-03-16T12:00:00' }), report(2, { targetModifiedAt: '2026-03-10T12:00:00' })]);
    const rows = all('[data-report]');
    expect(rows[0].querySelector('[data-modified-warning]')!.textContent).toContain('modifié depuis le signalement');
    expect(rows[1].querySelector('[data-modified-warning]')).toBeNull();
  });

  it('tells to compare the excerpts only when they differ; otherwise says the change is elsewhere (title or rest of the text)', () => {
    load('OPEN', [
      report(1, { excerpt: 'Changé', reportedExcerpt: 'Original' }),
      report(2, { targetModifiedAt: '2026-03-16T12:00:00' }),
    ]);
    const [differs, same] = all('[data-modified-warning]');
    expect(differs.textContent).toContain('comparez les deux extraits');
    expect(same.textContent).not.toContain('comparez les deux extraits');
    expect(same.textContent).toContain('extraits sont identiques');
    expect(same.textContent).toContain('titre');
  });

  it('isolates the user-written title, reason and first name for bidirectional text', () => {
    load('OPEN', [report(1, { threadTitle: 'مرحبا', reason: 'سبب', reporterName: 'علي' })]);
    const row = q('[data-report]')!;
    const bdis = Array.from(row.querySelectorAll('bdi')).map((b) => b.textContent);
    expect(bdis).toEqual(jasmine.arrayContaining(['مرحبا', 'سبب', 'علي']));
  });

  it('warns when the current excerpt differs from the reported one, and not otherwise', () => {
    load('OPEN', [report(1, { excerpt: 'Changé', reportedExcerpt: 'Original' }), report(2)]);
    const rows = all('[data-report]');
    expect(rows[0].querySelector('[data-modified-warning]')).not.toBeNull();
    expect(rows[1].querySelector('[data-modified-warning]')).toBeNull();
  });

  it('has the three tabs, and a tab loads its status from page 0', () => {
    load('OPEN', [report(1)], 0, 2, 11);
    expect(all('[role="tab"]').map((t) => t.textContent!.trim())).toEqual(['Ouverts', 'Traités', 'Rejetés']);
    click('[data-tab="DISMISSED"]');
    const req = listCall('DISMISSED');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([report(5, { status: 'DISMISSED', handledAt: '2026-03-17T12:00:00' })]));
    settle();
    expect(q('[data-tab="DISMISSED"]')!.getAttribute('aria-selected')).toBe('true');
    expect(q('[data-tab="OPEN"]')!.getAttribute('aria-selected')).toBe('false');
    expect(q('[data-report] [data-handled-at]')!.textContent).toMatch(/17 mars 2026/);
  });

  it('renders every user-controlled field as plain text and creates no element from it', () => {
    const evil = '<img src=x onerror=alert(1)>';
    load('OPEN', [
      report(1, {
        threadTitle: evil + '<script>t()</script>',
        excerpt: evil + '<script>e()</script>',
        reportedExcerpt: '<b>old</b><script>r()</script>',
        reason: evil + '<script>m()</script>',
        reporterName: '<img src=y onerror=alert(2)><script>p()</script>',
      }),
    ]);
    const row = q('[data-report]')!;
    expect(row.querySelector('img, script, b')).toBeNull();
    expect(el().querySelector('script')).toBeNull();
    expect(row.textContent).toContain(evil + '<script>t()</script>');
    expect(row.textContent).toContain(evil + '<script>e()</script>');
    expect(row.textContent).toContain('<b>old</b><script>r()</script>');
    expect(row.textContent).toContain(evil + '<script>m()</script>');
    expect(row.textContent).toContain('<img src=y onerror=alert(2)><script>p()</script>');
  });

  it('says so when the reporter account no longer exists', () => {
    load('OPEN', [report(1, { reporterName: null })]);
    expect(q('[data-report]')!.textContent).toContain('compte supprimé');
  });

  it('shows an empty state', () => {
    load('OPEN', []);
    expect(el().textContent).toContain('Aucun signalement ouvert');
    expect(q('[data-report]')).toBeNull();
  });

  it('shows an error with a retry that reloads the same tab', () => {
    listCall('OPEN').flush(null, { status: 500, statusText: 'err' });
    settle();
    expect(q('[role="alert"]')!.textContent).toContain('Impossible de charger les signalements');
    expect(el().textContent).not.toContain('Aucun signalement');
    click('[data-retry]');
    load('OPEN', [report(1)]);
    expect(q('[data-retry]')).toBeNull();
    expect(all('[data-report]').length).toBe(1);
  });

  it('paginates with previous / next', () => {
    load('OPEN', [report(1)], 0, 3, 25);
    expect(el().textContent).toContain('Page 1 / 3');
    expect(q<HTMLButtonElement>('[data-prev]')!.disabled).toBeTrue();
    click('[data-next]');
    const req = listCall('OPEN');
    expect(req.request.params.get('page')).toBe('1');
    req.flush(page([report(2)], 1, 3, 25));
    settle();
    expect(el().textContent).toContain('Page 2 / 3');
  });

  describe('stale rows and guards', () => {
    const cmp = () => fixture.componentInstance;

    it('drops the rows of the previous tab at once, with a coherent page counter', () => {
      load('OPEN', [report(1)], 0, 3, 25);
      click('[data-tab="HANDLED"]');
      expect(q('[data-report]')).toBeNull();
      expect(el().textContent).toContain('Page 1 / 1 · 0 signalement(s)');
      load('HANDLED', [report(2, { status: 'HANDLED' })]);
    });

    it('drops the rows on a load error and Réessayer asks for the requested page, not the last successful one', () => {
      load('OPEN', [report(1)], 0, 3, 25);
      click('[data-next]');
      listCall('OPEN').flush(null, { status: 500, statusText: 'err' });
      settle();
      expect(q('[data-report]')).toBeNull();
      click('[data-retry]');
      const req = listCall('OPEN');
      expect(req.request.params.get('page')).toBe('1');
      req.flush(page([report(2)], 1, 3, 25));
      settle();
    });

    it('disables every row action while a page is loading', () => {
      load('OPEN', [report(1)], 0, 3, 25);
      click('[data-next]');
      for (const sel of ['[data-hide]', '[data-lock]', '[data-unlock]', '[data-delete]', '[data-handle]', '[data-dismiss]']) {
        expect(q<HTMLButtonElement>(sel)!.disabled).withContext(sel).toBeTrue();
      }
      listCall('OPEN').flush(page([report(2)], 1, 3, 25));
      settle();
      expect(q<HTMLButtonElement>('[data-hide]')!.disabled).toBeFalse();
    });

    it('disables the pagination and ignores a tab change while an action is in flight', () => {
      load('OPEN', [report(1)], 0, 3, 25);
      click('[data-next]');
      load('OPEN', [report(1), report(9)], 1, 3, 25);
      click('[data-handle]');
      expect(q<HTMLButtonElement>('[data-prev]')!.disabled).toBeTrue();
      expect(q<HTMLButtonElement>('[data-next]')!.disabled).toBeTrue();
      cmp().select('HANDLED');
      expect(cmp().status()).toBe('OPEN');
      backend.expectOne(`${admin}/reports/1/handle`).flush(report(1));
      settle();
      const req = listCall('OPEN');
      expect(req.request.params.get('page')).toBe('1');
      req.flush(page([report(2)], 1, 3, 24));
    });

    it('sends one call when an action is triggered twice (guard, not just the disabled button)', () => {
      load('OPEN', [report(1)]);
      const item = cmp().items()[0];
      cmp().act(item, 'handle');
      cmp().act(item, 'handle');
      expect(backend.match(`${admin}/reports/1/handle`).length).toBe(1);
    });

    it('refuses an action while a page is loading (direct call, not a disabled button)', () => {
      load('OPEN', [report(1)], 0, 3, 25);
      const item = cmp().items()[0];
      click('[data-next]');
      expect(cmp().loading()).toBeTrue();
      cmp().act(item, 'hide');
      backend.expectNone(`${admin}/threads/101/hide`);
      listCall('OPEN').flush(page([report(2)], 1, 3, 25));
      settle();
    });

    it('goes back to the last existing page when the reloaded page is empty and the page count shrank', () => {
      load('OPEN', [report(1)], 0, 3, 25);
      click('[data-next]');
      listCall('OPEN').flush(page([report(2)], 1, 3, 25));
      settle();
      click('[data-next]');
      const beyond = listCall('OPEN');
      expect(beyond.request.params.get('page')).toBe('2');
      beyond.flush(page([], 2, 2, 15));
      settle();
      const back = listCall('OPEN');
      expect(back.request.params.get('page')).toBe('1');
      back.flush(page([report(3)], 1, 2, 15));
      settle();
      expect(el().textContent).toContain('Page 2 / 2');
      expect(el().textContent).not.toContain('Page 3');
    });

    it('does not delete without the confirmation of that very report', () => {
      load('OPEN', [report(1), report(2)]);
      const [first, second] = cmp().items();
      cmp().confirmDelete(first);
      backend.expectNone(`${admin}/threads/101`);
      cmp().askDelete(first);
      cmp().confirmDelete(second);
      backend.expectNone(`${admin}/threads/102`);
      expect(cmp().acting()).toBeFalse();
    });

    it('keeps the answer of the newest request when an older one answers last', () => {
      load('OPEN', [report(1)], 0, 3, 25);
      cmp().load(1);
      cmp().load(2);
      const [older, newer] = backend.match((r) => r.url === `${admin}/reports` && r.method === 'GET');
      expect(older.request.params.get('page')).toBe('1');
      expect(newer.request.params.get('page')).toBe('2');
      newer.flush(page([report(3, { threadTitle: 'Récent' })], 2, 3, 25));
      older.flush(page([report(2, { threadTitle: 'Ancien' })], 1, 3, 25));
      settle();
      expect(q('[data-report]')!.textContent).toContain('Récent');
      expect(el().textContent).not.toContain('Ancien');
      expect(el().textContent).toContain('Page 3 / 3');
    });
  });

  describe('actions', () => {
    it('offers close and dismiss only on open reports', () => {
      load('OPEN', [report(1)]);
      expect(q('[data-handle]')).not.toBeNull();
      expect(q('[data-dismiss]')).not.toBeNull();
      click('[data-tab="HANDLED"]');
      load('HANDLED', [report(2, { status: 'HANDLED' })]);
      expect(q('[data-handle]')).toBeNull();
      expect(q('[data-dismiss]')).toBeNull();
      expect(q('[data-hide]')).not.toBeNull();
      expect(q('[data-delete]')).not.toBeNull();
    });

    it('closes a report as handled then reloads the list', () => {
      load('OPEN', [report(1), report(2)]);
      click('[data-handle]');
      const req = backend.expectOne(`${admin}/reports/1/handle`);
      expect(req.request.method).toBe('POST');
      req.flush(report(1, { status: 'HANDLED' }));
      settle();
      expect(q('[data-notice]')!.textContent).toContain('Signalement clos');
      load('OPEN', [report(2)]);
      expect(all('[data-report]').length).toBe(1);
    });

    it('dismisses a report', () => {
      load('OPEN', [report(1)]);
      click('[data-dismiss]');
      backend.expectOne(`${admin}/reports/1/dismiss`).flush(report(1, { status: 'DISMISSED' }));
      settle();
      expect(q('[data-notice]')!.textContent).toContain('Signalement rejeté');
      load('OPEN', []);
    });

    it('hides a thread and a reply through their own endpoints', () => {
      load('OPEN', [report(1), reply(2)]);
      const [first] = all('[data-report]');
      first.querySelector<HTMLButtonElement>('[data-hide]')!.click();
      settle();
      const hideThread = backend.expectOne(`${admin}/threads/101/hide`);
      expect(hideThread.request.method).toBe('POST');
      expect(hideThread.request.body).toBeNull();
      hideThread.flush({ id: 101, hidden: true });
      settle();
      load('OPEN', [report(1, { targetHidden: true }), reply(2)]);
      all('[data-report]')[1].querySelector<HTMLButtonElement>('[data-hide]')!.click();
      settle();
      const hideReply = backend.expectOne(`${admin}/replies/502/hide`);
      expect(hideReply.request.method).toBe('POST');
      expect(hideReply.request.body).toBeNull();
      hideReply.flush({ id: 502, hidden: true });
      settle();
      load('OPEN', [report(1, { targetHidden: true }), reply(2, { targetHidden: true })]);
    });

    it('offers Démasquer on a hidden message and shows a hidden badge', () => {
      load('OPEN', [reply(1, { targetHidden: true })]);
      expect(q('[data-hide]')).toBeNull();
      expect(q('[data-hidden-badge]')).not.toBeNull();
      click('[data-unhide]');
      backend.expectOne(`${admin}/replies/501/unhide`).flush({ id: 501, hidden: false });
      settle();
      load('OPEN', [reply(1)]);
      expect(q('[data-hidden-badge]')).toBeNull();
    });

    it('unhides a thread', () => {
      load('OPEN', [report(1, { targetHidden: true })]);
      click('[data-unhide]');
      const unhide = backend.expectOne(`${admin}/threads/101/unhide`);
      expect(unhide.request.method).toBe('POST');
      expect(unhide.request.body).toBeNull();
      unhide.flush({ id: 101, hidden: false });
      settle();
      load('OPEN', [report(1)]);
    });

    it('locks and unlocks only when the target is a thread', () => {
      load('OPEN', [reply(1), report(2)]);
      const [r, t] = all('[data-report]');
      expect(r.querySelector('[data-lock]')).toBeNull();
      expect(r.querySelector('[data-unlock]')).toBeNull();
      t.querySelector<HTMLButtonElement>('[data-lock]')!.click();
      settle();
      backend.expectOne(`${admin}/threads/102/lock`).flush({ id: 102, hidden: false, locked: true });
      settle();
      expect(q('[data-notice]')!.textContent).toContain('verrouillé');
      load('OPEN', [reply(1), report(2)]);
      all('[data-report]')[1].querySelector<HTMLButtonElement>('[data-unlock]')!.click();
      settle();
      backend.expectOne(`${admin}/threads/102/unlock`).flush({ id: 102, hidden: false, locked: false });
      settle();
      load('OPEN', [reply(1), report(2)]);
    });

    it('never offers to edit the text of someone else', () => {
      load('OPEN', [report(1)]);
      expect(q('[data-edit]')).toBeNull();
      expect(q('textarea')).toBeNull();
    });
  });

  describe('deletion', () => {
    it('asks for confirmation, naming the consequence, and cancelling sends nothing', () => {
      load('OPEN', [report(1)]);
      click('[data-delete]');
      expect(q('[data-confirm-delete]')).not.toBeNull();
      expect(q('[data-delete-warning]')!.textContent).toContain('définitivement');
      click('[data-cancel]');
      expect(q('[data-confirm-delete]')).toBeNull();
      backend.expectNone(`${admin}/threads/101`);
    });

    it('deletes a thread after confirmation, then reloads', () => {
      load('OPEN', [report(1), report(2)]);
      click('[data-delete]');
      click('[data-confirm-delete]');
      const req = backend.expectOne(`${admin}/threads/101`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });
      settle();
      expect(q('[data-notice]')!.textContent).toContain('supprimé');
      load('OPEN', [report(2)]);
      expect(all('[data-report]').length).toBe(1);
    });

    it('deletes a reply through the replies endpoint', () => {
      load('OPEN', [reply(1)]);
      click('[data-delete]');
      click('[data-confirm-delete]');
      const del = backend.expectOne(`${admin}/replies/501`);
      expect(del.request.method).toBe('DELETE');
      del.flush(null, { status: 204, statusText: 'No Content' });
      settle();
      load('OPEN', []);
    });

    it('steps back a page after deleting the only row of the last page', () => {
      load('OPEN', [report(1)], 0, 2, 11);
      click('[data-next]');
      listCall('OPEN').flush(page([report(11)], 1, 2, 11));
      settle();
      click('[data-delete]');
      click('[data-confirm-delete]');
      backend.expectOne(`${admin}/threads/111`).flush(null, { status: 204, statusText: 'No Content' });
      settle();
      const req = listCall('OPEN');
      expect(req.request.params.get('page')).toBe('0');
      req.flush(page([report(1)], 0, 1, 10));
    });
  });

  describe('errors', () => {
    const failHide = (status: number, body: object = { message: 'raw <b>x</b>' }, headers = {}) => {
      load('OPEN', [report(1)]);
      click('[data-hide]');
      backend.expectOne(`${admin}/threads/101/hide`).flush(body, { status, statusText: 'err', headers });
      settle();
    };

    it('translates a 404 and reloads the list', () => {
      failHide(404);
      load('OPEN', []);
      expect(q('[role="alert"]')!.textContent).toContain('introuvable');
      expect(el().textContent).not.toContain('raw');
    });

    // The admin endpoints are idempotent and not rate limited: a 409 or a 429 is not expected and reads as a generic failure.
    it('reads an unexpected 409 or 429 as a generic failure and keeps the buttons usable', () => {
      failHide(409);
      expect(q('[role="alert"]')!.textContent).toContain("L'action n'a pas pu être enregistrée");
      expect(q<HTMLButtonElement>('[data-hide]')!.disabled).toBeFalse();
      click('[data-hide]');
      backend.expectOne(`${admin}/threads/101/hide`).flush({}, { status: 429, statusText: 'err' });
      settle();
      expect(q('[role="alert"]')!.textContent).toContain("L'action n'a pas pu être enregistrée");
    });

    it('clears the previous success message when an action fails', () => {
      load('OPEN', [report(1)]);
      click('[data-hide]');
      backend.expectOne(`${admin}/threads/101/hide`).flush({ id: 101, hidden: true });
      settle();
      load('OPEN', [report(1, { targetHidden: true })]);
      expect(q('[data-notice]')!.textContent).toContain('masqué');
      click('[data-unhide]');
      backend.expectOne(`${admin}/threads/101/unhide`).flush({}, { status: 500, statusText: 'err' });
      settle();
      expect(q('[data-notice]')!.textContent!.trim()).toBe('');
    });

    it('translates a 403 and a 401', () => {
      failHide(403);
      expect(q('[role="alert"]')!.textContent).toContain("droits nécessaires");
      click('[data-hide]');
      backend.expectOne(`${admin}/threads/101/hide`).flush({}, { status: 401, statusText: 'err' });
      settle();
      expect(q('[role="alert"]')!.textContent).toContain('session a expiré');
    });

    it('falls back to a generic French message', () => {
      failHide(500, { message: 'boom' });
      expect(q('[role="alert"]')!.textContent).toContain("L'action n'a pas pu être enregistrée");
      expect(el().textContent).not.toContain('boom');
    });

    it('keeps the confirmation open and reports a failed delete', () => {
      load('OPEN', [report(1)]);
      click('[data-delete]');
      click('[data-confirm-delete]');
      backend.expectOne(`${admin}/threads/101`).flush({}, { status: 500, statusText: 'err' });
      settle();
      expect(q('[role="alert"]')!.textContent).toContain("L'action n'a pas pu être enregistrée");
      expect(q<HTMLButtonElement>('[data-confirm-delete]')!.disabled).toBeFalse();
    });
  });

  describe('accessibility', () => {
    const active = () => document.activeElement;

    it('focuses the cancel button when asking to delete, and gives the focus back to Supprimer on cancel', () => {
      load('OPEN', [report(1)]);
      click('[data-delete]');
      expect(active()).toBe(q('[data-cancel]'));
      click('[data-cancel]');
      expect(active()).toBe(q('[data-delete]'));
    });

    it('focuses the status message after an action', () => {
      load('OPEN', [report(1)]);
      click('[data-handle]');
      backend.expectOne(`${admin}/reports/1/handle`).flush(report(1));
      settle();
      load('OPEN', []);
      expect(active()).toBe(q('[data-notice]'));
    });

    it('gives the focus back to the row button after hide, unhide and lock (the notice is announced, not focused)', () => {
      load('OPEN', [report(1)]);
      click('[data-hide]');
      backend.expectOne(`${admin}/threads/101/hide`).flush({ id: 101, hidden: true });
      settle();
      load('OPEN', [report(1, { targetHidden: true })]);
      expect(active()).toBe(q('[data-unhide]'));
      click('[data-unhide]');
      backend.expectOne(`${admin}/threads/101/unhide`).flush({ id: 101, hidden: false });
      settle();
      load('OPEN', [report(1)]);
      expect(active()).toBe(q('[data-hide]'));
      click('[data-lock]');
      backend.expectOne(`${admin}/threads/101/lock`).flush({ id: 101, hidden: false, locked: true });
      settle();
      load('OPEN', [report(1)]);
      expect(active()).toBe(q('[data-lock]'));
    });

    it('always renders the status region', () => {
      load('OPEN', [report(1)]);
      expect(q('[role="status"]')).not.toBeNull();
    });

    it('labels the action buttons with the target', () => {
      load('OPEN', [report(1)]);
      expect(q('[data-hide]')!.getAttribute('aria-label')).toContain('Titre 1');
      expect(q('[data-delete]')!.getAttribute('aria-label')).toContain('Titre 1');
    });

    it('links the tabs to a tabpanel and moves between them with the arrow keys', () => {
      load('OPEN', [report(1)]);
      const tab = q<HTMLButtonElement>('[data-tab="OPEN"]')!;
      const panel = q('[role="tabpanel"]')!;
      expect(tab.getAttribute('aria-controls')).toBe(panel.id);
      expect(panel.getAttribute('aria-labelledby')).toBe(tab.id);
      expect(tab.tabIndex).toBe(0);
      expect(q<HTMLButtonElement>('[data-tab="HANDLED"]')!.tabIndex).toBe(-1);
      tab.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
      settle();
      load('HANDLED', []);
      expect(q('[data-tab="HANDLED"]')!.getAttribute('aria-selected')).toBe('true');
      expect(active()).toBe(q('[data-tab="HANDLED"]'));
    });
  });
});
