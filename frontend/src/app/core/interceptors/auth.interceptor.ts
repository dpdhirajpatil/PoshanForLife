import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

const AUTH_ENDPOINTS = ['/auth/login', '/auth/refresh', '/auth/logout'];

function isAuthEndpoint(url: string): boolean {
  return AUTH_ENDPOINTS.some((path) => url.includes(path));
}

function withToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}

/**
 * Attaches the JWT bearer token to every outgoing request. On 401, attempts
 * ONE silent refresh and retries the original request; if that also fails the
 * session is treated as expired (toast + redirect to login). 403 shows an
 * "insufficient role" toast globally.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const toast = inject(ToastService);

  return next(withToken(req, auth.token)).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse)) {
        return throwError(() => err);
      }

      if (err.status === 401 && !isAuthEndpoint(req.url)) {
        return auth.refreshSession().pipe(
          switchMap(() => next(withToken(req, auth.token))),
          catchError((retryErr: unknown) => {
            if (retryErr instanceof HttpErrorResponse && retryErr.status === 401) {
              auth.handleSessionExpired();
            } else if (!(retryErr instanceof HttpErrorResponse)) {
              // refresh itself failed (revoked/expired refresh token)
              auth.handleSessionExpired();
            }
            return throwError(() => retryErr);
          }),
        );
      }

      if (err.status === 403) {
        toast.error('You do not have permission to perform this action.');
      }
      return throwError(() => err);
    }),
  );
};
