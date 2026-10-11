import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, switchMap, map, catchError, of, shareReplay } from 'rxjs';
import { TokenService } from './token.service';
import { User, LoginResponse } from '@models/entities';
import { environment } from '../../environments/environment';

/**
 * Exactly the backend `RegisterRequest` fields (`UserController.saveUser`).
 * `role` is deliberately absent — registration always creates a STUDENT.
 */
export interface RegisterPayload {
  firstname: string;
  lastname: string;
  email: string;
  password: string;
  confirmedPassword: string;
  birthday?: string;
  qualification?: string;
}

/** What the server can do right now; only the mail switch matters to the web. */
export interface Capabilities {
  emailEnabled: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private tokenService = inject(TokenService);
  private apiUrl = `${environment.apiUrl}/auth`;
  private usersUrl = `${environment.apiUrl}/users`;
  private capabilities$?: Observable<Capabilities>;

  login(username: string, password: string): Observable<User> {
    return this.http.post<LoginResponse>(this.apiUrl, { username, password }).pipe(
      tap(res => {
        this.tokenService.setTokens(res.accessToken, res.refreshToken);
      }),
      switchMap(() => this.getCurrentUserProfile(username)),
      tap(user => {
        this.tokenService.setUser(user);
      })
    );
  }

  getCurrentUserProfile(email: string): Observable<User> {
    return this.http.get<User>(`${this.usersUrl}/email/${email}`);
  }

  register(userData: RegisterPayload): Observable<User> {
    // Registration endpoint is POST /api/v1/users according to UserController
    return this.http.post<User>(this.usersUrl, userData);
  }

  refreshToken(refreshToken: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/refresh`, { refreshToken }).pipe(
      tap(res => {
        this.tokenService.setTokens(res.accessToken, res.refreshToken);
      })
    );
  }

  /** Always 202 for a well-formed address, whether or not an account exists. */
  requestPasswordReset(email: string, lang: string): Observable<unknown> {
    return this.http.post(`${this.apiUrl}/password-reset/request`, { email, lang });
  }

  /** 204 on success; 400 `{token}` for a bad link, `{password}` / `{confirmedPassword}` otherwise. */
  confirmPasswordReset(token: string, password: string, confirmedPassword: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/password-reset/confirm`, { token, password, confirmedPassword });
  }

  /**
   * Read once and shared. Any failure means "assume no mail": the pages then
   * say delivery is unavailable instead of promising an email that may never come.
   */
  getCapabilities(): Observable<Capabilities> {
    this.capabilities$ ??= this.http.get<Capabilities>(`${this.apiUrl}/capabilities`).pipe(
      catchError(() => of<Capabilities>({ emailEnabled: false })),
      shareReplay({ bufferSize: 1, refCount: false })
    );
    return this.capabilities$;
  }

  logout() {
    this.tokenService.clear();
  }
}
