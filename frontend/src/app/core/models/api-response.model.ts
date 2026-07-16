/**
 * Shared API envelope — mirrors the backend contract exactly.
 * Success: { success: true, data, meta?: { total, page, limit } }
 * Error:   { success: false, error, code, details? }
 */

export interface PageMeta {
  total: number;
  page: number;
  limit: number;
}

export interface ApiSuccess<T> {
  success: true;
  data: T;
  meta?: PageMeta;
}

export type ApiErrorCode =
  | 'AUTH_REQUIRED'
  | 'INSUFFICIENT_ROLE'
  | 'VALIDATION_ERROR'
  | 'RESOURCE_NOT_FOUND'
  | 'EMAIL_CONFLICT'
  | 'RATE_LIMIT_EXCEEDED'
  | 'OCR_FAILED'
  | 'STORAGE_ERROR'
  | 'INTERNAL_ERROR';

export interface ApiError {
  success: false;
  error: string;
  code: ApiErrorCode;
  details?: unknown;
}

export type ApiResponse<T> = ApiSuccess<T> | ApiError;

/** A paginated result after the envelope has been unwrapped. */
export interface Paged<T> {
  data: T;
  meta?: PageMeta;
}
