import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { User } from '@models/entities';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/users`;

  private usersSignal = signal<User[]>([]);
  readonly users = this.usersSignal.asReadonly();

  getUser(id: number): Observable<User> {
    return this.http.get<User>(`${this.apiUrl}/${id}`).pipe(
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
      // Ensure arrays are initialized if missing
      diplomas: user.diplomas || [],
      certificates: user.certificates || [],
      wishlist: (user.wishlist || []).map(school => ({
        ...school,
        rating: (school as any).averageRate || school.rating || 0,
        type: school.type || 'Établissement'
      }))
    };
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
