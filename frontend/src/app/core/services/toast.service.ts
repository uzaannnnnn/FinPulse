import { Injectable, signal } from '@angular/core';

export type ToastType = 'success' | 'error' | 'info';

export interface ToastMessage {
  id: number;
  type: ToastType;
  title?: string;
  message: string;
  durationMs: number;
}

@Injectable({
  providedIn: 'root',
})
export class ToastService {
  private readonly _toasts = signal<ToastMessage[]>([]);
  private nextId = 1;
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();

  readonly toasts = this._toasts.asReadonly();

  show(message: string, type: ToastType = 'info', title?: string, durationMs = 4500): number {
    const id = this.nextId++;
    const toast: ToastMessage = { id, type, title, message, durationMs };

    this._toasts.update((current) => [...current, toast]);

    if (durationMs > 0) {
      const timer = setTimeout(() => this.dismiss(id), durationMs);
      this.timers.set(id, timer);
    }

    return id;
  }

  success(message: string, title = 'Berhasil', durationMs = 4000): number {
    return this.show(message, 'success', title, durationMs);
  }

  error(message: string, title = 'Terjadi Kesalahan', durationMs = 5500): number {
    return this.show(message, 'error', title, durationMs);
  }

  info(message: string, title = 'Informasi', durationMs = 4000): number {
    return this.show(message, 'info', title, durationMs);
  }

  dismiss(id: number): void {
    const timer = this.timers.get(id);
    if (timer !== undefined) {
      clearTimeout(timer);
      this.timers.delete(id);
    }
    this._toasts.update((current) => current.filter((item) => item.id !== id));
  }

  clear(): void {
    this.timers.forEach((timer) => clearTimeout(timer));
    this.timers.clear();
    this._toasts.set([]);
  }
}
