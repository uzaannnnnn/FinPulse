import { TestBed } from '@angular/core/testing';
import { SyncStatusService } from './sync-status.service';

describe('SyncStatusService', () => {
  let service: SyncStatusService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SyncStatusService);
  });

  it('should start in idle state', () => {
    expect(service.status()).toBe('idle');
    expect(service.source()).toBeNull();
    expect(service.lastSyncedAt()).toBeNull();
  });

  it('should transition through syncing, synced (CACHE/DATABASE), error, and reset', () => {
    service.markSyncing();
    expect(service.status()).toBe('syncing');

    service.markSynced('CACHE', '2026-11-21T13:30:00Z');
    expect(service.status()).toBe('synced');
    expect(service.source()).toBe('CACHE');
    expect(service.lastSyncedAt()).toBe('2026-11-21T13:30:00Z');

    service.markSynced('DATABASE', '2026-11-21T13:35:00Z');
    expect(service.status()).toBe('synced');
    expect(service.source()).toBe('DATABASE');
    expect(service.lastSyncedAt()).toBe('2026-11-21T13:35:00Z');

    service.markError();
    expect(service.status()).toBe('error');

    service.reset();
    expect(service.status()).toBe('idle');
    expect(service.source()).toBeNull();
    expect(service.lastSyncedAt()).toBeNull();
  });
});
