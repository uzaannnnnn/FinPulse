import { Injectable, signal } from '@angular/core';

export type SyncStatus = 'idle' | 'syncing' | 'synced' | 'error';
export type SyncDataSource = 'CACHE' | 'DATABASE' | null;

@Injectable({
  providedIn: 'root',
})
export class SyncStatusService {
  private readonly statusSignal = signal<SyncStatus>('idle');
  private readonly sourceSignal = signal<SyncDataSource>(null);
  private readonly lastSyncedAtSignal = signal<string | null>(null);

  readonly status = this.statusSignal.asReadonly();
  readonly source = this.sourceSignal.asReadonly();
  readonly lastSyncedAt = this.lastSyncedAtSignal.asReadonly();

  markSyncing(): void {
    this.statusSignal.set('syncing');
  }

  markSynced(source: 'CACHE' | 'DATABASE', timestamp: string): void {
    this.statusSignal.set('synced');
    this.sourceSignal.set(source);
    this.lastSyncedAtSignal.set(timestamp);
  }

  markError(): void {
    this.statusSignal.set('error');
  }

  reset(): void {
    this.statusSignal.set('idle');
    this.sourceSignal.set(null);
    this.lastSyncedAtSignal.set(null);
  }
}
