import { CommonModule } from '@angular/common';
import { Component, computed, DestroyRef, HostListener, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, of, Subject } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, finalize, map, switchMap, tap } from 'rxjs/operators';
import { CategoryDto, TransactionType } from '../../core/models/category.model';
import {
  SortDirection,
  TransactionDto,
  TransactionFilterParams,
  TransactionPageResponse,
  TransactionSortField,
} from '../../core/models/transaction.model';
import { AlertService } from '../../core/services/alert.service';
import { CategoryService } from '../../core/services/category.service';
import { ToastService } from '../../core/services/toast.service';
import { TransactionService } from '../../core/services/transaction.service';
import { extractApiError, isUnauthorizedError } from '../../core/utils/error.util';
import { modalPopIn, springEnter, staggerList } from '../../shared/animations';
import {
  AppSelectComponent,
  AppSelectOption,
} from '../../shared/components/app-select/app-select.component';
import { SpinnerComponent } from '../../shared/components/spinner/spinner.component';
import {
  formatDateId,
  formatIdr,
  sanitizeHexColor,
  sanitizeHttpUrl,
} from '../../shared/utils/format.util';
import { TransactionFormModalComponent } from './transaction-form-modal.component';

export type TransactionTypeFilter = 'ALL' | TransactionType;

interface SortOption {
  value: string;
  label: string;
  sortBy: TransactionSortField;
  sortDir: SortDirection;
}

@Component({
  selector: 'app-transactions',
  standalone: true,
  imports: [CommonModule, SpinnerComponent, TransactionFormModalComponent, AppSelectComponent],
  templateUrl: './transactions.component.html',
  animations: [springEnter, staggerList, modalPopIn],
})
export class TransactionsComponent implements OnInit {
  private readonly transactionService = inject(TransactionService);
  private readonly categoryService = inject(CategoryService);
  private readonly alertService = inject(AlertService);
  private readonly toastService = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly loadTrigger$ = new Subject<void>();
  private readonly searchInput$ = new Subject<string>();

  readonly categories = signal<CategoryDto[]>([]);
  readonly transactions = signal<TransactionDto[]>([]);
  readonly isLoading = signal<boolean>(true);
  readonly errorMessage = signal<string | null>(null);

  // Filters
  readonly startDate = signal<string>('');
  readonly endDate = signal<string>('');
  readonly selectedType = signal<TransactionTypeFilter>('ALL');
  readonly selectedCategoryId = signal<string>('');
  readonly searchInput = signal<string>('');
  readonly activeSearch = signal<string>('');
  readonly selectedSort = signal<string>('transactionDate:DESC');

  // Pagination
  readonly page = signal<number>(0);
  readonly size = signal<number>(10);
  readonly totalElements = signal<number>(0);
  readonly totalPages = signal<number>(0);
  readonly isFirstPage = signal<boolean>(true);
  readonly isLastPage = signal<boolean>(true);

  // Modals
  readonly isFormModalOpen = signal<boolean>(false);
  readonly editingTransaction = signal<TransactionDto | null>(null);
  readonly deletingTransaction = signal<TransactionDto | null>(null);
  readonly isDeleting = signal<boolean>(false);
  readonly previewReceiptTx = signal<TransactionDto | null>(null);

  readonly sizeOptions = [10, 20, 50];
  readonly sizeSelectOptions: ReadonlyArray<AppSelectOption<number>> = this.sizeOptions.map(
    (s) => ({ value: s, label: String(s) })
  );
  readonly typeSelectOptions: ReadonlyArray<AppSelectOption<string>> = [
    { value: 'ALL', label: 'Semua' },
    { value: 'INCOME', label: 'Pemasukan' },
    { value: 'EXPENSE', label: 'Pengeluaran' },
  ];
  readonly sortOptions: SortOption[] = [
    { value: 'transactionDate:DESC', label: 'Tanggal Terbaru', sortBy: 'transactionDate', sortDir: 'DESC' },
    { value: 'transactionDate:ASC', label: 'Tanggal Terlama', sortBy: 'transactionDate', sortDir: 'ASC' },
    { value: 'amount:DESC', label: 'Nominal Tertinggi', sortBy: 'amount', sortDir: 'DESC' },
    { value: 'amount:ASC', label: 'Nominal Terendah', sortBy: 'amount', sortDir: 'ASC' },
  ];
  readonly sortSelectOptions: ReadonlyArray<AppSelectOption<string>> = this.sortOptions.map(
    (opt) => ({ value: opt.value, label: opt.label })
  );

