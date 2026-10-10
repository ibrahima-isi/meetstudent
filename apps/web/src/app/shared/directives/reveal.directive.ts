import { afterNextRender, DestroyRef, Directive, ElementRef, inject } from '@angular/core';

/**
 * Fades an element in the first time it scrolls into view.
 *
 * Content is never hidden on the server, without IntersectionObserver, under
 * `prefers-reduced-motion`, or when it is already on screen — hiding only ever
 * applies to something the visitor cannot see yet, so there is no flash and no
 * content that depends on JavaScript to appear.
 */
@Directive({ selector: '[appReveal]', host: { class: 'reveal' } })
export class RevealDirective {
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    afterNextRender(() => this.observe());
  }

  private observe(): void {
    const win = this.element.ownerDocument.defaultView;
    if (!win || typeof win.IntersectionObserver === 'undefined') {
      return;
    }
    if (win.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
      return;
    }
    if (this.element.getBoundingClientRect().top < win.innerHeight) {
      return;
    }

    this.element.setAttribute('data-reveal', 'pending');
    const observer = new win.IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          this.element.setAttribute('data-reveal', 'done');
          observer.disconnect();
        }
      },
      { threshold: 0.12 },
    );
    observer.observe(this.element);
    this.destroyRef.onDestroy(() => observer.disconnect());
  }
}
