import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';

export interface SeoConfig {
  title: string;
  description: string;
  robots?: string;
  canonicalPath?: string;
  ogType?: string;
  ogImage?: string;
  includeJsonLd?: boolean;
}

export const SITE_URL = 'https://finpulse.eshace.com';
export const DEFAULT_OG_IMAGE = `${SITE_URL}/og-image.png`;
export const JSON_LD_SCRIPT_ID = 'finpulse-jsonld';

export const DEFAULT_SEO: Required<SeoConfig> = {
  title: 'FinPulse — Aplikasi Pencatatan Keuangan Pribadi & Dasbor Pengeluaran',
  description:
    'Pantau pemasukan, pengeluaran harian, grafik arus kas, batas anggaran bulanan, hingga ekspor laporan PDF dan CSV dalam satu dasbor keuangan pribadi yang ringkas.',
  robots: 'index, follow',
  canonicalPath: '/',
  ogType: 'website',
  ogImage: DEFAULT_OG_IMAGE,
  includeJsonLd: true,
};

@Injectable({
  providedIn: 'root',
})
export class SeoService {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly document = inject(DOCUMENT);

  update(config: Partial<SeoConfig> = {}): void {
    const merged: Required<SeoConfig> = {
      ...DEFAULT_SEO,
      ...config,
    };

    const canonicalUrl = this.buildCanonicalUrl(merged.canonicalPath);

    this.title.setTitle(merged.title);

    this.meta.updateTag({ name: 'description', content: merged.description });
    this.meta.updateTag({ name: 'robots', content: merged.robots });

    this.meta.updateTag({ property: 'og:title', content: merged.title });
    this.meta.updateTag({ property: 'og:description', content: merged.description });
    this.meta.updateTag({ property: 'og:type', content: merged.ogType });
    this.meta.updateTag({ property: 'og:url', content: canonicalUrl });
    this.meta.updateTag({ property: 'og:image', content: merged.ogImage });
    this.meta.updateTag({ property: 'og:site_name', content: 'FinPulse' });
    this.meta.updateTag({ property: 'og:locale', content: 'id_ID' });

    this.meta.updateTag({ name: 'twitter:card', content: 'summary_large_image' });
    this.meta.updateTag({ name: 'twitter:title', content: merged.title });
    this.meta.updateTag({ name: 'twitter:description', content: merged.description });
    this.meta.updateTag({ name: 'twitter:image', content: merged.ogImage });

    this.setCanonicalLink(canonicalUrl);
    this.syncJsonLd(merged.includeJsonLd, merged.description);
  }

  private buildCanonicalUrl(path: string): string {
    if (!path || path === '/') {
      return `${SITE_URL}/`;
    }
    const normalized = path.startsWith('/') ? path : `/${path}`;
    return `${SITE_URL}${normalized}`;
  }

  private setCanonicalLink(url: string): void {
    const head = this.document.head;
    if (!head) {
      return;
    }

    let link = head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!link) {
      link = this.document.createElement('link');
      link.setAttribute('rel', 'canonical');
      head.appendChild(link);
    }
    link.setAttribute('href', url);
  }

  private syncJsonLd(include: boolean, description: string): void {
    const head = this.document.head;
    if (!head) {
      return;
    }

    const existing = head.querySelector<HTMLScriptElement>(`script#${JSON_LD_SCRIPT_ID}`);
    if (!include) {
      existing?.remove();
      return;
    }

    const payload = {
      '@context': 'https://schema.org',
      '@type': ['SoftwareApplication', 'WebApplication'],
      name: 'FinPulse',
      url: SITE_URL,
      description,
      applicationCategory: 'FinanceApplication',
      operatingSystem: 'Web',
      inLanguage: 'id',
      image: DEFAULT_OG_IMAGE,
      offers: {
        '@type': 'Offer',
        price: '0',
        priceCurrency: 'IDR',
      },
    };

    const script = existing ?? this.document.createElement('script');
    script.id = JSON_LD_SCRIPT_ID;
    script.type = 'application/ld+json';
    script.textContent = JSON.stringify(payload);

    if (!existing) {
      head.appendChild(script);
    }
  }
}
