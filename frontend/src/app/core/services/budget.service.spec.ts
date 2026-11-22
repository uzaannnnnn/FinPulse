import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { BudgetDto, CreateBudgetRequest, UpdateBudgetRequest } from '../models/budget.model';
import { BudgetService } from './budget.service';

describe('BudgetService', () => {
  let service: BudgetService;
  let httpMock: HttpTestingController;

  const mockBudget: BudgetDto = {
    id: '22222222-2222-2222-2222-222222222222',
    categoryId: '660e8400-e29b-41d4-a716-446655440001',
    categoryName: 'Makanan & Minuman',
    categoryIcon: 'utensils',
    categoryColor: '#F59E0B',
    month: 11,
    year: 2026,
    amountLimit: 2500000,
    spentAmount: 1750000,
    remainingAmount: 750000,
    utilizationPercentage: 70,
    isOverBudget: false,
    createdAt: '2026-11-01T00:00:00Z',
    updatedAt: '2026-11-21T00:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BudgetService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should list budgets with month and year query params', () => {
    let result: BudgetDto[] | undefined;
    service.listBudgets(11, 2026).subscribe((res) => (result = res));

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/budgets`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('month')).toBe('11');
    expect(req.request.params.get('year')).toBe('2026');
    req.flush([mockBudget]);

    expect(result).toEqual([mockBudget]);
  });

  it('should list budgets without query params when period omitted', () => {
    service.listBudgets().subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/budgets`);
    expect(req.request.params.has('month')).toBeFalse();
    expect(req.request.params.has('year')).toBeFalse();
    req.flush([]);
  });

  it('should create budget via POST', () => {
    const payload: CreateBudgetRequest = {
      categoryId: mockBudget.categoryId,
      month: 11,
      year: 2026,
      amountLimit: 2500000,
    };
    let result: BudgetDto | undefined;
    service.createBudget(payload).subscribe((res) => (result = res));

    const req = httpMock.expectOne(`${environment.apiUrl}/budgets`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(mockBudget, { status: 201, statusText: 'Created' });

    expect(result?.id).toBe(mockBudget.id);
  });

  it('should update budget via PUT', () => {
    const payload: UpdateBudgetRequest = { amountLimit: 3000000 };
    let result: BudgetDto | undefined;
    service.updateBudget(mockBudget.id, payload).subscribe((res) => (result = res));

    const req = httpMock.expectOne(`${environment.apiUrl}/budgets/${mockBudget.id}`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    req.flush({ ...mockBudget, amountLimit: 3000000 });

    expect(result?.amountLimit).toBe(3000000);
  });

  it('should delete budget via DELETE', () => {
    let completed = false;
    service.deleteBudget(mockBudget.id).subscribe(() => (completed = true));

    const req = httpMock.expectOne(`${environment.apiUrl}/budgets/${mockBudget.id}`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(completed).toBeTrue();
  });
});
