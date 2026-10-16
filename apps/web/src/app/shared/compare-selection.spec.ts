import { byRatingThenNameThenId, parseIdList } from './compare-selection';

describe('parseIdList', () => {
  it('keeps whole positive ids in order, without repeats', () => {
    expect(parseIdList('3, 1,3,2')).toEqual([3, 1, 2]);
  });

  it('ignores unreadable, zero and negative parts', () => {
    expect(parseIdList('1,x,-2,0,1.5,,2')).toEqual([1, 2]);
  });

  it('ignores ids the API cannot read (above 2^31-1)', () => {
    expect(parseIdList('1,2147483647,2147483648,99999999999999999999')).toEqual([1, 2147483647]);
  });

  it('answers an empty list for nothing', () => {
    expect(parseIdList(null)).toEqual([]);
    expect(parseIdList(undefined)).toEqual([]);
    expect(parseIdList('')).toEqual([]);
  });

  it('cuts to the limit when one is given, and not otherwise', () => {
    expect(parseIdList('1,2,3,4', 3)).toEqual([1, 2, 3]);
    expect(parseIdList('1,2,3,4')).toEqual([1, 2, 3, 4]);
  });
});

describe('byRatingThenNameThenId', () => {
  type Item = { id?: number; name: string; rate: number | null };
  const rank = (items: Item[]) =>
    [...items].sort(byRatingThenNameThenId<Item>((i) => i.rate, 'en')).map((i) => `${i.name}${i.id ?? ''}`);

  it('puts the best rated first and the unrated last', () => {
    expect(rank([
      { id: 1, name: 'A', rate: null },
      { id: 2, name: 'B', rate: 3 },
      { id: 3, name: 'C', rate: 4 },
    ])).toEqual(['C3', 'B2', 'A1']);
  });

  it('breaks ties by name, then by id', () => {
    expect(rank([
      { id: 5, name: 'Beta', rate: 3 },
      { id: 4, name: 'Beta', rate: 3 },
      { id: 6, name: 'Alpha', rate: 3 },
    ])).toEqual(['Alpha6', 'Beta4', 'Beta5']);
  });

  it('ranks an item without a name (legacy rows) first among its ties, without throwing', () => {
    const items = [
      { id: 2, name: 'B', rate: null },
      { id: 1, name: null as unknown as string, rate: null },
    ];
    expect(() => rank(items)).not.toThrow();
    expect(rank(items)).toEqual(['null1', 'B2']);
  });

  it('treats a missing id as 0', () => {
    expect(rank([{ id: 1, name: 'A', rate: null }, { name: 'A', rate: null }])).toEqual(['A', 'A1']);
  });
});
