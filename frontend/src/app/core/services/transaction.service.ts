import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CreateTransactionRequest,
  ReceiptUploadResponse,
  TransactionDto,
  TransactionFilterParams,
  TransactionPageResponse,
  UpdateTransactionRequest,
} from '../models/transaction.model';

@Injectable({
  providedIn: 'root',
})
export class TransactionService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/transactions`;

  listTransactions(params?: TransactionFilterParams): Observable<TransactionPageResponse> {
    let httpParams = new HttpParams();

    if (params) {
      if (params.page !== undefined && params.page !== null) {
        httpParams = httpParams.set('page', String(params.page));
      }
      if (params.size !== undefined && params.size !== null) {
        httpParams = httpParams.set('size', String(params.size));
      }
      if (params.sortBy) {
        httpParams = httpParams.set('sortBy', params.sortBy);
      }
      if (params.sortDir) {
        httpParams = httpParams.set('sortDir', params.sortDir);
      }
      if (params.type) {
        httpParams = httpParams.set('type', params.type);
      }
      if (params.categoryId) {
        httpParams = httpParams.set('categoryId', params.categoryId);
      }
      if (params.startDate) {
        httpParams = httpParams.set('startDate', params.startDate);
      }
      if (params.endDate) {
        httpParams = httpParams.set('endDate', params.endDate);
      }
      if (params.search && params.search.trim().length > 0) {
        httpParams = httpParams.set('search', params.search.trim());
      }
    }

    return this.http.get<TransactionPageResponse>(this.apiUrl, { params: httpParams });
  }

  getTransaction(id: string): Observable<TransactionDto> {
    return this.http.get<TransactionDto>(`${this.apiUrl}/${id}`);
  }

  createTransaction(req: CreateTransactionRequest): Observable<TransactionDto> {
    return this.http.post<TransactionDto>(this.apiUrl, req);
  }

  updateTransaction(id: string, req: UpdateTransactionRequest): Observable<TransactionDto> {
    return this.http.put<TransactionDto>(`${this.apiUrl}/${id}`, req);
  }

  deleteTransaction(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  uploadReceipt(file: File): Observable<ReceiptUploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ReceiptUploadResponse>(`${this.apiUrl}/receipt`, formData);
  }
}
