import { Injectable, computed, signal } from '@angular/core';
import { ROLE_ADMIN, User } from '@models/entities';

const TOKEN_KEY = 'auth_token';
const REFRESH_TOKEN_KEY = 'auth_refresh_token';
const USER_KEY = 'auth_user';

const read = (key: string): string | null => {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage.getItem(key);
  } catch {
    return null;
  }
};

const readUser = (): User | null => {
  const stored = read(USER_KEY);
  if (!stored) return null;
  try {
    return JSON.parse(stored) as User;
  } catch {
    return null;
  }
};

@Injectable({ providedIn: 'root' })
export class TokenService {
  readonly token = signal<string | null>(read(TOKEN_KEY));
  readonly refreshToken = signal<string | null>(read(REFRESH_TOKEN_KEY));
  readonly user = signal<User | null>(readUser());

  readonly isAuthenticated = computed(() => !!this.token());
  readonly isAdmin = computed(() => this.user()?.role?.name === ROLE_ADMIN);

  setTokens(accessToken: string, refreshToken: string) {
    localStorage.setItem(TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    this.token.set(accessToken);
    this.refreshToken.set(refreshToken);
  }

  setUser(user: User) {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this.user.set(user);
  }

  clear() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.token.set(null);
    this.refreshToken.set(null);
    this.user.set(null);
  }
}
