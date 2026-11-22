import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { environment } from '../../../environments/environment';
import { CategoryDto } from '../../core/models/category.model';
import { TransactionDto } from '../../core/models/transaction.model';
import { ToastService } from '../../core/services/toast.service';
import { TransactionFormModalComponent } from './transaction-form-modal.component';

describe('TransactionFormModalComponent', () => {
  let httpMock: HttpTestingController;

  const mockCategories: CategoryDto[] = [
    {
      id: 'cat-inc-1',
      name: 'Gaji',
      type: 'INCOME',
      color: '#10B981',
      isDefault: true,
    },
    {
      id: 'cat-exp-1',
      name: 'Makanan & Minuman',
      type: 'EXPENSE',
      color: '#F59E0B',
      isDefault: true,
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TransactionFormModalComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should filter categories by selected type and reset incompatible category', () => {
    const fixture = TestBed.createComponent(TransactionFormModalComponent);
    fixture.componentRef.setInput('categories', mockCategories);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    expect(component.selectedType()).toBe('EXPENSE');
    expect(component.filteredCategories().map((c) => c.id)).toEqual(['cat-exp-1']);

    component.form.controls.categoryId.setValue('cat-exp-1');
    component.selectType('INCOME');

    expect(component.selectedType()).toBe('INCOME');
    expect(component.filteredCategories().map((c) => c.id)).toEqual(['cat-inc-1']);
    expect(component.form.controls.categoryId.value).toBe('');
  });

  it('should reject invalid MIME type and oversized receipt file on client side', () => {
    const fixture = TestBed.createComponent(TransactionFormModalComponent);
    fixture.componentRef.setInput('categories', mockCategories);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    const pdfFile = new File(['%PDF-1.4'], 'struk.pdf', { type: 'application/pdf' });
    component.onFileSelected({
      target: { files: [pdfFile] },
    } as unknown as Event);

    expect(component.selectedFile()).toBeNull();
    expect(component.receiptError()).toContain('JPEG, PNG, atau WebP');

    const hugeFile = new File([new Uint8Array(5 * 1024 * 1024 + 10)], 'big.png', {
      type: 'image/png',
    });
    component.onFileSelected({
      target: { files: [hugeFile] },
    } as unknown as Event);

    expect(component.selectedFile()).toBeNull();
    expect(component.receiptError()).toContain('5 MB');
  });

  it('should upload receipt first and then create transaction, preserving form on 503 error', () => {
    const fixture = TestBed.createComponent(TransactionFormModalComponent);
    fixture.componentRef.setInput('categories', mockCategories);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    const savedSpy = spyOn(component.saved, 'emit');

    component.form.patchValue({
      categoryId: 'cat-exp-1',
      amount: 75000,
      transactionDate: '2026-11-21',
      description: 'Kopi sore',
    });

    const imageFile = new File(['img-bytes'], 'struk.jpg', { type: 'image/jpeg' });
    component.onFileSelected({
      target: { files: [imageFile] },
    } as unknown as Event);
    expect(component.selectedFile()).toBe(imageFile);

    // First attempt fails with 503 -> form data must stay intact
    component.onSubmit();
    const failedUploadReq = httpMock.expectOne(`${environment.apiUrl}/transactions/receipt`);
    failedUploadReq.flush(
      {
        timestamp: '2026-11-21T10:00:00Z',
        status: 503,
        error: 'Service Unavailable',
        message: 'Supabase Storage belum dikonfigurasi',
        path: '/api/transactions/receipt',
      },
      { status: 503, statusText: 'Service Unavailable' }
    );

    expect(component.isSubmitting()).toBeFalse();
    expect(component.receiptError()).toBe('Supabase Storage belum dikonfigurasi');
    expect(component.form.controls.description.value).toBe('Kopi sore');
    expect(component.form.controls.amount.value).toBe(75000);
    expect(savedSpy).not.toHaveBeenCalled();

    // Retry upload succeeds -> creates transaction with receiptUrl
    component.onSubmit();
    const okUploadReq = httpMock.expectOne(`${environment.apiUrl}/transactions/receipt`);
    okUploadReq.flush(
      {
        receiptUrl: 'https://xyz.supabase.co/storage/v1/object/public/receipts/u/struk.jpg',
        filePath: 'u/struk.jpg',
        contentType: 'image/jpeg',
        sizeBytes: 9,
      },
      { status: 201, statusText: 'Created' }
    );

    const createReq = httpMock.expectOne(`${environment.apiUrl}/transactions`);
    expect(createReq.request.method).toBe('POST');
    expect(createReq.request.body.receiptUrl).toBe(
      'https://xyz.supabase.co/storage/v1/object/public/receipts/u/struk.jpg'
    );

    const createdTx: TransactionDto = {
      id: 'tx-new',
      categoryId: 'cat-exp-1',
      categoryName: 'Makanan & Minuman',
      type: 'EXPENSE',
      amount: 75000,
      description: 'Kopi sore',
      transactionDate: '2026-11-21',
      receiptUrl: 'https://xyz.supabase.co/storage/v1/object/public/receipts/u/struk.jpg',
      createdAt: '2026-11-21T10:00:00Z',
      updatedAt: '2026-11-21T10:00:00Z',
    };
    createReq.flush(createdTx, { status: 201, statusText: 'Created' });

    expect(savedSpy).toHaveBeenCalledWith(createdTx);
    fixture.destroy();
  });

  it('should preserve typed form fields and receipt preview across change detection and revoke objectURL on cleanup', () => {
    const revokeSpy = spyOn(URL, 'revokeObjectURL').and.callThrough();
    const fixture = TestBed.createComponent(TransactionFormModalComponent);
    fixture.componentRef.setInput('categories', mockCategories);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    component.form.patchValue({
      categoryId: 'cat-exp-1',
      amount: 85000.5,
      transactionDate: '2026-11-21',
      description: 'Makan malam',
    });

    const imageFile = new File(['valid-img'], 'struk.png', { type: 'image/png' });
    component.onFileSelected({
      target: { files: [imageFile], value: 'C:\\fakepath\\struk.png' },
    } as unknown as Event);

    // Trigger Angular change detection — regression guard for effect() not tracking localPreviewUrl
    fixture.detectChanges();

    expect(component.selectedFile()).toBe(imageFile);
    expect(component.localPreviewUrl()).toMatch(/^blob:/);
    expect(component.activeReceiptPreview()).toBe(component.localPreviewUrl());
    expect(component.form.controls.amount.value).toBe(85000.5);
    expect(component.form.controls.description.value).toBe('Makan malam');
    expect(component.form.controls.categoryId.value).toBe('cat-exp-1');

    const createdUrl = component.localPreviewUrl()!;
    fixture.destroy();
    expect(revokeSpy).toHaveBeenCalledWith(createdUrl);
  });

  it('should validate decimal precision (> 2 decimals), empty receipt file, and sanitize unsafe receiptUrl', () => {
    const fixture = TestBed.createComponent(TransactionFormModalComponent);
    fixture.componentRef.setInput('categories', mockCategories);
    fixture.componentRef.setInput('transaction', {
      id: 'tx-xss',
      categoryId: 'cat-exp-1',
      categoryName: 'Makanan & Minuman',
      type: 'EXPENSE',
      amount: 50000,
      description: 'Unsafe receipt',
      transactionDate: '2026-11-21',
      receiptUrl: 'javascript:alert(1)',
      createdAt: '2026-11-21T10:00:00Z',
      updatedAt: '2026-11-21T10:00:00Z',
    } as TransactionDto);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    expect(component.existingReceiptUrl()).toBeNull();
    expect(component.activeReceiptPreview()).toBeNull();

    // > 2 decimals must fail validation
    component.form.controls.amount.setValue(100.999);
    expect(component.form.controls.amount.hasError('maxDecimals')).toBeTrue();

    component.form.controls.amount.setValue(100.99);
    expect(component.form.controls.amount.valid).toBeTrue();

    // Empty 0-byte file
    const emptyFile = new File([], 'empty.jpg', { type: 'image/jpeg' });
    component.onFileSelected({
      target: { files: [emptyFile], value: 'empty.jpg' },
    } as unknown as Event);
    expect(component.receiptError()).toBe('File foto struk tidak boleh kosong.');
    expect(component.selectedFile()).toBeNull();

    fixture.destroy();
  });

  it('should block concurrent submit mutations, handle 400 validation errors without re-uploading, and suppress 401 toast', () => {
    const toastService = TestBed.inject(ToastService);
    const toastSpy = spyOn(toastService, 'error');

    const fixture = TestBed.createComponent(TransactionFormModalComponent);
    fixture.componentRef.setInput('categories', mockCategories);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    component.form.patchValue({
      categoryId: 'cat-exp-1',
      amount: 50000,
      transactionDate: '2026-11-21',
      description: 'Test race',
    });

    const imageFile = new File(['img-bytes'], 'struk.jpg', { type: 'image/jpeg' });
    component.onFileSelected({
      target: { files: [imageFile], value: '' },
    } as unknown as Event);
    fixture.detectChanges();

    component.onSubmit();
    // Duplicate submit and type change while submitting must be ignored
    component.onSubmit();
    component.selectType('INCOME');
    component.removeReceipt();
    expect(component.selectedType()).toBe('EXPENSE');
    expect(component.selectedFile()).toBe(imageFile);

    const uploadReq = httpMock.expectOne(`${environment.apiUrl}/transactions/receipt`);
    uploadReq.flush(
      {
        receiptUrl: 'https://demo.supabase.co/storage/v1/object/public/receipts/u/r.jpg',
        filePath: 'u/r.jpg',
        contentType: 'image/jpeg',
        sizeBytes: 9,
      },
      { status: 201, statusText: 'Created' }
    );

    // Create returns 400 validation error -> receipt already uploaded is preserved
    const createReq = httpMock.expectOne(`${environment.apiUrl}/transactions`);
    createReq.flush(
      {
        timestamp: '2026-11-21T10:00:00Z',
        status: 400,
        error: 'Bad Request',
        message: 'Validasi input gagal',
        path: '/api/transactions',
        validationErrors: { amount: 'Nominal tidak valid' },
      },
      { status: 400, statusText: 'Bad Request' }
    );

    expect(component.isSubmitting()).toBeFalse();
    expect(component.selectedFile()).toBeNull();
    expect(component.existingReceiptUrl()).toBe(
      'https://demo.supabase.co/storage/v1/object/public/receipts/u/r.jpg'
    );
    expect(component.fieldValidationErrors()?.['amount']).toBe('Nominal tidak valid');
    expect(toastSpy).toHaveBeenCalledTimes(1);

    // Retry submit does NOT re-upload receipt; 401 must not trigger another error toast
    toastSpy.calls.reset();
    component.onSubmit();
    httpMock.expectNone(`${environment.apiUrl}/transactions/receipt`);
    const retryCreateReq = httpMock.expectOne(`${environment.apiUrl}/transactions`);
    retryCreateReq.flush(
      {
        timestamp: '2026-11-21T10:01:00Z',
        status: 401,
        error: 'Unauthorized',
        message: 'Token kedaluwarsa',
        path: '/api/transactions',
      },
      { status: 401, statusText: 'Unauthorized' }
    );
    expect(toastSpy).not.toHaveBeenCalled();

    fixture.destroy();
  });
});
