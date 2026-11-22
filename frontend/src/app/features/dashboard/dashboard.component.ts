import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  OnDestroy,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import {
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  DoughnutController,
  Legend,
  LinearScale,
  Tooltip,
} from 'chart.js';
import { of, Subject } from 'rxjs';
import { catchError, finalize, switchMap, tap } from 'rxjs/operators';
import { BudgetDto } from '../../core/models/budget.model';
import {
  CategoryBreakdownDto,
  DashboardSummaryDto,
  MonthlyCashflowDto,
} from '../../core/models/dashboard.model';
import { ReportFormat } from '../../core/models/report.model';
import { AlertService } from '../../core/services/alert.service';
import { AuthService } from '../../core/services/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { ReportService } from '../../core/services/report.service';
import { SyncStatusService } from '../../core/services/sync-status.service';
import { ToastService } from '../../core/services/toast.service';
import { extractApiError, isUnauthorizedError } from '../../core/utils/error.util';
import { springEnter, staggerList } from '../../shared/animations';
import {
  AppSelectComponent,
  AppSelectOption,
} from '../../shared/components/app-select/app-select.component';
import { HeartbeatWidgetComponent } from '../../shared/components/heartbeat-widget/heartbeat-widget.component';
import { SpinnerComponent } from '../../shared/components/spinner/spinner.component';
import { CountUpDirective } from '../../shared/directives/count-up.directive';
import { Tilt3dDirective } from '../../shared/directives/tilt-3d.directive';
import {
  clampPercentage,
  formatDateId,
  formatDateTimeId,
  formatIdr,
  formatPercent,
  getMonthName,
  getYearOptions,
  MONTH_OPTIONS,
  sanitizeHexColor,
  sanitizeHttpUrl,
} from '../../shared/utils/format.util';

Chart.register(
  BarController,
  BarElement,
  DoughnutController,
  ArcElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Legend
);

