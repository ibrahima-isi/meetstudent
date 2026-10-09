import { HttpErrorResponse } from '@angular/common/http';

/**
 * The API answers a failed bean validation with a flat `{ "field.path": "message" }`
 * map and status 400. Returns that map, or null for any other error.
 */
export function fieldErrorsFrom(err: unknown): Record<string, string> | null {
  if (!(err instanceof HttpErrorResponse) || err.status !== 400) return null;
  const body: unknown = err.error;
  if (!body || typeof body !== 'object' || Array.isArray(body)) return null;
  const entries = Object.entries(body);
  if (!entries.length || !entries.every(([, v]) => typeof v === 'string')) return null;
  return Object.fromEntries(entries) as Record<string, string>;
}

/** French user-facing message for a failed request; `fallback` covers the rest. */
export function messageFrom(err: unknown, fallback: string): string {
  switch (err instanceof HttpErrorResponse ? err.status : -1) {
    case 0:
      return 'Impossible de joindre le serveur. Réessayez plus tard.';
    case 401:
      return 'Votre session a expiré. Reconnectez-vous.';
    case 403:
      return "Vous n'avez pas les droits nécessaires pour cette action.";
    case 404:
      return 'Élément introuvable : il a peut-être déjà été supprimé.';
    default:
      return fallback;
  }
}
