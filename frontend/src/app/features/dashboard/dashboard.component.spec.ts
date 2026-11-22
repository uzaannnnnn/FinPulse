import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, Router } from '@angular/router';
import { Chart } from 'chart.js';
import { environment } from '../../../environments/environment';
import { DashboardSummaryDto } from '../../core/models/dashboard.model';
import { AlertService } from '../../core/services/alert.service';
import { SyncStatusService } from '../../core/services/sync-status.service';
import { ToastService } from '../../core/services/toast.service';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  let httpMock: HttpTestingController;

  const mockSummary: DashboardSummaryDto = {
    month: 11,
    year: 2026,
    totalIncome: 12000000,
    totalExpense: 5400000,
    balance: 6600000,
    cumulativeBalance: 28500000,
    transactionCount: 18,
    cashflowSeries: [
      {
        year: 2026,
        month: 10,
        label: 'Okt 2026',
        totalIncome: 10000000,
        totalExpense: 4200000,
        net: 5800000,
      },
      {
        year: 2026,
        month: 11,
        label: 'Nov 2026',
        totalIncome: 12000000,
        totalExpense: 5400000,
        net: 6600000,
      },
    ],
    expenseByCategory: [
      {
        categoryId: 'cat-food',
        categoryName: 'Makanan & Minuman',
        categoryColor: '#F59E0B',
        categoryIcon: 'utensils',
        amount: 3200000,
        percentage: 59.26,
      },
      {
        categoryId: 'cat-transport',
        categoryName: 'Transportasi',
        categoryColor: '#3B82F6',
        categoryIcon: 'car',
        amount: 2200000,
        percentage: 40.74,
      },
    ],
    budgetProgress: [
      {
        id: 'b-1',
        categoryId: 'cat-food',
        categoryName: 'Makanan & Minuman',
        categoryColor: '#F59E0B',
        month: 11,
        year: 2026,
        amountLimit: 2500000,
        spentAmount: 2887500,
        remainingAmount: -387500,
        utilizationPercentage: 115.5,
        isOverBudget: true,
        createdAt: '2026-11-01T00:00:00Z',
        updatedAt: '2026-11-21T00:00:00Z',
      },
      {
        id: 'b-2',
        categoryId: 'cat-transport',
        categoryName: 'Transportasi',
        categoryColor: '#3B82F6',
        month: 11,
        year: 2026,
        amountLimit: 2500000,
        spentAmount: 2125000,
        remainingAmount: 375000,
        utilizationPercentage: 85.0,
        isOverBudget: false,
        createdAt: '2026-11-01T00:00:00Z',
        updatedAt: '2026-11-21T00:00:00Z',
      },
      {
        id: 'b-3',
        categoryId: 'cat-bills',
        categoryName: 'Tagihan',
        categoryColor: '#10B981',
        month: 11,
        year: 2026,
        amountLimit: 1000000,
        spentAmount: 500000,
        remainingAmount: 500000,
        utilizationPercentage: 50.0,
        isOverBudget: false,
        createdAt: '2026-11-01T00:00:00Z',
        updatedAt: '2026-11-21T00:00:00Z',
      },
    ],
    recentTransactions: [
      {
        id: 'tx-1',
        categoryId: 'cat-food',
        categoryName: 'Makanan & Minuman',
        categoryColor: '#F59E0B',
        type: 'EXPENSE',
        amount: 125000,
        description: 'Makan siang tim',
        transactionDate: '2026-11-21',
        receiptUrl: null,
        createdAt: '2026-11-21T10:00:00Z',
        updatedAt: '2026-11-21T10:00:00Z',
      },
    ],
    generatedAt: '2026-11-21T13:30:00Z',
    fromCache: true,
  };

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should load dashboard summary on init and render KPIs, budgets, and recent transactions', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/dashboard/summary`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('monthsBack')).toBe('6');
    req.flush(mockSummary);

    fixture.detectChanges();

    expect(component.isLoading()).toBeFalse();
    expect(component.summary()).toEqual(mockSummary);
    expect(component.overBudgetCount()).toBe(1);

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Rp 12.000.000');
    expect(text).toContain('Rp 5.400.000');
    expect(text).toContain('Rp 6.600.000');
    expect(text).toContain('Rp 28.500.000');
    expect(text).toContain('18 transaksi');
    expect(text).toContain('Over Budget');
    expect(text).toContain('115.5%');
    expect(text).toContain('Makan siang tim');

    // Dynamic progress bar classes
    expect(component.getBudgetBarClass(mockSummary.budgetProgress[0])).toBe('bg-rose-500');
    expect(component.getBudgetBarClass(mockSummary.budgetProgress[1])).toBe('bg-amber-500');
    expect(component.getBudgetBarClass(mockSummary.budgetProgress[2])).toBe('bg-emerald-500');

    // Clamped visual width
    expect(component.getClampedPercentage(115.5)).toBe(100);

    fixture.destroy();
  });

  it('should reload dashboard summary when month or year changes', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/dashboard/summary`)
      .flush(mockSummary);
    fixture.detectChanges();

    component.onMonthChange('5');
    const monthReq = httpMock.expectOne(
      (r) =>
        r.url === `${environment.apiUrl}/dashboard/summary` &&
        r.params.get('month') === '5' &&
        r.params.get('monthsBack') === '6'
    );
    monthReq.flush({ ...mockSummary, month: 5, expenseByCategory: [], budgetProgress: [], recentTransactions: [] });
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Belum Ada Pengeluaran');
    expect(text).toContain('Belum Ada Anggaran Diatur');
    expect(text).toContain('Belum Ada Transaksi');

    fixture.destroy();
  });

  it('should show error state on failure and allow retry', () => {
    const toastService = TestBed.inject(ToastService);
    const toastSpy = spyOn(toastService, 'error');

    const fixture = TestBed.createComponent(DashboardComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/dashboard/summary`)
      .flush(
        {
          timestamp: '2026-11-21T10:00:00Z',
          status: 500,
          error: 'Internal Server Error',
          message: 'Gagal menghitung ringkasan',
          path: '/api/dashboard/summary',
        },
        { status: 500, statusText: 'Internal Server Error' }
      );
    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Gagal menghitung ringkasan');
    expect(toastSpy).toHaveBeenCalledTimes(1);

    component.loadSummary();
    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/dashboard/summary`)
      .flush(mockSummary);
    fixture.detectChanges();

    expect(component.errorMessage()).toBeNull();
    expect(component.summary()?.totalIncome).toBe(12000000);

    fixture.destroy();
  });

  it('should not show duplicate error toast on 401 unauthorized response', () => {
    const toastService = TestBed.inject(ToastService);
    const toastSpy = spyOn(toastService, 'error');

    const fixture = TestBed.createComponent(DashboardComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/dashboard/summary`)
      .flush(
        {
          timestamp: '2026-11-21T10:00:00Z',
          status: 401,
          error: 'Unauthorized',
          message: 'Sesi kedaluwarsa',
          path: '/api/dashboard/summary',
        },
        { status: 401, statusText: 'Unauthorized' }
      );

    expect(component.errorMessage()).toBe('Sesi kedaluwarsa');
    expect(toastSpy).not.toHaveBeenCalled();

    fixture.destroy();
  });

  it('should render empty cashflow state when all cashflow months are zero and ignore year > 2100', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    const allZeroSummary: DashboardSummaryDto = {
      month: 11,
      year: 2026,
      totalIncome: 0,
      totalExpense: 0,
      balance: 0,
      cumulativeBalance: 0,
      transactionCount: 0,
      cashflowSeries: [
        { year: 2026, month: 6, label: 'Jun 2026', totalIncome: 0, totalExpense: 0, net: 0 },
        { year: 2026, month: 7, label: 'Jul 2026', totalIncome: 0, totalExpense: 0, net: 0 },
        { year: 2026, month: 8, label: 'Aug 2026', totalIncome: 0, totalExpense: 0, net: 0 },
        { year: 2026, month: 9, label: 'Sep 2026', totalIncome: 0, totalExpense: 0, net: 0 },
        { year: 2026, month: 10, label: 'Oct 2026', totalIncome: 0, totalExpense: 0, net: 0 },
        { year: 2026, month: 11, label: 'Nov 2026', totalIncome: 0, totalExpense: 0, net: 0 },
      ],
      expenseByCategory: [],
      budgetProgress: [],
      recentTransactions: [],
      generatedAt: '2026-11-21T13:30:00Z',
      fromCache: false,
    };

    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/dashboard/summary`)
      .flush(allZeroSummary);
    fixture.detectChanges();

    expect(component.hasCashflowData()).toBeFalse();
    expect(component.hasExpenseBreakdown()).toBeFalse();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Belum ada data arus kas');
    expect(text).toContain('Belum Ada Pengeluaran');
    expect(text).toContain('Belum Ada Anggaran Diatur');
    expect(text).toContain('Belum Ada Transaksi');

    // Out-of-range year > 2100 must be ignored (no HTTP request)
    const prevYear = component.selectedYear();
    component.onYearChange('2101');
    expect(component.selectedYear()).toBe(prevYear);
    httpMock.expectNone((r) => r.url === `${environment.apiUrl}/dashboard/summary`);

    fixture.destroy();
  });

  it('should destroy Chart.js instances when period reloads and on component destroy', () => {
    const destroySpy = spyOn(Chart.prototype, 'destroy').and.callThrough();

    const fixture = TestBed.createComponent(DashboardComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/dashboard/summary`)
      .flush(mockSummary);
    fixture.detectChanges();

    // Changing month triggers loading -> destroys previous bar + doughnut charts
    component.onMonthChange('10');
    fixture.detectChanges();
    expect(destroySpy.calls.count()).toBeGreaterThanOrEqual(2);

    const callsBeforeReload = destroySpy.calls.count();
    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/dashboard/summary` && r.params.get('month') === '10')
      .flush(mockSummary);
    fixture.detectChanges();

    // Destroying component must destroy newly rendered charts
    fixture.destroy();
    expect(destroySpy.calls.count()).toBeGreaterThanOrEqual(callsBeforeReload + 2);
  });

  it('should update SyncStatusService, render over-budget banner, and trigger quick PDF export', async () => {
    const syncService = TestBed.inject(SyncStatusService);
    const alertService = TestBed.inject(AlertService);
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);

    alertService.loadAlerts().subscribe();
    httpMock.expectOne(`${environment.apiUrl}/alerts`).flush({
      unreadCount: 1,
      alerts: [
        {
          id: 'alert-1',
          budgetId: 'b-1',
          categoryId: 'cat-food',
          categoryName: 'Makanan & Minuman',
          categoryColor: '#F59E0B',
          month: 11,
          year: 2026,
          amountLimit: 2500000,
          spentAmount: 2887500,
          utilizationPercentage: 115.5,
          message: 'Pengeluaran kategori Makanan & Minuman melebihi batas.',
          isRead: false,
          createdAt: '2026-11-21T10:00:00Z',
          updatedAt: '2026-11-21T10:00:00Z',
        },
      ],
    });

    const fixture = TestBed.createComponent(DashboardComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    expect(syncService.status()).toBe('syncing');

    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/dashboard/summary`)
      .flush(mockSummary);
    fixture.detectChanges();

    expect(syncService.status()).toBe('synced');
    expect(syncService.source()).toBe('CACHE');
    expect(syncService.lastSyncedAt()).toBe('2026-11-21T13:30:00Z');

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="dashboard-sync-badge"]')?.textContent).toContain('Data terbaru');
    expect(el.querySelector('[data-testid="over-budget-banner"]')?.textContent).toContain('1 Peringatan Over-Budget');

    component.dismissAlertBanner();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(el.querySelector('[data-testid="over-budget-banner"]')).toBeNull();

    component.requestQuickExport('PDF');
    const exportReq = httpMock.expectOne(`${environment.apiUrl}/reports/export`);
    expect(exportReq.request.method).toBe('POST');
    expect(exportReq.request.body.format).toBe('PDF');
    exportReq.flush(
      {
        id: 'rep-1',
        month: component.selectedMonth(),
        year: component.selectedYear(),
        format: 'PDF',
        status: 'PENDING',
        createdAt: '2026-11-21T13:31:00Z',
        updatedAt: '2026-11-21T13:31:00Z',
      },
      { status: 202, statusText: 'Accepted' }
    );

    expect(navigateSpy).toHaveBeenCalledWith(['/reports']);
    fixture.destroy();
  });
});
