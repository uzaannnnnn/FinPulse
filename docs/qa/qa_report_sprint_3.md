# Laporan Audit QA & Keamanan FinPulse — Sprint 3

**Tanggal Audit**: 2026-11-21  
**Auditor**: QA & Security Auditor (`@qa`)  
**Cakupan**: `app_build/backend/` (`com.finpulse`) & `app_build/frontend/` (`finpulse-web`) terhadap `production_artifacts/api_contract.yaml` (v3.0.0-sprint3), `production_artifacts/spec_sprint_3.md`, serta regresi Sprint 1 & Sprint 2 (`production_artifacts/qa_report_sprint_2.md`)  
**Status Akhir**: **LULUS (READY FOR SPRINT 3 SIGN-OFF / DEVOPS VERIFICATION)**

---

## 1. Ringkasan Eksekutif

Audit menyeluruh terhadap keselarasan kontrak API, konsistensi skema Flyway `V3`, keamanan (zero hardcoded secrets, isolasi data antar user, proteksi CSV formula injection, sanitasi URL & error message), ketahanan Redis cache (race-condition & outage fallback), keandalan worker RabbitMQ, serta edge-case Frontend Angular 19+ telah dilakukan untuk seluruh ruang lingkup **Sprint 3** (**Cache Redis + Auto-Invalidation**, **Notifikasi Over-Budget RabbitMQ**, **Background Job Ekspor Laporan PDF/CSV**, **Indikator Status Sinkronisasi Data**), termasuk verifikasi regresi Sprint 1 & Sprint 2.

- **Contract Alignment**: Seluruh 7 endpoint baru Sprint 3 (`/api/alerts`, `/api/alerts/{id}/read`, `/api/alerts/read-all`, `/api/alerts/{id}`, `/api/reports/export`, `/api/reports`, `/api/reports/{id}`) serta ekstensi `/api/dashboard/summary` (`generatedAt`, `fromCache`, header `X-Cache-Status: HIT|MISS`, CORS `exposedHeaders`) selaras 100% antara Backend, Frontend, dan `production_artifacts/api_contract.yaml`.
- **Database Schema & Migrations**: `V3__sprint3_alerts_and_reports.sql` (`budget_alerts`, `report_exports`, constraint, dan 5 indeks komposit) konsisten dengan entity JPA `BudgetAlert.java` & `ReportExport.java` serta foreign key ke `V1__init_schema.sql`.
- **Temuan & Perbaikan**: Ditemukan **6 temuan nyata** (**1 High**, **4 Medium**, **1 Low**), termasuk celah *CSV formula injection bypass* via spasi/baris baru di awal sel, potensi `numeric field overflow` pada `budget_alerts.utilization_percentage` (`NUMERIC(7,2)`), serta kebocoran state `AlertService`/`SyncStatusService` lintas sesi pada `401` token expired di `authInterceptor`. **Seluruh temuan telah diperbaiki langsung** dan diverifikasi dengan pengujian regresi otomatis.
- **Verifikasi Pengujian & Build Akhir (Pasca Perubahan Final)**:
  - Backend `mvn test`: **72 tests run, 0 failures, 0 errors, 0 skipped** (`BUILD SUCCESS`; naik dari 66 menjadi 72 test).
  - Frontend `CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" npx ng test --watch=false --browsers=ChromeHeadless`: **101 of 101 SUCCESS** (naik dari 98 menjadi 101 specs, 0 failures).
  - Frontend `npm run build`: **Sukses tanpa error** (`Initial total: 352.06 kB`, 0 warnings/errors).

---

## 2. Hasil Audit Keselarasan Kontrak (`api_contract.yaml`) & Skema Database

### 2.1 Matriks Endpoint & Skema DTO Sprint 3

