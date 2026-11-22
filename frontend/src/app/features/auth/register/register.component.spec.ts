import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { RegisterComponent } from './register.component';

describe('RegisterComponent', () => {
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should enforce minLength on fullName and password', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    component.registerForm.setValue({
      fullName: 'A',
      email: 'invalid-email',
      password: '123',
    });

    component.onSubmit();

    expect(component.registerForm.invalid).toBeTrue();
    expect(component.getFieldError('fullName')).toBe('Nama lengkap minimal 2 karakter.');
    expect(component.getFieldError('email')).toBe('Format email tidak valid.');
    expect(component.getFieldError('password')).toBe('Password minimal 8 karakter.');
    httpMock.expectNone(`${environment.apiUrl}/auth/register`);
  });

  it('should register user and redirect to /dashboard on 201 Created', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    const component = fixture.componentInstance;
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
    fixture.detectChanges();

    component.registerForm.setValue({
      fullName: 'Budi Santoso',
      email: 'budi@finpulse.app',
      password: 'Password123!',
    });

    component.onSubmit();

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/register`);
    expect(req.request.method).toBe('POST');
    req.flush(
      {
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
      },
      { status: 201, statusText: 'Created' }
    );

    expect(navigateSpy).toHaveBeenCalledWith(['/dashboard']);
  });

  it('should enforce maxLength, trimmed minLength, and UTF-8 byte limits', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    component.registerForm.setValue({
      fullName: '   ',
      email: 'budi@finpulse.app',
      password: '        ',
    });
    component.onSubmit();

    expect(component.getFieldError('fullName')).toBe('Nama lengkap wajib diisi.');
    expect(component.getFieldError('password')).toBe('Password wajib diisi.');

    component.registerForm.setValue({
      fullName: ' A ',
      email: 'budi@finpulse.app',
      password: 'é'.repeat(40),
    });
    component.onSubmit();

    expect(component.getFieldError('fullName')).toBe('Nama lengkap minimal 2 karakter.');
    expect(component.getFieldError('password')).toBe('Password maksimal 72 byte UTF-8.');

    component.registerForm.setValue({
      fullName: 'B'.repeat(101),
      email: 'budi@finpulse.app',
      password: 'p'.repeat(73),
    });
    component.onSubmit();

    expect(component.getFieldError('fullName')).toBe('Nama lengkap maksimal 100 karakter.');
    expect(component.getFieldError('password')).toBe('Password maksimal 72 karakter.');
    httpMock.expectNone(`${environment.apiUrl}/auth/register`);
  });

  it('should display ErrorResponse message when backend returns 409 Conflict', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    component.registerForm.setValue({
      fullName: 'Budi Santoso',
      email: 'budi@finpulse.app',
      password: 'Password123!',
    });

    component.onSubmit();

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/register`);
    req.flush(
      {
        timestamp: '2026-11-21T11:00:00Z',
        status: 409,
        error: 'Conflict',
        message: 'Email sudah terdaftar',
        path: '/api/auth/register',
      },
      { status: 409, statusText: 'Conflict' }
    );

    expect(component.errorMessage()).toBe('Email sudah terdaftar');
  });
});
