# Laporan Audit QA & Keamanan FinPulse — Sprint 1

**Tanggal Audit**: 2026-11-21  
**Auditor**: QA & Security Auditor (`@qa`)  
**Cakupan**: `app_build/backend/` (`com.finpulse`) & `app_build/frontend/` (`finpulse-web`) terhadap `production_artifacts/api_contract.yaml` dan `production_artifacts/spec_sprint_1.md`  
**Status Akhir**: **LULUS (READY FOR SPRINT 1 SIGN-OFF / DEVOPS VERIFICATION)**

---

## 1. Ringkasan Eksekutif

Audit menyeluruh telah dilakukan pada implementasi Backend (Spring Boot 3.5.16 / Java 21) dan Frontend (Angular 19 / Standalone Components & Signals) untuk Sprint 1 (Fondasi & Autentikasi).

- **Contract Alignment**: Seluruh 6 endpoint Sprint 1 (`/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`, `/api/auth/me`, `/api/categories`), HTTP method, query parameter, status code, skema `ErrorResponse`, serta skema database Flyway `V1__init_schema.sql` dan entity JPA selaras 100% dengan `production_artifacts/api_contract.yaml` dan `production_artifacts/spec_sprint_1.md`.
- **Temuan & Perbaikan**: Ditemukan **7 temuan nyata** (1 High, 4 Medium, 2 Low) terkait batas byte UTF-8 BCrypt saat registrasi/login, validasi spasi pada `fullName`/form input, bug scope operator RxJS `catchError` pada `authInterceptor`, deduplikasi toast/redirect pada concurrent 401, celah revokasi refresh token saat logout dengan access token kedaluwarsa, guard null subject JWT, dan kelengkapan handler error HTTP standar. **Seluruh temuan telah diperbaiki langsung** dan diverifikasi melalui test baru serta build ulang.
- **Verifikasi Pengujian & Build**:
  - Backend `mvn test`: **32 tests run, 0 failures, 0 errors, 0 skipped** (naik dari 25 menjadi 32 test).
  - Frontend `CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" npx ng test --watch=false --browsers=ChromeHeadless`: **35 specs run, 35 SUCCESS** (naik dari 26 menjadi 35 specs).
  - Frontend `npm run build`: **Sukses tanpa error** (`Initial total: 317.48 kB`).

---

## 2. Hasil Audit Keselarasan Kontrak (`api_contract.yaml`) & Skema Database

### 2.1 Tabel Matriks Endpoint & Skema DTO

| Endpoint & Method | Request / Query Param | Status Code Kontrak | Backend (`@RestController`) | Frontend (`AuthService` / `CategoryService`) | Status |
|---|---|---|---|---|---|
| `POST /api/auth/register` | Body: `RegisterRequest` (`fullName`, `email`, `password`) | `201`, `400`, `409` | `AuthController.register` -> `201 AuthResponse`; validasi DTO + `EmailAlreadyExistsException` (`409`) | `AuthService.register()` + `RegisterComponent` (mengirim `fullName.trim()`, `email.trim()`, `password`) | **Sesuai** |
| `POST /api/auth/login` | Body: `LoginRequest` (`email`, `password`) | `200`, `400`, `401` | `AuthController.login` -> `200 AuthResponse`; `InvalidCredentialsException` (`401`) | `AuthService.login()` + `LoginComponent` (mengirim `email.trim()`, `password`) | **Sesuai** |
| `POST /api/auth/refresh` | Body: `RefreshTokenRequest` (`refreshToken`) | `200`, `400`, `401` | `AuthController.refresh` -> `200 AuthResponse` dengan rotasi token (`PESSIMISTIC_WRITE`) | `AuthService.refreshToken()` & `refreshTokenShared()` | **Sesuai** |
| `POST /api/auth/logout` | Optional Body: `RefreshTokenRequest`, Bearer JWT | `204`, `401` | `AuthController.logout` -> `204 No Content`, mencabut seluruh token aktif milik `principal.getId()` | `AuthService.logout()` mengirim `{ refreshToken }` atau `null` | **Sesuai** |
| `GET /api/auth/me` | Bearer JWT | `200`, `401` | `AuthController.me` -> `200 UserDto` (`id`, `email`, `fullName`, `createdAt`) | `AuthService.loadProfile()` -> update `currentUser` signal | **Sesuai** |
| `GET /api/categories` | Optional query `?type=INCOME\|EXPENSE`, Bearer JWT | `200`, `400`, `401` | `CategoryController.getCategories` -> `200 List<CategoryDto>` (`isDefault` via `@JsonProperty("isDefault")`) | `CategoryService.getCategories(type?)` + `DashboardShellComponent` | **Sesuai** |
| `ErrorResponse` | — | Semua `4xx`/`5xx` | `GlobalExceptionHandler`, `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler` -> `timestamp` ISO-8601, `status`, `error`, `message`, `path`, `validationErrors` | `ErrorResponse` interface & `extractApiError()` | **Sesuai** |