| Endpoint & Method | Request / Query Param | Status Code Kontrak | Backend (`@RestController`) | Frontend (`Service` & `Component`) | Status |
|---|---|---|---|---|---|
| `GET /api/dashboard/summary` | `month`, `year`, `monthsBack` (`1..12`, default `6`) | `200` (+ header `X-Cache-Status: HIT\|MISS`), `400`, `401` | `DashboardController.getDashboardSummary` -> `DashboardSummaryDto` (`generatedAt`, `fromCache`) + header `X-Cache-Status`; `SecurityConfig` mengekspos `X-Cache-Status` di CORS | `DashboardService.getSummary()`, `DashboardSummaryDto`, `SyncStatusService`, `DashboardComponent`, `AppLayoutComponent` | **Sesuai** |
| `GET /api/alerts` | `unreadOnly` (bool, default `false`), `limit` (`1..100`, default `20`) | `200`, `400`, `401` | `AlertController.listAlerts` -> `AlertListResponse` (`unreadCount`, `alerts: BudgetAlertDto[]` dengan `@JsonProperty("isRead")`) | `AlertService.loadAlerts()`, `AlertPanelComponent`, `AppLayoutComponent`, `DashboardComponent` | **Sesuai** |
| `PATCH /api/alerts/{id}/read` | Path: `id` (`UUID`) | `200`, `401`, `404` | `AlertController.markAlertAsRead` -> `200 BudgetAlertDto` (isolasi `userId` -> `404`) | `AlertService.markAsRead(id)` + `AlertPanelComponent` | **Sesuai** |
| `PATCH /api/alerts/read-all` | — | `204`, `401` | `AlertController.markAllAlertsAsRead` -> `204 No Content` (scoped per `userId`) | `AlertService.markAllAsRead()` + `AlertPanelComponent` | **Sesuai** |
| `DELETE /api/alerts/{id}` | Path: `id` (`UUID`) | `204`, `401`, `404` | `AlertController.deleteAlert` -> `204 No Content` (isolasi `userId` -> `404`) | `AlertService.deleteAlert(id)` + `AlertPanelComponent` | **Sesuai** |
| `POST /api/reports/export` | Body: `CreateReportExportRequest` (`month: 1..12`, `year: 2020..2100`, `format: PDF\|CSV`) | `202`, `400`, `401`, `503` | `ReportController.requestReportExport` -> `202 Accepted ReportExportDto`; `503` jika RabbitMQ gagal & job ditandai `FAILED` | `ReportService.requestExport()`, `ReportsComponent`, shortcut PDF/CSV di `DashboardComponent` | **Sesuai** |
| `GET /api/reports` | `limit` (`1..100`, default `20`) | `200`, `400`, `401` | `ReportController.listReportExports` -> `200 List<ReportExportDto>` (scoped per `userId`) | `ReportService.listExports()` + auto-polling `ReportsComponent` | **Sesuai** |
| `GET /api/reports/{id}` | Path: `id` (`UUID`) | `200`, `401`, `404` | `ReportController.getReportExportById` -> `200 ReportExportDto` (isolasi `userId` -> `404`) | `ReportService.getExport(id)` | **Sesuai** |

### 2.2 Konsistensi Migrasi `V3__sprint3_alerts_and_reports.sql` vs Entity JPA & `V1`/`V2`

- **`budget_alerts`** (`V3` vs `BudgetAlert.java`): Seluruh 13 kolom (`id`, `user_id`, `budget_id` nullable `ON DELETE SET NULL`, `category_id`, `month`, `year`, `amount_limit NUMERIC(15,2)`, `spent_amount NUMERIC(15,2)`, `utilization_percentage NUMERIC(7,2)`, `message TEXT`, `is_read BOOLEAN`, `created_at`, `updated_at`) dan 3 indeks (`idx_budget_alerts_user_created`, `idx_budget_alerts_user_read`, `idx_budget_alerts_user_cat_period`) selaras penuh.
- **`report_exports`** (`V3` vs `ReportExport.java`): Seluruh 15 kolom (`id`, `user_id`, `month`, `year`, `format VARCHAR(10)`, `status VARCHAR(20)`, `file_name`, `file_path`, `file_url`, `content_type`, `size_bytes`, `error_message`, `created_at`, `updated_at`, `completed_at`) dan 2 indeks (`idx_report_exports_user_created`, `idx_report_exports_user_status`) selaras penuh.

---

## 3. Hasil Audit Keamanan & Edge-Case (Fokus Sprint 3)

