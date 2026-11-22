import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DashboardSummaryDto } from '../models/dashboard.model';

@Injectable({
  providedIn: 'root',
})
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/dashboard/summary`;

  getSummary(month?: number, year?: number, monthsBack?: number): Observable<DashboardSummaryDto> {
    let params = new HttpParams();
    if (month !== undefined && month !== null) {
      params = params.set('month', String(month));
    }
    if (year !== undefined && year !== null) {
      params = params.set('year', String(year));
    }
    if (monthsBack !== undefined && monthsBack !== null) {
      params = params.set('monthsBack', String(monthsBack));
    }
    return this.http.get<DashboardSummaryDto>(this.apiUrl, { params });
  }
}
