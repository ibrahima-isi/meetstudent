import { FormControl, FormGroup } from '@angular/forms';
import { applyFieldErrors, notBlank } from './form-errors';

describe('form-errors', () => {
  const group = () =>
    new FormGroup({ name: new FormControl('', { nonNullable: true }), code: new FormControl('', { nonNullable: true }) });

  it('notBlank rejects whitespace only', () => {
    expect(notBlank(new FormControl('   ', { nonNullable: true }))).toEqual({ required: true });
    expect(notBlank(new FormControl(' a ', { nonNullable: true }))).toBeNull();
  });

  it('puts each server message on the matching control and touches it', () => {
    const g = group();
    const left = applyFieldErrors(g, { name: 'Trop long', code: 'Déjà utilisé' });
    expect(g.controls.name.errors).toEqual({ server: 'Trop long' });
    expect(g.controls.name.touched).toBeTrue();
    expect(g.controls.code.errors).toEqual({ server: 'Déjà utilisé' });
    expect(left).toBe('');
  });

  it('follows aliases and returns the messages that have no control', () => {
    const g = group();
    const left = applyFieldErrors(g, { 'program.name': 'Nom invalide', other: 'Autre', more: 'Encore' }, { 'program.name': 'name' });
    expect(g.controls.name.errors).toEqual({ server: 'Nom invalide' });
    expect(left).toBe('Autre Encore');
  });

  it('a server error disappears once the user edits the field', () => {
    const g = group();
    applyFieldErrors(g, { name: 'Trop long' });
    g.controls.name.setValue('x');
    expect(g.controls.name.errors).toBeNull();
  });
});
