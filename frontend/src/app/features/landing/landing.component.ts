import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface LandingFeature {
  badge: string;
  accentClass: string;
  title: string;
  description: string;
  icon: 'wallet' | 'chart' | 'bell' | 'document';
}

interface LandingStep {
  number: string;
  title: string;
  description: string;
  accentClass: string;
}

interface PreviewTransaction {
  title: string;
  category: string;
  amount: string;
  positive: boolean;
}

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './landing.component.html',
})
export class LandingComponent {
  readonly currentYear = 2026;

  readonly features: LandingFeature[] = [
    {
      badge: 'Harian',
      accentClass: 'bg-fp-mint text-fp-ink',
      title: 'Pencatatan Pemasukan & Pengeluaran',
      description:
        'Catat setiap pemasukan dan pengeluaran harian dalam hitungan detik. Lengkapi kategori, catatan singkat, serta foto struk belanja agar semua riwayat tersimpan rapi.',
      icon: 'wallet',
    },
    {
      badge: 'Visual',
      accentClass: 'bg-fp-amber text-fp-ink',
      title: 'Grafik Arus Kas & Kategori',
      description:
        'Lihat ke mana uang Anda mengalir lewat grafik tren bulanan dan rincian pengeluaran per kategori. Bandingkan pemasukan dan belanja hanya dalam sekali pandang.',
      icon: 'chart',
    },
    {
      badge: 'Terkendali',
      accentClass: 'bg-fp-coral text-white',
      title: 'Batas Anggaran Bulanan & Pengingat',
      description:
        'Tetapkan batas belanja untuk setiap pos kebutuhan rumah tangga maupun gaya hidup. Terima pengingat saat pemakaian anggaran mendekati atau melewati target.',
      icon: 'bell',
    },
    {
      badge: 'Siap Arsip',
      accentClass: 'bg-fp-ink text-fp-mint',
      title: 'Ekspor Laporan PDF & CSV',
      description:
        'Unduh ringkasan keuangan bulanan kapan saja dalam format PDF yang rapi untuk arsip pribadi, atau CSV untuk diolah lebih lanjut di lembar kerja Anda.',
      icon: 'document',
    },
  ];

  readonly steps: LandingStep[] = [
    {
      number: '01',
      title: 'Buat Akun Gratis',
      description:
        'Daftar dengan email Anda dalam kurang dari satu menit dan langsung masuk ke dasbor keuangan pribadi tanpa biaya.',
      accentClass: 'bg-fp-mint text-fp-ink',
    },
    {
      number: '02',
      title: 'Catat Transaksi & Atur Anggaran',
      description:
        'Masukkan pemasukan, pengeluaran harian, dan tentukan batas anggaran bulanan untuk kategori penting Anda.',
      accentClass: 'bg-fp-amber text-fp-ink',
    },
    {
      number: '03',
      title: 'Pantau Tren & Unduh Laporan',
      description:
        'Evaluasi kondisi keuangan lewat grafik arus kas yang jelas, lalu unduh laporan PDF atau CSV saat dibutuhkan.',
      accentClass: 'bg-fp-coral text-white',
    },
  ];

  readonly previewBars = [
    { label: 'Mei', incomeHeight: 72, expenseHeight: 46 },
    { label: 'Jun', incomeHeight: 78, expenseHeight: 52 },
    { label: 'Jul', incomeHeight: 68, expenseHeight: 44 },
    { label: 'Agu', incomeHeight: 86, expenseHeight: 50 },
    { label: 'Sep', incomeHeight: 92, expenseHeight: 48 },
  ];

  readonly previewTransactions: PreviewTransaction[] = [
    {
      title: 'Gaji Bulanan',
      category: 'Pemasukan',
      amount: '+Rp 12.500.000',
      positive: true,
    },
    {
      title: 'Belanja Kebutuhan Dapur',
      category: 'Makan & Minum',
      amount: '-Rp 640.000',
      positive: false,
    },
    {
      title: 'Tagihan Listrik & Internet',
      category: 'Tagihan',
      amount: '-Rp 520.000',
      positive: false,
    },
  ];
}
