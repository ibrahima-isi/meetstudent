import { DestroyRef, Injectable, inject, signal, computed } from '@angular/core';
import { User } from '@models/entities';

@Injectable({
  providedIn: 'root'
})
export class TokenService {
  private readonly TOKEN_KEY = 'auth_token';
  private readonly REFRESH_TOKEN_KEY = 'refresh_token';
  private readonly USER_KEY = 'auth_user';

  private readonly KEYS = [this.TOKEN_KEY, this.REFRESH_TOKEN_KEY, this.USER_KEY];

  token = signal<string | null>(this.getStoredToken());
  refreshToken = signal<string | null>(this.getStoredRefreshToken());
  user = signal<User | null>(this.getStoredUser());

  isAuthenticated = computed(() => !!this.token());

  constructor() {
    const destroyRef = inject(DestroyRef);
    if (typeof window === 'undefined') return;
    const onStorage = (event: StorageEvent) => {
      if (event.storageArea !== localStorage) return;
      if (event.key !== null && !this.KEYS.includes(event.key)) return;
      this.syncFromStorage();
    };
    window.addEventListener('storage', onStorage);
    destroyRef.onDestroy(() => window.removeEventListener('storage', onStorage));
  }

  setTokens(accessToken: string, refreshToken: string) {
    localStorage.setItem(this.TOKEN_KEY, accessToken);
    localStorage.setItem(this.REFRESH_TOKEN_KEY, refreshToken);
    this.token.set(accessToken);
    this.refreshToken.set(refreshToken);
    
    // Decode user from token if possible, or fetch it separately
    // For now we rely on explicit setUser call from AuthService
  }

  setUser(user: User) {
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
    this.user.set(user);
  }

  clear() {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.token.set(null);
    this.refreshToken.set(null);
    this.user.set(null);
  }

  /**
   * Adopts what another tab left in localStorage: a rotated pair of tokens, a refreshed user, or a sign-out
   * (no access token stored any more). Called on `storage` events, and by the refresh interceptor right before it
   * would spend this tab's refresh token, which another tab may already have rotated (the server treats a replay of an
   * old one as theft and ends the session everywhere). Does nothing outside a browser.
   */
  syncFromStorage(): void {
    if (typeof localStorage === 'undefined') return;
    if (this.getStoredToken() === null) {
      if (this.token() !== null || this.refreshToken() !== null || this.user() !== null) this.clear();
      return;
    }
    this.token.set(this.getStoredToken());
    this.refreshToken.set(this.getStoredRefreshToken());
    try {
      this.user.set(this.getStoredUser());
    } catch {
      // An unreadable user payload: keep the one this tab has.
    }
  }

  private getStoredToken(): string | null {
    if (typeof localStorage === 'undefined') return null;
    return localStorage.getItem(this.TOKEN_KEY);
  }

  private getStoredRefreshToken(): string | null {
    if (typeof localStorage === 'undefined') return null;
    return localStorage.getItem(this.REFRESH_TOKEN_KEY);
  }

  private getStoredUser(): User | null {
    if (typeof localStorage === 'undefined') return null;
    const stored = localStorage.getItem(this.USER_KEY);
    return stored ? JSON.parse(stored) : null;
  }
}
