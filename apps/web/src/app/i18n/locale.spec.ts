import {
  DEFAULT_LOCALE,
  isLocale,
  negotiateFromAcceptLanguage,
  readLocaleCookie,
} from './locale';

describe('locale', () => {
  it('defaults to French', () => {
    expect(DEFAULT_LOCALE).toBe('fr');
  });

  describe('isLocale', () => {
    it('accepts supported locales', () => {
      expect(isLocale('fr')).toBe(true);
      expect(isLocale('en')).toBe(true);
    });

    it('rejects anything else', () => {
      expect(isLocale('es')).toBe(false);
      expect(isLocale('')).toBe(false);
      expect(isLocale(null)).toBe(false);
      expect(isLocale(undefined)).toBe(false);
    });
  });

  describe('readLocaleCookie', () => {
    it('finds the locale among other cookies', () => {
      expect(readLocaleCookie('theme=dark; meetstudent_locale=en; sid=abc')).toBe('en');
    });

    it('tolerates surrounding whitespace', () => {
      expect(readLocaleCookie('  meetstudent_locale = fr  ')).toBe('fr');
    });

    it('returns null when the cookie is absent', () => {
      expect(readLocaleCookie('theme=dark')).toBeNull();
      expect(readLocaleCookie('')).toBeNull();
      expect(readLocaleCookie(null)).toBeNull();
    });

    it('returns null when the stored value is not a supported locale', () => {
      expect(readLocaleCookie('meetstudent_locale=de')).toBeNull();
    });

    it('does not match a cookie whose name merely ends with the same text', () => {
      expect(readLocaleCookie('other_meetstudent_locale=en')).toBeNull();
    });

    it('treats a malformed percent-escape exactly like an absent cookie', () => {
      // Both of these make decodeURIComponent throw a URIError: `%zz` is not
      // hex, and `%E9` is a lone Latin-1 byte that is not valid UTF-8. The
      // cookie is attacker-writable, so this must degrade, never throw.
      expect(readLocaleCookie('meetstudent_locale=%zz')).toBeNull();
      expect(readLocaleCookie('meetstudent_locale=%E9')).toBeNull();
      expect(readLocaleCookie('theme=dark; meetstudent_locale=%; sid=abc')).toBeNull();
    });
  });

  describe('negotiateFromAcceptLanguage', () => {
    it('picks the highest-quality supported language', () => {
      expect(negotiateFromAcceptLanguage('de;q=1.0, en;q=0.8, fr;q=0.9')).toBe('fr');
    });

    it('treats a missing q as 1.0 and keeps document order on ties', () => {
      expect(negotiateFromAcceptLanguage('en, fr')).toBe('en');
    });

    it('matches on the primary subtag', () => {
      expect(negotiateFromAcceptLanguage('fr-SN,fr;q=0.9')).toBe('fr');
      expect(negotiateFromAcceptLanguage('en-GB')).toBe('en');
    });

    it('ignores languages explicitly refused with q=0', () => {
      expect(negotiateFromAcceptLanguage('fr;q=0, en;q=0.5')).toBe('en');
    });

    it('returns null when nothing is supported', () => {
      expect(negotiateFromAcceptLanguage('de, es')).toBeNull();
      expect(negotiateFromAcceptLanguage('')).toBeNull();
      expect(negotiateFromAcceptLanguage(null)).toBeNull();
    });
  });
});
