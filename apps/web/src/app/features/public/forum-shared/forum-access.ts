import { Signal, computed, effect, inject, signal } from '@angular/core';
import { ROLE_EXPERT, ROLE_STUDENT } from '@models/roles';
import { AuthService } from '@services/auth.service';
import { TokenService } from '@services/token.service';
import { injectHydratedSession } from '@shared/hydrated-session';

/**
 * What the visitor can do in the forum, for the pages to explain it instead of offering controls the API would refuse:
 * `writer` (student, or expert not known to be blocked), `pending` / `rejected` (expert awaiting or refused),
 * `unverified` (e-mail not confirmed while the server sends mail: the API refuses writing then),
 * `readOnly` (administrator, or any other role), `anonymous`.
 * An expert without a status (session stored before approval existed) is not blocked: the API stays the judge.
 * Precedence: a refused or pending expert sees that reason first (confirming the e-mail would not unblock them),
 * then the unconfirmed e-mail.
 */
export type ForumWriteState = 'anonymous' | 'writer' | 'pending' | 'rejected' | 'unverified' | 'readOnly';

/** Call in an injection context. The server always renders `anonymous` (the session is in localStorage), see `injectHydratedSession`. */
export function injectForumWriteState(): Signal<ForumWriteState> {
  const token = inject(TokenService);
  const auth = inject(AuthService);
  const session = injectHydratedSession();

  const unconfirmed = computed(() => session.signedIn() && token.user()?.emailVerified === false);
  /** Whether the server sends mail (and so requires a confirmed e-mail to write); read only for an unconfirmed account. */
  const emailEnabled = signal(false);
  let asked = false;
  effect(() => {
    if (!unconfirmed() || asked) return;
    asked = true;
    auth.getCapabilities().subscribe((capabilities) => emailEnabled.set(capabilities.emailEnabled));
  });

  return computed(() => {
    if (!session.signedIn()) return 'anonymous';
    const user = token.user();
    const role = user?.role?.name;
    if (role === ROLE_STUDENT) return unconfirmed() && emailEnabled() ? 'unverified' : 'writer';
    if (role === ROLE_EXPERT) {
      if (user?.expertStatus === 'PENDING') return 'pending';
      if (user?.expertStatus === 'REJECTED') return 'rejected';
      return unconfirmed() && emailEnabled() ? 'unverified' : 'writer';
    }
    return 'readOnly';
  });
}
