ti# PRD — Chat Template Multiple Image (Frontend / Dashboard)

| Field | Value |
|---|---|
| Feature name | Chat Template Multiple Image — Frontend |
| Repo | `FE/dashboard` (Vue 3 + TypeScript + Pinia) |
| Component | `ModalFormTemplateChat.vue`, `templateChatStore.ts`, `ListWorkspaceTemplateChat.vue`, `MultipleFileUpload.vue` (common, dipakai tanpa diubah), `ChatDetail.vue` (quick chat), `chatStore.ts`, `pages/chat/index.vue` |
| Status | **Approved** — keputusan §11 resolved (2026-10-15): rekomendasi default, kecuali Q3 — **tanpa mekanisme mengurutkan gambar** |
| Last updated | 2026-10-15 |
| Dokumen terkait | [PRD Backend](chat-template-multiple-image.md) · [TDD Backend](../tdd/chat-template-multiple-image.md) · [TDD Frontend](../tdd/chat-template-multiple-image-frontend.md) · [PRD FE Chat Multiple Image](chat-multiple-image-frontend.md) · [TDD FE Chat Multiple Image](../tdd/chat-multiple-image-frontend.md) |

> Backend sudah **Implemented**: `POST /template` menerima `mediaLinks: string[]` (maks 10), `GET /template`, `GET /template/{id}`, dan `POST /conversation/quick-chat` mengembalikan `mediaLinks`; `POST /send-message` menerima URL gambar template (bucket produk) dan menyalinnya ke bucket chat. PRD ini mengatur sisi dashboard.

---

## 1. Background

### 1.1 Kondisi FE saat ini

Menurut dokumentasi FE (`docs/frontend/ui-flow.md`, `business-modules.md`):

- **Form template** (`ModalFormTemplateChat`): satu `FileUpload` opsional. File di-upload **saat dipilih** ke `POST /master/saktiform-media`, URL disimpan sebagai `mediaLink`. Accept tertulis `.jpg .jpeg .png .gif .mp4`, maks 5 MB.
- **Quick chat** (`ChatDetail` → modal "Quick Chat / Template"): memilih template memanggil `POST /conversation/quick-chat` dan **hanya** mengisi teks composer. Gambar template tidak pernah dipakai di chat.
- **Chat multiple image** (PRD/TDD FE terpisah, Approved): composer mendukung beberapa lampiran (`attachments`), maks 10 gambar, teks menjadi caption gambar pertama, dikirim satu request `POST /send-message`.

> ⚠️ **Snapshot repo FE lokal** (`dev` @ `e7825d2`, 2026-02-22) lebih tua dari dokumentasi: `ModalFormTemplateChat` di snapshot belum punya upload media sama sekali. Implementasi wajib dimulai dari `dev` terbaru.

### 1.2 Temuan

| # | Temuan | Dampak |
|---|---|---|
| T-1 | Accept form template mencantumkan `.gif` dan `.mp4`, padahal `POST /master/saktiform-media` hanya menerima gambar (`StorageService.uploadImage` → `File bukan gambar valid`) dan mengonversi raster ke WebP (animasi GIF hilang). | Upload video selalu gagal; GIF jadi gambar statis. Accept harus dikoreksi. |
| T-2 | Komponen `MultipleFileUpload` (dipakai form produk) sudah ada: multi slot, preview, hapus per file, `maxFiles`. Tidak punya fitur mengubah urutan. | Dapat dipakai ulang **tanpa diubah** (pengurutan tidak dibutuhkan, §11 Q3). |
| T-3 | Quick chat mengganti teks composer tetapi tidak menyentuh lampiran. | Dibutuhkan aturan saat template bergambar dipilih ketika composer sudah berisi lampiran. |

---

## 2. Problem Statement

Admin tidak bisa menyimpan lebih dari satu gambar per template, dan agent tidak bisa memakai gambar template saat membalas chat. Agent harus mengunduh lalu mengunggah ulang gambar katalog secara manual setiap kali.

---

## 3. Goals & Non-Goals

