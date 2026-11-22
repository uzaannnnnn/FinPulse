import {
  DestroyRef,
  Directive,
  ElementRef,
  HostListener,
  inject,
  input,
  OnInit,
  Renderer2,
} from '@angular/core';

/**
 * Subtle mouse-tracking 3D perspective tilt + dynamic glare effect
 * for hero financial cards (e.g. primary Balance / Wallet card).
 */
@Directive({
  selector: '[appTilt3d]',
  standalone: true,
})
export class Tilt3dDirective implements OnInit {
  private readonly el = inject(ElementRef<HTMLElement>);
  private readonly renderer = inject(Renderer2);
  private readonly destroyRef = inject(DestroyRef);

  readonly maxTilt = input<number>(7);
  readonly perspective = input<number>(950);
  readonly glare = input<boolean>(true);

  private rafId: number | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => {
      if (this.rafId !== null) {
        cancelAnimationFrame(this.rafId);
        this.rafId = null;
      }
    });
  }

  ngOnInit(): void {
    const node = this.el.nativeElement;
    this.renderer.addClass(node, 'fp-tilt-host');
    this.renderer.setStyle(node, 'transition', 'transform 220ms cubic-bezier(0.34, 1.56, 0.64, 1)');
  }

  @HostListener('mousemove', ['$event'])
  onMouseMove(event: MouseEvent): void {
    if (this.prefersReducedMotion()) {
      return;
    }

    const node = this.el.nativeElement;
    const rect = node.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) {
      return;
    }

    const relX = (event.clientX - rect.left) / rect.width;
    const relY = (event.clientY - rect.top) / rect.height;
    const clampedX = Math.max(0, Math.min(1, relX));
    const clampedY = Math.max(0, Math.min(1, relY));

    const limit = Math.max(0, this.maxTilt());
    const rotateY = (clampedX - 0.5) * 2 * limit;
    const rotateX = (0.5 - clampedY) * 2 * limit;

    if (this.rafId !== null) {
      cancelAnimationFrame(this.rafId);
    }

    this.rafId = requestAnimationFrame(() => {
      this.renderer.setStyle(node, 'transition', 'transform 70ms ease-out');
      this.renderer.setStyle(
        node,
        'transform',
        `perspective(${this.perspective()}px) rotateX(${rotateX.toFixed(2)}deg) rotateY(${rotateY.toFixed(2)}deg) scale3d(1.01, 1.01, 1.01)`
      );

      if (this.glare()) {
        node.style.setProperty('--fp-glare-x', `${(clampedX * 100).toFixed(1)}%`);
        node.style.setProperty('--fp-glare-y', `${(clampedY * 100).toFixed(1)}%`);
        node.style.setProperty('--fp-glare-opacity', '1');
      }
      this.rafId = null;
    });
  }

  @HostListener('mouseleave')
  onMouseLeave(): void {
    if (this.rafId !== null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }

    const node = this.el.nativeElement;
    this.renderer.setStyle(node, 'transition', 'transform 320ms cubic-bezier(0.34, 1.56, 0.64, 1)');
    this.renderer.setStyle(
      node,
      'transform',
      `perspective(${this.perspective()}px) rotateX(0deg) rotateY(0deg) scale3d(1, 1, 1)`
    );
    node.style.setProperty('--fp-glare-opacity', '0');
  }

  private prefersReducedMotion(): boolean {
    return (
      typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches
    );
  }
}
