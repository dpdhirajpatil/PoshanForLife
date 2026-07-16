import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

/**
 * Attaches the JWT bearer token to every outgoing API request and handles
 * auth failures globally: 401 → drop session and redirect to login,
 * 403 → "insufficient role" toast.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const toast = inject(ToastService);

  const token = auth.token;
  const authorizedReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authorizedReq).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        const isLoginCall = req.url.includes('/auth/login');
        if (err.status === 401 && !isLoginCall) {
          auth.handleSessionExpired();
        } else if (err.status === 403) {
          toast.error('You do not have permission to perform this action.');
        }
      }
      return throwError(() => err);
    }),
  );
};
