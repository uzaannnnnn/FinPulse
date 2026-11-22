# Skill: Frontend Development — Angular (`@frontend`)

## Objective
Mengimplementasikan antarmuka web **FinPulse** berbasis Angular di `frontend/` sesuai `docs/specs/spec_sprint_<N>.md` dan `docs/api_contract.yaml`.

## Rules of Engagement
- Gunakan **Angular 19+** dengan **Standalone Components**, **Signals** untuk state management, dan **Tailwind CSS**.
- Pisahkan struktur folder dengan jelas: `src/app/core/` (auth service, guard, JWT interceptor, models), `src/app/features/` (landing, auth, dashboard, transactions, budgets, reports), dan `src/app/shared/` (komponen UI reusable).
- Pastikan semua tipe TypeScript (`interface` / `type`) cocok 100% dengan `docs/api_contract.yaml`.

## Instructions
1. Baca `docs/specs/spec_sprint_<N>.md` dan `docs/api_contract.yaml`.
2. Jika Sprint 1, inisialisasi workspace Angular (`finpulse-web`) di `frontend/`, pasang Tailwind CSS, siapkan `environment.ts` (`apiUrl: 'http://localhost:8080/api'`), `auth.interceptor.ts`, `auth.guard.ts`, serta halaman Login, Register, dan layout sidebar **FinPulse**.
3. Implementasikan fitur sesuai sprint aktif:
   - **Sprint 1**: Flow Register, Login, Logout, penyimpanan token aman, dan proteksi route.
   - **Sprint 2**: Halaman daftar transaksi (tambah/edit/hapus, filter tanggal & kategori, upload preview foto struk), halaman manajemen batas anggaran, dan Dashboard FinPulse berisi kartu KPI (Total Pemasukan, Pengeluaran, Sisa Saldo), grafik arus kas bulanan (Chart.js), serta *progress bar* anggaran.
   - **Sprint 3**: Panel notifikasi *over-budget*, tombol permintaan ekspor laporan PDF/CSV (polling/status unduhan dari backend), dan indikator status sinkronisasi data.
4. Jaga tampilan tetap bersih, modern (*fintech feel*), responsif untuk desktop & mobile, serta lengkapi dengan *loading spinner* dan *toast notification*.
