/**
 * Characters that draw nothing or reorder text: control characters (Cc), format characters (Cf: bidi controls,
 * zero-width space, word joiner, soft hyphen, BOM, tag characters) except the zero-width non-joiner and joiner
 * (U+200C/D: Persian spelling and emoji sequences need them), line and paragraph separators, variation selectors
 * (U+FE00-FE0F, U+E0100-E01EF), the Khmer inherent vowels U+17B4/5, and the letters or marks that render blank
 * (Hangul and Braille fillers, Mongolian vowel separator, combining grapheme joiner).
 */
const INVISIBLE =
  /(?![‌‍])[\p{Cc}\p{Cf}\p{Zl}\p{Zp}︀-️\u{E0100}-\u{E01EF}឴឵ㅤ⠀ᅟᅠﾠ᠎͏]/gu;
const EXOTIC_SPACE = /[\p{Zs}]+/gu;
/** More than two stacked combining marks only serve to smear text over its neighbours. */
const STACKED_MARKS = /(\p{Mn}{2})\p{Mn}+/gu;

/**
 * A first name as shown in the forum. It comes from an account, and older accounts predate the server
 * check that refuses hidden characters, so they are stripped here before display. Letters of every
 * script, accents and apostrophes stay; HTML is not touched (the template interpolates, it never binds HTML).
 * Empty when nothing visible is left.
 */
export function cleanDisplayName(name: string | null | undefined): string {
  if (!name) return '';
  return name.replace(INVISIBLE, '').replace(STACKED_MARKS, '$1').replace(EXOTIC_SPACE, ' ').trim();
}

/** A thread title on one line (for `document.title`): line breaks and tabs become spaces, then the same cleaning. */
export function cleanTitle(title: string | null | undefined): string {
  return cleanDisplayName((title ?? '').replace(/[\t\r\n]+/g, ' '));
}
