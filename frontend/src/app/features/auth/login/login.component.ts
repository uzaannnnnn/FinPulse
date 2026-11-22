import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { extractApiError } from '../../../core/utils/error.util';
import { notBlankValidator } from '../../../core/utils/form-validators.util';
import { SpinnerComponent } from '../../../shared/components/spinner/spinner.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, SpinnerComponent],
  templateUrl: './login.component.html',
})
export class LoginComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly authService = inject(AuthService);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly isSubmitting = signal(false);
  readonly showPassword = signal(false);
  readonly submitted = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly serverFieldErrors = signal<Record<string, string>>({});

  readonly loginForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, notBlankValidator()]],
  });

  togglePasswordVisibility(): void {
    this.showPassword.update((visible) => !visible);
  }

  getFieldError(field: 'email' | 'password'): string | null {
    const serverError = this.serverFieldErrors()[field];
    if (serverError) {
      return serverError;
    }

    const control = this.loginForm.controls[field];
    if (!control.invalid || (!control.touched && !this.submitted())) {
      return null;
    }

    if (control.hasError('required')) {
      return field === 'email' ? 'Email wajib diisi.' : 'Password wajib diisi.';
    }
    if (field === 'email' && control.hasError('email')) {
      return 'Format email tidak valid.';
    }
    return null;
  }

  onSubmit(): void {
    this.submitted.set(true);
    this.errorMessage.set(null);
    this.serverFieldErrors.set({});

    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    const raw = this.loginForm.getRawValue();
    this.isSubmitting.set(true);

    this.authService
      .login({
        email: raw.email.trim(),
        password: raw.password,
      })
      .pipe(
        finalize(() => this.isSubmitting.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          this.toastService.success('Berhasil masuk');
          const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
          const safeTarget =
            returnUrl &&
            returnUrl.startsWith('/') &&
            !returnUrl.startsWith('//') &&
            !returnUrl.startsWith('/\\')
              ? returnUrl
              : '/dashboard';
          void this.router.navigateByUrl(safeTarget);
        },
        error: (error: unknown) => {
          const parsed = extractApiError(
            error,
            'Login gagal. Periksa kembali email dan password Anda.'
          );
          this.errorMessage.set(parsed.message);
          if (parsed.validationErrors) {
            this.serverFieldErrors.set(parsed.validationErrors);
          }
          this.toastService.error('Gagal masuk');
        },
      });
  }
}
