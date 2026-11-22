import {
  HttpContextToken,
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { AuthResponse } from '../models/auth.model';
import { AlertService } from '../services/alert.service';
import { AuthService } from '../services/auth.service';
import { SyncStatusService } from '../services/sync-status.service';
import { ToastService } from '../services/toast.service';

const RETRIED_AFTER_REFRESH = new HttpContextToken<boolean>(() => false);

const PUBLIC_AUTH_PATHS = new Set(['/auth/login', '/auth/register', '/auth/refresh']);

const handledRefreshErrors = new WeakSet<Observable<AuthResponse>>();

function getApiRelativePath(url: string): string {
  return url.slice(environment.apiUrl.length).split('?')[0];
}

function isApiRequest(url: string): boolean {
  return url === environment.apiUrl || url.startsWith(`${environment.apiUrl}/`);
}

function attachBearerToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  if (!token) {
    return req;
  }
  return req.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isApiRequest(req.url)) {
    return next(req);
  }

  const authService = inject(AuthService);
  const alertService = inject(AlertService);
  const syncStatusService = inject(SyncStatusService);
  const router = inject(Router);
  const toastService = inject(ToastService);

  const relativePath = getApiRelativePath(req.url);
  const isPublicAuthRequest = PUBLIC_AUTH_PATHS.has(relativePath);
  const isLogoutRequest = relativePath === '/auth/logout';

  const outgoingReq = isPublicAuthRequest
    ? req
    : attachBearerToken(req, authService.getAccessToken());

  return next(outgoingReq).pipe(
    catchError((error: unknown) => {
      const isUnauthorized = error instanceof HttpErrorResponse && error.status === 401;
      const alreadyRetried = req.context.get(RETRIED_AFTER_REFRESH);

      if (!isUnauthorized || isPublicAuthRequest || alreadyRetried) {
        return throwError(() => error);
      }

      if (isLogoutRequest && !authService.getRefreshToken()) {
        authService.clearSession();
        alertService.reset();
        syncStatusService.reset();
        return throwError(() => error);
      }

      const refresh$ = authService.refreshTokenShared();

      return refresh$.pipe(
        catchError((refreshError: unknown) => {
          authService.clearSession();
          alertService.reset();
          syncStatusService.reset();
          if (!isLogoutRequest && !handledRefreshErrors.has(refresh$)) {
            handledRefreshErrors.add(refresh$);
            toastService.error('Sesi berakhir');
            void router.navigate(['/login']);
          }
          return throwError(() => refreshError);
        }),
        switchMap((authResponse) => {
          const retriedReq = attachBearerToken(req, authResponse.accessToken).clone({
            context: req.context.set(RETRIED_AFTER_REFRESH, true),
          });
          return next(retriedReq);
        })
      );
    })
  );
};
