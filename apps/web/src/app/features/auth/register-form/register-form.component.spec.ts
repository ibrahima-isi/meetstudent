import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { RegisterFormComponent } from './register-form.component';
import { environment } from '../../../../environments/environment';

/**
 * The backend binds RegisterRequest, which requires `confirmedPassword` and
 * always creates a STUDENT. Sending `confirmPassword` (or omitting it) makes
 * every signup 400, and any `role` in the payload is silently ignored.
 */
describe('RegisterFormComponent registration payload', () => {
  let fixture: ComponentFixture<RegisterFormComponent>;
  let component: RegisterFormComponent;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RegisterFormComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideZonelessChangeDetection(),
        // The form navigates to /verify now instead of emitting, so it reaches
        // Router and LocaleService — and LocaleService reaches Transloco.
        provideRouter([]),
        provideTransloco(translocoOptions)
      ],
    });
    fixture = TestBed.createComponent(RegisterFormComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function fillValidForm() {
    component.step1Form.patchValue({
      firstname: 'Awa',
      lastname: 'Diop',
      email: 'awa@example.com',
      town: 'Dakar'
    });
    component.step2Form.patchValue({
      password: 'sup3rsecret',
      confirmPassword: 'sup3rsecret',
      terms: true
    });
  }

  it('sends confirmedPassword matching the password', () => {
    fillValidForm();
    component.handleSubmit();

    const req = httpMock.expectOne(`${environment.apiUrl}/users`);
    expect(req.request.body.confirmedPassword).toBe('sup3rsecret');
    expect(req.request.body.password).toBe('sup3rsecret');
    req.flush({});
  });

  it('sends the fields the backend RegisterRequest expects', () => {
    fillValidForm();
    component.handleSubmit();

    const req = httpMock.expectOne(`${environment.apiUrl}/users`);
    expect(req.request.body.firstname).toBe('Awa');
    expect(req.request.body.lastname).toBe('Diop');
    expect(req.request.body.email).toBe('awa@example.com');
    req.flush({});
  });

  // Opposite of the rule: role must NOT be client-controlled. Registration
  // always yields a STUDENT; role changes go through PATCH /users/{id}/role.
  it('never sends a role in the registration payload', () => {
    fillValidForm();
    component.setUserType('teacher');
    component.handleSubmit();

    const req = httpMock.expectOne(`${environment.apiUrl}/users`);
    expect(req.request.body.role).toBeUndefined();
    req.flush({});
  });

  // Opposite of the happy path: mismatched passwords must not reach the network.
  it('does not call the API when passwords do not match', () => {
    fillValidForm();
    component.step2Form.patchValue({ confirmPassword: 'different' });
    component.handleSubmit();

    httpMock.expectNone(`${environment.apiUrl}/users`);
    expect(component.error()).toBeTruthy();
  });
});

describe('RegisterFormComponent translations', () => {
  let fixture: ComponentFixture<RegisterFormComponent>;
  let httpMock: HttpTestingController;

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  async function render(lang: 'fr' | 'en') {
    const transloco = TestBed.inject(TranslocoService);
    // *transloco renders nothing until the dynamic import resolves.
    await firstValueFrom(transloco.load(lang));
    transloco.setActiveLang(lang);
    fixture = TestBed.createComponent(RegisterFormComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RegisterFormComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideTransloco(translocoOptions),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('renders in French, the source language', async () => {
    await render('fr');

    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe('Créer un compte');
    expect(text()).toContain('Étape 1 sur 2 : informations de base');
  });

  it('renders in English when English is active', async () => {
    await render('en');

    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe('Create Account');
    expect(text()).toContain('Step 1 of 2: Basic Information');
  });

  it('explains a password mismatch in the active language', async () => {
    await render('fr');
    const component = fixture.componentInstance;
    component.step.set(2);
    component.step2Form.setValue({ password: 'sup3rsecret', confirmPassword: 'other', terms: true });

    component.handleSubmit();
    await settle();

    expect(text()).toContain('Les mots de passe ne correspondent pas.');
  });

  // Decision 5: ErrorResponse.message is for logs, never for the user.
  it('shows its own failure text, never the API message', async () => {
    await render('en');
    const component = fixture.componentInstance;
    component.step1Form.patchValue({ firstname: 'Awa', lastname: 'Diop', email: 'awa@example.com', town: 'Dakar' });
    component.step.set(2);
    component.step2Form.setValue({ password: 'sup3rsecret', confirmPassword: 'sup3rsecret', terms: true });

    component.handleSubmit();
    httpMock
      .expectOne(`${environment.apiUrl}/users`)
      .flush({ message: 'Email already used (constraint uk_users_email)' }, { status: 409, statusText: 'Conflict' });
    await settle();

    expect(text()).toContain('Registration failed.');
    expect(text()).not.toContain('uk_users_email');
  });
});
