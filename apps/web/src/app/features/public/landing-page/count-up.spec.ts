import { countUp, FrameScheduler } from './count-up';

class FakeFrames implements FrameScheduler {
  time = 1000;
  private nextId = 1;
  private pending = new Map<number, (time: number) => void>();
  cancelled: number[] = [];

  now = () => this.time;
  request(callback: (time: number) => void): number {
    const id = this.nextId++;
    this.pending.set(id, callback);
    return id;
  }
  cancel(id: number): void {
    this.cancelled.push(id);
    this.pending.delete(id);
  }
  get hasPending(): boolean {
    return this.pending.size > 0;
  }
  /** Moves the clock to `elapsed` ms after the start and runs the pending frame. */
  tickAt(elapsed: number): void {
    this.time = 1000 + elapsed;
    const entries = [...this.pending.entries()];
    this.pending.clear();
    for (const [, cb] of entries) {
      cb(this.time);
    }
  }
}

describe('countUp', () => {
  let frames: FakeFrames;
  let values: number[];
  const run = (target: number, duration = 1000) => countUp(target, duration, (v) => values.push(v), frames);

  beforeEach(() => {
    frames = new FakeFrames();
    values = [];
  });

  it('ends exactly on the target and stops requesting frames', () => {
    run(1234);
    for (let t = 0; t <= 1000; t += 100) {
      frames.tickAt(t);
    }
    expect(values[values.length - 1]).toBe(1234);
    expect(frames.hasPending).toBeFalse();
  });

  it('ends on the target even when the last frame arrives late', () => {
    run(77);
    frames.tickAt(5000);
    expect(values).toEqual([77]);
  });

  it('never decreases', () => {
    run(500);
    for (let t = 0; t <= 1000; t += 16) {
      frames.tickAt(t);
    }
    expect(values.length).toBeGreaterThan(10);
    for (let i = 1; i < values.length; i++) {
      expect(values[i]).toBeGreaterThanOrEqual(values[i - 1]);
    }
  });

  it('emits 0 on the first tick at 0ms and never overshoots', () => {
    run(500);
    frames.tickAt(0);
    expect(values).toEqual([0]);
    for (let t = 16; t <= 1000; t += 16) {
      frames.tickAt(t);
    }
    expect(Math.max(...values)).toBe(500);
  });

  it('does not overshoot when the clock reads before the start', () => {
    run(500);
    frames.tickAt(-50);
    expect(values).toEqual([0]);
  });

  it('eases out: more than half of the target at half of the time', () => {
    run(1000);
    frames.tickAt(500);
    expect(values[0]).toBeGreaterThan(500);
    expect(values[0]).toBeLessThan(1000);
  });

  it('emits 0 once and stops for a target of 0', () => {
    run(0);
    frames.tickAt(0);
    expect(values).toEqual([0]);
    expect(frames.hasPending).toBeFalse();
  });

  it('stops ticking once cancelled and cancels the pending frame', () => {
    const cancel = run(100);
    frames.tickAt(100);
    const count = values.length;
    cancel();
    expect(frames.cancelled.length).toBe(1);
    frames.tickAt(200);
    frames.tickAt(1000);
    expect(values.length).toBe(count);
    expect(frames.hasPending).toBeFalse();
  });

  for (const [label, target] of [['negative', -5], ['NaN', NaN], ['Infinity', Infinity], ['-Infinity', -Infinity]] as const) {
    it(`treats a ${label} target as 0`, () => {
      run(target);
      frames.tickAt(0);
      frames.tickAt(1000);
      expect(values.every((v) => v === 0)).toBeTrue();
      expect(values.length).toBeGreaterThan(0);
    });
  }

  it('jumps straight to the target with a duration of 0', () => {
    run(42, 0);
    frames.tickAt(0);
    expect(values).toEqual([42]);
    expect(frames.hasPending).toBeFalse();
  });
});
