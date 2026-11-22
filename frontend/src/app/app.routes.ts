import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { SeoConfig } from './core/services/seo.service';
import { AppLayoutComponent } from './shared/components/app-layout/app-layout.component';

const PRIVATE_ROBOTS = 'noindex, nofollow';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/landing/landing.component').then((m) => m.LandingComponent),
    data: {
      seo: {
        title: 'FinPulse — Aplikasi Pencatatan Keuangan Pribadi & Dasbor Pengeluaran',
        description:
          'Pantau pemasukan, pengeluaran harian, grafik arus kas, batas anggaran bulanan, hingga ekspor laporan PDF dan CSV dalam satu dasbor keuangan pribadi yang ringkas.',
        robots: 'index, follow',
        canonicalPath: '/',
        includeJsonLd: true,
      } satisfies SeoConfig,
    },
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/login/login.component').then((m) => m.LoginComponent),
    data: {
      seo: {
        title: 'Masuk — FinPulse',
        description:
          'Masuk ke akun FinPulse Anda untuk memantau pemasukan, pengeluaran, dan batas anggaran bulanan.',
        robots: PRIVATE_ROBOTS,
        canonicalPath: '/login',
        includeJsonLd: false,
      } satisfies SeoConfig,
    },
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/register/register.component').then((m) => m.RegisterComponent),
    data: {
      seo: {
        title: 'Daftar Gratis — FinPulse',
        description:
          'Buat akun FinPulse gratis untuk mulai mencatat pemasukan, pengeluaran harian, dan anggaran bulanan.',
        robots: PRIVATE_ROBOTS,
        canonicalPath: '/register',
        includeJsonLd: false,
      } satisfies SeoConfig,
    },
  },
  {
    path: '',
    component: AppLayoutComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
        data: {
          seo: {
            title: 'Dasbor Keuangan — FinPulse',
            description: 'Ringkasan saldo, arus kas, dan status anggaran bulanan Anda di FinPulse.',
            robots: PRIVATE_ROBOTS,
            canonicalPath: '/dashboard',
            includeJsonLd: false,
          } satisfies SeoConfig,
        },
      },
      {
        path: 'transactions',
        loadComponent: () =>
          import('./features/transactions/transactions.component').then(
            (m) => m.TransactionsComponent
          ),
        data: {
          seo: {
            title: 'Transaksi — FinPulse',
            description: 'Kelola riwayat pemasukan dan pengeluaran harian Anda di FinPulse.',
            robots: PRIVATE_ROBOTS,
            canonicalPath: '/transactions',
            includeJsonLd: false,
          } satisfies SeoConfig,
        },
      },
      {
        path: 'budgets',
        loadComponent: () =>
          import('./features/budgets/budgets.component').then((m) => m.BudgetsComponent),
        data: {
          seo: {
            title: 'Anggaran Bulanan — FinPulse',
            description:
              'Atur batas anggaran bulanan per kategori dan pantau pemakaiannya di FinPulse.',
            robots: PRIVATE_ROBOTS,
            canonicalPath: '/budgets',
            includeJsonLd: false,
          } satisfies SeoConfig,
        },
      },
      {
        path: 'reports',
        loadComponent: () =>
          import('./features/reports/reports.component').then((m) => m.ReportsComponent),
        data: {
          seo: {
            title: 'Laporan Keuangan — FinPulse',
            description: 'Ekspor dan unduh laporan keuangan bulanan PDF dan CSV di FinPulse.',
            robots: PRIVATE_ROBOTS,
            canonicalPath: '/reports',
            includeJsonLd: false,
          } satisfies SeoConfig,
        },
      },
    ],
  },
  {
    path: '**',
    redirectTo: '',
  },
];
