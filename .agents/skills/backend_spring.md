# Skill: Backend Development — Spring Boot 3 (`@backend`)

## Objective
Mengimplementasikan seluruh fitur backend **FinPulse** untuk Sprint `<N>` di `backend/` sesuai `docs/specs/spec_sprint_<N>.md` dan `docs/api_contract.yaml`.

## Rules of Engagement
- Gunakan **Java 21**, **Spring Boot 3.x** (Maven), dan root package `com.finpulse`.
- Pisahkan paket dengan rapi: `config`, `controller`, `service`, `repository`, `entity`, `dto`, `security`, `messaging`, `exception`.
- Setiap perubahan skema tabel wajib dibuat lewat file migrasi **Flyway** (`src/main/resources/db/migration/V<N>__<deskripsi>.sql`).
- Semua query data pengguna (`transactions`, `budgets`, `categories`) WAJIB difilter berdasarkan `userId` dari JWT yang terautentikasi.
- Gunakan global `@RestControllerAdvice` untuk format error JSON yang konsisten.

## Instructions
1. Baca `docs/specs/spec_sprint_<N>.md` dan `docs/api_contract.yaml`.
2. Jika Sprint 1, inisialisasi proyek Maven Spring Boot 3 (`artifactId: finpulse-api`) di `backend/` lengkap dengan `pom.xml`, `application.yml` (berbasis environment variable `${SUPABASE_DB_URL}`, `${JWT_SECRET}`, dll.), dan migrasi Flyway `V1__init_schema.sql`.
3. Implementasikan Entity, Repository, DTO (dengan Jakarta Validation), Service, dan Controller sesuai sprint aktif:
   - **Sprint 1**: Auth JWT (Access + Refresh Token), Spring Security filter chain, CORS untuk Angular (`http://localhost:4200`), dan seed kategori bawaan.
   - **Sprint 2**: CRUD Transaksi + pagination & filter, CRUD Budgets + kalkulasi pemakaian bulanan, integrasi REST Supabase Storage untuk upload struk, dan endpoint ringkasan analitik dashboard.
   - **Sprint 3**: Integrasi Spring Data Redis (`@Cacheable` / `@CacheEvict` pada ringkasan bulanan), konfigurasi Spring AMQP (RabbitMQ) untuk event `finpulse.transaction.created` -> cek *over-budget*, dan antrean `finpulse.report.export` untuk generate PDF/CSV secara asinkron.
4. Pastikan semua file lengkap tanpa placeholder `// TODO` pada logika inti.
