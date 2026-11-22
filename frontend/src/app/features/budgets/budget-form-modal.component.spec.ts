import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { environment } from '../../../environments/environment';
import { BudgetDto } from '../../core/models/budget.model';
import { CategoryDto } from '../../core/models/category.model';
import { ToastService } from '../../core/services/toast.service';
import { BudgetFormModalComponent } from './budget-form-modal.component';

describe('BudgetFormModalComponent', () => {
  let httpMock: HttpTestingController;

  const mockCategories: CategoryDto[] = [
    { id: 'cat-inc', name: 'Gaji', type: 'INCOME', isDefault: true },
    { id: 'cat-food', name: 'Makanan & Minuman', type: 'EXPENSE', isDefault: true },
    { id: 'cat-trans', name: 'Transportasi', type: 'EXPENSE', isDefault: true },
  ];

  const mockBudget: BudgetDto = {
    id: 'b-1',
    categoryId: 'cat-food',
    categoryName: 'Makanan & Minuman',
    categoryColor: '#F59E0B',
    month: 11,
    year: 2026,
    amountLimit: 2000000,
    spentAmount: 1000000,
    remainingAmount: 1000000,
    utilizationPercentage: 50,
    isOverBudget: false,
    createdAt: '2026-11-01T00:00:00Z',
    updatedAt: '2026-11-01T00:00:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BudgetFormModalComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should list only EXPENSE categories, mark existing ones as used, and create budget', () => {
    const fixture = TestBed.createComponent(BudgetFormModalComponent);
    fixture.componentRef.setInput('categories', mockCategories);
    fixture.componentRef.setInput('existingCategoryIds', ['cat-food']);
    fixture.componentRef.setInput('defaultMonth', 11);
    fixture.componentRef.setInput('defaultYear', 2026);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    const savedSpy = spyOn(component.saved, 'emit');

    expect(component.expenseCategories().map((c) => c.id)).toEqual(['cat-food', 'cat-trans']);
    expect(component.isCategoryUsed('cat-food')).toBeTrue();
    expect(component.isCategoryUsed('cat-trans')).toBeFalse();

    component.form.patchValue({
      categoryId: 'cat-trans',
      amountLimit: 1500000,
    });
    component.onSubmit();

    const req = httpMock.expectOne(`${environment.apiUrl}/budgets`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      categoryId: 'cat-trans',
      month: 11,
      year: 2026,
      amountLimit: 1500000,
    });
    req.flush({ ...mockBudget, id: 'b-2', categoryId: 'cat-trans', amountLimit: 1500000 });

    expect(savedSpy).toHaveBeenCalled();
  });

  it('should update amountLimit in edit mode via PUT', () => {
    const fixture = TestBed.createComponent(BudgetFormModalComponent);
    fixture.componentRef.setInput('budget', mockBudget);
    fixture.componentRef.setInput('categories', mockCategories);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    const savedSpy = spyOn(component.saved, 'emit');

    expect(component.isEditMode()).toBeTrue();
    component.form.controls.amountLimit.setValue(3000000);
    component.onSubmit();

    const req = httpMock.expectOne(`${environment.apiUrl}/budgets/${mockBudget.id}`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ amountLimit: 3000000 });
    req.flush({ ...mockBudget, amountLimit: 3000000 });

    expect(savedSpy).toHaveBeenCalled();
  });

  it('should handle 409 duplicate budget error, reject > 2 decimals, guard year <= 2100, and suppress 401 toast', () => {
    const toastService = TestBed.inject(ToastService);
    const toastSpy = spyOn(toastService, 'error');

    const fixture = TestBed.createComponent(BudgetFormModalComponent);
    fixture.componentRef.setInput('categories', mockCategories);
    fixture.componentRef.setInput('defaultMonth', 11);
    fixture.componentRef.setInput('defaultYear', 2026);
    fixture.detectChanges();

    const component = fixture.componentInstance;

    // > 2 decimals rejected
    component.form.controls.amountLimit.setValue(1000.999);
    expect(component.form.controls.amountLimit.hasError('maxDecimals')).toBeTrue();

    // Out-of-range year ignored
    component.onPeriodYearChange('2101');
    expect(component.formYear()).toBe(2026);

    // 409 Conflict handling
    component.form.patchValue({
      categoryId: 'cat-food',
      amountLimit: 2000000,
    });
    component.onSubmit();

    const conflictReq = httpMock.expectOne(`${environment.apiUrl}/budgets`);
    conflictReq.flush(
      {
        timestamp: '2026-11-21T10:00:00Z',
        status: 409,
        error: 'Conflict',
        message: 'Anggaran untuk kategori dan periode bulan/tahun tersebut sudah ada',
        path: '/api/budgets',
      },
      { status: 409, statusText: 'Conflict' }
    );

    expect(component.isSubmitting()).toBeFalse();
    expect(component.submitError()).toContain('sudah ada');
    expect(toastSpy).toHaveBeenCalledTimes(1);

    // 401 suppresses duplicate error toast
    toastSpy.calls.reset();
    component.onSubmit();
    const unauthReq = httpMock.expectOne(`${environment.apiUrl}/budgets`);
    unauthReq.flush(
      {
        timestamp: '2026-11-21T10:01:00Z',
        status: 401,
        error: 'Unauthorized',
        message: 'Token kedaluwarsa',
        path: '/api/budgets',
      },
      { status: 401, statusText: 'Unauthorized' }
    );
    expect(toastSpy).not.toHaveBeenCalled();
  });
});
