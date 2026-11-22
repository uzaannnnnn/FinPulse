import { CommonModule } from '@angular/common';
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { EMPTY } from 'rxjs';
import { catchError, filter, finalize } from 'rxjs/operators';
import { AlertService } from '../../../core/services/alert.service';
import { AuthService } from '../../../core/services/auth.service';
import { SyncStatusService } from '../../../core/services/sync-status.service';
import { ToastService } from '../../../core/services/toast.service';
import { formatDateTimeId } from '../../utils/format.util';
import { routeTransition } from '../../animations';
import { AlertPanelComponent } from '../alert-panel/alert-panel.component';
import { SpinnerComponent } from '../spinner/spinner.component';

interface NavItem {
  label: string;
  route: string;
  badge?: string;
  icon: 'dashboard' | 'transactions' | 'budgets' | 'reports';
}

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    SpinnerComponent,
    AlertPanelComponent,
  ],
  animations: [routeTransition],
  templateUrl: './app-layout.component.html',
})
export class AppLayoutComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly alertService = inject(AlertService);
  private readonly syncStatusService = inject(SyncStatusService);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly currentUser = this.authService.currentUser;
  readonly syncStatus = this.syncStatusService.status;
  readonly syncSource = this.syncStatusService.source;
  readonly lastSyncedAt = this.syncStatusService.lastSyncedAt;

  readonly isMobileMenuOpen = signal(false);
  readonly isLoggingOut = signal(false);
  readonly routeAnimationState = signal<string>('initial');

  readonly navItems: NavItem[] = [
    { label: 'Dashboard', route: '/dashboard', icon: 'dashboard' },
    { label: 'Transaksi', route: '/transactions', icon: 'transactions' },
    { label: 'Anggaran', route: '/budgets', icon: 'budgets' },
    { label: 'Laporan', route: '/reports', icon: 'reports' },
  ];

  readonly syncLabel = computed(() => {
    const status = this.syncStatus();
    if (status === 'syncing') {
      return 'Menyinkronkan...';
    }
    if (status === 'error') {
      return 'Gagal Sinkron';
    }
    if (status === 'synced') {
      return 'Data terbaru';
    }
    return null;
  });

  readonly syncTooltip = computed(() => {
    const syncedAt = this.lastSyncedAt();
    if (!syncedAt) {
      return 'Status sinkronisasi data FinPulse';
    }
    return `Terakhir diperbarui: ${formatDateTimeId(syncedAt)}`;
  });

  readonly userInitials = computed(() => {
    const fullName = this.currentUser()?.fullName?.trim();
    if (!fullName) {
      return 'FP';
    }
    const parts = fullName.split(/\s+/).filter(Boolean);
    if (parts.length === 1) {
      return parts[0].slice(0, 2).toUpperCase();
    }
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  });

  ngOnInit(): void {
    this.routeAnimationState.set(this.router.url.split('?')[0] || 'root');
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((event) => {
        this.routeAnimationState.set(event.urlAfterRedirects.split('?')[0] || 'root');
      });

    this.alertService
      .loadAlerts()
      .pipe(
        catchError(() => EMPTY),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe();
  }

  toggleMobileMenu(): void {
    this.isMobileMenuOpen.update((open) => !open);
  }

  closeMobileMenu(): void {
    this.isMobileMenuOpen.set(false);
  }

  onLogout(): void {
    if (this.isLoggingOut()) {
      return;
    }
    this.isLoggingOut.set(true);
    this.authService
      .logout()
      .pipe(finalize(() => this.isLoggingOut.set(false)))
      .subscribe(() => {
        this.alertService.reset();
        this.syncStatusService.reset();
        this.toastService.info('Berhasil keluar');
        void this.router.navigate(['/login']);
      });
  }
}
