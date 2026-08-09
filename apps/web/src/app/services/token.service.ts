import { Injectable, signal, computed } from '@angular/core';
import { User } from '@models/entities';

@Injectable({
  providedIn: 'root'
})
export class TokenService {
  private readonly TOKEN_KEY = 'auth_token';
  private readonly REFRESH_TOKEN_KEY = 'refresh_token';
  private readonly USER_KEY = 'auth_user';

  token = signal<string | null>(this.getStoredToken());
  refreshToken = signal<string | null>(this.getStoredRefreshToken());
  user = signal<User | null>(this.getStoredUser());

  isAuthenticated = computed(() => !!this.token());

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
