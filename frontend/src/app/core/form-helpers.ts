import { AbstractControl, ValidatorFn } from '@angular/forms';

export function normalisePhone(value: string): string {
  return value.replace(/[\s\-()]/g, '');
}

export const phoneValidator: ValidatorFn = (c) => {
  const v = normalisePhone(String(c.value ?? ''));
  return !v || /^\+?[0-9]{9,15}$/.test(v) ? null : { phone: true };
};

export function errorMessage(c: AbstractControl, force = false): string | null {
  const e = c.errors;
  if (!e || !(c.touched || force)) return null;
  if (e['server']) return String(e['server']);
  if (e['required']) return 'This field is required.';
  if (e['email']) return 'Enter a valid email address.';
  if (e['phone']) return 'Enter 9 to 15 digits, with an optional leading +.';
  if (e['maxlength']) return `Please use at most ${e['maxlength'].requiredLength} characters.`;
  return 'This value is not valid.';
}
