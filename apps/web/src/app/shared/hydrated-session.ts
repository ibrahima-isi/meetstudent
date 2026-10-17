import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { afterNextRender, computed, inject, PLATFORM_ID, signal, Signal } from '@angular/core';
import { TokenService } from '@services/token.service';

export interface HydratedSession {
  /** False until the first client render is done on a server-rendered document. */
  readonly hydrated: Signal<boolean>;
  /** True only once `hydrated` is, so the markup always matches what the server rendered first. */
  readonly signedIn: Signal<boolean>;
}

/**
 * The session lives in localStorage, which the server cannot read: the server always
 * renders the anonymous markup. Reading the token before the first client render would
 * produce different markup and break hydration, so `hydrated` flips after it.
 *
 * Exception: a pure client bootstrap (RenderMode.Client routes) has no server markup to
 * match. Angular stamps `ng-server-context` on server-rendered documents only, so in the
 * browser without it the session can be read at once.
 *
 * Call it in an injection context (a field initialiser or a constructor).
 */
export function injectHydratedSession(): HydratedSession {
  const token = inject(TokenService);
  const hydrated = signal(
    isPlatformBrowser(inject(PLATFORM_ID)) && !inject(DOCUMENT).querySelector('[ng-server-context]'),
  );
  afterNextRender(() => hydrated.set(true));
  return { hydrated, signedIn: computed(() => hydrated() && token.isAuthenticated()) };
}
