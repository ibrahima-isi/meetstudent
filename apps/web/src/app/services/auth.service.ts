import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, switchMap, map } from 'rxjs';
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

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private tokenService = inject(TokenService);
  private apiUrl = `${environment.apiUrl}/auth`;
  private usersUrl = `${environment.apiUrl}/users`;

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

  logout() {
    this.tokenService.clear();
  }
}
