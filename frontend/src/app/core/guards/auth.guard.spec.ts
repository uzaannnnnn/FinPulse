import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, RouterStateSnapshot, UrlTree } from '@angular/router';
import { ACCESS_TOKEN_KEY } from '../services/auth.service';
import { authGuard } from './auth.guard';
import { guestGuard } from './guest.guard';

describe('Route Guards', () => {
  const dummyRoute = {} as ActivatedRouteSnapshot;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('authGuard should redirect unauthenticated users to /login', () => {
    const state = { url: '/transactions' } as RouterStateSnapshot;
    const result = TestBed.runInInjectionContext(() => authGuard(dummyRoute, state));
    expect(result instanceof UrlTree).toBeTrue();
    expect((result as UrlTree).toString()).toBe('/login?returnUrl=%2Ftransactions');
  });

  it('authGuard should allow authenticated users', () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'token-123');
    const state = { url: '/dashboard' } as RouterStateSnapshot;
    const result = TestBed.runInInjectionContext(() => authGuard(dummyRoute, state));
    expect(result).toBeTrue();
  });

  it('guestGuard should allow unauthenticated users', () => {
    const state = { url: '/login' } as RouterStateSnapshot;
    const result = TestBed.runInInjectionContext(() => guestGuard(dummyRoute, state));
    expect(result).toBeTrue();
  });

  it('guestGuard should redirect authenticated users to /dashboard', () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'token-123');
    const state = { url: '/login' } as RouterStateSnapshot;
    const result = TestBed.runInInjectionContext(() => guestGuard(dummyRoute, state));
    expect(result instanceof UrlTree).toBeTrue();
    expect((result as UrlTree).toString()).toBe('/dashboard');
  });
});
