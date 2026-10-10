export interface FrameScheduler {
  now(): number;
  request(callback: (time: number) => void): number;
  cancel(id: number): void;
}

export const browserFrames: FrameScheduler = {
  now: () => performance.now(),
  request: (cb) => requestAnimationFrame(cb),
  cancel: (id) => cancelAnimationFrame(id),
};

const easeOutCubic = (t: number): number => 1 - Math.pow(1 - t, 3);

/** Counts from 0 to `target`; returns a function that stops it. Never overshoots. */
export function countUp(
  target: number,
  durationMs: number,
  onTick: (value: number) => void,
  frames: FrameScheduler = browserFrames,
): () => void {
  const end = Number.isFinite(target) && target > 0 ? Math.round(target) : 0;
  const start = frames.now();
  let handle = 0;
  let stopped = false;

  const step = (time: number): void => {
    if (stopped) {
      return;
    }
    const progress = durationMs <= 0 || end === 0 ? 1 : Math.min(Math.max((time - start) / durationMs, 0), 1);
    onTick(progress >= 1 ? end : Math.round(end * easeOutCubic(progress)));
    if (progress < 1) {
      handle = frames.request(step);
    }
  };

  handle = frames.request(step);
  return () => {
    stopped = true;
    frames.cancel(handle);
  };
}