### 2.2 Konsistensi `V1__init_schema.sql` vs Entity JPA & Spec Sprint 1

Audit kolom, tipe data, nullability, constraint, indeks, dan data seed pada `app_build/backend/src/main/resources/db/migration/V1__init_schema.sql`:
- **`users`**: `id` (`UUID`), `email` (`VARCHAR(255) UNIQUE NOT NULL`), `password_hash` (`VARCHAR(255) NOT NULL`), `full_name` (`VARCHAR(100) NOT NULL`), `created_at`, `updated_at` -> selaras dengan `User.java`.
- **`categories`**: `id`, `user_id` (nullable FK ke `users.id ON DELETE CASCADE`), `name` (`VARCHAR(100)`), `type` (`CHECK (type IN ('INCOME', 'EXPENSE'))`), `icon`, `color`, `is_default`, `created_at`, serta indeks `idx_categories_user_id` & `idx_categories_type` -> selaras dengan `Category.java` dan 10 kategori bawaan (`Gaji`, `Investasi`, `Bonus`, `Pemasukan Lainnya`, `Makanan & Minuman`, `Transportasi`, `Belanja`, `Tagihan & Utilitas`, `Hiburan`, `Kesehatan`).
- **`transactions`**: `id`, `user_id`, `category_id` (`ON DELETE RESTRICT`), `type`, `amount` (`NUMERIC(15,2) CHECK (amount > 0)`), `description` (`TEXT`), `transaction_date` (`DATE`), `receipt_url` (`TEXT`), `created_at`, `updated_at`, serta indeks -> selaras dengan `Transaction.java`.
- **`budgets`**: `id`, `user_id`, `category_id`, `month` (`1..12`), `year` (`>= 2020`), `amount_limit` (`NUMERIC(15,2)`), `uq_budgets_user_category_period` -> selaras dengan `Budget.java`.
- **`refresh_tokens`**: `id`, `user_id`, `token` (`VARCHAR(512) UNIQUE`), `expires_at`, `revoked`, `created_at`, indeks `idx_refresh_tokens_user_id` -> selaras dengan `RefreshToken.java`.

---

## 3. Hasil Audit Keamanan & Edge-Case

1. **Zero Hardcoded Secrets**:
   - `app_build/backend/src/main/resources/application.yml` mengeksternalisasi seluruh kredensial sensitif via env vars (`${SUPABASE_DB_URL}`, `${SUPABASE_DB_USER}`, `${SUPABASE_DB_PASSWORD}`, `${JWT_SECRET}`). Secret dummy hanya ada pada `src/test/resources/application-test.yml` untuk profile `test` H2.
   - `app_build/frontend/src/environments/environment.ts` dan `environment.development.ts` tidak mengandung secret atau kunci API.
2. **Autentikasi JWT & Perlindungan Endpoint**:
   - `SecurityConfig` stateless (`SessionCreationPolicy.STATELESS`), hanya membuka rute publik yang dispesifikasikan (`/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/actuator/health/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`).
   - `JwtService` memvalidasi panjang minimal secret 32 byte untuk HMAC-SHA256 (`HS256`) dan menggunakan `Jwts.parser().verifyWith(signingKey)`.
   - `RestAuthenticationEntryPoint` dan `RestAccessDeniedHandler` mengembalikan `ErrorResponse` JSON standar (`401` / `403`).
3. **Isolasi Data Antar Pengguna**:
   - `POST /api/auth/logout` mengambil `principal.getId()` dari token JWT yang terautentikasi dan hanya mencabut token milik `userId` tersebut (`revokeAllActiveByUserId(userId)`). Mengirimkan `refreshToken` milik user lain di body tidak akan mencabut sesi user lain.
   - `GET /api/categories` memfilter via JPQL terparameterisasi `WHERE c.user IS NULL OR c.user.id = :userId`, mengisolasi custom category antar akun.
4. **CORS & Error Hardening**:
   - CORS membaca `${CORS_ALLOWED_ORIGINS:http://localhost:4200}` dengan `allowCredentials(true)` dan metode/header eksplisit.
   - `GlobalExceptionHandler` menangani malformed JSON (`400`), validation errors (`400`), invalid query param (`400`), duplicate email (`409`), kredensial/token invalid (`401`), not found (`404`), dan error tak terduga (`500`) tanpa membocorkan stack trace ke klien.

---

## 4. Temuan Audit & Perbaikan yang Diterapkan

