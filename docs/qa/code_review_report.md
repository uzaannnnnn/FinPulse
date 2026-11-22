# Code Review & Safe Refactoring Report (`@reviewer`) — FinPulse

- **Tanggal Audit**: 22 November 2026
- **Peran**: `@reviewer` (Code Reviewer & Refactoring Specialist)
- **Ruang Lingkup**: `backend/` (Spring Boot 3 / Java 21) dan `frontend/` (Angular 19+ / Signals)
- **Batasan Kepatuhan**:
  - `docs/api_contract.yaml`: **Tidak diubah**
  - Migrasi Flyway (`backend/src/main/resources/db/migration/`): **Tidak diubah**
  - Logika bisnis & kontrak eksternal: **100% dipertahankan**

---

## Ringkasan Eksekutif

| Area | File Dimodifikasi | Fokus Refactoring Utama | Status Verifikasi |
| --- | :---: | --- | --- |
| **Backend (`backend/`)** | 6 file | Eliminasi query SQL duplikat, short-circuit `COUNT`, efisiensi alokasi PDF, DRY Supabase Storage, hapus dead code | `mvn test` — **72/72 PASS** |
| **Frontend (`frontend/`)** | 20 file | Migrasi kalkulasi template ke `computed()`, cleanup RxJS (`takeUntilDestroyed`), DRY validator/error/format, hapus unused imports & komentar dangkal | `npm run build` & `ng test` — **113/113 PASS** |
| **Konfigurasi `.agents/`** | 3 file | Penambahan peran `@reviewer`, skill `code_review.md`, & integrasi pipeline `run_sprint.md` | Terpasang |

---

## 1. Backend (`backend/`)

### 1.1 Efisiensi & Performa
1. **Eliminasi query SQL `GROUP BY` duplikat saat dashboard cache miss**
   - **File**: `DashboardSummaryCacheService.java`, `TransactionRepository.java`
   - **Temuan**: `getOrComputeSummary` menjalankan `sumByTypeForUserInRange(...)` lalu langsung menjalankan `sumMonthlyCashflowForUserInRange(...)` yang sudah mencakup periode bulan aktif di elemen terakhir `cashflowSeries`.
   - **Perbaikan**: Menghitung `cashflowSeries` terlebih dahulu lalu mengambil `totalIncome`, `totalExpense`, dan `balance` dari `cashflowSeries.getLast()`. Menghapus method repository `sumByTypeForUserInRange` yang tidak lagi dibutuhkan serta memakai ulang nilai `expenseByCategory` ke `HashMap.newHashMap(expenseByCategory.size())`.
2. **Short-circuit query `COUNT` redundan pada `AlertService.listAlerts`**
   - **File**: `AlertService.java`
   - **Temuan**: Ketika `unreadOnly == true` dan `alerts.size() < limit`, seluruh notifikasi belum terbaca sudah termuat di memori, namun query `countByUserIdAndIsReadFalse` tetap dieksekusi.
   - **Perbaikan**: Menggunakan `alerts.size()` langsung pada kondisi tersebut untuk menghemat 1 round-trip database.
3. **Eliminasi alokasi berulang pada generator PDF**
   - **File**: `ReportGeneratorService.java`
   - **Temuan**: `NumberFormat`, `Locale("id-ID")`, dan `java.awt.Color` dialokasikan ulang di setiap sel/header tabel PDF.
   - **Perbaikan**: Mengangkat `ID_LOCALE` dan konstanta warna ke `private static final`, serta membuat satu instance `NumberFormat` per pemanggilan `buildPdfBytes`.
4. **Verifikasi N+1, query dalam loop, & `@Transactional(readOnly = true)`**
   - Seluruh query daftar/detail entitas berelasi sudah menggunakan `JOIN FETCH` atau `@EntityGraph(attributePaths = {"category"})`.
   - Tidak ditemukan query database di dalam loop.
   - Seluruh method baca di service layer sudah memakai `@Transactional(readOnly = true)` (`DashboardService.getSummary` sengaja non-transactional agar cache hit Redis tidak membuka koneksi DB).

### 1.2 Kebersihan Komentar (*Strict Comment Hygiene*)
- Seluruh 94 file utama dan 15 file test diaudit: 0 *commented-out dead code*.
- Komentar inline dangkal pada `ReportGeneratorService.buildPdfBytes` dihapus dan diganti dengan method helper yang self-documenting.
- Komentar teknis *why* (seperti pessimistic write lock pada `RefreshTokenRepository.findByTokenForUpdate`, isolasi kegagalan `EventPublisher`, dan urutan commit-before-enqueue di `ReportExportService`) dipertahankan.

### 1.3 Clean Code, DRY & Dead Code
- **Dead code**: Menghapus `RefreshTokenRepository.findByToken(String)` yang tidak pernah dipanggil (hanya `findByTokenForUpdate` yang digunakan).
- **DRY**: Mengekstrak helper `uploadObjectToSupabase(...)` di `SupabaseStorageService.java` untuk menyatukan logika upload `uploadReceipt` dan `uploadReport` (~40 baris duplikasi dihilangkan).
- **Dekomposisi fungsi**: Memecah `ReportGeneratorService.buildPdfBytes` menjadi `addKpiSection`, `addBudgetSection`, dan `addTransactionsSection`.
- **Debug leftovers**: 0 `System.out.println` / `System.err.println` / `printStackTrace()`, 0 unused imports.

### File Backend yang Dimodifikasi (6)
- `backend/src/main/java/com/finpulse/repository/RefreshTokenRepository.java`
- `backend/src/main/java/com/finpulse/repository/TransactionRepository.java`
- `backend/src/main/java/com/finpulse/service/AlertService.java`
- `backend/src/main/java/com/finpulse/service/DashboardSummaryCacheService.java`
- `backend/src/main/java/com/finpulse/service/ReportGeneratorService.java`
- `backend/src/main/java/com/finpulse/service/SupabaseStorageService.java`

