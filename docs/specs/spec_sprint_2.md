# Spesifikasi Teknis FinPulse — Sprint 2: Inti Finance & Analitik

## 1. Tujuan & Exit Criteria Sprint 2

Sprint 2 membangun fitur inti manajemen keuangan pribadi **FinPulse** di atas fondasi autentikasi Sprint 1, meliputi CRUD transaksi pemasukan/pengeluaran dengan paginasi dan filter, upload foto struk ke Supabase Storage, manajemen batas anggaran bulanan (*budgets*) beserta kalkulasi realisasi belanja, serta Dashboard Analitik interaktif.

### Exit Criteria
- [ ] Migrasi Flyway `V2__sprint2_analytics_indexes.sql` menambahkan indeks komposit untuk performa query analitik transaksi dan anggaran per periode.
- [ ] CRUD Transaksi (`GET /api/transactions`, `GET /api/transactions/{id}`, `POST /api/transactions`, `PUT /api/transactions/{id}`, `DELETE /api/transactions/{id}`) mendukung paginasi, filter tanggal (`startDate`, `endDate`), filter `type`, filter `categoryId`, dan pencarian `search`, dengan isolasi ketat per `userId`.
- [ ] Upload struk transaksi (`POST /api/transactions/receipt`) memvalidasi tipe file gambar (`image/jpeg`, `image/png`, `image/webp` via MIME & magic bytes) serta batas ukuran maksimal `5 MB`, lalu mengunggah ke Supabase Storage via REST API menggunakan `${SUPABASE_URL}` & `${SUPABASE_SERVICE_KEY}`.
- [ ] CRUD Anggaran Bulanan (`GET /api/budgets`, `POST /api/budgets`, `PUT /api/budgets/{id}`, `DELETE /api/budgets/{id}`) menghitung `spentAmount`, `remainingAmount`, `utilizationPercentage`, dan flag `isOverBudget` secara akurat untuk periode `(month, year)`.
- [ ] Endpoint analitik `GET /api/dashboard/summary` mengembalikan KPI bulanan & kumulatif, seri arus kas bulanan (`cashflowSeries`), rincian pengeluaran per kategori (`expenseByCategory`), daftar progres anggaran (`budgetProgress`), dan 5 transaksi terbaru (`recentTransactions`).
- [ ] Frontend Angular 19+ mengimplementasikan halaman penuh **Transaksi** (`/transactions`), **Anggaran** (`/budgets`), dan **Dashboard** (`/dashboard`) lengkap dengan kartu KPI, grafik arus kas bulanan (`chart.js`), *progress bar* anggaran, preview upload struk, *loading skeleton*, *empty state*, dan *toast notification*.

---

## 2. Arsitektur & Perubahan Struktur Direktori

### Backend (`app_build/backend/src/main/java/com/finpulse/`)
- `config/`: Tambahan `SupabaseProperties` (`@ConfigurationProperties(prefix = "supabase")`) dan bean `RestClient` untuk komunikasi REST ke Supabase Storage.
- `controller/`: `TransactionController`, `BudgetController`, `DashboardController`.
- `service/`: `TransactionService`, `BudgetService`, `DashboardService`, `StorageService` (interface) & `SupabaseStorageService` (implementasi REST Supabase Storage).
- `repository/`: `TransactionRepository` (dengan `JpaSpecificationExecutor<Transaction>` + query agregasi bulanan & per kategori), `BudgetRepository`.
- `dto/`: `PageResponse<T>`, `TransactionDto`, `CreateTransactionRequest`, `UpdateTransactionRequest`, `ReceiptUploadResponse`, `BudgetDto`, `CreateBudgetRequest`, `UpdateBudgetRequest`, `DashboardSummaryDto`, `MonthlyCashflowDto`, `CategoryBreakdownDto`.
- `exception/`: Tambahan `StorageUnavailableException` (`503`), `InvalidFileException` (`400`), `DuplicateBudgetException` (`409`), `BusinessValidationException` (`400`).
- `resources/db/migration/`: `V2__sprint2_analytics_indexes.sql`.

