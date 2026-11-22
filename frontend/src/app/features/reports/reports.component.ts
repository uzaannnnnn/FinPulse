import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  OnDestroy,
  OnInit,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { interval, of, Subscription } from 'rxjs';
import { catchError, exhaustMap, finalize } from 'rxjs/operators';
import {
  CreateReportExportRequest,
  ReportExportDto,
  ReportFormat,
  ReportStatus,
} from '../../core/models/report.model';
import { ReportService } from '../../core/services/report.service';
import { ToastService } from '../../core/services/toast.service';
import { extractApiError } from '../../core/utils/error.util';
import { springEnter, staggerList } from '../../shared/animations';
import {
  AppSelectComponent,
  AppSelectOption,
} from '../../shared/components/app-select/app-select.component';
import { SpinnerComponent } from '../../shared/components/spinner/spinner.component';
import {
  formatDateTimeId,
  formatFileSize,
  getMonthName,
  getYearOptions,
  MONTH_OPTIONS,
  sanitizeHttpUrl,
} from '../../shared/utils/format.util';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, SpinnerComponent, AppSelectComponent],
  templateUrl: './reports.component.html',
  animations: [springEnter, staggerList],
})
export class ReportsComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly reportService = inject(ReportService);
  private readonly toastService = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly now = new Date();
  private pollSubscription: Subscription | null = null;

  readonly monthOptions = MONTH_OPTIONS;
  readonly yearOptions = getYearOptions(this.now.getFullYear());
  readonly yearSelectOptions: ReadonlyArray<AppSelectOption<number>> = this.yearOptions.map(
    (y) => ({ value: y, label: String(y) })
  );

  readonly exports = signal<ReportExportDto[]>([]);
  readonly isLoading = signal<boolean>(true);
  readonly isSubmitting = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly submitError = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    month: [this.now.getMonth() + 1, [Validators.required, Validators.min(1), Validators.max(12)]],
    year: [
      this.now.getFullYear(),
      [Validators.required, Validators.min(2020), Validators.max(2100)],
    ],
    format: ['PDF' as ReportFormat, [Validators.required]],
  });

  readonly hasActiveJobs = computed(() =>
    this.exports().some((job) => job.status === 'PENDING' || job.status === 'PROCESSING')
  );

  readonly completedCount = computed(
    () => this.exports().filter((job) => job.status === 'COMPLETED').length
  );

  readonly activeCount = computed(
    () =>
      this.exports().filter((job) => job.status === 'PENDING' || job.status === 'PROCESSING').length
  );

  readonly formatDateTimeId = formatDateTimeId;
  readonly formatFileSize = formatFileSize;

  constructor() {
    effect(() => {
      const shouldPoll = this.hasActiveJobs();
      untracked(() => {
        if (shouldPoll) {
          this.startPolling();
        } else {
          this.stopPolling();
        }
      });
    });
  }

  ngOnInit(): void {
    this.loadExports();
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  selectFormat(format: ReportFormat): void {
    if (this.isSubmitting()) {
      return;
    }
    this.form.controls.format.setValue(format);
  }

  loadExports(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.reportService
      .listExports(20)
      .pipe(
        finalize(() => this.isLoading.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (items) => {
          this.exports.set(items);
        },
        error: (error: unknown) => {
          const parsed = extractApiError(error, 'Gagal memuat riwayat ekspor laporan.');
          this.errorMessage.set(parsed.message);
          const isUnauthorized = error instanceof HttpErrorResponse && error.status === 401;
          if (!isUnauthorized) {
            this.toastService.error('Gagal memuat data');
          }
        },
      });
  }

  onSubmit(): void {
    if (this.isSubmitting()) {
      return;
    }
    this.submitError.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const payload: CreateReportExportRequest = {
      month: Number(raw.month),
      year: Number(raw.year),
      format: raw.format,
    };

    this.isSubmitting.set(true);
    this.reportService
      .requestExport(payload)
      .pipe(
        finalize(() => this.isSubmitting.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (createdJob) => {
          this.exports.update((current) => [
            createdJob,
            ...current.filter((item) => item.id !== createdJob.id),
          ]);
          this.toastService.success('Ekspor dijadwalkan');
        },
        error: (error: unknown) => {
          const isServiceUnavailable =
            error instanceof HttpErrorResponse && error.status === 503;
          const isUnauthorized = error instanceof HttpErrorResponse && error.status === 401;

          const message = isServiceUnavailable
            ? 'Layanan ekspor sedang sibuk, coba lagi sebentar lagi.'
            : extractApiError(error, 'Gagal mengajukan permintaan ekspor laporan.').message;

          this.submitError.set(message);
          if (!isUnauthorized) {
            this.toastService.error(
              isServiceUnavailable ? 'Antrean tidak tersedia' : 'Ekspor gagal'
            );
          }
        },
      });
  }

  getPeriodLabel(job: ReportExportDto): string {
    return `${getMonthName(job.month)} ${job.year}`;
  }

  getSafeDownloadUrl(url: string | null | undefined): string | null {
    return sanitizeHttpUrl(url);
  }

  getStatusLabel(status: ReportStatus): string {
    switch (status) {
      case 'PENDING':
        return 'Menunggu';
      case 'PROCESSING':
        return 'Sedang Diproses';
      case 'COMPLETED':
        return 'Selesai';
      case 'FAILED':
        return 'Gagal';
    }
  }

  getStatusBadgeClass(status: ReportStatus): string {
    switch (status) {
      case 'PENDING':
        return 'border-2 border-fp-ink bg-[#FACC15] text-fp-ink shadow-brutal-sm';
      case 'PROCESSING':
        return 'border-2 border-fp-ink bg-sky-200 text-fp-ink shadow-brutal-sm';
      case 'COMPLETED':
        return 'border-2 border-fp-ink bg-[#00D68F] text-fp-ink shadow-brutal-sm';
      case 'FAILED':
        return 'border-2 border-fp-ink bg-[#FF4D6D] text-white shadow-brutal-sm';
    }
  }

  getStatusDotClass(status: ReportStatus): string {
    switch (status) {
      case 'PENDING':
        return 'bg-fp-ink animate-pulse';
      case 'PROCESSING':
        return 'bg-fp-ink animate-pulse';
      case 'COMPLETED':
        return 'bg-fp-ink';
      case 'FAILED':
        return 'bg-white';
    }
  }

  private startPolling(): void {
    if (this.pollSubscription) {
      return;
    }

    this.pollSubscription = interval(2500)
      .pipe(
        exhaustMap(() =>
          this.reportService.listExports(20).pipe(catchError(() => of<ReportExportDto[] | null>(null)))
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((items) => {
        if (items !== null) {
          this.exports.set(items);
        }
      });
  }

  private stopPolling(): void {
    if (this.pollSubscription) {
      this.pollSubscription.unsubscribe();
      this.pollSubscription = null;
    }
  }
}
