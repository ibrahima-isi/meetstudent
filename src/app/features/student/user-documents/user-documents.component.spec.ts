import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { UserDocumentsComponent } from './user-documents.component';
import { environment } from '../../../../environments/environment';
import { Media } from '@models/entities';

function media(partial: Partial<Media>): Media {
  return {
    id: 1,
    category: 'DIPLOMA',
    visibility: 'PRIVATE',
    verificationStatus: 'PENDING',
    rejectionReason: null,
    originalFilename: 'diploma.pdf',
    contentType: 'application/pdf',
    sizeBytes: 100,
    publicUrl: null,
    ...partial
  };
}

describe('UserDocumentsComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideZonelessChangeDetection()
      ]
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lists only personal documents returned by mine()', () => {
    const fixture = TestBed.createComponent(UserDocumentsComponent);
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiUrl}/media/mine`);
    req.flush([
      media({ id: 1, category: 'DIPLOMA' }),
      media({ id: 2, category: 'CERTIFICATE' }),
      media({ id: 3, category: 'USER_PHOTO', visibility: 'PUBLIC' })
    ]);

    const component = fixture.componentInstance;
    expect(component.documents().length).toBe(2);
    expect(component.documents().some(d => d.category === 'USER_PHOTO')).toBeFalse();
  });

  it('shows an empty list when the user has no documents', () => {
    const fixture = TestBed.createComponent(UserDocumentsComponent);
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiUrl}/media/mine`);
    req.flush([]);

    expect(fixture.componentInstance.documents()).toEqual([]);
  });

  it('exposes the French label for each verification status', () => {
    const fixture = TestBed.createComponent(UserDocumentsComponent);
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiUrl}/media/mine`);
    req.flush([]);

    const component = fixture.componentInstance;
    expect(component.statusLabel('PENDING')).toBe('En attente');
    expect(component.statusLabel('VERIFIED')).toBe('Vérifié');
    expect(component.statusLabel('REJECTED')).toBe('Rejeté');
    expect(component.statusLabel(null)).toBe('');
  });

  function fakeWindow(): Window {
    return { location: { href: '' }, closed: false, close: () => {} } as unknown as Window;
  }

  it('open() fetches the media as a blob and produces an object URL', () => {
    spyOn(window, 'open').and.returnValue(fakeWindow());
    const fixture = TestBed.createComponent(UserDocumentsComponent);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiUrl}/media/mine`).flush([]);

    fixture.componentInstance.open(media({ id: 7 }));

    const req = httpMock.expectOne(`${environment.apiUrl}/media/7`);
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['x']));
  });

  it('opens the window synchronously, before the HTTP response arrives (popup-blocker safe)', () => {
    const win = fakeWindow();
    const openSpy = spyOn(window, 'open').and.returnValue(win);
    const fixture = TestBed.createComponent(UserDocumentsComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/media/mine`).flush([]);

    fixture.componentInstance.open(media({ id: 7 }));

    // window.open must already have happened here — before the async HTTP
    // response is flushed — or Safari/Chrome will treat it as blocked.
    expect(openSpy).toHaveBeenCalledWith('', '_blank');

    const req = httpMock.expectOne(`${environment.apiUrl}/media/7`);
    req.flush(new Blob(['x']));

    expect(win.location.href).toContain('blob:');
  });

  it('sets a French error and issues no HTTP call when the popup is blocked', () => {
    spyOn(window, 'open').and.returnValue(null);
    const fixture = TestBed.createComponent(UserDocumentsComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/media/mine`).flush([]);

    fixture.componentInstance.open(media({ id: 7 }));

    expect(fixture.componentInstance.error()).toBeTruthy();
    httpMock.expectNone(`${environment.apiUrl}/media/7`);
  });

  it('revokes every object URL it created on destroy', () => {
    spyOn(window, 'open').and.returnValue(fakeWindow());
    spyOn(URL, 'revokeObjectURL');
    const fixture = TestBed.createComponent(UserDocumentsComponent);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiUrl}/media/mine`).flush([]);

    fixture.componentInstance.open(media({ id: 7 }));
    httpMock.expectOne(`${environment.apiUrl}/media/7`).flush(new Blob(['x']));

    fixture.componentInstance.open(media({ id: 8 }));
    httpMock.expectOne(`${environment.apiUrl}/media/8`).flush(new Blob(['y']));

    fixture.destroy();

    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2);
  });

  it('sets an error message when loading documents fails', () => {
    const fixture = TestBed.createComponent(UserDocumentsComponent);
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiUrl}/media/mine`);
    req.flush('server error', { status: 500, statusText: 'Internal Server Error' });

    const component = fixture.componentInstance;
    expect(component.error()).toBeTruthy();
    expect(component.loading()).toBeFalse();
  });

  it('uploads the selected file with the chosen category and an idempotency key', () => {
    const fixture = TestBed.createComponent(UserDocumentsComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/media/mine`).flush([]);

    const component = fixture.componentInstance;
    component.selectedCategory.set('CERTIFICATE');

    const file = new File([new ArrayBuffer(1024)], 'a.pdf', { type: 'application/pdf' });
    const event = { target: { files: [file] } } as unknown as Event;
    component.onFileSelected(event);

    const req = httpMock.expectOne(
      r => r.method === 'POST' && r.url === `${environment.apiUrl}/media`
    );
    expect(req.request.params.get('category')).toBe('CERTIFICATE');
    expect(req.request.headers.get('Idempotency-Key')).toBeTruthy();
    req.flush(media({ id: 9, category: 'CERTIFICATE' }));

    httpMock.expectOne(`${environment.apiUrl}/media/mine`).flush([]);
  });

  it('rejects a file above the size limit without calling the API', () => {
    const fixture = TestBed.createComponent(UserDocumentsComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/media/mine`).flush([]);

    const component = fixture.componentInstance;
    const file = new File([new ArrayBuffer(10485761)], 'big.pdf', { type: 'application/pdf' });
    const event = { target: { files: [file] } } as unknown as Event;
    component.onFileSelected(event);

    expect(component.error()).toBeTruthy();
    httpMock.expectNone(`${environment.apiUrl}/media`);
  });

  it('rejects a disallowed extension without calling the API', () => {
    const fixture = TestBed.createComponent(UserDocumentsComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/media/mine`).flush([]);

    const component = fixture.componentInstance;
    const file = new File([new ArrayBuffer(1024)], 'evil.exe', { type: 'application/octet-stream' });
    const event = { target: { files: [file] } } as unknown as Event;
    component.onFileSelected(event);

    expect(component.error()).toBeTruthy();
    httpMock.expectNone(`${environment.apiUrl}/media`);
  });

  it('clears uploading and sets an error when the upload fails', () => {
    const fixture = TestBed.createComponent(UserDocumentsComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/media/mine`).flush([]);

    const component = fixture.componentInstance;
    const file = new File([new ArrayBuffer(1024)], 'a.pdf', { type: 'application/pdf' });
    const event = { target: { files: [file] } } as unknown as Event;
    component.onFileSelected(event);

    const req = httpMock.expectOne(
      r => r.method === 'POST' && r.url === `${environment.apiUrl}/media`
    );
    req.flush('server error', { status: 500, statusText: 'Internal Server Error' });

    expect(component.error()).toBeTruthy();
    expect(component.uploading()).toBeFalse();
  });

  it('confirming a pending delete sends the DELETE request and reloads', () => {
    const fixture = TestBed.createComponent(UserDocumentsComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/media/mine`).flush([]);

    const component = fixture.componentInstance;
    component.requestDelete(5);
    expect(component.pendingDeleteId()).toBe(5);

    component.confirmDelete();

    const req = httpMock.expectOne(`${environment.apiUrl}/media/5`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    httpMock.expectOne(`${environment.apiUrl}/media/mine`).flush([]);
    expect(component.pendingDeleteId()).toBeNull();
  });

  it('requesting a delete without confirming issues no HTTP call, and cancelling clears the pending state', () => {
    const fixture = TestBed.createComponent(UserDocumentsComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/media/mine`).flush([]);

    const component = fixture.componentInstance;
    component.requestDelete(5);
    expect(component.pendingDeleteId()).toBe(5);
    httpMock.expectNone(`${environment.apiUrl}/media/5`);

    component.cancelDelete();

    expect(component.pendingDeleteId()).toBeNull();
    httpMock.expectNone(`${environment.apiUrl}/media/5`);
  });

  it('sets an error and clears the pending state when the delete fails', () => {
    const fixture = TestBed.createComponent(UserDocumentsComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/media/mine`).flush([]);

    const component = fixture.componentInstance;
    component.requestDelete(5);
    component.confirmDelete();

    const req = httpMock.expectOne(`${environment.apiUrl}/media/5`);
    req.flush('server error', { status: 500, statusText: 'Internal Server Error' });

    expect(component.error()).toBeTruthy();
    expect(component.pendingDeleteId()).toBeNull();
  });

  it('resets the file input value after handling a selection, so the same file can be re-picked', () => {
    const fixture = TestBed.createComponent(UserDocumentsComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/media/mine`).flush([]);

    const component = fixture.componentInstance;
    const file = new File([new ArrayBuffer(1024)], 'a.pdf', { type: 'application/pdf' });
    const input = { value: 'C:\\fakepath\\a.pdf', files: [file] } as unknown as HTMLInputElement;
    const event = { target: input } as unknown as Event;

    component.onFileSelected(event);

    expect(input.value).toBe('');

    const req = httpMock.expectOne(
      r => r.method === 'POST' && r.url === `${environment.apiUrl}/media`
    );
    req.flush(media({ id: 9 }));
    httpMock.expectOne(`${environment.apiUrl}/media/mine`).flush([]);
  });
});
