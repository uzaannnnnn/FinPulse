# Skill: DevOps Setup & Local Verification (`@devops`)

## Objective
Menyiapkan environment lokal & produksi **FinPulse** (`docker-compose.yml`, `.env.example`, `backend/Dockerfile`, `frontend/vercel.json`, `DEPLOY.md`), memverifikasi *build* dan *test* berjalan sukses, serta menyalakan aplikasi lokal.

## Rules of Engagement
- Jangan pernah commit atau menulis nilai kredensial asli; selalu sediakan `.env.example` di root dengan penjelasan tiap variabel.
- Gunakan terminal untuk memverifikasi *build* dan *test* secara nyata, bukan sekadar menebak.

## Instructions
1. **Infra Config**:
   - Siapkan `.env.example` di root berisi placeholder untuk `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`, `SUPABASE_URL`, `SUPABASE_SERVICE_KEY`, `JWT_SECRET`, `REDIS_HOST`, `RABBITMQ_HOST`.
   - Siapkan `docker-compose.yml` di root untuk menjalankan **Redis** (port 6379) dan **RabbitMQ** dengan Management UI (port 5672 & 15672) beserta *healthcheck*.
2. **Verify Backend**:
   - Masuk ke `backend/` dan jalankan `mvn test` (atau `./mvnw test`). Jika gagal, catat error dan perbaiki konfigurasi *build* atau laporkan ke `@qa`/`@backend`.
3. **Verify Frontend**:
   - Masuk ke `frontend/`, jalankan `npm install` dan `npm run build`. Pastikan kompilasi TypeScript & Tailwind bersih tanpa error.
4. **Run Locally & Report**:
   - Jika Docker aktif dan file `.env` sudah diisi pengguna, jalankan `docker compose up -d`, backend Spring Boot, dan `ng serve`.
   - Laporkan status build serta tautan lokal FinPulse:
     - Frontend: `http://localhost:4200`
     - Backend API: `http://localhost:8080`
     - RabbitMQ Management UI (Sprint 3): `http://localhost:15672`
