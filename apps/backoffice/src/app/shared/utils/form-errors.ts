import { AbstractControl, FormGroup, ValidationErrors } from '@angular/forms';

/** `Validators.required` lets whitespace through; a name must not be blank. */
export const notBlank = (control: AbstractControl<string>): ValidationErrors | null =>
  control.value.trim() ? null : { required: true };

/**
 * Puts each server field message on the control of the same name (or of its alias) and
 * touches it. Returns the messages that have no control, joined, for an alert.
 */
export function applyFieldErrors(
  form: FormGroup,
  fields: Record<string, string>,
  aliases: Record<string, string> = {},
): string {
  const unmapped: string[] = [];
  for (const [key, message] of Object.entries(fields)) {
    const control = form.get(aliases[key] ?? key);
    if (control) {
      control.setErrors({ server: message });
      control.markAsTouched();
    } else {
      unmapped.push(message);
    }
  }
  return unmapped.join(' ');
}
