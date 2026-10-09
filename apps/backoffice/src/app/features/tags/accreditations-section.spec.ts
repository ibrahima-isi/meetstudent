import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AccreditationsSection } from './accreditations-section';
import { API_URL } from '@services/api-config';
import { Page } from '@models/entities';
import { Accreditation } from '@models/accreditation';

const api = 'http://api.test/api/v1';

const acc = (id: number, extra: Partial<Accreditation> = {}): Accreditation => ({
  id,
  code: `A${id}`,
  name: `Accred ${id}`,
  description: `Desc ${id}`,
  ...extra,
});

const page = (content: Accreditation[], number = 0, totalPages = 1, totalElements = content.length): Page<Accreditation> => ({
  content,
  number,
  totalPages,
  totalElements,
  size: 10,
});

describe('AccreditationsSection', () => {
  let fixture: ComponentFixture<AccreditationsSection>;
  let backend: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement>(sel: string) => el().querySelector<T>(sel);
  const all = (sel: string) => Array.from(el().querySelectorAll<HTMLElement>(sel));
  const settle = () => fixture.detectChanges();
  const listCall = () => backend.expectOne((r) => r.url === `${api}/accreditations` && r.method === 'GET');
  const load = (content: Accreditation[], number = 0, totalPages = 1, total = content.length) => {
    listCall().flush(page(content, number, totalPages, total));
    settle();
  };
  const fill = (id: string, value: string) => {
    const input = q<HTMLInputElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const submit = () => {
    q<HTMLFormElement>('form[data-form]')!.dispatchEvent(new Event('submit'));
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
    fixture = TestBed.createComponent(AccreditationsSection);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('shows a loading state, then the first page', () => {
    expect(el().textContent).toContain('Chargement');
    const req = listCall();
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    req.flush(page([acc(1), acc(2)]));
    settle();

    expect(el().textContent).not.toContain('Chargement');
    const rows = all('tr[data-accreditation]');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Accred 1');
    expect(rows[0].textContent).toContain('A1');
    expect(rows[0].textContent).toContain('Desc 1');
  });

  it('shows an empty state', () => {
    load([]);
    expect(el().textContent).toContain('Aucune accréditation');
  });

  it('shows an error with a retry that reloads', () => {
    listCall().flush(null, { status: 500, statusText: 'err' });
    settle();
    expect(q('[role=alert]')?.textContent).toContain('Impossible de charger');
    q<HTMLButtonElement>('[data-retry]')!.click();
    load([acc(1)]);
    expect(q('[role=alert]')).toBeNull();
    expect(all('tr[data-accreditation]').length).toBe(1);
  });

  it('ignores a stale response that arrives after a newer one', () => {
    const first = listCall();
    fixture.componentInstance.load(1);
    const second = listCall();
    second.flush(page([acc(2)], 1, 2, 11));
    first.flush(page([acc(1)]));
    settle();
    const rows = all('tr[data-accreditation]');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('Accred 2');
  });

  it('paginates', () => {
    load([acc(1)], 0, 3, 25);
    expect(q<HTMLButtonElement>('[data-prev]')!.disabled).toBeTrue();
    expect(el().textContent).toContain('Page 1 / 3');
    q<HTMLButtonElement>('[data-next]')!.click();
    const req = listCall();
    expect(req.request.params.get('page')).toBe('1');
    req.flush(page([acc(2)], 1, 3, 25));
    settle();
    expect(el().textContent).toContain('Page 2 / 3');
  });

  describe('create', () => {
    beforeEach(() => {
      load([acc(1)]);
      q<HTMLButtonElement>('[data-new]')!.click();
      settle();
    });

    it('posts the trimmed values, reloads and confirms', () => {
      fill('acc-name', ' EQUIS ');
      fill('acc-code', 'EQ');
      fill('acc-description', ' Europe ');
      submit();
      const req = backend.expectOne(`${api}/accreditations`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ name: 'EQUIS', code: 'EQ', description: 'Europe' });
      req.flush(acc(9));
      settle();
      load([acc(1), acc(9)]);

      expect(q('form[data-form]')).toBeNull();
      expect(all('tr[data-accreditation]').length).toBe(2);
      expect(q('[data-notice]')?.textContent).toContain('Accréditation enregistrée');
    });

    it('omits empty optional fields', () => {
      fill('acc-name', 'EQUIS');
      submit();
      const req = backend.expectOne(`${api}/accreditations`);
      expect(req.request.body).toEqual({ name: 'EQUIS' });
      req.flush(acc(9));
      load([acc(1), acc(9)]);
    });

    it('validates without calling the API', () => {
      submit();
      expect(q('[data-error=name]')?.textContent).toContain('obligatoire');

      fill('acc-name', 'x'.repeat(51));
      fill('acc-code', 'ABCDEF');
      fill('acc-description', 'y'.repeat(256));
      submit();
      expect(q('[data-error=name]')?.textContent).toContain('50');
      expect(q('[data-error=code]')?.textContent).toContain('5');
      expect(q('[data-error=description]')?.textContent).toContain('255');
    });

    it('maps 400 field errors onto the fields', () => {
      fill('acc-name', 'EQUIS');
      submit();
      backend
        .expectOne(`${api}/accreditations`)
        .flush({ name: 'Nom invalide' }, { status: 400, statusText: 'Bad Request' });
      settle();
      expect(q('[data-error=name]')?.textContent).toContain('Nom invalide');
    });

    it('explains a 500 (the API answers so on a duplicate code) and keeps the form', () => {
      fill('acc-name', 'EQUIS');
      fill('acc-code', 'A1');
      submit();
      backend.expectOne(`${api}/accreditations`).flush(null, { status: 500, statusText: 'err' });
      settle();
      expect(q('form[data-form] [role=alert]')?.textContent).toContain('code existe peut-être déjà');
      expect(q<HTMLInputElement>('#acc-name')!.value).toBe('EQUIS');
    });

    it('cancels without saving', () => {
      q<HTMLButtonElement>('[data-cancel-form]')!.click();
      settle();
      expect(q('form[data-form]')).toBeNull();
    });
  });

  describe('edit', () => {
    beforeEach(() => {
      load([acc(1), acc(2)]);
      all('[data-edit]')[1].click();
      settle();
    });

    it('edits the row inline, prefilled, and sends a PUT', () => {
      expect(all('tr[data-accreditation]').length).toBe(1);
      expect(q<HTMLInputElement>('#acc-name')!.value).toBe('Accred 2');
      expect(q<HTMLInputElement>('#acc-code')!.value).toBe('A2');
      fill('acc-name', 'Renamed');
      submit();
      const req = backend.expectOne(`${api}/accreditations/2`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ name: 'Renamed', code: 'A2', description: 'Desc 2' });
      req.flush(acc(2, { name: 'Renamed' }));
      settle();
      load([acc(1), acc(2, { name: 'Renamed' })]);
      expect(q('form[data-form]')).toBeNull();
      expect(el().textContent).toContain('Renamed');
    });

    it('can be cancelled', () => {
      q<HTMLButtonElement>('[data-cancel-form]')!.click();
      settle();
      expect(q('form[data-form]')).toBeNull();
      expect(all('tr[data-accreditation]').length).toBe(2);
    });
  });

  describe('delete', () => {
    beforeEach(() => load([acc(1), acc(2)]));

    const ask = () => all('[data-delete]')[0].click();

    it('asks for confirmation with a warning about programs, and can be cancelled', () => {
      ask();
      settle();
      expect(el().textContent).toContain('Supprimer cette accréditation ?');
      expect(el().textContent).toContain('associée à des programmes');
      q<HTMLButtonElement>('[data-cancel-delete]')!.click();
      settle();
      expect(q('[data-confirm-delete]')).toBeNull();
    });

    it('deletes after confirmation and reloads', () => {
      ask();
      settle();
      q<HTMLButtonElement>('[data-confirm-delete]')!.click();
      const req = backend.expectOne(`${api}/accreditations/1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(acc(1));
      load([acc(2)]);
      expect(all('tr[data-accreditation]').length).toBe(1);
      expect(q('[data-notice]')?.textContent).toContain('Accréditation supprimée');
    });

    for (const status of [409, 500]) {
      it(`explains a ${status} on delete (accreditation still used by programs)`, () => {
        ask();
        settle();
        q<HTMLButtonElement>('[data-confirm-delete]')!.click();
        backend.expectOne(`${api}/accreditations/1`).flush(null, { status, statusText: 'err' });
        settle();
        expect(q('[role=alert]')?.textContent).toContain('encore associée');
        expect(q('[data-confirm-delete]')).toBeNull();
        expect(all('tr[data-accreditation]').length).toBe(2);
      });
    }

    it('goes back a page when the only row of the last page is deleted', () => {
      fixture.componentInstance.load(1);
      load([acc(3)], 1, 2, 11);
      all('[data-delete]')[0].click();
      settle();
      q<HTMLButtonElement>('[data-confirm-delete]')!.click();
      backend.expectOne(`${api}/accreditations/3`).flush(acc(3));
      const req = listCall();
      expect(req.request.params.get('page')).toBe('0');
      req.flush(page([acc(1)]));
    });
  });
});