### Frontend (`app_build/frontend/src/app/`)
- `core/models/`: `transaction.model.ts`, `budget.model.ts`, `dashboard.model.ts`.
- `core/services/`: `transaction.service.ts`, `budget.service.ts`, `dashboard.service.ts`.
- `features/transactions/`:
  - `transactions.component.{ts,html,spec.ts}` (menggantikan placeholder Sprint 1)
  - `transaction-form-modal.component.{ts,html}` (form tambah/edit transaksi + upload & preview foto struk)
- `features/budgets/`:
  - `budgets.component.{ts,html,spec.ts}` (menggantikan placeholder Sprint 1)
  - `budget-form-modal.component.{ts,html}` (form tambah/edit batas anggaran)
- `features/dashboard/`:
  - `dashboard.component.{ts,html,spec.ts}` (menggantikan `DashboardShellComponent`, menampilkan KPI cards, grafik Chart.js arus kas bulanan + donat kategori, *progress bar* anggaran, dan transaksi terbaru).
- `shared/pipes/` atau `shared/utils/`: helper format mata uang Rupiah (`IDR`) dan format tanggal Indonesia.

---

## 3. Skema Database & Migrasi Flyway (`V2__sprint2_analytics_indexes.sql`)

Tabel `transactions` dan `budgets` sudah dibuat pada `V1__init_schema.sql`. Pada Sprint 2 ditambahkan migrasi `V2__sprint2_analytics_indexes.sql` untuk mengoptimalkan query rentang tanggal per user dan tipe transaksi (mendukung kalkulasi `spentAmount` dan grafik arus kas bulanan tanpa *full table scan*):

```sql
CREATE INDEX IF NOT EXISTS idx_transactions_user_type_date
    ON transactions (user_id, type, transaction_date);

CREATE INDEX IF NOT EXISTS idx_budgets_user_period
    ON budgets (user_id, year, month);
```

### Aturan Konsistensi & Integritas Data
1. **Kepemilikan Kategori**: Saat membuat/mengubah transaksi atau anggaran, `categoryId` wajib merujuk kategori sistem (`user_id IS NULL`) atau kategori milik pengguna aktif (`user_id = currentUserId`). Jika tidak ditemukan atau milik pengguna lain, kembalikan `404 Not Found`.
2. **Kecocokan Tipe Transaksi vs Kategori**: `CreateTransactionRequest.type` dan `UpdateTransactionRequest.type` wajib sama dengan `Category.type`. Jika berbeda, tolak dengan `400 Bad Request`.
3. **Aturan Kategori Anggaran**: Anggaran (`Budget`) hanya boleh dibuat untuk kategori bertipe `EXPENSE`. Jika kategori bertipe `INCOME`, kembalikan `400 Bad Request`.
4. **Unik Periode Anggaran**: Satu pengguna hanya boleh memiliki 1 anggaran per `(category_id, month, year)`. Duplikasi mengembalikan `409 Conflict`.
5. **Kalkulasi `spentAmount` Anggaran**: Menggunakan rentang tanggal inklusif `[LocalDate.of(year, month, 1), YearMonth.of(year, month).atEndOfMonth()]` agar memanfaatkan indeks B-Tree `(user_id, type, transaction_date)` dan kompatibel penuh antara PostgreSQL & H2 mode PostgreSQL.

---

## 4. Integrasi REST Supabase Storage (Upload Struk)

### Konfigurasi Environment (`application.yml`)
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 6MB

supabase:
  url: ${SUPABASE_URL:}
  service-key: ${SUPABASE_SERVICE_KEY:}
  storage-bucket: ${SUPABASE_STORAGE_BUCKET:receipts}
