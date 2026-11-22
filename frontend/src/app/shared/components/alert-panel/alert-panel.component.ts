import { CommonModule } from '@angular/common';
import {
  Component,
  computed,
  DestroyRef,
  ElementRef,
  HostListener,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs/operators';
import { BudgetAlertDto } from '../../../core/models/alert.model';
import { AlertService } from '../../../core/services/alert.service';
import { ToastService } from '../../../core/services/toast.service';
import { isUnauthorizedError } from '../../../core/utils/error.util';
import {
  formatDateTimeId,
  formatIdr,
  formatPercent,
  getMonthName,
  sanitizeHexColor,
} from '../../utils/format.util';
import { modalPopIn } from '../../animations';
import { SpinnerComponent } from '../spinner/spinner.component';

@Component({
  selector: 'app-alert-panel',
  standalone: true,
  imports: [CommonModule, SpinnerComponent],
  templateUrl: './alert-panel.component.html',
  animations: [modalPopIn],
})
export class AlertPanelComponent {
  private readonly alertService = inject(AlertService);
  private readonly toastService = inject(ToastService);
  private readonly elementRef = inject(ElementRef<HTMLElement>);
  private readonly destroyRef = inject(DestroyRef);

  readonly alerts = this.alertService.alerts;
  readonly unreadCount = this.alertService.unreadCount;
  readonly isLoading = this.alertService.isLoading;

  readonly isOpen = signal<boolean>(false);
  readonly isMarkingAll = signal<boolean>(false);
  readonly busyAlertIds = signal<ReadonlySet<string>>(new Set());

  readonly unreadBadgeLabel = computed(() => {
    const count = this.unreadCount();
    if (count > 99) {
      return '99+';
    }
    return String(count);
  });

  readonly formatIdr = formatIdr;
  readonly formatDateTimeId = formatDateTimeId;
  readonly formatPercent = formatPercent;

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.isOpen()) {
      return;
    }
    const target = event.target as Node | null;
    if (target && !this.elementRef.nativeElement.contains(target)) {
      this.isOpen.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.isOpen()) {
      this.isOpen.set(false);
    }
  }

  togglePanel(): void {
    this.isOpen.update((open) => !open);
  }

  closePanel(): void {
    this.isOpen.set(false);
  }

  refreshAlerts(): void {
    if (this.isLoading()) {
      return;
    }
    this.alertService
      .loadAlerts()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: (error: unknown) => this.handleActionError(error, 'Gagal memuat data'),
      });
  }

  markAsRead(alert: BudgetAlertDto): void {
    if (alert.isRead || this.isAlertBusy(alert.id)) {
      return;
    }
    this.setAlertBusy(alert.id, true);
    this.alertService
      .markAsRead(alert.id)
      .pipe(
        finalize(() => this.setAlertBusy(alert.id, false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        error: (error: unknown) => this.handleActionError(error, 'Aksi gagal'),
      });
  }

  markAllAsRead(): void {
    if (this.unreadCount() === 0 || this.isMarkingAll()) {
      return;
    }
    this.isMarkingAll.set(true);
    this.alertService
      .markAllAsRead()
      .pipe(
        finalize(() => this.isMarkingAll.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          this.toastService.success('Ditandai dibaca');
        },
        error: (error: unknown) => this.handleActionError(error, 'Aksi gagal'),
      });
  }

  deleteAlert(alert: BudgetAlertDto): void {
    if (this.isAlertBusy(alert.id)) {
      return;
    }
    this.setAlertBusy(alert.id, true);
    this.alertService
      .deleteAlert(alert.id)
      .pipe(
        finalize(() => this.setAlertBusy(alert.id, false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        error: (error: unknown) => this.handleActionError(error, 'Gagal menghapus'),
      });
  }

  isAlertBusy(id: string): boolean {
    return this.busyAlertIds().has(id);
  }

  getCategoryColor(color: string | null | undefined): string {
    return sanitizeHexColor(color, '#EF4444');
  }

  getPeriodLabel(alert: BudgetAlertDto): string {
    return `${getMonthName(alert.month)} ${alert.year}`;
  }

  private handleActionError(error: unknown, message: string): void {
    if (!isUnauthorizedError(error)) {
      this.toastService.error(message);
    }
  }

  private setAlertBusy(id: string, busy: boolean): void {
    this.busyAlertIds.update((current) => {
      const next = new Set(current);
      if (busy) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }
}
