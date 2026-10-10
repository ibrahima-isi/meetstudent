import { Component, provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RevealDirective } from './reveal.directive';

@Component({ imports: [RevealDirective], template: `<div appReveal id="target">x</div>` })
class HostComponent {}

class FakeObserver {
  static last: FakeObserver | null = null;
  disconnected = false;
  constructor(public callback: (entries: { isIntersecting: boolean }[]) => void) {
    FakeObserver.last = this;
  }
  observe(): void {}
  disconnect(): void {
    this.disconnected = true;
  }
}

describe('RevealDirective', () => {
  let original: typeof IntersectionObserver;

  beforeEach(() => {
    original = window.IntersectionObserver;
    FakeObserver.last = null;
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
  });

  afterEach(() => {
    window.IntersectionObserver = original;
  });

  async function render(top: number): Promise<HTMLElement> {
    const fixture: ComponentFixture<HostComponent> = TestBed.createComponent(HostComponent);
    const el = fixture.nativeElement.querySelector('#target') as HTMLElement;
    spyOn(el, 'getBoundingClientRect').and.returnValue({ top } as DOMRect);
    fixture.detectChanges();
    await fixture.whenStable();
    return el;
  }

  function useFakeObserver(): void {
    window.IntersectionObserver = FakeObserver as unknown as typeof IntersectionObserver;
  }

  it('adds the reveal class', async () => {
    useFakeObserver();
    const el = await render(5000);
    expect(el.classList.contains('reveal')).toBeTrue();
  });

  it('hides an element below the fold until it intersects, then marks it done and stops watching', async () => {
    useFakeObserver();
    const el = await render(5000);
    expect(el.getAttribute('data-reveal')).toBe('pending');

    FakeObserver.last!.callback([{ isIntersecting: true }]);

    expect(el.getAttribute('data-reveal')).toBe('done');
    expect(FakeObserver.last!.disconnected).toBeTrue();
  });

  it('keeps waiting while the entry is not intersecting', async () => {
    useFakeObserver();
    const el = await render(5000);
    FakeObserver.last!.callback([{ isIntersecting: false }]);
    expect(el.getAttribute('data-reveal')).toBe('pending');
  });

  it('never hides an element that is already on screen (no flash)', async () => {
    useFakeObserver();
    const el = await render(10);
    expect(el.getAttribute('data-reveal')).toBeNull();
  });

  it('leaves content visible when IntersectionObserver is unavailable', async () => {
    (window as unknown as { IntersectionObserver: unknown }).IntersectionObserver = undefined;
    const el = await render(5000);
    expect(el.getAttribute('data-reveal')).toBeNull();
  });

  it('leaves content visible for visitors who prefer reduced motion', async () => {
    useFakeObserver();
    spyOn(window, 'matchMedia').and.returnValue({ matches: true } as MediaQueryList);
    const el = await render(5000);
    expect(el.getAttribute('data-reveal')).toBeNull();
  });
});
