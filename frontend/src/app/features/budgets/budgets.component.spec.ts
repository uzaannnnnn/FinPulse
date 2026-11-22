import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { environment } from '../../../environments/environment';
import { BudgetDto } from '../../core/models/budget.model';
import { CategoryDto } from '../../core/models/category.model';
import { ToastService } from '../../core/services/toast.service';
import { BudgetsComponent } from './budgets.component';

describe('BudgetsComponent', () => {
  let httpMock: HttpTestingController;

  const mockExpenseCategories: CategoryDto[] = [
    {
      id: 'cat-food',
      name: 'Makanan & Minuman',
      type: 'EXPENSE',
      icon: 'utensils',
      color: '#F59E0B',
      isDefault: true,
    },
    {
      id: 'cat-transport',
      name: 'Transportasi',
      type: 'EXPENSE',
      icon: 'car',
      color: '#3B82F6',
      isDefault: true,
    },
  ];

  const mockBudgets: BudgetDto[] = [
    {
      id: 'b-1',
      categoryId: 'cat-food',
      categoryName: 'Makanan & Minuman',
      categoryColor: '#F59E0B',
      month: 11,
      year: 2026,
      amountLimit: 2000000,
      spentAmount: 2310000,
      remainingAmount: -310000,
      utilizationPercentage: 115.5,
      isOverBudget: true,
      createdAt: '2026-11-01T00:00:00Z',
      updatedAt: '2026-11-21T00:00:00Z',
    },
    {
      id: 'b-2',
      categoryId: 'cat-transport',
      categoryName: 'Transportasi',
      categoryColor: '#3B82F6',
      month: 11,
      year: 2026,
      amountLimit: 1000000,
      spentAmount: 850000,
      remainingAmount: 150000,
      utilizationPercentage: 85.0,
      isOverBudget: false,
      createdAt: '2026-11-01T00:00:00Z',
      updatedAt: '2026-11-21T00:00:00Z',
    },
    {
      id: 'b-3',
      categoryId: 'cat-bills',
      categoryName: 'Tagihan',
      categoryColor: '#10B981',
      month: 11,
      year: 2026,
      amountLimit: 1000000,
      spentAmount: 400000,
      remainingAmount: 600000,
      utilizationPercentage: 40.0,
      isOverBudget: false,
      createdAt: '2026-11-01T00:00:00Z',
      updatedAt: '2026-11-21T00:00:00Z',
    },
  ];

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [BudgetsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  function flushInitial(budgets: BudgetDto[] = mockBudgets): void {
    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/categories` && r.params.get('type') === 'EXPENSE')
      .flush(mockExpenseCategories);
    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/budgets`)
      .flush(budgets);
  }

  it('should load budgets and render totals, dynamic statuses, and clamped progress bars', () => {
    const fixture = TestBed.createComponent(BudgetsComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    flushInitial();
    fixture.detectChanges();

    expect(component.isLoading()).toBeFalse();
    expect(component.totalLimit()).toBe(4000000);
    expect(component.totalSpent()).toBe(3560000);
    expect(component.totalRemaining()).toBe(440000);

    expect(component.getStatusLabel(mockBudgets[0])).toBe('Melebihi Batas');
    expect(component.getStatusLabel(mockBudgets[1])).toBe('Mendekati Batas');
    expect(component.getStatusLabel(mockBudgets[2])).toBe('Aman');

    expect(component.getProgressBarClass(mockBudgets[0])).toBe('bg-rose-500');
    expect(component.getProgressBarClass(mockBudgets[1])).toBe('bg-amber-500');
    expect(component.getProgressBarClass(mockBudgets[2])).toBe('bg-emerald-500');

    expect(component.getClampedPercentage(115.5)).toBe(100);

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Rp 4.000.000');
    expect(text).toContain('Rp 3.560.000');
    expect(text).toContain('115.5%');
    expect(text).toContain('Melebihi sebesar');
    expect(text).toContain('Rp 310.000');
  });

  it('should reload budgets when period month or year changes', () => {
    const fixture = TestBed.createComponent(BudgetsComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    flushInitial();

    component.onMonthChange('3');
    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/budgets` && r.params.get('month') === '3'
    );
    req.flush([]);
    fixture.detectChanges();

    expect(component.budgets().length).toBe(0);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Belum Ada Anggaran');
  });

  it('should delete budget after confirmation and reload list', () => {
    const fixture = TestBed.createComponent(BudgetsComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    flushInitial();

    component.openDeleteConfirm(mockBudgets[0]);
    expect(component.deletingBudget()?.id).toBe('b-1');

    component.confirmDelete();
    const delReq = httpMock.expectOne(`${environment.apiUrl}/budgets/b-1`);
    expect(delReq.request.method).toBe('DELETE');
    delReq.flush(null, { status: 204, statusText: 'No Content' });

    const reloadReq = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/budgets`);
    reloadReq.flush(mockBudgets.slice(1));

    expect(component.deletingBudget()).toBeNull();
    expect(component.budgets().length).toBe(2);
  });

  it('should not trigger duplicate error toast on 401 unauthorized response', () => {
    const toastService = TestBed.inject(ToastService);
    const toastSpy = spyOn(toastService, 'error');

    const fixture = TestBed.createComponent(BudgetsComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/categories` && r.params.get('type') === 'EXPENSE')
      .flush(mockExpenseCategories);
    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/budgets`)
      .flush(
        {
          timestamp: '2026-11-21T10:00:00Z',
          status: 401,
          error: 'Unauthorized',
          message: 'Token tidak valid',
          path: '/api/budgets',
        },
        { status: 401, statusText: 'Unauthorized' }
      );

    expect(component.errorMessage()).toBe('Token tidak valid');
    expect(toastSpy).not.toHaveBeenCalled();
  });
});
