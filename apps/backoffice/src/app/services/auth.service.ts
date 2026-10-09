import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, switchMap, tap, throwError } from 'rxjs';
import { LoginResponse, ROLE_ADMIN, User } from '@models/entities';
import { API_URL } from './api-config';
import { TokenService } from './token.service';

/** The credentials are valid but the account is not an administrator. */
export class NotAdminError extends Error {
  constructor() {
    super('Accès réservé aux administrateurs.');
    this.name = 'NotAdminError';
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokens = inject(TokenService);
  private readonly apiUrl = inject(API_URL);

  /** Logs in; a non-admin is logged straight back out and gets a `NotAdminError`. */
  login(username: string, password: string): Observable<User> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/auth`, { username, password }).pipe(
      tap((res) => this.tokens.setTokens(res.accessToken, res.refreshToken)),
      switchMap(() => this.http.get<User>(`${this.apiUrl}/users/email/${encodeURIComponent(username)}`)),
      switchMap((user) => {
        if (user.role?.name !== ROLE_ADMIN) {
          this.logout();
          return throwError(() => new NotAdminError());
        }
        this.tokens.setUser(user);
        return [user];
      }),
    );
  }

  refreshToken(refreshToken: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.apiUrl}/auth/refresh`, { refreshToken })
      .pipe(tap((res) => this.tokens.setTokens(res.accessToken, res.refreshToken)));
  }

  logout() {
    this.tokens.clear();
  }
}
