import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { User } from '@models/entities';
import { TokenService } from './token.service';

describe('TokenService', () => {
  let tokens: TokenService;

  const user = (firstname: string) => ({ id: 1, firstname, lastname: 'B', email: 'a@b.c', role: { id: 1, name: 'ROLE_STUDENT' } }) as User;

  /** What another tab does: writes to localStorage, which fires `storage` here only (never in the writing tab). */
  function otherTabWrites(key: string, value: string | null) {
    const oldValue = localStorage.getItem(key);
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
    window.dispatchEvent(new StorageEvent('storage', { key, oldValue, newValue: value, storageArea: localStorage }));
  }

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
    tokens = TestBed.inject(TokenService);
    tokens.setTokens('access-1', 'refresh-1');
    tokens.setUser(user('Ada'));
  });

  afterEach(() => localStorage.clear());

  describe('another tab rotates the session', () => {
    it('adopts the new access and refresh tokens', () => {
      otherTabWrites('auth_token', 'access-2');
      otherTabWrites('refresh_token', 'refresh-2');

      expect(tokens.token()).toBe('access-2');
      expect(tokens.refreshToken()).toBe('refresh-2');
      expect(tokens.isAuthenticated()).toBeTrue();
    });

    it('adopts a refresh token written alone', () => {
      otherTabWrites('refresh_token', 'refresh-2');
      expect(tokens.refreshToken()).toBe('refresh-2');
      expect(tokens.token()).toBe('access-1');
    });

    it('adopts the user the other tab saved', () => {
      otherTabWrites('auth_user', JSON.stringify(user('Grace')));
      expect(tokens.user()?.firstname).toBe('Grace');
    });

    it('signs in a tab that had no session when another tab signs in', () => {
      tokens.clear();
      otherTabWrites('auth_token', 'access-9');
      otherTabWrites('refresh_token', 'refresh-9');
      expect(tokens.isAuthenticated()).toBeTrue();
      expect(tokens.refreshToken()).toBe('refresh-9');
    });

    it('ignores an unreadable user payload instead of throwing', () => {
      expect(() => otherTabWrites('auth_user', '{not json')).not.toThrow();
      expect(tokens.user()?.firstname).toBe('Ada');
    });
  });

  describe('another tab signs out', () => {
    it('forgets the session when the access token is removed', () => {
      localStorage.removeItem('refresh_token');
      localStorage.removeItem('auth_user');
      otherTabWrites('auth_token', null);
      expect(tokens.token()).toBeNull();
      expect(tokens.refreshToken()).toBeNull();
      expect(tokens.user()).toBeNull();
    });

    it('forgets the session when storage is cleared altogether', () => {
      localStorage.clear();
      window.dispatchEvent(new StorageEvent('storage', { key: null, storageArea: localStorage }));
      expect(tokens.isAuthenticated()).toBeFalse();
      expect(tokens.user()).toBeNull();
    });

    it('keeps the session when a sign-in is already stored by the time the removal is seen', () => {
      localStorage.setItem('auth_token', 'access-2');
      window.dispatchEvent(new StorageEvent('storage', { key: 'auth_token', oldValue: 'access-1', newValue: null, storageArea: localStorage }));
      expect(tokens.token()).toBe('access-2');
    });
  });

  describe('what it must not react to', () => {
    it('ignores unrelated keys', () => {
      otherTabWrites('theme', 'dark');
      expect(tokens.token()).toBe('access-1');
      expect(tokens.refreshToken()).toBe('refresh-1');
    });

    it('ignores sessionStorage events', () => {
      window.dispatchEvent(new StorageEvent('storage', { key: 'refresh_token', newValue: 'refresh-evil', storageArea: sessionStorage }));
      expect(tokens.refreshToken()).toBe('refresh-1');
    });

    it('stops listening once destroyed', () => {
      TestBed.resetTestingModule();
      otherTabWrites('refresh_token', 'refresh-late');
      expect(tokens.refreshToken()).toBe('refresh-1');
    });
  });
});
