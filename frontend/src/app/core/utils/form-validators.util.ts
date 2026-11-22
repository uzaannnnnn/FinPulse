import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

const UTF8_ENCODER = new TextEncoder();
export const MAX_MONETARY_VALUE = 9_999_999_999_999.99;

function hasMoreThanTwoDecimals(raw: unknown, num: number): boolean {
  const str = String(raw).trim();
  if (/^[+-]?\d+(\.\d+)?$/.test(str)) {
    const dotIndex = str.indexOf('.');
    return dotIndex >= 0 && str.length - dotIndex - 1 > 2;
  }
  return Math.abs(num * 100 - Math.round(num * 100)) > 1e-8;
}

export function positiveDecimalValidator(
  positiveErrorKey: string,
  maxErrorKey: string,
  maxValue = MAX_MONETARY_VALUE
): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const raw = control.value;
    if (raw === null || raw === undefined || raw === '') {
      return null;
    }
    const num = Number(raw);
    if (!Number.isFinite(num) || num <= 0) {
      return { [positiveErrorKey]: true };
    }
    if (num > maxValue) {
      return { [maxErrorKey]: true };
    }
    if (hasMoreThanTwoDecimals(raw, num)) {
      return { maxDecimals: true };
    }
    return null;
  };
}

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