1. **Zero Hardcoded Secrets**:
   - `app_build/backend/src/main/resources/application.yml` mengeksternalisasi seluruh konfigurasi sensitif dan broker melalui environment variables: `${SUPABASE_DB_URL}`, `${SUPABASE_DB_USER}`, `${SUPABASE_DB_PASSWORD}`, `${SUPABASE_URL:}`, `${SUPABASE_SERVICE_KEY:}`, `${SUPABASE_STORAGE_BUCKET:receipts}`, `${SUPABASE_REPORTS_BUCKET:reports}`, `${JWT_SECRET}`, `${REDIS_HOST:localhost}`, `${REDIS_PORT:6379}`, `${REDIS_PASSWORD:}`, `${RABBITMQ_HOST:localhost}`, `${RABBITMQ_PORT:5672}`, `${RABBITMQ_USERNAME:finpulse}`, `${RABBITMQ_PASSWORD:finpulse_dev}` (fallback dev lokal sesuai spesifikasi `docker-compose.yml`).
   - Pemindaian `grep` pada `app_build/backend/src/main` dan `app_build/frontend/src` mengonfirmasi tidak ada kredensial produksi, service key, maupun token statis yang ter-hardcode.
2. **Redis Cache Race Condition, Concurrency & Isolasi**:
   - `DashboardService` menyelesaikan periode lewat `PeriodResolver.resolve(month, year)` sebelum memanggil `DashboardSummaryCacheService.getOrComputeSummary(...)` sehingga key `@Cacheable` selalu berformat `<userId>:<year>:<month>:<monthsBack>` (bebas key `null:null`).
   - `AtomicBoolean cacheMissMarker` dibuat lokal per invocation di `DashboardService.getSummary(...)` (stack-confined per thread), sehingga 100% aman dari race condition lintas thread/request.
   - `DashboardCacheInvalidator.evictAfterCommit(userId)` mendaftarkan `TransactionSynchronization.afterCommit()` pada setiap mutasi `TransactionService` (`create`, `update`, `delete`) dan `BudgetService` (`create`, `update`, `delete`), serta hanya menghapus key milik `userId` terkait (`SCAN *dashboardSummary::<userId>:*` di Redis / prefix `<userId>:` di `ConcurrentMapCache`), menjaga isolasi cache User B.
   - `CacheConfig` menggunakan `StringRedisSerializer` + `Jackson2JsonRedisSerializer<>(redisObjectMapper, DashboardSummaryDto.class)` dengan `JavaTimeModule` (tanpa Java native serialization) dan `CacheErrorHandler` yang menangkap error Redis tanpa mengembalikan HTTP 500.
3. **RabbitMQ & Async Workers**:
   - `EventPublisher.publishTransactionCreatedAfterCommit(...)` dipanggil pasca-commit DB dan menelan kegagalan broker agar penyimpanan transaksi inti tetap berhasil meskipun RabbitMQ offline.
   - `ReportExportService.requestExport(...)` menyimpan dan meng-commit record `PENDING` sebelum publish ke `finpulse.report.export`; jika broker gagal, status langsung diperbarui menjadi `FAILED` dan mengembalikan `503 Service Unavailable`.
   - `OverBudgetAlertConsumer` mengisolasi query berdasarkan `userId`, mengabaikan transaksi `INCOME` / tanpa budget / dalam batas, dan melakukan deduplikasi update pada alert `isRead = false` untuk `(userId, categoryId, month, year)`.
   - `ReportExportConsumer` bersifat idempoten (melewati job `COMPLETED`), mengunggah file ke `<userId>/reports/<uuid>-<sanitizedFileName>`, serta menyimpan `errorMessage` aman tanpa membocorkan stack trace atau `SUPABASE_SERVICE_KEY`.
4. **Isolasi Data Antar User**:
   - `AlertService` dan `ReportExportService` memfilter seluruh operasi baca/ubah/hapus menggunakan `findByIdAndUserId` dan query `WHERE user.id = :userId`. User B selalu menerima `404 Not Found` saat mencoba mengakses, menandai dibaca, atau menghapus alert maupun polling job laporan milik User A.
5. **Frontend Edge Cases**:
   - `ReportsComponent`: auto-polling `interval(2500)` + `exhaustMap` + `takeUntilDestroyed` otomatis berhenti saat tidak ada job `PENDING`/`PROCESSING` dan saat komponen dihancurkan (`ngOnDestroy`), tidak menampilkan toast duplikat pada `401`, menyaring `fileUrl` via `sanitizeHttpUrl()`, dan menegakkan batas tahun `2020..2100`.
   - `AlertPanelComponent` & `AppLayoutComponent`: `@HostListener` outside click & Escape otomatis dibersihkan Angular saat destroy, `categoryColor` disanitasi lewat `sanitizeHexColor()`, toast `401` disupresi, serta state alert & sync di-reset saat logout maupun sesi kedaluwarsa.
   - `DashboardComponent`: badge status sinkronisasi (`Cache Redis` / `Live DB` + `generatedAt`) akurat, tombol cepat **Ekspor PDF** / **Ekspor CSV** terlindungi guard `exportingFormat() !== null` dan menangani `503` secara ramah.

