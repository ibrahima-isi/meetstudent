import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
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
