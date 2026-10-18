import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MessagesPage } from './messages-page';
import { API_URL } from '@services/api-config';
import { Page } from '@models/entities';
import { ContactMessage } from '@models/message';

const api = 'http://api.test/api/v1';

const msg = (id: number, extra: Partial<ContactMessage> = {}): ContactMessage => ({
  id,
  email: `sender${id}@example.com`,
  subject: `Sujet ${id}`,
  message: `Texte ${id}`,
  language: 'fr',
  createdAt: '2026-03-05T14:30:00Z',
  mailStatus: 'UNKNOWN',
  mailAttempts: 0,
  mailSentAt: null,
  ...extra,
});

const page = (content: ContactMessage[], number = 0, totalPages = 1, totalElements = content.length): Page<ContactMessage> => ({
  content,
  number,
  totalPages,
  totalElements,
  size: 10,
});

describe('MessagesPage', () => {
  let fixture: ComponentFixture<MessagesPage>;
  let backend: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement>(sel: string) => el().querySelector<T>(sel);
  const all = (sel: string) => Array.from(el().querySelectorAll<HTMLElement>(sel));
  const settle = () => fixture.detectChanges();
  const listCall = () => backend.expectOne((r) => r.url === `${api}/admin/messages` && r.method === 'GET');
  const load = (content: ContactMessage[], number = 0, totalPages = 1, total = content.length) => {
    listCall().flush(page(content, number, totalPages, total));
    settle();
  };

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
    fixture = TestBed.createComponent(MessagesPage);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('has a title, shows loading, then the first page', () => {
    expect(q('h1')!.textContent).toContain('Messages');
    expect(el().textContent).toContain('Chargement');
    const req = listCall();
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    req.flush(page([msg(1), msg(2)]));
    settle();

    expect(el().textContent).not.toContain('Chargement');
    const rows = all('[data-message]');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('sender1@example.com');
    expect(rows[0].textContent).toContain('Sujet 1');
    expect(rows[0].textContent).toContain('Texte 1');
  });

  it('formats the date in French', () => {
    load([msg(1, { createdAt: '2026-03-05T14:30:00Z' })]);
    expect(q('[data-message] time')!.textContent).toMatch(/5 mars 2026/);
    expect(q('[data-message] time')!.getAttribute('datetime')).toBe('2026-03-05T14:30:00Z');
  });

  it('renders the message as plain text with line breaks preserved, never as HTML', () => {
    load([msg(1, { message: 'Ligne 1\nLigne 2 <img src=x onerror=alert(1)><b>gras</b>', subject: '<script>x</script>' })]);
    const body = q<HTMLElement>('[data-message] [data-body]')!;
    expect(body.textContent).toContain('Ligne 1\nLigne 2 <img src=x onerror=alert(1)><b>gras</b>');
    expect(getComputedStyle(body).whiteSpace).toBe('pre-wrap');
    expect(el().querySelector('[data-message] img')).toBeNull();
    expect(el().querySelector('[data-message] b')).toBeNull();
    expect(el().querySelector('script')).toBeNull();
    expect(q('[data-message]')!.textContent).toContain('<script>x</script>');
  });

  it('shows an empty state', () => {
    load([]);
    expect(el().textContent).toContain('Aucun message');
    expect(q('table')).toBeNull();
  });

  it('shows an error with a retry that reloads', () => {
    listCall().flush(null, { status: 500, statusText: 'err' });
    settle();
    expect(q('[role="alert"]')!.textContent).toContain('Impossible de charger les messages');
    expect(el().textContent).not.toContain('Aucun message');

    q<HTMLButtonElement>('[data-retry]')!.click();
    settle();
    load([msg(1)]);
    expect(q('[data-retry]')).toBeNull();
    expect(all('[data-message]').length).toBe(1);
  });

  it('paginates with previous / next', () => {
    load([msg(1)], 0, 3, 25);
    expect(el().textContent).toContain('Page 1 / 3');
    expect(q<HTMLButtonElement>('[data-prev]')!.disabled).toBeTrue();

    q<HTMLButtonElement>('[data-next]')!.click();
    settle();
    const req = listCall();
    expect(req.request.params.get('page')).toBe('1');
    req.flush(page([msg(2)], 1, 3, 25));
    settle();
    expect(el().textContent).toContain('Page 2 / 3');
    expect(q<HTMLButtonElement>('[data-prev]')!.disabled).toBeFalse();
  });

  it('asks for confirmation before deleting, and cancelling sends nothing', () => {
    load([msg(1), msg(2)]);
    q<HTMLButtonElement>('[data-message] [data-delete]')!.click();
    settle();
    expect(q('[data-confirm-delete]')).not.toBeNull();
    q<HTMLButtonElement>('[data-cancel-delete]')!.click();
    settle();
    expect(q('[data-confirm-delete]')).toBeNull();
    backend.expectNone(`${api}/admin/messages/1`);
  });

  it('deletes after confirmation and reloads the same page', () => {
    load([msg(1), msg(2)]);
    q<HTMLButtonElement>('[data-message] [data-delete]')!.click();
    settle();
    q<HTMLButtonElement>('[data-confirm-delete]')!.click();
    const del = backend.expectOne(`${api}/admin/messages/1`);
    expect(del.request.method).toBe('DELETE');
    del.flush(null, { status: 204, statusText: 'No Content' });
    settle();

    expect(q('[data-notice]')!.textContent).toContain('Message supprimé');
    load([msg(2)]);
    expect(all('[data-message]').length).toBe(1);
  });

  it('steps back a page after deleting the only row of the last page', () => {
    load([msg(1)], 0, 2, 11);
    q<HTMLButtonElement>('[data-next]')!.click();
    settle();
    listCall().flush(page([msg(11)], 1, 2, 11));
    settle();

    q<HTMLButtonElement>('[data-message] [data-delete]')!.click();
    settle();
    q<HTMLButtonElement>('[data-confirm-delete]')!.click();
    backend.expectOne(`${api}/admin/messages/11`).flush(null, { status: 204, statusText: 'No Content' });
    settle();

    const req = listCall();
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([msg(1)], 0, 1, 10));
  });

  it('shows an error when the delete fails and keeps the list', () => {
    load([msg(1)]);
    q<HTMLButtonElement>('[data-message] [data-delete]')!.click();
    settle();
    q<HTMLButtonElement>('[data-confirm-delete]')!.click();
    backend.expectOne(`${api}/admin/messages/1`).flush(null, { status: 500, statusText: 'err' });
    settle();
    expect(q('[role="alert"]')!.textContent).toContain('La suppression a échoué');
    expect(all('[data-message]').length).toBe(1);
  });

  it('labels the delete button for assistive tech with the subject', () => {
    load([msg(1, { subject: 'Question admission' })]);
    expect(q('[data-message] [data-delete]')!.getAttribute('aria-label')).toContain('Question admission');
  });
});
