/** Pointer this close to the viewport top (px), or a page this close to its top, always shows the bar. */
export const REVEAL_ZONE_PX = 80;
/** Scroll movement below this (px) is jitter, not intent. */
export const SCROLL_DELTA_PX = 8;

export interface DockState {
  readonly scrollY: number;
  readonly previousScrollY: number;
  /** Last known pointer position, null before any mouse movement and on touch devices. */
  readonly pointerY: number | null;
  readonly focusWithin: boolean;
  readonly menuOpen: boolean;
  /** The current answer, kept when the movement is too small to decide. */
  readonly visible: boolean;
}

/** Whether the floating bar should be shown after an input changed. Pure, so every rule is a one-line test. */
export function nextDockVisible(state: DockState): boolean {
  if (state.menuOpen || state.focusWithin) {
    return true;
  }
  if (state.scrollY <= REVEAL_ZONE_PX) {
    return true;
  }
  if (state.pointerY !== null && state.pointerY <= REVEAL_ZONE_PX) {
    return true;
  }

  const delta = state.scrollY - state.previousScrollY;
  if (delta > SCROLL_DELTA_PX) {
    return false;
  }
  if (delta < -SCROLL_DELTA_PX) {
    return true;
  }
  return state.visible;
}
