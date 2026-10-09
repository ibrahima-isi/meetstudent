import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProgramAccreditations } from './program-accreditations';
import { API_URL } from '@services/api-config';

const api = 'http://api.test/api/v1';
const accreditations = [
  { id: 2, name: 'AACSB' },
  { id: 3, name: 'EQUIS' },
];
const linked = [{ programId: 5, accreditationId: 2, accreditation: { id: 2, name: 'AACSB' }, startsAt: 2020, endsAt: 2025 }];

describe('ProgramAccreditations', () => {
  let fixture: ComponentFixture<ProgramAccreditations>;
  let backend: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement>(sel: string) => el().querySelector<T>(sel);
  const all = (sel: string) => Array.from(el().querySelectorAll<HTMLElement>(sel));
  const type = (sel: string, value: string) => {
    const input = q<HTMLInputElement>(sel)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const choose = (value: string) => {
    const select = q<HTMLSelectElement>('#accreditation')!;
    select.value = value;
    select.dispatchEvent(new Event('change'));
  };
  const options = () => all('#accreditation option').map((o) => o.textContent!.trim());

  function setup(links = linked) {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: api },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProgramAccreditations);
    fixture.componentRef.setInput('programId', 5);
    fixture.detectChanges();
    backend.expectOne(`${api}/programs/5/accreditations`).flush(links);
    backend.expectOne((r) => r.url === `${api}/accreditations`).flush({ content: accreditations });
    fixture.detectChanges();
  }

  afterEach(() => backend.verify());

  it('lists the linked accreditations with their years', () => {
    setup();
    const rows = all('li[data-link-row]');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('AACSB');
    expect(rows[0].textContent).toContain('2020');
    expect(rows[0].textContent).toContain('2025');
  });

  it('only offers the accreditations that are not linked yet', () => {
    setup();
    expect(options()).toEqual(['Choisir…', 'EQUIS']);
  });

  it('says so when nothing is linked', () => {
    setup([]);
    expect(el().textContent).toContain('Aucune accréditation');
  });

  it('links the chosen accreditation with its years', () => {
    setup();
    choose('3');
    type('#startsAt', '2021');
    type('#endsAt', '2026');
    q<HTMLButtonElement>('[data-link]')!.click();

    const req = backend.expectOne((r) => r.url === `${api}/programs/5/accreditations/3`);
    expect(req.request.method).toBe('POST');
    expect(req.request.params.get('startsAt')).toBe('2021');
    expect(req.request.params.get('endsAt')).toBe('2026');
    req.flush({ programId: 5, accreditationId: 3, startsAt: 2021, endsAt: 2026 });
    fixture.detectChanges();

    const rows = all('li[data-link-row]');
    expect(rows.length).toBe(2);
    expect(rows[1].textContent).toContain('EQUIS');
    expect(options()).toEqual(['Choisir…']);
  });

  it('sends nothing when the accreditation or a year is missing, or the years are reversed', () => {
    setup();
    q<HTMLButtonElement>('[data-link]')!.click();
    fixture.detectChanges();
    expect(q('[data-link-error]')!.textContent).toContain('accréditation');

    choose('3');
    type('#startsAt', '2026');
    type('#endsAt', '2021');
    q<HTMLButtonElement>('[data-link]')!.click();
    fixture.detectChanges();
    expect(q('[data-link-error]')!.textContent).toContain('après');

    type('#startsAt', '');
    q<HTMLButtonElement>('[data-link]')!.click();
    fixture.detectChanges();
    expect(q('[data-link-error]')!.textContent).toContain('années');
  });

  it('shows an alert and keeps the list when linking fails', () => {
    setup();
    choose('3');
    type('#startsAt', '2021');
    type('#endsAt', '2026');
    q<HTMLButtonElement>('[data-link]')!.click();
    backend.expectOne((r) => r.url === `${api}/programs/5/accreditations/3`).flush(null, { status: 500, statusText: 'x' });
    fixture.detectChanges();
    expect(q('[role=alert]')!.textContent).toContain('association');
    expect(all('li[data-link-row]').length).toBe(1);
  });

  it('unlinks an accreditation', () => {
    setup();
    q<HTMLButtonElement>('[data-unlink]')!.click();
    const req = backend.expectOne(`${api}/programs/5/accreditations/2`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    expect(all('li[data-link-row]').length).toBe(0);
    expect(options()).toEqual(['Choisir…', 'AACSB', 'EQUIS']);
  });

  it('shows an alert when the lists cannot be loaded', () => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: api },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProgramAccreditations);
    fixture.componentRef.setInput('programId', 5);
    fixture.detectChanges();
    backend.expectOne(`${api}/programs/5/accreditations`).flush(null, { status: 500, statusText: 'x' });
    backend.expectOne((r) => r.url === `${api}/accreditations`).flush({ content: accreditations });
    fixture.detectChanges();
    expect(q('[role=alert]')!.textContent).toContain('accréditations');
  });
});