```

### Alur Upload (`POST /api/transactions/receipt`)
1. Menerima `multipart/form-data` dengan field `file`.
2. Validasi file:
   - File tidak kosong.
   - Ukuran `<= 5 * 1024 * 1024` bytes (5 MB).
   - `Content-Type` dan *magic bytes* wajib valid sebagai salah satu dari:
     - **JPEG** (`FF D8 FF`) -> `.jpg`
     - **PNG** (`89 50 4E 47 0D 0A 1A 0A`) -> `.png`
     - **WebP** (`RIFF....WEBP`) -> `.webp`
   - Jika gagal validasi, kembalikan `400 Bad Request`.
3. Jika `supabase.url` atau `supabase.service-key` kosong (belum dikonfigurasi di environment), lemparkan `StorageUnavailableException` (`503 Service Unavailable`).
4. Bangun *object path* terisolasi per user: `<userId>/<uuid>.<ext>`.
5. Kirim request `POST ${SUPABASE_URL}/storage/v1/object/${SUPABASE_STORAGE_BUCKET}/${objectPath}` dengan header:
   - `Authorization: Bearer ${SUPABASE_SERVICE_KEY}`
   - `apikey: ${SUPABASE_SERVICE_KEY}`
   - `Content-Type: <validatedContentType>`
   - `x-upsert: true`
6. Kembalikan `201 Created` dengan `ReceiptUploadResponse`:
   - `receiptUrl`: `${SUPABASE_URL}/storage/v1/object/public/${SUPABASE_STORAGE_BUCKET}/${objectPath}`
   - `filePath`: `<objectPath>`
   - `contentType`: `<validatedContentType>`
   - `sizeBytes`: ukuran file dalam byte.
7. Saat `CreateTransactionRequest` atau `UpdateTransactionRequest` menyertakan `receiptUrl` non-null, backend memvalidasi bahwa URL valid berawalan `https://` atau `http://` (mencegah payload `javascript:`/`data:`).

---

## 5. Kontrak API & Agregasi Analitik Dashboard

Ringkasan endpoint baru Sprint 2 (detail lengkap ada di `production_artifacts/api_contract.yaml`):

| Endpoint | Method | Ringkasan |
|---|---|---|
| `/api/transactions` | `GET` | Daftar transaksi milik user dengan paginasi & filter (`page`, `size`, `sortBy`, `sortDir`, `type`, `categoryId`, `startDate`, `endDate`, `search`) |
| `/api/transactions/{id}` | `GET` | Detail 1 transaksi milik user |
| `/api/transactions` | `POST` | Tambah transaksi baru (`201 Created`) |
| `/api/transactions/{id}` | `PUT` | Perbarui transaksi milik user (`200 OK`) |
| `/api/transactions/{id}` | `DELETE` | Hapus transaksi milik user (`204 No Content`) |
| `/api/transactions/receipt` | `POST` | Upload foto struk ke Supabase Storage (`201 Created`) |
| `/api/budgets` | `GET` | Daftar anggaran bulanan user (opsional `month` & `year`, default bulan/tahun berjalan) |
| `/api/budgets` | `POST` | Tambah batas anggaran kategori bulanan (`201 Created`) |
| `/api/budgets/{id}` | `PUT` | Perbarui batas nominal anggaran (`200 OK`) |
| `/api/budgets/{id}` | `DELETE` | Hapus batas anggaran (`204 No Content`) |
| `/api/dashboard/summary` | `GET` | Ringkasan KPI, grafik arus kas bulanan (`monthsBack`, default 6), pengeluaran per kategori, progres anggaran, dan 5 transaksi terbaru |

---

## 6. Spesifikasi UI & Alur Frontend (Angular 19+)

### Dependensi Tambahan
- `chart.js` (`^4.x`) untuk rendering `<canvas>` grafik arus kas bulanan (Grouped Bar Chart: Pemasukan vs Pengeluaran) dan grafik komposisi pengeluaran per kategori (Doughnut Chart) secara reaktif dengan Angular Signals & `viewChild`.

