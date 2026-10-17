import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ContactService } from './contact.service';

describe('ContactService', () => {
  let backend: HttpTestingController;
  let service: ContactService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    service = TestBed.inject(ContactService);
  });

  afterEach(() => backend.verify());

  const message = { email: 'a@b.sn', subject: 'Hi', message: 'Hello there', language: 'fr' as const, website: '' };

  it('posts the message to /contact and completes on the empty 202', () => {
    let done = false;
    service.send(message).subscribe({ complete: () => (done = true) });

    const req = backend.expectOne('/api/v1/contact');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(message);
    req.flush(null, { status: 202, statusText: 'Accepted' });
    expect(done).toBeTrue();
  });

  it('passes the server error through untouched so the page can map it', () => {
    let error: unknown;
    service.send(message).subscribe({ error: (e: unknown) => (error = e) });
    backend.expectOne('/api/v1/contact').flush({ email: 'invalid' }, { status: 400, statusText: 'Bad Request' });

    expect(error instanceof HttpErrorResponse).toBeTrue();
    expect((error as HttpErrorResponse).status).toBe(400);
    expect((error as HttpErrorResponse).error).toEqual({ email: 'invalid' });
  });
});
