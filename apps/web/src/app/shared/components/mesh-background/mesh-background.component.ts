import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Animated gradient backdrop. Pure CSS: no canvas, no images, nothing to load. */
@Component({
  selector: 'app-mesh-background',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true' },
  styles: [
    `
      :host {
        position: absolute;
        inset: 0;
        z-index: -1;
        overflow: hidden;
        pointer-events: none;
        background:
          radial-gradient(60% 50% at 15% 10%, color-mix(in oklch, var(--mesh-1) 32%, transparent), transparent 70%),
          radial-gradient(50% 45% at 90% 15%, color-mix(in oklch, var(--mesh-2) 28%, transparent), transparent 70%),
          var(--background);
      }
      .orb { position: absolute; border-radius: 9999px; filter: blur(70px); opacity: 0.55; }
      .orb-1 { width: 28rem; height: 28rem; top: -8rem; left: -6rem; background: var(--mesh-1); }
      .orb-2 { width: 24rem; height: 24rem; top: 10%; right: -6rem; background: var(--mesh-2); animation-delay: -6s; }
      .orb-3 { width: 20rem; height: 20rem; bottom: -6rem; left: 35%; background: var(--mesh-3); animation-delay: -12s; }
      @media (max-width: 47.99rem) { .orb { filter: blur(50px); } .orb-3 { display: none; } }
    `,
  ],
  template: `
    <span class="orb orb-1 animate-orb"></span>
    <span class="orb orb-2 animate-orb"></span>
    <span class="orb orb-3 animate-orb"></span>
  `,
})
export class MeshBackgroundComponent {}
