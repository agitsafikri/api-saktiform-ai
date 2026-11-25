# PRD — Chat Template Multiple Image

| Field | Value |
|---|---|
| Feature name | Chat Template Multiple Image |
| Component | `ChatTemplate` entity, `AddChatTemplateDto`, `DetailTemplate`, `ChatTemplateListDto`, `MessageTemplateService`, `ChatTemplateRepository`, `ConversationService.getQuickChat`, (terdampak) `BlastCampaignService` |
| Status | **Approved** — semua keputusan §12 resolved mengikuti rekomendasi default (2026-10-15) · backend **Implemented** (lihat TDD) |
| Scope | Per-workspace (template di-scope ke workspace) |
| Last updated | 2026-10-15 |
| Dokumen terkait | [PRD Chat Template Media Link](chat-template-media-link.md) (single media, existing) · [TDD Chat Template Multiple Image](../tdd/chat-template-multiple-image.md) · [PRD Frontend](chat-template-multiple-image-frontend.md) · [TDD Frontend](../tdd/chat-template-multiple-image-frontend.md) · [PRD Chat Multiple Image](chat-multiple-image.md) (kirim banyak gambar di chat) · [PRD Blast Chat](blast-chat.md) |

> PRD ini memperluas fitur **Chat Template Media Link** (1 media per template) agar satu template bisa **menyimpan dan mengembalikan (fetch) beberapa gambar**, berurutan. Bagian §2 berisi analisis kelayakan; keputusan yang masih terbuka ada di §12 beserta rekomendasi default.

---

## 1. Background

### 1.1 Kondisi saat ini

Template chat (`ChatTemplate`, tabel `chat_template`) menyimpan **satu** media pada kolom `media_link` (path relatif storage, `text`, nullable).

| Alur | Endpoint / Method | Perilaku media sekarang |
|---|---|---|
| Upload file | `POST /master/saktiform-media` → `MasterService.saveSaktiformMedia` → `StorageService.uploadImage` | Hanya **gambar** (non-gambar ditolak, raster dikonversi ke WebP), disimpan di **bucket produk** dengan path `media/<uuid>.<ext>`; mengembalikan public URL bucket produk. |
| Simpan (create/update) | `POST /template` → `MessageTemplateService.upsertMessageTemplate` | `mediaLink` (public URL) → `extractPathFromPublicUrl` → disimpan sebagai path. `null` = hapus media (overwrite-null). URL tidak valid → 400. |
| List | `GET /template` → `getListTemplate` (JPQL constructor `ChatTemplateListDto`) | `mediaLink` = `getProdukPublicUrl(path)`. |
| Detail | `GET /template/{id}` → `getDetail` (`DetailTemplate`) | `mediaLink` = `getProdukPublicUrl(path)`. |
| Pakai di chat (quick chat) | `POST /conversation/quick-chat` → `ConversationService.getQuickChat` (`QuickChatResponse`) | **Hanya `message`** (teks terisi placeholder). Media template **tidak** dikembalikan → saat ini media template tidak pernah terpakai di chat. |
| Pakai di blast | `BlastCampaignService.create` (messageSource `TEMPLATE`) | Snapshot `tpl.getMediaLink()` (path) ke `blast_campaign.media_link`; worker kirim **image + caption** (1 gambar). |

### 1.2 Fitur terkait yang sudah ada

`POST /send-message` sekarang sudah mendukung **multiple image** (`mediaLink: string | string[]`, maks 10, dikirim berurutan, caption hanya di gambar pertama) — lihat [PRD Chat Multiple Image](chat-multiple-image.md). Ini membuka jalan agar gambar template bisa dikirim sekaligus dari chat.

---

## 2. Analisis Kelayakan (Possibility)

**Kesimpulan: FEASIBLE**, dengan perubahan backend kecil-menengah dan **tanpa breaking change** bila memakai pendekatan aditif (§2.2). Ada **dua temuan bucket** (§2.4) yang harus diputuskan karena menentukan apakah gambar template benar-benar bisa dipakai di chat dan blast.

