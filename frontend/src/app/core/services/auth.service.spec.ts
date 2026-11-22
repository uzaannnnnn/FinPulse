import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { AuthResponse, UserDto } from '../models/auth.model';
import {
  ACCESS_TOKEN_KEY,
  AuthService,
  REFRESH_TOKEN_KEY,
  USER_KEY,
} from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  const mockUser: UserDto = {
    id: '550e8400-e29b-41d4-a716-446655440000',
    email: 'budi@finpulse.app',
    fullName: 'Budi Santoso',
    createdAt: '2026-11-21T10:30:00Z',
  };

  const mockAuthResponse: AuthResponse = {
    accessToken: 'access-token-123',
    refreshToken: 'refresh-token-456',
    tokenType: 'Bearer',
    expiresIn: 900,
    user: mockUser,
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should start unauthenticated when localStorage is empty', () => {
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.currentUser()).toBeNull();
    expect(service.getAccessToken()).toBeNull();
  });

  it('should login and persist tokens and user in localStorage and signals', () => {
    service
      .login({ email: 'budi@finpulse.app', password: 'Password123!' })
      .subscribe((res) => {
        expect(res).toEqual(mockAuthResponse);
      });

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      email: 'budi@finpulse.app',
      password: 'Password123!',
    });
    req.flush(mockAuthResponse);

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.currentUser()).toEqual(mockUser);
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBe('access-token-123');
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBe('refresh-token-456');
    expect(JSON.parse(localStorage.getItem(USER_KEY) ?? '{}')).toEqual(mockUser);
  });

  it('should register and store new session', () => {
    service
      .register({
        fullName: 'Budi Santoso',
        email: 'budi@finpulse.app',
        password: 'Password123!',
      })
      .subscribe((res) => {
        expect(res.user.email).toBe('budi@finpulse.app');
      });

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/register`);
    expect(req.request.method).toBe('POST');
    req.flush(mockAuthResponse, { status: 201, statusText: 'Created' });

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.currentUser()?.fullName).toBe('Budi Santoso');
  });

  it('should refresh token and update stored tokens', () => {
    localStorage.setItem(REFRESH_TOKEN_KEY, 'old-refresh-token');

    const rotatedResponse: AuthResponse = {
      ...mockAuthResponse,
      accessToken: 'new-access-token',
      refreshToken: 'new-refresh-token',
    };

    service.refreshToken().subscribe((res) => {
      expect(res.accessToken).toBe('new-access-token');
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ refreshToken: 'old-refresh-token' });
    req.flush(rotatedResponse);

    expect(service.getAccessToken()).toBe('new-access-token');
    expect(service.getRefreshToken()).toBe('new-refresh-token');
  });

  it('should logout and clear local session even if backend returns error', () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'access-token-123');
    localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-token-456');
    localStorage.setItem(USER_KEY, JSON.stringify(mockUser));

    let completed = false;
    service.logout().subscribe({
      next: () => {
        expect(service.isAuthenticated()).toBeFalse();
        expect(service.currentUser()).toBeNull();
      },
      complete: () => {
        completed = true;
      },
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/logout`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ refreshToken: 'refresh-token-456' });
    req.flush({ message: 'Server error' }, { status: 500, statusText: 'Internal Server Error' });

    expect(completed).toBeTrue();
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull();
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull();
    expect(localStorage.getItem(USER_KEY)).toBeNull();
  });

  it('should load current profile and update currentUser signal', () => {
    service.loadProfile().subscribe((user) => {
      expect(user).toEqual(mockUser);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/me`);
    expect(req.request.method).toBe('GET');
    req.flush(mockUser);

    expect(service.currentUser()).toEqual(mockUser);
  });
});
