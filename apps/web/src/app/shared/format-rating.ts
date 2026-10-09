/** An average as shown to people: one decimal at most, 0 when there is none yet. */
export const roundRating = (value: number | null | undefined): number =>
  Math.round((value ?? 0) * 10) / 10;