### 2.1 Opsi penyimpanan

| Opsi | Deskripsi | Plus | Minus |
|---|---|---|---|
| **A. Kolom `jsonb` baru `media_links`** (rekomendasi) | `@JdbcTypeCode(SqlTypes.JSON) List<String> mediaLinks` — array path berurutan. | Pola sudah dipakai project (`ProdukFormConfig.options`, `Order`, `ProdukEkstra`); `ddl-auto=update` menambah kolom otomatis; urutan terjaga; 1 query, tanpa join. | Tidak bisa FK/constraint per gambar (tidak dibutuhkan). |
| B. Tabel anak `chat_template_media` (`id`, `id_template`, `path`, `sort_order`) | Relasi `@OneToMany` orphanRemoval. | Paling relasional. | Entity + repository baru, risiko N+1 di list, lebih banyak kode untuk kebutuhan sederhana. |
| C. Simpan JSON/CSV di kolom `media_link` existing | Ubah isi kolom lama. | Tanpa kolom baru. | **Breaking** untuk blast (membaca `media_link` sebagai 1 path) & data lama; parsing rapuh. **Ditolak.** |

### 2.2 Kompatibilitas mundur (pendekatan aditif)

- Kolom lama `media_link` **tetap ada** dan di-*dual-write* = **path gambar pertama** (atau `null`). Konsumen lama (blast, report) tetap jalan tanpa perubahan.
- Request menerima field baru `mediaLinks: string[]`; field lama `mediaLink: string` tetap diterima sebagai fallback.
- Response menambah `mediaLinks: string[]` dan **mempertahankan** `mediaLink` (= gambar pertama) sehingga FE lama tidak rusak.
- Data lama: backfill sekali jalan `media_links = [media_link]` (idempotent, pola `*SchemaInitializer` on `ApplicationReadyEvent`), plus fallback baca `media_links ?? [media_link]`.

### 2.3 Dampak per komponen

| Komponen | Dampak | Tingkat |
|---|---|---|
| `ChatTemplate` | + `mediaLinks` (jsonb). `mediaLink` tetap. | Rendah |
| `AddChatTemplateDto` | + `List<String> mediaLinks`. | Rendah |
| `MessageTemplateService.upsertMessageTemplate` | Validasi (maks, URL valid, buang blank), extract path semua URL, dual-write. | Rendah |
| `ChatTemplateRepository.getListChatTemplate` | JPQL constructor expression perlu menyertakan `mediaLinks` (atau map dari entity) — perlu diverifikasi Hibernate memetakan atribut JSON di constructor expression. | Rendah–Sedang |
| `DetailTemplate`, `ChatTemplateListDto` | + `List<String> mediaLinks` (public URL). | Rendah |
| `QuickChatResponse` / `getQuickChat` | + `mediaLinks` (public URL) agar chat bisa memakai gambar template. | Rendah |
| Schema initializer baru | Backfill `media_links` dari `media_link`. | Rendah |
| `BlastCampaignService` | Tidak wajib berubah (tetap baca `media_link` = gambar pertama). Lihat §2.4-B. | — |
| Skema DB | + 1 kolom nullable `jsonb`. Tanpa drop/rename. | Rendah |
| `WhatsappClientHelper`, WebSocket, `Chat` | Tidak berubah. | — |

### 2.4 Temuan bucket (bug existing yang relevan)

Gambar template disimpan di **bucket produk** (`produk-media`), sedangkan media chat di **bucket chat** (`saktiform-media`). Kedua bucket memakai prefix path `media/...` sehingga **path saja tidak bisa membedakan bucket**.

