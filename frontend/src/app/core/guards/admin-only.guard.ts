import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Admin-only routes (e.g. Users). Wrong role lands on the 403 page. */
export const adminOnlyGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.hasAnyRole('ADMIN')) {
    return true;
  }
  if (!auth.isLoggedIn()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  return router.createUrlTree(['/forbidden']);
};
