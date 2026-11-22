import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { environment } from '../../../../environments/environment';
import { AlertListResponse, BudgetAlertDto } from '../../../core/models/alert.model';
import { AlertService } from '../../../core/services/alert.service';
import { ToastService } from '../../../core/services/toast.service';
import { AlertPanelComponent } from './alert-panel.component';

describe('AlertPanelComponent', () => {
  let httpMock: HttpTestingController;
  let alertService: AlertService;

  const mockAlert: BudgetAlertDto = {
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
    message: 'Pengeluaran kategori Makanan & Minuman melebihi batas anggaran.',
    isRead: false,
    createdAt: '2026-11-21T10:00:00Z',
    updatedAt: '2026-11-21T10:30:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AlertPanelComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    alertService = TestBed.inject(AlertService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function seedAlerts(response: AlertListResponse): void {
    alertService.loadAlerts().subscribe();
    httpMock.expectOne(`${environment.apiUrl}/alerts`).flush(response);
  }

  it('should display unread badge, cap at 99+, and open/close panel on toggle and Escape', () => {
    const fixture = TestBed.createComponent(AlertPanelComponent);
    const component = fixture.componentInstance;
    seedAlerts({ unreadCount: 120, alerts: [mockAlert] });
    fixture.detectChanges();

    const badge = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="unread-badge"]');
    expect(badge?.textContent?.trim()).toBe('99+');

    component.togglePanel();
    fixture.detectChanges();
    expect(component.isOpen()).toBeTrue();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Makanan & Minuman');
    expect(text).toContain('November 2026');
    expect(text).toContain('117.5%');
    expect(text).toContain('Rp 2.350.000');

    component.onEscapeKey();
    fixture.detectChanges();
    expect(component.isOpen()).toBeFalse();
  });

  it('should close panel on outside click', () => {
    const fixture = TestBed.createComponent(AlertPanelComponent);
    const component = fixture.componentInstance;
    component.togglePanel();
    fixture.detectChanges();
    expect(component.isOpen()).toBeTrue();

    document.body.click();
    fixture.detectChanges();
    expect(component.isOpen()).toBeFalse();
  });

  it('should mark single alert as read, mark all as read, delete alert, and refresh', () => {
    const fixture = TestBed.createComponent(AlertPanelComponent);
    const component = fixture.componentInstance;
    seedAlerts({ unreadCount: 1, alerts: [mockAlert] });
    component.togglePanel();
    fixture.detectChanges();

    component.markAsRead(mockAlert);
    httpMock
      .expectOne(`${environment.apiUrl}/alerts/alert-1/read`)
      .flush({ ...mockAlert, isRead: true });
    expect(alertService.unreadCount()).toBe(0);

    component.refreshAlerts();
    httpMock
      .expectOne(`${environment.apiUrl}/alerts`)
      .flush({ unreadCount: 1, alerts: [mockAlert] });
    expect(alertService.unreadCount()).toBe(1);

    component.markAllAsRead();
    httpMock
      .expectOne(`${environment.apiUrl}/alerts/read-all`)
      .flush(null, { status: 204, statusText: 'No Content' });
    expect(alertService.unreadCount()).toBe(0);

    component.deleteAlert(mockAlert);
    httpMock
      .expectOne(`${environment.apiUrl}/alerts/alert-1`)
      .flush(null, { status: 204, statusText: 'No Content' });
    expect(alertService.alerts().length).toBe(0);

    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Semua Anggaran Terkendali');
  });

  it('should sanitize malicious categoryColor and suppress error toast on 401 responses', () => {
    const toastService = TestBed.inject(ToastService);
    const toastSpy = spyOn(toastService, 'error');

    const fixture = TestBed.createComponent(AlertPanelComponent);
    const component = fixture.componentInstance;
    seedAlerts({ unreadCount: 1, alerts: [mockAlert] });
    fixture.detectChanges();

    expect(component.getCategoryColor('javascript:alert(1)')).toBe('#EF4444');
    expect(component.getCategoryColor('#10B981')).toBe('#10B981');

    component.markAsRead(mockAlert);
    httpMock
      .expectOne(`${environment.apiUrl}/alerts/alert-1/read`)
      .flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    component.deleteAlert(mockAlert);
    httpMock
      .expectOne(`${environment.apiUrl}/alerts/alert-1`)
      .flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(toastSpy).not.toHaveBeenCalled();
  });
});
