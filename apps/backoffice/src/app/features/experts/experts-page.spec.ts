import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ExpertsPage } from './experts-page';
import { API_URL } from '@services/api-config';
import { Page } from '@models/entities';
import { ExpertAccount } from '@models/expert';

const api = 'http://api.test/api/v1';

const expert = (id: number, extra: Partial<ExpertAccount> = {}): ExpertAccount => ({
  id,
  firstname: `Prénom${id}`,
  lastname: `Nom${id}`,
  email: `expert${id}@example.com`,
  qualification: `Maths ${id}`,
  registeredAt: '2026-03-05T14:30:00',
  status: 'PENDING',
  reason: null,
  decisionDate: null,
  ...extra,
});

const page = (content: ExpertAccount[], number = 0, totalPages = 1, totalElements = content.length): Page<ExpertAccount> => ({
  content,
  number,
  totalPages,
  totalElements,
  size: 10,
});

describe('ExpertsPage', () => {
  let fixture: ComponentFixture<ExpertsPage>;
  let backend: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement>(sel: string) => el().querySelector<T>(sel);
  const all = (sel: string) => Array.from(el().querySelectorAll<HTMLElement>(sel));
  const settle = () => fixture.detectChanges();
  const listCall = (status: string) =>
    backend.expectOne((r) => r.url === `${api}/admin/experts` && r.method === 'GET' && r.params.get('status') === status);
  const load = (status: string, content: ExpertAccount[], number = 0, totalPages = 1, total = content.length) => {
    listCall(status).flush(page(content, number, totalPages, total));
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
    fixture = TestBed.createComponent(ExpertsPage);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('has a title, loads the pending experts first and shows name, email, specialty and date', () => {
    expect(q('h1')!.textContent).toContain('Experts');
    expect(el().textContent).toContain('Chargement');
    const req = listCall('PENDING');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    req.flush(page([expert(1), expert(2)]));
    settle();

    expect(el().textContent).not.toContain('Chargement');
    const rows = all('[data-expert]');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Prénom1 Nom1');
    expect(rows[0].textContent).toContain('expert1@example.com');
    expect(rows[0].textContent).toContain('Maths 1');
    expect(rows[0].querySelector('time')!.textContent).toMatch(/5 mars 2026/);
    expect(q('[data-tab="PENDING"]')!.getAttribute('aria-selected')).toBe('true');
  });

  it('has the three tabs, and a tab loads its status from page 0', () => {
    load('PENDING', [expert(1)], 0, 2, 11);
    expect(all('[role="tab"]').map((t) => t.textContent!.trim())).toEqual(['En attente', 'Approuvés', 'Rejetés']);

    q<HTMLButtonElement>('[data-tab="REJECTED"]')!.click();
    settle();
    const req = listCall('REJECTED');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([expert(5, { status: 'REJECTED', reason: 'Dossier incomplet' })]));
    settle();
    expect(q('[data-tab="REJECTED"]')!.getAttribute('aria-selected')).toBe('true');
    expect(q('[data-tab="PENDING"]')!.getAttribute('aria-selected')).toBe('false');
    expect(q('[data-expert]')!.textContent).toContain('Dossier incomplet');
  });

  it('offers the right actions per status', () => {
    load('PENDING', [expert(1)]);
    expect(q('[data-approve]')).not.toBeNull();
    expect(q('[data-reject]')).not.toBeNull();

    q<HTMLButtonElement>('[data-tab="APPROVED"]')!.click();
    settle();
    load('APPROVED', [expert(2, { status: 'APPROVED' })]);
    expect(q('[data-approve]')).toBeNull();
    expect(q('[data-reject]')).not.toBeNull();

    q<HTMLButtonElement>('[data-tab="REJECTED"]')!.click();
    settle();
    load('REJECTED', [expert(3, { status: 'REJECTED' })]);
    expect(q('[data-approve]')).not.toBeNull();
    expect(q('[data-reject]')).toBeNull();
  });

  it('renders names, specialty and reason as plain text, never as HTML', () => {
    load('PENDING', [
      expert(1, {
        firstname: '<img src=x onerror=alert(1)>',
        lastname: '<script>x</script>',
        qualification: '<b>gras</b>',
        reason: '<img src=y onerror=alert(2)>',
      }),
    ]);
    expect(el().querySelector('[data-expert] img')).toBeNull();
    expect(el().querySelector('[data-expert] b')).toBeNull();
    expect(el().querySelector('script')).toBeNull();
    expect(q('[data-expert]')!.textContent).toContain('<img src=x onerror=alert(1)>');
    expect(q('[data-expert]')!.textContent).toContain('<script>x</script>');
  });

  it('shows an empty state per tab', () => {
    load('PENDING', []);
    expect(el().textContent).toContain('Aucun expert en attente');
    expect(q('table')).toBeNull();
  });

  it('shows an error with a retry that reloads the same tab', () => {
    listCall('PENDING').flush(null, { status: 500, statusText: 'err' });
    settle();
    expect(q('[role="alert"]')!.textContent).toContain('Impossible de charger les experts');
    expect(el().textContent).not.toContain('Aucun expert');

    q<HTMLButtonElement>('[data-retry]')!.click();
    settle();
    load('PENDING', [expert(1)]);
    expect(q('[data-retry]')).toBeNull();
    expect(all('[data-expert]').length).toBe(1);
  });

  it('paginates with previous / next', () => {
    load('PENDING', [expert(1)], 0, 3, 25);
    expect(el().textContent).toContain('Page 1 / 3');
    expect(q<HTMLButtonElement>('[data-prev]')!.disabled).toBeTrue();
    q<HTMLButtonElement>('[data-next]')!.click();
    settle();
    const req = listCall('PENDING');
    expect(req.request.params.get('page')).toBe('1');
    req.flush(page([expert(2)], 1, 3, 25));
    settle();
    expect(el().textContent).toContain('Page 2 / 3');
  });

  it('asks for confirmation before approving, and cancelling sends nothing', () => {
    load('PENDING', [expert(1)]);
    q<HTMLButtonElement>('[data-approve]')!.click();
    settle();
    expect(q('[data-confirm-approve]')).not.toBeNull();
    q<HTMLButtonElement>('[data-cancel]')!.click();
    settle();
    expect(q('[data-confirm-approve]')).toBeNull();
    backend.expectNone(`${api}/admin/experts/1/approve`);
  });

  it('approves after confirmation, then reloads the list', () => {
    load('PENDING', [expert(1), expert(2)]);
    q<HTMLButtonElement>('[data-approve]')!.click();
    settle();
    q<HTMLButtonElement>('[data-confirm-approve]')!.click();
    const req = backend.expectOne(`${api}/admin/experts/1/approve`);
    expect(req.request.method).toBe('POST');
    req.flush(expert(1, { status: 'APPROVED' }));
    settle();

    expect(q('[data-notice]')!.textContent).toContain('Expert approuvé');
    load('PENDING', [expert(2)]);
    expect(all('[data-expert]').length).toBe(1);
  });

  it('rejects with an optional reason after confirmation', () => {
    load('PENDING', [expert(1)]);
    q<HTMLButtonElement>('[data-reject]')!.click();
    settle();
    const field = q<HTMLTextAreaElement>('[data-reason]')!;
    expect(field.maxLength).toBe(255);
    field.value = '  Diplôme illisible ';
    field.dispatchEvent(new Event('input'));
    settle();
    q<HTMLButtonElement>('[data-confirm-reject]')!.click();
    const req = backend.expectOne(`${api}/admin/experts/1/reject`);
    expect(req.request.body).toEqual({ reason: 'Diplôme illisible' });
    req.flush(expert(1, { status: 'REJECTED' }));
    settle();
    expect(q('[data-notice]')!.textContent).toContain('Expert rejeté');
    load('PENDING', []);
  });

  it('rejects without any reason when the field is left empty', () => {
    load('PENDING', [expert(1)]);
    q<HTMLButtonElement>('[data-reject]')!.click();
    settle();
    q<HTMLButtonElement>('[data-confirm-reject]')!.click();
    const req = backend.expectOne(`${api}/admin/experts/1/reject`);
    expect(req.request.body).toBeNull();
    req.flush(expert(1, { status: 'REJECTED' }));
    settle();
    load('PENDING', []);
  });

  it('disables the confirm button while the decision is in flight (no double submit)', () => {
    load('PENDING', [expert(1)]);
    q<HTMLButtonElement>('[data-approve]')!.click();
    settle();
    q<HTMLButtonElement>('[data-confirm-approve]')!.click();
    settle();
    expect(q<HTMLButtonElement>('[data-confirm-approve]')!.disabled).toBeTrue();
    q<HTMLButtonElement>('[data-confirm-approve]')!.click();
    backend.expectOne(`${api}/admin/experts/1/approve`).flush(expert(1));
    settle();
    load('PENDING', []);
  });

  it('translates a 404 and a 409, and reloads the list', () => {
    load('PENDING', [expert(1)]);
    q<HTMLButtonElement>('[data-approve]')!.click();
    settle();
    q<HTMLButtonElement>('[data-confirm-approve]')!.click();
    backend.expectOne(`${api}/admin/experts/1/approve`).flush({ message: 'raw <b>x</b>' }, { status: 404, statusText: 'Not Found' });
    settle();
    load('PENDING', []);
    expect(q('[role="alert"]')!.textContent).toContain('Compte introuvable');
    expect(el().textContent).not.toContain('raw');

    q<HTMLButtonElement>('[data-tab="APPROVED"]')!.click();
    settle();
    load('APPROVED', [expert(2, { status: 'APPROVED' })]);
    q<HTMLButtonElement>('[data-reject]')!.click();
    settle();
    q<HTMLButtonElement>('[data-confirm-reject]')!.click();
    backend.expectOne(`${api}/admin/experts/2/reject`).flush({ message: 'x' }, { status: 409, statusText: 'Conflict' });
    settle();
    load('APPROVED', []);
    expect(q('[role="alert"]')!.textContent).toContain("Ce compte n'est pas un expert");
  });

  it('shows the API validation message for a refused reason (400) and keeps the form', () => {
    load('PENDING', [expert(1)]);
    q<HTMLButtonElement>('[data-reject]')!.click();
    settle();
    q<HTMLButtonElement>('[data-confirm-reject]')!.click();
    backend.expectOne(`${api}/admin/experts/1/reject`).flush({ reason: 'Le motif contient des caractères non autorisés' }, { status: 400, statusText: 'Bad Request' });
    settle();
    expect(q('[role="alert"]')!.textContent).toContain('Le motif contient des caractères non autorisés');
    expect(q('[data-confirm-reject]')).not.toBeNull();
  });

  it('falls back to a generic French message for other failures', () => {
    load('PENDING', [expert(1)]);
    q<HTMLButtonElement>('[data-approve]')!.click();
    settle();
    q<HTMLButtonElement>('[data-confirm-approve]')!.click();
    backend.expectOne(`${api}/admin/experts/1/approve`).flush({ message: 'boom' }, { status: 500, statusText: 'err' });
    settle();
    expect(q('[role="alert"]')!.textContent).toContain("La décision n'a pas pu être enregistrée");
    expect(el().textContent).not.toContain('boom');
  });

  it('steps back a page after deciding the only row of the last page', () => {
    load('PENDING', [expert(1)], 0, 2, 11);
    q<HTMLButtonElement>('[data-next]')!.click();
    settle();
    listCall('PENDING').flush(page([expert(11)], 1, 2, 11));
    settle();
    q<HTMLButtonElement>('[data-approve]')!.click();
    settle();
    q<HTMLButtonElement>('[data-confirm-approve]')!.click();
    backend.expectOne(`${api}/admin/experts/11/approve`).flush(expert(11));
    settle();
    const req = listCall('PENDING');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([expert(1)], 0, 1, 10));
  });

  describe('accessibility', () => {
    const active = () => document.activeElement;

    it('moves the focus to the reason field when rejecting, to Confirmer when approving', () => {
      load('PENDING', [expert(1)]);
      q<HTMLButtonElement>('[data-reject]')!.click();
      settle();
      expect(active()).toBe(q('[data-reason]'));
      q<HTMLButtonElement>('[data-cancel]')!.click();
      settle();
      q<HTMLButtonElement>('[data-approve]')!.click();
      settle();
      expect(active()).toBe(q('[data-confirm-approve]'));
    });

    it('gives the focus back to the action button on cancel', () => {
      load('PENDING', [expert(1)]);
      q<HTMLButtonElement>('[data-reject]')!.click();
      settle();
      q<HTMLButtonElement>('[data-cancel]')!.click();
      settle();
      expect(active()).toBe(q('[data-reject]'));
      q<HTMLButtonElement>('[data-approve]')!.click();
      settle();
      q<HTMLButtonElement>('[data-cancel]')!.click();
      settle();
      expect(active()).toBe(q('[data-approve]'));
    });

    it('focuses the success message after a decision', () => {
      load('PENDING', [expert(1)]);
      q<HTMLButtonElement>('[data-approve]')!.click();
      settle();
      q<HTMLButtonElement>('[data-confirm-approve]')!.click();
      backend.expectOne(`${api}/admin/experts/1/approve`).flush(expert(1));
      settle();
      load('PENDING', []);
      expect(active()).toBe(q('[data-notice]'));
      expect(q('[data-notice]')!.textContent).toContain('Expert approuvé');
    });

    it('always renders the status region', () => {
      load('PENDING', [expert(1)]);
      expect(q('[role="status"]')).not.toBeNull();
    });

    it('links the tabs to a tabpanel and moves between them with the arrow keys', () => {
      load('PENDING', [expert(1)]);
      const tab = q<HTMLButtonElement>('[data-tab="PENDING"]')!;
      const panel = q('[role="tabpanel"]')!;
      expect(tab.getAttribute('aria-controls')).toBe(panel.id);
      expect(panel.getAttribute('aria-labelledby')).toBe(tab.id);
      expect(tab.tabIndex).toBe(0);
      expect(q<HTMLButtonElement>('[data-tab="APPROVED"]')!.tabIndex).toBe(-1);

      tab.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
      settle();
      load('APPROVED', []);
      expect(q('[data-tab="APPROVED"]')!.getAttribute('aria-selected')).toBe('true');
      expect(active()).toBe(q('[data-tab="APPROVED"]'));
      q('[data-tab="APPROVED"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
      settle();
      load('PENDING', []);
      expect(q('[data-tab="PENDING"]')!.getAttribute('aria-selected')).toBe('true');
    });

    it('describes the reason field with its limit, and flags it invalid after a refused reason', () => {
      load('PENDING', [expert(1)]);
      q<HTMLButtonElement>('[data-reject]')!.click();
      settle();
      const field = q<HTMLTextAreaElement>('[data-reason]')!;
      const hint = document.getElementById(field.getAttribute('aria-describedby')!.split(' ')[0])!;
      expect(hint.textContent).toContain('255 caractères max.');
      expect(field.getAttribute('aria-invalid')).toBe('false');

      q<HTMLButtonElement>('[data-confirm-reject]')!.click();
      backend.expectOne(`${api}/admin/experts/1/reject`).flush({ reason: 'Motif refusé' }, { status: 400, statusText: 'Bad Request' });
      settle();
      expect(q('[data-reason]')!.getAttribute('aria-invalid')).toBe('true');
    });
  });
});
