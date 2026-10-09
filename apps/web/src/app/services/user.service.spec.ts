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
});
