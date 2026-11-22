import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { environment } from '../../../environments/environment';
import { CategoryDto } from '../../core/models/category.model';
import { TransactionDto, TransactionPageResponse } from '../../core/models/transaction.model';
import { ToastService } from '../../core/services/toast.service';
import { TransactionsComponent } from './transactions.component';

describe('TransactionsComponent', () => {
  let httpMock: HttpTestingController;

  const mockCategories: CategoryDto[] = [
    {
      id: 'cat-income-1',
      name: 'Gaji',
      type: 'INCOME',
      icon: 'wallet',
      color: '#10B981',
      isDefault: true,
    },
    {
      id: 'cat-expense-1',
      name: 'Makanan & Minuman',
      type: 'EXPENSE',
      icon: 'utensils',
      color: '#F59E0B',
      isDefault: true,
    },
  ];

  const mockTx: TransactionDto = {
    id: 'tx-100',
    categoryId: 'cat-expense-1',
    categoryName: 'Makanan & Minuman',
    categoryIcon: 'utensils',
    categoryColor: '#F59E0B',
    type: 'EXPENSE',
    amount: 125000,
    description: 'Makan siang tim',
    transactionDate: '2026-11-21',
    receiptUrl: 'https://xyz.supabase.co/storage/v1/object/public/receipts/u/r.jpg',
    createdAt: '2026-11-21T10:00:00Z',
    updatedAt: '2026-11-21T10:00:00Z',
  };

  const mockPage: TransactionPageResponse = {
    content: [mockTx],
    page: 0,
    size: 10,
    totalElements: 15,
    totalPages: 2,
    first: true,
    last: false,
  };

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [TransactionsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  function flushInitialLoad(pageResponse: TransactionPageResponse = mockPage): void {
    httpMock.expectOne(`${environment.apiUrl}/categories`).flush(mockCategories);
    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/transactions`)
      .flush(pageResponse);
  }

  it('should load categories and transactions on init', () => {
    const fixture = TestBed.createComponent(TransactionsComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    flushInitialLoad();
    fixture.detectChanges();

    expect(component.isLoading()).toBeFalse();
    expect(component.categories().length).toBe(2);
    expect(component.transactions().length).toBe(1);
    expect(component.totalElements()).toBe(15);

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Makanan & Minuman');
    expect(text).toContain('Makan siang tim');
    expect(text).toContain('Rp 125.000');
  });

  it('should debounce search filter and pass search query param', fakeAsync(() => {
    const fixture = TestBed.createComponent(TransactionsComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    flushInitialLoad();

    component.onSearchInput('kopi');
    tick(150);
    httpMock.expectNone((r) => r.url === `${environment.apiUrl}/transactions`);

    tick(200);
    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/transactions` && r.params.get('search') === 'kopi'
    );
    expect(req.request.params.get('page')).toBe('0');
    req.flush({ ...mockPage, content: [], totalElements: 0, totalPages: 0, last: true });

    expect(component.transactions().length).toBe(0);
  }));

  it('should block invalid date range when startDate is after endDate', () => {
    const fixture = TestBed.createComponent(TransactionsComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    flushInitialLoad();

    component.onStartDateChange('2026-11-25');
    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/transactions` && r.params.get('startDate') === '2026-11-25')
      .flush(mockPage);

    component.onEndDateChange('2026-11-10');
    expect(component.dateRangeError()).toBe('Tanggal mulai tidak boleh melebihi tanggal akhir.');
    httpMock.expectNone((r) => r.url === `${environment.apiUrl}/transactions`);
  });

  it('should filter by type, paginate, and reset incompatible category', () => {
    const fixture = TestBed.createComponent(TransactionsComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    flushInitialLoad();

    component.onCategoryChange('cat-expense-1');
    httpMock
      .expectOne(
        (r) =>
          r.url === `${environment.apiUrl}/transactions` &&
          r.params.get('categoryId') === 'cat-expense-1'
      )
      .flush(mockPage);

    component.onTypeChange('INCOME');
    expect(component.selectedCategoryId()).toBe('');
    httpMock
      .expectOne(
        (r) =>
          r.url === `${environment.apiUrl}/transactions` &&
          r.params.get('type') === 'INCOME' &&
          !r.params.has('categoryId')
      )
      .flush(mockPage);

    component.goToNextPage();
    httpMock
      .expectOne(
        (r) => r.url === `${environment.apiUrl}/transactions` && r.params.get('page') === '1'
      )
      .flush({ ...mockPage, page: 1, first: false, last: true });

    expect(component.page()).toBe(1);
  });

  it('should open receipt preview and delete transaction after confirmation', () => {
    const fixture = TestBed.createComponent(TransactionsComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    flushInitialLoad();

    component.openReceiptPreview(mockTx);
    expect(component.previewReceiptTx()?.id).toBe(mockTx.id);
    component.closeReceiptPreview();
    expect(component.previewReceiptTx()).toBeNull();

    component.openDeleteConfirm(mockTx);
    expect(component.deletingTransaction()?.id).toBe(mockTx.id);

    component.confirmDelete();
    const deleteReq = httpMock.expectOne(`${environment.apiUrl}/transactions/${mockTx.id}`);
    expect(deleteReq.request.method).toBe('DELETE');
    deleteReq.flush(null, { status: 204, statusText: 'No Content' });

    const reloadReq = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/transactions`);
    reloadReq.flush({ ...mockPage, content: [], totalElements: 0, totalPages: 0, last: true });

    expect(component.deletingTransaction()).toBeNull();
    expect(component.transactions().length).toBe(0);
  });

  it('should not show duplicate error toast on 401 response', () => {
    const toastService = TestBed.inject(ToastService);
    const toastSpy = spyOn(toastService, 'error');

    const fixture = TestBed.createComponent(TransactionsComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiUrl}/categories`).flush(mockCategories);
    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/transactions`)
      .flush(
        {
          timestamp: '2026-11-21T10:00:00Z',
          status: 401,
          error: 'Unauthorized',
          message: 'Sesi berakhir',
          path: '/api/transactions',
        },
        { status: 401, statusText: 'Unauthorized' }
      );

    expect(component.errorMessage()).toBe('Sesi berakhir');
    expect(toastSpy).not.toHaveBeenCalled();
  });

  it('should cancel pending debounced search on resetFilters and allow searching same term again', fakeAsync(() => {
    const fixture = TestBed.createComponent(TransactionsComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    flushInitialLoad();

    // Type 'kopi' then immediately reset within < 300ms
    component.onSearchInput('kopi');
    tick(100);
    component.resetFilters();

    const resetReq = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/transactions` && !r.params.has('search')
    );
    resetReq.flush(mockPage);

    // After debounce window elapses, stale 'kopi' must NOT fire
    tick(350);
    httpMock.expectNone((r) => r.url === `${environment.apiUrl}/transactions`);
    expect(component.activeSearch()).toBe('');

    // Search 'kopi' again after reset -> must execute cleanly
    component.onSearchInput('kopi');
    tick(310);
    const searchReq = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/transactions` && r.params.get('search') === 'kopi'
    );
    searchReq.flush(mockPage);
  }));

  it('should ignore unsafe javascript/data receipt URLs and bound search input to 100 chars', () => {
    const fixture = TestBed.createComponent(TransactionsComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    flushInitialLoad();

    component.openReceiptPreview({ ...mockTx, receiptUrl: 'javascript:alert(1)' });
    expect(component.previewReceiptTx()).toBeNull();

    component.openReceiptPreview({ ...mockTx, receiptUrl: 'data:image/svg+xml,<svg/>' });
    expect(component.previewReceiptTx()).toBeNull();

    component.onSearchInput('x'.repeat(150));
    expect(component.searchInput().length).toBe(100);
  });

  it('should refresh alerts after saving an EXPENSE transaction but not for INCOME', () => {
    const fixture = TestBed.createComponent(TransactionsComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    flushInitialLoad();

    component.onTransactionSaved(mockTx);
    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/transactions`)
      .flush(mockPage);
    const alertReq = httpMock.expectOne(`${environment.apiUrl}/alerts`);
    expect(alertReq.request.method).toBe('GET');
    alertReq.flush({ unreadCount: 1, alerts: [] });

    component.onTransactionSaved({ ...mockTx, type: 'INCOME' });
    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/transactions`)
      .flush(mockPage);
    httpMock.expectNone(`${environment.apiUrl}/alerts`);
  });
});
