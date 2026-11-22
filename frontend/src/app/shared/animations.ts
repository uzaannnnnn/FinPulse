import {
  animate,
  query,
  stagger,
  style,
  transition,
  trigger,
} from '@angular/animations';

export const FP_SPRING_CURVE = 'cubic-bezier(0.34, 1.56, 0.64, 1)';

/**
 * Spring-based entrance animation for Bento cards, sections, and banners.
 */
export const springEnter = trigger('springEnter', [
  transition(':enter', [
    style({ opacity: 0, transform: 'translateY(16px) scale(0.97)' }),
    animate(
      `380ms ${FP_SPRING_CURVE}`,
      style({ opacity: 1, transform: 'translateY(0) scale(1)' })
    ),
  ]),
  transition(':leave', [
    animate(
      '180ms ease-in',
      style({ opacity: 0, transform: 'translateY(-8px) scale(0.98)' })
    ),
  ]),
]);

/**
 * Staggered list rendering for transaction rows, budget cards, and history tables.
 */
export const staggerList = trigger('staggerList', [
  transition('* => *', [
    query(
      ':enter',
      [
        style({ opacity: 0, transform: 'translateY(14px) scale(0.98)' }),
        stagger(45, [
          animate(
            `340ms ${FP_SPRING_CURVE}`,
            style({ opacity: 1, transform: 'translateY(0) scale(1)' })
          ),
        ]),
      ],
      { optional: true }
    ),
  ]),
]);

/**
 * Smooth kinetic route transition for router outlet containers.
 */
export const routeTransition = trigger('routeTransition', [
  transition('* <=> *', [
    query(
      ':enter',
      [
        style({ opacity: 0, transform: 'translateY(12px)' }),
        animate(
          `300ms ${FP_SPRING_CURVE}`,
          style({ opacity: 1, transform: 'translateY(0)' })
        ),
      ],
      { optional: true }
    ),
  ]),
]);

/**
 * Elastic pop-in for Neo-Brutalist modals and confirmation dialogs.
 */
export const modalPopIn = trigger('modalPopIn', [
  transition(':enter', [
    style({ opacity: 0, transform: 'translateY(18px) scale(0.93)' }),
    animate(
      `280ms ${FP_SPRING_CURVE}`,
      style({ opacity: 1, transform: 'translateY(0) scale(1)' })
    ),
  ]),
  transition(':leave', [
    animate(
      '150ms ease-in',
      style({ opacity: 0, transform: 'translateY(10px) scale(0.96)' })
    ),
  ]),
]);

/**
 * Elastic slide-and-pop for Toast notifications.
 */
export const toastPop = trigger('toastPop', [
  transition(':enter', [
    style({ opacity: 0, transform: 'translateX(36px) scale(0.92)' }),
    animate(
      `320ms ${FP_SPRING_CURVE}`,
      style({ opacity: 1, transform: 'translateX(0) scale(1)' })
    ),
  ]),
  transition(':leave', [
    animate(
      '180ms ease-in',
      style({ opacity: 0, transform: 'translateX(28px) scale(0.94)' })
    ),
  ]),
]);