- **A. Chat — gambar template terkirim tapi rusak di riwayat chat.** Jika FE mengirim URL gambar template ke `POST /send-message`, `extractPathFromPublicUrl` menerima URL bucket produk dan menyimpan path-nya ke `Chat.media`, lalu read path chat (`ConversationService`) membangun URL dengan `getPublicUrl` (bucket chat) → **link rusak**. Sudah tercatat sebagai PRD Chat Multiple Image §12 Q7. Fitur "pakai gambar template di chat" **bergantung** pada perbaikan ini.
- **B. Blast dari template dengan media — kemungkinan gagal kirim.** `BlastSendTxService.resolveMediaUrl` mengubah path snapshot template dengan `getPublicUrl` (bucket chat), padahal objeknya di bucket produk → URL yang dikirim ke provider kemungkinan 404. *Perlu diverifikasi di staging*; bila terkonfirmasi ini bug existing, terlepas dari fitur ini.

Opsi perbaikan A (dibahas di §12 Q6): saat `send-message` menerima URL bucket produk, backend **meng-copy objek ke bucket chat** (MinIO `copyObject` lintas bucket) lalu menyimpan path bucket chat. Read path chat tidak berubah, dan riwayat chat tetap utuh walau gambar template kemudian diganti.

---

## 3. Problem Statement

Admin ingin satu template (mis. "Katalog Promo") berisi **beberapa gambar** sekaligus. Saat ini template hanya bisa menyimpan satu gambar, dan gambar itu pun tidak dikembalikan ke alur quick chat, sehingga agent harus mengunggah ulang gambar secara manual setiap kali.

---

## 4. Goals & Non-Goals

### Goals
- G-1: Template dapat **menyimpan 0..N gambar berurutan** (N default 10).
- G-2: Endpoint list, detail, dan quick chat **mengembalikan semua gambar** sebagai public URL, urutan sama dengan saat disimpan.
- G-3: **Tanpa breaking change** untuk FE lama, blast, dan data template existing.
- G-4: Gambar template dapat dikirim di chat melalui `POST /send-message` multiple image (bergantung pada keputusan §12 Q6).

### Non-Goals
- Multiple image pada **blast** (tetap 1 gambar = gambar pertama). Lihat §12 Q7.
- Media non-gambar (video/dokumen) pada template — endpoint upload hanya menerima gambar.
- Menghapus file lama di MinIO saat gambar dilepas dari template (konsisten dengan perilaku saat ini: tidak ada cleanup).
- Mengubah format placeholder / rendering teks template.
- Mengirim gambar otomatis dari backend saat quick chat (quick chat tetap hanya "generate", pengiriman tetap oleh agent via `/send-message`).

---

## 5. User Stories

- **US-1** — Sebagai admin, saya ingin menambahkan beberapa gambar ke satu template, terkirim sesuai urutan saya menambahkannya.
- **US-2** — Sebagai admin, saat membuka template untuk diedit, saya ingin melihat semua gambar yang tersimpan sesuai urutannya.
- **US-3** — Sebagai agent, saat memilih template di chat, saya ingin teks **dan** gambar-gambarnya langsung masuk ke composer, lalu terkirim sekaligus dengan teks sebagai caption gambar pertama.
- **US-4** — Sebagai admin, template lama saya (1 gambar) tetap tampil dan berfungsi tanpa perlu disimpan ulang.

---

## 6. Functional Requirements

### Simpan (`POST /template`)