---

## 4. Temuan Audit & Perbaikan yang Diterapkan

1. **[HIGH] Bypass Proteksi CSV Formula Injection via Leading Whitespace / Newline**
   - **Lokasi**: `app_build/backend/src/main/java/com/finpulse/service/ReportGeneratorService.java`
   - **Akar Masalah**: `sanitizeCsvFormula()` sebelumnya hanya memeriksa `value.charAt(0)` terhadap `=`, `+`, `-`, `@`, `\t`, `\r`, sehingga nilai yang diawali spasi atau newline seperti `" =CMD|'/C calc'!A0"` atau `"\n=HYPERLINK(...)"` lolos tanpa prefiks `'` dan tetap berisiko dieksekusi di aplikasi spreadsheet.
   - **Perbaikan**: Memeriksa karakter pertama (`=`, `+`, `-`, `@`, `\t`, `\r`, `\n`) sekaligus karakter non-whitespace pertama (`value.stripLeading()`) agar formula dengan awalan spasi/newline turut diberi prefiks `'`.

2. **[MEDIUM] Potensi Overflow `NUMERIC(7, 2)` pada `budget_alerts.utilization_percentage`**
   - **Lokasi**: `app_build/backend/src/main/java/com/finpulse/messaging/OverBudgetAlertConsumer.java`
   - **Akar Masalah**: Kolom `budget_alerts.utilization_percentage` bertipe `NUMERIC(7, 2)` (maksimum `99999.99`). Pengeluaran besar terhadap batas anggaran kecil (mis. Rp 50.000.000 vs batas Rp 100 -> `50000000.00%`) akan memicu *PostgreSQL numeric field overflow* dan menyebabkan retry/DLQ pada consumer RabbitMQ.
   - **Perbaikan**: Melakukan clamping nilai `utilizationPercentage` pada batas atas `99999.99` sebelum disimpan ke `budget_alerts`.

3. **[MEDIUM] Kebocoran State `AlertService` & `SyncStatusService` Saat Sesi Berakhir (`401` Refresh Gagal)**
   - **Lokasi**: `app_build/frontend/src/app/core/interceptors/auth.interceptor.ts`
   - **Akar Masalah**: Reset `AlertService` dan `SyncStatusService` hanya terjadi pada logout manual di `AppLayoutComponent.onLogout()`, belum dipanggil ketika refresh token gagal (`401`) di `authInterceptor`. Jika pengguna berbeda masuk di tab yang sama setelah sesi kedaluwarsa, notifikasi over-budget dan status sinkronisasi milik pengguna sebelumnya dapat tersisa di memori.
   - **Perbaikan**: Menginjeksi `AlertService` dan `SyncStatusService` pada `authInterceptor` dan memanggil `.reset()` setiap kali `authService.clearSession()` dijalankan.

4. **[MEDIUM] Ketahanan Deserialisasi JSON Redis & RabbitMQ Terhadap Evolusi Skema**
   - **Lokasi**: `app_build/backend/src/main/java/com/finpulse/config/CacheConfig.java`, `app_build/backend/src/main/java/com/finpulse/messaging/RabbitMqConfig.java`
   - **Akar Masalah**: `ObjectMapper` mandiri untuk serializer Redis dan RabbitMQ belum menonaktifkan `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES`, berisiko memicu error deserialisasi saat rolling deployment menambah field baru pada DTO/event.
   - **Perbaikan**: Menonaktifkan `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` pada `redisObjectMapper` dan `amqpObjectMapper`.

5. **[MEDIUM] Daftar Alert Tidak Otomatis Disegarkan Setelah Mutasi Anggaran di `BudgetsComponent`**
   - **Lokasi**: `app_build/frontend/src/app/features/budgets/budgets.component.ts`
   - **Akar Masalah**: `TransactionsComponent.onTransactionSaved()` sudah menyegarkan `AlertService.loadAlerts()`, namun `BudgetsComponent.onBudgetSaved()` belum melakukannya sehingga badge lonceng dan banner dashboard tidak langsung terbarui setelah perubahan anggaran.
   - **Perbaikan**: Memanggil `alertService.loadAlerts()` (dengan `catchError(() => EMPTY)` dan `takeUntilDestroyed`) di `BudgetsComponent.onBudgetSaved()`.

