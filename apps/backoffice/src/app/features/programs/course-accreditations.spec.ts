import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_URL } from '@services/api-config';
import { CourseAccreditations } from './course-accreditations';

const api = 'http://api.test/api/v1';
const known = [{ id: 2, name: 'AACSB' }, { id: 3, name: 'EQUIS' }];
const linked = [{ courseId: 9, accreditationId: 2, accreditation: known[0], startsAt: 2020, endsAt: 2025 }];

describe('CourseAccreditations', () => {
  let fixture: ComponentFixture<CourseAccreditations>;
  let backend: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement>(selector: string) => el().querySelector<T>(selector);
  const rows = () => Array.from(el().querySelectorAll('[data-link-row]'));
  const options = () => Array.from(el().querySelectorAll('#course-accreditation option')).map((option) => option.textContent!.trim());
  const type = (selector: string, value: string) => {
    const input = q<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const choose = (value: string) => {
    const select = q<HTMLSelectElement>('#course-accreditation')!;
    select.value = value;
    select.dispatchEvent(new Event('change'));
  };

  function setup(links = linked, laterLinks: typeof linked | null = null, laterCatalog: typeof known | null = null) {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: api },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CourseAccreditations);
    fixture.componentRef.setInput('courseId', 9);
    fixture.detectChanges();
    backend.expectOne((request) => request.url === `${api}/courses/9/accreditations` && request.params.get('page') === '0')
      .flush({ content: links, totalPages: laterLinks ? 2 : 1 });
    if (laterLinks) {
      backend.expectOne((request) => request.url === `${api}/courses/9/accreditations` && request.params.get('page') === '1')
        .flush({ content: laterLinks, totalPages: 2 });
    }
    backend.expectOne((request) => request.url === `${api}/accreditations` && request.params.get('page') === '0')
      .flush({ content: known, totalPages: laterCatalog ? 2 : 1 });
    if (laterCatalog) {
      backend.expectOne((request) => request.url === `${api}/accreditations` && request.params.get('page') === '1')
        .flush({ content: laterCatalog, totalPages: 2 });
    }
    fixture.detectChanges();
  }

  afterEach(() => backend.verify());

  it('shows the course link and offers only other accreditations', () => {
    setup();
    expect(rows().length).toBe(1);
    expect(rows()[0].textContent).toContain('AACSB');
    expect(options()).toEqual(['Choisir…', 'EQUIS']);
  });

  it('links a selected accreditation with validity years', () => {
    setup();
    choose('3');
    type('#course-startsAt', '2021');
    type('#course-endsAt', '2026');
    q<HTMLButtonElement>('[data-link]')!.click();
    const request = backend.expectOne((candidate) => candidate.url === `${api}/courses/9/accreditations/3`);
    expect(request.request.method).toBe('POST');
    expect(request.request.params.get('startsAt')).toBe('2021');
    expect(request.request.params.get('endsAt')).toBe('2026');
    request.flush({ courseId: 9, accreditationId: 3, accreditation: known[1], startsAt: 2021, endsAt: 2026 });
    fixture.detectChanges();
    expect(rows().length).toBe(2);
    expect(options()).toEqual(['Choisir…']);
  });

  it('unlinks only the chosen course association and keeps the accreditation selectable', () => {
    setup();
    q<HTMLButtonElement>('[data-unlink]')!.click();
    const request = backend.expectOne(`${api}/courses/9/accreditations/2`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    expect(rows().length).toBe(0);
    expect(options()).toEqual(['Choisir…', 'AACSB', 'EQUIS']);
    expect(q('[data-notice]')!.textContent).toContain('Lien retiré');
  });

  it('keeps the link and explains a missing association on rejected unlink', () => {
    setup();
    q<HTMLButtonElement>('[data-unlink]')!.click();
    backend.expectOne(`${api}/courses/9/accreditations/2`)
      .flush({ message: 'Association non trouvée' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(rows().length).toBe(1);
    expect(q('[role=alert]')!.textContent).toContain('introuvable');
  });

  it('keeps a linked accreditation outside the first catalog page selectable after unlink', () => {
    setup([{ courseId: 9, accreditationId: 250, accreditation: { id: 250, name: 'AMBA' }, startsAt: 2020, endsAt: 2025 }]);
    q<HTMLButtonElement>('[data-unlink]')!.click();
    backend.expectOne(`${api}/courses/9/accreditations/250`)
      .flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    expect(options()).toContain('AMBA');
  });

  it('loads later pages of course links so every association can be removed', () => {
    setup(linked, [{ courseId: 9, accreditationId: 250, accreditation: { id: 250, name: 'AMBA' }, startsAt: 2020, endsAt: 2025 }]);
    expect(rows().length).toBe(2);
    q<HTMLButtonElement>('[data-link-row]:last-child [data-unlink]')!.click();
    backend.expectOne(`${api}/courses/9/accreditations/250`)
      .flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    expect(rows().length).toBe(1);
    expect(options()).toContain('AMBA');
  });

  it('can select a preserved accreditation beyond page one after reopening', () => {
    setup([], null, [{ id: 250, name: 'AMBA' }]);
    expect(options()).toContain('AMBA');
  });
});
