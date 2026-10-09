import { roundRating } from './format-rating';

describe('roundRating', () => {
  it('keeps one decimal', () => {
    expect(roundRating(5.333333333333333)).toBe(5.3);
    expect(roundRating(4.26)).toBe(4.3);
  });

  it('leaves whole and one-decimal values alone', () => {
    expect(roundRating(4)).toBe(4);
    expect(roundRating(3.5)).toBe(3.5);
  });

  it('reads a missing rating as 0', () => {
    expect(roundRating(undefined)).toBe(0);
    expect(roundRating(null)).toBe(0);
  });
});
