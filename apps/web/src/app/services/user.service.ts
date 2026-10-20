import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, map, of, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { Program, User } from '@models/entities';
import { MediaService } from './media.service';
import { TokenService } from './token.service';

/**
 * The backend `UpdateProfileRequest`. Every field is optional: an omitted (null)
 * field is left unchanged, and so is an empty `password`, which otherwise must
 * be 8 characters or more. The role is deliberately absent — it only changes
 * through the admin-only `PATCH /users/{id}/role` — and so is the email, which
 * changes through {@link UserService.requestEmailChange}.
 */
export interface ProfileUpdate {
  firstname?: string;
  lastname?: string;
  birthday?: string;
  password?: string;
  currentPassword?: string;
  qualification?: string;
}

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private http = inject(HttpClient);
  private mediaService = inject(MediaService);
  private tokenService = inject(TokenService);
  private apiUrl = `${environment.apiUrl}/users`;

  private usersSignal = signal<User[]>([]);
  readonly users = this.usersSignal.asReadonly();

  getUser(id: number): Observable<User> {
    return this.http.get<User>(`${this.apiUrl}/id/${id}`).pipe(
      map(user => this.mapUserFields(user)),
      tap(user => {
        this.usersSignal.update(users => {
          const index = users.findIndex(u => u.id === user.id);
          if (index !== -1) {
            users[index] = user;
            return [...users];
          }
          return [...users, user];
        });
      })
    );
  }

  private mapUserFields(user: User): User {
    return {
      ...user,
      // Media objects now, not URL strings. These are PRIVATE — render their
      // content via MediaService.blobUrl(id), never a bare <img src>.
      diplomas: user.diplomas || [],
      certificates: user.certificates || [],
      presentationVideo: user.presentationVideo ?? null,
      wishlist: (user.wishlist || []).map(school => ({
        ...school,
        rating: (school as any).averageRate || school.rating || 0,
        coverImageUrl: this.mediaService.resolveUrl(school.cover) ?? school.coverImageUrl
      }))
    };
  }

  /** Re-reads the signed-in account and stores the fresh approval status in the session. */
  refreshSessionUser(): Observable<User | null> {
    const current = this.tokenService.user();
    if (current?.id === undefined) return of(null);
    return this.getUser(current.id).pipe(
      map((fresh) => {
        const { expertStatus, expertStatusReason } = fresh;
        if (expertStatus === current.expertStatus && expertStatusReason === current.expertStatusReason) return current;
        const updated: User = { ...current, expertStatus, expertStatusReason };
        this.tokenService.setUser(updated);
        return updated;
      }),
      // The session stays as it was: the API remains the judge of every action.
      catchError(() => of(null)),
    );
  }

  /** Self-service profile save: `PATCH /users/{id}` (the owner or an admin). */
  updateProfile(id: number, changes: ProfileUpdate): Observable<User> {
    return this.http.patch<User>(`${this.apiUrl}/${id}`, changes);
  }

  /**
   * Asks to move the account to a new address. The address only changes once the link mailed to it is
   * opened. 202; 400 `{currentPassword}` / `{newEmail}`; 429 limit or cooldown; 503 mail off.
   */
  requestEmailChange(id: number, newEmail: string, currentPassword: string, lang: string): Observable<unknown> {
    return this.http.post(`${this.apiUrl}/${id}/email-change`, { newEmail, currentPassword, lang });
  }

  /** The address waiting for its link, or null when no change is pending. */
  getPendingEmail(id: number): Observable<string | null> {
    return this.http.get<{ pendingEmail?: string }>(`${this.apiUrl}/${id}/email-change`).pipe(
      map(body => body.pendingEmail ?? null)
    );
  }

  /** Drops the pending change; its link stops working. */
  cancelEmailChange(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}/email-change`);
  }

  /** Mails the link of the pending change again. 202; 409 nothing pending; 429 limit or cooldown. */
  resendEmailChange(id: number, lang: string): Observable<unknown> {
    return this.http.post(`${this.apiUrl}/${id}/email-change/resend`, {}, { params: new HttpParams().set('lang', lang) });
  }

  addToWishlist(userId: number, schoolId: number): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/${userId}/wishlist/${schoolId}`, {}).pipe(
      map(user => this.mapUserFields(user))
    );
  }

  removeFromWishlist(userId: number, schoolId: number): Observable<User> {
    return this.http.delete<User>(`${this.apiUrl}/${userId}/wishlist/${schoolId}`).pipe(
      map(user => this.mapUserFields(user))
    );
  }

  /** Programs of the student's wishlist (schools stay on `UserDTO.wishlist`). */
  getProgramWishlist(userId: number): Observable<Program[]> {
    return this.http.get<Program[]>(`${this.apiUrl}/${userId}/wishlist/programs`).pipe(
      map(programs => programs.map(program => this.mapProgram(program)))
    );
  }

  /** Idempotent; answers the updated list of programs. */
  addProgramToWishlist(userId: number, programId: number): Observable<Program[]> {
    return this.http.post<Program[]>(`${this.apiUrl}/${userId}/wishlist/programs/${programId}`, {}).pipe(
      map(programs => programs.map(program => this.mapProgram(program)))
    );
  }

  removeProgramFromWishlist(userId: number, programId: number): Observable<Program[]> {
    return this.http.delete<Program[]>(`${this.apiUrl}/${userId}/wishlist/programs/${programId}`).pipe(
      map(programs => programs.map(program => this.mapProgram(program)))
    );
  }

  private mapProgram(program: Program): Program {
    return { ...program, photoImageUrl: this.mediaService.resolveUrl(program.photo) ?? program.photoImageUrl };
  }
}
