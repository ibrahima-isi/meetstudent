import { DockState, nextDockVisible, REVEAL_ZONE_PX, SCROLL_DELTA_PX } from './dock-visibility';

const base: DockState = {
  scrollY: 600,
  previousScrollY: 600,
  pointerY: null,
  focusWithin: false,
  menuOpen: false,
  visible: true,
};
const at = (patch: Partial<DockState>): boolean => nextDockVisible({ ...base, ...patch });

describe('nextDockVisible', () => {
  it('hides when the page scrolls down past the threshold', () => {
    expect(at({ previousScrollY: 600, scrollY: 600 + SCROLL_DELTA_PX + 1 })).toBeFalse();
  });

  it('returns when the page scrolls up past the threshold', () => {
    expect(at({ visible: false, previousScrollY: 900, scrollY: 900 - SCROLL_DELTA_PX - 1 })).toBeTrue();
  });

  it('ignores jitter smaller than the threshold in either direction', () => {
    expect(at({ visible: true, scrollY: 600 + SCROLL_DELTA_PX })).toBeTrue();
    expect(at({ visible: false, scrollY: 600 - SCROLL_DELTA_PX })).toBeFalse();
  });

  it('is always visible near the top of the page, including iOS overscroll', () => {
    expect(at({ visible: false, previousScrollY: 300, scrollY: REVEAL_ZONE_PX })).toBeTrue();
    expect(at({ visible: false, previousScrollY: 5, scrollY: -30 })).toBeTrue();
  });

  it('reappears when the pointer reaches the top edge, even while scrolling down', () => {
    expect(at({ visible: false, pointerY: REVEAL_ZONE_PX, previousScrollY: 600, scrollY: 700 })).toBeTrue();
  });

  it('does not reappear for a pointer just below the zone', () => {
    expect(at({ visible: false, pointerY: REVEAL_ZONE_PX + 1 })).toBeFalse();
  });

  it('stays visible while keyboard focus is inside, whatever the scroll', () => {
    expect(at({ focusWithin: true, previousScrollY: 600, scrollY: 900 })).toBeTrue();
  });

  it('stays visible while the mobile menu is open', () => {
    expect(at({ menuOpen: true, previousScrollY: 600, scrollY: 900 })).toBeTrue();
  });
});
