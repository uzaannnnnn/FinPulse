import {
  DestroyRef,
  Directive,
  effect,
  ElementRef,
  inject,
  input,
  NgZone,
} from '@angular/core';
import { ANIMATION_MODULE_TYPE } from '@angular/platform-browser/animations';
import { formatIdr } from '../utils/format.util';

export type CountUpFormat = 'idr' | 'number' | 'percent';

/**
 * Smooth rolling odometer / count-up animation for currency and numeric figures.
 * Respects `prefers-reduced-motion: reduce` and `NoopAnimations`, and cleans up RAF on destroy.
 */
@Directive({
  selector: '[appCountUp]',
  standalone: true,
})
export class CountUpDirective {
  private readonly el = inject(ElementRef<HTMLElement>);
  private readonly ngZone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);
  private readonly animationModuleType = inject(ANIMATION_MODULE_TYPE, { optional: true });

  readonly targetValue = input<number>(0, { alias: 'appCountUp' });
  readonly duration = input<number>(620);
  readonly format = input<CountUpFormat>('idr');
  readonly prefix = input<string>('');
  readonly suffix = input<string>('');

  private currentValue = 0;
  private hasInitialized = false;
  private rafId: number | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.cancelAnimation();
    });

    effect(() => {
      const rawTarget = this.targetValue();
      const target = Number.isFinite(rawTarget) ? rawTarget : 0;
      const durationMs = Math.max(0, this.duration());
      const formatMode = this.format();
      const prefixStr = this.prefix();
      const suffixStr = this.suffix();

      this.animateTo(target, durationMs, formatMode, prefixStr, suffixStr);
    });
  }

  private animateTo(
    target: number,
    durationMs: number,
    formatMode: CountUpFormat,
    prefixStr: string,
    suffixStr: string
  ): void {
    this.cancelAnimation();

    const prefersReducedMotion =
      typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const isNoopAnimations = this.animationModuleType === 'NoopAnimations';

    const from = this.hasInitialized ? this.currentValue : 0;
    this.hasInitialized = true;

    // Paint final target immediately for reduced-motion, NoopAnimations, zero duration, or equal values.
    if (prefersReducedMotion || isNoopAnimations || durationMs === 0 || from === target) {
      this.currentValue = target;
      this.renderValue(target, formatMode, prefixStr, suffixStr);
      this.el.nativeElement.setAttribute(
        'aria-label',
        this.formatValue(target, formatMode, prefixStr, suffixStr)
      );
      return;
    }

    // Set accessible aria-label with exact final value immediately.
    const finalFormatted = this.formatValue(target, formatMode, prefixStr, suffixStr);
    this.el.nativeElement.setAttribute('aria-label', finalFormatted);

    // Paint initial frame synchronously so element is never blank.
    this.renderValue(from, formatMode, prefixStr, suffixStr);

    this.ngZone.runOutsideAngular(() => {
      const startTime = performance.now();
      const delta = target - from;

      const step = (now: number) => {
        const elapsed = now - startTime;
        const progress = Math.min(1, elapsed / durationMs);
        // Ease-out quartic curve for crisp kinetic deceleration
        const eased = 1 - Math.pow(1 - progress, 4);
        const value = progress >= 1 ? target : from + delta * eased;

        this.currentValue = value;
        this.renderValue(value, formatMode, prefixStr, suffixStr);

        if (progress < 1) {
          this.rafId = requestAnimationFrame(step);
        } else {
          this.rafId = null;
        }
      };

      this.rafId = requestAnimationFrame(step);
    });
  }

  private renderValue(
    value: number,
    formatMode: CountUpFormat,
    prefixStr: string,
    suffixStr: string
  ): void {
    this.el.nativeElement.textContent = this.formatValue(value, formatMode, prefixStr, suffixStr);
  }

  private formatValue(
    value: number,
    formatMode: CountUpFormat,
    prefixStr: string,
    suffixStr: string
  ): string {
    switch (formatMode) {
      case 'idr':
        return `${prefixStr}${formatIdr(Math.round(value))}${suffixStr}`;
      case 'percent':
        return `${prefixStr}${value.toFixed(1)}%${suffixStr}`;
      case 'number':
      default:
        return `${prefixStr}${Math.round(value).toLocaleString('id-ID')}${suffixStr}`;
    }
  }

  private cancelAnimation(): void {
    if (this.rafId !== null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
  }
}