### Halaman & Alur Pengguna
1. **Dashboard (`/dashboard`)**:
   - **Header & Filter Periode**: Pemilih Bulan & Tahun (default bulan/tahun saat ini) yang memicu pemuatan ulang `GET /api/dashboard/summary`.
   - **3 Kartu KPI Utama**:
     - **Total Pemasukan** (periode terpilih, aksen Emerald `#10B981`).
     - **Total Pengeluaran** (periode terpilih, aksen Rose `#EF4444`).
     - **Sisa Saldo Periode** (`totalIncome - totalExpense` bulan terpilih) disertai info kecil **Saldo Kumulatif Total** (`cumulativeBalance`).
   - **Grafik Analitik (Chart.js)**:
     - **Arus Kas Bulanan** (6 bulan terakhir): Bar chart perbandingan `totalIncome` vs `totalExpense`.
     - **Pengeluaran per Kategori**: Doughnut chart proporsi kategori pengeluaran periode terpilih (dengan *empty state* bila belum ada pengeluaran).
   - **Progres Anggaran Bulan Ini**: Daftar *progress bar* kategori anggaran dengan pewarnaan dinamis (`< 80%` Emerald, `80% - 99.9%` Amber, `>= 100%` Rose + badge *Over Budget*), serta tautan ke `/budgets`.
   - **Transaksi Terbaru**: 5 transaksi terakhir dengan badge kategori, nominal terformat `Rp`, dan tautan ke `/transactions`.

2. **Transaksi (`/transactions`)**:
   - **Toolbar Filter**: Filter rentang tanggal (`startDate`, `endDate`), Tipe (`Semua`, `INCOME`, `EXPENSE`), Kategori (sinkron dari `/api/categories`), input pencarian deskripsi (*debounced*), dan tombol **Reset Filter**.
   - **Tabel / Daftar Responsif**: Kolom tanggal, kategori (warna & badge), deskripsi, indikator lampiran struk (klik untuk membuka modal preview gambar struk), tipe, nominal `Rp`, dan aksi Edit / Hapus.
   - **Paginasi**: Navigasi halaman (`Sebelumnya`, `Halaman X dari Y`, `Berikutnya`, pilihan jumlah per halaman `10`/`20`/`50`).
   - **Modal Tambah / Edit Transaksi**:
     - Reactive Form: pilihan tipe (`INCOME` / `EXPENSE`) yang otomatis memfilter dropdown kategori sesuai tipe, `amount` (> 0), `transactionDate`, `description`, dan area **Upload Struk** (drag/pilih file -> validasi client JPEG/PNG/WebP max 5MB -> preview thumbnail lokal -> upload ke `POST /api/transactions/receipt` sebelum menyimpan transaksi, atau opsi hapus lampiran struk).
   - **Konfirmasi Hapus**: Dialog konfirmasi sebelum memanggil `DELETE /api/transactions/{id}`.

3. **Anggaran (`/budgets`)**:
   - **Header Periode**: Pemilih Bulan & Tahun + kartu ringkasan (Total Batas Anggaran, Total Terpakai, Sisa Anggaran).
   - **Daftar Kartu Anggaran**: Menampilkan setiap kategori `EXPENSE` yang sudah diatur batasnya pada periode tersebut dengan *progress bar*, angka `Terpakai / Batas`, persentase utilitas, status (`Aman` / `Mendekati Batas` / `Melebihi Batas`), serta tombol Edit Batas & Hapus.
   - **Modal Tambah / Edit Anggaran**:
     - Saat tambah: dropdown hanya menampilkan kategori bertipe `EXPENSE` (menghindari input kategori yang sudah memiliki anggaran di periode terpilih dengan info yang jelas), input `amountLimit` (> 0), bulan & tahun.
     - Saat edit: memperbarui `amountLimit` via `PUT /api/budgets/{id}`.
