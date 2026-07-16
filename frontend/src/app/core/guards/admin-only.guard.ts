import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

/** Admin-only routes (e.g. Users). Doctors get a toast and land on the dashboard. */
export const adminOnlyGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);

  if (auth.hasAnyRole('ADMIN')) {
    return true;
  }
  toast.error('This area is restricted to administrators.');
  return router.createUrlTree(['/dashboard']);
};
