import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { extractApiError } from '../../../core/utils/error.util';
import {
  maxUtf8BytesValidator,
  notBlankValidator,
  trimmedLengthValidator,
} from '../../../core/utils/form-validators.util';
import { SpinnerComponent } from '../../../shared/components/spinner/spinner.component';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, SpinnerComponent],
  templateUrl: './register.component.html',
})
export class RegisterComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly authService = inject(AuthService);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly isSubmitting = signal(false);
  readonly showPassword = signal(false);
  readonly submitted = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly serverFieldErrors = signal<Record<string, string>>({});

  readonly registerForm = this.fb.group({
    fullName: [
      '',
      [
        Validators.required,
        notBlankValidator(),
        trimmedLengthValidator(2, 100),
        Validators.maxLength(100),
      ],
    ],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: [
      '',
      [
        Validators.required,
        notBlankValidator(),
        Validators.minLength(8),
        Validators.maxLength(72),
        maxUtf8BytesValidator(72),
      ],
    ],
  });

  togglePasswordVisibility(): void {
    this.showPassword.update((visible) => !visible);
  }

  getFieldError(field: 'fullName' | 'email' | 'password'): string | null {
    const serverError = this.serverFieldErrors()[field];
    if (serverError) {
      return serverError;
    }

    const control = this.registerForm.controls[field];
    if (!control.invalid || (!control.touched && !this.submitted())) {
      return null;
    }

    if (control.hasError('required')) {
      switch (field) {
        case 'fullName':
          return 'Nama lengkap wajib diisi.';
        case 'email':
          return 'Email wajib diisi.';
        case 'password':
          return 'Password wajib diisi.';
      }
    }

    if (field === 'fullName') {
      if (control.hasError('minlength')) {
        return 'Nama lengkap minimal 2 karakter.';
      }
      if (control.hasError('maxlength')) {
        return 'Nama lengkap maksimal 100 karakter.';
      }
    }

    if (field === 'email') {
      if (control.hasError('email')) {
        return 'Format email tidak valid.';
      }
      if (control.hasError('maxlength')) {
        return 'Email maksimal 255 karakter.';
      }
    }

    if (field === 'password') {
      if (control.hasError('minlength')) {
        return 'Password minimal 8 karakter.';
      }
      if (control.hasError('maxlength')) {
        return 'Password maksimal 72 karakter.';
      }
      if (control.hasError('maxUtf8Bytes')) {
        return 'Password maksimal 72 byte UTF-8.';
      }
    }

    return null;
  }

  onSubmit(): void {
    this.submitted.set(true);
    this.errorMessage.set(null);
    this.serverFieldErrors.set({});

    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }

    const raw = this.registerForm.getRawValue();
    this.isSubmitting.set(true);

    this.authService
      .register({
        fullName: raw.fullName.trim(),
        email: raw.email.trim(),
        password: raw.password,
      })
      .pipe(
        finalize(() => this.isSubmitting.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          this.toastService.success('Akun berhasil dibuat');
          void this.router.navigate(['/dashboard']);
        },
        error: (error: unknown) => {
          const parsed = extractApiError(
            error,
            'Registrasi gagal. Periksa kembali data yang Anda masukkan.'
          );
          this.errorMessage.set(parsed.message);
          if (parsed.validationErrors) {
            this.serverFieldErrors.set(parsed.validationErrors);
          }
          this.toastService.error('Registrasi gagal');
        },
      });
  }
}
