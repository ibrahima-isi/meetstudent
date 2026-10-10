import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { timeout } from 'rxjs';
import { School } from '@models/entities';
import { TokenService } from './token.service';
import { UserService } from './user.service';

/**
 * The user's wishlist of SCHOOLS (the backend models it that way). The server
 * is the source of truth: it is loaded from the API, mutations are applied
 * optimistically and then replaced by the server's answer, or rolled back.
 */
/** A load that never answers would block every later one; after this it counts as failed. */
const LOAD_TIMEOUT_MS = 15000;

@Injectable({ providedIn: 'root' })
export class WishlistService {
  private readonly userService = inject(UserService);
  private readonly tokenService = inject(TokenService);

  /**
   * Bumped whenever the list is cleared or its owner changes. Every request
   * remembers the value it started under and drops its answer if it moved, so
   * a slow response can never refill (or roll back onto) someone else's list.
   */
  private generation = 0;
  private owner: number | undefined = this.tokenService.user()?.id;

  private readonly state = signal<School[]>([]);
  private readonly pending = signal<ReadonlySet<number>>(new Set());

  readonly schools = this.state.asReadonly();
  readonly status = signal<'idle' | 'loading' | 'loaded' | 'error'>('idle');
  /** True after a failed add/remove (already rolled back) until dismissed. */
  readonly error = signal(false);
  private readonly ids = computed(() => new Set(this.state().map((s) => s.id)));

  constructor() {
    // The one place every sign-out path goes through (dock, home page, refresh
    // interceptor): they all end up changing the session user in TokenService.
    // It runs asynchronously, so load() and toggle() also sync the owner themselves.
    effect(() => {
      this.tokenService.user();
      untracked(() => this.syncOwner());
    });
  }

  /** Resets the list the moment the session user differs from the one it belongs to. */
  private syncOwner(): void {
    const id = this.tokenService.user()?.id;
    if (id !== this.owner) {
      this.owner = id;
      this.clear();
    }
  }

  has(schoolId: number | undefined): boolean {
    return schoolId !== undefined && this.ids().has(schoolId);
  }

  isPending(schoolId: number | undefined): boolean {
    return schoolId !== undefined && this.pending().has(schoolId);
  }

  /** Forgets everything; called on logout so the next session never sees this one's list. */
  clear(): void {
    this.generation++;
    this.state.set([]);
    this.pending.set(new Set());
    this.status.set('idle');
    this.error.set(false);
  }

  dismissError(): void {
    this.error.set(false);
  }

  /** Fetches the wishlist from the API; also the retry action of the error state. */
  load(): void {
    this.syncOwner();
    const userId = this.tokenService.user()?.id;
    if (userId === undefined) {
      this.state.set([]);
      return;
    }
    // Home and the navbar cart both ask on entry; one request answers both.
    // clear() resets the status, so a new user is never held back by an old load.
    if (this.status() === 'loading') {
      return;
    }
    const generation = this.generation;
    this.status.set('loading');
    this.userService.getUser(userId).pipe(timeout(LOAD_TIMEOUT_MS)).subscribe({
      next: (user) => {
        if (generation !== this.generation) {
          return;
        }
        this.state.set(user.wishlist ?? []);
        this.status.set('loaded');
      },
      error: () => {
        if (generation === this.generation) {
          this.status.set('error');
        }
      },
    });
  }

  /** Adds or removes a school: shown at once, rolled back if the server refuses. */
  toggle(school: School): void {
    this.syncOwner();
    const userId = this.tokenService.user()?.id;
    const schoolId = school.id;
    if (userId === undefined || schoolId === undefined || this.isPending(schoolId)) {
      return;
    }

    const generation = this.generation;
    const adding = !this.has(schoolId);
    this.error.set(false);
    this.setPending(schoolId, true);
    this.state.update((list) => (adding ? [...list, school] : list.filter((s) => s.id !== schoolId)));

    const request = adding
      ? this.userService.addToWishlist(userId, schoolId)
      : this.userService.removeFromWishlist(userId, schoolId);
    request.subscribe({
      next: (user) => {
        if (generation !== this.generation) {
          return;
        }
        this.state.set(user.wishlist ?? []);
        this.setPending(schoolId, false);
      },
      error: () => {
        if (generation !== this.generation) {
          return;
        }
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
