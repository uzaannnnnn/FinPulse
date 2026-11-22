import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  BudgetDto,
  CreateBudgetRequest,
  UpdateBudgetRequest,
} from '../models/budget.model';

@Injectable({
  providedIn: 'root',
})
export class BudgetService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/budgets`;

  listBudgets(month?: number, year?: number): Observable<BudgetDto[]> {
    let params = new HttpParams();
    if (month !== undefined && month !== null) {
      params = params.set('month', String(month));
    }
    if (year !== undefined && year !== null) {
      params = params.set('year', String(year));
    }
    return this.http.get<BudgetDto[]>(this.apiUrl, { params });
  }

  createBudget(req: CreateBudgetRequest): Observable<BudgetDto> {
    return this.http.post<BudgetDto>(this.apiUrl, req);
  }

  updateBudget(id: string, req: UpdateBudgetRequest): Observable<BudgetDto> {
    return this.http.put<BudgetDto>(`${this.apiUrl}/${id}`, req);
  }

  deleteBudget(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
