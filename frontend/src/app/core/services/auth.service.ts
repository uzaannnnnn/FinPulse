import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { catchError, finalize, map, shareReplay, tap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  AuthResponse,
  LoginRequest,
  RefreshTokenRequest,
  RegisterRequest,
  UserDto,
} from '../models/auth.model';

export const ACCESS_TOKEN_KEY = 'finpulse_access_token';
export const REFRESH_TOKEN_KEY = 'finpulse_refresh_token';
export const USER_KEY = 'finpulse_user';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/auth`;

  private readonly _accessToken = signal<string | null>(this.readTokenStorage(ACCESS_TOKEN_KEY));
  private readonly _currentUser = signal<UserDto | null>(this.readStoredUser());
  private readonly _isLoading = signal<boolean>(false);

  private refreshInFlight$: Observable<AuthResponse> | null = null;

  readonly currentUser = this._currentUser.asReadonly();
  readonly isAuthenticated = computed(() => Boolean(this._accessToken()));
  readonly isLoading = this._isLoading.asReadonly();

  register(payload: RegisterRequest): Observable<AuthResponse> {
    this._isLoading.set(true);
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, payload).pipe(
      tap((response) => this.setSession(response)),
      finalize(() => this._isLoading.set(false))
    );
  }

  login(payload: LoginRequest): Observable<AuthResponse> {
    this._isLoading.set(true);
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, payload).pipe(
      tap((response) => this.setSession(response)),
      finalize(() => this._isLoading.set(false))
    );
  }

  refreshToken(): Observable<AuthResponse> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      this.clearSession();
      return throwError(() => new Error('Refresh token tidak tersedia'));
    }

    const body: RefreshTokenRequest = { refreshToken };
    return this.http.post<AuthResponse>(`${this.apiUrl}/refresh`, body).pipe(
      tap((response) => this.setSession(response)),
      catchError((error) => {
        this.clearSession();
        return throwError(() => error);
      })
    );
  }

  /**
   * Coordinates concurrent 401 refresh attempts so rotating refresh tokens
   * are only exchanged once per expiration cycle.
   */
  refreshTokenShared(): Observable<AuthResponse> {
    if (!this.refreshInFlight$) {
      this.refreshInFlight$ = this.refreshToken().pipe(
        finalize(() => {
          this.refreshInFlight$ = null;
        }),
        shareReplay(1)
      );
    }
    return this.refreshInFlight$;
  }

  logout(): Observable<void> {
    this._isLoading.set(true);
    const refreshToken = this.getRefreshToken();
    const body: RefreshTokenRequest | null = refreshToken ? { refreshToken } : null;

    return this.http.post<void>(`${this.apiUrl}/logout`, body).pipe(
      map(() => void 0),
      catchError(() => of(void 0)),
      tap(() => this.clearSession()),
      finalize(() => this._isLoading.set(false))
    );
  }

  loadProfile(): Observable<UserDto> {
    return this.http.get<UserDto>(`${this.apiUrl}/me`).pipe(
      tap((user) => {
        this._currentUser.set(user);
        this.writeStorage(USER_KEY, JSON.stringify(user));
      })
    );
  }

  getAccessToken(): string | null {
    return this._accessToken() ?? this.readTokenStorage(ACCESS_TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return this.readTokenStorage(REFRESH_TOKEN_KEY);
  }

  clearSession(): void {
    this.removeStorage(ACCESS_TOKEN_KEY);
    this.removeStorage(REFRESH_TOKEN_KEY);
    this.removeStorage(USER_KEY);
    this._accessToken.set(null);
    this._currentUser.set(null);
  }

  private setSession(response: AuthResponse): void {
    this.writeStorage(ACCESS_TOKEN_KEY, response.accessToken);
    this.writeStorage(REFRESH_TOKEN_KEY, response.refreshToken);
    this.writeStorage(USER_KEY, JSON.stringify(response.user));
    this._accessToken.set(response.accessToken);
    this._currentUser.set(response.user);
  }

  private readStoredUser(): UserDto | null {
    const raw = this.readStorage(USER_KEY);
    if (!raw) {
      return null;
    }
    try {
      const parsed = JSON.parse(raw) as UserDto;
      if (parsed && typeof parsed.id === 'string' && typeof parsed.email === 'string') {
        return parsed;
      }
      this.removeStorage(USER_KEY);
      return null;
    } catch {
      this.removeStorage(USER_KEY);
      return null;
    }
  }

  private readTokenStorage(key: string): string | null {
    const raw = this.readStorage(key);
    if (!raw || raw.trim().length === 0) {
      return null;
    }
    return raw.trim();
  }

  private isBrowserStorageAvailable(): boolean {
    return typeof window !== 'undefined' && typeof localStorage !== 'undefined';
  }

  private readStorage(key: string): string | null {
    try {
      return this.isBrowserStorageAvailable() ? localStorage.getItem(key) : null;
    } catch {
      return null;
    }
  }

  private writeStorage(key: string, value: string): void {
    try {
      if (this.isBrowserStorageAvailable()) {
        localStorage.setItem(key, value);
      }
    } catch {
      // Ignore storage quota / privacy mode errors
    }
  }

  private removeStorage(key: string): void {
    try {
      if (this.isBrowserStorageAvailable()) {
        localStorage.removeItem(key);
      }
    } catch {
      // Ignore storage access errors
    }
  }
}
