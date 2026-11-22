# Panduan Deploy Produksi FinPulse (`https://finpulse.eshace.com`)

Dokumen ini menjelaskan langkah deploy produksi **FinPulse** (Frontend Angular 19+ ke **Vercel** dan Backend Spring Boot 3 ke **Render / Railway / Google Cloud Run**), konfigurasi domain kustom, daftar lengkap environment variables produksi, serta checklist verifikasi SEO pasca live.

> **Keamanan:** Jangan pernah menaruh kredensial asli (password database, `SUPABASE_SERVICE_KEY`, `JWT_SECRET`, URL Redis/RabbitMQ) di dalam kode atau repository Git. Selalu masukkan melalui menu **Environment Variables** pada platform hosting.

---

## Arsitektur Produksi Ringkas

| Komponen | Layanan Rekomendasi | URL Produksi |
| --- | --- | --- |
| Frontend (Angular 19 + Prerender) | Vercel | `https://finpulse.eshace.com` |
| Backend REST API (Spring Boot 3 / Docker) | Render / Railway / Google Cloud Run | `https://api.finpulse.eshace.com` |
| Database & Object Storage | Supabase (PostgreSQL + Storage) | `https://<project-ref>.supabase.co` |
| Cache (Opsional / Hemat) | Upstash Redis atau `simple` in-memory | `rediss://...` |
| Message Broker (Opsional / Hemat) | CloudAMQP atau listener nonaktif | `amqps://...` |

---

## 1. Deploy Frontend ke Vercel & Custom Domain `finpulse.eshace.com`

Frontend menggunakan Angular 19 application builder dengan static prerendering (menghasilkan output di `dist/finpulse-web/browser`) serta konfigurasi `frontend/vercel.json` (SPA rewrite, security headers, cache policy, dan `X-Robots-Tag: noindex, nofollow` pada rute privat).

