import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { User } from '@models/entities';
import { MediaService } from './media.service';

/**
 * The backend `UpdateProfileRequest`. Every field is optional: an omitted (null)
 * field is left unchanged, and so is an empty `password`, which otherwise must
 * be 8 characters or more. The role is deliberately absent — it only changes
 * through the admin-only `PATCH /users/{id}/role`.
 */
export interface ProfileUpdate {
  firstname?: string;
  lastname?: string;
  email?: string;
  birthday?: string;
  password?: string;
  qualification?: string;
}

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private http = inject(HttpClient);
  private mediaService = inject(MediaService);
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

  /** Self-service profile save: `PATCH /users/{id}` (the owner or an admin). */
  updateProfile(id: number, changes: ProfileUpdate): Observable<User> {
    return this.http.patch<User>(`${this.apiUrl}/${id}`, changes);
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
}