### Goals
- G-1: Admin dapat menambah, melihat, dan menghapus **hingga 10 gambar** per template.
- G-2: Saat agent memilih template di chat, **teks dan semua gambarnya** masuk ke composer, lalu terkirim sekaligus memakai alur chat multiple image (teks = caption gambar pertama).
- G-3: Template lama (1 gambar) tampil dan bisa diedit tanpa migrasi manual.
- G-4: Aman terhadap urutan deploy (FE baru + backend lama tidak merusak data).

### Non-Goals
- Video/dokumen pada template.
- Multi-image pada blast/campaign (backend tetap memakai gambar pertama).
- Mengubah urutan gambar (drag & drop maupun tombol geser) — §11 Q3. Urutan = urutan gambar ditambahkan; untuk mengubahnya, hapus lalu tambah ulang.
- Mengedit gambar template langsung dari composer chat (agent hanya bisa menghapus gambar dari pesan yang akan dikirim).

---

## 4. User Stories

- **US-1** — Sebagai admin, saya menambahkan beberapa gambar ke template, dan melihat preview-nya.
- **US-2** — Sebagai admin, saat mengedit template, saya melihat semua gambar tersimpan sesuai urutannya, bisa menghapus sebagian dan menambah gambar baru.
- **US-3** — Sebagai admin, di daftar template saya bisa melihat template mana yang berisi gambar dan berapa banyak.
- **US-4** — Sebagai agent, saat memilih template di chat, teks dan gambar template langsung masuk composer; saya bisa menghapus gambar yang tidak perlu sebelum mengirim.
- **US-5** — Sebagai agent, saya mengirim template bergambar dengan satu klik kirim, dan gambar tampil normal di riwayat chat.

---

## 5. Functional Requirements

### 5.1 Form template (`ModalFormTemplateChat`)

| ID | Requirement |
|---|---|
| FR-1 | Field "Gambar Template" memakai upload multi file, maks **10** gambar, opsional. |
| FR-2 | Tipe yang diterima: `.jpg, .jpeg, .png, .webp`; maks **5 MB** per file (mengikuti batas existing). File lain ditolak dengan toast. (T-1) |
| FR-3 | Setiap gambar menampilkan preview; dapat dihapus satu per satu. |
| FR-4 | Urutan gambar = urutan ditambahkan (gambar tersimpan lebih dulu, gambar baru di belakang). **Tidak ada** fitur mengubah urutan (§11 Q3). Urutan tampilan = urutan kirim di chat. |
| FR-5 | Mode edit: gambar tersimpan dimuat dari `mediaLinks` (fallback `mediaLink` bila `mediaLinks` tidak ada) sesuai urutan. |
| FR-6 | Gambar baru di-upload **saat Simpan** (pola form produk), berurutan, ke `POST /master/saktiform-media`. Gambar lama (sudah berupa URL) tidak di-upload ulang. |
| FR-7 | Bila ada upload yang gagal, simpan **dibatalkan** (template tidak berubah), toast error, form tetap terbuka dengan pilihan gambar utuh. |
| FR-8 | Payload simpan selalu berisi `mediaLinks` (array berurutan, boleh kosong) **dan** `mediaLink` = elemen pertama atau `null` (G-4: backend lama tetap menyimpan gambar pertama). |
| FR-9 | Menghapus semua gambar lalu simpan = template tanpa gambar. |
| FR-10 | Tombol Simpan menampilkan loading selama upload + simpan; tidak bisa diklik ganda. |
| FR-11 | Error backend (`Maksimal 10 gambar per template`, `URL tidak valid untuk bucket ini`) ditampilkan apa adanya lewat toast. |

### 5.2 Daftar template

| ID | Requirement |
|---|---|
| FR-12 | Daftar template (`ListWorkspaceTemplateChat`) menampilkan indikator jumlah gambar (mis. ikon gambar + "3") bila `mediaLinks.length > 0`. |
| FR-13 | Modal quick chat di `ChatDetail` menampilkan indikator yang sama pada setiap item template. |

### 5.3 Quick chat → composer (`ChatDetail`)

