import { Signal, computed, inject } from '@angular/core';
import { ROLE_EXPERT, ROLE_STUDENT } from '@models/roles';
import { TokenService } from '@services/token.service';
import { injectHydratedSession } from '@shared/hydrated-session';

/**
 * What the visitor can do in the forum, for the pages to explain it instead of offering controls the API would refuse:
 * `writer` (student, or expert not known to be blocked), `pending` / `rejected` (expert awaiting or refused),
 * `readOnly` (administrator, or any other role), `anonymous`.
 * An expert without a status (session stored before approval existed) is not blocked: the API stays the judge.
 */
export type ForumWriteState = 'anonymous' | 'writer' | 'pending' | 'rejected' | 'readOnly';

/** Call in an injection context. The server always renders `anonymous` (the session is in localStorage), see `injectHydratedSession`. */
export function injectForumWriteState(): Signal<ForumWriteState> {
  const token = inject(TokenService);
  const session = injectHydratedSession();
  return computed(() => {
    if (!session.signedIn()) return 'anonymous';
    const user = token.user();
    const role = user?.role?.name;
    if (role === ROLE_STUDENT) return 'writer';
    if (role === ROLE_EXPERT) {
      if (user?.expertStatus === 'PENDING') return 'pending';
      if (user?.expertStatus === 'REJECTED') return 'rejected';
      return 'writer';
    }
    return 'readOnly';
  });
}