const FALLBACK_CHART_PALETTE = [
  '#0F172A',
  '#10B981',
  '#F59E0B',
  '#EF4444',
  '#3B82F6',
  '#8B5CF6',
  '#EC4899',
  '#14B8A6',
];

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    SpinnerComponent,
    HeartbeatWidgetComponent,
    CountUpDirective,
    Tilt3dDirective,
    AppSelectComponent,
  ],
  animations: [springEnter, staggerList],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit, OnDestroy {
  private readonly authService = inject(AuthService);
  private readonly dashboardService = inject(DashboardService);
  private readonly syncStatusService = inject(SyncStatusService);
  private readonly alertService = inject(AlertService);
  private readonly reportService = inject(ReportService);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private readonly now = new Date();
  private readonly loadTrigger$ = new Subject<{ month: number; year: number }>();

  readonly cashflowCanvas = viewChild<ElementRef<HTMLCanvasElement>>('cashflowCanvas');
  readonly categoryCanvas = viewChild<ElementRef<HTMLCanvasElement>>('categoryCanvas');

  private cashflowChart: Chart<'bar'> | null = null;
  private categoryChart: Chart<'doughnut'> | null = null;
  private categoryTooltipEl: HTMLDivElement | null = null;

  readonly currentUser = this.authService.currentUser;
  readonly syncStatus = this.syncStatusService.status;
  readonly syncSource = this.syncStatusService.source;
  readonly lastSyncedAt = this.syncStatusService.lastSyncedAt;
  readonly unreadAlertsCount = this.alertService.unreadCount;
  readonly latestUnreadAlert = computed(
    () => this.alertService.alerts().find((item) => !item.isRead) ?? null
  );

  readonly monthOptions = MONTH_OPTIONS;
  readonly yearOptions = getYearOptions(this.now.getFullYear());
  readonly yearSelectOptions: ReadonlyArray<AppSelectOption<number>> = this.yearOptions.map(
    (y) => ({ value: y, label: String(y) })
  );

  readonly selectedMonth = signal<number>(this.now.getMonth() + 1);
  readonly selectedYear = signal<number>(this.now.getFullYear());
  readonly summary = signal<DashboardSummaryDto | null>(null);
  readonly isLoading = signal<boolean>(true);
  readonly errorMessage = signal<string | null>(null);
  readonly isAlertBannerDismissed = signal<boolean>(false);
  readonly exportingFormat = signal<ReportFormat | null>(null);

  readonly selectedPeriodLabel = computed(
    () => `${getMonthName(this.selectedMonth())} ${this.selectedYear()}`
  );

  readonly hasCashflowData = computed(() => {
    const series = this.summary()?.cashflowSeries ?? [];
    return series.some((item) => item.totalIncome > 0 || item.totalExpense > 0);
  });

  readonly hasExpenseBreakdown = computed(() => {
    const items = this.summary()?.expenseByCategory ?? [];
    return items.some((item) => item.amount > 0);
  });

  readonly recentTransactions = computed(() =>
    (this.summary()?.recentTransactions ?? []).slice(0, 5)
  );

  readonly overBudgetCount = computed(
    () =>
      (this.summary()?.budgetProgress ?? []).filter(
        (b) => b.isOverBudget || b.utilizationPercentage >= 100
      ).length
  );

  readonly formatIdr = formatIdr;
  readonly formatDateId = formatDateId;
  readonly formatDateTimeId = formatDateTimeId;

  constructor() {
    this.loadTrigger$
      .pipe(
        tap(() => {
          this.isLoading.set(true);
          this.errorMessage.set(null);
          this.syncStatusService.markSyncing();
        }),
        switchMap(({ month, year }) =>
          this.dashboardService.getSummary(month, year, 6).pipe(
            catchError((error: unknown) => {
              const parsed = extractApiError(error, 'Gagal memuat ringkasan dashboard.');
              this.errorMessage.set(parsed.message);
              this.syncStatusService.markError();
              if (!isUnauthorizedError(error)) {
                this.toastService.error('Gagal memuat data');
              }
              return of<DashboardSummaryDto | null>(null);
            })
          )
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((data) => {
        if (data !== null) {
          this.summary.set(data);
          this.syncStatusService.markSynced(
            data.fromCache ? 'CACHE' : 'DATABASE',
            data.generatedAt
          );
        }
        this.isLoading.set(false);
      });

    effect(() => {
      const currentSummary = this.summary();
      const loading = this.isLoading();
      const error = this.errorMessage();
      const cashflowRef = this.cashflowCanvas();
      const categoryRef = this.categoryCanvas();

      if (loading || !currentSummary || error) {
        this.destroyCharts();
        return;
      }

      this.renderCashflowChart(cashflowRef?.nativeElement ?? null, currentSummary.cashflowSeries);
      this.renderCategoryChart(categoryRef?.nativeElement ?? null, currentSummary.expenseByCategory);
    });
  }

  ngOnInit(): void {
    this.loadSummary();
  }

  ngOnDestroy(): void {
    this.destroyCharts();
  }

  onMonthChange(value: string | number): void {
    const parsedMonth = Number(value);
    if (!Number.isInteger(parsedMonth) || parsedMonth < 1 || parsedMonth > 12) {
      return;
    }
    this.selectedMonth.set(parsedMonth);
    this.loadSummary();
  }

  onYearChange(value: string | number): void {
    const parsedYear = Number(value);
    if (!Number.isInteger(parsedYear) || parsedYear < 2020 || parsedYear > 2100) {
      return;
    }
    this.selectedYear.set(parsedYear);
    this.loadSummary();
  }

  loadSummary(): void {
    this.loadTrigger$.next({
      month: this.selectedMonth(),
      year: this.selectedYear(),
    });
  }

  dismissAlertBanner(): void {
    this.isAlertBannerDismissed.set(true);
  }

  requestQuickExport(format: ReportFormat): void {
    if (this.exportingFormat() !== null) {
      return;
    }

    const month = this.selectedMonth();
    const year = this.selectedYear();
    this.exportingFormat.set(format);

    this.reportService
      .requestExport({ month, year, format })
      .pipe(
        finalize(() => this.exportingFormat.set(null)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          this.toastService.success('Ekspor dijadwalkan');
          void this.router.navigate(['/reports']);
        },
        error: (error: unknown) => {
          const isServiceUnavailable =
            error instanceof HttpErrorResponse && error.status === 503;
          if (!isUnauthorizedError(error)) {
            this.toastService.error(
              isServiceUnavailable ? 'Antrean tidak tersedia' : 'Ekspor gagal'
            );
          }
        },
      });
  }

  hasSafeReceipt(url: string | null | undefined): boolean {
    return sanitizeHttpUrl(url) !== null;
  }

  getCategoryColor(color: string | null | undefined, index = 0): string {
    return sanitizeHexColor(color, FALLBACK_CHART_PALETTE[index % FALLBACK_CHART_PALETTE.length]);
  }

  getClampedPercentage(percentage: number): number {
    return clampPercentage(percentage);
  }

  formatPercent(percentage: number): string {
    return formatPercent(percentage);
  }

  getBudgetBarClass(budget: BudgetDto): string {
    if (budget.isOverBudget || budget.utilizationPercentage >= 100) {
      return 'bg-rose-500';
    }
    if (budget.utilizationPercentage >= 80) {
      return 'bg-amber-500';
    }
    return 'bg-emerald-500';
  }

  getBudgetStatusLabel(budget: BudgetDto): string | null {
    if (budget.isOverBudget || budget.utilizationPercentage >= 100) {
      return 'Over Budget';
    }
    if (budget.utilizationPercentage >= 80) {
      return 'Mendekati Batas';
    }
    return null;
  }

  getBudgetStatusBadgeClass(budget: BudgetDto): string {
    if (budget.isOverBudget || budget.utilizationPercentage >= 100) {
      return 'bg-rose-50 text-rose-700 ring-1 ring-rose-200';
    }
    return 'bg-amber-50 text-amber-700 ring-1 ring-amber-200';
  }

  private renderCashflowChart(
    canvas: HTMLCanvasElement | null,
    series: MonthlyCashflowDto[]
  ): void {
    this.destroyCashflowChart();

    if (!canvas || series.length === 0) {
      return;
    }

    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return;
    }

    try {
      this.cashflowChart = new Chart<'bar'>(ctx, {
        type: 'bar',
        data: {
          labels: series.map((item) => item.label),
          datasets: [
            {
              label: 'Pemasukan',
              data: series.map((item) => item.totalIncome),
              backgroundColor: '#00D68F',
              hoverBackgroundColor: '#10B981',
              borderColor: '#0F172A',
              borderWidth: 2,
              borderRadius: 6,
              borderSkipped: false,
              maxBarThickness: 30,
            },
            {
              label: 'Pengeluaran',
              data: series.map((item) => item.totalExpense),
              backgroundColor: '#FF4D6D',
              hoverBackgroundColor: '#F43F5E',
              borderColor: '#0F172A',
              borderWidth: 2,
              borderRadius: 6,
              borderSkipped: false,
              maxBarThickness: 30,
            },
          ],
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          interaction: {
            mode: 'index',
            intersect: false,
          },
          plugins: {
            legend: {
              position: 'top',
              align: 'end',
              labels: {
                usePointStyle: true,
                pointStyle: 'circle',
                boxWidth: 8,
                boxHeight: 8,
                color: '#475569',
                font: {
                  size: 12,
                  weight: 600,
                },
              },
            },
            tooltip: {
              backgroundColor: '#0F172A',
              titleColor: '#F8FAFC',
              bodyColor: '#E2E8F0',
              padding: 12,
              cornerRadius: 10,
              callbacks: {
                label: (context) => {
                  const val = typeof context.parsed.y === 'number' ? context.parsed.y : 0;
                  return ` ${context.dataset.label}: ${formatIdr(val)}`;
                },
              },
            },
          },
          scales: {
            x: {
              grid: {
                display: false,
              },
              ticks: {
                color: '#64748B',
                font: {
                  size: 12,
                },
              },
            },
            y: {
              beginAtZero: true,
              border: {
                display: false,
              },
              grid: {
                color: '#F1F5F9',
              },
              ticks: {
                color: '#64748B',
                font: {
                  size: 11,
                },
                callback: (value) => this.formatCompactAxis(Number(value)),
              },
            },
          },
        },
      });
    } catch {
      this.cashflowChart = null;
    }
  }

  private renderCategoryChart(
    canvas: HTMLCanvasElement | null,
    items: CategoryBreakdownDto[]
  ): void {
    this.destroyCategoryChart();

    if (!canvas || items.length === 0) {
      return;
    }

    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return;
    }

    try {
      this.categoryChart = new Chart<'doughnut'>(ctx, {
        type: 'doughnut',
        data: {
          labels: items.map((item) => item.categoryName),
          datasets: [
            {
              data: items.map((item) => item.amount),
              backgroundColor: items.map((item, idx) =>
                this.getCategoryColor(item.categoryColor, idx)
              ),
              borderColor: '#0F172A',
              borderWidth: 2,
              hoverOffset: 8,
            },
          ],
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          layout: {
            padding: 16,
          },
          cutout: '68%',
          plugins: {
            legend: {
              display: false,
            },
            tooltip: {
              enabled: false,
              external: (context) => {
                this.renderExternalCategoryTooltip(canvas, context.tooltip, items);
              },
            },
          },
        },
      });
    } catch {
      this.categoryChart = null;
    }
  }

  private renderExternalCategoryTooltip(
    canvas: HTMLCanvasElement,
    tooltip: {
      opacity: number;
      caretX: number;
      caretY: number;
      dataPoints?: Array<{ dataIndex: number }>;
    },
    items: CategoryBreakdownDto[]
  ): void {
    const parent = canvas.parentElement;
    if (!parent) {
      return;
    }

    if (!this.categoryTooltipEl || !parent.contains(this.categoryTooltipEl)) {
      const el = document.createElement('div');
      el.setAttribute('data-testid', 'category-external-tooltip');
      el.className =
        'pointer-events-none absolute z-50 w-max max-w-[230px] rounded-xl border-2 border-[#0F172A] bg-[#0F172A] px-3 py-2 text-left text-[#FAF8F5] shadow-[3px_3px_0px_0px_#00D68F] transition-opacity duration-150';
      el.style.opacity = '0';
      parent.appendChild(el);
      this.categoryTooltipEl = el;
    }

    const tooltipEl = this.categoryTooltipEl;
    if (!tooltip || tooltip.opacity === 0) {
      tooltipEl.style.opacity = '0';
      return;
    }

    const dataIndex = tooltip.dataPoints?.[0]?.dataIndex ?? -1;
    const item = items[dataIndex];
    if (!item) {
      tooltipEl.style.opacity = '0';
      return;
    }

    const accent = this.getCategoryColor(item.categoryColor, dataIndex);
    tooltipEl.replaceChildren();

    const titleRow = document.createElement('div');
    titleRow.className = 'flex items-center gap-1.5 text-xs font-bold text-[#FAF8F5]';
    const dot = document.createElement('span');
    dot.className = 'inline-block h-2.5 w-2.5 shrink-0 rounded-full border border-white/40';
    dot.style.backgroundColor = accent;
    const nameSpan = document.createElement('span');
    nameSpan.className = 'truncate';
    nameSpan.textContent = item.categoryName;
    titleRow.appendChild(dot);
    titleRow.appendChild(nameSpan);

    const valueRow = document.createElement('div');
    valueRow.className = 'fp-num mt-0.5 text-[11px] font-bold text-[#00D68F]';
    valueRow.textContent = `${formatIdr(item.amount)} \u2022 ${item.percentage.toFixed(1)}%`;

    tooltipEl.appendChild(titleRow);
    tooltipEl.appendChild(valueRow);

    const parentWidth = parent.clientWidth || 180;
    const tooltipWidth = tooltipEl.offsetWidth || 170;
    const rawLeft = tooltip.caretX - tooltipWidth / 2;
    const minLeft = -16;
    const maxLeft = Math.max(minLeft, parentWidth - tooltipWidth + 16);
    const clampedLeft = Math.min(maxLeft, Math.max(minLeft, rawLeft));
    const topPos = Math.max(4, tooltip.caretY - (tooltipEl.offsetHeight || 48) - 10);

    tooltipEl.style.left = `${Math.round(clampedLeft)}px`;
    tooltipEl.style.top = `${Math.round(topPos)}px`;
    tooltipEl.style.opacity = '1';
  }

  private formatCompactAxis(value: number): string {
    const abs = Math.abs(value);
    if (abs >= 1_000_000_000) {
      return `Rp ${(value / 1_000_000_000).toFixed(1).replace(/\.0$/, '')} M`;
    }
    if (abs >= 1_000_000) {
      return `Rp ${(value / 1_000_000).toFixed(1).replace(/\.0$/, '')} jt`;
    }
    if (abs >= 1_000) {
      return `Rp ${(value / 1_000).toFixed(0)} rb`;
    }
    return `Rp ${value}`;
  }

  private destroyCashflowChart(): void {
    if (this.cashflowChart) {
      this.cashflowChart.destroy();
      this.cashflowChart = null;
    }
  }

  private destroyCategoryChart(): void {
    if (this.categoryChart) {
      this.categoryChart.destroy();
      this.categoryChart = null;
    }
    if (this.categoryTooltipEl) {
      this.categoryTooltipEl.remove();
      this.categoryTooltipEl = null;
    }
  }

  private destroyCharts(): void {
    this.destroyCashflowChart();
    this.destroyCategoryChart();
  }
}