### 4.1 Backend (`app_build/backend/`)

1. **[HIGH] Crash 500 BCrypt saat Password Multi-Byte UTF-8 > 72 Byte & Truncation Bypass saat Login**
   - **Akar Masalah**: `RegisterRequest.password` hanya memvalidasi `@Size(min = 8, max = 72)` berdasarkan panjang `char` Java (UTF-16), padahal `BCryptPasswordEncoder.encode()` (`BCrypt.hashpw`) melempar `IllegalArgumentException("password cannot be more than 72 bytes")` untuk input UTF-8 > 72 byte (mis. 40 karakter `'é'` = 40 char / 80 byte), sehingga memicu `500 Internal Server Error`. Sebaliknya, `BCrypt.checkpw` saat login memotong input > 72 byte secara diam-diam.
   - **Perbaikan**:
     - Menambahkan validator Jakarta `@Utf8MaxBytes(max = 72)` (`validation/Utf8MaxBytes.java`, `validation/Utf8MaxBytesValidator.java`) pada `RegisterRequest.password` agar mengembalikan `400 Bad Request` (`validationErrors.password`).
     - Menambahkan guard di `AuthService.login()` (`request.password().getBytes(StandardCharsets.UTF_8).length > 72`) agar melempar `InvalidCredentialsException` (`401`).

2. **[MEDIUM] Bypass `minLength: 2` pada `RegisterRequest.fullName` dengan Spasi**
   - **Akar Masalah**: Input seperti `" A "` memiliki panjang 3 sehingga lolos `@NotBlank` dan `@Size(min = 2, max = 100)`, lalu di-`trim()` oleh `AuthService.register()` menjadi `"A"` (1 karakter).
   - **Perbaikan**: Menambahkan validator `@TrimmedSize(min = 2, max = 100)` (`validation/TrimmedSize.java`, `validation/TrimmedSizeValidator.java`) pada `RegisterRequest.fullName`.

3. **[MEDIUM] Handler HTTP Method/MediaType Tidak Didukung & Hardening `server.error`**
   - **Akar Masalah**: Request dengan HTTP method salah (`HttpRequestMethodNotSupportedException`, mis. `GET /api/auth/login`), `Content-Type` salah (`HttpMediaTypeNotSupportedException`), atau `AccessDeniedException` sebelumnya jatuh ke `Exception.class` (`500 Internal Server Error`).
   - **Perbaikan**:
     - Menambahkan handler `405 Method Not Allowed`, `415 Unsupported Media Type`, `406 Not Acceptable`, dan `403 Forbidden` di `GlobalExceptionHandler.java`, serta menyamakan pesan generik pada `AuthenticationException` eksternal agar tidak membocorkan detail internal.
     - Menambahkan `server.error.include-stacktrace: never`, `include-message: never`, `include-exception: false` di `application.yml`.

4. **[LOW] Null-Safety pada `JwtService.extractUserId(Claims)`**
   - **Akar Masalah**: `UUID.fromString(claims.getSubject())` akan melempar `NullPointerException` jika token JWT tidak memiliki claim `sub`, yang tidak tertangkap oleh blok `catch (JwtException | IllegalArgumentException | ...)` di `JwtAuthenticationFilter`.
   - **Perbaikan**: Memvalidasi `subject != null && !subject.isBlank()` di `JwtService.extractUserId()` dan melempar `IllegalArgumentException`.

### 4.2 Frontend (`app_build/frontend/`)

5. **[HIGH] Bug Scope `catchError` setelah `switchMap` di `auth.interceptor.ts`**
   - **Akar Masalah**: Pada `auth.interceptor.ts`, blok `catchError((refreshError) => ...)` diletakkan *setelah* `switchMap(() => next(retriedReq))`. Akibatnya, jika token berhasil di-refresh lalu request ulang (`retriedReq`) gagal dengan status `400`, `404`, atau `500`, `catchError` ikut menangkap error tersebut sebagai kegagalan refresh—menghapus sesi valid yang baru diperbarui (`clearSession()`), menampilkan toast `"Sesi Kedaluwarsa"`, dan me-redirect user ke `/login`.
   - **Perbaikan**: Memindahkan `catchError` ke pipe `authService.refreshTokenShared()` *sebelum* `switchMap`, serta memperketat matcher URL API (`isApiRequest`).

