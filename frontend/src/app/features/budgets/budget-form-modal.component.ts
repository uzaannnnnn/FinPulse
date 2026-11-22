import {
  Component,
  computed,
  DestroyRef,
  effect,
  HostListener,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { BudgetDto } from '../../core/models/budget.model';
import { CategoryDto } from '../../core/models/category.model';
import { BudgetService } from '../../core/services/budget.service';
import { ToastService } from '../../core/services/toast.service';
import { extractApiError, isUnauthorizedError } from '../../core/utils/error.util';
import { positiveDecimalValidator } from '../../core/utils/form-validators.util';
import { modalPopIn } from '../../shared/animations';
import {
  AppSelectComponent,
  AppSelectOption,
} from '../../shared/components/app-select/app-select.component';
import { SpinnerComponent } from '../../shared/components/spinner/spinner.component';
import {
  getMonthName,
  getYearOptions,
  MONTH_OPTIONS,
  sanitizeHexColor,
} from '../../shared/utils/format.util';

@Component({
  selector: 'app-budget-form-modal',
  standalone: true,
  imports: [ReactiveFormsModule, SpinnerComponent, AppSelectComponent],
  templateUrl: './budget-form-modal.component.html',
  animations: [modalPopIn],
})
export class BudgetFormModalComponent {
  private readonly fb = inject(FormBuilder);
  private readonly budgetService = inject(BudgetService);
  private readonly toastService = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly budget = input<BudgetDto | null>(null);
  readonly categories = input<CategoryDto[]>([]);
  readonly existingCategoryIds = input<string[]>([]);
  readonly defaultMonth = input<number>(new Date().getMonth() + 1);
  readonly defaultYear = input<number>(new Date().getFullYear());

  readonly saved = output<BudgetDto>();
  readonly closed = output<void>();

  readonly monthOptions = MONTH_OPTIONS;
  readonly yearOptions = computed(() => {
    const baseYears = getYearOptions(new Date().getFullYear());
    const defYear = this.defaultYear();
    if (!baseYears.includes(defYear)) {
      return [defYear, ...baseYears].sort((a, b) => b - a);
    }
    return baseYears;
  });

  readonly isEditMode = computed(() => this.budget() !== null);
  readonly isSubmitting = signal<boolean>(false);
  readonly submitError = signal<string | null>(null);
  readonly fieldValidationErrors = signal<Record<string, string> | null>(null);

  readonly formMonth = signal<number>(new Date().getMonth() + 1);
  readonly formYear = signal<number>(new Date().getFullYear());

  readonly expenseCategories = computed(() =>
    this.categories().filter((cat) => cat.type === 'EXPENSE')
  );

  readonly existingCategorySet = computed(() => {
    if (
      this.formMonth() === this.defaultMonth() &&
      this.formYear() === this.defaultYear()
    ) {
      return new Set(this.existingCategoryIds());
    }
    return new Set<string>();
  });

  readonly availableCategoryCount = computed(() => {
    const existing = this.existingCategorySet();
    return this.expenseCategories().filter((cat) => !existing.has(cat.id)).length;
  });

  readonly yearSelectOptions = computed<ReadonlyArray<AppSelectOption<number>>>(() =>
    this.yearOptions().map((y) => ({ value: y, label: String(y) }))
  );

  readonly categorySelectOptions = computed<ReadonlyArray<AppSelectOption<string>>>(() =>
    this.expenseCategories().map((cat) => {
      const used = this.isCategoryUsed(cat.id);
      return {
        value: cat.id,
        label: used ? `${cat.name} (Terpakai)` : cat.name,
        disabled: used,
      };
    })
  );

  readonly editPeriodLabel = computed(() => {
    const b = this.budget();
    return b ? `${getMonthName(b.month)} ${b.year}` : '';
  });

  readonly editCategoryColor = computed(() =>
    sanitizeHexColor(this.budget()?.categoryColor, '#0F172A')
  );

  readonly form = this.fb.nonNullable.group({
    categoryId: ['', [Validators.required]],
    month: [new Date().getMonth() + 1, [Validators.required, Validators.min(1), Validators.max(12)]],
    year: [new Date().getFullYear(), [Validators.required, Validators.min(2020), Validators.max(2100)]],
    amountLimit: [
      null as number | null,
      [Validators.required, positiveDecimalValidator('positiveLimit', 'maxLimit')],
    ],
  });

  constructor() {
    effect(() => {
      const currentBudget = this.budget();
      const month = this.defaultMonth();
      const year = this.defaultYear();

      untracked(() => {
        this.submitError.set(null);
        this.fieldValidationErrors.set(null);
        this.isSubmitting.set(false);

        if (currentBudget) {
          this.formMonth.set(currentBudget.month);
          this.formYear.set(currentBudget.year);
          this.form.reset({
            categoryId: currentBudget.categoryId,
            month: currentBudget.month,
            year: currentBudget.year,
            amountLimit: currentBudget.amountLimit,
          });
        } else {
          this.formMonth.set(month);
          this.formYear.set(year);
          this.form.reset({
            categoryId: '',
            month,
            year,
            amountLimit: null,
          });
        }
      });
    });
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    this.requestClose();
  }

  onPeriodMonthChange(value: string | number): void {
    const parsed = Number(value);
    if (!Number.isInteger(parsed) || parsed < 1 || parsed > 12) {
      return;
    }
    this.formMonth.set(parsed);
    this.form.controls.month.setValue(parsed);
    this.clearCategoryIfAlreadyUsed();
  }

  onPeriodYearChange(value: string | number): void {
    const parsed = Number(value);
    if (!Number.isInteger(parsed) || parsed < 2020 || parsed > 2100) {
      return;
    }
    this.formYear.set(parsed);
    this.form.controls.year.setValue(parsed);
    this.clearCategoryIfAlreadyUsed();
  }

  isCategoryUsed(categoryId: string): boolean {
    return this.existingCategorySet().has(categoryId);
  }

  requestClose(): void {
    if (this.isSubmitting()) {
      return;
    }
    this.closed.emit();
  }

  onSubmit(): void {
    if (this.isSubmitting()) {
      return;
    }

    this.submitError.set(null);
    this.fieldValidationErrors.set(null);

    const editing = this.budget();

    if (editing) {
      if (this.form.controls.amountLimit.invalid) {
        this.form.controls.amountLimit.markAsTouched();
        return;
      }

      const amountLimit = Number(this.form.controls.amountLimit.value);
      this.isSubmitting.set(true);

      this.budgetService
        .updateBudget(editing.id, { amountLimit })
        .pipe(
          finalize(() => this.isSubmitting.set(false)),
          takeUntilDestroyed(this.destroyRef)
        )
        .subscribe({
          next: (updated) => {
            this.toastService.success('Tersimpan');
            this.saved.emit(updated);
          },
          error: (error: unknown) => this.handleSubmitError(error, 'Gagal memperbarui anggaran.'),
        });
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    this.isSubmitting.set(true);

    this.budgetService
      .createBudget({
        categoryId: raw.categoryId,
        month: Number(raw.month),
        year: Number(raw.year),
        amountLimit: Number(raw.amountLimit),
      })
      .pipe(
        finalize(() => this.isSubmitting.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (created) => {
          this.toastService.success('Tersimpan');
          this.saved.emit(created);
        },
        error: (error: unknown) => this.handleSubmitError(error, 'Gagal membuat batas anggaran.'),
      });
  }

  private clearCategoryIfAlreadyUsed(): void {
    const selected = this.form.controls.categoryId.value;
    if (selected && this.existingCategorySet().has(selected)) {
      this.form.controls.categoryId.setValue('');
    }
  }

  private handleSubmitError(error: unknown, fallback: string): void {
    const parsed = extractApiError(error, fallback);
    this.submitError.set(parsed.message);
    this.fieldValidationErrors.set(parsed.validationErrors);
    if (!isUnauthorizedError(error)) {
      this.toastService.error('Gagal menyimpan');
    }
  }
}
