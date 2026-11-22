import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { CountUpDirective } from './count-up.directive';

@Component({
  standalone: true,
  imports: [CountUpDirective],
  template: `<span data-testid="val" [appCountUp]="value()" [duration]="duration()"></span>`,
})
class TestCountUpHostComponent {
  readonly value = signal(1250000);
  readonly duration = signal(0);
}

describe('CountUpDirective', () => {
  it('should render formatted IDR value immediately when duration is 0 or NoopAnimations is active', () => {
    TestBed.configureTestingModule({
      imports: [TestCountUpHostComponent],
      providers: [provideNoopAnimations()],
    });

    const fixture = TestBed.createComponent(TestCountUpHostComponent);
    fixture.detectChanges();

    const span = fixture.nativeElement.querySelector('[data-testid="val"]') as HTMLElement;
    expect(span.textContent).toBe('Rp 1.250.000');

    fixture.componentInstance.value.set(-450000);
    fixture.detectChanges();
    expect(span.textContent).toBe('-Rp 450.000');
    fixture.destroy();
  });

  it('should animate smoothly when browser animations are enabled', (done) => {
    TestBed.configureTestingModule({
      imports: [TestCountUpHostComponent],
    });

    const fixture = TestBed.createComponent(TestCountUpHostComponent);
    fixture.componentInstance.duration.set(80);
    fixture.componentInstance.value.set(500000);
    fixture.detectChanges();

    setTimeout(() => {
      const span = fixture.nativeElement.querySelector('[data-testid="val"]') as HTMLElement;
      expect(span.textContent).toBe('Rp 500.000');
      fixture.destroy();
      done();
    }, 150);
  });
});
