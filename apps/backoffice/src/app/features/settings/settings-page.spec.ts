import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SettingsPage } from './settings-page';
import { API_URL } from '@services/api-config';

const api = 'http://api.test/api/v1';
const url = `${api}/site-settings`;
const settings = { contactEmail: 'contact@meetstudent.com', aboutFr: 'Bonjour.\n\nSuite.', aboutEn: 'Hello.' };

describe('SettingsPage', () => {
  let fixture: ComponentFixture<SettingsPage>;
  let backend: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement>(sel: string) => el().querySelector<T>(sel);
  const settle = () => fixture.detectChanges();
  const load = () => {
    backend.expectOne(url).flush(settings);
    settle();
  };
  const type = (sel: string, value: string) => {
    const field = q<HTMLInputElement | HTMLTextAreaElement>(sel)!;
    field.value = value;
    field.dispatchEvent(new Event('input'));
    settle();
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
    fixture = TestBed.createComponent(SettingsPage);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('is titled "Paramètres du site" and shows a loading state, then the current values', () => {
    expect(q('h1')?.textContent).toContain('Paramètres du site');
    expect(el().textContent).toContain('Chargement');
    load();
    expect(el().textContent).not.toContain('Chargement');
    expect(q<HTMLInputElement>('#settings-contactEmail')!.value).toBe('contact@meetstudent.com');
    expect(q<HTMLTextAreaElement>('#settings-aboutFr')!.value).toBe('Bonjour.\n\nSuite.');
    expect(q<HTMLTextAreaElement>('#settings-aboutEn')!.value).toBe('Hello.');
  });

  it('shows an error with a retry that reloads', () => {
    backend.expectOne(url).flush(null, { status: 500, statusText: 'err' });
    settle();
    expect(q('[role=alert]')?.textContent).toContain('Impossible de charger');
    expect(q('form[data-form]')).toBeNull();

    q<HTMLButtonElement>('button[data-retry]')!.click();
    settle();
    load();
    expect(q('form[data-form]')).toBeTruthy();
  });

  it('saves only what changed, so one language can change without the other', () => {
    load();
    type('#settings-aboutEn', '  New English text.  ');
    submit();

    const req = backend.expectOne(url);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ aboutEn: 'New English text.' });
    req.flush({ ...settings, aboutEn: 'New English text.' });
    settle();

    expect(q('[role=status]')?.textContent).toContain('Paramètres enregistrés');
    expect(q<HTMLTextAreaElement>('#settings-aboutEn')!.value).toBe('New English text.');
  });

  it('sends nothing when nothing changed', () => {
    load();
    submit();
    expect(q('[role=status]')?.textContent).toContain('Aucune modification');
  });

  it('refuses a blank text, an invalid email and an overlong text without calling the API', () => {
    load();
    type('#settings-aboutFr', '   ');
    type('#settings-contactEmail', 'not-an-email');
    type('#settings-aboutEn', 'a'.repeat(5001));
    submit();

    expect(q('[data-error=aboutFr]')?.textContent).toContain('obligatoire');
    expect(q('[data-error=contactEmail]')?.textContent).toContain('adresse e-mail valide');
    expect(q('[data-error=aboutEn]')?.textContent).toContain('5000');
    expect(q('#settings-aboutFr')!.getAttribute('aria-invalid')).toBe('true');
  });

  it('shows the messages of the API under their fields', () => {
    load();
    type('#settings-contactEmail', 'a@b.sn');
    submit();
    backend.expectOne(url).flush({ contactEmail: 'contactEmail must be a valid email address' }, { status: 400, statusText: 'Bad Request' });
    settle();

    expect(q('[data-error=contactEmail]')?.textContent).toContain('must be a valid email address');
    expect(q('[role=status]')).toBeNull();
  });

  it('reports a failed save and keeps what was typed', () => {
    load();
    type('#settings-aboutFr', 'Nouveau texte.');
    submit();
    backend.expectOne(url).flush(null, { status: 500, statusText: 'err' });
    settle();

    expect(q('[role=alert]')?.textContent).toContain("L'enregistrement a échoué");
    expect(q<HTMLTextAreaElement>('#settings-aboutFr')!.value).toBe('Nouveau texte.');
  });

  it('shows how many characters each text uses', () => {
    load();
    expect(q('[data-count=aboutEn]')?.textContent).toContain('6 / 5000');
  });
});
