import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { UserService } from './user.service';
import { environment } from '../../environments/environment';
import { User } from '@models/entities';
import { provideZonelessChangeDetection } from '@angular/core';

describe('UserService', () => {
  let service: UserService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        UserService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideZonelessChangeDetection()
      ],
    });
    service = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch user and map fields', () => {
    const mockUser: User = {
      id: 1,
      firstname: 'John',
      lastname: 'Doe',
      email: 'john@example.com',
      role: { name: 'STUDENT' },
      wishlist: [{ id: 1, name: 'School 1' } as any]
    };

    service.getUser(1).subscribe(user => {
      expect(user.id).toBe(1);
      expect(user.wishlist?.length).toBe(1);
      // No French default: the template supplies one in the active language.
      expect(user.wishlist?.[0].type).toBeUndefined();
      expect(service.users().length).toBe(1);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/users/id/1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockUser);
  });

  it('should add to wishlist', () => {
    service.addToWishlist(1, 1).subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/users/1/wishlist/1`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  describe('program wishlist', () => {
    const url = `${environment.apiUrl}/users/5/wishlist/programs`;

    it('reads the programs of the wishlist', () => {
      let result: { id?: number; name: string }[] = [];
      service.getProgramWishlist(5).subscribe((r) => (result = r));
      const req = httpMock.expectOne(url);
      expect(req.request.method).toBe('GET');
      req.flush([{ id: 3, name: 'Licence Info' }]);
      expect(result.map((p) => p.id)).toEqual([3]);
    });

    it('adds a program with a POST and returns the updated list', () => {
      let result: { id?: number; name: string }[] = [];
      service.addProgramToWishlist(5, 3).subscribe((r) => (result = r));
      const req = httpMock.expectOne(`${url}/3`);
      expect(req.request.method).toBe('POST');
      req.flush([{ id: 3, name: 'Licence Info' }]);
      expect(result.map((p) => p.id)).toEqual([3]);
    });

    it('removes a program with a DELETE', () => {
      service.removeProgramFromWishlist(5, 3).subscribe();
      const req = httpMock.expectOne(`${url}/3`);
      expect(req.request.method).toBe('DELETE');
      req.flush([]);
    });
  });

  it('PATCHes the profile fields to /users/{id} and returns the saved user', () => {
    const saved = { id: 5, firstname: 'Awa', lastname: 'Diop', email: 'a@b.sn', role: { name: 'ROLE_STUDENT' } } as User;
    let result: User | undefined;

    service.updateProfile(5, { firstname: 'Awa', password: 'n3wpassword' }).subscribe((u) => (result = u));

    const req = httpMock.expectOne(`${environment.apiUrl}/users/5`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ firstname: 'Awa', password: 'n3wpassword' });
    req.flush(saved);
    expect(result).toEqual(saved);
  });

  describe('email change', () => {
    const url = `${environment.apiUrl}/users/5/email-change`;

    it('asks for a new address with the current password and the language', () => {
      service.requestEmailChange(5, 'new@example.com', 'S3cure-pass', 'fr').subscribe();

      const req = httpMock.expectOne(url);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ newEmail: 'new@example.com', currentPassword: 'S3cure-pass', lang: 'fr' });
      req.flush({ status: 'accepted' }, { status: 202, statusText: 'Accepted' });
    });

    it('reads the pending address, or null when there is none', () => {
      const results: (string | null)[] = [];
      service.getPendingEmail(5).subscribe((e) => results.push(e));
      httpMock.expectOne(url).flush({ pendingEmail: 'new@example.com' });
      service.getPendingEmail(5).subscribe((e) => results.push(e));
      httpMock.expectOne(url).flush({});

      expect(results).toEqual(['new@example.com', null]);
    });

    it('cancels the pending change', () => {
      service.cancelEmailChange(5).subscribe();

      const req = httpMock.expectOne(url);
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('sends the link of the pending change again', () => {
      service.resendEmailChange(5, 'en').subscribe();

      const req = httpMock.expectOne(`${url}/resend?lang=en`);
      expect(req.request.method).toBe('POST');
      expect(req.request.params.get('lang')).toBe('en');
      req.flush({ status: 'accepted' }, { status: 202, statusText: 'Accepted' });
    });
  });
});
