import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ForumService } from './forum.service';

describe('ForumService', () => {
  let backend: HttpTestingController;
  let service: ForumService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    service = TestBed.inject(ForumService);
  });

  afterEach(() => backend.verify());

  it('lists threads with the page and size, and no school filter by default', () => {
    service.listThreads({ page: 2, size: 10 }).subscribe();

    const req = backend.expectOne((r) => r.url === '/api/v1/forum/threads');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.has('schoolId')).toBeFalse();
    req.flush({ content: [] });
  });

  it('adds the school filter when one is picked', () => {
    service.listThreads({ page: 0, size: 10, schoolId: 7 }).subscribe();

    const req = backend.expectOne((r) => r.url === '/api/v1/forum/threads');
    expect(req.request.params.get('schoolId')).toBe('7');
    req.flush({ content: [] });
  });

  it('reads one thread', () => {
    let title = '';
    service.getThread(4).subscribe((t) => (title = t.title));

    const req = backend.expectOne('/api/v1/forum/threads/4');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 4, title: 'Hello' });
    expect(title).toBe('Hello');
  });

  it('lists the replies of a thread by page', () => {
    service.listReplies(4, { page: 1, size: 20 }).subscribe();

    const req = backend.expectOne((r) => r.url === '/api/v1/forum/threads/4/replies');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('20');
    req.flush({ content: [] });
  });

  it('opens a thread with POST, school included', () => {
    service.createThread({ title: 'T', body: 'B', schoolId: 3 }).subscribe();

    const req = backend.expectOne('/api/v1/forum/threads');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ title: 'T', body: 'B', schoolId: 3 });
    req.flush({ id: 9 }, { status: 201, statusText: 'Created' });
  });

  it('edits a thread with PUT, sending the title and body only', () => {
    service.updateThread(4, { title: 'T2', body: 'B2', schoolId: 3 }).subscribe();

    const req = backend.expectOne('/api/v1/forum/threads/4');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ title: 'T2', body: 'B2' });
    req.flush({ id: 4 });
  });

  it('deletes a thread', () => {
    let done = false;
    service.deleteThread(4).subscribe({ complete: () => (done = true) });

    const req = backend.expectOne('/api/v1/forum/threads/4');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(done).toBeTrue();
  });

  it('replies with POST and a body', () => {
    service.createReply(4, 'Hi').subscribe();

    const req = backend.expectOne('/api/v1/forum/threads/4/replies');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ body: 'Hi' });
    req.flush({ id: 1 }, { status: 201, statusText: 'Created' });
  });

  it('edits and deletes a reply by its own id', () => {
    service.updateReply(8, 'New').subscribe();
    const put = backend.expectOne('/api/v1/forum/replies/8');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ body: 'New' });
    put.flush({ id: 8 });

    service.deleteReply(8).subscribe();
    const del = backend.expectOne('/api/v1/forum/replies/8');
    expect(del.request.method).toBe('DELETE');
    del.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('passes the server error through untouched so a page can map 400, 409 and 429', () => {
    let error: unknown;
    service.createReply(4, 'Hi').subscribe({ error: (e: unknown) => (error = e) });
    backend.expectOne('/api/v1/forum/threads/4/replies').flush({ message: 'locked' }, { status: 409, statusText: 'Conflict' });

    expect(error instanceof HttpErrorResponse).toBeTrue();
    expect((error as HttpErrorResponse).status).toBe(409);
  });
});
