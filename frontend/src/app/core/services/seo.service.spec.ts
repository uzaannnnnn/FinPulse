import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { Meta, Title } from '@angular/platform-browser';
import {
  DEFAULT_OG_IMAGE,
  JSON_LD_SCRIPT_ID,
  SeoService,
  SITE_URL,
} from './seo.service';

describe('SeoService', () => {
  let service: SeoService;
  let title: Title;
  let meta: Meta;
  let documentRef: Document;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SeoService);
    title = TestBed.inject(Title);
    meta = TestBed.inject(Meta);
    documentRef = TestBed.inject(DOCUMENT);
  });

  afterEach(() => {
    documentRef.head.querySelector(`script#${JSON_LD_SCRIPT_ID}`)?.remove();
    documentRef.head.querySelector('link[rel="canonical"]')?.remove();
  });

  it('should apply public SEO tags, canonical link, and JSON-LD for landing route', () => {
    service.update({
      title: 'FinPulse — Aplikasi Pencatatan Keuangan Pribadi',
      description: 'Kelola pemasukan dan pengeluaran harian lebih tenang.',
      robots: 'index, follow',
      canonicalPath: '/',
      includeJsonLd: true,
    });

    expect(title.getTitle()).toBe('FinPulse — Aplikasi Pencatatan Keuangan Pribadi');
    expect(meta.getTag('name="description"')?.content).toBe(
      'Kelola pemasukan dan pengeluaran harian lebih tenang.'
    );
    expect(meta.getTag('name="robots"')?.content).toBe('index, follow');
    expect(meta.getTag('property="og:url"')?.content).toBe(`${SITE_URL}/`);
    expect(meta.getTag('property="og:image"')?.content).toBe(DEFAULT_OG_IMAGE);
    expect(meta.getTag('property="og:site_name"')?.content).toBe('FinPulse');
    expect(meta.getTag('property="og:locale"')?.content).toBe('id_ID');
    expect(meta.getTag('name="twitter:card"')?.content).toBe('summary_large_image');

    const canonical = documentRef.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    expect(canonical?.getAttribute('href')).toBe(`${SITE_URL}/`);

    const jsonLdEl = documentRef.head.querySelector<HTMLScriptElement>(
      `script#${JSON_LD_SCRIPT_ID}`
    );
    expect(jsonLdEl).not.toBeNull();
    const parsed = JSON.parse(jsonLdEl?.textContent ?? '{}');
    expect(parsed.name).toBe('FinPulse');
    expect(parsed.url).toBe(SITE_URL);
    expect(parsed.applicationCategory).toBe('FinanceApplication');
    expect(parsed.inLanguage).toBe('id');
    expect(parsed.offers?.priceCurrency).toBe('IDR');
  });

  it('should apply noindex and remove JSON-LD on private routes', () => {
    service.update({ includeJsonLd: true, canonicalPath: '/' });
    expect(documentRef.head.querySelector(`script#${JSON_LD_SCRIPT_ID}`)).not.toBeNull();

    service.update({
      title: 'Dasbor Keuangan — FinPulse',
      description: 'Ringkasan saldo Anda.',
      robots: 'noindex, nofollow',
      canonicalPath: '/dashboard',
      includeJsonLd: false,
    });

    expect(meta.getTag('name="robots"')?.content).toBe('noindex, nofollow');
    expect(meta.getTag('property="og:url"')?.content).toBe(`${SITE_URL}/dashboard`);
    const canonical = documentRef.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    expect(canonical?.getAttribute('href')).toBe(`${SITE_URL}/dashboard`);
    expect(documentRef.head.querySelector(`script#${JSON_LD_SCRIPT_ID}`)).toBeNull();
  });
});
