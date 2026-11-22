import { CommonModule } from '@angular/common';
import {
  Component,
  computed,
  effect,
  ElementRef,
  forwardRef,
  HostListener,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { modalPopIn } from '../../animations';

export interface AppSelectOption<T = string | number> {
  value: T;
  label: string;
  disabled?: boolean;
  badge?: string;
}

/**
 * Custom Neo-Brutalist dropdown select replacing native `<select>` elements.
 * Supports both Angular Reactive/Template Forms (`ControlValueAccessor`)
 * and direct signal/property bindings (`[value]` + `(valueChange)`).
 */
@Component({
  selector: 'app-select',
  standalone: true,
  imports: [CommonModule],
  animations: [modalPopIn],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => AppSelectComponent),
      multi: true,
    },
  ],
  template: `
    <div class="relative inline-block w-full text-left" [attr.data-testid]="selectId() || null">
      <button
        type="button"
        [id]="selectId() || null"
        [disabled]="isDisabled()"
        [attr.aria-label]="ariaLabel() || selectedLabel()"
        [attr.aria-expanded]="isOpen()"
        aria-haspopup="listbox"
        (click)="toggleDropdown()"
        (keydown)="onTriggerKeydown($event)"
        class=" flex w-full items-center justify-between gap-2 rounded-xl border-2 border-fp-ink bg-white text-fp-ink transition-all duration-150 focus:outline-none disabled:cursor-not-allowed disabled:opacity-60"
        [ngClass]="[
          compact() ? 'px-2.5 py-1.5 text-xs font-bold' : 'px-3.5 py-2.5 text-sm font-bold',
          mono() ? 'fp-num' : '',
          isOpen() ? 'shadow-brutal -translate-x-[1px] -translate-y-[1px] bg-fp-paper' : 'shadow-brutal-sm hover:bg-fp-canvas/60',
          invalid() ? '!border-[#FF4D6D]' : ''
        ]"
      >
        <span
          class="truncate"
          [class.text-slate-400]="!hasSelectedOption()"
          [class.text-fp-ink]="hasSelectedOption()"
        >
          {{ selectedLabel() }}
        </span>

        <svg
          class="h-4 w-4 shrink-0 text-fp-ink transition-transform duration-200"
          [class.rotate-180]="isOpen()"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          stroke-width="2.6"
        >
          <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      @if (isOpen()) {
        <ul
          @modalPopIn
          role="listbox"
          [attr.aria-label]="ariaLabel() || 'Pilihan dropdown'"
          class="absolute left-0 right-0 z-50 mt-1.5 max-h-60 min-w-[10rem] overflow-y-auto rounded-xl border-2 border-fp-ink bg-fp-paper p-1.5 shadow-brutal-lg focus:outline-none"
        >
          @for (opt of options(); track opt.value) {
            <li
              role="option"
              [attr.aria-selected]="isSelected(opt.value)"
              [attr.aria-disabled]="opt.disabled || false"
              (click)="selectOption(opt)"
              class="flex items-center justify-between gap-2 rounded-lg px-3 py-2 text-xs font-bold transition-colors sm:text-sm"
              [ngClass]="{
                'fp-num': mono(),
                'cursor-not-allowed text-slate-400 opacity-60': opt.disabled,
                'cursor-pointer bg-[#FACC15] text-fp-ink border-2 border-fp-ink shadow-brutal-sm': isSelected(opt.value) && !opt.disabled,
                'cursor-pointer text-fp-ink hover:bg-fp-canvas': !isSelected(opt.value) && !opt.disabled
              }"
            >
              <span class="truncate">{{ opt.label }}</span>
              @if (isSelected(opt.value)) {
                <svg class="h-4 w-4 shrink-0 text-fp-ink" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.8">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
                </svg>
              } @else if (opt.badge) {
                <span class="shrink-0 rounded border border-fp-ink/30 bg-fp-canvas px-1.5 py-0.5 text-[10px] font-bold text-slate-600">
                  {{ opt.badge }}
                </span>
              }
            </li>
          }
        </ul>
      }
    </div>
  `,
})
export class AppSelectComponent<T = any> implements ControlValueAccessor {
  private readonly elementRef = inject(ElementRef<HTMLElement>);