  readonly skeletonRows = [1, 2, 3, 4, 5];

  readonly dateRangeError = computed(() => {
    const start = this.startDate();
    const end = this.endDate();
    if (start && end && start > end) {
      return 'Tanggal mulai tidak boleh melebihi tanggal akhir.';
    }
    return null;
  });

  readonly filterCategories = computed(() => {
    const type = this.selectedType();
    if (type === 'ALL') {
      return this.categories();
    }
    return this.categories().filter((cat) => cat.type === type);
  });

  readonly categorySelectOptions = computed<ReadonlyArray<AppSelectOption<string>>>(() => [
    { value: '', label: 'Semua Kategori' },
    ...this.filterCategories().map((cat) => ({ value: cat.id, label: cat.name })),
  ]);

  readonly hasActiveFilters = computed(() =>
    Boolean(
      this.startDate() ||
        this.endDate() ||
        this.selectedType() !== 'ALL' ||
        this.selectedCategoryId() ||
        this.searchInput().trim().length > 0
    )
  );

  readonly displayRangeStart = computed(() => {
    if (this.totalElements() === 0) {
      return 0;
    }
    return this.page() * this.size() + 1;
  });

  readonly displayRangeEnd = computed(() => {
    if (this.totalElements() === 0) {
      return 0;
    }
    return Math.min(this.totalElements(), (this.page() + 1) * this.size());
  });

  readonly displayTotalPages = computed(() => Math.max(1, this.totalPages()));

  readonly formatIdr = formatIdr;
  readonly formatDateId = formatDateId;