### Langkah Deploy di Vercel
1. Masuk ke [Vercel Dashboard](https://vercel.com/dashboard) lalu klik **Add New... -> Project**.
2. Pilih repository Git **FinPulse** dan klik **Import**.
3. Pada bagian **Configure Project**, atur:
   - **Root Directory**: `frontend`
   - **Framework Preset**: `Other` (karena `vercel.json` mengatur `"framework": null` agar tidak menimpa `outputDirectory`)
   - **Build Command**: `npm run build`
   - **Output Directory**: `dist/finpulse-web/browser`
4. Klik **Deploy** dan tunggu hingga proses build selesai tanpa error.

### Konfigurasi Custom Domain `finpulse.eshace.com`
1. Buka proyek di Vercel -> tab **Settings** -> **Domains**.
2. Tambahkan domain `finpulse.eshace.com` lalu klik **Add**.
3. Di panel DNS domain `eshace.com` (Cloudflare / Namecheap / Niagahoster / Registrar lainnya), tambahkan record berikut:

| Type | Name / Host | Target / Value | Proxy / TTL |
| --- | --- | --- | --- |
| `CNAME` | `finpulse` | `cname.vercel-dns.com` | DNS Only (matikan proxy orange jika pakai Cloudflare) / Auto |

4. Tunggu propagasi DNS (umumnya 5–15 menit) hingga Vercel menerbitkan sertifikat SSL/TLS secara otomatis dan status domain menjadi **Valid Configuration**.

### Catatan URL Backend API (`environment.prod.ts`)
Saat `npm run build` dijalankan, Angular otomatis mengganti `src/environments/environment.ts` dengan `src/environments/environment.prod.ts`:

```ts
export const environment = {
  production: true,
  apiUrl: 'https://api.finpulse.eshace.com/api',
};
```

- Jika URL backend Anda berbeda (misalnya masih memakai domain bawaan Render `https://finpulse-api.onrender.com/api` atau Cloud Run `https://finpulse-api-xxxx.a.run.app/api`), ubah nilai `apiUrl` pada `frontend/src/environments/environment.prod.ts`, lalu commit dan push untuk memicu redeploy Vercel.
- Pengembangan lokal (`ng serve` / `ng test`) tidak terpengaruh dan tetap memakai `http://localhost:8080/api`.

---

## 2. Deploy Backend ke Render / Railway / Google Cloud Run + Subdomain `api.finpulse.eshace.com`

Backend sudah dilengkapi `backend/Dockerfile` (multi-stage Maven + Eclipse Temurin 21 JRE Alpine, user non-root `finpulse`, `SPRING_PROFILES_ACTIVE=prod`, optimasi RAM JVM, dan `HEALTHCHECK` ke `/actuator/health`).

### Opsi A: Render (Paling Sederhana)
1. Buka [Render Dashboard](https://dashboard.render.com) -> **New +** -> **Web Service**.
2. Hubungkan repository Git FinPulse, lalu atur:
   - **Root Directory**: `backend`
   - **Language / Runtime**: `Docker`
   - **Dockerfile Path**: `./Dockerfile` (relatif terhadap `Root Directory`)
   - **Health Check Path**: `/actuator/health`
3. Tambahkan seluruh Environment Variables pada [Bagian 3](#3-daftar-lengkap-environment-variables-produksi), termasuk `SPRING_PROFILES_ACTIVE=prod`.
4. Klik **Create Web Service**. Render otomatis menginjeksi variabel `PORT`—pastikan tambahkan `SERVER_PORT` jika perlu atau sesuaikan `SERVER_PORT=10000` (Render default port) agar healthcheck dan proxy Render cocok.

### Opsi B: Railway
1. Buka [Railway Dashboard](https://railway.app) -> **New Project** -> **Deploy from GitHub repo**.
2. Pada **Settings -> Source**, atur **Root Directory** ke `backend` (Railway otomatis mendeteksi `Dockerfile`).
3. Pada **Settings -> Deploy**, atur **Healthcheck Path** ke `/actuator/health`.
4. Masukkan Environment Variables dari [Bagian 3](#3-daftar-lengkap-environment-variables-produksi) (tambahkan `SERVER_PORT=8080` atau samakan dengan target port di Railway Networking).
5. Aktifkan **Public Networking -> Generate Domain** atau tambahkan custom domain `api.finpulse.eshace.com`.

### Opsi C: Google Cloud Run
1. Build dan push image Docker ke Artifact Registry (atau deploy langsung dari source):
   ```bash
   gcloud run deploy finpulse-api \
     --source ./backend \
     --region asia-southeast2 \
     --allow-unauthenticated \
     --port 8080 \
     --memory 512Mi \
     --set-env-vars "SPRING_PROFILES_ACTIVE=prod,CORS_ALLOWED_ORIGINS=https://finpulse.eshace.com"
   ```
2. Simpan secret sensitif (`SUPABASE_DB_PASSWORD`, `SUPABASE_SERVICE_KEY`, `JWT_SECRET`, dll.) di **Google Secret Manager** lalu mount sebagai environment variables di Cloud Run.
3. Atur startup/liveness probe HTTP GET ke `/actuator/health` pada port `8080`.

### Konfigurasi Custom Subdomain `api.finpulse.eshace.com` (Opsional)
Jika ingin memakai subdomain rapi `https://api.finpulse.eshace.com`:
1. Tambahkan `api.finpulse.eshace.com` pada menu **Custom Domains** di Render / Railway / Cloud Run Domain Mapping.
2. Tambahkan record DNS `CNAME` pada panel DNS `eshace.com` sesuai target yang diberikan platform:

| Type | Name / Host | Target / Value (Contoh) | Catatan |
| --- | --- | --- | --- |
| `CNAME` | `api.finpulse` | `<nama-service>.onrender.com` / `<target>.up.railway.app` / `ghs.googlehosted.com` | Gunakan target persis dari dashboard hosting |

3. Verifikasi endpoint health setelah SSL aktif:
   ```bash
   curl -i https://api.finpulse.eshace.com/actuator/health
   ```
   Respons yang diharapkan: HTTP `200 OK` dengan body `{"status":"UP"}`.

---

## 3. Daftar Lengkap Environment Variables Produksi

Konfigurasi berikut merujuk langsung ke `application.yml` dan `application-prod.yml`.

### A. Server, Profil & JVM

| Variabel | Wajib | Contoh / Nilai Produksi | Keterangan |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Ya | `prod` | Mengaktifkan `application-prod.yml` (sudah default di `Dockerfile`). |
| `SERVER_PORT` | Opsional | `8080` | Port HTTP aplikasi (sesuaikan jika platform seperti Render memakai port lain, mis. `10000`). |
| `JAVA_TOOL_OPTIONS` | Opsional | `-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC` | Membatasi heap JVM di container 512MB–1GB (sudah default di `Dockerfile`). |
| `LOG_LEVEL_ROOT` | Opsional | `INFO` | Level logging global. |
| `LOG_LEVEL_FINPULSE` | Opsional | `INFO` | Level logging package `com.finpulse`. |

### B. Supabase PostgreSQL & Storage

> **Prasyarat Supabase Storage:** Sebelum upload bukti struk & ekspor laporan digunakan di produksi, buat dua **Public bucket** di **Supabase Dashboard -> Storage -> New bucket**:
> 1. Bucket **`receipts`** (Public bucket) — untuk foto struk transaksi (JPEG/PNG/WebP, maks 5 MB).
> 2. Bucket **`reports`** (Public bucket) — untuk hasil ekspor laporan bulanan PDF/CSV.

| Variabel | Wajib | Contoh / Nilai Produksi | Keterangan |
| --- | --- | --- | --- |
| `SUPABASE_DB_URL` | Ya | `jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require` | JDBC URL PostgreSQL Supabase (disarankan Session Pooler IPv4 atau Direct Connection dengan `?sslmode=require`). |
| `SUPABASE_DB_USER` | Ya | `postgres.<project-ref>` (pooler) atau `postgres` | Username database PostgreSQL Supabase. |
| `SUPABASE_DB_PASSWORD` | Ya | `<isi-password-db-supabase>` | Password database PostgreSQL Supabase. |
| `SUPABASE_URL` | Ya | `https://<project-ref>.supabase.co` | Project URL Supabase (Settings -> API). |
| `SUPABASE_SERVICE_KEY` | Ya | `<isi-service-role-key-supabase>` | Service Role Key untuk upload ke bucket `receipts` & `reports` dari backend. Jangan pernah ekspos ke frontend. |
| `SUPABASE_STORAGE_BUCKET` | Opsional | `receipts` | Nama bucket publik untuk foto struk (default: `receipts`). |
| `SUPABASE_REPORTS_BUCKET` | Opsional | `reports` | Nama bucket publik untuk file ekspor laporan (default: `reports`). |

### C. Keamanan JWT, CORS & Dokumentasi API (Swagger)

| Variabel | Wajib | Contoh / Nilai Produksi | Keterangan |
| --- | --- | --- | --- |
| `JWT_SECRET` | Ya | `<random-base64-minimal-32-byte>` | Kunci rahasia HMAC-SHA256 JWT. Generate via `openssl rand -base64 32`. |
| `JWT_ACCESS_EXPIRATION_MS` | Opsional | `900000` | Masa berlaku Access Token dalam ms (default: 15 menit). |
| `JWT_REFRESH_EXPIRATION_MS` | Opsional | `604800000` | Masa berlaku Refresh Token dalam ms (default: 7 hari). |
| `CORS_ALLOWED_ORIGINS` | Ya | `https://finpulse.eshace.com` | Origin frontend yang diizinkan (pisahkan koma jika lebih dari satu). |
| `SPRINGDOC_API_DOCS_ENABLED` | Opsional | `false` | Menonaktifkan `/v3/api-docs` di produksi (default `false` pada profil `prod`). |
| `SPRINGDOC_SWAGGER_UI_ENABLED` | Opsional | `false` | Menonaktifkan `/swagger-ui.html` di produksi (default `false` pada profil `prod`). |

### D. Caching Redis (Upstash Redis atau Mode Hemat)

Pilih salah satu mode berikut:

- **Mode Penuh (Upstash Redis):**
  - `SPRING_CACHE_TYPE=redis`
  - `REDIS_URL=rediss://default:<upstash-password>@<endpoint>.upstash.io:6379`
  - `REDIS_SSL_ENABLED=true`
  - `FINPULSE_CACHE_TTL_SECONDS=300`
  - `HEALTH_REDIS_ENABLED=false` (ubah ke `true` hanya jika ingin status Redis ikut memengaruhi `/actuator/health`)
- **Mode Hemat (Tanpa External Redis):**
  - `SPRING_CACHE_TYPE=simple`
  - `HEALTH_REDIS_ENABLED=false`

| Variabel | Wajib | Contoh / Nilai Produksi | Keterangan |
| --- | --- | --- | --- |
| `SPRING_CACHE_TYPE` | Opsional | `redis` atau `simple` | Gunakan `simple` untuk mode hemat in-memory tanpa server Redis. |
| `REDIS_URL` | Jika `redis` | `rediss://default:<password>@<host>.upstash.io:6379` | URL koneksi penuh Upstash Redis (TLS `rediss://`). |
| `REDIS_SSL_ENABLED` | Jika `redis` | `true` | Aktifkan TLS saat menghubungkan ke Upstash Redis. |
| `FINPULSE_CACHE_TTL_SECONDS` | Opsional | `300` | TTL cache ringkasan dashboard dalam detik (default: 300). |
| `HEALTH_REDIS_ENABLED` | Opsional | `false` | Default `false` di `prod` agar gangguan Redis tidak mematikan healthcheck. |

### E. Message Broker RabbitMQ (CloudAMQP atau Mode Hemat)

Pilih salah satu mode berikut:

- **Mode Penuh (CloudAMQP — untuk Over-Budget Alert & Async Export Laporan):**
  - `RABBITMQ_URL=amqps://<user>:<password>@<host>.rmq.cloudamqp.com/<vhost>`
  - `RABBITMQ_SSL_ENABLED=true`
  - `RABBITMQ_LISTENER_AUTO_STARTUP=true`
  - `HEALTH_RABBIT_ENABLED=false` (ubah ke `true` jika ingin healthcheck memantau RabbitMQ)
- **Mode Hemat (Tanpa Broker Eksternal):**
  - `RABBITMQ_LISTENER_AUTO_STARTUP=false`
  - `HEALTH_RABBIT_ENABLED=false`

| Variabel | Wajib | Contoh / Nilai Produksi | Keterangan |
| --- | --- | --- | --- |
| `RABBITMQ_URL` | Jika aktif | `amqps://<user>:<pass>@<host>.rmq.cloudamqp.com/<vhost>` | URL AMQPS penuh dari CloudAMQP. |
| `RABBITMQ_SSL_ENABLED` | Jika aktif | `true` | Aktifkan TLS untuk koneksi `amqps://`. |
| `RABBITMQ_LISTENER_AUTO_STARTUP` | Opsional | `true` atau `false` | Set `false` pada mode hemat tanpa RabbitMQ. |
| `HEALTH_RABBIT_ENABLED` | Opsional | `false` | Default `false` di `prod` agar healthcheck tetap stabil. |

---

## 4. Checklist Verifikasi SEO & Produksi Pasca Live

Lakukan pemeriksaan berikut segera setelah `https://finpulse.eshace.com` aktif:

- [ ] **Google Search Console (GSC):**
  1. Tambahkan properti URL-prefix `https://finpulse.eshace.com` (atau Domain property `eshace.com`) di [Google Search Console](https://search.google.com/search-console).
  2. Selesaikan verifikasi kepemilikan (TXT DNS record atau HTML meta tag).
  3. Buka menu **Sitemaps**, submit `https://finpulse.eshace.com/sitemap.xml`, lalu pastikan status **Success**.
- [ ] **Cek Aset SEO & Static Files:**
  - Buka `https://finpulse.eshace.com/robots.txt` -> pastikan menampilkan aturan `Allow: /`, `Disallow` untuk rute privat, serta `Sitemap: https://finpulse.eshace.com/sitemap.xml`.
  - Buka `https://finpulse.eshace.com/sitemap.xml` -> pastikan XML valid dan merujuk ke `https://finpulse.eshace.com/`.
  - Buka `https://finpulse.eshace.com/manifest.webmanifest` -> pastikan JSON valid.
  - Verifikasi header `X-Robots-Tag: noindex, nofollow` muncul pada `/login`, `/register`, `/dashboard`, `/transactions`, `/budgets`, `/reports`:
    ```bash
    curl -I https://finpulse.eshace.com/login | grep -i x-robots-tag
    ```
- [ ] **Google Rich Results Test (`SoftwareApplication` JSON-LD):**
  - Uji `https://finpulse.eshace.com` di [Google Rich Results Test](https://search.google.com/test/rich-results).
  - Pastikan structured data `SoftwareApplication` terdeteksi valid tanpa error.
- [ ] **Core Web Vitals (PageSpeed Insights):**
  - Uji `https://finpulse.eshace.com` di [PageSpeed Insights](https://pagespeed.web.dev/) untuk tab **Mobile** dan **Desktop**.
  - Pastikan skor **Performance**, **Accessibility**, **Best Practices**, dan **SEO** optimal (target ≥ 90) serta LCP/CLS/INP berstatus hijau.
- [ ] **Link Preview & Open Graph (`og-image.png`):**
  - Pastikan `https://finpulse.eshace.com/og-image.png` dapat diakses langsung dengan HTTP `200 OK`.
  - Kirim tautan `https://finpulse.eshace.com` di chat **WhatsApp** untuk memeriksa tampilan thumbnail, judul, dan deskripsi.
  - Uji di **X / Twitter Card Validator** (atau buat draft post X) untuk memastikan `summary_large_image` tampil rapi.
  - Uji di [LinkedIn Post Inspector](https://www.linkedin.com/post-inspector/) untuk memastikan metadata Open Graph dan `og-image.png` terbaca sempurna.
