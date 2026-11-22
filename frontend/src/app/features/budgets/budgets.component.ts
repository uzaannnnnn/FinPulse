import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, HostListener, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, of, Subject } from 'rxjs';
import { catchError, finalize, switchMap, tap } from 'rxjs/operators';
import { BudgetDto } from '../../core/models/budget.model';
import { CategoryDto } from '../../core/models/category.model';
import { AlertService } from '../../core/services/alert.service';
import { BudgetService } from '../../core/services/budget.service';
import { CategoryService } from '../../core/services/category.service';
import { ToastService } from '../../core/services/toast.service';
import { extractApiError } from '../../core/utils/error.util';
import { modalPopIn, springEnter, staggerList } from '../../shared/animations';
import {
  AppSelectComponent,
  AppSelectOption,
} from '../../shared/components/app-select/app-select.component';
import { SpinnerComponent } from '../../shared/components/spinner/spinner.component';
import { CountUpDirective } from '../../shared/directives/count-up.directive';
import {
  formatIdr,
  getMonthName,
  getYearOptions,
  MONTH_OPTIONS,
  sanitizeHexColor,
} from '../../shared/utils/format.util';
import { BudgetFormModalComponent } from './budget-form-modal.component';

@Component({
  selector: 'app-budgets',
  standalone: true,
  imports: [
    CommonModule,
    SpinnerComponent,
    BudgetFormModalComponent,
    CountUpDirective,
    AppSelectComponent,
  ],
  templateUrl: './budgets.component.html',
  animations: [springEnter, staggerList, modalPopIn],
})
export class BudgetsComponent implements OnInit {
  private readonly budgetService = inject(BudgetService);
  private readonly categoryService = inject(CategoryService);
  private readonly alertService = inject(AlertService);
  private readonly toastService = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly now = new Date();
  private readonly loadTrigger$ = new Subject<{ month: number; year: number }>();

  readonly monthOptions = MONTH_OPTIONS;
  readonly yearOptions = getYearOptions(this.now.getFullYear());
  readonly yearSelectOptions: ReadonlyArray<AppSelectOption<number>> = this.yearOptions.map(
    (y) => ({ value: y, label: String(y) })
  );

  readonly selectedMonth = signal<number>(this.now.getMonth() + 1);
  readonly selectedYear = signal<number>(this.now.getFullYear());

  readonly budgets = signal<BudgetDto[]>([]);
  readonly expenseCategories = signal<CategoryDto[]>([]);
  readonly isLoading = signal<boolean>(true);
  readonly errorMessage = signal<string | null>(null);

  // Modals
  readonly isFormModalOpen = signal<boolean>(false);
  readonly editingBudget = signal<BudgetDto | null>(null);
  readonly deletingBudget = signal<BudgetDto | null>(null);
  readonly isDeleting = signal<boolean>(false);

  readonly skeletonCards = [1, 2, 3];

  readonly selectedPeriodLabel = computed(
    () => `${getMonthName(this.selectedMonth())} ${this.selectedYear()}`
  );

  readonly existingCategoryIds = computed(() =>
    this.budgets().map((budget) => budget.categoryId)
  );

  readonly totalLimit = computed(() =>
    this.budgets().reduce((sum, item) => sum + item.amountLimit, 0)
  );

  readonly totalSpent = computed(() =>
    this.budgets().reduce((sum, item) => sum + item.spentAmount, 0)
  );

  readonly totalRemaining = computed(() => this.totalLimit() - this.totalSpent());

  readonly overallUtilization = computed(() => {
    const limit = this.totalLimit();
    if (limit <= 0) {
      return 0;
    }
    return (this.totalSpent() / limit) * 100;
  });

  readonly formatIdr = formatIdr;

