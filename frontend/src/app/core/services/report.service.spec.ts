import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { CreateReportExportRequest, ReportExportDto } from '../models/report.model';
import { ReportService } from './report.service';

describe('ReportService', () => {
  let service: ReportService;
  let httpMock: HttpTestingController;

  const mockReport: ReportExportDto = {
    id: 'report-1',
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

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReportService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should POST report export request', () => {
    const payload: CreateReportExportRequest = { month: 11, year: 2026, format: 'PDF' };
    let result: ReportExportDto | undefined;

    service.requestExport(payload).subscribe((res) => (result = res));

    const req = httpMock.expectOne(`${environment.apiUrl}/reports/export`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(mockReport, { status: 202, statusText: 'Accepted' });

    expect(result).toEqual(mockReport);
  });

  it('should GET report exports list with optional limit param', () => {
    let result: ReportExportDto[] | undefined;

    service.listExports(15).subscribe((res) => (result = res));

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/reports`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('limit')).toBe('15');
    req.flush([mockReport]);

    expect(result).toEqual([mockReport]);
  });

  it('should GET report exports without params when limit is omitted', () => {
    service.listExports().subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/reports`);
    expect(req.request.params.keys().length).toBe(0);
    req.flush([mockReport]);
  });

  it('should GET single report export by id', () => {
    let result: ReportExportDto | undefined;

    service.getExport('report-1').subscribe((res) => (result = res));

    const req = httpMock.expectOne(`${environment.apiUrl}/reports/report-1`);
    expect(req.request.method).toBe('GET');
    req.flush({ ...mockReport, status: 'COMPLETED' });

    expect(result?.status).toBe('COMPLETED');
  });
});
