import { HttpErrorResponse } from '@angular/common/http';
import { ErrorResponse } from '../models/error.model';

export interface ParsedApiError {
  message: string;
  validationErrors: Record<string, string> | null;
  status: number | null;
}

export function isUnauthorizedError(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 401;
}

/**
 * Extracts a user-friendly message and optional field validation map
 * from an Angular HttpErrorResponse matching the FinPulse ErrorResponse contract.
 */
export function extractApiError(error: unknown, fallbackMessage: string): ParsedApiError {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return {
        message: 'Tidak dapat terhubung ke server FinPulse. Pastikan backend berjalan.',
        validationErrors: null,
        status: 0,
      };
    }

    const body = error.error as Partial<ErrorResponse> | string | null | undefined;
    if (body && typeof body === 'object') {
      const message =
        typeof body.message === 'string' && body.message.trim().length > 0
          ? body.message
          : fallbackMessage;

      const validationErrors =
        body.validationErrors && typeof body.validationErrors === 'object'
          ? body.validationErrors
          : null;

      return {
        message,
        validationErrors,
        status: error.status,
      };
    }

    if (typeof body === 'string' && body.trim().length > 0) {
      return {
        message: body,
        validationErrors: null,
        status: error.status,
      };
    }

    return {
      message: fallbackMessage,
      validationErrors: null,
      status: error.status,
    };
  }

  if (error instanceof Error && error.message.trim().length > 0) {
    return {
      message: error.message,
      validationErrors: null,
      status: null,
    };
  }

  return {
    message: fallbackMessage,
    validationErrors: null,
    status: null,
  };
}
