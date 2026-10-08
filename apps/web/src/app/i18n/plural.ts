/**
 * The key for a count, following the language's own plural rule: French puts
 * zero with one ("0 établissement trouvé"), English puts it with many
 * ("0 schools found"). Both languages have only these two forms, so a base key
 * carries exactly `.one` and `.other`.
 */
export function pluralKey(base: string, count: number, locale: string): string {
  const form = new Intl.PluralRules(locale).select(count) === 'one' ? 'one' : 'other';
  return `${base}.${form}`;
}