6. **[LOW] Hardening Null-Check Format Laporan, Panjang Nama File Storage, & JPA `@Modifying` Flush**
   - **Lokasi**: `ReportGeneratorService.java`, `SupabaseStorageService.java`, `BudgetAlertRepository.java`
   - **Perbaikan**: Menambahkan guard `format == null` pada `ReportGeneratorService.generate()`, membatasi panjang `sanitizeReportFileName()` maksimal 120 karakter di `SupabaseStorageService`, dan menambahkan `clearAutomatically = true, flushAutomatically = true` pada `@Modifying` di `BudgetAlertRepository.markAllAsReadByUserId()`.

---

## 5. Ringkasan Penambahan & Verifikasi Pengujian Otomatis

### 5.1 Pengujian Baru & Diperluas
- **Backend (`app_build/backend/src/test/`)**:
  - `CacheConfigTest` (+2 test baru): round-trip serialisasi/deserialisasi JSON `DashboardSummaryDto` (`Instant`, `LocalDate`, `BigDecimal`, `BudgetDto.isOverBudget`) tanpa Java serialization, serta verifikasi `CacheErrorHandler` menelan kegagalan koneksi Redis tanpa melempar exception.
  - `ReportGeneratorServiceTest` (+2 test baru): proteksi CSV formula injection berawalan spasi & newline (`" =CMD..."`, `"  +SUM..."`, `"\n=HYPERLINK..."`) termasuk sel quoted; generasi PDF OpenPDF bebas crash pada deskripsi `null`, teks sangat panjang, Unicode, serta periode tanpa transaksi/anggaran.
  - `OverBudgetAlertConsumerTest` (+1 test baru): clamping utilisasi ekstrem ke `99999.99` agar aman terhadap `NUMERIC(7, 2)`, serta no-op saat budget periode tidak ada.
  - `SupabaseStorageServiceTest` (+1 test baru): verifikasi `uploadReport` mengisolasi path `<userId>/reports/<uuid>-...` dan menetralkan nama file *path traversal*.
- **Frontend (`app_build/frontend/src/app/`)**:
  - `auth.interceptor.spec.ts` (+1 spec baru): verifikasi `AlertService.reset()` dan `SyncStatusService.reset()` dipanggil saat refresh `401` gagal.
  - `reports.component.spec.ts` (+1 spec baru): verifikasi supresi toast duplikat pada `401`, guard double-submit, dan validasi batas tahun `2020..2100`.
  - `alert-panel.component.spec.ts` (+1 spec baru): verifikasi sanitasi `categoryColor` berbahaya (`javascript:alert(1)` -> `#EF4444`) dan supresi toast pada `401`.

### 5.2 Hasil Eksekusi Terakhir (Pasca Perubahan Final)

| Komponen | Perintah Eksekusi | Hasil Aktual |
|---|---|---|
| Backend Test Suite | `cd /Users/zannho/Desktop/FinPulse/app_build/backend && mvn test` | **72 tests run, 0 failures, 0 errors, 0 skipped** (`BUILD SUCCESS`) |
| Frontend Unit & Component Tests | `cd /Users/zannho/Desktop/FinPulse/app_build/frontend && CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" npx ng test --watch=false --browsers=ChromeHeadless` | **101 of 101 SUCCESS** (`TOTAL: 101 SUCCESS`) |
| Frontend Production Build | `cd /Users/zannho/Desktop/FinPulse/app_build/frontend && npm run build` | **Application bundle generation complete** (`Initial total: 352.06 kB`, 0 errors) |

---

## 6. Kesimpulan Kesiapan Sprint 3

Implementasi Sprint 3 **FinPulse** di `app_build/backend/` dan `app_build/frontend/` dinyatakan **LULUS AUDIT QA & KEAMANAN** dan **SIAP** melanjutkan ke **Tahap 4 (`@devops`)** untuk menyiapkan `app_build/docker-compose.yml` (Redis & RabbitMQ), memperbarui `.env.example`, memverifikasi migrasi `V3__sprint3_alerts_and_reports.sql`, dan menjalankan seluruh layanan lokal FinPulse secara penuh.
