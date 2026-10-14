import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { DestroyRef, Injectable, PLATFORM_ID, inject } from '@angular/core';

/** Browser intent is short lived and consumed by one authenticated business read. */
@Injectable({ providedIn: 'root' })
export class SessionActivityService {
  private intentAt: number | null = null;

  constructor() {
    if (!isPlatformBrowser(inject(PLATFORM_ID))) return;
    const document = inject(DOCUMENT);
    const destroyRef = inject(DestroyRef);
    const interaction = (event: Event): void => {
      if (event.isTrusted) this.intentAt = Date.now();
    };
    for (const type of ['click', 'keydown', 'input']) {
      document.addEventListener(type, interaction, true);
      destroyRef.onDestroy(() => document.removeEventListener(type, interaction, true));
    }
  }

  consume(): boolean {
    const intentAt = this.intentAt;
    this.intentAt = null;
    return intentAt !== null && Date.now() - intentAt <= 1000;
  }
}
