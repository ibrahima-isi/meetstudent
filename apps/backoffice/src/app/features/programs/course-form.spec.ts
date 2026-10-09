import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseForm } from './course-form';
import { API_URL, SERVER_URL } from '@services/api-config';
import { Course } from '@models/course';

const api = 'http://api.test/api/v1';
const existing: Course = { id: 9, code: 'ALG', name: 'Algèbre', photoMediaId: 4 };

describe('CourseForm', () => {
  let fixture: ComponentFixture<CourseForm>;
  let backend: HttpTestingController;
  let saved: Course[];
  let cancelled: number;

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement>(sel: string) => el().querySelector<T>(sel);
  const type = (sel: string, value: string) => {
    const input = q<HTMLInputElement>(sel)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
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
  const png = (name = 'c.png') => new File([new Uint8Array(20)], name, { type: 'image/png' });
  const errorOf = (field: string) => q(`[data-error="${field}"]`)?.textContent?.trim() ?? '';

  function setup(course: Course | null = null) {
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
    fixture = TestBed.createComponent(CourseForm);
    saved = [];
    cancelled = 0;
    fixture.componentInstance.saved.subscribe((c) => saved.push(c));
    fixture.componentInstance.cancelled.subscribe(() => cancelled++);
    fixture.componentRef.setInput('programId', 4);
    fixture.componentRef.setInput('course', course);
    fixture.detectChanges();
  }

  afterEach(() => backend.verify());

  it('titles the form after the mode', () => {
    setup();
    expect(q('h2')!.textContent).toContain('Nouveau cours');
  });

  it('prefills the fields on edit and says a photo exists', () => {
    setup(existing);
    expect(q('h2')!.textContent).toContain('Modifier');
    expect(q<HTMLInputElement>('#name')!.value).toBe('Algèbre');
    expect(q<HTMLInputElement>('#code')!.value).toBe('ALG');
    expect(el().textContent).toContain('déjà enregistrée');
  });

  it('requires a name and sends nothing while invalid', () => {
    setup();
    type('#name', '   ');
    submit();
    backend.expectNone(`${api}/courses`);
    expect(errorOf('name')).toContain('obligatoire');
  });

  it('mirrors the column limits: name 50, code 5', () => {
    setup();
    type('#name', 'n'.repeat(51));
    type('#code', 'ABCDEF');
    submit();
    backend.expectNone(`${api}/courses`);
    expect(errorOf('name')).toContain('50');
    expect(errorOf('code')).toContain('5');
    type('#name', 'n'.repeat(50));
    type('#code', 'ABCDE');
    fixture.detectChanges();
    expect(errorOf('name')).toBe('');
    expect(errorOf('code')).toBe('');
  });

  it('creates with the trimmed values and the program id, then emits saved', () => {
    setup();
    type('#name', '  Algèbre ');
    type('#code', 'ALG');
    submit();
    const req = backend.expectOne(`${api}/courses`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: 'ALG', name: 'Algèbre', programId: 4 });
    req.flush({ id: 11, name: 'Algèbre' });
    expect(saved.length).toBe(1);
  });

  it('updates with PUT, omitting an empty code and the photo when none was chosen', () => {
    setup({ id: 9, name: 'Algèbre' });
    type('#name', 'Algèbre 2');
    submit();
    const req = backend.expectOne(`${api}/courses/9`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ name: 'Algèbre 2', programId: 4 });
    req.flush({ id: 9, name: 'Algèbre 2' });
    expect(saved.length).toBe(1);
  });

  it('uploads the photo as a course photo first, then attaches its id', () => {
    setup();
    type('#name', 'Algèbre');
    pick(png());
    submit();
    const up = backend.expectOne(`${api}/media`);
    const form = up.request.body as FormData;
    expect(form.get('category')).toBe('COURSE_PHOTO');
    expect((form.get('file') as File).name).toBe('c.png');
    up.flush({ id: 21 });
    const req = backend.expectOne(`${api}/courses`);
    expect(req.request.body).toEqual({ name: 'Algèbre', programId: 4, photoMediaId: 21 });
    req.flush({ id: 11, name: 'Algèbre' });
  });

  it('does not upload a rejected file', () => {
    setup();
    pick(new File(['x'], 'doc.pdf', { type: 'application/pdf' }));
    type('#name', 'Algèbre');
    submit();
    backend.expectNone(`${api}/media`);
    backend.expectOne(`${api}/courses`).flush({ id: 1, name: 'Algèbre' });
  });

  it('stops with an alert when the upload fails and does not re-upload once it worked', () => {
    setup();
    type('#name', 'Algèbre');
    pick(png());
    submit();
    backend.expectOne(`${api}/media`).flush(null, { status: 500, statusText: 'x' });
    fixture.detectChanges();
    expect(q('[role=alert]')!.textContent).toContain("image");
    backend.expectNone(`${api}/courses`);

    submit();
    backend.expectOne(`${api}/media`).flush({ id: 21 });
    backend.expectOne(`${api}/courses`).flush(null, { status: 500, statusText: 'x' });
    fixture.detectChanges();

    submit();
    backend.expectNone(`${api}/media`);
    expect(backend.expectOne(`${api}/courses`).request.body.photoMediaId).toBe(21);
  });

  it('maps server field errors onto the controls without an alert', () => {
    setup();
    type('#name', 'Algèbre');
    submit();
    backend.expectOne(`${api}/courses`).flush({ code: 'Code déjà utilisé' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(errorOf('code')).toBe('Code déjà utilisé');
    expect(q('[role=alert]')).toBeNull();
  });

  it('shows an alert on a server failure and re-enables the button', () => {
    setup();
    type('#name', 'Algèbre');
    submit();
    backend.expectOne(`${api}/courses`).flush(null, { status: 500, statusText: 'x' });
    fixture.detectChanges();
    expect(q('[role=alert]')!.textContent).toContain('unique');
    expect(q<HTMLButtonElement>('button[type=submit]')!.disabled).toBeFalse();
  });

  it('does not submit twice while saving', () => {
    setup();
    type('#name', 'Algèbre');
    submit();
    submit();
    backend.expectOne(`${api}/courses`).flush({ id: 1, name: 'Algèbre' });
  });

  it('emits cancelled', () => {
    setup();
    q<HTMLButtonElement>('[data-cancel]')!.click();
    expect(cancelled).toBe(1);
  });
});
