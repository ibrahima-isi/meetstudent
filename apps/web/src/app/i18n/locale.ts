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

    const value = decodeCookieValue(pair.slice(separator + 1).trim());
    return isLocale(value) ? value : null;
  }

  return null;
}

/**
 * `decodeURIComponent` throws a `URIError` on a malformed escape — `%zz`, or a
 * lone Latin-1 byte such as `%E9`. The cookie is client-writable, so a bad value
 * must degrade to "nothing remembered" rather than break every render until the
 * user thinks to clear their cookies.
 */
function decodeCookieValue(raw: string): string | null {
  try {
    return decodeURIComponent(raw);
  } catch {
    return null;
  }
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

/**
 * Rewrites a URL so it addresses the same page in another locale:
 * `/fr/schools/7?tab=x#y` → `/en/schools/7?tab=x#y`.
 *
 * The query string and the fragment travel with it — they identify the page as
 * much as the path does, and dropping them would make switching language a
 * silent way to lose your place. A first segment that is not a locale is a path
 * that never had a prefix, so it gains one rather than losing its first
 * directory.
 */
export function urlInLocale(url: string, locale: Locale): string {
  const [first = '', ...rest] = url.split('/').slice(1);
  const head = first.split(/[?#]/)[0];
  const suffix = isLocale(head)
    ? `${first.slice(head.length)}${rest.length > 0 ? `/${rest.join('/')}` : ''}`
    : url === '/'
      ? ''
      : url;

  return `/${locale}${suffix}`;
}
