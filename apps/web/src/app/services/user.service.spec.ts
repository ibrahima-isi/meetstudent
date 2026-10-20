import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { UserService } from './user.service';
import { environment } from '../../environments/environment';
import { User } from '@models/entities';
import { provideZonelessChangeDetection } from '@angular/core';
import { TokenService } from './token.service';

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

  describe('refreshSessionUser', () => {
    afterEach(() => TestBed.inject(TokenService).clear());

    it('re-reads the signed-in account and keeps the new approval status in the session', () => {
      const tokens = TestBed.inject(TokenService);
      tokens.setUser({ id: 5, firstname: 'A', lastname: 'B', email: 'a@b.c', role: { name: 'ROLE_EXPERT' }, expertStatus: 'PENDING' } as User);
      let result: User | null = null;

      service.refreshSessionUser().subscribe((user) => (result = user));
      httpMock.expectOne(`${environment.apiUrl}/users/id/5`).flush({
        id: 5, firstname: 'A', lastname: 'B', email: 'a@b.c', role: { name: 'ROLE_EXPERT' }, expertStatus: 'APPROVED',
      });

      expect(tokens.user()?.expertStatus).toBe('APPROVED');
      expect((result as User | null)?.expertStatus).toBe('APPROVED');
    });

    it('asks nothing when nobody is signed in', () => {
      let result: User | null | undefined;

      service.refreshSessionUser().subscribe((user) => (result = user));

      httpMock.expectNone(`${environment.apiUrl}/users/id/5`);
      expect(result).toBeNull();
    });

    it('keeps the session as it was when the API fails', () => {
      const tokens = TestBed.inject(TokenService);
      tokens.setUser({ id: 5, role: { name: 'ROLE_EXPERT' }, expertStatus: 'PENDING' } as User);
      let result: User | null | undefined;

      service.refreshSessionUser().subscribe((user) => (result = user));
      httpMock.expectOne(`${environment.apiUrl}/users/id/5`).flush('x', { status: 500, statusText: 'err' });

      expect(tokens.user()?.expertStatus).toBe('PENDING');
      expect(result).toBeNull();
    });
  });
});