| ID | Requirement |
|---|---|
| FR-14 | Memilih template mengisi teks composer dengan `message` (perilaku existing) **dan** memasukkan setiap URL di `mediaLinks` ke composer sebagai lampiran gambar, sesuai urutan. |
| FR-15 | Lampiran dari template ditampilkan di strip preview yang sama dengan lampiran lokal, termasuk counter `n/10`, dan bisa dihapus satu per satu. |
| FR-16 | Memilih template lain **mengganti** lampiran yang berasal dari template sebelumnya; lampiran yang dipilih agent sendiri dari perangkat tetap dipertahankan. |
| FR-17 | Bila total gambar melebihi 10, hanya gambar sampai kuota yang dimasukkan, dengan toast `Hanya N gambar template yang ditambahkan (maksimal 10 gambar)`. |
| FR-18 | Bila composer berisi dokumen (non-gambar), gambar template **tidak** dimasukkan; teks tetap diisi; toast `Hapus dokumen terlebih dahulu untuk menambahkan gambar template`. |
| FR-19 | Lampiran template **tidak di-upload ulang**: URL dikirim langsung di `mediaLink[]` `POST /send-message`; hanya lampiran lokal yang di-upload. Urutan gabungan mengikuti urutan di strip. |
| FR-20 | Hasil kirim (sukses, gagal sebagian, gagal total) mengikuti aturan PRD FE Chat Multiple Image (composer dikosongkan / menyisakan gambar yang belum terkirim / tidak berubah), berlaku sama untuk lampiran template dan lokal. |
| FR-21 | Berpindah conversation membersihkan lampiran template seperti lampiran lokal. |

---

## 6. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | `MultipleFileUpload` dipakai tanpa perubahan; perilaku form produk tidak berubah. |
| NFR-2 | Mengikuti konvensi FE: `<script setup lang="ts">`, Pinia Options API, `alertStore.setAlert`, `postData/getData`, `errorHelper`. |
| NFR-3 | Toleran terhadap response backend lama (tanpa `mediaLinks`): fallback ke `mediaLink`. |
| NFR-4 | Lolos `vue-tsc -b && vite build` tanpa error tipe baru. |

---

## 7. UX / Wireframe (ringkas)

**Form template**

```
Nama Template *        [______________________]
Konten Template *      [______________________]
Variabel tersedia      (Nama Customer) (Kode Order) ...

Gambar Template (opsional, maks 10)            3/10
┌─────┐ ┌─────┐ ┌─────┐ ┌ ─ ─ ┐
│ img │ │ img │ │ img │ │  +  │
│   🗑 │ │   🗑 │ │   🗑 │ └ ─ ─ ┘
└─────┘ └─────┘ └─────┘
.jpg .jpeg .png .webp · Maks 5 MB per gambar
                                   [Batal] [Simpan]
```

**Composer chat setelah memilih template**

```
┌──────────────────────────────────────────────┐
│ [img][img][img]                        3/10  │  ← lampiran template (bisa dihapus ×)
├──────────────────────────────────────────────┤
│ 📎  Halo Budi, berikut katalog promo kami…  ➤ │
└──────────────────────────────────────────────┘
```

---

## 8. Acceptance Criteria

