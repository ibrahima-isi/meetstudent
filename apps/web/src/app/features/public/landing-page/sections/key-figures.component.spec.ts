import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom, Observable, of, throwError } from 'rxjs';
import { translocoOptions } from '@i18n/transloco.config';
import { LocaleService } from '@services/locale.service';
import { ProgramService } from '@services/program.service';
import { SchoolService } from '@services/school.service';
import { KeyFiguresComponent } from './key-figures.component';

describe('KeyFiguresComponent', () => {
  let fixture: ComponentFixture<KeyFiguresComponent>;
  let getSchools: jasmine.Spy;
  let getPrograms: jasmine.Spy;
  let observers: FakeObserver[];
  let originalObserver: typeof IntersectionObserver | undefined;
  let reducedMotion: boolean;
  let hostTop: number;

  class FakeObserver {
    observed: Element[] = [];
    disconnected = false;
    constructor(public callback: IntersectionObserverCallback) {
      observers.push(this);
    }
    observe(el: Element): void {
      this.observed.push(el);
    }
    disconnect(): void {
      this.disconnected = true;
    }
    unobserve(): void {}
    takeRecords(): IntersectionObserverEntry[] {
      return [];
    }
    fire(isIntersecting = true): void {
      this.callback([{ isIntersecting } as IntersectionObserverEntry], this as unknown as IntersectionObserver);
    }
  }

  /** The component's own observer (the reveal directive creates one on its section). */
  const own = () => observers.find((o) => o.observed.includes(fixture.nativeElement))!;
  const root = () => fixture.nativeElement as HTMLElement;
  const text = (selector: string) => Array.from(root().querySelectorAll(selector)).map((e) => e.textContent?.trim());
  const fmt = (n: number) => n.toLocaleString('fr');

  async function create(schools: Observable<unknown>, programs: Observable<unknown>) {
    getSchools.and.returnValue(schools);
    getPrograms.and.returnValue(programs);
    fixture = TestBed.createComponent(KeyFiguresComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }
  const page = (totalElements?: number) => of({ content: [], totalElements });

  beforeEach(async () => {
    observers = [];
    reducedMotion = false;
    hostTop = 5000; // below the fold by default
    originalObserver = window.IntersectionObserver;
    window.IntersectionObserver = FakeObserver as unknown as typeof IntersectionObserver;
    const nativeMatchMedia = window.matchMedia.bind(window);
    spyOn(window, 'matchMedia').and.callFake((query: string) =>
      query.includes('prefers-reduced-motion') ? ({ matches: reducedMotion } as MediaQueryList) : nativeMatchMedia(query),
    );
    spyOn(HTMLElement.prototype, 'getBoundingClientRect').and.callFake(
      () => ({ top: hostTop, bottom: hostTop + 100, left: 0, right: 0, width: 100, height: 100, x: 0, y: hostTop }) as DOMRect,
    );

    getSchools = jasmine.createSpy('getSchools');
    getPrograms = jasmine.createSpy('getPrograms');
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideTransloco(translocoOptions),
        { provide: LocaleService, useValue: { active: signal('fr') } },
        { provide: SchoolService, useValue: { getSchools } },
        { provide: ProgramService, useValue: { getPrograms } },
      ],
    });
    await firstValueFrom(TestBed.inject(TranslocoService).load('fr'));
  });

  afterEach(() => {
    window.IntersectionObserver = originalObserver as typeof IntersectionObserver;
  });

  it('renders the final numbers in .sr-only text, formatted for the active locale', async () => {
    await create(page(1234), page(34));
    expect(text('.sr-only').filter((t) => t !== 'La plateforme en chiffres')).toEqual([fmt(1234), fmt(34)]);
  });

  it('marks the animated numbers aria-hidden', async () => {
    await create(page(12), page(34));
    const hidden = Array.from(root().querySelectorAll('[aria-hidden="true"]'));
    expect(hidden.length).toBe(2);
    expect(hidden.every((e) => e.closest('dd') !== null)).toBeTrue();
  });

  it('requests a page of size 1, not the catalogue', async () => {
    await create(page(12), page(34));
    expect(getSchools).toHaveBeenCalledWith(0, 1);
    expect(getPrograms).toHaveBeenCalledWith(0, 1);
  });

  it('hides the section when the schools request fails', async () => {
    await create(throwError(() => new Error('x')), page(34));
    expect(root().querySelector('section')).toBeNull();
  });

  it('hides the section when the programs request fails', async () => {
    await create(page(12), throwError(() => new Error('x')));
    expect(root().querySelector('section')).toBeNull();
  });

  it('hides the section when both totals are 0', async () => {
    await create(page(0), page(0));
    expect(root().querySelector('section')).toBeNull();
  });

  it('treats a missing totalElements as 0', async () => {
    await create(page(undefined), page(undefined));
    expect(root().querySelector('section')).toBeNull();
  });

  it('shows the section when only one total is non-zero', async () => {
    await create(page(0), page(9));
    expect(root().querySelector('section')).not.toBeNull();
    expect(text('.sr-only').filter((t) => t !== 'La plateforme en chiffres')).toEqual([fmt(0), fmt(9)]);
  });

  describe('visible numbers', () => {
    const visible = () => text('dd [aria-hidden="true"]');

    it('equal the final ones without IntersectionObserver', async () => {
      (window as { IntersectionObserver?: unknown }).IntersectionObserver = undefined;
      await create(page(1234), page(34));
      expect(visible()).toEqual([fmt(1234), fmt(34)]);
      expect(own()).toBeUndefined();
    });

    it('equal the final ones under prefers-reduced-motion', async () => {
      reducedMotion = true;
      await create(page(1234), page(34));
      expect(visible()).toEqual([fmt(1234), fmt(34)]);
      expect(own()).toBeUndefined();
    });

    it('equal the final ones when the section is already on screen', async () => {
      hostTop = 10;
      await create(page(1234), page(34));
      expect(visible()).toEqual([fmt(1234), fmt(34)]);
      expect(own()).toBeUndefined();
    });

    it('start at 0 below the fold, with the final numbers still in the sr-only text', async () => {
      await create(page(1234), page(34));
      expect(own()).toBeDefined();
      expect(visible()).toEqual([fmt(0), fmt(0)]);
      expect(text('.sr-only').filter((t) => t !== 'La plateforme en chiffres')).toEqual([fmt(1234), fmt(34)]);
    });

    it('are never zero when the data arrives after the observer is set up (no flash for animation-less visitors)', async () => {
      (window as { IntersectionObserver?: unknown }).IntersectionObserver = undefined;
      await create(page(7), page(8));
      expect(visible()).toEqual([fmt(7), fmt(8)]);
    });

    it('count up to the final numbers once the section intersects', async () => {
      const frames: ((t: number) => void)[] = [];
      spyOn(window, 'requestAnimationFrame').and.callFake((cb: FrameRequestCallback) => frames.push(cb));
      let now = 0;
      spyOn(performance, 'now').and.callFake(() => now);
      await create(page(1234), page(34));
      own().fire(true);
      now = 5000;
      frames.splice(0).forEach((cb) => cb(now));
      fixture.detectChanges();
      expect(visible()).toEqual([fmt(1234), fmt(34)]);
      expect(own().disconnected).toBeTrue();
    });

    it('ignores a non-intersecting notification', async () => {
      await create(page(5), page(6));
      own().fire(false);
      fixture.detectChanges();
      expect(visible()).toEqual([fmt(0), fmt(0)]);
      expect(own().disconnected).toBeFalse();
    });
  });

  describe('teardown', () => {
    it('disconnects the observer on destroy', async () => {
      await create(page(5), page(6));
      expect(own().disconnected).toBeFalse();
      fixture.destroy();
      expect(own().disconnected).toBeTrue();
    });

    it('cancels a running count on destroy', async () => {
      const raf = spyOn(window, 'requestAnimationFrame').and.callFake(() => 987654);
      const cancel = spyOn(window, 'cancelAnimationFrame');
      await create(page(500), page(600));
      raf.calls.reset();
      own().fire(true);
      expect(raf).toHaveBeenCalledTimes(2);
      cancel.calls.reset();
      fixture.destroy();
      expect(cancel).toHaveBeenCalledWith(987654);
    });
  });
});
