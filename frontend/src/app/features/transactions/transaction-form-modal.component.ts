import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  HostListener,
  inject,
  input,
  OnDestroy,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { CategoryDto, TransactionType } from '../../core/models/category.model';
import {
  CreateTransactionRequest,
  TransactionDto,
  UpdateTransactionRequest,
} from '../../core/models/transaction.model';
import { ToastService } from '../../core/services/toast.service';
import { TransactionService } from '../../core/services/transaction.service';
import { extractApiError } from '../../core/utils/error.util';
import { modalPopIn } from '../../shared/animations';
import {
  AppSelectComponent,
  AppSelectOption,
} from '../../shared/components/app-select/app-select.component';
import { SpinnerComponent } from '../../shared/components/spinner/spinner.component';
import { getTodayIsoDate, sanitizeHttpUrl } from '../../shared/utils/format.util';

const ALLOWED_RECEIPT_MIME_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const MAX_RECEIPT_BYTES = 5 * 1024 * 1024;
const MAX_AMOUNT = 9_999_999_999_999.99;

function hasMoreThanTwoDecimals(raw: unknown, num: number): boolean {
  const str = String(raw).trim();
  if (/^[+-]?\d+(\.\d+)?$/.test(str)) {
    const dotIndex = str.indexOf('.');
    return dotIndex >= 0 && str.length - dotIndex - 1 > 2;
  }
  return Math.abs(num * 100 - Math.round(num * 100)) > 1e-8;
}

function positiveAmountValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const raw = control.value;
    if (raw === null || raw === undefined || raw === '') {
      return null;
    }
    const num = Number(raw);
    if (!Number.isFinite(num) || num <= 0) {
      return { positiveAmount: true };
    }
    if (num > MAX_AMOUNT) {
      return { maxAmount: true };
    }
    if (hasMoreThanTwoDecimals(raw, num)) {
      return { maxDecimals: true };
    }
    return null;
  };
}

