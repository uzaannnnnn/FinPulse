export interface MonthOption {
  value: number;
  label: string;
  shortLabel: string;
}

export const MONTH_OPTIONS: ReadonlyArray<MonthOption> = [
  { value: 1, label: 'Januari', shortLabel: 'Jan' },
  { value: 2, label: 'Februari', shortLabel: 'Feb' },
  { value: 3, label: 'Maret', shortLabel: 'Mar' },
  { value: 4, label: 'April', shortLabel: 'Apr' },
  { value: 5, label: 'Mei', shortLabel: 'Mei' },
  { value: 6, label: 'Juni', shortLabel: 'Jun' },
  { value: 7, label: 'Juli', shortLabel: 'Jul' },
  { value: 8, label: 'Agustus', shortLabel: 'Agu' },
  { value: 9, label: 'September', shortLabel: 'Sep' },
  { value: 10, label: 'Oktober', shortLabel: 'Okt' },
  { value: 11, label: 'November', shortLabel: 'Nov' },
  { value: 12, label: 'Desember', shortLabel: 'Des' },
];

const IDR_NUMBER_FORMATTER = new Intl.NumberFormat('id-ID', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

/**
 * Formats a numeric value to Indonesian Rupiah (e.g. `Rp 1.250.000`, `-Rp 250.000`).
 */
export function formatIdr(amount: number | null | undefined): string {
  if (amount === null || amount === undefined || Number.isNaN(amount)) {
    return 'Rp 0';
  }
  const normalized = Object.is(amount, -0) ? 0 : amount;
  const isNegative = normalized < 0;
  const formattedAbs = IDR_NUMBER_FORMATTER.format(Math.abs(normalized));
  return isNegative ? `-Rp ${formattedAbs}` : `Rp ${formattedAbs}`;
}

/**
 * Formats an ISO date string (`YYYY-MM-DD`) into Indonesian human-readable date
 * without UTC timezone shifting.
 */
export function formatDateId(
  isoDate: string | null | undefined,
  monthStyle: 'short' | 'long' = 'short'
): string {
  if (!isoDate) {
    return '-';
  }

  const datePart = isoDate.slice(0, 10);
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(datePart);
  if (!match) {
    return isoDate;
  }

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);

  const monthOption = MONTH_OPTIONS[month - 1];
  if (!monthOption || day < 1 || day > 31) {
    return isoDate;
  }

  const monthLabel = monthStyle === 'long' ? monthOption.label : monthOption.shortLabel;
  return `${day} ${monthLabel} ${year}`;
}

export function getMonthName(month: number): string {
  return MONTH_OPTIONS[month - 1]?.label ?? '';
}

export function getTodayIsoDate(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function getYearOptions(referenceYear = new Date().getFullYear()): number[] {
  const clampedRef = Math.min(2100, Math.max(2020, referenceYear));
  const startYear = Math.max(2020, clampedRef - 4);
  const endYear = Math.min(2100, clampedRef + 2);
  const years: number[] = [];
  for (let y = endYear; y >= startYear; y--) {
    years.push(y);
  }
  return years;
}

export function sanitizeHexColor(color: string | null | undefined, fallback: string): string {
  if (color && /^#[0-9A-Fa-f]{6}$/.test(color)) {
    return color;
  }
  return fallback;
}

/**
 * Validates that an external URL uses strictly http:// or https:// with a valid host
 * and no embedded credentials, preventing javascript:/data:/vbscript: URL injection.
 */
export function sanitizeHttpUrl(url: string | null | undefined): string | null {
  if (!url) {
    return null;
  }
  const trimmed = url.trim();
  if (!trimmed || trimmed.length > 2048) {
    return null;
  }
  try {
    const parsed = new URL(trimmed);
    if (
      (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') ||
      !parsed.hostname ||
      parsed.username ||
      parsed.password
    ) {
      return null;
    }
    return trimmed;
  } catch {
    return null;
  }
}

/**
 * Formats an ISO-8601 date-time string into a concise Indonesian timestamp
 * (e.g. `21 Nov 2026, 20:30`).
 */
export function formatDateTimeId(isoDateTime: string | null | undefined): string {
  if (!isoDateTime) {
    return '-';
  }
  const date = new Date(isoDateTime);
  if (Number.isNaN(date.getTime())) {
    return isoDateTime;
  }
  const day = date.getDate();
  const month = MONTH_OPTIONS[date.getMonth()]?.shortLabel ?? '';
  const year = date.getFullYear();
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${day} ${month} ${year}, ${hours}:${minutes}`;
}

export function clampPercentage(percentage: number): number {
  if (!Number.isFinite(percentage)) {
    return 0;
  }
  return Math.min(100, Math.max(0, percentage));
}

export function formatPercent(percentage: number): string {
  if (!Number.isFinite(percentage)) {
    return '0.0%';
  }
  return `${percentage.toFixed(1)}%`;
}

/**
 * Formats a byte size into human-readable B / KB / MB string.
 */
export function formatFileSize(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined || !Number.isFinite(bytes) || bytes < 0) {
    return '-';
  }
  if (bytes >= 1024 * 1024) {
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  }
  if (bytes >= 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${Math.round(bytes)} B`;
}
