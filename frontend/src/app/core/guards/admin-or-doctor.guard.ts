import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

/** Portal features available to both staff roles. */
export const adminOrDoctorGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);

  if (auth.hasAnyRole('ADMIN', 'DOCTOR')) {
    return true;
  }
  toast.error('You do not have permission to access this area.');
  return router.createUrlTree(['/login']);
};