---

## 2. Frontend (`frontend/`)

### 2.1 Efisiensi & Performa
1. **Migrasi kalkulasi template ke `computed()` Signals**
   - `budget-form-modal.component.ts` & `.html`: Mengganti method `getEditPeriodLabel()` dan `getEditCategoryColor()` menjadi `computed()` Signals (`editPeriodLabel`, `editCategoryColor`).
   - `budgets.component.ts` & `.html`: Menambahkan `overallUtilizationLabel`, `overallClampedUtilization`, dan `overallProgressBarClass` sebagai `computed()` Signals agar template tidak memanggil helper/ternary di setiap siklus change detection.
   - `heartbeat-widget.component.ts`: Memusatkan normalisasi `income`/`expense` ke `normalizedAmounts = computed(...)` agar `ratio` dan `zone` tidak mengulang sanitasi angka.
2. **Penghapusan alokasi array literal di `@for`**
   - `reports.component.ts` & `.html`: Mengganti `@for (row of [1, 2, 3]; track row)` dengan properti `readonly skeletonRows = [1, 2, 3]`. Seluruh loop `@for` lain telah memiliki `track` yang tepat.
3. **RxJS Subscription Cleanup (`takeUntilDestroyed`)**
   - Menambahkan `takeUntilDestroyed(this.destroyRef)` pada `app.component.ts`, `login.component.ts`, `register.component.ts`, dan `app-layout.component.ts`.
   - Komponen lain (`dashboard`, `transactions`, `budgets`, `reports`, `alert-panel`) sudah menerapkan cleanup `Chart`, `interval`, `requestAnimationFrame`, dan `URL.revokeObjectURL`.

### 2.2 Kebersihan Komentar (*Strict Comment Hygiene*)
- Menghapus komentar dangkal (*what* comment) di `count-up.directive.ts`.
- Seluruh komentar tersisa hanya berupa *section divider* atau penjelasan alasan teknis non-obvious (*why*). Tidak ditemukan *commented-out dead code*.

### 2.3 Clean Code, DRY & Dead Code
- **Unused imports**: Menghapus import tidak terpakai (`CommonModule`, `HttpErrorResponse`, `extractApiError`, validator imports) pada `alert-panel`, `spinner`, `login`, `register`, `budget-form-modal`, `budgets`, `transactions`, dan `transaction-form-modal`.
- **DRY**:
  - `core/utils/form-validators.util.ts`: Mengekstrak `MAX_MONETARY_VALUE` dan `positiveDecimalValidator(...)` yang sebelumnya diduplikasi di modal budget & transaksi.
  - `shared/utils/format.util.ts`: Mengekstrak `clampPercentage()` dan `formatPercent()` yang sebelumnya diduplikasi di dashboard, budgets, dan alert-panel.
  - `core/utils/error.util.ts`: Menambahkan `isUnauthorizedError(error)` untuk menggantikan pengecekan 401 berulang di 7 komponen (12 lokasi).
  - `alert-panel.component.ts`: Mengekstrak `handleActionError(...)` untuk menggantikan 4 blok error handler yang identik.
- **CSS & Debug leftovers**: Seluruh 12 custom class (`fp-*`) di `styles.css` terverifikasi aktif dipakai; 0 `console.log` / `console.debug` di kode produksi.

### File Frontend yang Dimodifikasi (20)
- `frontend/src/app/app.component.ts`
- `frontend/src/app/core/utils/error.util.ts`
- `frontend/src/app/core/utils/form-validators.util.ts`
- `frontend/src/app/features/auth/login/login.component.ts`
- `frontend/src/app/features/auth/register/register.component.ts`
- `frontend/src/app/features/budgets/budget-form-modal.component.ts`
- `frontend/src/app/features/budgets/budget-form-modal.component.html`
- `frontend/src/app/features/budgets/budgets.component.ts`
- `frontend/src/app/features/budgets/budgets.component.html`
- `frontend/src/app/features/dashboard/dashboard.component.ts`
- `frontend/src/app/features/reports/reports.component.ts`
- `frontend/src/app/features/reports/reports.component.html`
- `frontend/src/app/features/transactions/transaction-form-modal.component.ts`
- `frontend/src/app/features/transactions/transactions.component.ts`
- `frontend/src/app/shared/components/alert-panel/alert-panel.component.ts`
- `frontend/src/app/shared/components/app-layout/app-layout.component.ts`
- `frontend/src/app/shared/components/heartbeat-widget/heartbeat-widget.component.ts`
- `frontend/src/app/shared/components/spinner/spinner.component.ts`
- `frontend/src/app/shared/directives/count-up.directive.ts`
- `frontend/src/app/shared/utils/format.util.ts`

---

## 3. Hasil Verifikasi Akhir

| Perintah Verifikasi | Hasil | Detail |
| --- | :---: | --- |
| `cd backend && mvn test` | **PASS** | `Tests run: 72, Failures: 0, Errors: 0, Skipped: 0` |
| `cd frontend && npm run build` | **PASS** | Bundle produksi + prerender 1 rute statis (`/`) sukses, 0 error |
| `cd frontend && npx ng test --watch=false --browsers=ChromeHeadless` | **PASS** | `TOTAL: 113 SUCCESS`, 0 failure |

**Kesimpulan**: Seluruh 185 pengujian otomatis (72 backend + 113 frontend) lulus tanpa regresi. Codebase bersih, efisien, dan siap melanjutkan tahap `@devops` / push ke GitHub.
