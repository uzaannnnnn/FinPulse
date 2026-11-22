import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HeartbeatWidgetComponent } from './heartbeat-widget.component';

@Component({
  standalone: true,
  imports: [HeartbeatWidgetComponent],
  template: `<app-heartbeat-widget [income]="income()" [expense]="expense()" />`,
})
class TestHeartbeatHostComponent {
  readonly income = signal(10000000);
  readonly expense = signal(4000000);
}

describe('HeartbeatWidgetComponent', () => {
  it('should switch zone, BPM, and color dynamically based on expense-to-income ratio', () => {
    TestBed.configureTestingModule({
      imports: [TestHeartbeatHostComponent],
    });

    const fixture = TestBed.createComponent(TestHeartbeatHostComponent);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="heartbeat-zone-badge"]')?.textContent?.trim()).toBe('Sehat');
    expect(el.querySelector('[data-testid="heartbeat-ratio"]')?.textContent?.trim()).toBe('40.0%');
    expect(el.querySelector('[data-testid="heartbeat-bpm"]')?.textContent?.trim()).toBe('66 BPM');

    // Elevated zone (70%)
    fixture.componentInstance.expense.set(7000000);
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="heartbeat-zone-badge"]')?.textContent?.trim()).toBe('Waspada');
    expect(el.querySelector('[data-testid="heartbeat-bpm"]')?.textContent?.trim()).toBe('92 BPM');

    // Critical zone (95%)
    fixture.componentInstance.expense.set(9500000);
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="heartbeat-zone-badge"]')?.textContent?.trim()).toBe('Kritis');
    expect(el.querySelector('[data-testid="heartbeat-bpm"]')?.textContent?.trim()).toBe('132 BPM');

    // Idle zone (0 / 0)
    fixture.componentInstance.income.set(0);
    fixture.componentInstance.expense.set(0);
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="heartbeat-zone-badge"]')?.textContent?.trim()).toBe('Standby');
    expect(el.querySelector('[data-testid="heartbeat-bpm"]')?.textContent?.trim()).toBe('0 BPM');

    fixture.destroy();
  });
});
