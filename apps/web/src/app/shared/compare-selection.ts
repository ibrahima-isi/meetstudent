/** Shared by the schools and programs comparisons. */

/** Positive whole ids from a comma-separated list, without repeats; at most `limit` when given. */
export function parseIdList(raw: string | null | undefined, limit?: number): number[] {
  const ids = (raw ?? '')
    .split(',')
    .map((part) => part.trim())
    .filter((part) => /^\d+$/.test(part))
    .map(Number)
    .filter((id) => id > 0);
  const unique = [...new Set(ids)];
  return limit === undefined ? unique : unique.slice(0, limit);
}

/** Best rated first, unrated last; ties by name (in the reader's language), then by id. */
export function byRatingThenNameThenId<T extends { id?: number; name: string }>(
  rateOf: (item: T) => number | null,
  locale: string,
): (a: T, b: T) => number {
  return (a, b) => {
    const rateA = rateOf(a);
    const rateB = rateOf(b);
    if (rateA !== rateB) {
      if (rateA === null) return 1;
      if (rateB === null) return -1;
      return rateB - rateA;
    }
    return a.name.localeCompare(b.name, locale) || (a.id ?? 0) - (b.id ?? 0);
  };
}