- **Given** admin menambah 3 gambar dan menyimpan, **then** `POST /template` berisi `mediaLinks` 3 URL berurutan dan `mediaLink` = URL pertama; edit ulang menampilkan 3 gambar dengan urutan sama.
- **Given** template 2 gambar diedit dan admin menambah 1 gambar, **then** gambar baru berada di urutan ke-3.
- **Given** template lama dengan 1 gambar, **when** diedit, **then** gambar tampil; menambah 1 gambar lalu simpan → 2 gambar.
- **Given** upload gambar ke-2 gagal saat simpan, **then** `POST /template` tidak dipanggil, toast error, form tetap terbuka.
- **Given** admin memilih file `.mp4`, **then** file ditolak dengan toast; tidak ada request upload.
- **Given** template 3 gambar, **then** daftar template dan modal quick chat menampilkan indikator "3".
- **Given** composer kosong, **when** agent memilih template 3 gambar, **then** teks terisi dan 3 gambar muncul di strip (3/10); klik kirim → satu `POST /send-message` dengan `mediaLink` 3 URL template, **tanpa** request upload; gambar tampil di riwayat chat.
- **Given** agent sudah memilih 2 foto dari perangkat, **when** memilih template 3 gambar, **then** strip berisi 5 gambar; saat kirim hanya 2 foto lokal yang di-upload, `mediaLink` berisi 5 URL sesuai urutan strip.
- **Given** agent memilih template A (3 gambar) lalu template B (2 gambar), **then** strip berisi 2 gambar template B (+ lampiran lokal bila ada).
- **Given** composer berisi 9 gambar lokal, **when** memilih template 3 gambar, **then** hanya 1 gambar template ditambahkan + toast kuota.
- **Given** composer berisi 1 dokumen PDF, **when** memilih template bergambar, **then** teks terisi, gambar tidak ditambahkan, toast muncul.
- **Given** pengiriman gagal di gambar ke-3 dari 5, **then** composer menyisakan gambar ke-3..5 (template maupun lokal), teks kosong.

---

## 9. Risiko

| ID | Risiko | Mitigasi |
|---|---|---|
| R-1 | Snapshot FE lokal lama; implementasi di atas kode usang akan konflik. | `git pull` `dev` terbaru sebelum mulai. |
| R-2 | FE Chat Multiple Image belum diimplementasikan; fitur composer di PRD ini bergantung padanya. | Implementasikan bersama / sesudahnya; TDD FE mendefinisikan delta pada tipe `ChatAttachment`. |
| R-3 | `MultipleFileUpload` memutasi array prop (`localFiles = ref(props.files)`) dan memakai slot kosong `{}`. | Payload memfilter item tanpa `url`; komponen tidak diubah. |
| R-4 | FE lama (sebelum rilis ini) mengedit template multi gambar → terpotong jadi 1 (PRD backend R-1). | Rilis FE ini segera setelah backend; tidak ada FE lama yang tersisa di produksi. |
| R-5 | Gambar template dihapus dari storage setelah masuk composer. | Tidak ada cleanup file di backend (non-goal) → risiko sangat kecil; bila terjadi, provider gagal dan alur gagal-sebagian berlaku. |

---

## 10. Dependensi & Urutan Rilis

1. Backend Chat Template Multiple Image — **sudah selesai** (perlu deploy).
2. Backend Chat Multiple Image — sudah selesai.
3. FE Chat Multiple Image (composer multi lampiran) — prasyarat FR-14..21.
4. FE PRD ini.

Form template (§5.1–5.2) dapat dirilis lebih dulu tanpa menunggu langkah 3.

---

## 11. Resolved Decisions

| # | Pertanyaan | Keputusan |
|---|---|---|
| Q1 | Kapan gambar form template di-upload: saat dipilih (pola lama) atau saat Simpan (pola produk)? | **Saat Simpan** — tidak meninggalkan file yatim saat admin batal, satu titik error. |
| Q2 | Tipe & ukuran file template? | **`.jpg .jpeg .png .webp`, 5 MB** — buang `.gif`/`.mp4` (T-1). |
| Q3 | Cara mengurutkan gambar? | **Tidak ada mekanisme pengurutan** (keputusan product owner). Urutan = urutan ditambahkan. |
| Q4 | Memilih template saat composer sudah ada lampiran lokal: ganti semua atau gabung? | **Gabung**: lampiran lokal dipertahankan, lampiran template sebelumnya diganti (FR-16). |
| Q5 | Template bergambar dipilih saat ada dokumen di composer? | **Gambar tidak dimasukkan** + toast; teks tetap diisi (FR-18). |
| Q6 | Posisi gambar template terhadap lampiran lokal di strip? | **Template di depan** (gambar katalog sebagai pesan utama, caption menempel di gambar template pertama). |
| Q7 | Perlu indikator jumlah gambar di daftar template & modal quick chat? | **Ya** (FR-12, FR-13). |
