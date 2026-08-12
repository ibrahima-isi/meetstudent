import { Injectable, signal } from '@angular/core';

/**
 * Holds the address between registering and verifying. Deliberately not a query
 * parameter: an email is personal data, and URLs are shared, logged and cached.
 * It is in-memory only, so a reload clears it — `email-verification` asks for
 * the address when it is empty, which also covers arriving at `/verify` cold.
 */
@Injectable({ providedIn: 'root' })
export class RegistrationFlowService {
  private readonly pending = signal('');

  readonly pendingEmail = this.pending.asReadonly();

  remember(email: string): void {
    this.pending.set(email);
  }

  clear(): void {
    this.pending.set('');
  }
}
