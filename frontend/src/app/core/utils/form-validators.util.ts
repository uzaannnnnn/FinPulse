import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

const UTF8_ENCODER = new TextEncoder();

export function notBlankValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    if (typeof value === 'string' && value.length > 0 && value.trim().length === 0) {
      return { required: true };
    }
    return null;
  };
}

export function trimmedLengthValidator(min: number, max: number): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    if (typeof value !== 'string' || value.trim().length === 0) {
      return null;
    }
    const trimmedLength = value.trim().length;
    if (trimmedLength < min) {
      return { minlength: { requiredLength: min, actualLength: trimmedLength } };
    }
    if (trimmedLength > max) {
      return { maxlength: { requiredLength: max, actualLength: trimmedLength } };
    }
    return null;
  };
}

export function maxUtf8BytesValidator(maxBytes: number): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    if (typeof value !== 'string' || value.length === 0) {
      return null;
    }
    const byteLength = UTF8_ENCODER.encode(value).length;
    return byteLength > maxBytes ? { maxUtf8Bytes: { maxBytes, actualBytes: byteLength } } : null;
  };
}
