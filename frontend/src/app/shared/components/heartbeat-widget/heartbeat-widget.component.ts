import { CommonModule } from '@angular/common';
import { Component, computed, input } from '@angular/core';

export type PulseZone = 'idle' | 'healthy' | 'elevated' | 'critical';

/**
 * "FinPulse" Living Heartbeat Widget:
 * Animated SVG EKG waveform whose cadence (BPM / animation speed) and color
 * react dynamically to the user's monthly expense-to-income ratio.
 */
@Component({
  selector: 'app-heartbeat-widget',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div
      data-testid="heartbeat-widget"
      class="fp-card flex h-full flex-col justify-between p-5"
      [style.--fp-beat-duration]="beatDuration()"
    >
      <div class="flex items-start justify-between gap-2">
        <div>
          <div class="flex items-center gap-2">
            <span
              class="fp-beat-dot inline-block h-2.5 w-2.5 rounded-full border border-fp-ink"
              [style.background-color]="strokeColor()"
            ></span>
            <h2 class="text-sm font-bold uppercase tracking-wider text-fp-ink">
              FinPulse Vital
            </h2>
          </div>
          <p class="mt-0.5 text-xs font-medium text-slate-600">
            Rasio pengeluaran terhadap pemasukan
          </p>
        </div>

        <span
          data-testid="heartbeat-zone-badge"
          class="rounded-lg border-2 border-fp-ink px-2.5 py-0.5 text-[11px] font-bold uppercase tracking-wide shadow-brutal-sm"
          [ngClass]="badgeClass()"
        >
          {{ zoneLabel() }}
        </span>
      </div>

      <!-- Animated EKG SVG Waveform -->
      <div class="relative my-4 overflow-hidden rounded-xl border-2 border-fp-ink bg-[#0F172A] px-3 py-4">
        <div
          class="pointer-events-none absolute inset-0 opacity-20"
          style="background-image: linear-gradient(to right, #334155 1px, transparent 1px), linear-gradient(to bottom, #334155 1px, transparent 1px); background-size: 16px 16px;"
        ></div>

        <svg
          class="relative h-16 w-full"
          viewBox="0 0 320 64"
          fill="none"
          preserveAspectRatio="none"
          role="img"
          [attr.aria-label]="'Gelombang detak keuangan: ' + zoneLabel() + ' (' + ratioLabel() + ')'"
        >
          <!-- Baseline ghost waveform -->
          <path
            [attr.d]="wavePath()"
            stroke="#334155"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <!-- Kinetic live sweep waveform -->
          <path
            class="fp-ekg-path"
            [attr.d]="wavePath()"
            [attr.stroke]="strokeColor()"
            stroke-width="3"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </div>

      <!-- Readout Footer -->
      <div class="flex items-end justify-between gap-3 border-t-2 border-dashed border-slate-300 pt-3">
        <div>
          <p class="text-[11px] font-bold uppercase tracking-wider text-slate-500">
            Burn Ratio
          </p>
          <p
            data-testid="heartbeat-ratio"
            class="fp-num text-xl font-bold leading-tight text-fp-ink"
          >
            {{ ratioLabel() }}
          </p>
        </div>

        <div class="text-right">
          <p class="text-[11px] font-bold uppercase tracking-wider text-slate-500">
            Cadence
          </p>
          <p
            data-testid="heartbeat-bpm"
            class="fp-num text-sm font-bold"
            [style.color]="textAccentColor()"
          >
            {{ bpmValue() }} BPM
          </p>
        </div>
      </div>
    </div>
  `,
})
export class HeartbeatWidgetComponent {
  readonly income = input<number>(0);
  readonly expense = input<number>(0);

  private readonly normalizedAmounts = computed(() => ({
    inc: Math.max(0, Number.isFinite(this.income()) ? this.income() : 0),
    exp: Math.max(0, Number.isFinite(this.expense()) ? this.expense() : 0),
  }));

  readonly ratio = computed<number>(() => {
    const { inc, exp } = this.normalizedAmounts();
    if (inc === 0 && exp === 0) {
      return 0;
    }
    if (inc === 0 && exp > 0) {
      return 100;
    }
    return (exp / inc) * 100;
  });

  readonly zone = computed<PulseZone>(() => {
    const { inc, exp } = this.normalizedAmounts();
    if (inc === 0 && exp === 0) {
      return 'idle';
    }
    const r = this.ratio();
    if (r >= 85) {
      return 'critical';
    }
    if (r >= 60) {
      return 'elevated';
    }
    return 'healthy';
  });

  readonly beatDuration = computed<string>(() => {
    switch (this.zone()) {
      case 'critical':
        return '0.85s';
      case 'elevated':
        return '1.4s';
      case 'healthy':
        return '2.2s';
      default:
        return '3.4s';
    }
  });

  readonly bpmValue = computed<number>(() => {
    switch (this.zone()) {
      case 'critical':
        return 132;
      case 'elevated':
        return 92;
      case 'healthy':
        return 66;
      default:
        return 0;
    }
  });

  readonly strokeColor = computed<string>(() => {
    switch (this.zone()) {
      case 'critical':
        return '#FF4D6D';
      case 'elevated':
        return '#FACC15';
      case 'healthy':
        return '#00D68F';
      default:
        return '#94A3B8';
    }
  });

  readonly textAccentColor = computed<string>(() => {
    switch (this.zone()) {
      case 'critical':
        return '#E11D48';
      case 'elevated':
        return '#B45309';
      case 'healthy':
        return '#059669';
      default:
        return '#475569';
    }
  });

  readonly zoneLabel = computed<string>(() => {
    switch (this.zone()) {
      case 'critical':
        return 'Kritis';
      case 'elevated':
        return 'Waspada';
      case 'healthy':
        return 'Sehat';
      default:
        return 'Standby';
    }
  });

  readonly badgeClass = computed<string>(() => {
    switch (this.zone()) {
      case 'critical':
        return 'bg-[#FF4D6D] text-white';
      case 'elevated':
        return 'bg-[#FACC15] text-fp-ink';
      case 'healthy':
        return 'bg-[#00D68F] text-fp-ink';
      default:
        return 'bg-slate-200 text-slate-700';
    }
  });

  readonly ratioLabel = computed<string>(() => {
    if (this.zone() === 'idle') {
      return '0.0%';
    }
    const r = this.ratio();
    return r > 999 ? '999%+' : `${r.toFixed(1)}%`;
  });

  readonly wavePath = computed<string>(() => {
    if (this.zone() === 'idle') {
      return 'M 0 34 L 90 34 L 104 28 L 116 38 L 128 34 L 320 34';
    }
    if (this.zone() === 'critical') {
      return 'M 0 34 L 24 34 L 34 10 L 44 56 L 54 18 L 64 34 L 112 34 L 122 8 L 132 58 L 142 16 L 152 34 L 204 34 L 214 10 L 224 56 L 234 18 L 244 34 L 320 34';
    }
    if (this.zone() === 'elevated') {
      return 'M 0 34 L 46 34 L 58 14 L 70 52 L 82 22 L 94 34 L 176 34 L 188 14 L 200 52 L 212 22 L 224 34 L 320 34';
    }
    return 'M 0 34 L 76 34 L 88 28 L 98 34 L 110 12 L 122 50 L 134 26 L 144 34 L 228 34 L 240 28 L 250 34 L 262 16 L 274 46 L 286 34 L 320 34';
  });
}
