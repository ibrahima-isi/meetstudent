import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TokenService } from './token.service';
import { User } from '@models/entities';

const admin: User = {
  id: 1,
  firstname: 'Ada',
  lastname: 'Admin',
  email: 'ada@x.io',
  role: { name: 'ROLE_ADMIN' },
};

describe('TokenService', () => {
  const create = () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
    return TestBed.inject(TokenService);
  };

  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it('starts anonymous', () => {
    const tokens = create();
    expect(tokens.isAuthenticated()).toBeFalse();
    expect(tokens.isAdmin()).toBeFalse();
  });

  it('persists tokens and user, and restores them in a new instance', () => {
    const tokens = create();
    tokens.setTokens('a', 'r');
    tokens.setUser(admin);

    const reloaded = create();
    expect(reloaded.token()).toBe('a');
    expect(reloaded.refreshToken()).toBe('r');
    expect(reloaded.user()).toEqual(admin);
    expect(reloaded.isAuthenticated()).toBeTrue();
    expect(reloaded.isAdmin()).toBeTrue();
  });

  it('is not admin for another role', () => {
    const tokens = create();
    tokens.setUser({ ...admin, role: { name: 'ROLE_STUDENT' } });
    expect(tokens.isAdmin()).toBeFalse();
  });

  it('clear wipes signals and storage', () => {
    const tokens = create();
    tokens.setTokens('a', 'r');
    tokens.setUser(admin);
    tokens.clear();

    expect(tokens.token()).toBeNull();
    expect(tokens.user()).toBeNull();
    expect(localStorage.length).toBe(0);
  });

  it('ignores a corrupted stored user', () => {
    localStorage.setItem('auth_user', '{not json');
    expect(create().user()).toBeNull();
  });
});
