import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthResponse, UserDto } from '../models/auth.model';
import { AlertService } from '../services/alert.service';
import { ACCESS_TOKEN_KEY, AuthService, REFRESH_TOKEN_KEY } from '../services/auth.service';
import { SyncStatusService } from '../services/sync-status.service';
import { ToastService } from '../services/toast.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;
  let toastService: ToastService;
  let router: Router;

  const mockUser: UserDto = {
    id: '550e8400-e29b-41d4-a716-446655440000',
    email: 'budi@finpulse.app',
    fullName: 'Budi Santoso',
    createdAt: '2026-11-21T10:30:00Z',
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
    toastService = TestBed.inject(ToastService);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should attach Bearer token for protected API calls', () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'valid-jwt');

    http.get(`${environment.apiUrl}/categories`).subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/categories`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer valid-jwt');
    req.flush([]);
  });

  it('should NOT attach Bearer token on /auth/login, /auth/register, or /auth/refresh', () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'valid-jwt');

    http.post(`${environment.apiUrl}/auth/login`, {}).subscribe();
    http.post(`${environment.apiUrl}/auth/register`, {}).subscribe();
    http.post(`${environment.apiUrl}/auth/refresh`, {}).subscribe();

    const loginReq = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    const registerReq = httpMock.expectOne(`${environment.apiUrl}/auth/register`);
    const refreshReq = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);

    expect(loginReq.request.headers.has('Authorization')).toBeFalse();
    expect(registerReq.request.headers.has('Authorization')).toBeFalse();
    expect(refreshReq.request.headers.has('Authorization')).toBeFalse();

    loginReq.flush({});
    registerReq.flush({});
    refreshReq.flush({});
  });

  it('should refresh token once on 401 and retry failed request with new token', () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'expired-jwt');
    localStorage.setItem(REFRESH_TOKEN_KEY, 'valid-refresh');

    let responseData: unknown;
    http.get(`${environment.apiUrl}/categories`).subscribe((data) => {
      responseData = data;
    });

    const firstReq = httpMock.expectOne(`${environment.apiUrl}/categories`);
    expect(firstReq.request.headers.get('Authorization')).toBe('Bearer expired-jwt');
    firstReq.flush({ message: 'Token expired' }, { status: 401, statusText: 'Unauthorized' });

    const refreshReq = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);
    const refreshedAuth: AuthResponse = {
      accessToken: 'fresh-jwt',
      refreshToken: 'fresh-refresh',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: mockUser,
    };
    refreshReq.flush(refreshedAuth);

    const retryReq = httpMock.expectOne(`${environment.apiUrl}/categories`);
    expect(retryReq.request.headers.get('Authorization')).toBe('Bearer fresh-jwt');
    retryReq.flush([{ id: '1', name: 'Gaji', type: 'INCOME', isDefault: true }]);

    expect(responseData).toEqual([{ id: '1', name: 'Gaji', type: 'INCOME', isDefault: true }]);
  });

  it('should clear session and navigate to /login when refresh fails', () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'expired-jwt');
    localStorage.setItem(REFRESH_TOKEN_KEY, 'revoked-refresh');
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);

    let failed = false;
    http.get(`${environment.apiUrl}/categories`).subscribe({
      error: () => {
        failed = true;
      },
    });

    const firstReq = httpMock.expectOne(`${environment.apiUrl}/categories`);
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    const refreshReq = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);
    refreshReq.flush({ message: 'Invalid refresh token' }, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBeTrue();
    expect(authService.isAuthenticated()).toBeFalse();
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });

  it('should share a single refresh call across concurrent 401 responses', () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'expired-jwt');
    localStorage.setItem(REFRESH_TOKEN_KEY, 'valid-refresh');

    let meResult: unknown;
    let catResult: unknown;

    http.get(`${environment.apiUrl}/auth/me`).subscribe((res) => (meResult = res));
    http.get(`${environment.apiUrl}/categories`).subscribe((res) => (catResult = res));

    const firstMe = httpMock.expectOne(`${environment.apiUrl}/auth/me`);
    const firstCat = httpMock.expectOne(`${environment.apiUrl}/categories`);

    firstMe.flush({ message: 'Expired' }, { status: 401, statusText: 'Unauthorized' });
    firstCat.flush({ message: 'Expired' }, { status: 401, statusText: 'Unauthorized' });

    const refreshReqs = httpMock.match(`${environment.apiUrl}/auth/refresh`);
    expect(refreshReqs.length).toBe(1);
    refreshReqs[0].flush({
      accessToken: 'shared-fresh-jwt',
      refreshToken: 'shared-fresh-refresh',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: mockUser,
    });

    const retryMe = httpMock.expectOne(`${environment.apiUrl}/auth/me`);
    const retryCat = httpMock.expectOne(`${environment.apiUrl}/categories`);
    expect(retryMe.request.headers.get('Authorization')).toBe('Bearer shared-fresh-jwt');
    expect(retryCat.request.headers.get('Authorization')).toBe('Bearer shared-fresh-jwt');

    retryMe.flush(mockUser);
    retryCat.flush([]);

    expect(meResult).toEqual(mockUser);
    expect(catResult).toEqual([]);
  });

  it('should NOT clear session or navigate to /login when retried request fails with 400 after successful refresh', () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'expired-jwt');
    localStorage.setItem(REFRESH_TOKEN_KEY, 'valid-refresh');
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
    const toastErrorSpy = spyOn(toastService, 'error');

    let capturedError: HttpErrorResponse | undefined;
    http.get(`${environment.apiUrl}/categories?type=INVALID`).subscribe({
      error: (err) => {
        capturedError = err as HttpErrorResponse;
      },
    });

    const firstReq = httpMock.expectOne(`${environment.apiUrl}/categories?type=INVALID`);
    firstReq.flush({ message: 'Expired' }, { status: 401, statusText: 'Unauthorized' });

    const refreshReq = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);
    refreshReq.flush({
      accessToken: 'fresh-jwt',
      refreshToken: 'fresh-refresh',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: mockUser,
    });

    const retryReq = httpMock.expectOne(`${environment.apiUrl}/categories?type=INVALID`);
    expect(retryReq.request.headers.get('Authorization')).toBe('Bearer fresh-jwt');
    retryReq.flush({ message: 'Invalid type' }, { status: 400, statusText: 'Bad Request' });

    expect(capturedError?.status).toBe(400);
    expect(authService.isAuthenticated()).toBeTrue();
    expect(authService.getAccessToken()).toBe('fresh-jwt');
    expect(navigateSpy).not.toHaveBeenCalled();
    expect(toastErrorSpy).not.toHaveBeenCalled();
  });

  it('should emit session-expired toast and navigate to /login only once across concurrent 401 failures', () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'expired-jwt');
    localStorage.setItem(REFRESH_TOKEN_KEY, 'revoked-refresh');
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
    const toastErrorSpy = spyOn(toastService, 'error');

    let meFailed = false;
    let catFailed = false;

    http.get(`${environment.apiUrl}/auth/me`).subscribe({ error: () => (meFailed = true) });
    http.get(`${environment.apiUrl}/categories`).subscribe({ error: () => (catFailed = true) });

    const firstMe = httpMock.expectOne(`${environment.apiUrl}/auth/me`);
    const firstCat = httpMock.expectOne(`${environment.apiUrl}/categories`);

    firstMe.flush({ message: 'Expired' }, { status: 401, statusText: 'Unauthorized' });
    firstCat.flush({ message: 'Expired' }, { status: 401, statusText: 'Unauthorized' });

    const refreshReq = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);
    refreshReq.flush({ message: 'Revoked' }, { status: 401, statusText: 'Unauthorized' });

    expect(meFailed).toBeTrue();
    expect(catFailed).toBeTrue();
    expect(toastErrorSpy).toHaveBeenCalledTimes(1);
    expect(navigateSpy).toHaveBeenCalledTimes(1);
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });

  it('should refresh token and retry /auth/logout when access token is expired and refresh token is valid', () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'expired-jwt');
    localStorage.setItem(REFRESH_TOKEN_KEY, 'valid-refresh');

    let completed = false;
    http.post<void>(`${environment.apiUrl}/auth/logout`, { refreshToken: 'valid-refresh' }).subscribe({
      complete: () => (completed = true),
    });

    const firstLogout = httpMock.expectOne(`${environment.apiUrl}/auth/logout`);
    firstLogout.flush({ message: 'Expired' }, { status: 401, statusText: 'Unauthorized' });

    const refreshReq = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);
    refreshReq.flush({
      accessToken: 'fresh-jwt',
      refreshToken: 'fresh-refresh',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: mockUser,
    });

    const retryLogout = httpMock.expectOne(`${environment.apiUrl}/auth/logout`);
    expect(retryLogout.request.headers.get('Authorization')).toBe('Bearer fresh-jwt');
    retryLogout.flush(null, { status: 204, statusText: 'No Content' });

    expect(completed).toBeTrue();
  });

  it('should NOT show session-expired toast when refresh fails during /auth/logout', () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'expired-jwt');
    localStorage.setItem(REFRESH_TOKEN_KEY, 'revoked-refresh');
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
    const toastErrorSpy = spyOn(toastService, 'error');

    let failed = false;
    http.post<void>(`${environment.apiUrl}/auth/logout`, { refreshToken: 'revoked-refresh' }).subscribe({
      error: () => (failed = true),
    });

    const firstLogout = httpMock.expectOne(`${environment.apiUrl}/auth/logout`);
    firstLogout.flush({ message: 'Expired' }, { status: 401, statusText: 'Unauthorized' });

    const refreshReq = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);
    refreshReq.flush({ message: 'Revoked' }, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBeTrue();
    expect(authService.isAuthenticated()).toBeFalse();
    expect(toastErrorSpy).not.toHaveBeenCalled();
    expect(navigateSpy).not.toHaveBeenCalled();
  });

  it('should reset AlertService and SyncStatusService when 401 refresh fails to prevent cross-user leakage', () => {
    const alertService = TestBed.inject(AlertService);
    const syncStatusService = TestBed.inject(SyncStatusService);
    const alertResetSpy = spyOn(alertService, 'reset').and.callThrough();
    const syncResetSpy = spyOn(syncStatusService, 'reset').and.callThrough();
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);

    syncStatusService.markSynced('CACHE', '2026-11-21T12:00:00Z');
    localStorage.setItem(ACCESS_TOKEN_KEY, 'expired-jwt');
    localStorage.setItem(REFRESH_TOKEN_KEY, 'revoked-refresh');

    http.get(`${environment.apiUrl}/dashboard/summary`).subscribe({ error: () => undefined });

    httpMock
      .expectOne(`${environment.apiUrl}/dashboard/summary`)
      .flush({ message: 'Expired' }, { status: 401, statusText: 'Unauthorized' });

    httpMock
      .expectOne(`${environment.apiUrl}/auth/refresh`)
      .flush({ message: 'Revoked' }, { status: 401, statusText: 'Unauthorized' });

    expect(alertResetSpy).toHaveBeenCalled();
    expect(syncResetSpy).toHaveBeenCalled();
    expect(syncStatusService.status()).toBe('idle');
    expect(syncStatusService.source()).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });
});