  readonly options = input<ReadonlyArray<AppSelectOption<T>>>([]);
  readonly value = input<T | null | undefined>(undefined);
  readonly placeholder = input<string>('Pilih...');
  readonly selectId = input<string>('');
  readonly ariaLabel = input<string>('');
  readonly mono = input<boolean>(false);
  readonly compact = input<boolean>(false);
  readonly invalid = input<boolean>(false);
  readonly disabled = input<boolean>(false);

  readonly valueChange = output<any>();

  readonly isOpen = signal<boolean>(false);
  private readonly internalValue = signal<T | null | undefined>(undefined);
  private readonly cvaDisabled = signal<boolean>(false);

  private onChangeFn: (val: any) => void = () => {};
  private onTouchedFn: () => void = () => {};

  readonly isDisabled = computed(() => this.disabled() || this.cvaDisabled());

  readonly activeValue = computed<T | null | undefined>(() => {
    const bound = this.value();
    if (bound !== undefined) {
      return bound;
    }
    return this.internalValue();
  });

  readonly selectedOption = computed<AppSelectOption<T> | undefined>(() => {
    const current = this.activeValue();
    return this.options().find((opt) => String(opt.value) === String(current ?? ''));
  });

  readonly hasSelectedOption = computed(() => {
    const opt = this.selectedOption();
    return opt !== undefined && opt.value !== ('' as unknown as T);
  });

  readonly selectedLabel = computed(() => {
    const opt = this.selectedOption();
    if (opt) {
      return opt.label;
    }
    return this.placeholder();
  });

  constructor() {
    effect(() => {
      const bound = this.value();
      if (bound !== undefined) {
        this.internalValue.set(bound);
      }
    });
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.isOpen()) {
      return;
    }
    const target = event.target as Node | null;
    if (target && !this.elementRef.nativeElement.contains(target)) {
      this.closeDropdown();
    }
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.isOpen()) {
      this.closeDropdown();
    }
  }

  writeValue(val: T | null): void {
    this.internalValue.set(val);
  }

  registerOnChange(fn: (val: any) => void): void {
    this.onChangeFn = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouchedFn = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.cvaDisabled.set(isDisabled);
  }

  toggleDropdown(): void {
    if (this.isDisabled()) {
      return;
    }
    this.isOpen.update((open) => !open);
    if (!this.isOpen()) {
      this.onTouchedFn();
    }
  }

  closeDropdown(): void {
    if (this.isOpen()) {
      this.isOpen.set(false);
      this.onTouchedFn();
    }
  }

  isSelected(optionValue: T): boolean {
    const current = this.activeValue();
    if (current === undefined || current === null) {
      return optionValue === ('' as unknown as T);
    }
    return String(optionValue) === String(current);
  }

  selectOption(option: AppSelectOption<T>): void {
    if (option.disabled || this.isDisabled()) {
      return;
    }
    this.internalValue.set(option.value);
    this.onChangeFn(option.value);
    this.onTouchedFn();
    this.valueChange.emit(option.value);
    this.isOpen.set(false);
  }

  onTriggerKeydown(event: KeyboardEvent): void {
    if (this.isDisabled()) {
      return;
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.toggleDropdown();
      return;
    }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const enabledOptions = this.options().filter((o) => !o.disabled);
      if (enabledOptions.length === 0) {
        return;
      }
      const currentIdx = enabledOptions.findIndex((o) => this.isSelected(o.value));
      const step = event.key === 'ArrowDown' ? 1 : -1;
      const nextIdx =
        currentIdx < 0
          ? 0
          : (currentIdx + step + enabledOptions.length) % enabledOptions.length;
      this.selectOption(enabledOptions[nextIdx]);
    }
  }
}
