# PRD — Chat Multiple Image (Frontend / Dashboard)

| Field | Value |
|---|---|
| Feature | Kirim multiple gambar dari Chat Room — implementasi UI di Saktiform Dashboard |
| Modul | **Chat** (`src/modules/chat/`, halaman `/chat`) |
| Dokumen terkait | [PRD Backend](./chat-multiple-image.md), [TDD Backend](../tdd/chat-multiple-image.md), [TDD Frontend](../tdd/chat-multiple-image-frontend.md), [FE API Mapping](../frontend/api-mapping.md) `POST /send-message`, [FE UI Flow](../frontend/ui-flow.md) |
| Stack | Vue 3 `<script setup>` + TypeScript, Vite, Pinia, Axios, STOMP/SockJS, SCSS utility classes |
| Status | **Approved** — semua keputusan §11 resolved (2026-10-15). TDD: [TDD Frontend](../tdd/chat-multiple-image-frontend.md) |
| Scope | Frontend-only (backend sudah selesai & ter-unit-test) |
| Last updated | 2026-10-15 |
| Target pembaca | Frontend Developer, QA, Product |

> PRD ini mendefinisikan kebutuhan UI/UX agar agent dapat memilih dan mengirim **hingga 10 gambar sekaligus** dari composer Chat Room. Dianalisis dari source dashboard di `FE/dashboard` (branch `dev`, commit `e7825d2`, 2026-02-22). **Snapshot lokal ini lebih tua dari dokumentasi FE** (mis. `labelStore` / `LabelChips` yang didokumentasikan belum ada) — developer wajib mencocokkan ulang dengan branch terbaru sebelum implementasi (lihat TDD §1.2).

---

## 1. Latar Belakang

### 1.1 Perilaku saat ini (dari source)

| Area | File | Perilaku |
|---|---|---|
| Pilih file | `ChatDetail.vue` | `<input type="file">` **tanpa `multiple`**; `accept="image/*,application/pdf,.doc,.docx,.xls,.xlsx"`. State tunggal `selectedFile` + `previewUrl`. Memilih file baru **menimpa** file sebelumnya. |
| Preview | `ChatDetail.vue` | Satu thumbnail 50×50 (gambar) atau ikon paperclip (dokumen) + nama & ukuran + tombol ×. |
| Kirim | `ChatDetail.handleSendClick` | `emit('send-message', text, file)` lalu **langsung** mengosongkan teks & file (sebelum tahu hasil kirim). Tidak ada guard double-submit / loading. |
| Upload & kirim | `pages/chat/index.vue` `handleSendMessage` | Upload 1 file → tentukan `messageType` dari MIME → `sendMessage({ mediaLink: url, message: '' })`. **Teks dikirim sebagai pesan TEXT terpisah** setelahnya, walaupun placeholder bertuliskan "Tambahkan keterangan (opsional)…". |
| Error | `pages/chat/index.vue` | Gagal upload → `alert()` native browser. Gagal `sendMessage` → **diabaikan** (return value tidak dicek). |
| Validasi | — | Tidak ada validasi ukuran file di client. Backend: `max-file-size=10MB` per file. |
| Render | `ChatDetail.vue` | Pesan `IMAGE` → `<img>` maks 250×250 + `msg.text` di bawahnya. Pesan masuk via WebSocket `NEW_MESSAGE` (de-dupe by `id`). |

### 1.2 Kemampuan backend (sudah tersedia)

- `POST /send-message` menerima `mediaLink: string | string[]`.
- `IMAGE`: 1–10 URL, dikirim berurutan sebagai pesan WhatsApp terpisah; **caption (`message`) hanya di gambar pertama**.
- `VIDEO` / `DOCUMENT` / `AUDIO`: tepat 1 URL.
- Response `data: { total, sent, failed, chatIds }`. Gagal sebagian → HTTP 200 + `success:false` + `message` "x dari n gambar terkirim…". Validasi / gagal total → HTTP 400.
- Setiap gambar terkirim memicu satu event `NEW_MESSAGE` → muncul satu per satu di thread.

---

## 2. Tujuan

**Tujuan bisnis:** agent dapat mengirim katalog/foto produk beberapa sekaligus tanpa mengulang upload satu per satu.