| ID | Requirement |
|---|---|
| FR-1 | `AddChatTemplateDto` menerima field opsional `mediaLinks: string[]` (public URL, berurutan). |
| FR-2 | Prioritas input: jika `mediaLinks` dikirim (bukan `null`), pakai `mediaLinks`. Jika tidak, fallback ke `mediaLink` lama (`[mediaLink]` atau kosong). |
| FR-3 | Elemen `null`/blank dibuang; urutan sisa elemen dipertahankan. |
| FR-4 | Jumlah gambar setelah dibersihkan MUST ≤ batas (default 10, konfigurasi `chat.template.max-images`); jika lebih → **400** `Maksimal N gambar per template`. |
| FR-5 | Setiap URL dikonversi via `StorageService.extractPathFromPublicUrl`; satu saja tidak valid → **400** `URL tidak valid untuk bucket ini`, **tidak ada** perubahan tersimpan. |
| FR-6 | Persist `media_links` = daftar path (atau `null` bila kosong) **dan** `media_link` = path pertama (atau `null`) — dual-write. |
| FR-7 | Update bersifat **replace penuh** (overwrite): daftar baru menggantikan daftar lama; daftar kosong / tidak ada media = hapus semua gambar (konsisten dengan overwrite-null existing). |
| FR-8 | URL duplikat diizinkan (tidak di-dedupe) — admin mungkin sengaja mengulang. *(lihat §12 Q4)* |

### Fetch

| ID | Requirement |
|---|---|
| FR-9 | `GET /template/{id}` (`DetailTemplate`) mengembalikan `mediaLinks: string[]` (public URL via `getProdukPublicUrl`, urutan tersimpan; `[]` bila tidak ada) dan `mediaLink` = elemen pertama atau `null`. |
| FR-10 | `GET /template` (list, `ChatTemplateListDto`) mengembalikan `mediaLinks` dan `mediaLink` dengan aturan yang sama dengan FR-9. |
| FR-11 | `POST /conversation/quick-chat` (`QuickChatResponse`) mengembalikan `mediaLinks: string[]` (public URL) selain `message`. |
| FR-12 | Template lama (hanya `media_link`) dibaca sebagai `mediaLinks = [publicUrl(media_link)]` (via backfill FR-13 dan fallback saat baca). |

### Migrasi & kompatibilitas

| ID | Requirement |
|---|---|
| FR-13 | Saat startup, backfill idempotent: `UPDATE chat_template SET media_links = jsonb_build_array(media_link) WHERE media_links IS NULL AND media_link IS NOT NULL`. Kegagalan di-log, tidak menggagalkan startup (pola `LabelSchemaInitializer`). |
| FR-14 | Blast (`BlastCampaignService`) tetap membaca `media_link` (= gambar pertama) tanpa perubahan perilaku. |

### Penggunaan di chat (bergantung §12 Q6)

| ID | Requirement |
|---|---|
| FR-15 | FE dapat meneruskan `mediaLinks` hasil quick chat langsung ke `POST /send-message` (`messageType: IMAGE`, `mediaLink: [...]`, `message` = teks template sebagai caption gambar pertama) tanpa upload ulang. |
| FR-16 | Gambar yang dikirim dari bucket produk MUST tampil benar di riwayat chat (perbaikan §2.4-A). |

---

## 7. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | Backward compatible: client yang hanya mengirim/membaca `mediaLink` tetap bekerja (dengan catatan risiko R-1). |
| NFR-2 | Tidak ada perubahan pada envelope `RestResponse`; error tetap 400 via catch existing di `ChatTemplateController`. |
| NFR-3 | List template tetap 1 query (tanpa N+1). |
| NFR-4 | Mengikuti konvensi store-path / return-URL `StorageService` (DB simpan path, API kembalikan public URL bucket produk). |
| NFR-5 | Batas jumlah gambar dapat dikonfigurasi lewat env (`CHAT_TEMPLATE_MAX_IMAGES`, default 10), selaras dengan batas `/send-message`. |

---

## 8. Data Model Changes

| Tabel | Perubahan |
|---|---|
| `chat_template` | + `media_links jsonb NULL` — array string path relatif bucket produk, berurutan. Ditambahkan otomatis oleh `ddl-auto=update`. |
| `chat_template` | `media_link` **tetap**, kini selalu = elemen pertama `media_links` (denormalisasi untuk kompatibilitas). |

Contoh isi:

```json
media_link  = "media/1b2c....webp"
media_links = ["media/1b2c....webp", "media/9f8e....webp", "media/77aa....webp"]
```

