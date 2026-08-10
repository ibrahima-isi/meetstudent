/**
 * French is the source language: the UI was authored in French, so `fr` is both
 * the default and the fallback. English is a translation.
 */
export const SUPPORTED_LOCALES = ['fr', 'en'] as const;

export type Locale = (typeof SUPPORTED_LOCALES)[number];

export const DEFAULT_LOCALE: Locale = 'fr';

/** Written only on an explicit user choice, never on a negotiated guess. */
export const LOCALE_COOKIE = 'meetstudent_locale';

export function isLocale(value: string | null | undefined): value is Locale {
  return value != null && (SUPPORTED_LOCALES as readonly string[]).includes(value);
}

/**
 * Reads the remembered locale out of a raw `Cookie` header (server) or
 * `document.cookie` (browser) — both use the same `a=1; b=2` shape.
 */
export function readLocaleCookie(cookieHeader: string | null | undefined): Locale | null {
  if (!cookieHeader) {
    return null;
  }

  for (const pair of cookieHeader.split(';')) {
    const separator = pair.indexOf('=');
    if (separator === -1) {
      continue;
    }

    if (pair.slice(0, separator).trim() !== LOCALE_COOKIE) {
      continue;
    }

    const value = decodeURIComponent(pair.slice(separator + 1).trim());
    return isLocale(value) ? value : null;
  }

  return null;
}

/**
 * Picks the best supported locale from an `Accept-Language` header, honouring
 * quality values. Returns null when the client asked for nothing we serve —
 * the caller decides the fallback.
 */
export function negotiateFromAcceptLanguage(
  header: string | null | undefined,
): Locale | null {
  if (!header) {
    return null;
  }

  const ranked = header
    .split(',')
    .map((part) => {
      const [tag, ...params] = part.trim().split(';');
      const quality = params
        .map((param) => param.trim())
        .find((param) => param.startsWith('q='));
      const parsed = quality ? Number.parseFloat(quality.slice(2)) : 1;

      return {
        tag: tag.trim().toLowerCase(),
        // A malformed q is treated as a refusal rather than a preference.
        quality: Number.isNaN(parsed) ? 0 : parsed,
      };
    })
    .filter((entry) => entry.tag !== '' && entry.quality > 0)
    // Array.prototype.sort is stable, so equal qualities keep header order.
    .sort((a, b) => b.quality - a.quality);

  for (const { tag } of ranked) {
    const primary = tag.split('-')[0];
    if (isLocale(primary)) {
      return primary;
    }
  }

  return null;
}
