import { FormGroup } from '@angular/forms';
import { ApiError } from '../models/api-response.model';

/**
 * Maps a VALIDATION_ERROR response's field details onto form controls as
 * { server: message } errors, so templates can show backend messages
 * field-by-field. Returns true if at least one field was matched.
 */
export function applyServerFieldErrors(form: FormGroup, error: ApiError): boolean {
  if (error.code !== 'VALIDATION_ERROR' || typeof error.details !== 'object' || !error.details) {
    return false;
  }
  let matched = false;
  for (const [field, message] of Object.entries(error.details as Record<string, unknown>)) {
    const control = form.get(field);
    if (control) {
      control.setErrors({ ...control.errors, server: String(message) });
      control.markAsTouched();
      matched = true;
    }
  }
  return matched;
}
