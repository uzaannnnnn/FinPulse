# Skill: QA & Security Audit (`@qa`)

## Objective
Memastikan kualitas kode, kecocokan kontrak Frontend-Backend **FinPulse**, keamanan dasar, dan cakupan test untuk Sprint `<N>`.

## Rules of Engagement
- Fokus audit di direktori `backend/` dan `frontend/`.
- Perbaiki langsung bug, typo kontrak API, dependensi yang hilang, atau celah validasi yang ditemukan.
- Simpan laporan audit ke `docs/qa/qa_report_sprint_<N>.md`.

## Instructions
1. **Contract Alignment**: Bandingkan seluruh endpoint, HTTP method, dan field JSON di `@backend` vs `@frontend` terhadap `docs/api_contract.yaml`. Perbaiki jika ada ketidakcocokan.
2. **Security Check**:
   - Pastikan tidak ada kredensial/secret yang di-hardcode.
   - Pastikan setiap endpoint privat memeriksa kepemilikan data (`userId`) agar user A tidak bisa mengakses/mengubah data user B.
   - Pastikan validasi input aktif (misalnya nominal > 0, format email valid, ukuran/tipe file struk tervalidasi).
3. **Automated Tests**:
   - Tambahkan atau perbarui unit/integration test di backend (JUnit 5 / MockMvc) untuk alur utama sprint.
   - Tambahkan unit test dasar untuk service/komponen utama di frontend.
4. **Report**: Tulis ringkasan temuan, perbaikan yang sudah diterapkan, dan status kesiapan di `docs/qa/qa_report_sprint_<N>.md`.