**Tujuan teknis:**
- Composer mendukung **multi-select + penambahan bertahap** gambar (maks 10) dengan preview per gambar dan hapus per gambar.
- Satu aksi kirim = upload semua gambar → **satu** request `POST /send-message` dengan array `mediaLink`.
- Umpan balik jelas: loading saat kirim, toast error/sukses sebagian lewat `alertStore` (menggantikan `alert()` native).
- Gambar yang gagal terkirim **tetap di composer** agar bisa dikirim ulang.
- Tidak ada perubahan perilaku untuk kirim teks biasa dan dokumen tunggal (kecuali caption — §11 Q1).

### Non-Goals
- Tampilan "album"/grid untuk beberapa gambar di thread (tetap 1 bubble per gambar).
- Drag & drop, paste dari clipboard, crop/kompres gambar di client.
- Multi-file untuk dokumen/video/audio.
- Reply-to dengan media (belum ada UI reply di composer saat ini).
- Perubahan backend.

---

## 3. Scope

### 3.1 Included
- `ChatDetail.vue`: state lampiran menjadi list, `<input multiple>`, preview strip, counter `n/10`, validasi client, loading/disabled saat kirim, mempertahankan lampiran yang gagal.
- `pages/chat/index.vue`: `handleSendMessage` menerima `File[]`, upload paralel terbatas, satu request kirim, interpretasi response, toast.
- `chatStore.ts`: tipe payload `mediaLink: string | string[] | null`, tipe response, helper upload banyak file.
- `types.ts`: tipe baru untuk payload & hasil kirim.
- Update `docs/frontend/*` terkait (component/UI flow).

### 3.2 Not Included
- Komponen `common/` baru (pakai markup inline di `ChatDetail`, mengikuti pola preview existing). `MultipleFileUpload` **tidak** dipakai karena didesain untuk form (props `width/height/size` wajib, state internal sendiri) dan tidak cocok untuk composer chat.
- Unit test otomatis (dashboard belum punya test runner — lihat TDD §10).

---

## 4. User Stories

| ID | Sebagai | Saya ingin | Agar |
|---|---|---|---|
| US-1 | Agent | memilih beberapa gambar sekaligus dari dialog file | tidak perlu memilih satu per satu |
| US-2 | Agent | menambah gambar lagi setelah memilih | bisa menyusun kiriman dari beberapa folder |
| US-3 | Agent | melihat preview semua gambar & menghapus salah satunya | yakin apa yang akan dikirim |
| US-4 | Agent | menulis keterangan yang menempel di gambar pertama | customer melihat konteks gambar |
| US-5 | Agent | tahu bila sebagian gambar gagal & mengirim ulang sisanya | tidak ada gambar yang terlewat |
| US-6 | Agent | tidak bisa menekan kirim dua kali saat proses berjalan | tidak ada kiriman ganda |

---

## 5. Functional Requirements

### 5.1 Pemilihan lampiran

| ID | Requirement |
|---|---|
| FR-1 | Input file memiliki atribut `multiple`; `accept` tetap seperti sekarang. |
| FR-2 | Lampiran disimpan sebagai **list**. Memilih file lagi **menambahkan** ke list (bukan menimpa), dengan urutan sesuai urutan pilih. |
| FR-3 | Maksimal **10 gambar** per kiriman. File yang melebihi kuota tidak ditambahkan; tampil toast `"Maksimal 10 gambar per pengiriman"`. |
| FR-4 | Dokumen (non-image) hanya boleh **1** dan **tidak boleh dicampur** dengan gambar. Memilih dokumen saat sudah ada lampiran (atau memilih gambar saat sudah ada dokumen) → file ditolak dengan toast `"Dokumen tidak dapat digabung dengan lampiran lain"`. |
| FR-5 | Setiap file maksimal **10 MB** (selaras batas backend). File lebih besar ditolak dengan toast `"<nama file> melebihi 10 MB"`; file lain dalam seleksi yang sama tetap diproses. |
| FR-6 | File dengan tipe di luar `accept` (mis. hasil "All files") ditolak dengan toast `"Tipe file tidak didukung"`. |
| FR-7 | Input file di-reset setelah setiap seleksi (agar file yang sama bisa dipilih ulang) — perilaku existing dipertahankan. |

### 5.2 Preview

| ID | Requirement |
|---|---|
| FR-8 | Lampiran ditampilkan sebagai strip horizontal (scroll-x) thumbnail 50×50; dokumen memakai ikon paperclip + nama & ukuran (seperti sekarang). |
| FR-9 | Setiap thumbnail punya tombol × untuk menghapus item tersebut; object URL preview di-`revoke` saat item dihapus, setelah kirim sukses, saat ganti conversation, dan saat komponen unmount. |
| FR-10 | Untuk gambar ditampilkan counter `n/10`. |
| FR-11 | Placeholder input teks saat ada lampiran: `"Tambahkan keterangan (opsional)..."` (existing). |

