import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ToastMessage, ToastService } from '../../../core/services/toast.service';
import { toastPop } from '../../animations';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [CommonModule],
  animations: [toastPop],
  template: `
    <div
      class="pointer-events-none fixed right-4 top-4 z-50 flex w-[calc(100%-2rem)] max-w-sm flex-col gap-3 sm:right-6 sm:top-6"
      aria-live="polite"
      aria-atomic="true"
    >
      @for (toast of toastService.toasts(); track toast.id) {
        <div
          @toastPop
          class="pointer-events-auto flex items-start gap-3 rounded-2xl border-2 border-fp-ink bg-fp-paper p-4 shadow-brutal"
          [ngClass]="containerClasses(toast)"
          role="alert"
        >
          <div class="mt-0.5 shrink-0">
            @if (toast.type === 'success') {
              <span class="flex h-7 w-7 items-center justify-center rounded-lg border-2 border-fp-ink bg-[#00D68F] text-fp-ink">
                <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.7">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
                </svg>
              </span>
            } @else if (toast.type === 'error') {
              <span class="flex h-7 w-7 items-center justify-center rounded-lg border-2 border-fp-ink bg-[#FF4D6D] text-white">
                <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.7">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </span>
            } @else {
              <span class="flex h-7 w-7 items-center justify-center rounded-lg border-2 border-fp-ink bg-[#FACC15] text-fp-ink">
                <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.7">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M13 16h-1v-4h-1m1-4h.01M12 20a8 8 0 100-16 8 8 0 000 16z" />
                </svg>
              </span>
            }
          </div>

          <div class="min-w-0 flex-1">
            @if (toast.title) {
              <p class="text-sm font-bold text-fp-ink">{{ toast.title }}</p>
            }
            <p class="text-xs font-medium text-slate-700" [class.mt-0.5]="toast.title">{{ toast.message }}</p>
          </div>

          <button
            type="button"
            (click)="toastService.dismiss(toast.id)"
            class="fp-btn-sm shrink-0 rounded-lg bg-white p-1 text-fp-ink hover:bg-slate-100"
            aria-label="Tutup notifikasi"
          >
            <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      }
    </div>
  `,
})
export class ToastComponent {
  readonly toastService = inject(ToastService);

  containerClasses(toast: ToastMessage): string {
    switch (toast.type) {
      case 'success':
        return 'border-l-[6px] border-l-[#00D68F]';
      case 'error':
        return 'border-l-[6px] border-l-[#FF4D6D]';
      default:
        return 'border-l-[6px] border-l-[#FACC15]';
    }
  }
}