Tidak ada perubahan pada tabel lain.

---

## 9. API Changes

### 9.1 `POST /template` — request

```json
{
  "id": null,
  "idWorkspace": 1,
  "namaTemplate": "Katalog Promo Oktober",
  "content": "Halo {nama_customer}, berikut katalog promo kami 🙏",
  "mediaLinks": [
    "https://<public-base>/produk-media/media/a.webp",
    "https://<public-base>/produk-media/media/b.webp",
    "https://<public-base>/produk-media/media/c.webp"
  ]
}
```

Client lama yang mengirim `"mediaLink": "https://..."` tetap diterima (FR-2).

### 9.2 `GET /template/{id}` dan item `GET /template` — response `data`

```json
{
  "id": "…",
  "namaTemplate": "Katalog Promo Oktober",
  "content": "Halo {nama_customer}, …",
  "mediaLink": "https://<public-base>/produk-media/media/a.webp",
  "mediaLinks": [
    "https://<public-base>/produk-media/media/a.webp",
    "https://<public-base>/produk-media/media/b.webp",
    "https://<public-base>/produk-media/media/c.webp"
  ]
}
```

(List juga tetap memuat `kategori`.)

### 9.3 `POST /conversation/quick-chat` — response `data`

```json
{
  "message": "Halo Budi, berikut katalog promo kami 🙏",
  "mediaLinks": ["https://…/a.webp", "https://…/b.webp", "https://…/c.webp"]
}
```

---

## 10. Acceptance Criteria

- **Given** create dengan 3 URL valid di `mediaLinks`, **then** `media_links` berisi 3 path berurutan dan `media_link` = path pertama.
- **Given** create dengan hanya `mediaLink` (client lama), **then** `media_links = [path]`, `media_link = path`.
- **Given** update dengan `mediaLinks: []`, **then** `media_links = null` dan `media_link = null`.
- **Given** 11 URL (batas 10), **then** 400 `Maksimal 10 gambar per template` dan data lama tidak berubah.
- **Given** URL ke-2 bukan URL bucket valid, **then** 400 `URL tidak valid untuk bucket ini` dan data lama tidak berubah.
- **Given** `mediaLinks: ["a", "", null, "b"]`, **then** tersimpan 2 path (`a`, `b`) dengan urutan itu.
- **Given** template lama dengan `media_link` saja (sebelum deploy), **when** aplikasi start, **then** `media_links` terisi `[media_link]`; detail/list mengembalikan `mediaLinks` 1 elemen.
- **Given** template dengan 3 gambar, **when** `GET /template/{id}`, `GET /template`, dan `POST /conversation/quick-chat`, **then** masing-masing mengembalikan 3 public URL bucket produk dengan urutan sama.
- **Given** campaign blast dibuat dari template 3 gambar, **then** `blast_campaign.media_link` = path gambar pertama (perilaku blast tidak berubah).
- **(Jika Q6 = ya)** **Given** agent mengirim gambar template via `/send-message`, **then** gambar tampil benar di riwayat chat (agent & customer), dan mengganti gambar template sesudahnya tidak merusak riwayat chat.

---

## 11. Risiko & Edge Cases

