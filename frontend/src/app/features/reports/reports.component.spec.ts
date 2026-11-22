import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { environment } from '../../../environments/environment';
import { ReportExportDto } from '../../core/models/report.model';
import { ToastService } from '../../core/services/toast.service';
import { ReportsComponent } from './reports.component';

describe('ReportsComponent', () => {
  let httpMock: HttpTestingController;

  const pendingJob: ReportExportDto = {
    id: 'job-1',
    month: 11,
    year: 2026,
    format: 'PDF',
    status: 'PENDING',
    fileName: null,
    fileUrl: null,
    contentType: null,
    sizeBytes: null,
    errorMessage: null,
    createdAt: '2026-11-21T12:00:00Z',
    updatedAt: '2026-11-21T12:00:00Z',
    completedAt: null,
  };

  const completedJob: ReportExportDto = {
    ...pendingJob,
    status: 'COMPLETED',
    fileName: 'finpulse-report-2026-11.pdf',
    fileUrl: 'https://xyz.supabase.co/storage/v1/object/public/reports/u/finpulse-report-2026-11.pdf',
    contentType: 'application/pdf',
    sizeBytes: 48210,
    completedAt: '2026-11-21T12:00:05Z',
  };

  const failedJob: ReportExportDto = {
    id: 'job-2',
    month: 10,
    year: 2026,
    format: 'CSV',
    status: 'FAILED',
    fileName: null,
    fileUrl: null,
    contentType: null,
    sizeBytes: null,
    errorMessage: 'Supabase Storage belum dikonfigurasi.',
    createdAt: '2026-11-20T12:00:00Z',
    updatedAt: '2026-11-20T12:00:02Z',
    completedAt: '2026-11-20T12:00:02Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReportsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should load exports on init and render completed download link and failed message', () => {
    const fixture = TestBed.createComponent(ReportsComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/reports`);
    expect(req.request.method).toBe('GET');
    req.flush([completedJob, failedJob]);
    fixture.detectChanges();

    expect(component.isLoading()).toBeFalse();
    expect(component.exports().length).toBe(2);
    expect(component.hasActiveJobs()).toBeFalse();

    const el = fixture.nativeElement as HTMLElement;
    const text = el.textContent ?? '';
    expect(text).toContain('Laporan November 2026');
    expect(text).toContain('finpulse-report-2026-11.pdf');
    expect(text).toContain('47.1 KB');
    expect(text).toContain('Supabase Storage belum dikonfigurasi.');

    const downloadLink = el.querySelector('a[href*="finpulse-report-2026-11.pdf"]');
    expect(downloadLink).not.toBeNull();
    expect(downloadLink?.getAttribute('target')).toBe('_blank');
    expect(downloadLink?.getAttribute('rel')).toBe('noopener noreferrer');
  });

  it('should submit export request, auto-poll while PENDING, and stop polling once COMPLETED', fakeAsync(() => {
    const fixture = TestBed.createComponent(ReportsComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === `${environment.apiUrl}/reports`).flush([]);
    fixture.detectChanges();

    component.form.patchValue({ month: 11, year: 2026, format: 'PDF' });
    component.onSubmit();

    const postReq = httpMock.expectOne(`${environment.apiUrl}/reports/export`);
    expect(postReq.request.method).toBe('POST');
    expect(postReq.request.body).toEqual({ month: 11, year: 2026, format: 'PDF' });
    postReq.flush(pendingJob, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();

    expect(component.hasActiveJobs()).toBeTrue();

    // After 2500ms, auto-polling triggers GET /api/reports
    tick(2500);
    const poll1 = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/reports`);
    poll1.flush([{ ...pendingJob, status: 'PROCESSING' }]);
    fixture.detectChanges();
    expect(component.hasActiveJobs()).toBeTrue();

    // Next 2500ms poll completes the job
    tick(2500);
    const poll2 = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/reports`);
    poll2.flush([completedJob]);
    fixture.detectChanges();
    expect(component.hasActiveJobs()).toBeFalse();

    // Further ticks must NOT trigger any more HTTP calls
    tick(5000);
    httpMock.expectNone((r) => r.url === `${environment.apiUrl}/reports`);

    fixture.destroy();
  }));

  it('should show friendly error message when RabbitMQ returns 503 Service Unavailable', () => {
    const toastService = TestBed.inject(ToastService);
    const toastSpy = spyOn(toastService, 'error');

    const fixture = TestBed.createComponent(ReportsComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === `${environment.apiUrl}/reports`).flush([]);
    fixture.detectChanges();

    component.onSubmit();
    httpMock
      .expectOne(`${environment.apiUrl}/reports/export`)
      .flush(
        {
          timestamp: '2026-11-21T12:00:00Z',
          status: 503,
          error: 'Service Unavailable',
          message: 'RabbitMQ unavailable',
          path: '/api/reports/export',
        },
        { status: 503, statusText: 'Service Unavailable' }
      );
    fixture.detectChanges();

    expect(component.submitError()).toContain('Layanan ekspor sedang sibuk');
    expect(toastSpy).toHaveBeenCalledTimes(1);
  });

  it('should reject unsafe javascript download URLs', () => {
    const fixture = TestBed.createComponent(ReportsComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/reports`)
      .flush([{ ...completedJob, fileUrl: 'javascript:alert(1)' }]);
    fixture.detectChanges();

    expect(component.getSafeDownloadUrl('javascript:alert(1)')).toBeNull();
    const downloadLink = (fixture.nativeElement as HTMLElement).querySelector('a[target="_blank"]');
    expect(downloadLink).toBeNull();
  });

  it('should suppress duplicate toast on 401, guard double submit, and enforce 2020..2100 year bounds', () => {
    const toastService = TestBed.inject(ToastService);
    const toastSpy = spyOn(toastService, 'error');

    const fixture = TestBed.createComponent(ReportsComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === `${environment.apiUrl}/reports`)
      .flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });
    expect(toastSpy).not.toHaveBeenCalled();

    // Year out of bounds -> form invalid, no request sent
    component.form.patchValue({ month: 11, year: 2019, format: 'CSV' });
    expect(component.form.invalid).toBeTrue();
    component.onSubmit();
    httpMock.expectNone(`${environment.apiUrl}/reports/export`);

    component.form.patchValue({ month: 11, year: 2101, format: 'CSV' });
    expect(component.form.invalid).toBeTrue();
    component.onSubmit();
    httpMock.expectNone(`${environment.apiUrl}/reports/export`);

    // Valid submit + double submit guard
    component.form.patchValue({ month: 11, year: 2026, format: 'CSV' });
    component.onSubmit();
    component.onSubmit();

    const postReq = httpMock.expectOne(`${environment.apiUrl}/reports/export`);
    postReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });
    expect(toastSpy).not.toHaveBeenCalled();
  });
});
