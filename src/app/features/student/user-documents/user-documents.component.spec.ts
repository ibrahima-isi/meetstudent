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

  it('open() fetches the media as a blob and produces an object URL', () => {
    const fixture = TestBed.createComponent(UserDocumentsComponent);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiUrl}/media/mine`).flush([]);

    fixture.componentInstance.open(media({ id: 7 }));

    const req = httpMock.expectOne(`${environment.apiUrl}/media/7`);
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['x']));
  });

  it('revokes every object URL it created on destroy', () => {
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
});