@Component({
  selector: 'app-transaction-form-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, SpinnerComponent, AppSelectComponent],
  templateUrl: './transaction-form-modal.component.html',
  animations: [modalPopIn],
})
export class TransactionFormModalComponent implements OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly transactionService = inject(TransactionService);
  private readonly toastService = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly transaction = input<TransactionDto | null>(null);
  readonly categories = input<CategoryDto[]>([]);

  readonly saved = output<TransactionDto>();
  readonly closed = output<void>();

  readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');

  readonly isEditMode = computed(() => this.transaction() !== null);

  readonly selectedType = signal<TransactionType>('EXPENSE');
  readonly selectedFile = signal<File | null>(null);
  readonly localPreviewUrl = signal<string | null>(null);
  readonly existingReceiptUrl = signal<string | null>(null);
  readonly receiptError = signal<string | null>(null);
  readonly isDragOver = signal<boolean>(false);

  readonly isUploadingReceipt = signal<boolean>(false);
  readonly isSubmitting = signal<boolean>(false);
  readonly submitError = signal<string | null>(null);
  readonly fieldValidationErrors = signal<Record<string, string> | null>(null);

  readonly filteredCategories = computed(() => {
    const currentType = this.selectedType();
    return this.categories().filter((category) => category.type === currentType);
  });

  readonly categorySelectOptions = computed<ReadonlyArray<AppSelectOption<string>>>(() =>
    this.filteredCategories().map((cat) => ({ value: cat.id, label: cat.name }))
  );

  readonly activeReceiptPreview = computed(() => {
    const localUrl = this.localPreviewUrl();
    if (localUrl && localUrl.startsWith('blob:')) {
      return localUrl;
    }
    return sanitizeHttpUrl(this.existingReceiptUrl());
  });

  readonly form = this.fb.nonNullable.group({
    type: ['EXPENSE' as TransactionType, [Validators.required]],
    categoryId: ['', [Validators.required]],
    amount: [null as number | null, [Validators.required, positiveAmountValidator()]],
    transactionDate: [getTodayIsoDate(), [Validators.required]],
    description: ['', [Validators.maxLength(500)]],
  });

  constructor() {
    effect(() => {
      const tx = this.transaction();
      untracked(() => this.resetFormForTransaction(tx));
    });
  }

  ngOnDestroy(): void {
    this.revokeLocalPreviewUrl();
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    this.requestClose();
  }

  selectType(type: TransactionType): void {
    if (this.isSubmitting() || this.selectedType() === type) {
      return;
    }
    this.selectedType.set(type);
    this.form.controls.type.setValue(type);

    const currentCategoryId = this.form.controls.categoryId.value;
    const isStillValid = this.categories().some(
      (cat) => cat.id === currentCategoryId && cat.type === type
    );
    if (!isStillValid) {
      this.form.controls.categoryId.setValue('');
      this.form.controls.categoryId.markAsUntouched();
    }
  }

  onFileSelected(event: Event): void {
    if (this.isSubmitting()) {
      return;
    }
    const inputEl = event.target as HTMLInputElement;
    const file = inputEl.files?.[0] ?? null;
    if (file) {
      this.processReceiptFile(file);
    }
    inputEl.value = '';
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    if (this.isSubmitting()) {
      return;
    }
    this.isDragOver.set(true);
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver.set(false);
    if (this.isSubmitting()) {
      return;
    }
    const file = event.dataTransfer?.files?.[0] ?? null;
    if (file) {
      this.processReceiptFile(file);
    }
  }

  removeReceipt(): void {
    if (this.isSubmitting()) {
      return;
    }
    this.revokeLocalPreviewUrl();
    this.selectedFile.set(null);
    this.existingReceiptUrl.set(null);
    this.receiptError.set(null);
    const inputEl = this.fileInput()?.nativeElement;
    if (inputEl) {
      inputEl.value = '';
    }
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

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const fileToUpload = this.selectedFile();
    if (fileToUpload) {
      this.isSubmitting.set(true);
      this.isUploadingReceipt.set(true);
      this.receiptError.set(null);

      this.transactionService
        .uploadReceipt(fileToUpload)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (uploadRes) => {
            this.isUploadingReceipt.set(false);
            const safeUrl = sanitizeHttpUrl(uploadRes.receiptUrl);
            if (!safeUrl) {
              this.isSubmitting.set(false);
              const msg = 'URL struk yang dikembalikan server tidak valid.';
              this.receiptError.set(msg);
              this.submitError.set(msg);
              this.toastService.error('Upload struk gagal');
              return;
            }
            this.existingReceiptUrl.set(safeUrl);
            this.revokeLocalPreviewUrl();
            this.selectedFile.set(null);
            this.persistTransaction(safeUrl);
          },
          error: (error: unknown) => {
            this.isUploadingReceipt.set(false);
            this.isSubmitting.set(false);
            const parsed = extractApiError(
              error,
              'Gagal mengunggah foto struk. Coba lagi atau simpan transaksi tanpa lampiran struk.'
            );
            this.receiptError.set(parsed.message);
            this.submitError.set(parsed.message);
            const isUnauthorized = error instanceof HttpErrorResponse && error.status === 401;
            if (!isUnauthorized) {
              this.toastService.error('Upload struk gagal');
            }
          },
        });
      return;
    }

    this.isSubmitting.set(true);
    this.persistTransaction(sanitizeHttpUrl(this.existingReceiptUrl()));
  }

  getDescriptionLength(): number {
    return (this.form.controls.description.value ?? '').length;
  }

  formatFileSize(bytes: number): string {
    if (bytes >= 1024 * 1024) {
      return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
    }
    return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }

  private persistTransaction(receiptUrl: string | null): void {
    const raw = this.form.getRawValue();
    const trimmedDescription = raw.description.trim();
    const payload: CreateTransactionRequest & UpdateTransactionRequest = {
      categoryId: raw.categoryId,
      type: raw.type,
      amount: Number(raw.amount),
      transactionDate: raw.transactionDate,
      description: trimmedDescription.length > 0 ? trimmedDescription : null,
      receiptUrl: receiptUrl ?? null,
    };

    const editingTx = this.transaction();
    const request$ = editingTx
      ? this.transactionService.updateTransaction(editingTx.id, payload)
      : this.transactionService.createTransaction(payload);

    request$
      .pipe(
        finalize(() => this.isSubmitting.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
      next: (savedTx) => {
        this.toastService.success('Tersimpan');
        this.saved.emit(savedTx);
      },
      error: (error: unknown) => {
        const parsed = extractApiError(
          error,
          editingTx ? 'Gagal memperbarui transaksi.' : 'Gagal menyimpan transaksi baru.'
        );
        this.submitError.set(parsed.message);
        this.fieldValidationErrors.set(parsed.validationErrors);
        const isUnauthorized = error instanceof HttpErrorResponse && error.status === 401;
        if (!isUnauthorized) {
          this.toastService.error('Gagal menyimpan');
        }
      },
    });
  }

  private processReceiptFile(file: File): void {
    this.receiptError.set(null);

    if (!ALLOWED_RECEIPT_MIME_TYPES.has(file.type)) {
      this.receiptError.set('Format file harus berupa gambar JPEG, PNG, atau WebP.');
      return;
    }

    if (file.size <= 0) {
      this.receiptError.set('File foto struk tidak boleh kosong.');
      return;
    }

    if (file.size > MAX_RECEIPT_BYTES) {
      this.receiptError.set('Ukuran foto struk tidak boleh melebihi 5 MB.');
      return;
    }

    this.revokeLocalPreviewUrl();
    this.selectedFile.set(file);
    this.localPreviewUrl.set(URL.createObjectURL(file));
  }

  private resetFormForTransaction(tx: TransactionDto | null): void {
    this.revokeLocalPreviewUrl();
    this.selectedFile.set(null);
    this.receiptError.set(null);
    this.submitError.set(null);
    this.fieldValidationErrors.set(null);
    this.isUploadingReceipt.set(false);
    this.isSubmitting.set(false);

    if (tx) {
      this.selectedType.set(tx.type);
      this.existingReceiptUrl.set(sanitizeHttpUrl(tx.receiptUrl));
      this.form.reset({
        type: tx.type,
        categoryId: tx.categoryId,
        amount: tx.amount,
        transactionDate: tx.transactionDate.slice(0, 10),
        description: tx.description ?? '',
      });
    } else {
      this.selectedType.set('EXPENSE');
      this.existingReceiptUrl.set(null);
      this.form.reset({
        type: 'EXPENSE',
        categoryId: '',
        amount: null,
        transactionDate: getTodayIsoDate(),
        description: '',
      });
    }
  }

  private revokeLocalPreviewUrl(): void {
    const url = this.localPreviewUrl();
    if (url) {
      URL.revokeObjectURL(url);
      this.localPreviewUrl.set(null);
    }
  }
}
