import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { Subject, of, throwError } from 'rxjs';
import { School, User } from '@models/entities';
import { TokenService } from './token.service';
import { UserService } from './user.service';
import { WishlistService } from './wishlist.service';

describe('WishlistService', () => {
  const a: School = { id: 1, name: 'A', address: { location: '', city: '', country: '' } };
  const b: School = { id: 2, name: 'B', address: { location: '', city: '', country: '' } };
  let userService: jasmine.SpyObj<UserService>;
  let service: WishlistService;

  const userWith = (wishlist: School[]) => ({ id: 9, wishlist }) as User;

  beforeEach(() => {
    userService = jasmine.createSpyObj('UserService', ['getUser', 'addToWishlist', 'removeFromWishlist']);
    userService.getUser.and.returnValue(of(userWith([a])));
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: UserService, useValue: userService },
        { provide: TokenService, useValue: { user: signal({ id: 9 }), isAuthenticated: signal(true) } },
      ],
    });
    service = TestBed.inject(WishlistService);
  });

  it('loads the wishlist from the API, so it survives a reload', () => {
    service.load();

    expect(userService.getUser).toHaveBeenCalledWith(9);
    expect(service.schools()).toEqual([a]);
    expect(service.has(1)).toBeTrue();
    expect(service.has(2)).toBeFalse();
  });

  it('shows an added school immediately, before the server answers', () => {
    const reply = new Subject<User>();
    userService.addToWishlist.and.returnValue(reply);

    service.toggle(b);

    expect(userService.addToWishlist).toHaveBeenCalledWith(9, 2);
    expect(service.has(2)).toBeTrue();
    expect(service.isPending(2)).toBeTrue();

    reply.next(userWith([b]));
    reply.complete();

    expect(service.schools()).toEqual([b]); // the server answer is the truth
    expect(service.isPending(2)).toBeFalse();
  });

  it('rolls an add back and flags the error when the server refuses', () => {
    userService.addToWishlist.and.returnValue(throwError(() => new Error('boom')));

    service.toggle(b);

    expect(service.has(2)).toBeFalse();
    expect(service.error()).toBeTrue();
    expect(service.isPending(2)).toBeFalse();
  });

  it('removes a school optimistically and rolls it back on failure', () => {
    service.load();
    const reply = new Subject<User>();
    userService.removeFromWishlist.and.returnValue(reply);

    service.toggle(a);
    expect(userService.removeFromWishlist).toHaveBeenCalledWith(9, 1);
    expect(service.has(1)).toBeFalse();

    reply.error(new Error('boom'));

    expect(service.has(1)).toBeTrue();
    expect(service.error()).toBeTrue();
  });

  it('keeps a successful removal', () => {
    service.load();
    userService.removeFromWishlist.and.returnValue(of(userWith([])));

    service.toggle(a);

    expect(service.schools()).toEqual([]);
    expect(service.error()).toBeFalse();
  });

  it('ignores a second toggle on a school whose request is still in flight', () => {
    userService.addToWishlist.and.returnValue(new Subject<User>());

    service.toggle(b);
    service.toggle(b);

    expect(userService.addToWishlist).toHaveBeenCalledTimes(1);
    expect(userService.removeFromWishlist).not.toHaveBeenCalled();
  });

  it('reports a load failure instead of showing an empty list as fact', () => {
    userService.getUser.and.returnValue(throwError(() => new Error('down')));

    service.load();

    expect(service.status()).toBe('error');
  });

  it('clears its error when asked', () => {
    userService.addToWishlist.and.returnValue(throwError(() => new Error('boom')));
    service.toggle(b);

    service.dismissError();

    expect(service.error()).toBeFalse();
  });

  it('forgets everything on clear, so the next visitor starts empty', () => {
    const reply = new Subject<User>();
    userService.addToWishlist.and.returnValue(reply);
    service.load();
    service.toggle(b);
    service.error.set(true);

    service.clear();

    expect(service.schools()).toEqual([]);
    expect(service.isPending(2)).toBeFalse();
    expect(service.status()).toBe('idle');
    expect(service.error()).toBeFalse();
  });
});
