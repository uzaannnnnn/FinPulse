import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);

  if (typeof window === 'undefined' || typeof localStorage === 'undefined') {
    return router.createUrlTree(['/login']);
  }

  const authService = inject(AuthService);

  if (authService.isAuthenticated()) {
    return true;
  }

  const returnUrl = state.url && state.url !== '/' && state.url !== '/dashboard' ? state.url : null;
  return returnUrl
    ? router.createUrlTree(['/login'], { queryParams: { returnUrl } })
    : router.createUrlTree(['/login']);
};
