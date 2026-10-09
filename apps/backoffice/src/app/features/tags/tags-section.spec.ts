import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TagsSection } from './tags-section';
import { API_URL } from '@services/api-config';
import { Tag } from '@models/school';

const api = 'http://api.test/api/v1';
const tags: Tag[] = [
  { id: 1, name: 'PUBLIC' },
  { id: 2, name: 'PRIVE' },
];

describe('TagsSection', () => {
  let fixture: ComponentFixture<TagsSection>;
  let backend: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement>(sel: string) => el().querySelector<T>(sel);
  const all = (sel: string) => Array.from(el().querySelectorAll<HTMLElement>(sel));
  const settle = () => fixture.detectChanges();
  const listCall = () => backend.expectOne(`${api}/tags`);
  const load = (content: Tag[] = tags) => {
    listCall().flush(content);
    settle();
  };
  const submitName = (value: string) => {
    const input = q<HTMLInputElement>('#tag-name')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    q<HTMLFormElement>('form[data-create]')!.dispatchEvent(new Event('submit'));
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
    fixture = TestBed.createComponent(TagsSection);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('shows a loading state, then the tags', () => {
    expect(el().textContent).toContain('Chargement');
    load();
    expect(el().textContent).not.toContain('Chargement');
    const rows = all('tr[data-tag]');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('PUBLIC');
  });

  it('shows an empty state', () => {
    load([]);
    expect(el().textContent).toContain('Aucun tag');
  });

  it('shows an error with a retry that reloads', () => {
    listCall().flush(null, { status: 500, statusText: 'err' });
    settle();
    expect(q('[role=alert]')?.textContent).toContain('Impossible de charger');
    expect(all('tr[data-tag]').length).toBe(0);

    q<HTMLButtonElement>('[data-retry]')!.click();
    load([tags[0]]);
    expect(q('[role=alert]')).toBeNull();
    expect(all('tr[data-tag]').length).toBe(1);
  });

  it('ignores a stale response that arrives after a newer one', () => {
    const first = backend.expectOne(`${api}/tags`);
    fixture.componentInstance.load();
    const second = backend.expectOne(`${api}/tags`);
    second.flush([tags[1]]);
    first.flush([tags[0]]);
    settle();
    const rows = all('tr[data-tag]');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('PRIVE');
  });

  it('offers no edit action: the API has no tag update endpoint', () => {
    load();
    expect(q('[data-edit]')).toBeNull();
  });

  describe('create', () => {
    beforeEach(() => load());

    it('posts the trimmed name, reloads and confirms', () => {
      submitName('  MASTER ');
      const req = backend.expectOne(`${api}/tags`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ name: 'MASTER' });
      req.flush({ id: 3, name: 'MASTER' });
      load([...tags, { id: 3, name: 'MASTER' }]);

      expect(all('tr[data-tag]').length).toBe(3);
      expect(q('[data-notice]')?.textContent).toContain('Tag ajouté');
      expect(q<HTMLInputElement>('#tag-name')!.value).toBe('');
    });

    it('refuses a blank name without calling the API', () => {
      submitName('   ');
      expect(q('[data-error=name]')?.textContent).toContain('obligatoire');
    });

    it('refuses a name longer than 255 characters', () => {
      submitName('x'.repeat(256));
      expect(q('[data-error=name]')?.textContent).toContain('255');
    });

    it('refuses a duplicate (case-insensitive) without calling the API', () => {
      submitName('public');
      expect(q('[data-error=name]')?.textContent).toContain('existe déjà');
    });

    it('maps a 400 field error onto the name field', () => {
      submitName('NEW');
      backend.expectOne(`${api}/tags`).flush({ name: 'Nom invalide' }, { status: 400, statusText: 'Bad Request' });
      settle();
      expect(q('[data-error=name]')?.textContent).toContain('Nom invalide');
    });

    it('explains a 500 (the API answers so on a duplicate name)', () => {
      submitName('NEW');
      backend.expectOne(`${api}/tags`).flush(null, { status: 500, statusText: 'err' });
      settle();
      expect(q('[role=alert]')?.textContent).toContain('existe peut-être déjà');
      expect(q<HTMLInputElement>('#tag-name')!.value).toBe('NEW');
    });
  });

  describe('delete', () => {
    beforeEach(() => load());

    const ask = () => all('[data-delete]')[0].click();

    it('asks for confirmation with a warning about schools, and can be cancelled', () => {
      ask();
      settle();
      expect(el().textContent).toContain('Supprimer ce tag ?');
      expect(el().textContent).toContain('utilisé par des écoles');
      q<HTMLButtonElement>('[data-cancel-delete]')!.click();
      settle();
      expect(q('[data-confirm-delete]')).toBeNull();
    });

    it('deletes after confirmation and reloads', () => {
      ask();
      settle();
      q<HTMLButtonElement>('[data-confirm-delete]')!.click();
      const req = backend.expectOne(`${api}/tags/1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });
      load([tags[1]]);
      expect(all('tr[data-tag]').length).toBe(1);
      expect(q('[data-notice]')?.textContent).toContain('Tag supprimé');
    });

    for (const status of [409, 500]) {
      it(`explains a ${status} on delete (tag still used by schools)`, () => {
        ask();
        settle();
        q<HTMLButtonElement>('[data-confirm-delete]')!.click();
        backend.expectOne(`${api}/tags/1`).flush(null, { status, statusText: 'err' });
        settle();
        expect(q('[role=alert]')?.textContent).toContain('encore utilisé');
        expect(q('[data-confirm-delete]')).toBeNull();
        expect(all('tr[data-tag]').length).toBe(2);
      });
    }
  });
});