  constructor() {
    this.searchInput$
      .pipe(
        debounceTime(300),
        map((term) => term.trim().slice(0, 100)),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((trimmed) => {
        if (trimmed === this.activeSearch()) {
          return;
        }
        this.activeSearch.set(trimmed);
        this.page.set(0);
        this.loadTransactions();
      });

    this.loadTrigger$
      .pipe(
        tap(() => {
          this.isLoading.set(true);
          this.errorMessage.set(null);
        }),
        switchMap(() =>
          this.transactionService.listTransactions(this.buildFilterParams()).pipe(
            catchError((error: unknown) => {
              const parsed = extractApiError(error, 'Gagal memuat daftar transaksi.');
              this.errorMessage.set(parsed.message);
              if (!isUnauthorizedError(error)) {
                this.toastService.error('Gagal memuat data');
              }
              return of<TransactionPageResponse | null>(null);
            })
          )
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((response) => {
        if (response !== null) {
          this.transactions.set(response.content);
          this.page.set(response.page);
          this.size.set(response.size);
          this.totalElements.set(response.totalElements);
          this.totalPages.set(response.totalPages);
          this.isFirstPage.set(response.first);
          this.isLastPage.set(response.last);
        }
        this.isLoading.set(false);
      });
  }

  ngOnInit(): void {
    this.loadCategories();
    this.loadTransactions();
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.previewReceiptTx()) {
      this.closeReceiptPreview();
    } else if (this.deletingTransaction() && !this.isDeleting()) {
      this.cancelDelete();
    }
  }

  loadCategories(): void {
    this.categoryService
      .getCategories()
      .pipe(
        catchError(() => of<CategoryDto[]>([])),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((cats) => this.categories.set(cats));
  }

  loadTransactions(): void {
    if (this.dateRangeError()) {
      return;
    }
    this.loadTrigger$.next();
  }

  onStartDateChange(value: string): void {
    this.startDate.set(value);
    if (!this.dateRangeError()) {
      this.page.set(0);
      this.loadTransactions();
    }
  }

  onEndDateChange(value: string): void {
    this.endDate.set(value);
    if (!this.dateRangeError()) {
      this.page.set(0);
      this.loadTransactions();
    }
  }

  onTypeChange(value: string): void {
    const nextType: TransactionTypeFilter =
      value === 'INCOME' || value === 'EXPENSE' ? value : 'ALL';
    this.selectedType.set(nextType);

    if (nextType !== 'ALL' && this.selectedCategoryId()) {
      const stillMatches = this.categories().some(
        (cat) => cat.id === this.selectedCategoryId() && cat.type === nextType
      );
      if (!stillMatches) {
        this.selectedCategoryId.set('');
      }
    }

    this.page.set(0);
    this.loadTransactions();
  }

  onCategoryChange(value: string): void {
    this.selectedCategoryId.set(value);
    this.page.set(0);
    this.loadTransactions();
  }

  onSearchInput(value: string): void {
    const bounded = value.slice(0, 100);
    this.searchInput.set(bounded);
    this.searchInput$.next(bounded);
  }

  onSortChange(value: string): void {
    this.selectedSort.set(value);
    this.page.set(0);
    this.loadTransactions();
  }

  resetFilters(): void {
    this.startDate.set('');
    this.endDate.set('');
    this.selectedType.set('ALL');
    this.selectedCategoryId.set('');
    this.searchInput.set('');
    this.activeSearch.set('');
    this.searchInput$.next('');
    this.selectedSort.set('transactionDate:DESC');
    this.page.set(0);
    this.loadTransactions();
  }

  onPageSizeChange(value: string | number): void {
    const parsedSize = Number(value);
    if (!this.sizeOptions.includes(parsedSize)) {
      return;
    }
    this.size.set(parsedSize);
    this.page.set(0);
    this.loadTransactions();
  }

  goToPreviousPage(): void {
    if (this.isFirstPage() || this.page() <= 0 || this.isLoading()) {
      return;
    }
    this.page.update((p) => p - 1);
    this.loadTransactions();
  }

  goToNextPage(): void {
    if (this.isLastPage() || this.page() + 1 >= this.totalPages() || this.isLoading()) {
      return;
    }
    this.page.update((p) => p + 1);
    this.loadTransactions();
  }

  openCreateModal(): void {
    this.editingTransaction.set(null);
    this.isFormModalOpen.set(true);
  }

  openEditModal(tx: TransactionDto): void {
    this.editingTransaction.set(tx);
    this.isFormModalOpen.set(true);
  }

  closeFormModal(): void {
    this.isFormModalOpen.set(false);
    this.editingTransaction.set(null);
  }

  onTransactionSaved(savedTx?: TransactionDto): void {
    this.closeFormModal();
    this.loadTransactions();
    if (!savedTx || savedTx.type === 'EXPENSE') {
      this.alertService
        .loadAlerts()
        .pipe(
          catchError(() => EMPTY),
          takeUntilDestroyed(this.destroyRef)
        )
        .subscribe();
    }
  }

  openDeleteConfirm(tx: TransactionDto): void {
    this.deletingTransaction.set(tx);
  }

  cancelDelete(): void {
    if (this.isDeleting()) {
      return;
    }
    this.deletingTransaction.set(null);
  }

  confirmDelete(): void {
    const tx = this.deletingTransaction();
    if (!tx || this.isDeleting()) {
      return;
    }

    this.isDeleting.set(true);
    this.transactionService
      .deleteTransaction(tx.id)
      .pipe(
        finalize(() => this.isDeleting.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          this.deletingTransaction.set(null);
          this.toastService.success('Berhasil dihapus');
          if (this.transactions().length === 1 && this.page() > 0) {
            this.page.update((p) => p - 1);
          }
          this.loadTransactions();
        },
        error: (error: unknown) => {
          if (!isUnauthorizedError(error)) {
            this.toastService.error('Gagal menghapus');
          }
        },
      });
  }

  getSafeReceiptUrl(url: string | null | undefined): string | null {
    return sanitizeHttpUrl(url);
  }

  openReceiptPreview(tx: TransactionDto): void {
    const safeUrl = sanitizeHttpUrl(tx.receiptUrl);
    if (!safeUrl) {
      return;
    }
    this.previewReceiptTx.set({ ...tx, receiptUrl: safeUrl });
  }

  closeReceiptPreview(): void {
    this.previewReceiptTx.set(null);
  }

  getCategoryAccent(tx: TransactionDto): string {
    return sanitizeHexColor(tx.categoryColor, tx.type === 'INCOME' ? '#10B981' : '#EF4444');
  }

  private buildFilterParams(): TransactionFilterParams {
    const sortOption =
      this.sortOptions.find((opt) => opt.value === this.selectedSort()) ?? this.sortOptions[0];

    const params: TransactionFilterParams = {
      page: this.page(),
      size: this.size(),
      sortBy: sortOption.sortBy,
      sortDir: sortOption.sortDir,
    };

    if (this.selectedType() !== 'ALL') {
      params.type = this.selectedType() as TransactionType;
    }
    if (this.selectedCategoryId()) {
      params.categoryId = this.selectedCategoryId();
    }
    if (this.startDate()) {
      params.startDate = this.startDate();
    }
    if (this.endDate()) {
      params.endDate = this.endDate();
    }
    if (this.activeSearch()) {
      params.search = this.activeSearch();
    }

    return params;
  }
}
