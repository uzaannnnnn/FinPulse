import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { AlertListResponse, BudgetAlertDto } from '../models/alert.model';
import { AlertService } from './alert.service';

describe('AlertService', () => {
  let service: AlertService;
  let httpMock: HttpTestingController;

  const mockAlert1: BudgetAlertDto = {
    id: 'alert-1',
    budgetId: 'budget-1',
    categoryId: 'cat-food',
    categoryName: 'Makanan & Minuman',
    categoryColor: '#F59E0B',
    month: 11,
    year: 2026,
    amountLimit: 2000000,
    spentAmount: 2350000,
    utilizationPercentage: 117.5,
    message: 'Pengeluaran kategori Makanan & Minuman pada Nov 2026 mencapai 117.50% dari batas anggaran.',
    isRead: false,
    createdAt: '2026-11-21T10:00:00Z',
    updatedAt: '2026-11-21T10:30:00Z',
  };

  const mockAlert2: BudgetAlertDto = {
    id: 'alert-2',
    budgetId: 'budget-2',
    categoryId: 'cat-transport',
    categoryName: 'Transportasi',
    categoryColor: '#3B82F6',
    month: 11,
    year: 2026,
    amountLimit: 1000000,
    spentAmount: 1100000,
    utilizationPercentage: 110.0,
    message: 'Pengeluaran kategori Transportasi melebihi anggaran.',
    isRead: true,
    createdAt: '2026-11-20T08:00:00Z',
    updatedAt: '2026-11-20T09:00:00Z',
  };

  const mockResponse: AlertListResponse = {
    unreadCount: 1,
    alerts: [mockAlert1, mockAlert2],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AlertService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should load alerts and update signals', () => {
    let response: AlertListResponse | undefined;
    service.loadAlerts(true, 10).subscribe((res) => (response = res));

    expect(service.isLoading()).toBeTrue();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/alerts`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('unreadOnly')).toBe('true');
    expect(req.request.params.get('limit')).toBe('10');
    req.flush(mockResponse);

    expect(response).toEqual(mockResponse);
    expect(service.isLoading()).toBeFalse();
    expect(service.alerts().length).toBe(2);
    expect(service.unreadCount()).toBe(1);
  });

  it('should mark single alert as read and decrement unreadCount', () => {
    service.loadAlerts().subscribe();
    httpMock.expectOne(`${environment.apiUrl}/alerts`).flush(mockResponse);

    const updatedAlert: BudgetAlertDto = { ...mockAlert1, isRead: true };
    service.markAsRead('alert-1').subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/alerts/alert-1/read`);
    expect(req.request.method).toBe('PATCH');
    req.flush(updatedAlert);

    expect(service.unreadCount()).toBe(0);
    expect(service.alerts()[0].isRead).toBeTrue();
  });

  it('should mark all alerts as read and zero out unreadCount', () => {
    service.loadAlerts().subscribe();
    httpMock.expectOne(`${environment.apiUrl}/alerts`).flush(mockResponse);

    service.markAllAsRead().subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/alerts/read-all`);
    expect(req.request.method).toBe('PATCH');
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(service.unreadCount()).toBe(0);
    expect(service.alerts().every((a) => a.isRead)).toBeTrue();
  });

  it('should delete alert and decrement unreadCount when alert was unread', () => {
    service.loadAlerts().subscribe();
    httpMock.expectOne(`${environment.apiUrl}/alerts`).flush(mockResponse);

    service.deleteAlert('alert-1').subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/alerts/alert-1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(service.alerts().length).toBe(1);
    expect(service.alerts()[0].id).toBe('alert-2');
    expect(service.unreadCount()).toBe(0);
  });

  it('should reset all signals to initial state', () => {
    service.loadAlerts().subscribe();
    httpMock.expectOne(`${environment.apiUrl}/alerts`).flush(mockResponse);

    service.reset();
    expect(service.alerts()).toEqual([]);
    expect(service.unreadCount()).toBe(0);
    expect(service.isLoading()).toBeFalse();
  });
});
