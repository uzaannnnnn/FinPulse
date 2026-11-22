# Skill: Code Review & Safe Refactoring (`@reviewer`)

## Objective
Mengaudit dan merapikan kualitas kode **FinPulse** pada `backend/` (Spring Boot 3 / Java 21) dan `frontend/` (Angular 19+ / Signals) agar efisien, bersih dari *dead code* dan komentar dangkal, serta bebas regresi.

## Rules of Engagement
- **DILARANG** mengubah kontrak API (`docs/api_contract.yaml`), skema migrasi Flyway (`backend/src/main/resources/db/migration/`), atau logika bisnis aplikasi.
- Hanya lakukan **refactoring aman** yang mempertahankan perilaku eksternal 100% identik.
- Wajib memverifikasi seluruh suite test (`mvn test` di `backend/`, `npm run build` dan `ng test --watch=false --browsers=ChromeHeadless` di `frontend/`) lulus penuh setelah refactoring.
- Simpan ringkasan temuan dan perubahan di `docs/qa/code_review_report.md`.

## Audit Checklist

### 1. Efisiensi & Performa Kode
- **Backend (Spring Boot)**:
  - Periksa potensi N+1 query JPA (tambahkan `@EntityGraph` / `JOIN FETCH` atau batch lookup jika perlu).
  - Hindari eksekusi query database di dalam loop (`for` / `stream().map(...)`).
  - Pastikan seluruh method service yang hanya membaca data menggunakan `@Transactional(readOnly = true)`.
  - Sederhanakan pipeline Java Stream atau alokasi koleksi yang berulang/boros.
- **Frontend (Angular)**:
  - Pastikan tidak ada pemanggilan method berat/komputasi berulang langsung di template HTML (migrasikan ke `computed()` Signal).
  - Pastikan tidak ada RxJS `subscribe()` tanpa cleanup (`takeUntilDestroyed` / `DestroyRef` / `ngOnDestroy`) yang berpotensi *memory leak*.
  - Optimalkan re-render dan pastikan `@for` menggunakan `track` yang tepat.

### 2. Kebersihan Komentar (*Strict Comment Hygiene*)
- Hapus seluruh komentar yang tidak relevan, komentar dangkal yang hanya mengulang kode (contoh: `// save to db`, `// return dto`, `// inject service`), dan seluruh kode mati yang di-comment (*commented-out code*).
- Pertahankan komentar **hanya** jika berfungsi sebagai:
  - Penanda bagian (*section divider*), atau
  - Penjelasan alasan teknis yang non-obvious (*why*, bukan *what*).

### 3. Clean Code, DRY & Dead Code
- Hapus import, variabel, parameter, method, file, atau CSS class yang tidak terpakai.
- Rapikan duplikasi logika yang jelas dan pecah fungsi yang terlalu panjang agar mudah dibaca.
- Pastikan tidak ada `console.log`, `console.debug`, atau `System.out.println` sisa debugging.

## Output
- Tulis laporan lengkap (temuan per kategori, daftar file yang direfaktor, dan hasil verifikasi test) di `docs/qa/code_review_report.md`.
