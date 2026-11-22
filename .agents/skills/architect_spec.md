# Skill: Architect Spec & API Contract (`@architect`)

## Objective
Menyusun dokumen spesifikasi teknis per sprint untuk **FinPulse** dan mengunci kontrak API sebelum `@backend` dan `@frontend` mulai menulis kode.

## Scope per Sprint
- **Sprint 1 (Fondasi & Auth)**: Struktur monorepo `backend/` (`com.finpulse`) & `frontend/`, skema awal Supabase via Flyway (`users`, `categories`, `transactions`, `budgets`), autentikasi JWT (register, login, refresh, logout), dan layout dasar Angular **FinPulse** (Login, Register, Dashboard shell).
- **Sprint 2 (Inti Finance & Analitik)**: CRUD transaksi pemasukan/pengeluaran, filter tanggal/kategori, upload foto struk ke Supabase Storage, CRUD batas anggaran bulanan (`budgets`), serta dashboard FinPulse berisi ringkasan saldo, grafik arus kas bulanan, dan *progress bar* anggaran.
- **Sprint 3 (Cache, Async & Ekspor)**: `docker-compose.yml` (Redis & RabbitMQ), caching ringkasan dashboard di Redis + *auto-invalidation*, RabbitMQ consumer untuk deteksi *over-budget alert*, serta *background job* ekspor laporan bulanan (PDF/CSV) yang disimpan ke Supabase Storage.

## Rules of Engagement
- **Output Files**:
  1. `docs/specs/spec_sprint_<N>.md`
  2. `docs/api_contract.yaml` (format OpenAPI 3.0 yang diperbarui inkremental tiap sprint)
- **Approval Gate (WAJIB)**: Setelah menyimpan file, BERHENTI dan tanyakan persetujuan pengguna. Jika pengguna memberi komentar atau revisi di chat maupun di dalam file Markdown, baca ulang, perbaiki, lalu minta persetujuan lagi.

## Instructions
1. Baca `docs/` untuk melihat spesifikasi sprint sebelumnya (jika Sprint > 1).
2. Tulis `docs/specs/spec_sprint_<N>.md` berisi:
   - Tujuan & Exit Criteria Sprint `<N>` FinPulse
   - Skema Database / Perubahan Migrasi Flyway (PostgreSQL / Supabase)
   - Daftar Komponen Frontend & Alur UI
   - Strategi Cache Redis / Queue RabbitMQ (khusus Sprint 3)
3. Buat atau perbarui `docs/api_contract.yaml` dengan endpoint, request DTO, response DTO, dan kode error yang lengkap.
4. **Halt Execution**: Tanyakan ke pengguna: *"Spesifikasi Sprint `<N>` FinPulse dan `api_contract.yaml` sudah siap di `docs/`. Apakah kamu setuju untuk lanjut ke tahap coding, atau ada bagian yang ingin direvisi dulu?"*
