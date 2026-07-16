import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiError, ApiSuccess, Paged } from '../models/api-response.model';

/** Query params accepted by the thin wrappers below. */
export type QueryParams = Record<string, string | number | boolean | undefined>;

/**
 * Thin wrapper around HttpClient that talks to the backend envelope:
 * unwraps `data` on success and normalizes every failure into an ApiError.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  get<T>(path: string, params?: QueryParams): Observable<T> {
    return this.unwrap(
      this.http.get<ApiSuccess<T>>(this.url(path), { params: this.toHttpParams(params) }),
    );
  }

  /** For list endpoints — keeps the pagination meta alongside the data. */
  getPaged<T>(path: string, params?: QueryParams): Observable<Paged<T>> {
    return this.http
      .get<ApiSuccess<T>>(this.url(path), { params: this.toHttpParams(params) })
      .pipe(
        map((res) => ({ data: res.data, meta: res.meta })),
        catchError((err) => this.normalizeError(err)),
      );
  }

  post<T>(path: string, body: unknown): Observable<T> {
    return this.unwrap(this.http.post<ApiSuccess<T>>(this.url(path), body));
  }

  put<T>(path: string, body: unknown): Observable<T> {
    return this.unwrap(this.http.put<ApiSuccess<T>>(this.url(path), body));
  }

  patch<T>(path: string, body: unknown): Observable<T> {
    return this.unwrap(this.http.patch<ApiSuccess<T>>(this.url(path), body));
  }

  delete<T>(path: string): Observable<T> {
    return this.unwrap(this.http.delete<ApiSuccess<T>>(this.url(path)));
  }

  private url(path: string): string {
    return `${this.baseUrl}${path.startsWith('/') ? '' : '/'}${path}`;
  }

  private unwrap<T>(source: Observable<ApiSuccess<T>>): Observable<T> {
    return source.pipe(
      map((res) => res.data),
      catchError((err) => this.normalizeError(err)),
    );
  }

  /** Always surface a well-formed ApiError to subscribers. */
  private normalizeError(err: unknown): Observable<never> {
    if (err instanceof HttpErrorResponse && this.isApiError(err.error)) {
      return throwError(() => err.error as ApiError);
    }
    const fallback: ApiError = {
      success: false,
      error: err instanceof HttpErrorResponse && err.status === 0
        ? 'Cannot reach the server. Check your connection.'
        : 'An unexpected error occurred.',
      code: 'INTERNAL_ERROR',
    };
    return throwError(() => fallback);
  }

  private isApiError(body: unknown): body is ApiError {
    return (
      typeof body === 'object' &&
      body !== null &&
      (body as ApiError).success === false &&
      typeof (body as ApiError).code === 'string'
    );
  }

  private toHttpParams(params?: QueryParams): HttpParams | undefined {
    if (!params) return undefined;
    let httpParams = new HttpParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined) {
        httpParams = httpParams.set(key, String(value));
      }
    }
    return httpParams;
  }
}
