import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { RegisterFormComponent } from './register-form.component';
import { Router } from '@angular/router';
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

  // Launch scope: the backend has no email verification, so a successful
  // signup goes straight to login, carrying a flag for the success notice.
  it('goes straight to login after a successful registration', () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    fillValidForm();
    component.handleSubmit();

    httpMock.expectOne(`${environment.apiUrl}/users`).flush({});

    expect(navigate).toHaveBeenCalledTimes(1);
    const [commands, extras] = navigate.calls.mostRecent().args;
    expect((commands as string[]).at(-1)).toBe('login');
    expect(commands).not.toContain('verify');
    expect(extras).toEqual({ queryParams: { registered: '1' } });
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

  async function submitWith(body: Record<string, string>) {
    const component = fixture.componentInstance;
    component.step1Form.patchValue({ firstname: 'Awa', lastname: 'Diop', email: 'awa@example.com', town: 'Dakar' });
    component.step.set(2);
    await settle();
    component.step2Form.setValue({ password: 'sup3rsecret', confirmPassword: 'sup3rsecret', terms: true });
    component.handleSubmit();
    httpMock.expectOne(`${environment.apiUrl}/users`).flush(body, { status: 400, statusText: 'Bad Request' });
  }

  it('shows the API email message under the email field, back on step 1', async () => {
    await render('fr');
    await submitWith({ email: 'Cet email est déjà utilisé' });
    await settle();

    const component = fixture.componentInstance;
    expect(component.step()).toBe(1);
    const message = fixture.nativeElement.querySelector('[data-testid="error-email"]') as HTMLElement;
    expect(message.textContent).toContain('Cet email est déjà utilisé');
    expect(text()).not.toContain("L'inscription a échoué");
  });

  it('shows the API confirmedPassword message under the confirmation field', async () => {
    await render('fr');
    await submitWith({ confirmedPassword: 'Les mots de passe ne correspondent pas' });
    await settle();

    const component = fixture.componentInstance;
    expect(component.step()).toBe(2);
    const message = fixture.nativeElement.querySelector('[data-testid="error-confirmedPassword"]') as HTMLElement;
    expect(message.textContent).toContain('Les mots de passe ne correspondent pas');
  });

  it('clears a field error as soon as the visitor edits that field', async () => {
    await render('fr');
    await submitWith({ email: 'Cet email est déjà utilisé' });
    await settle();

    fixture.componentInstance.step1Form.get('email')?.setValue('awa2@example.com');
    await settle();

    expect(fixture.nativeElement.querySelector('[data-testid="error-email"]')).toBeNull();
  });

  function bacOptions(): { value: string; label: string }[] {
    const select = fixture.nativeElement.querySelector('select#bacType') as HTMLSelectElement;
    return Array.from(select.options)
      .filter((option) => option.value !== '')
      .map((option) => ({ value: option.value, label: option.textContent!.trim() }));
  }

  it('labels the bac options in French', async () => {
    await render('fr');
    fixture.componentInstance.setUserType('student');
    await settle();

    expect(bacOptions()).toEqual([
      { value: 'bac-general', label: 'Bac Général' },
      { value: 'bac-techno-sti2d', label: 'Bac Technologique - STI2D' },
      { value: 'bac-techno-stmg', label: 'Bac Technologique - STMG' },
      { value: 'bac-pro', label: 'Bac Professionnel' },
    ]);
  });

  it('labels the bac options in English without changing their values', async () => {
    await render('en');
    fixture.componentInstance.setUserType('student');
    await settle();

    expect(bacOptions()).toEqual([
      { value: 'bac-general', label: 'General Baccalaureate' },
      { value: 'bac-techno-sti2d', label: 'Technological Baccalaureate - STI2D' },
      { value: 'bac-techno-stmg', label: 'Technological Baccalaureate - STMG' },
      { value: 'bac-pro', label: 'Vocational Baccalaureate' },
    ]);
    expect(text()).not.toContain('Bac Général');
    expect(text()).not.toContain('Bac Professionnel');
  });

  function collegeLevelOptions(): { value: string; label: string }[] {
    const select = fixture.nativeElement.querySelector('select#collegeLevel') as HTMLSelectElement;
    return Array.from(select.options)
      .filter((option) => option.value !== '')
      .map((option) => ({ value: option.value, label: option.textContent!.trim() }));
  }

  it('labels the college levels in French', async () => {
    await render('fr');
    fixture.componentInstance.setUserType('student');
    await settle();

    expect(collegeLevelOptions()).toEqual([
      { value: 'l1', label: 'Licence 1 (L1)' },
      { value: 'l2', label: 'Licence 2 (L2)' },
      { value: 'm1', label: 'Master 1 (M1)' },
    ]);
  });

  it('labels the college levels in English without changing their values', async () => {
    await render('en');
    fixture.componentInstance.setUserType('student');
    await settle();

    expect(collegeLevelOptions()).toEqual([
      { value: 'l1', label: "Bachelor's year 1 (L1)" },
      { value: 'l2', label: "Bachelor's year 2 (L2)" },
      { value: 'm1', label: "Master's year 1 (M1)" },
    ]);
    expect(text()).not.toContain('Licence 1');
  });
});

