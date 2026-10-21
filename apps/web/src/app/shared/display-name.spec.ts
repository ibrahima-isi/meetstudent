import { cleanDisplayName, cleanTitle } from './display-name';

describe('cleanDisplayName', () => {
  it('keeps plain names, accents, CJK and apostrophes as they are', () => {
    for (const name of ['Awa', 'Aïssatou', "N'Diaye", 'Jean-Pierre', 'José', 'Zoë', '山田', '김민수', 'Ἀλέξανδρος', 'محمد']) {
      expect(cleanDisplayName(name)).withContext(name).toBe(name);
    }
  });

  it('removes control characters, tabs and line breaks', () => {
    expect(cleanDisplayName('Aw\u0000a\u0007')).toBe('Awa');
    expect(cleanDisplayName('Awa\u007F\u0085')).toBe('Awa');
    expect(cleanDisplayName('Awa\nFall\tX\r')).toBe('AwaFallX');
  });

  it('removes bidirectional controls, which can reorder the text around a name', () => {
    expect(cleanDisplayName('‮awa‬')).toBe('awa');
    expect(cleanDisplayName('⁦Awa⁩‎‏؜')).toBe('Awa');
  });

  it('removes zero-width space, word joiner, soft hyphen and the byte-order mark', () => {
    expect(cleanDisplayName('A\u200Bw\u2060a\uFEFF\u00AD')).toBe('Awa');
  });

  it('keeps the zero-width non-joiner and joiner: Persian needs the first, emoji sequences the second', () => {
    expect(cleanDisplayName('می\u200Cخواهم')).toBe('می\u200Cخواهم');
    expect(cleanDisplayName('👩\u200D💻')).toBe('👩\u200D💻');
    expect(cleanDisplayName('Aw\u200C\u200Da')).toBe('Aw\u200C\u200Da');
  });

  it('removes variation selectors, including the supplementary ones', () => {
    expect(cleanDisplayName('A\uFE0Fw\uFE00a\u{E0100}\u{E01EF}')).toBe('Awa');
  });

  it('removes the Khmer inherent vowels and the combining grapheme joiner', () => {
    expect(cleanDisplayName('A\u17B4w\u17B5a\u034F')).toBe('Awa');
  });

  it('limits stacked combining marks to two in a row', () => {
    expect(cleanDisplayName('a\u0301\u0302\u0303\u0304\u0305b')).toBe('a\u0301\u0302b');
    expect(cleanDisplayName('e\u0301\u0302x')).toBe('e\u0301\u0302x');
  });

  it('removes the invisible fillers that render as blank letters', () => {
    expect(cleanDisplayName('ㅤAwa⠀ᅟᅠﾠ᠎')).toBe('Awa');
    expect(cleanDisplayName('Awa͏')).toBe('Awa');
  });

  it('removes line and paragraph separators and invisible tag characters', () => {
    expect(cleanDisplayName('Awa  ')).toBe('Awa');
    expect(cleanDisplayName('A\u{E0041}wa')).toBe('Awa');
  });

  it('turns exotic spaces into one plain space and trims the ends', () => {
    expect(cleanDisplayName('  Awa   Fall　')).toBe('Awa Fall');
  });

  it('answers an empty string when nothing visible is left, or when there is no name', () => {
    expect(cleanDisplayName('​ㅤ‮')).toBe('');
    expect(cleanDisplayName(undefined)).toBe('');
    expect(cleanDisplayName(null)).toBe('');
  });

  it('leaves markup characters alone: escaping is the template interpolation job', () => {
    expect(cleanDisplayName('<img src=x onerror=alert(1)>')).toBe('<img src=x onerror=alert(1)>');
  });

  it('answers empty when only removed marks were left', () => {
    expect(cleanDisplayName('\uFE0F\u17B4\u034F')).toBe('');
  });
});

describe('cleanTitle', () => {
  it('turns line breaks and tabs into spaces instead of gluing the words, then cleans like a name', () => {
    expect(cleanTitle('Which\nmaster\t in\r\nDakar?')).toBe('Which master in Dakar?');
    expect(cleanTitle('\u202Ehello\u202C')).toBe('hello');
  });

  it('leaves markup alone', () => {
    expect(cleanTitle('<img src=x onerror=1>')).toBe('<img src=x onerror=1>');
  });
});
