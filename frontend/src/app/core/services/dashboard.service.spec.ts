import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { DashboardSummaryDto } from '../models/dashboard.model';
import { DashboardService } from './dashboard.service';

describe('DashboardService', () => {
  let service: DashboardService;
  let httpMock: HttpTestingController;

  const mockSummary: DashboardSummaryDto = {
    month: 11,
    year: 2026,
    totalIncome: 12000000,
    totalExpense: 5400000,
    balance: 6600000,
    cumulativeBalance: 28500000,
    transactionCount: 18,
    cashflowSeries: [
      {
        year: 2026,
        month: 11,
        label: 'Nov 2026',
        totalIncome: 12000000,
        totalExpense: 5400000,
        net: 6600000,
      },
    ],
    expenseByCategory: [],
    budgetProgress: [],
    recentTransactions: [],
    generatedAt: '2026-11-21T13:30:00Z',
    fromCache: false,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DashboardService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch dashboard summary with month, year, and monthsBack params', () => {
    let result: DashboardSummaryDto | undefined;
    service.getSummary(11, 2026, 6).subscribe((res) => (result = res));

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/dashboard/summary`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('month')).toBe('11');
    expect(req.request.params.get('year')).toBe('2026');
    expect(req.request.params.get('monthsBack')).toBe('6');
    req.flush(mockSummary);

    expect(result).toEqual(mockSummary);
  });

  it('should fetch dashboard summary without params when omitted', () => {
    service.getSummary().subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/dashboard/summary`);
    expect(req.request.params.keys().length).toBe(0);
    req.flush(mockSummary);
  });
});