describe('RegisterFormComponent restyle', () => {
  let fixture: ComponentFixture<RegisterFormComponent>;

  async function render() {
    const transloco = TestBed.inject(TranslocoService);
    await firstValueFrom(transloco.load('fr'));
    transloco.setActiveLang('fr');
    fixture = TestBed.createComponent(RegisterFormComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function root(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function segments(): HTMLElement[] {
    return Array.from(root().querySelectorAll<HTMLElement>('[data-testid="progress-segment"]'));
  }

  function expectFieldInputs() {
    const fields = Array.from(root().querySelectorAll('input[formControlName], select[formControlName]')).filter(
      (el) => (el as HTMLInputElement).type !== 'checkbox',
    );
    expect(fields.length).toBeGreaterThan(0);
    for (const field of fields) {
      expect(field.classList.contains('field-input')).withContext(field.id).toBeTrue();
    }
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
  });

  it('styles every step 1 field with field-input and the next button as a large primary button', async () => {
    await render();
    fixture.componentInstance.setUserType('student');
    await settle();
    expectFieldInputs();
    fixture.componentInstance.setUserType('teacher');
    await settle();
    expectFieldInputs();

    const submit = root().querySelector('form button[type="submit"]') as HTMLElement;
    for (const cls of ['btn', 'btn-primary', 'btn-lg']) {
      expect(submit.classList.contains(cls)).withContext(cls).toBeTrue();
    }
  });

  it('styles every step 2 field with field-input and the submit button as a large primary button', async () => {
    await render();
    fixture.componentInstance.step.set(2);
    await settle();
    expectFieldInputs();

    const submit = root().querySelector('form button[type="submit"]') as HTMLElement;
    for (const cls of ['btn', 'btn-primary', 'btn-lg']) {
      expect(submit.classList.contains(cls)).withContext(cls).toBeTrue();
    }
  });

  it('colours the progress segments with the brand token for reached steps and shows a caption', async () => {
    await render();
    let [first, second] = segments();
    expect(first.classList.contains('bg-brand')).toBeTrue();
    expect(second.classList.contains('bg-muted')).toBeTrue();
    expect(first.getAttribute('aria-current')).toBe('step');
    expect(second.getAttribute('aria-current')).toBeNull();
    expect(root().textContent).toContain('1/2');

    fixture.componentInstance.step.set(2);
    await settle();
    [first, second] = segments();
    expect(first.classList.contains('bg-brand')).toBeTrue();
    expect(second.classList.contains('bg-brand')).toBeTrue();
    expect(first.getAttribute('aria-current')).toBeNull();
    expect(second.getAttribute('aria-current')).toBe('step');
    expect(root().textContent).toContain('2/2');
  });

  it('marks a field with an API error as aria-invalid', async () => {
    await render();
    const component = fixture.componentInstance;
    component.step1Form.patchValue({ firstname: 'Awa', lastname: 'Diop', email: 'awa@example.com', town: 'Dakar' });
    component.step.set(2);
    await settle();
    component.step2Form.setValue({ password: 'sup3rsecret', confirmPassword: 'sup3rsecret', terms: true });
    component.handleSubmit();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock
      .expectOne(`${environment.apiUrl}/users`)
      .flush({ email: 'Cet email est déjà utilisé' }, { status: 400, statusText: 'Bad Request' });
    await settle();

    expect(root().querySelector('#email')?.getAttribute('aria-invalid')).toBe('true');
    expect(root().querySelector('#town')?.getAttribute('aria-invalid')).toBeNull();
  });
});
