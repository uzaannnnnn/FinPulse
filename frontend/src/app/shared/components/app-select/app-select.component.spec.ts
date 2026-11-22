import { Component, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { AppSelectComponent, AppSelectOption } from './app-select.component';

@Component({
  standalone: true,
  imports: [AppSelectComponent, ReactiveFormsModule],
  template: `
    <app-select
      selectId="month-select"
      [options]="options"
      [value]="selected()"
      (valueChange)="selected.set($event)"
    />
    <app-select
      selectId="cva-select"
      [options]="options"
      [formControl]="control"
    />
  `,
})
class TestSelectHostComponent {
  readonly options: AppSelectOption<number>[] = [
    { value: 1, label: 'Januari' },
    { value: 11, label: 'November' },
  ];
  readonly selected = signal<number>(11);
  readonly control = new FormControl<number>(11, { nonNullable: true });
}

describe('AppSelectComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestSelectHostComponent],
      providers: [provideNoopAnimations()],
    }).compileComponents();
  });

  it('should render active option label accurately and emit valueChange on selection', () => {
    const fixture = TestBed.createComponent(TestSelectHostComponent);
    fixture.detectChanges();

    const trigger = fixture.nativeElement.querySelector('#month-select') as HTMLButtonElement;
    expect(trigger.textContent?.trim()).toBe('November');

    trigger.click();
    fixture.detectChanges();

    const items = fixture.nativeElement.querySelectorAll('[role="option"]') as NodeListOf<HTMLElement>;
    expect(items.length).toBe(2);
    items[0].click();
    fixture.detectChanges();

    expect(fixture.componentInstance.selected()).toBe(1);
    expect(trigger.textContent?.trim()).toBe('Januari');
    fixture.destroy();
  });

  it('should work with Reactive Forms ControlValueAccessor', () => {
    const fixture = TestBed.createComponent(TestSelectHostComponent);
    fixture.detectChanges();

    const cvaTrigger = fixture.nativeElement.querySelector('#cva-select') as HTMLButtonElement;
    expect(cvaTrigger.textContent?.trim()).toBe('November');

    fixture.componentInstance.control.setValue(1);
    fixture.detectChanges();
    expect(cvaTrigger.textContent?.trim()).toBe('Januari');
    fixture.destroy();
  });
});
