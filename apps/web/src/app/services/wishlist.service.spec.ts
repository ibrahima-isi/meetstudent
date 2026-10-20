import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Subject, of, throwError } from 'rxjs';
import { Program, School, User } from '@models/entities';
import { TokenService } from './token.service';
import { UserService } from './user.service';
import { WishlistService } from './wishlist.service';
import { environment } from '../../environments/environment';

describe('WishlistService', () => {
  const a: School = { id: 1, name: 'A', address: { location: '', city: '', country: '' } };
  const b: School = { id: 2, name: 'B', address: { location: '', city: '', country: '' } };
  let userService: jasmine.SpyObj<UserService>;
  let service: WishlistService;
  let user: ReturnType<typeof signal<{ id: number; role: { name: string } } | null>>;

  const userWith = (wishlist: School[]) => ({ id: 9, wishlist }) as User;
  const session = (id: number, role = 'ROLE_STUDENT') => ({ id, role: { name: role } });

  beforeEach(() => {
    user = signal<{ id: number; role: { name: string } } | null>(session(9));
    userService = jasmine.createSpyObj('UserService', [
      'getUser',
      'addToWishlist',
      'removeFromWishlist',
      'getProgramWishlist',
      'addProgramToWishlist',
      'removeProgramFromWishlist',
    ]);
    userService.getUser.and.returnValue(of(userWith([a])));
    userService.getProgramWishlist.and.returnValue(of([]));
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: UserService, useValue: userService },
        { provide: TokenService, useValue: { user, isAuthenticated: signal(true) } },
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

  it('empties itself when the session ends, whichever code path ends it', () => {
    service.load();
    expect(service.schools()).toEqual([a]);

    user.set(null); // what TokenService.clear() does
    TestBed.tick();

    expect(service.schools()).toEqual([]);
    expect(service.status()).toBe('idle');
  });

  it('never shows the previous user\'s schools after a user switch', () => {
    service.load();

    user.set(session(10));
    TestBed.tick();

    expect(service.schools()).toEqual([]);
  });

  it('ignores a toggle answer that arrives after clear', () => {
    const reply = new Subject<User>();
    userService.addToWishlist.and.returnValue(reply);
    service.toggle(b);

    service.clear();
    reply.next(userWith([a, b]));
    reply.complete();

    expect(service.schools()).toEqual([]);
    expect(service.status()).toBe('idle');
    expect(service.isPending(2)).toBeFalse();
  });

  it('does not roll a failed toggle back onto a list that was cleared meanwhile', () => {
    const reply = new Subject<User>();
    userService.addToWishlist.and.returnValue(reply);
    service.toggle(b);
    service.clear();
    user.set(session(10));
    userService.getUser.and.returnValue(of(userWith([a])));
    service.load();

    reply.error(new Error('boom'));

    expect(service.schools()).toEqual([a]);
    expect(service.error()).toBeFalse();
  });

  it('does not ask twice while a load is in flight', () => {
    userService.getUser.and.returnValue(new Subject<User>());

    service.load();
    service.load();

    expect(userService.getUser).toHaveBeenCalledTimes(1);
  });

  it('loads again after a failure, and for a new user while the old answer is pending', () => {
    userService.getUser.and.returnValues(throwError(() => new Error('x')), new Subject<User>(), new Subject<User>());
    service.load();
    expect(service.status()).toBe('error');

    service.load();
    user.set(session(10));
    TestBed.tick();
    service.load();

    expect(userService.getUser).toHaveBeenCalledTimes(3);
  });

  it('loads for a user set a moment ago, before the owner effect has flushed', () => {
    const reply = new Subject<User>();
    userService.getUser.and.returnValue(reply);

    user.set(session(10));
    service.load();
    reply.next(userWith([b]));
    TestBed.tick();

    expect(userService.getUser).toHaveBeenCalledWith(10);
    expect(service.schools()).toEqual([b]);
  });

  it('shows only the new user\'s list when the user changes while a load is in flight', () => {
    const first = new Subject<User>();
    const second = new Subject<User>();
    userService.getUser.and.returnValues(first, second);
    service.load();

    user.set(session(10));
    service.load();
    first.next(userWith([a]));
    second.next(userWith([b]));
    TestBed.tick();

    expect(userService.getUser).toHaveBeenCalledTimes(2);
    expect(service.schools()).toEqual([b]);
  });

  it('gives up on a load that never answers, so a retry is possible', () => {
    // Zoneless suite: no fakeAsync, the Jasmine clock drives rxjs' timer.
    jasmine.clock().install();
    try {
      userService.getUser.and.returnValues(new Subject<User>(), of(userWith([a])));
      service.load();

      jasmine.clock().tick(15000);
      expect(service.status()).toBe('error');

      service.load();
      expect(service.schools()).toEqual([a]);
    } finally {
      jasmine.clock().uninstall();
    }
  });

  it('ignores a load answer that arrives after clear', () => {
    const reply = new Subject<User>();
    userService.getUser.and.returnValue(reply);
    service.load();

    service.clear();
    reply.next(userWith([a]));

    expect(service.schools()).toEqual([]);
    expect(service.status()).toBe('idle');
  });

  it('ignores a load answer that belongs to the previous user', () => {
    const reply = new Subject<User>();
    userService.getUser.and.returnValue(reply);
    service.load();

    user.set(session(10));
    TestBed.tick();
    reply.next(userWith([a]));

    expect(service.schools()).toEqual([]);
  });

  it('never loads or changes a student wishlist for an expert session', () => {
    user.set(session(9, 'ROLE_EXPERT'));
    TestBed.tick();

    service.load();
    service.toggle(a);

    expect(userService.getUser).not.toHaveBeenCalled();
    expect(userService.addToWishlist).not.toHaveBeenCalled();
    expect(userService.removeFromWishlist).not.toHaveBeenCalled();
    expect(service.schools()).toEqual([]);
  });

  it('clears the wishlist when one account changes role and reloads after returning to student', () => {
    service.load();
    expect(service.schools()).toEqual([a]);

    user.set(session(9, 'ROLE_EXPERT'));
    TestBed.tick();
    expect(service.schools()).toEqual([]);
    service.load();
    expect(userService.getUser).toHaveBeenCalledTimes(1);

    user.set(session(9));
    TestBed.tick();
    userService.getUser.and.returnValue(of(userWith([b])));
    service.load();
    expect(userService.getUser).toHaveBeenCalledTimes(2);
    expect(service.schools()).toEqual([b]);
  });

  describe('programs', () => {
    const p1: Program = { id: 11, name: 'Licence Info' };
    const p2: Program = { id: 12, name: 'Master Data' };

    it('loads the programs next to the schools', () => {
      userService.getProgramWishlist.and.returnValue(of([p1]));

      service.load();

      expect(userService.getProgramWishlist).toHaveBeenCalledWith(9);
      expect(service.programs()).toEqual([p1]);
      expect(service.hasProgram(11)).toBeTrue();
      expect(service.hasProgram(12)).toBeFalse();
      expect(service.status()).toBe('loaded');
      expect(service.programsError()).toBeFalse();
    });

    it('keeps the schools when only the program list fails, and says so without blocking', () => {
      userService.getProgramWishlist.and.returnValue(throwError(() => new Error('no V32')));

      service.load();

      expect(service.status()).toBe('loaded');
      expect(service.schools()).toEqual([a]);
      expect(service.programs()).toEqual([]);
      expect(service.programsError()).toBeTrue();
    });

    it('still reports the blocking error when the schools fail', () => {
      userService.getUser.and.returnValue(throwError(() => new Error('down')));

      service.load();

      expect(service.status()).toBe('error');
    });

    it('forgets the partial error on clear', () => {
      userService.getProgramWishlist.and.returnValue(throwError(() => new Error('x')));
      service.load();

      service.clear();

      expect(service.programsError()).toBeFalse();
    });

    it('shows an added program at once, then keeps the server answer', () => {
      const reply = new Subject<Program[]>();
      userService.addProgramToWishlist.and.returnValue(reply);

      service.toggleProgram(p2);

      expect(userService.addProgramToWishlist).toHaveBeenCalledWith(9, 12);
      expect(service.hasProgram(12)).toBeTrue();
      expect(service.isProgramPending(12)).toBeTrue();

      reply.next([p1, p2]);
      reply.complete();

      expect(service.programs()).toEqual([p1, p2]);
      expect(service.isProgramPending(12)).toBeFalse();
    });

    it('rolls an add back and flags the error when the server refuses', () => {
      userService.addProgramToWishlist.and.returnValue(throwError(() => new Error('boom')));

      service.toggleProgram(p2);

      expect(service.hasProgram(12)).toBeFalse();
      expect(service.error()).toBeTrue();
      expect(service.isProgramPending(12)).toBeFalse();
    });

    it('removes a program and rolls it back on failure', () => {
      userService.getProgramWishlist.and.returnValue(of([p1]));
      service.load();
      userService.removeProgramFromWishlist.and.returnValue(throwError(() => new Error('boom')));

      service.toggleProgram(p1);

      expect(userService.removeProgramFromWishlist).toHaveBeenCalledWith(9, 11);
      expect(service.hasProgram(11)).toBeTrue();
      expect(service.error()).toBeTrue();
    });

    it('keeps a successful removal', () => {
      userService.getProgramWishlist.and.returnValue(of([p1]));
      service.load();
      userService.removeProgramFromWishlist.and.returnValue(of([]));

      service.toggleProgram(p1);

      expect(service.programs()).toEqual([]);
    });

    it('ignores a second toggle while the first is in flight', () => {
      userService.addProgramToWishlist.and.returnValue(new Subject<Program[]>());

      service.toggleProgram(p2);
      service.toggleProgram(p2);

      expect(userService.addProgramToWishlist).toHaveBeenCalledTimes(1);
    });

    it('never touches the API for an expert', () => {
      user.set(session(9, 'ROLE_EXPERT'));
      TestBed.tick();

      service.toggleProgram(p1);

      expect(userService.addProgramToWishlist).not.toHaveBeenCalled();
      expect(service.programs()).toEqual([]);
    });

    it('forgets the programs on clear and when the session ends', () => {
      userService.getProgramWishlist.and.returnValue(of([p1]));
      service.load();

      user.set(null);
      TestBed.tick();

      expect(service.programs()).toEqual([]);
    });

    it('ignores a program answer that arrives after clear', () => {
      const reply = new Subject<Program[]>();
      userService.addProgramToWishlist.and.returnValue(reply);
      service.toggleProgram(p2);

      service.clear();
      reply.next([p2]);

      expect(service.programs()).toEqual([]);
      expect(service.isProgramPending(12)).toBeFalse();
    });
  });
});

describe('WishlistService HTTP boundary', () => {
  const school: School = { id: 1, name: 'A', address: { location: '', city: '', country: '' } };

  it('sends no user or wishlist request for an expert, then loads for a student', () => {
    const user = signal<{ id: number; role: { name: string } } | null>({ id: 9, role: { name: 'ROLE_EXPERT' } });
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TokenService, useValue: { user } },
      ],
    });
    const service = TestBed.inject(WishlistService);
    const http = TestBed.inject(HttpTestingController);

    service.load();
    service.toggle(school);
    http.expectNone((request) => request.url.includes('/users/'));

    user.set({ id: 9, role: { name: 'ROLE_STUDENT' } });
    TestBed.tick();
    service.load();
    const request = http.expectOne(`${environment.apiUrl}/users/id/9`);
    expect(request.request.method).toBe('GET');
    request.flush({ id: 9, wishlist: [school] });
    http.expectOne(`${environment.apiUrl}/users/9/wishlist/programs`).flush([{ id: 5, name: 'P' }]);
    expect(service.programs().map((item) => item.id)).toEqual([5]);
    expect(service.schools().map((item) => item.id)).toEqual([1]);
    http.verify();
  });
});