### 5.3 Kirim

| ID | Requirement |
|---|---|
| FR-12 | Tombol kirim / Enter tidak melakukan apa-apa bila teks kosong **dan** tidak ada lampiran (existing). |
| FR-13 | Selama proses kirim (upload + request), composer dalam status **sending**: tombol kirim & paperclip disabled, Enter diabaikan, indikator teks `"Mengirim n gambar..."` ditampilkan. |
| FR-14 | Semua gambar di-upload ke `POST /master/upload-file` (paralel, maks 3 bersamaan). Urutan URL hasil upload **sama** dengan urutan di composer. |
| FR-15 | Bila **ada** upload yang gagal → **tidak ada** pesan yang dikirim; toast danger `"<k> file gagal diunggah. Silahkan coba lagi."`; semua lampiran & teks tetap di composer. |
| FR-16 | Bila semua upload sukses dan lampiran berupa gambar → **satu** request `POST /send-message` `{ messageType: 'IMAGE', mediaLink: [url...], message: <teks> }`. Teks menjadi caption gambar pertama — **tidak** dikirim lagi sebagai pesan TEXT terpisah (§11 Q1). |
| FR-17 | Dokumen tunggal → `{ messageType: <DOCUMENT/VIDEO/AUDIO dari MIME>, mediaLink: [url], message: <teks> }` (§11 Q1). |
| FR-18 | Tanpa lampiran → `{ messageType: 'TEXT', mediaLink: null, message: <teks> }` (existing). |
| FR-19 | Response `success: true` → composer dikosongkan (teks & lampiran). Tidak ada toast sukses (pesan muncul via WebSocket — existing). |
| FR-20 | Response `success: false` **dengan** `data.sent > 0` (gagal sebagian) → toast danger berisi `message` backend; lampiran index `>= data.sent` **tetap** di composer, yang terkirim dihapus; teks dikosongkan (caption sudah terkirim di gambar pertama). |
| FR-21 | Response `success: false` tanpa `data` (validasi 400 / gagal total / jaringan) → toast danger berisi `message` backend (atau `"Jaringan Bermasalah"`); teks & lampiran tetap di composer. |
| FR-22 | `alert()` native pada gagal upload diganti toast `alertStore` (`danger`). |
| FR-23 | Ganti conversation saat status sending: request tetap berjalan; hasil tidak mengubah composer conversation baru. Lampiran yang belum dikirim di conversation lama dibuang saat ganti conversation (perilaku teks existing: dikosongkan). |

### 5.4 Tampilan di thread

| ID | Requirement |
|---|---|
| FR-24 | Tidak ada perubahan render: setiap gambar tampil sebagai bubble `IMAGE` sendiri sesuai urutan event `NEW_MESSAGE`; caption hanya di bubble pertama. |

---

## 6. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | Mengikuti konvensi dashboard: Pinia Options API, `postData`/`errorHelper`, `alertStore.setAlert`, utility class SCSS existing; tanpa dependency baru. |
| NFR-2 | `vue-tsc -b && vite build` lulus tanpa error tipe. |
| NFR-3 | Tidak ada memory leak object URL (FR-9). |
| NFR-4 | Responsif: strip preview bisa di-scroll horizontal di mobile (≤ 768px), tidak memperlebar layout. |
| NFR-5 | Upload paralel dibatasi (FR-14) agar tidak membebani backend/MinIO dan koneksi agent. |

---

## 7. UX Detail

```
┌──────────────────────────────────────────────────────────────┐
│ [img][img][img][img] →  (scroll-x)                     3/10   │  ← strip preview, × di tiap thumbnail
├────┬────┬────────────────────────────────────────────┬───────┤
│ 📎 │ 💬 │ Tambahkan keterangan (opsional)...          │  ➤    │
└────┴────┴────────────────────────────────────────────┴───────┘
Sending:  "Mengirim 3 gambar..." · 📎 & ➤ disabled
```

| State | Tampilan |
|---|---|
| Kosong | Seperti sekarang. |
| 1..10 gambar | Strip preview + counter. |
| 1 dokumen | Preview dokumen existing (ikon + nama + ukuran). |
| Sending | Indikator teks, kontrol disabled. |
| Gagal sebagian | Toast danger; strip berisi sisa gambar yang gagal; teks kosong. |
| Gagal total / upload gagal | Toast danger; composer tidak berubah. |