  constructor() {
    this.loadTrigger$
      .pipe(
        tap(() => {
          this.isLoading.set(true);
          this.errorMessage.set(null);
        }),
        switchMap(({ month, year }) =>
          this.budgetService.listBudgets(month, year).pipe(
            catchError((error: unknown) => {
              const parsed = extractApiError(error, 'Gagal memuat daftar anggaran bulanan.');
              this.errorMessage.set(parsed.message);
              const isUnauthorized = error instanceof HttpErrorResponse && error.status === 401;
              if (!isUnauthorized) {
                this.toastService.error('Gagal memuat data');
              }
              return of<BudgetDto[] | null>(null);
            })
          )
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((data) => {
        if (data !== null) {
          this.budgets.set(data);
        }
        this.isLoading.set(false);
      });
  }

  ngOnInit(): void {
    this.loadExpenseCategories();
    this.loadBudgets();
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.deletingBudget() && !this.isDeleting()) {
      this.cancelDelete();
    }
  }

  loadExpenseCategories(): void {
    this.categoryService
      .getCategories('EXPENSE')
      .pipe(
        catchError(() => of<CategoryDto[]>([])),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((cats) => this.expenseCategories.set(cats));
  }

  loadBudgets(): void {
    this.loadTrigger$.next({
      month: this.selectedMonth(),
      year: this.selectedYear(),
    });
  }

  onMonthChange(value: string | number): void {
    const parsed = Number(value);
    if (!Number.isInteger(parsed) || parsed < 1 || parsed > 12) {
      return;
    }
    this.selectedMonth.set(parsed);
    this.loadBudgets();
  }

  onYearChange(value: string | number): void {
    const parsed = Number(value);
    if (!Number.isInteger(parsed) || parsed < 2020 || parsed > 2100) {
      return;
    }
    this.selectedYear.set(parsed);
    this.loadBudgets();
  }

  openCreateModal(): void {
    this.editingBudget.set(null);
    this.isFormModalOpen.set(true);
  }

  openEditModal(budget: BudgetDto): void {
    this.editingBudget.set(budget);
    this.isFormModalOpen.set(true);
  }

  closeFormModal(): void {
    this.isFormModalOpen.set(false);
    this.editingBudget.set(null);
  }

  onBudgetSaved(savedBudget: BudgetDto): void {
    this.closeFormModal();
    if (
      savedBudget.month !== this.selectedMonth() ||
      savedBudget.year !== this.selectedYear()
    ) {
      this.selectedMonth.set(savedBudget.month);
      this.selectedYear.set(savedBudget.year);
    }
    this.loadBudgets();
    this.alertService
      .loadAlerts()
      .pipe(
        catchError(() => EMPTY),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe();
  }

  openDeleteConfirm(budget: BudgetDto): void {
    this.deletingBudget.set(budget);
  }

  cancelDelete(): void {
    if (this.isDeleting()) {
      return;
    }
    this.deletingBudget.set(null);
  }

  confirmDelete(): void {
    const target = this.deletingBudget();
    if (!target || this.isDeleting()) {
      return;
    }

    this.isDeleting.set(true);
    this.budgetService
      .deleteBudget(target.id)
      .pipe(
        finalize(() => this.isDeleting.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          this.deletingBudget.set(null);
          this.toastService.success('Berhasil dihapus');
          this.loadBudgets();
        },
        error: (error: unknown) => {
          const isUnauthorized = error instanceof HttpErrorResponse && error.status === 401;
          if (!isUnauthorized) {
            this.toastService.error('Gagal menghapus');
          }
        },
      });
  }

  getBudgetPeriodLabel(budget: BudgetDto): string {
    return `${getMonthName(budget.month)} ${budget.year}`;
  }

  getCategoryAccent(budget: BudgetDto): string {
    return sanitizeHexColor(budget.categoryColor, '#0F172A');
  }

  getClampedPercentage(percentage: number): number {
    if (!Number.isFinite(percentage)) {
      return 0;
    }
    return Math.min(100, Math.max(0, percentage));
  }

  formatPercent(percentage: number): string {
    if (!Number.isFinite(percentage)) {
      return '0.0%';
    }
    return `${percentage.toFixed(1)}%`;
  }

  getStatusLabel(budget: BudgetDto): 'Aman' | 'Mendekati Batas' | 'Melebihi Batas' {
    if (budget.isOverBudget || budget.utilizationPercentage >= 100) {
      return 'Melebihi Batas';
    }
    if (budget.utilizationPercentage >= 80) {
      return 'Mendekati Batas';
    }
    return 'Aman';
  }

  getStatusBadgeClass(budget: BudgetDto): string {
    if (budget.isOverBudget || budget.utilizationPercentage >= 100) {
      return 'border-2 border-fp-ink bg-[#FF4D6D] text-white shadow-brutal-sm';
    }
    if (budget.utilizationPercentage >= 80) {
      return 'border-2 border-fp-ink bg-[#FACC15] text-fp-ink shadow-brutal-sm';
    }
    return 'border-2 border-fp-ink bg-[#00D68F] text-fp-ink shadow-brutal-sm';
  }

  getProgressBarClass(budget: BudgetDto): string {
    if (budget.isOverBudget || budget.utilizationPercentage >= 100) {
      return 'bg-rose-500';
    }
    if (budget.utilizationPercentage >= 80) {
      return 'bg-amber-500';
    }
    return 'bg-emerald-500';
  }
}
