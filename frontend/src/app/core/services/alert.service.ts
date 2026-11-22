import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { finalize, tap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { AlertListResponse, BudgetAlertDto } from '../models/alert.model';

@Injectable({
  providedIn: 'root',
})
export class AlertService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/alerts`;

  private readonly alertsSignal = signal<BudgetAlertDto[]>([]);
  private readonly unreadCountSignal = signal<number>(0);
  private readonly isLoadingSignal = signal<boolean>(false);

  readonly alerts = this.alertsSignal.asReadonly();
  readonly unreadCount = this.unreadCountSignal.asReadonly();
  readonly isLoading = this.isLoadingSignal.asReadonly();

  loadAlerts(unreadOnly?: boolean, limit?: number): Observable<AlertListResponse> {
    let params = new HttpParams();
    if (unreadOnly !== undefined && unreadOnly !== null) {
      params = params.set('unreadOnly', String(unreadOnly));
    }
    if (limit !== undefined && limit !== null) {
      params = params.set('limit', String(limit));
    }

    this.isLoadingSignal.set(true);
    return this.http.get<AlertListResponse>(this.apiUrl, { params }).pipe(
      tap((response) => {
        this.alertsSignal.set(response.alerts ?? []);
        this.unreadCountSignal.set(Math.max(0, response.unreadCount ?? 0));
      }),
      finalize(() => this.isLoadingSignal.set(false))
    );
  }

  markAsRead(id: string): Observable<BudgetAlertDto> {
    return this.http.patch<BudgetAlertDto>(`${this.apiUrl}/${id}/read`, {}).pipe(
      tap((updated) => {
        const previous = this.alertsSignal().find((item) => item.id === id);
        this.alertsSignal.update((items) =>
          items.map((item) => (item.id === id ? updated : item))
        );
        if (previous && !previous.isRead && updated.isRead) {
          this.unreadCountSignal.update((count) => Math.max(0, count - 1));
        }
      })
    );
  }

  markAllAsRead(): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/read-all`, {}).pipe(
      tap(() => {
        this.alertsSignal.update((items) =>
          items.map((item) => (item.isRead ? item : { ...item, isRead: true }))
        );
        this.unreadCountSignal.set(0);
      })
    );
  }

  deleteAlert(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(
      tap(() => {
        const target = this.alertsSignal().find((item) => item.id === id);
        this.alertsSignal.update((items) => items.filter((item) => item.id !== id));
        if (target && !target.isRead) {
          this.unreadCountSignal.update((count) => Math.max(0, count - 1));
        }
      })
    );
  }

  reset(): void {
    this.alertsSignal.set([]);
    this.unreadCountSignal.set(0);
    this.isLoadingSignal.set(false);
  }
}
