import { Injectable, computed, inject, signal } from '@angular/core';
import { School } from '@models/entities';
import { TokenService } from './token.service';
import { UserService } from './user.service';

/**
 * The user's wishlist of SCHOOLS (the backend models it that way). The server
 * is the source of truth: it is loaded from the API, mutations are applied
 * optimistically and then replaced by the server's answer, or rolled back.
 */
@Injectable({ providedIn: 'root' })
export class WishlistService {
  private readonly userService = inject(UserService);
  private readonly tokenService = inject(TokenService);

  private readonly state = signal<School[]>([]);
  private readonly pending = signal<ReadonlySet<number>>(new Set());

  readonly schools = this.state.asReadonly();
  readonly status = signal<'idle' | 'loading' | 'loaded' | 'error'>('idle');
  /** True after a failed add/remove (already rolled back) until dismissed. */
  readonly error = signal(false);
  private readonly ids = computed(() => new Set(this.state().map((s) => s.id)));

  has(schoolId: number | undefined): boolean {
    return schoolId !== undefined && this.ids().has(schoolId);
  }

  isPending(schoolId: number | undefined): boolean {
    return schoolId !== undefined && this.pending().has(schoolId);
  }

  dismissError(): void {
    this.error.set(false);
  }

  /** Fetches the wishlist from the API; also the retry action of the error state. */
  load(): void {
    const userId = this.tokenService.user()?.id;
    if (userId === undefined) {
      this.state.set([]);
      return;
    }
    this.status.set('loading');
    this.userService.getUser(userId).subscribe({
      next: (user) => {
        this.state.set(user.wishlist ?? []);
        this.status.set('loaded');
      },
      error: () => this.status.set('error'),
    });
  }

  /** Adds or removes a school: shown at once, rolled back if the server refuses. */
  toggle(school: School): void {
    const userId = this.tokenService.user()?.id;
    const schoolId = school.id;
    if (userId === undefined || schoolId === undefined || this.isPending(schoolId)) {
      return;
    }

    const adding = !this.has(schoolId);
    this.error.set(false);
    this.setPending(schoolId, true);
    this.state.update((list) => (adding ? [...list, school] : list.filter((s) => s.id !== schoolId)));

    const request = adding
      ? this.userService.addToWishlist(userId, schoolId)
      : this.userService.removeFromWishlist(userId, schoolId);
    request.subscribe({
      next: (user) => {
        this.state.set(user.wishlist ?? []);
        this.setPending(schoolId, false);
      },
      error: () => {
        this.state.update((list) => (adding ? list.filter((s) => s.id !== schoolId) : [...list, school]));
        this.error.set(true);
        this.setPending(schoolId, false);
      },
    });
  }

  private setPending(schoolId: number, on: boolean): void {
    this.pending.update((current) => {
      const next = new Set(current);
      if (on) {
        next.add(schoolId);
      } else {
        next.delete(schoolId);
      }
      return next;
    });
  }
}