| ID | Risiko / Edge case | Mitigasi |
|---|---|---|
| R-1 | **FE lama mengedit template multi-gambar** dan hanya mengirim `mediaLink` → daftar terpotong jadi 1 gambar (FR-2 fallback). | Rilis FE baru bersamaan; dokumentasikan. Alternatif ketat ada di §12 Q3. |
| R-2 | JPQL constructor expression tidak memetakan atribut JSON di list. | Verifikasi di TDD; fallback: query entity lalu map ke DTO (tetap 1 query). |
| R-3 | Backfill berjalan bersamaan dengan write di instance lain. | Query idempotent dengan `WHERE media_links IS NULL`; dual-write menjaga konsistensi berikutnya. |
| R-4 | `media_link` dan `media_links` tidak sinkron jika ada penulisan di luar service (mis. SQL manual). | Satu-satunya jalur tulis adalah `upsertMessageTemplate`; read memprioritaskan `media_links`. |
| R-5 | Gambar template dipakai di chat → link rusak (§2.4-A). | Keputusan §12 Q6; tanpa perbaikan, FE **tidak boleh** mengaktifkan kirim gambar template di chat. |
| R-6 | Blast dari template dengan media kemungkinan gagal (§2.4-B). | Verifikasi staging; perbaikan terpisah (§12 Q8). |
| R-7 | File MinIO yatim saat gambar dihapus dari template. | Non-goal; sama dengan perilaku sekarang. |
| E-1 | Template kategori sistem (`FOLLOWUP-*`, `CONFIRMATION-*`) diberi gambar. | Diizinkan untuk disimpan/fetch; pengiriman otomatis order follow-up tetap **teks saja** (tidak berubah). |

> Catatan bug existing di luar scope: `upsertMessageTemplate` membuat kategori `"QUICK_REPLY"`, sedangkan `getQuickChat` mengecek `"Quick Reply"` → template quick reply selalu melewati cabang isi-placeholder-order. Tidak diperbaiki di PRD ini; sebaiknya dibuat tiket terpisah.

---

## 12. Resolved Decisions

| # | Pertanyaan | Keputusan |
|---|---|---|
| Q1 | Model penyimpanan: kolom `jsonb` vs tabel anak? | **Kolom `jsonb` `media_links`** + dual-write `media_link` (§2.1-A). |
| Q2 | Batas jumlah gambar per template? | **10**, configurable, selaras dengan `/send-message`. |
| Q3 | Saat request hanya berisi `mediaLink` (client lama) untuk template yang punya >1 gambar: potong jadi 1, atau abaikan dan pertahankan daftar lama? | **Potong (replace)** — semantik overwrite konsisten; risiko R-1 diterima karena FE dirilis bersamaan. |
| Q4 | Dedupe URL duplikat? | **Tidak** — simpan apa adanya. |
| Q5 | `quick-chat` ikut mengembalikan `mediaLinks`? | **Ya** — tanpa ini gambar template tidak bisa dipakai di chat. |
| Q6 | Perbaiki masalah bucket agar gambar template bisa dikirim di chat (§2.4-A)? | **Ya, masuk scope**: di `send-message`, URL bucket produk di-copy ke bucket chat sebelum disimpan sebagai `Chat.media`. Tanpa ini, FR-15/16 ditunda. |
| Q7 | Blast mengirim semua gambar template? | **Tidak** (tetap gambar pertama) — multi-image blast melipatgandakan pesan per penerima & risiko rate limit/ban; PRD terpisah bila diperlukan. |
| Q8 | Perbaiki dugaan bug blast bucket (§2.4-B) di sini? | **Tidak** — verifikasi di staging, lalu tiket terpisah (perbaikan kecil: resolve path template dengan `getProdukPublicUrl`). |
| Q9 | Perlu PRD/TDD frontend (form template multi-upload, quick chat memasukkan gambar ke composer)? | **Ya** — sudah dibuat ([PRD FE](chat-template-multiple-image-frontend.md)); UI tanpa fitur pengurutan gambar (urutan = urutan ditambahkan). |

---

## 13. Rencana Rilis (ringkas)

1. **Backend fase 1** — kolom `media_links`, simpan/fetch (FR-1..14), backfill. Aman dirilis sendiri (aditif).
2. **Backend fase 2** (jika Q6 = ya) — copy objek bucket produk → bucket chat di `send-message` (FR-16).
3. **Frontend** — form template multi-gambar; quick chat memasukkan `mediaLinks` ke composer dan mengirim via `/send-message` multiple image (FR-15).
4. Verifikasi staging, termasuk dugaan bug blast §2.4-B.
