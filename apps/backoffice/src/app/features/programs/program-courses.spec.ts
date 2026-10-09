import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProgramCourses } from './program-courses';
import { API_URL, SERVER_URL } from '@services/api-config';
import { Program } from '@models/program';

const api = 'http://api.test/api/v1';
const program: Program = {
  id: 4,
  name: 'Master',
  courses: [
    { id: 12, name: 'Statistiques', code: 'STA' },
    { id: 11, name: 'Algèbre', code: 'ALG', photoMediaId: 3 },
  ],
};

describe('ProgramCourses', () => {
  let fixture: ComponentFixture<ProgramCourses>;
  let backend: HttpTestingController;
  let backs: number;
  let changes: number;

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement>(sel: string) => el().querySelector<T>(sel);
  const all = (sel: string) => Array.from(el().querySelectorAll<HTMLElement>(sel));
  const click = (sel: string, index = 0) => {
    all(sel)[index].click();
    fixture.detectChanges();
  };

  function setup(p: Program = program) {
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
    fixture = TestBed.createComponent(ProgramCourses);
    backs = 0;
    changes = 0;
    fixture.componentInstance.back.subscribe(() => backs++);
    fixture.componentInstance.changed.subscribe(() => changes++);
    fixture.componentRef.setInput('program', p);
    fixture.detectChanges();
  }

  afterEach(() => backend.verify());

  it('lists the courses of the program by name', () => {
    setup();
    expect(q('h2')!.textContent).toContain('Master');
    const rows = all('tr[data-course]');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Algèbre');
    expect(rows[0].textContent).toContain('ALG');
    expect(rows[1].textContent).toContain('Statistiques');
  });

  it('shows an empty state', () => {
    setup({ id: 4, name: 'Master', courses: [] });
    expect(all('tr[data-course]').length).toBe(0);
    expect(el().textContent).toContain('Aucun cours');
  });

  it('goes back to the programs', () => {
    setup();
    click('[data-back]');
    expect(backs).toBe(1);
  });

  it('opens an empty form, and cancelling returns to the list without a request', () => {
    setup();
    click('[data-new]');
    expect(q('app-course-form')).not.toBeNull();
    expect(q<HTMLInputElement>('#name')!.value).toBe('');
    click('[data-cancel]');
    expect(q('app-course-form')).toBeNull();
    expect(all('tr[data-course]').length).toBe(2);
  });

  it('opens the form prefilled from a row', () => {
    setup();
    click('[data-edit]', 0);
    expect(q<HTMLInputElement>('#name')!.value).toBe('Algèbre');
  });

  it('creates a course for this program, announces it and tells the page to reload', () => {
    setup();
    click('[data-new]');
    const name = q<HTMLInputElement>('#name')!;
    name.value = 'Probabilités';
    name.dispatchEvent(new Event('input'));
    click('button[type=submit]');
    const req = backend.expectOne(`${api}/courses`);
    expect(req.request.body).toEqual({ name: 'Probabilités', programId: 4 });
    req.flush({ id: 20, name: 'Probabilités' });
    fixture.detectChanges();

    expect(q('app-course-form')).toBeNull();
    expect(q('[data-notice]')!.textContent).toContain('Cours enregistré');
    expect(changes).toBe(1);
  });

  describe('delete', () => {
    it('asks for confirmation before sending anything', () => {
      setup();
      click('[data-delete]', 0);
      expect(all('[data-confirm-delete]').length).toBe(1);
      backend.expectNone(`${api}/courses/11`);
    });

    it('cancelling dismisses the confirmation without a request', () => {
      setup();
      click('[data-delete]', 0);
      click('[data-cancel-delete]');
      expect(all('[data-confirm-delete]').length).toBe(0);
    });

    it('confirming deletes the course and tells the page to reload', () => {
      setup();
      click('[data-delete]', 0);
      click('[data-confirm-delete]');
      const req = backend.expectOne(`${api}/courses/11`);
      expect(req.request.method).toBe('DELETE');
      req.flush({ id: 11, name: 'Algèbre' });
      fixture.detectChanges();
      expect(changes).toBe(1);
      expect(q('[data-notice]')!.textContent).toContain('Cours supprimé');
    });

    it('shows an alert and keeps the row when the delete fails', () => {
      setup();
      click('[data-delete]', 0);
      click('[data-confirm-delete]');
      backend.expectOne(`${api}/courses/11`).flush(null, { status: 500, statusText: 'x' });
      fixture.detectChanges();
      expect(q('[role=alert]')!.textContent).toContain('suppression');
      expect(all('tr[data-course]').length).toBe(2);
      expect(changes).toBe(0);
    });
  });
});
