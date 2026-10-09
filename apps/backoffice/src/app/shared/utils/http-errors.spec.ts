import { HttpErrorResponse } from '@angular/common/http';
import { fieldErrorsFrom, messageFrom } from './http-errors';

const err = (status: number, body: unknown = null) => new HttpErrorResponse({ status, error: body });

describe('fieldErrorsFrom', () => {
  it('reads the {field: message} map the API returns on a 400', () => {
    expect(fieldErrorsFrom(err(400, { name: 'must not be blank', 'address.city': 'too long' }))).toEqual({
      name: 'must not be blank',
      'address.city': 'too long',
    });
  });

  it('returns null for other statuses, non-maps and non-string values', () => {
    expect(fieldErrorsFrom(err(500, { name: 'x' }))).toBeNull();
    expect(fieldErrorsFrom(err(400, null))).toBeNull();
    expect(fieldErrorsFrom(err(400, 'text'))).toBeNull();
    expect(fieldErrorsFrom(err(400, { status: 400, message: 'm', n: 3 }))).toBeNull();
    expect(fieldErrorsFrom(new Error('x'))).toBeNull();
  });
});

describe('messageFrom', () => {
  it('maps common statuses to French messages', () => {
    expect(messageFrom(err(0), 'f')).toContain('joindre le serveur');
    expect(messageFrom(err(401), 'f')).toContain('session');
    expect(messageFrom(err(403), 'f')).toContain('droits');
    expect(messageFrom(err(404), 'f')).toContain('introuvable');
  });

  it('falls back for anything else', () => {
    expect(messageFrom(err(500), 'Échec.')).toBe('Échec.');
    expect(messageFrom('boom', 'Échec.')).toBe('Échec.');
  });
});