6. **[MEDIUM] Celah Logout dengan Access Token Kedaluwarsa & Duplikasi Toast Concurrent 401**
   - **Akar Masalah**:
     - Saat beberapa request berjalan bersamaan (`AppComponent` `/auth/me` + `DashboardShellComponent` `/categories`) mendapat `401` dan refresh gagal, toast `"Sesi Kedaluwarsa"` dan `router.navigate(['/login'])` dipanggil berulang kali untuk setiap request, ditambah toast `'Gagal Memuat Kategori'` dari `DashboardShellComponent`.
     - `isLogoutRequest` selalu melewati refresh token pada `401`, sehingga jika user menekan tombol Keluar setelah access token kedaluwarsa (>15 menit) sementara refresh token masih aktif, refresh token di database tidak pernah dicabut.
   - **Perbaikan**:
     - Mendeduplikasi penanganan error refresh gagal menggunakan `WeakSet` pada observable `refresh$` di `auth.interceptor.ts`, dan menekan toast duplikat `401` di `dashboard-shell.component.ts`.
     - Mengizinkan `authInterceptor` mencoba refresh token saat `/auth/logout` mendapat `401` jika refresh token tersedia (agar backend mencabut seluruh sesi), sambil menekan toast `"Sesi Kedaluwarsa"` jika refresh gagal selama proses logout.
     - Memastikan `AuthService.readTokenStorage()` mengabaikan nilai token kosong/whitespace di `localStorage`.

7. **[LOW] Validasi Form Whitespace/UTF-8 Bytes, Open-Redirect `returnUrl`, dan `environment.ts`**
   - **Perbaikan**:
     - Mengekstrak validator reusable (`notBlankValidator`, `trimmedLengthValidator`, `maxUtf8BytesValidator`) di `src/app/core/utils/form-validators.util.ts` dan menerapkannya di `RegisterComponent` & `LoginComponent`.
     - Menambahkan guard `!returnUrl.startsWith('/\\')` pada sanitasi `returnUrl` di `LoginComponent`.
     - Menyesuaikan `production: true` pada `src/environments/environment.ts` (tetap mempertahankan `apiUrl: 'http://localhost:8080/api'`).

---

## 5. Ringkasan Penambahan & Verifikasi Test Otomatis

### 5.1 Pengujian Baru yang Ditambahkan
- **Backend**:
  - `AuthControllerIntegrationTest`: batas panjang maksimal `RegisterRequest`, password UTF-8 > 72 byte (`400`), `fullName` dengan spasi (`" A "`), penolakan truncation bypass > 72 byte saat login (`401`), verifikasi format timestamp ISO-8601 dan token tanpa claim `sub` (`401`), serta respons standar `405`/`415` tanpa stack trace.
  - `JwtServiceTest`: validasi penolakan claim `sub` kosong/hilang.
  - `AuthServiceTest`: pengujian token kedaluwarsa (`revoked = true`) dan isolasi `logout` ketika menerima refresh token milik user lain.
- **Frontend**:
  - `auth.interceptor.spec.ts`: regression test error `400` setelah refresh sukses tidak menghapus sesi, deduplikasi toast & redirect pada concurrent `401` gagal, refresh & retry pada `/auth/logout` `401`, serta graceful logout tanpa toast kedaluwarsa saat refresh gagal.
  - `register.component.spec.ts`: validasi whitespace-only, `trimmedLength` (`" A "`), `maxLength`, `maxUtf8Bytes` (> 72 byte), dan error handling `409 Conflict`.
  - `login.component.spec.ts`: validasi password whitespace-only dan sanitasi `returnUrl` (`//evil.example/phish` fallback ke `/dashboard` vs `/transactions` valid).
  - `dashboard-shell.component.spec.ts`: memastikan error `401` tidak memicu toast duplikat `'Gagal Memuat Kategori'`.

### 5.2 Hasil Eksekusi Terakhir (Pasca Perbaikan Final)

| Komponen | Perintah Eksekusi | Hasil Aktual |
|---|---|---|
| Backend Test Suite | `cd app_build/backend && mvn test` | **32 tests run, 0 failures, 0 errors, 0 skipped** (`BUILD SUCCESS`) |
| Frontend Unit & Component Tests | `cd app_build/frontend && CHROME_BIN="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" npx ng test --watch=false --browsers=ChromeHeadless` | **35 of 35 SUCCESS** (`TOTAL: 35 SUCCESS`) |
| Frontend Production Build | `cd app_build/frontend && npm run build` | **Application bundle generation complete** (`Initial total: 317.48 kB`, 0 errors) |

---

## 6. Kesimpulan Kesiapan Sprint 1

Implementasi Sprint 1 **FinPulse** di `app_build/backend/` dan `app_build/frontend/` dinyatakan **SIAP** melanjutkan ke **Tahap 4 (`@devops`)** untuk konfigurasi infrastruktur lokal (`docker-compose.yml`, `.env.example`), verifikasi migrasi Flyway terhadap PostgreSQL/Supabase, dan uji jalan end-to-end.
