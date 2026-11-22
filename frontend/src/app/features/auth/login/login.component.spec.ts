import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should validate required email and password before submitting', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    component.onSubmit();
    fixture.detectChanges();

    expect(component.loginForm.invalid).toBeTrue();
    expect(component.getFieldError('email')).toBe('Email wajib diisi.');
    expect(component.getFieldError('password')).toBe('Password wajib diisi.');
    httpMock.expectNone(`${environment.apiUrl}/auth/login`);
  });

  it('should submit valid credentials, show no error, and navigate to /dashboard', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    const component = fixture.componentInstance;
    const navigateSpy = spyOn(router, 'navigateByUrl').and.resolveTo(true);
    fixture.detectChanges();

    component.loginForm.setValue({
      email: 'budi@finpulse.app',
      password: 'Password123!',
    });

    component.onSubmit();

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    req.flush({
      accessToken: 'jwt-123',
      refreshToken: 'ref-456',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: {
        id: '550e8400-e29b-41d4-a716-446655440000',
        email: 'budi@finpulse.app',
        fullName: 'Budi Santoso',
        createdAt: '2026-11-21T10:30:00Z',
      },
    });

    expect(component.errorMessage()).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith('/dashboard');
  });

  it('should display ErrorResponse message when backend returns 401', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    component.loginForm.setValue({
      email: 'budi@finpulse.app',
      password: 'wrong-password',
    });

    component.onSubmit();

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    req.flush(
      {
        timestamp: '2026-11-21T11:00:00Z',
        status: 401,
        error: 'Unauthorized',
        message: 'Email atau password salah',
        path: '/api/auth/login',
      },
      { status: 401, statusText: 'Unauthorized' }
    );

    expect(component.errorMessage()).toBe('Email atau password salah');
  });

  it('should reject whitespace-only password before submitting', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    component.loginForm.setValue({
      email: 'budi@finpulse.app',
      password: '     ',
    });

    component.onSubmit();

    expect(component.loginForm.invalid).toBeTrue();
    expect(component.getFieldError('password')).toBe('Password wajib diisi.');
    httpMock.expectNone(`${environment.apiUrl}/auth/login`);
  });

  it('should sanitize unsafe returnUrl and redirect safe internal returnUrl', () => {
    const route = TestBed.inject(ActivatedRoute);
    const navigateSpy = spyOn(router, 'navigateByUrl').and.resolveTo(true);

    const fixture = TestBed.createComponent(LoginComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    // Unsafe double-slash open redirect attempt falls back to /dashboard
    const queryParamMapSpy = spyOnProperty(route.snapshot, 'queryParamMap', 'get').and.returnValue(
      convertToParamMap({ returnUrl: '//evil.example/phish' })
    );
    component.loginForm.setValue({ email: 'budi@finpulse.app', password: 'Password123!' });
    component.onSubmit();

    const firstReq = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    firstReq.flush({
      accessToken: 'jwt-123',
      refreshToken: 'ref-456',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: {
        id: '550e8400-e29b-41d4-a716-446655440000',
        email: 'budi@finpulse.app',
        fullName: 'Budi Santoso',
        createdAt: '2026-11-21T10:30:00Z',
      },
    });
    expect(navigateSpy).toHaveBeenCalledWith('/dashboard');

    // Safe internal returnUrl is honored
    navigateSpy.calls.reset();
    queryParamMapSpy.and.returnValue(
      convertToParamMap({ returnUrl: '/transactions' })
    );
    component.onSubmit();

    const secondReq = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    secondReq.flush({
      accessToken: 'jwt-123',
      refreshToken: 'ref-456',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: {
        id: '550e8400-e29b-41d4-a716-446655440000',
        email: 'budi@finpulse.app',
        fullName: 'Budi Santoso',
        createdAt: '2026-11-21T10:30:00Z',
      },
    });
    expect(navigateSpy).toHaveBeenCalledWith('/transactions');
  });
});
