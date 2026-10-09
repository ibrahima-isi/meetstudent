import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SchoolsPage } from './schools-page';
import { API_URL, SERVER_URL } from '@services/api-config';
import { Page, School } from '@models/school';

const api = 'http://api.test/api/v1';

const school = (id: number, extra: Partial<School> = {}): School => ({
  id,
  code: `S${id}`,
  name: `Ecole ${id}`,
  address: { location: 'rue', city: 'Lyon', country: 'France' },
  tags: [{ id: 1, name: 'PUBLIC' }],
  ...extra,
});

const page = (content: School[], number = 0, totalPages = 1, totalElements = content.length): Page<School> => ({
  content,
  number,
  totalPages,
  totalElements,
  size: 10,
});

describe('SchoolsPage', () => {
  let fixture: ComponentFixture<SchoolsPage>;
  let backend: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement>(sel: string) => el().querySelector<T>(sel);
  const all = (sel: string) => Array.from(el().querySelectorAll<HTMLElement>(sel));
  const settle = () => fixture.detectChanges();
  const listCall = () => backend.expectOne((r) => r.url === `${api}/schools`);

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: api },
        { provide: SERVER_URL, useValue: 'http://srv.test' },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SchoolsPage);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  /** Initial load answered with the given schools. */
  const load = (content: School[], number = 0, totalPages = 1, total = content.length) => {
    listCall().flush(page(content, number, totalPages, total));
    settle();
  };

  it('shows a loading state, then the first page of schools', () => {
    expect(el().textContent).toContain('Chargement');
    const req = listCall();
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    req.flush(page([school(1), school(2)]));
    settle();

    expect(el().textContent).not.toContain('Chargement');
    const rows = all('tr[data-school]');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Ecole 1');
    expect(rows[0].textContent).toContain('S1');
    expect(rows[0].textContent).toContain('Lyon');
    expect(rows[0].textContent).toContain('PUBLIC');
  });

  it('shows the logo from the server root', () => {
    load([school(1, { logo: { id: 3, publicUrl: '/uploads/public/l.png' } }), school(2)]);
    const imgs = all('tr[data-school] img');
    expect(imgs.length).toBe(1);
    expect(imgs[0].getAttribute('src')).toBe('http://srv.test/uploads/public/l.png');
  });

  it('shows an empty state', () => {
    load([]);
    expect(el().textContent).toContain('Aucune école');
  });

  it('shows an error alert with a retry that reloads', () => {
    listCall().flush(null, { status: 500, statusText: 'err' });
    settle();
    expect(q('[role=alert]')?.textContent).toContain('Impossible de charger');
    expect(all('tr[data-school]').length).toBe(0);

    q<HTMLButtonElement>('[data-retry]')!.click();
    load([school(1)]);
    expect(q('[role=alert]')).toBeNull();
    expect(all('tr[data-school]').length).toBe(1);
  });

  describe('search', () => {
    const search = (text: string) => {
      const input = q<HTMLInputElement>('#search')!;
      input.value = text;
      input.dispatchEvent(new Event('input'));
      q<HTMLFormElement>('form[data-search]')!.dispatchEvent(new Event('submit'));
      settle();
    };

    beforeEach(() => load([school(1)], 0, 3, 25));

    it('queries the name endpoint and goes back to page 0', () => {
      q<HTMLButtonElement>('[data-next]')!.click();
      listCall().flush(page([school(2)], 1, 3, 25));
      settle();

      search('lyon');
      const req = backend.expectOne((r) => r.url === `${api}/schools/name/lyon`);
      expect(req.request.params.get('page')).toBe('0');
      req.flush(page([school(5, { name: 'Lyon Business' })]));
      settle();
      expect(all('tr[data-school]')[0].textContent).toContain('Lyon Business');
    });

    it('shows a distinct empty state for a search without result', () => {
      search('zzz');
      backend.expectOne((r) => r.url === `${api}/schools/name/zzz`).flush(page([]));
      settle();
      expect(el().textContent).toContain('Aucun résultat');
    });

    it('clearing the search restores the full list', () => {
      search('lyon');
      backend.expectOne((r) => r.url === `${api}/schools/name/lyon`).flush(page([]));
      settle();
      q<HTMLButtonElement>('[data-clear-search]')!.click();
      expect((q<HTMLInputElement>('#search')!).value).toBe('');
      listCall().flush(page([school(1)]));
    });
  });

  describe('pagination', () => {
    it('has no way back on the first page and a way forward', () => {
      load([school(1)], 0, 3, 25);
      expect(q<HTMLButtonElement>('[data-prev]')!.disabled).toBeTrue();
      expect(q<HTMLButtonElement>('[data-next]')!.disabled).toBeFalse();
      expect(el().textContent).toContain('Page 1 / 3');
      expect(el().textContent).toContain('25');
    });

    it('requests the next and previous pages', () => {
      load([school(1)], 0, 3, 25);
      q<HTMLButtonElement>('[data-next]')!.click();
      const next = listCall();
      expect(next.request.params.get('page')).toBe('1');
      next.flush(page([school(2)], 1, 3, 25));
      settle();
      expect(el().textContent).toContain('Page 2 / 3');

      q<HTMLButtonElement>('[data-prev]')!.click();
      const prev = listCall();
      expect(prev.request.params.get('page')).toBe('0');
      prev.flush(page([school(1)], 0, 3, 25));
    });

    it('disables next on the last page', () => {
      load([school(1)], 2, 3, 25);
      expect(q<HTMLButtonElement>('[data-next]')!.disabled).toBeTrue();
    });

    it('keeps the current page visible when loading the next one fails', () => {
      load([school(1)], 0, 3, 25);
      q<HTMLButtonElement>('[data-next]')!.click();
      listCall().flush(null, { status: 500, statusText: 'err' });
      settle();
      expect(q('[role=alert]')).not.toBeNull();
    });
  });

  describe('form', () => {
    beforeEach(() => load([school(1), school(2)]));

    const flushTags = () => backend.expectOne(`${api}/tags`).flush([{ id: 1, name: 'PUBLIC' }]);

    it('opens an empty form from the new button', () => {
      q<HTMLButtonElement>('[data-new]')!.click();
      settle();
      flushTags();
      settle();
      expect(q('app-school-form')).not.toBeNull();
      expect(q<HTMLInputElement>('#name')!.value).toBe('');
      expect(all('tr[data-school]').length).toBe(0);
    });

    it('opens the form prefilled from a row', () => {
      all('[data-edit]')[1].click();
      settle();
      flushTags();
      settle();
      expect(q<HTMLInputElement>('#name')!.value).toBe('Ecole 2');
    });

    it('goes back to the list without reloading on cancel', () => {
      q<HTMLButtonElement>('[data-new]')!.click();
      settle();
      flushTags();
      settle();
      q<HTMLButtonElement>('[data-cancel]')!.click();
      settle();
      expect(q('app-school-form')).toBeNull();
      expect(all('tr[data-school]').length).toBe(2);
    });

    it('reloads the list after a save and announces it', () => {
      q<HTMLButtonElement>('[data-new]')!.click();
      settle();
      flushTags();
      settle();
      const input = q<HTMLInputElement>('#name')!;
      input.value = 'Nouvelle';
      input.dispatchEvent(new Event('input'));
      q<HTMLButtonElement>('button[type=submit]')!.click();
      backend.expectOne(`${api}/schools`).flush(school(9, { name: 'Nouvelle' }));
      settle();

      expect(q('app-school-form')).toBeNull();
      listCall().flush(page([school(9, { name: 'Nouvelle' })]));
      settle();
      expect(el().textContent).toContain('Nouvelle');
      expect(q('[role=status], [data-notice]')?.textContent).toContain('enregistrée');
    });
  });

  describe('delete', () => {
    beforeEach(() => load([school(1), school(2)]));

    it('asks for confirmation before sending anything', () => {
      all('[data-delete]')[0].click();
      settle();
      expect(q('[data-confirm-delete]')).not.toBeNull();
      backend.expectNone(`${api}/schools/1`);
    });

    it('cancelling dismisses the confirmation without a request', () => {
      all('[data-delete]')[0].click();
      settle();
      q<HTMLButtonElement>('[data-cancel-delete]')!.click();
      settle();
      expect(q('[data-confirm-delete]')).toBeNull();
      backend.expectNone(`${api}/schools/1`);
    });

    it('confirming deletes then reloads the list', () => {
      all('[data-delete]')[0].click();
      settle();
      q<HTMLButtonElement>('[data-confirm-delete]')!.click();
      const del = backend.expectOne(`${api}/schools/1`);
      expect(del.request.method).toBe('DELETE');
      del.flush(school(1));
      settle();
      listCall().flush(page([school(2)]));
      settle();
      expect(all('tr[data-school]').length).toBe(1);
      expect(q('[data-confirm-delete]')).toBeNull();
    });

    it('shows an alert and keeps the row when the delete fails', () => {
      all('[data-delete]')[0].click();
      settle();
      q<HTMLButtonElement>('[data-confirm-delete]')!.click();
      backend.expectOne(`${api}/schools/1`).flush(null, { status: 500, statusText: 'err' });
      settle();
      expect(q('[role=alert]')?.textContent).toContain('suppression');
      expect(all('tr[data-school]').length).toBe(2);
      expect(q('[data-confirm-delete]')).toBeNull();
    });
  });

  it('steps back a page when the last school of a later page is deleted', () => {
    // fresh component: the outer beforeEach already created one with an unanswered load
    listCall().flush(page([school(11)], 1, 2, 11));
    settle();
    all('[data-delete]')[0].click();
    settle();
    q<HTMLButtonElement>('[data-confirm-delete]')!.click();
    backend.expectOne(`${api}/schools/11`).flush(school(11));
    settle();
    const req = listCall();
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([school(1)], 0, 1, 10));
  });
});
