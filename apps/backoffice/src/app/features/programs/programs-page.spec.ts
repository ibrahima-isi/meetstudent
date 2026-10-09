import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProgramsPage } from './programs-page';
import { API_URL, SERVER_URL } from '@services/api-config';
import { Page } from '@models/entities';
import { Program } from '@models/program';

const api = 'http://api.test/api/v1';

const program = (id: number, extra: Partial<Program> = {}): Program => ({
  id,
  code: `P${id}`,
  name: `Filiere ${id}`,
  duration: 3,
  schoolId: 3,
  courses: [{ id: id * 10, name: `Cours ${id}` }],
  ...extra,
});

const page = (content: Program[], number = 0, totalPages = 1, totalElements = content.length): Page<Program> => ({
  content,
  number,
  totalPages,
  totalElements,
  size: 10,
});

describe('ProgramsPage', () => {
  let fixture: ComponentFixture<ProgramsPage>;
  let backend: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement>(sel: string) => el().querySelector<T>(sel);
  const all = (sel: string) => Array.from(el().querySelectorAll<HTMLElement>(sel));
  const settle = () => fixture.detectChanges();
  const click = (sel: string, index = 0) => {
    all(sel)[index].click();
    settle();
  };
  const listCall = () => backend.expectOne((r) => r.url === `${api}/programs`);

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
    fixture = TestBed.createComponent(ProgramsPage);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  /** The schools used for names and for the form's select. */
  const answerSchools = (content = [{ id: 3, name: 'Ecole A' }]) => {
    const req = backend.expectOne((r) => r.url === `${api}/schools`);
    expect(req.request.params.get('size')).toBe('200');
    expect(req.request.params.get('sort')).toBe('name,asc');
    req.flush({ content });
  };

  /** Initial load answered with the given programs. */
  const load = (content: Program[], number = 0, totalPages = 1, total = content.length) => {
    answerSchools();
    listCall().flush(page(content, number, totalPages, total));
    settle();
  };

  it('shows a loading state, then the first page of programs', () => {
    expect(el().textContent).toContain('Chargement');
    answerSchools();
    const req = listCall();
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    req.flush(page([program(1), program(2)]));
    settle();

    expect(el().textContent).not.toContain('Chargement');
    const rows = all('tr[data-program]');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Filiere 1');
    expect(rows[0].textContent).toContain('P1');
    expect(rows[0].textContent).toContain('Ecole A');
    expect(rows[0].textContent).toContain('3 ans');
    expect(rows[0].textContent).toContain('1');
  });

  it('shows the photo from the server root', () => {
    load([program(1, { photo: { id: 3, publicUrl: '/uploads/public/p.png' } }), program(2)]);
    expect(all('tr[data-program] img')[0].getAttribute('src')).toBe('http://srv.test/uploads/public/p.png');
  });

  it('falls back to the school number when the school list has no such school or failed', () => {
    answerSchools([]);
    listCall().flush(page([program(1, { schoolId: 9 }), program(2, { schoolId: null })]));
    settle();
    const rows = all('tr[data-program]');
    expect(rows[0].textContent).toContain('n° 9');
    expect(rows[1].textContent).not.toContain('n°');
  });

  it('still lists programs when the schools cannot be loaded', () => {
    backend.expectOne((r) => r.url === `${api}/schools`).flush(null, { status: 500, statusText: 'x' });
    listCall().flush(page([program(1)]));
    settle();
    expect(all('tr[data-program]').length).toBe(1);
  });

  it('shows an empty state', () => {
    load([]);
    expect(el().textContent).toContain('Aucune filière');
  });

  it('shows an error alert with a retry that reloads', () => {
    answerSchools();
    listCall().flush(null, { status: 500, statusText: 'x' });
    settle();
    expect(q('[role=alert]')!.textContent).toContain('Impossible de charger');
    click('[data-retry]');
    listCall().flush(page([program(1)]));
    settle();
    expect(all('tr[data-program]').length).toBe(1);
    expect(q('[role=alert]')).toBeNull();
  });

  describe('search', () => {
    const search = (term: string) => {
      const input = q<HTMLInputElement>('#search')!;
      input.value = term;
      input.dispatchEvent(new Event('input'));
      q<HTMLFormElement>('form[data-search]')!.dispatchEvent(new Event('submit'));
      settle();
    };

    it('queries the name endpoint and goes back to page 0', () => {
      load([program(1)], 2, 5, 50);
      search(' Dro ');
      const req = backend.expectOne((r) => r.url === `${api}/programs/name/Dro`);
      expect(req.request.params.get('page')).toBe('0');
      req.flush(page([program(2)]));
      settle();
      expect(all('tr[data-program]')[0].textContent).toContain('Filiere 2');
    });

    it('shows a distinct empty state for a search without result', () => {
      load([program(1)]);
      search('zzz');
      backend.expectOne((r) => r.url === `${api}/programs/name/zzz`).flush(page([]));
      settle();
      expect(el().textContent).toContain('Aucun résultat');
    });

    it('clearing the search restores the full list', () => {
      load([program(1)]);
      search('zzz');
      backend.expectOne((r) => r.url === `${api}/programs/name/zzz`).flush(page([]));
      settle();
      click('[data-clear-search]');
      listCall().flush(page([program(1)]));
      settle();
      expect(q<HTMLInputElement>('#search')!.value).toBe('');
      expect(all('tr[data-program]').length).toBe(1);
    });

    it('ignores a slow answer that arrives after a newer one', () => {
      load([program(1)]);
      search('a');
      const slow = backend.expectOne((r) => r.url === `${api}/programs/name/a`);
      search('b');
      const fast = backend.expectOne((r) => r.url === `${api}/programs/name/b`);
      fast.flush(page([program(2, { name: 'Bravo' })]));
      slow.flush(page([program(3, { name: 'Alpha' })]));
      settle();
      const rows = all('tr[data-program]');
      expect(rows.length).toBe(1);
      expect(rows[0].textContent).toContain('Bravo');
    });
  });

  describe('pagination', () => {
    it('has no way back on the first page and a way forward', () => {
      load([program(1)], 0, 3, 25);
      expect(el().textContent).toContain('Page 1 / 3');
      expect(q<HTMLButtonElement>('[data-prev]')!.disabled).toBeTrue();
      expect(q<HTMLButtonElement>('[data-next]')!.disabled).toBeFalse();
    });

    it('requests the next and previous pages', () => {
      load([program(1)], 0, 3, 25);
      click('[data-next]');
      const next = listCall();
      expect(next.request.params.get('page')).toBe('1');
      next.flush(page([program(2)], 1, 3, 25));
      settle();
      click('[data-prev]');
      const prev = listCall();
      expect(prev.request.params.get('page')).toBe('0');
      prev.flush(page([program(1)], 0, 3, 25));
    });

    it('disables next on the last page', () => {
      load([program(1)], 2, 3, 25);
      expect(q<HTMLButtonElement>('[data-next]')!.disabled).toBeTrue();
    });

    it('keeps the current page visible when loading the next one fails', () => {
      load([program(1)], 0, 3, 25);
      click('[data-next]');
      listCall().flush(null, { status: 500, statusText: 'x' });
      settle();
      expect(all('tr[data-program]').length).toBe(1);
      expect(q('[role=alert]')).not.toBeNull();
    });
  });

  describe('create and edit', () => {
    it('opens an empty form from the new button', () => {
      load([program(1)]);
      click('[data-new]');
      expect(q('app-program-form')).not.toBeNull();
      expect(q<HTMLInputElement>('#name')!.value).toBe('');
      expect(Array.from(el().querySelectorAll('#schoolId option')).map((o) => o.textContent!.trim())).toEqual([
        'Aucune',
        'Ecole A',
      ]);
    });

    it('opens the form prefilled from a row', () => {
      load([program(1)]);
      click('[data-edit]');
      backend.expectOne(`${api}/programs/1/accreditations`).flush([]);
      backend.expectOne((r) => r.url === `${api}/accreditations`).flush({ content: [] });
      settle();
      expect(q<HTMLInputElement>('#name')!.value).toBe('Filiere 1');
    });

    it('goes back to the list without reloading on cancel', () => {
      load([program(1)]);
      click('[data-new]');
      click('[data-cancel]');
      expect(q('app-program-form')).toBeNull();
      expect(all('tr[data-program]').length).toBe(1);
    });

    it('reloads the current page after a save and announces it', () => {
      load([program(1)], 1, 3, 25);
      click('[data-new]');
      const name = q<HTMLInputElement>('#name')!;
      name.value = 'Nouvelle';
      name.dispatchEvent(new Event('input'));
      click('button[type=submit]');
      backend.expectOne(`${api}/programs`).flush({ id: 9, name: 'Nouvelle' });
      settle();
      const reload = listCall();
      expect(reload.request.params.get('page')).toBe('1');
      reload.flush(page([program(1), program(9, { name: 'Nouvelle' })], 1, 3, 26));
      settle();
      expect(q('[data-notice]')!.textContent).toContain('Filière enregistrée');
      expect(all('tr[data-program]').length).toBe(2);
    });
  });

  describe('delete', () => {
    it('asks for confirmation before sending anything, and mentions the courses', () => {
      load([program(1, { courses: [{ id: 1, name: 'a' }, { id: 2, name: 'b' }] })]);
      click('[data-delete]');
      expect(el().textContent).toContain('2 cours');
      expect(all('[data-confirm-delete]').length).toBe(1);
      backend.expectNone(`${api}/programs/1`);
    });

    it('cancelling dismisses the confirmation without a request', () => {
      load([program(1)]);
      click('[data-delete]');
      click('[data-cancel-delete]');
      expect(all('[data-confirm-delete]').length).toBe(0);
    });

    it('confirming deletes then reloads the list', () => {
      load([program(1), program(2)]);
      click('[data-delete]');
      click('[data-confirm-delete]');
      const del = backend.expectOne(`${api}/programs/1`);
      expect(del.request.method).toBe('DELETE');
      del.flush(program(1));
      settle();
      listCall().flush(page([program(2)]));
      settle();
      expect(all('tr[data-program]').length).toBe(1);
      expect(q('[data-notice]')!.textContent).toContain('Filière supprimée');
    });

    it('shows an alert and keeps the row when the delete fails', () => {
      load([program(1)]);
      click('[data-delete]');
      click('[data-confirm-delete]');
      backend.expectOne(`${api}/programs/1`).flush(null, { status: 500, statusText: 'x' });
      settle();
      expect(q('[role=alert]')!.textContent).toContain('suppression');
      expect(all('tr[data-program]').length).toBe(1);
    });

    it('steps back a page when the last program of a later page is deleted', () => {
      load([program(1)], 2, 3, 21);
      click('[data-delete]');
      click('[data-confirm-delete]');
      backend.expectOne(`${api}/programs/1`).flush(program(1));
      const reload = listCall();
      expect(reload.request.params.get('page')).toBe('1');
      reload.flush(page([program(2)], 1, 2, 11));
    });
  });

  describe('courses', () => {
    it('opens the courses of a program and goes back to the list', () => {
      load([program(1), program(2)]);
      expect(all('[data-courses]')[1].textContent).toContain('1');
      click('[data-courses]', 1);
      expect(q('app-program-courses')).not.toBeNull();
      expect(q('h2')!.textContent).toContain('Filiere 2');
      expect(all('tr[data-course]').length).toBe(1);
      click('[data-back]');
      expect(q('app-program-courses')).toBeNull();
      expect(all('tr[data-program]').length).toBe(2);
    });

    it('reloads the programs after a course change and shows the fresh courses', () => {
      load([program(1)]);
      click('[data-courses]');
      click('[data-new]');
      const name = q<HTMLInputElement>('#name')!;
      name.value = 'Nouveau cours';
      name.dispatchEvent(new Event('input'));
      click('button[type=submit]');
      backend.expectOne(`${api}/courses`).flush({ id: 99, name: 'Nouveau cours' });
      settle();
      listCall().flush(
        page([program(1, { courses: [{ id: 10, name: 'Cours 1' }, { id: 99, name: 'Nouveau cours' }] })]),
      );
      settle();
      expect(q('app-program-courses')).not.toBeNull();
      expect(all('tr[data-course]').length).toBe(2);
    });
  });
});
