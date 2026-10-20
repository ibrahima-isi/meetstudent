import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { timeout } from 'rxjs';
import { Program, School } from '@models/entities';
import { ROLE_STUDENT } from '@models/roles';
import { TokenService } from './token.service';
import { UserService } from './user.service';

/**
 * The user's wishlist of schools and programs (two server lists, one service). The server
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
  private owner: string | undefined = this.sessionOwner();

  private readonly state = signal<School[]>([]);
  private readonly pending = signal<ReadonlySet<number>>(new Set());
  private readonly programState = signal<Program[]>([]);
  private readonly programPending = signal<ReadonlySet<number>>(new Set());

  readonly schools = this.state.asReadonly();
  readonly programs = this.programState.asReadonly();
  /** True when only the program list could not be read; the schools are unaffected. */
  readonly programsError = signal(false);
  readonly available = computed(() => this.tokenService.user()?.role?.name === ROLE_STUDENT);
  readonly status = signal<'idle' | 'loading' | 'loaded' | 'error'>('idle');
  /** True after a failed add/remove (already rolled back) until dismissed. */
  readonly error = signal(false);
  private readonly ids = computed(() => new Set(this.state().map((s) => s.id)));
  private readonly programIds = computed(() => new Set(this.programState().map((p) => p.id)));

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
    const owner = this.sessionOwner();
    if (owner !== this.owner) {
      this.owner = owner;
      this.clear();
    }
  }

  private sessionOwner(): string | undefined {
    const user = this.tokenService.user();
    return user ? `${user.id}:${user.role?.name ?? ''}` : undefined;
  }

  has(schoolId: number | undefined): boolean {
    return schoolId !== undefined && this.ids().has(schoolId);
  }

  isPending(schoolId: number | undefined): boolean {
    return schoolId !== undefined && this.pending().has(schoolId);
  }

  hasProgram(programId: number | undefined): boolean {
    return programId !== undefined && this.programIds().has(programId);
  }

  isProgramPending(programId: number | undefined): boolean {
    return programId !== undefined && this.programPending().has(programId);
  }

  /** Forgets everything; called on logout so the next session never sees this one's list. */
  clear(): void {
    this.generation++;
    this.state.set([]);
    this.pending.set(new Set());
    this.programState.set([]);
    this.programPending.set(new Set());
    this.programsError.set(false);
    this.status.set('idle');
    this.error.set(false);
  }

  dismissError(): void {
    this.error.set(false);
  }

  /** Fetches the wishlist from the API; also the retry action of the error state. */
  load(): void {
    this.syncOwner();
    if (!this.available()) return;
    const userId = this.tokenService.user()?.id;
    if (userId === undefined) {
      this.state.set([]);
      this.programState.set([]);
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
        this.syncOwner();
        if (generation !== this.generation) {
          return;
        }
        this.state.set(user.wishlist ?? []);
        this.status.set('loaded');
      },
      error: () => {
        this.syncOwner();
        if (generation === this.generation) {
          this.status.set('error');
        }
      },
    });
    this.loadPrograms(userId, generation);
  }

  /**
   * Programs load apart from the schools: if that endpoint fails the schools still show, and
   * `programsError` carries a quiet, non-blocking note instead of the shared error state.
   */
  private loadPrograms(userId: number, generation: number): void {
    this.programsError.set(false);
    this.userService.getProgramWishlist(userId).pipe(timeout(LOAD_TIMEOUT_MS)).subscribe({
      next: (programs) => {
        this.syncOwner();
        if (generation === this.generation) {
          this.programState.set(programs);
        }
      },
      error: () => {
        this.syncOwner();
        if (generation === this.generation) {
          this.programsError.set(true);
        }
      },
    });
  }

  /** Adds or removes a school: shown at once, rolled back if the server refuses. */
  toggle(school: School): void {
    this.syncOwner();
    if (!this.available()) return;
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
        this.syncOwner();
        if (generation !== this.generation) {
          return;
        }
        this.state.set(user.wishlist ?? []);
        this.setPending(schoolId, false);
      },
      error: () => {
        this.syncOwner();
        if (generation !== this.generation) {
          return;
        }
        this.state.update((list) => (adding ? list.filter((s) => s.id !== schoolId) : [...list, school]));
        this.error.set(true);
        this.setPending(schoolId, false);
      },
    });
  }

  /** Adds or removes a program, like {@link toggle} does for a school. */
  toggleProgram(program: Program): void {
    this.syncOwner();
    if (!this.available()) return;
    const userId = this.tokenService.user()?.id;
    const programId = program.id;
    if (userId === undefined || programId === undefined || this.isProgramPending(programId)) {
      return;
    }

    const generation = this.generation;
    const adding = !this.hasProgram(programId);
    this.error.set(false);
    this.setProgramPending(programId, true);
    this.programState.update((list) => (adding ? [...list, program] : list.filter((p) => p.id !== programId)));

    const request = adding
      ? this.userService.addProgramToWishlist(userId, programId)
      : this.userService.removeProgramFromWishlist(userId, programId);
    request.subscribe({
      next: (programs) => {
        this.syncOwner();
        if (generation !== this.generation) {
          return;
        }
        this.programState.set(programs);
        this.setProgramPending(programId, false);
      },
      error: () => {
        this.syncOwner();
        if (generation !== this.generation) {
          return;
        }
        this.programState.update((list) => (adding ? list.filter((p) => p.id !== programId) : [...list, program]));
        this.error.set(true);
        this.setProgramPending(programId, false);
      },
    });
  }

  private setProgramPending(programId: number, on: boolean): void {
    this.programPending.update((current) => {
      const next = new Set(current);
      if (on) {
        next.add(programId);
      } else {
        next.delete(programId);
      }
      return next;
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
