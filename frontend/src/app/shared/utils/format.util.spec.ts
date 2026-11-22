import {
  formatDateId,
  formatIdr,
  getYearOptions,
  MONTH_OPTIONS,
  sanitizeHttpUrl,
} from './format.util';

describe('format.util', () => {
  it('should format positive, zero, and negative IDR amounts', () => {
    expect(formatIdr(1250000)).toBe('Rp 1.250.000');
    expect(formatIdr(-250000)).toBe('-Rp 250.000');
    expect(formatIdr(0)).toBe('Rp 0');
    expect(formatIdr(-0)).toBe('Rp 0');
    expect(formatIdr(null)).toBe('Rp 0');
  });

  it('should format ISO date strings in Indonesian without timezone shift', () => {
    expect(formatDateId('2026-11-21')).toBe('21 Nov 2026');
    expect(formatDateId('2026-01-05', 'long')).toBe('5 Januari 2026');
    expect(formatDateId('')).toBe('-');
  });

  it('should expose all 12 Indonesian month options', () => {
    expect(MONTH_OPTIONS.length).toBe(12);
    expect(MONTH_OPTIONS[0].label).toBe('Januari');
    expect(MONTH_OPTIONS[11].label).toBe('Desember');
  });

  it('should sanitize external HTTP/HTTPS URLs and reject unsafe schemes or credentials', () => {
    expect(sanitizeHttpUrl('https://demo.supabase.co/storage/v1/object/public/receipts/u/a.jpg')).toBe(
      'https://demo.supabase.co/storage/v1/object/public/receipts/u/a.jpg'
    );
    expect(sanitizeHttpUrl('http://localhost:8080/img.png')).toBe('http://localhost:8080/img.png');
    expect(sanitizeHttpUrl('javascript:alert(1)')).toBeNull();
    expect(sanitizeHttpUrl('data:image/svg+xml,<svg onload=alert(1)>')).toBeNull();
    expect(sanitizeHttpUrl('vbscript:msgbox(1)')).toBeNull();
    expect(sanitizeHttpUrl('ftp://files.example.com/a.jpg')).toBeNull();
    expect(sanitizeHttpUrl('https://user:pass@evil.example.com/a.jpg')).toBeNull();
    expect(sanitizeHttpUrl('   ')).toBeNull();
    expect(sanitizeHttpUrl(null)).toBeNull();
  });

  it('should clamp year options within 2020..2100', () => {
    const lowYears = getYearOptions(2020);
    expect(Math.min(...lowYears)).toBe(2020);

    const highYears = getYearOptions(2100);
    expect(Math.max(...highYears)).toBe(2100);
  });
});
