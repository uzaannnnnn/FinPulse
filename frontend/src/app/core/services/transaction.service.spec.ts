import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import {
  CreateTransactionRequest,
  ReceiptUploadResponse,
  TransactionDto,
  TransactionPageResponse,
  UpdateTransactionRequest,
} from '../models/transaction.model';
import { TransactionService } from './transaction.service';

describe('TransactionService', () => {
  let service: TransactionService;
  let httpMock: HttpTestingController;

  const mockTx: TransactionDto = {
    id: '11111111-1111-1111-1111-111111111111',
    categoryId: '660e8400-e29b-41d4-a716-446655440001',
    categoryName: 'Makanan & Minuman',
    categoryIcon: 'utensils',
    categoryColor: '#F59E0B',
    type: 'EXPENSE',
    amount: 125000,
    description: 'Makan siang tim',
    transactionDate: '2026-11-21',
    receiptUrl: 'https://xyz.supabase.co/storage/v1/object/public/receipts/user/abc.jpg',
    createdAt: '2026-11-21T10:00:00Z',
    updatedAt: '2026-11-21T10:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TransactionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should list transactions with full filter and pagination query params', () => {
    let response: TransactionPageResponse | undefined;

    service
      .listTransactions({
        page: 1,
        size: 20,
        sortBy: 'amount',
        sortDir: 'DESC',
        type: 'EXPENSE',
        categoryId: '660e8400-e29b-41d4-a716-446655440001',
        startDate: '2026-11-01',
        endDate: '2026-11-30',
        search: '  makan  ',
      })
      .subscribe((res) => (response = res));

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/transactions`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('sortBy')).toBe('amount');
    expect(req.request.params.get('sortDir')).toBe('DESC');
    expect(req.request.params.get('type')).toBe('EXPENSE');
    expect(req.request.params.get('categoryId')).toBe('660e8400-e29b-41d4-a716-446655440001');
    expect(req.request.params.get('startDate')).toBe('2026-11-01');
    expect(req.request.params.get('endDate')).toBe('2026-11-30');
    expect(req.request.params.get('search')).toBe('makan');

    req.flush({
      content: [mockTx],
      page: 1,
      size: 20,
      totalElements: 21,
      totalPages: 2,
      first: false,
      last: true,
    });

    expect(response?.content.length).toBe(1);
    expect(response?.totalElements).toBe(21);
  });

  it('should omit undefined or blank filter params when listing transactions', () => {
    service.listTransactions({ page: 0, search: '   ' }).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/transactions`);
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.has('search')).toBeFalse();
    expect(req.request.params.has('type')).toBeFalse();
    req.flush({
      content: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    });
  });

  it('should get single transaction by id', () => {
    let result: TransactionDto | undefined;
    service.getTransaction(mockTx.id).subscribe((res) => (result = res));

    const req = httpMock.expectOne(`${environment.apiUrl}/transactions/${mockTx.id}`);
    expect(req.request.method).toBe('GET');
    req.flush(mockTx);

    expect(result).toEqual(mockTx);
  });

  it('should create transaction via POST', () => {
    const payload: CreateTransactionRequest = {
      categoryId: mockTx.categoryId,
      type: 'EXPENSE',
      amount: 125000,
      description: 'Makan siang tim',
      transactionDate: '2026-11-21',
      receiptUrl: null,
    };
    let result: TransactionDto | undefined;
    service.createTransaction(payload).subscribe((res) => (result = res));

    const req = httpMock.expectOne(`${environment.apiUrl}/transactions`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(mockTx);

    expect(result?.id).toBe(mockTx.id);
  });

  it('should update transaction via PUT', () => {
    const payload: UpdateTransactionRequest = {
      categoryId: mockTx.categoryId,
      type: 'EXPENSE',
      amount: 150000,
      description: 'Makan siang tim (revisi)',
      transactionDate: '2026-11-21',
      receiptUrl: mockTx.receiptUrl,
    };
    let result: TransactionDto | undefined;
    service.updateTransaction(mockTx.id, payload).subscribe((res) => (result = res));

    const req = httpMock.expectOne(`${environment.apiUrl}/transactions/${mockTx.id}`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    req.flush({ ...mockTx, amount: 150000 });

    expect(result?.amount).toBe(150000);
  });

  it('should delete transaction via DELETE', () => {
    let completed = false;
    service.deleteTransaction(mockTx.id).subscribe(() => (completed = true));

    const req = httpMock.expectOne(`${environment.apiUrl}/transactions/${mockTx.id}`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(completed).toBeTrue();
  });

  it('should upload receipt as multipart FormData with key "file"', () => {
    const file = new File(['dummy-image'], 'struk.jpg', { type: 'image/jpeg' });
    const mockUploadRes: ReceiptUploadResponse = {
      receiptUrl: 'https://xyz.supabase.co/storage/v1/object/public/receipts/user/struk.jpg',
      filePath: 'user/struk.jpg',
      contentType: 'image/jpeg',
      sizeBytes: 11,
    };

    let result: ReceiptUploadResponse | undefined;
    service.uploadReceipt(file).subscribe((res) => (result = res));

    const req = httpMock.expectOne(`${environment.apiUrl}/transactions/receipt`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBeTrue();
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush(mockUploadRes, { status: 201, statusText: 'Created' });

    expect(result).toEqual(mockUploadRes);
  });
});
