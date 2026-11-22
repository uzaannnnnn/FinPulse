---
description: Jalankan pipeline multi-agen otonom FinPulse untuk Sprint 1, 2, atau 3
---

Saat pengguna mengetik `/run_sprint <nomor_sprint>` (contoh: `/run_sprint 1`), orkestrasi tim agen **FinPulse** berdasarkan `.agents/agents.md` dan `.agents/skills/`.

### Urutan Eksekusi:

1. **Tahap Perencanaan (`@architect`)**
   - Jalankan skill `.agents/skills/architect_spec.md` untuk Sprint `<nomor_sprint>`.
   - **STOP di Approval Gate**: Tunggu pengguna meninjau `docs/specs/spec_sprint_<nomor_sprint>.md` dan `docs/api_contract.yaml`. Jika pengguna memberi catatan revisi, ulangi tahap ini sampai pengguna mengetik **"Approved"** atau **"Lanjut"**.

2. **Tahap Implementasi Paralel (`@backend` & `@frontend`)**
   - Setelah spesifikasi disetujui, jalankan dua sub-agen berikut secara paralel (atau berurutan jika mode sesi tunggal):
     - Sub-agen **`@backend`** menjalankan `.agents/skills/backend_spring.md` untuk Sprint `<nomor_sprint>`.
     - Sub-agen **`@frontend`** menjalankan `.agents/skills/frontend_angular.md` untuk Sprint `<nomor_sprint>`.

3. **Tahap Audit & Testing (`@qa`)**
   - Setelah `@backend` dan `@frontend` selesai, jalankan **`@qa`** menggunakan `.agents/skills/qa_audit.md` untuk memverifikasi kesesuaian kontrak, keamanan, dan pengujian otomatis.

4. **Tahap Code Review & Refactoring (`@reviewer`)**
   - Setelah `@qa` selesai, jalankan **`@reviewer`** menggunakan `.agents/skills/code_review.md` untuk mengaudit efisiensi performa, kebersihan komentar, DRY, dan penghapusan *dead code* di `backend/` dan `frontend/` tanpa mengubah kontrak API, migrasi Flyway, atau logika bisnis, lalu simpan laporannya ke `docs/qa/code_review_report.md`.

5. **Tahap Build & Verifikasi Lokal (`@devops`)**
   - Jalankan **`@devops`** menggunakan `.agents/skills/devops_run.md` untuk memverifikasi `mvn test`, `npm run build`, Docker Compose (Sprint 3), dan melaporkan status akhir Sprint `<nomor_sprint>` ke pengguna.
