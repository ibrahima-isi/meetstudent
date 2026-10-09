import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProgramForm } from './program-form';
import { API_URL, SERVER_URL } from '@services/api-config';
import { Program } from '@models/program';
import { School } from '@models/school';

const api = 'http://api.test/api/v1';
const schools: School[] = [
  { id: 3, name: 'Ecole A' },
  { id: 8, name: 'Ecole B' },
];
const existing: Program = {
  id: 7,
  code: 'MST',
  name: 'Master',
  duration: 2,
  schoolId: 8,
  photoMediaId: 4,
  photo: { id: 4, publicUrl: '/uploads/public/p.png' },
};

describe('ProgramForm', () => {
  let fixture: ComponentFixture<ProgramForm>;
  let backend: HttpTestingController;
  let saved: Program[];
  let cancelled: number;

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement>(sel: string) => el().querySelector<T>(sel);
  const type = (sel: string, value: string) => {
    const input = q<HTMLInputElement>(sel)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const choose = (value: string) => {
    const select = q<HTMLSelectElement>('#schoolId')!;
    select.value = value;
    select.dispatchEvent(new Event('change'));
  };
  const submit = () => {
    q<HTMLButtonElement>('button[type=submit]')!.click();
    fixture.detectChanges();
  };
  const pick = (f: File) => {
    const dt = new DataTransfer();
    dt.items.add(f);
    const input = q<HTMLInputElement>('#photo')!;
    input.files = dt.files;
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };
  const png = (name = 'p.png') => new File([new Uint8Array(20)], name, { type: 'image/png' });
  const errorOf = (field: string) => q(`[data-error="${field}"]`)?.textContent?.trim() ?? '';

  function setup(program: Program | null = null, list: School[] = schools) {
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
    fixture = TestBed.createComponent(ProgramForm);
    saved = [];
    cancelled = 0;
    fixture.componentInstance.saved.subscribe((p) => saved.push(p));
    fixture.componentInstance.cancelled.subscribe(() => cancelled++);
    fixture.componentRef.setInput('program', program);
    fixture.componentRef.setInput('schools', list);
    fixture.detectChanges();
    if (program) {
      backend.expectOne(`${api}/programs/${program.id}/accreditations`).flush([]);
      backend.expectOne((r) => r.url === `${api}/accreditations`).flush({ content: [] });
      fixture.detectChanges();
    }
  }

  afterEach(() => backend.verify());

  it('offers no accreditations before the program exists', () => {
    setup();
    expect(q('app-program-accreditations')).toBeNull();
    expect(el().textContent).toContain('Enregistrez la filière');
  });

  it('shows the accreditations of an existing program', () => {
    setup(existing);
    expect(q('app-program-accreditations')).not.toBeNull();
  });

  it('prefills the fields, the school and the photo on edit', () => {
    setup(existing);
    expect(q('h2')!.textContent).toContain('Modifier');
    expect(q<HTMLInputElement>('#name')!.value).toBe('Master');
    expect(q<HTMLInputElement>('#code')!.value).toBe('MST');
    expect(q<HTMLInputElement>('#duration')!.value).toBe('2');
    expect(q<HTMLSelectElement>('#schoolId')!.value).toBe('8');
    expect(q<HTMLImageElement>('img[data-preview]')!.getAttribute('src')).toBe('http://srv.test/uploads/public/p.png');
  });

  it('lists the schools, with none selected on create', () => {
    setup();
    const labels = Array.from(el().querySelectorAll('#schoolId option')).map((o) => o.textContent!.trim());
    expect(labels).toEqual(['Aucune', 'Ecole A', 'Ecole B']);
    expect(q<HTMLSelectElement>('#schoolId')!.value).toBe('');
  });

  it('keeps the program school selectable when the list does not contain it', () => {
    setup(existing, [schools[0]]);
    expect(q<HTMLSelectElement>('#schoolId')!.value).toBe('8');
  });

  it('requires a name and sends nothing while invalid', () => {
    setup();
    submit();
    backend.expectNone(`${api}/programs`);
    expect(errorOf('name')).toContain('obligatoire');
  });

  it('mirrors the column limits: name 50, code 5', () => {
    setup();
    type('#name', 'n'.repeat(51));
    type('#code', 'ABCDEF');
    submit();
    backend.expectNone(`${api}/programs`);
    expect(errorOf('name')).toContain('50');
    expect(errorOf('code')).toContain('5');
  });

  it('accepts only a positive whole number of years', () => {
    setup();
    type('#name', 'Master');
    for (const bad of ['0', '-1', '1.5', 'abc']) {
      type('#duration', bad);
      submit();
      backend.expectNone(`${api}/programs`);
      expect(errorOf('duration')).toContain('entier');
    }
    type('#duration', '');
    submit();
    backend.expectOne(`${api}/programs`).flush({ id: 1, name: 'Master' });
  });

  it('creates with the trimmed values, then emits saved', () => {
    setup();
    type('#name', '  Master ');
    type('#code', 'MST');
    type('#duration', '2');
    choose('3');
    submit();
    const req = backend.expectOne(`${api}/programs`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: 'MST', name: 'Master', duration: 2, schoolId: 3 });
    req.flush({ id: 1, name: 'Master' });
    expect(saved.length).toBe(1);
  });

  it('omits what is empty instead of sending blanks', () => {
    setup();
    type('#name', 'Master');
    submit();
    const req = backend.expectOne(`${api}/programs`);
    expect(req.request.body).toEqual({ name: 'Master' });
    req.flush({ id: 1, name: 'Master' });
  });

  it('updates with PUT and leaves the photo out when none was chosen', () => {
    setup(existing);
    type('#name', 'Master 2');
    submit();
    const req = backend.expectOne(`${api}/programs/7`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ code: 'MST', name: 'Master 2', duration: 2, schoolId: 8 });
    req.flush({ id: 7, name: 'Master 2' });
    expect(saved.length).toBe(1);
  });

  it('uploads the photo as a program photo first, then attaches its id', () => {
    setup();
    type('#name', 'Master');
    pick(png());
    expect(q<HTMLImageElement>('img[data-preview]')!.getAttribute('src')).toMatch(/^blob:/);
    submit();
    const up = backend.expectOne(`${api}/media`);
    expect((up.request.body as FormData).get('category')).toBe('PROGRAM_PHOTO');
    up.flush({ id: 21 });
    const req = backend.expectOne(`${api}/programs`);
    expect(req.request.body).toEqual({ name: 'Master', photoMediaId: 21 });
    req.flush({ id: 1, name: 'Master' });
  });

  it('stops with an alert when the upload fails and does not re-upload once it worked', () => {
    setup();
    type('#name', 'Master');
    pick(png());
    submit();
    backend.expectOne(`${api}/media`).flush(null, { status: 500, statusText: 'x' });
    fixture.detectChanges();
    expect(q('[role=alert]')!.textContent).toContain('image');
    backend.expectNone(`${api}/programs`);

    submit();
    backend.expectOne(`${api}/media`).flush({ id: 21 });
    backend.expectOne(`${api}/programs`).flush(null, { status: 500, statusText: 'x' });
    fixture.detectChanges();

    submit();
    backend.expectNone(`${api}/media`);
    expect(backend.expectOne(`${api}/programs`).request.body.photoMediaId).toBe(21);
  });

  it('maps server field errors onto the controls, and unknown ones to an alert', () => {
    setup();
    type('#name', 'Master');
    submit();
    backend
      .expectOne(`${api}/programs`)
      .flush({ code: 'Code déjà utilisé', other: 'Autre souci' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(errorOf('code')).toBe('Code déjà utilisé');
    expect(q('[role=alert]')!.textContent).toContain('Autre souci');
  });

  it('shows an alert on a server failure and re-enables the button', () => {
    setup();
    type('#name', 'Master');
    submit();
    backend.expectOne(`${api}/programs`).flush(null, { status: 500, statusText: 'x' });
    fixture.detectChanges();
    expect(q('[role=alert]')!.textContent).toContain('unique');
    expect(q<HTMLButtonElement>('button[type=submit]')!.disabled).toBeFalse();
  });

  it('does not submit twice while saving', () => {
    setup();
    type('#name', 'Master');
    submit();
    submit();
    backend.expectOne(`${api}/programs`).flush({ id: 1, name: 'Master' });
  });

  it('emits cancelled', () => {
    setup();
    q<HTMLButtonElement>('[data-cancel]')!.click();
    expect(cancelled).toBe(1);
  });
});
