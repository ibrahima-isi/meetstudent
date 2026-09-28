import { pluralKey } from './plural';

describe('pluralKey', () => {
  it('picks the singular for one in both languages', () => {
    expect(pluralKey('schools.found', 1, 'fr')).toBe('schools.found.one');
    expect(pluralKey('schools.found', 1, 'en')).toBe('schools.found.one');
  });

  it('picks the plural for several in both languages', () => {
    expect(pluralKey('schools.found', 3, 'fr')).toBe('schools.found.other');
    expect(pluralKey('schools.found', 3, 'en')).toBe('schools.found.other');
  });

  // The rule that differs, and the reason this is not `count === 1`.
  it('treats zero as singular in French and plural in English', () => {
    expect(pluralKey('schools.found', 0, 'fr')).toBe('schools.found.one');
    expect(pluralKey('schools.found', 0, 'en')).toBe('schools.found.other');
  });
});
