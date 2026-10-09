import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { SchoolForm } from './school-form';
import { API_URL, SERVER_URL } from '@services/api-config';
import { School } from '@models/school';

const api = 'http://api.test/api/v1';
const tags = [
  { id: 1, name: 'PUBLIC' },
  { id: 2, name: 'PRIVATE' },
];

const existing: School = {
  id: 7,
  code: 'ESS',
  name: 'Ecole Sup',
  address: { location: '1 rue A', city: 'Lyon', country: 'France' },
  tags: [{ id: 2, name: 'PRIVATE' }],
  logoMediaId: 3,
  logo: { id: 3, publicUrl: '/uploads/public/logo.png' },
};

describe('SchoolForm', () => {
  let fixture: ComponentFixture<SchoolForm>;
  let backend: HttpTestingController;
  let saved: School[];
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
  const pick = (sel: string, f: File) => {
    const dt = new DataTransfer();
    dt.items.add(f);
    const input = q<HTMLInputElement>(sel)!;
    input.files = dt.files;
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };
  const png = (name = 'logo.png', size = 20) => new File([new Uint8Array(size)], name, { type: 'image/png' });
  const errorOf = (field: string) => q(`[data-error="${field}"]`)?.textContent?.trim() ?? '';

  function setup(school: School | null = null) {
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
    fixture = TestBed.createComponent(SchoolForm);
    saved = [];
    cancelled = 0;
    fixture.componentInstance.saved.subscribe((s) => saved.push(s));
    fixture.componentInstance.cancelled.subscribe(() => cancelled++);
    fixture.componentRef.setInput('school', school);
    fixture.detectChanges();
    backend.expectOne(`${api}/tags`).flush(tags);
    fixture.detectChanges();
  }

  afterEach(() => backend.verify());

  it('renders a checkbox per tag, unchecked on create', () => {
    setup();
    const boxes = el().querySelectorAll<HTMLInputElement>('input[data-tag]');
    expect(boxes.length).toBe(2);
    expect(Array.from(boxes).every((b) => !b.checked)).toBeTrue();
  });

  it('prefills the fields and checks the school tags on edit', () => {
    setup(existing);
    expect(q<HTMLInputElement>('#name')!.value).toBe('Ecole Sup');
    expect(q<HTMLInputElement>('#code')!.value).toBe('ESS');
    expect(q<HTMLInputElement>('#city')!.value).toBe('Lyon');
    const checked = Array.from(el().querySelectorAll<HTMLInputElement>('input[data-tag]')).map((b) => b.checked);
    expect(checked).toEqual([false, true]);
  });

  it('shows the current logo from the server root', () => {
    setup(existing);
    expect(q<HTMLImageElement>('img[data-preview="logo"]')!.getAttribute('src')).toBe(
      'http://srv.test/uploads/public/logo.png',
    );
  });

  it('requires a name and sends nothing while invalid', () => {
    setup();
    submit();
    backend.expectNone(`${api}/schools`);
    expect(errorOf('name')).toContain('obligatoire');
  });

  it('mirrors the column limits: name 50, code 5, address parts 255', () => {
    setup();
    type('#name', 'n'.repeat(51));
    type('#code', 'ABCDEF');
    type('#location', 'l'.repeat(256));
    type('#city', 'c'.repeat(256));
    type('#country', 'k'.repeat(256));
    submit();
    backend.expectNone(`${api}/schools`);
    for (const f of ['name', 'code', 'location', 'city', 'country']) {
      expect(errorOf(f)).toContain('caractères');
    }
    type('#name', 'n'.repeat(50));
    type('#code', 'ABCDE');
    type('#location', 'l'.repeat(255));
    type('#city', 'c'.repeat(255));
    type('#country', 'k'.repeat(255));
    fixture.detectChanges();
    for (const f of ['name', 'code', 'location', 'city', 'country']) expect(errorOf(f)).toBe('');
  });

  it('creates with the trimmed values and the checked tags, then emits saved', () => {
    setup();
    type('#name', '  Nouvelle École ');
    type('#code', 'NE');
    type('#city', 'Paris');
    type('#country', 'France');
    el().querySelectorAll<HTMLInputElement>('input[data-tag]')[0].click();
    submit();

    const req = backend.expectOne(`${api}/schools`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      code: 'NE',
      name: 'Nouvelle École',
      address: { location: '', city: 'Paris', country: 'France' },
      tags: [{ id: 1, name: 'PUBLIC' }],
    });
    req.flush({ id: 9, name: 'Nouvelle École' });
    expect(saved.length).toBe(1);
    expect(saved[0].id).toBe(9);
  });

  it('updates with PUT and leaves the media ids out when no file was chosen', () => {
    setup(existing);
    type('#name', 'Ecole Sup 2');
    el().querySelectorAll<HTMLInputElement>('input[data-tag]')[0].click();
    submit();

    const req = backend.expectOne(`${api}/schools/7`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      code: 'ESS',
      name: 'Ecole Sup 2',
      address: { location: '1 rue A', city: 'Lyon', country: 'France' },
      tags: [
        { id: 1, name: 'PUBLIC' },
        { id: 2, name: 'PRIVATE' },
      ],
    });
    req.flush({ ...existing, name: 'Ecole Sup 2' });
    expect(saved.length).toBe(1);
  });

  it('omits an empty code instead of sending a blank one', () => {
    setup();
    type('#name', 'X');
    submit();
    const req = backend.expectOne(`${api}/schools`);
    expect('code' in req.request.body).toBeFalse();
    req.flush({ id: 1, name: 'X' });
  });

  it('rejects a bad file at selection time, shows why, and uploads nothing', () => {
    setup();
    pick('#logo-file', new File(['x'], 'doc.pdf', { type: 'application/pdf' }));
    expect(errorOf('logo')).toContain('JPEG, PNG ou WebP');
    type('#name', 'X');
    submit();
    backend.expectNone(`${api}/media`);
    backend.expectOne(`${api}/schools`).flush({ id: 1, name: 'X' });
  });

  it('previews a valid file locally before saving', () => {
    setup();
    pick('#cover-file', png('cover.png'));
    expect(q<HTMLImageElement>('img[data-preview="cover"]')!.getAttribute('src')).toMatch(/^blob:/);
    expect(errorOf('cover')).toBe('');
  });

  it('uploads logo and cover first, then attaches their ids on create', () => {
    setup();
    pick('#logo-file', png('logo.png'));
    pick('#cover-file', png('cover.png'));
    type('#name', 'X');
    submit();

    const uploads = backend.match(`${api}/media`);
    expect(uploads.length).toBe(2);
    const byCategory = (c: string): TestRequest =>
      uploads.find((r) => (r.request.body as FormData).get('category') === c)!;
    backend.expectNone(`${api}/schools`);
    byCategory('SCHOOL_LOGO').flush({ id: 11 });
    byCategory('SCHOOL_COVER').flush({ id: 12 });

    const req = backend.expectOne(`${api}/schools`);
    expect(req.request.body.logoMediaId).toBe(11);
    expect(req.request.body.coverMediaId).toBe(12);
    req.flush({ id: 1, name: 'X' });
    expect(saved.length).toBe(1);
  });

  it('attaches only the new image on edit', () => {
    setup(existing);
    pick('#cover-file', png('cover.png'));
    submit();
    backend.expectOne(`${api}/media`).flush({ id: 12 });
    const req = backend.expectOne(`${api}/schools/7`);
    expect(req.request.body.coverMediaId).toBe(12);
    expect('logoMediaId' in req.request.body).toBeFalse();
    req.flush(existing);
  });

  it('stops and shows an alert when an upload fails, then does not re-upload on retry once it worked', () => {
    setup();
    pick('#logo-file', png());
    type('#name', 'X');
    submit();
    backend.expectOne(`${api}/media`).flush(null, { status: 500, statusText: 'err' });
    fixture.detectChanges();
    backend.expectNone(`${api}/schools`);
    expect(q('[role=alert]')?.textContent).toContain('image');

    submit();
    backend.expectOne(`${api}/media`).flush({ id: 11 });
    // the school save itself fails: the uploaded id must be kept for the next try
    backend.expectOne(`${api}/schools`).flush(null, { status: 500, statusText: 'err' });
    fixture.detectChanges();
    submit();
    backend.expectNone(`${api}/media`);
    const req = backend.expectOne(`${api}/schools`);
    expect(req.request.body.logoMediaId).toBe(11);
    req.flush({ id: 1, name: 'X' });
  });

  it('maps server field errors onto the matching controls without an alert', () => {
    setup();
    type('#name', 'X');
    submit();
    backend
      .expectOne(`${api}/schools`)
      .flush({ name: 'Nom déjà pris', 'address.city': 'Ville invalide', code: 'Code invalide' }, {
        status: 400,
        statusText: 'Bad Request',
      });
    fixture.detectChanges();

    expect(errorOf('name')).toBe('Nom déjà pris');
    expect(errorOf('city')).toBe('Ville invalide');
    expect(errorOf('code')).toBe('Code invalide');
    expect(q('[role=alert]')).toBeNull();
    expect(saved.length).toBe(0);
  });

  it('clears a server error once the user edits that field', () => {
    setup();
    type('#name', 'X');
    submit();
    backend.expectOne(`${api}/schools`).flush({ name: 'Nom déjà pris' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    type('#name', 'Y');
    fixture.detectChanges();
    expect(errorOf('name')).toBe('');
  });

  it('shows an alert for a field error the form has no control for', () => {
    setup();
    type('#name', 'X');
    submit();
    backend.expectOne(`${api}/schools`).flush({ programs: 'invalid' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(q('[role=alert]')?.textContent).toContain('invalid');
  });

  it('shows an alert on a server failure and re-enables the button', () => {
    setup();
    type('#name', 'X');
    submit();
    backend.expectOne(`${api}/schools`).flush(null, { status: 500, statusText: 'err' });
    fixture.detectChanges();
    expect(q('[role=alert]')?.textContent).toContain('échoué');
    expect(q<HTMLButtonElement>('button[type=submit]')!.disabled).toBeFalse();
  });

  it('does not submit twice while saving', () => {
    setup();
    type('#name', 'X');
    submit();
    expect(q<HTMLButtonElement>('button[type=submit]')!.disabled).toBeTrue();
    submit();
    backend.expectOne(`${api}/schools`).flush({ id: 1, name: 'X' });
  });

  it('warns when the tag list cannot be loaded but keeps the form usable', () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: api },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SchoolForm);
    fixture.componentRef.setInput('school', null);
    fixture.detectChanges();
    backend.expectOne(`${api}/tags`).flush(null, { status: 500, statusText: 'err' });
    fixture.detectChanges();
    expect(q('[role=alert]')?.textContent).toContain('tags');
    expect(q('#name')).not.toBeNull();
  });

  it('emits cancelled', () => {
    setup();
    q<HTMLButtonElement>('[data-cancel]')!.click();
    expect(cancelled).toBe(1);
  });
});