---

## 8. Acceptance Criteria

- **Given** composer kosong, **when** agent memilih 3 gambar sekaligus, **then** 3 thumbnail tampil berurutan dengan counter `3/10`.
- **Given** 3 gambar terpilih, **when** agent memilih 2 gambar lagi, **then** total 5 thumbnail (bukan diganti).
- **Given** 9 gambar terpilih, **when** agent memilih 3 gambar, **then** hanya 1 ditambahkan dan toast `Maksimal 10 gambar per pengiriman` muncul.
- **Given** gambar terpilih, **when** agent memilih PDF, **then** PDF ditolak dengan toast.
- **Given** file 12 MB, **when** dipilih, **then** ditolak dengan toast; file lain di seleksi yang sama tetap masuk.
- **Given** 3 gambar + teks "Pilihan warna", **when** kirim, **then** 3 upload, **1** request `/send-message` dengan `mediaLink` 3 URL berurutan & `message` "Pilihan warna"; **tidak** ada request TEXT terpisah; composer kosong; 3 bubble muncul via WebSocket, caption di bubble pertama.
- **Given** proses kirim berjalan, **when** agent menekan Enter / klik kirim lagi, **then** tidak ada request tambahan.
- **Given** upload gambar ke-2 gagal, **then** tidak ada `/send-message`, toast danger, composer tetap berisi 3 gambar & teks.
- **Given** backend membalas `success:false, data:{total:3, sent:1}`, **then** toast berisi pesan backend, composer berisi gambar ke-2 & ke-3, teks kosong.
- **Given** backend membalas 400, **then** toast berisi pesan backend; composer tidak berubah.
- **Given** hanya teks, **when** kirim, **then** perilaku sama seperti sekarang.
- **Given** 1 PDF + teks, **when** kirim, **then** 1 request `DOCUMENT` dengan `message` = teks (§11 Q1).

---

## 9. Dependensi & Asumsi

- Backend `POST /send-message` multiple image sudah deploy di environment yang sama (lihat [TDD Backend](../tdd/chat-multiple-image.md)). Backend tetap menerima `mediaLink` string, jadi FE lama aman selama transisi; FE baru **membutuhkan** backend baru.
- `POST /master/upload-file` tetap 1 file per request.
- Snapshot FE lokal sudah tua (§ header) — konflik dengan perubahan Label di header `ChatDetail` diperkirakan minim karena area yang diubah adalah composer (bawah), bukan header.

---

## 10. Risiko

| Risiko | Mitigasi |
|---|---|
| FE baru di-deploy sebelum backend → backend lama menolak array `mediaLink` (400). | Deploy backend lebih dulu (urutan rilis). |
| Upload 10 × 10 MB lambat di koneksi agent. | Indikator sending, paralel 3, batas 10 MB per file. |
| File yatim di MinIO saat upload sebagian gagal / kirim dibatalkan. | Diterima (perilaku existing juga meninggalkan file bila kirim gagal). |
| Perubahan caption (Q1) mengubah cara pesan tampil di WA customer. | Disetujui product owner; mudah di-revert (fallback di TDD §7, cukup di `handleSendMessage`). |

---

## 11. Resolved Decisions

Semua rekomendasi default di bawah disetujui oleh product owner (2026-10-15).

| # | Pertanyaan | Keputusan |
|---|---|---|
| Q1 | Teks saat ada lampiran: jadi **caption** (baru) atau tetap pesan **TEXT terpisah** (perilaku sekarang)? | **Caption** — sesuai placeholder "keterangan", hemat 1 pesan, dan didukung backend (caption di gambar pertama). Berlaku untuk gambar & dokumen. |
| Q2 | Batas jumlah gambar di FE? | **10**, sama dengan default backend (konstanta FE, tidak diambil dari API). |
| Q3 | Batas ukuran per file? | **10 MB** (batas backend). |
| Q4 | Upload gagal sebagian: kirim yang berhasil saja, atau batalkan semua? | **Batalkan semua** (FR-15) — urutan & caption tetap konsisten, agent cukup klik kirim lagi. |
| Q5 | Konkurensi upload? | **3** paralel. |
| Q6 | Tampilkan grid/album di thread? | **Tidak** (fase berikutnya). |
