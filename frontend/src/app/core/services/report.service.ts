import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateReportExportRequest, ReportExportDto } from '../models/report.model';

@Injectable({
  providedIn: 'root',
})
export class ReportService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/reports`;

  requestExport(req: CreateReportExportRequest): Observable<ReportExportDto> {
    return this.http.post<ReportExportDto>(`${this.apiUrl}/export`, req);
  }

  listExports(limit?: number): Observable<ReportExportDto[]> {
    let params = new HttpParams();
    if (limit !== undefined && limit !== null) {
      params = params.set('limit', String(limit));
    }
    return this.http.get<ReportExportDto[]>(this.apiUrl, { params });
  }

  getExport(id: string): Observable<ReportExportDto> {
    return this.http.get<ReportExportDto>(`${this.apiUrl}/${id}`);
  }
}
