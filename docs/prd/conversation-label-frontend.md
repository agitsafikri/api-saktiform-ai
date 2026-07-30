# PRD — Conversation Label (Frontend / Dashboard)

| Field | Value |
|---|---|
| Feature | Conversation Label — implementasi UI di Saktiform Dashboard |
| Modul | **Chat/Inbox** (ekstensi modul `chat` existing — bukan modul baru) |
| Dokumen terkait | [PRD Backend](./conversation-label.md), [TDD Backend](../tdd/conversation-label.md), [API Reference §6.13](../api-reference.md), [FE Project Overview](../frontend/project-overview.md), [FE Architecture](../frontend/architecture.md), [FE Business Modules](../frontend/business-modules.md) |
| Stack | Vue 3 (`<script setup>`, Composition API), TypeScript, Vite, Pinia, Axios, SCSS |
| Status | Ready for Implementation |
| Scope | Frontend-only (backend Conversation Label sudah selesai Fase 1–6 & terverifikasi) |
| Last updated | 2026-07-18 |
| Target pembaca | Frontend Developer, QA, Product |

> PRD ini mendefinisikan kebutuhan UI/UX fitur **Conversation Label** pada dashboard, dipetakan ke REST API backend yang **sudah diimplementasikan** (`/chat/label`, `/chat/conversation/{id}/label`, plus field `labels` & filter `labelId` pada list/detail conversation). Fitur ini **memperluas modul Chat/Inbox existing** (`src/modules/chat/`, `src/pages/chat/index.vue`), bukan modul baru. Mengikuti konvensi dashboard existing (module-based, Pinia store, helper `apiConfig/method.ts`, komponen `common/`, `alertStore`, `headerContentStore`). Tidak berisi kode final.

---

## Daftar Isi

1. [Latar Belakang & Tujuan](#1-latar-belakang--tujuan)
2. [Scope](#2-scope)
3. [Penempatan di Aplikasi & Hak Akses](#3-penempatan-di-aplikasi--hak-akses)
4. [Arsitektur Frontend](#4-arsitektur-frontend)
5. [Peta Interaksi & Navigasi](#5-peta-interaksi--navigasi)
6. [Detail Layar & Komponen](#6-detail-layar--komponen)
7. [State Management (labelStore + chatStore)](#7-state-management-labelstore--chatstore)
8. [Kontrak API (mapping)](#8-kontrak-api-mapping)
9. [Warna Label & Kontras](#9-warna-label--kontras)
10. [Real-time & Sinkronisasi](#10-real-time--sinkronisasi)
11. [Komponen (reuse & baru)](#11-komponen-reuse--baru)
12. [Validasi & Error Handling](#12-validasi--error-handling)
13. [State Kosong / Loading / Error](#13-state-kosong--loading--error)
14. [Format, i18n & Responsive](#14-format-i18n--responsive)
15. [Non-Functional Requirement](#15-non-functional-requirement)
16. [Acceptance Criteria](#16-acceptance-criteria)
17. [Open Question — RESOLVED](#17-open-question--resolved)

---

## 1. Latar Belakang & Tujuan

Backend Conversation Label sudah lengkap: master label per-workspace (teks + warna hex), assign/unassign banyak label ke conversation (many-to-many), field `labels` pada list & detail conversation, serta filter `labelId` (OR). Frontend perlu menyediakan antarmuka bagi agen/admin untuk **membuat & mengelola** label master, **menempelkan/melepas** label pada percakapan, **melihat** label pada daftar & detail percakapan, dan **memfilter** inbox berdasarkan label.

**Tujuan produk:**
- Agen dapat mengkategorikan percakapan secara visual (mis. "Prospek", "Komplain", "VIP") dan konsisten lintas percakapan.
- Menemukan percakapan lebih cepat lewat **filter label** pada inbox.
- Pengelolaan label master (buat/ubah/hapus + warna) yang ringan dan langsung dari konteks Chat.
- Konsisten dengan look-and-feel dashboard existing (komponen `common/`, chip/tag, toast alert, modal).

**Tujuan teknis:**
- Reuse penuh infrastruktur FE existing: `apiConfig/method.ts`, `alertStore`, `authStore` (workspace & role), komponen `common/` (`Modal`, `ConfirmModal`, `InputCustom`, `MultipleSelectCustom`, `ChipCustom`, `DropdownCustom`, `ButtonCustom`).
- **Perluas modul `chat` existing** (`src/modules/chat/`) alih-alih membuat modul baru; tambah `labelStore` khusus (CRUD master + assignment) agar `chatStore` tetap fokus pada percakapan.
- Integrasi baca "gratis": field `labels` sudah ada pada respons list/detail conversation → cukup ditipekan & dirender.

---

## 2. Scope

### 2.1 Included (MVP FE)
- **Chip label** pada tiap baris percakapan di `ChatList` (kedua tab: Ditangani/Belum Ditangani) dan pada **header `ChatDetail`** percakapan aktif.
- **Assign/Unassign label** ke percakapan aktif lewat **label picker** (popover/dropdown daftar seluruh label workspace dengan checkbox: tercentang = terpasang).
- **Filter inbox by label** — kontrol multi-select label pada panel filter `ChatList` existing; kirim `labelId` (repeatable, OR).
- **Kelola Label Master** — modal "Kelola Label": list, buat (nama + warna), ubah (nama/warna), hapus (dengan konfirmasi & peringatan cascade).
- **Pemilih warna** — palet swatch preset + input hex opsional (validasi `#RRGGBB` di client).

### 2.2 Not Included (MVP FE)
- Label untuk entity selain Conversation (Order/Contact) — di luar scope backend.
- Auto-labelling / aturan otomatis, hierarki/nested label.
- Halaman manajemen label terpisah di menu Pengaturan (dikelola via modal dari Chat — lihat §3).
- Sinkronisasi real-time label lintas agen (perubahan label agen lain baru tampak saat refresh/refetch — lihat §10).
- Bulk assign label ke banyak percakapan sekaligus.

### 2.3 Out of Scope
- Perubahan alur bot/RAG, order, blast, atau modul Pengaturan.
- Perubahan backend (semua endpoint sudah tersedia).

---

## 3. Penempatan di Aplikasi & Hak Akses

### 3.1 Lokasi UI (tanpa route/halaman baru)
Seluruh UI label berada **di dalam halaman Chat existing** `src/pages/chat/index.vue` (route `/chat`, grup sidebar **Chat**). **Tidak ada route, halaman, atau menu sidebar baru.**

| Elemen | Lokasi |
|---|---|
| Chip label (read) | Baris percakapan di `ChatList` + header `ChatDetail` |
| Tombol "Kelola Label" | Header panel kiri `ChatList` (dekat search/filter) → membuka `LabelManagerModal` |
| Label picker (assign) | Header `ChatDetail` (tombol/ikon "Label") → membuka `LabelAssignDropdown` untuk percakapan aktif |
| Filter label | Panel filter `ChatList` existing (bersama filter agent/status/tanggal/unread) |

### 3.2 Hak Akses (role)
**Semua role** yang punya akses menu Chat (OWNER, ADMIN, CUSTOMER_SERVICE) boleh mengelola master label maupun assign/unassign (selaras backend FR-1 & OQ-9). **Tidak ada gating role tambahan**; cukup autentikasi + membership workspace. Semua request scoped ke `authStore.user.activeWorkspace.id` (query `workspaceId`).

---

## 4. Arsitektur Frontend

### 4.1 Struktur (ekstensi modul chat)
```
src/modules/chat/
├── store/
│   ├── chatStore.ts                 # (ubah) ChatItem + field labels; fetch* terima labelIds
│   └── labelStore.ts                # (baru) CRUD master label + assign/unassign + list
├── types.ts                         # (ubah) tambah LabelDto, LabelRequest; ChatItem.labels
├── utils/
│   └── labelColor.ts                # (baru) kontras teks atas warna hex + validasi/normalisasi hex
└── components/
    ├── ChatList.vue                 # (ubah) render LabelChips per baris + filter label
    ├── ChatDetail.vue               # (ubah) render LabelChips + tombol assign di header
    └── label/                       # (baru)
        ├── LabelChips.vue           # render array label sebagai chip berwarna (read-only)
        ├── LabelAssignDropdown.vue  # daftar label workspace + checkbox assign/unassign
        ├── LabelManagerModal.vue    # CRUD master (list + form + hapus)
        └── LabelColorPicker.vue     # palet swatch preset + input hex
```
Komponen modul otomatis ter-register (`unplugin-vue-components` men-scan `src/modules/**/components/`). Store & utils auto-import.

### 4.2 API Layer
- Semua panggilan lewat helper `src/apiConfig/method.ts`: `getData`, `postData`, `putData`, `destroyData`. Arg pertama `type='api_url'`.
- Axios interceptor menambahkan `Bearer <token>` otomatis. Envelope `{ success, message, data }`.
- **Filter `labelId` repeatable:** kirim array `labelId` sehingga terserialisasi `labelId=1&labelId=2` (bukan `labelId[]=…`) agar cocok `@RequestParam List<Long> labelId` di backend (lihat TDD untuk konfigurasi serializer).

### 4.3 Prinsip
- `labelStore` memegang: daftar master label workspace (dipakai picker, filter, manager) + loading/action flags.
- `chatStore` hanya ditambah field `labels` pada item percakapan dan parameter `labelIds` pada fetch list; assignment tidak masuk `chatStore`.
- Semua sukses/gagal aksi → `useAlertStore` toast (auto-hide 3s).

---

## 5. Peta Interaksi & Navigasi

```
/chat  (Inbox)
  ├── Panel kiri (ChatList)
  │     ├── [Kelola Label] ─────────────► LabelManagerModal (CRUD master)
  │     ├── Panel filter ── + Filter Label (multi-select) ─► refetch list (labelId, OR)
  │     └── Baris percakapan ── menampilkan chip label ── klik baris → pilih percakapan
  │
  └── Panel kanan (ChatDetail, saat percakapan dipilih)
        ├── Header: nama kontak + chip label + [+ Label] ─► LabelAssignDropdown
        │      └── centang/uncentang label ─► assign / unassign ─► chip diperbarui
        └── (thread pesan seperti biasa)
```

**Alur utama (happy path) assign:** buka `/chat` → pilih percakapan → klik "+ Label" di header detail → dropdown menampilkan seluruh label workspace (yang terpasang tercentang) → centang "Prospek" → label ter-assign, chip muncul di header & baris list → uncentang "Prospek" → label dilepas.

**Alur kelola master:** klik "Kelola Label" → modal list label → "Tambah" isi nama + pilih warna → simpan → label baru muncul di daftar & tersedia di picker/filter.

**Alur filter:** buka panel filter `ChatList` → pilih satu/lebih label → daftar percakapan hanya menampilkan yang memiliki salah satu label tsb (OR).

---

## 6. Detail Layar & Komponen

### 6.1 Chip label pada daftar percakapan (`ChatList`)
- Tiap item percakapan sudah membawa `labels: LabelDto[]` dari respons `/conversation/assigned|unassigned`.
- Render `<LabelChips :labels="chat.labels" :max="3" />` di bawah/di samping nama kontak; bila > `max`, tampilkan chip "+N".
- Chip: latar = `colorHex`, teks = warna kontras otomatis (§9), ukuran kecil, non-interaktif di list.
- Bila `labels` kosong → tidak render apa-apa (tanpa placeholder).

### 6.2 Chip + assign pada detail percakapan (`ChatDetail`)
- Header detail menampilkan `<LabelChips :labels="detail.labels" />` (dari `/conversation/detail`) + tombol **"+ Label"** (`ButtonCustom`/ikon `TagOutline`).
- Tombol membuka **`LabelAssignDropdown`** (`DropdownCustom`) berisi:
  - Daftar **seluruh master label workspace** (`labelStore.list`); tiap baris = swatch warna + nama + checkbox.
  - Checkbox tercentang = label terpasang pada percakapan aktif.
  - **Toggle centang** → `labelStore.assign(convId, [labelId])`; **uncentang** → `labelStore.unassign(convId, labelId)` (idempotent, optimistic).
  - Field pencarian label (bila daftar panjang) + link "Kelola Label" (buka manager) + empty state "Belum ada label. Buat dulu di Kelola Label."
- Setelah toggle: perbarui chip di header **dan** pada item percakapan terkait di `chatStore` (optimistic), lalu (opsional) `fetchConversationDetail` untuk sinkronisasi.

### 6.3 Filter label pada panel filter (`ChatList`)
- Tambah kontrol **`MultipleSelectCustom`** "Label" pada panel filter existing, opsi dari `labelStore.list` (`{ name, value: id }`).
- Pilihan → set `chatStore` filter `labelIds` → `fetchAssignedChats`/`fetchUnassignedChats` dengan `labelId` (repeatable). Reset ke page 1.
- Kosongkan pilihan → filter label non-aktif (list kembali penuh; backward compatible).
- Semantik banyak label = **OR** (backend FR-16).

### 6.4 Kelola Label Master (`LabelManagerModal`)
- Dibuka dari tombol "Kelola Label" di header `ChatList`. Reuse `Modal` (`#content` + `#action`).
- **Mode list:** tabel/daftar label (swatch + nama), tombol "Tambah Label", tiap baris punya aksi "Ubah" & "Hapus".
- **Mode form (create/edit):** `InputCustom` Nama (wajib) + `<LabelColorPicker>` (wajib). Simpan → `create`/`update` → refetch list.
- **Hapus:** `ConfirmModal` "Hapus label '<nama>'? Label akan dilepas dari semua percakapan." → `destroy` → refetch list + (opsional) refresh inbox agar chip yang terhapus hilang.
- Empty state: "Belum ada label di workspace ini."

### 6.5 Pemilih warna (`LabelColorPicker`)
- **Palet swatch preset** (~10–12 warna, mis. Tailwind 500-an: `#ef4444`, `#f59e0b`, `#22c55e`, `#3b82f6`, `#8b5cf6`, dst.) — klik memilih.
- **Input hex opsional** (`InputCustom`) untuk warna kustom; validasi `#RRGGBB` (6 digit, `#` opsional) sebelum simpan; tampilkan preview swatch + kontras teks.
- Nilai yang dikirim ke API dinormalkan `#rrggbb` (lowercase); backend juga menormalkan sebagai backstop.

---

## 7. State Management (labelStore + chatStore)

### 7.1 `labelStore` (baru)
**State (garis besar):**
- `list: LabelDto[]` (master label workspace), `loadingList`.
- `assigned: Record<string, LabelDto[]>` atau cukup pakai `labels` pada item chat (cache ringan per percakapan aktif — opsional).
- `actionLoading` (disable tombol saat request), `saving`.

**Actions (garis besar):**
- `fetchList()` — `GET /chat/label` (dipakai picker, filter, manager). Dipanggil saat masuk `/chat`.
- `create(payload)`, `update(id, payload)`, `remove(id)` — CRUD master.
- `assign(conversationId, labelIds)` — `POST /chat/conversation/{id}/label`.
- `unassign(conversationId, labelId)` — `DELETE /chat/conversation/{id}/label/{labelId}`.
- `listForConversation(conversationId)` — `GET /chat/conversation/{id}/label` (opsional; detail sudah membawa `labels`).

Semua action: try/catch + `errorHelper` + `alertStore`. Setelah CRUD master, `fetchList()` ulang.

### 7.2 `chatStore` (ubah minimal)
- `ChatItem` tambah `labels: LabelDto[]`.
- State filter tambah `labelIds: number[]` (dipakai kedua tab).
- `fetchAssignedChats`/`fetchUnassignedChats` menyertakan `labelId: this.filter.labelIds` bila non-kosong.
- Helper lokal untuk **patch labels** pada item percakapan tertentu setelah assign/unassign (optimistic), agar chip di list ikut update tanpa full refetch.

---

## 8. Kontrak API (mapping)

Base: `VITE_BASE_URL`. Semua terproteksi JWT. Query `workspaceId` = `authStore.user.activeWorkspace.id`. Envelope `{ success, message, data }`. Detail bentuk ada di [API Reference §6.13](../api-reference.md).

| Aksi UI | Method | Endpoint | Store action |
|---|---|---|---|
| List master label | GET | `/chat/label?workspaceId` | `labelStore.fetchList()` |
| Buat label | POST | `/chat/label?workspaceId` — body `{ name, colorHex }` | `labelStore.create(payload)` |
| Ubah label | PUT | `/chat/label/{labelId}?workspaceId` — body `{ name, colorHex }` | `labelStore.update(id, payload)` |
| Hapus label (cascade) | DELETE | `/chat/label/{labelId}?workspaceId` | `labelStore.remove(id)` |
| Assign label ke percakapan | POST | `/chat/conversation/{conversationId}/label?workspaceId` — body `{ labelIds:[…] }` | `labelStore.assign(convId, ids)` |
| Unassign label | DELETE | `/chat/conversation/{conversationId}/label/{labelId}?workspaceId` | `labelStore.unassign(convId, id)` |
| List label per percakapan (opsional) | GET | `/chat/conversation/{conversationId}/label?workspaceId` | `labelStore.listForConversation(convId)` |
| List percakapan + `labels` + filter | GET | `/conversation/assigned\|unassigned?workspaceId&…&labelId=` | `chatStore.fetchAssignedChats/…` |
| Detail percakapan + `labels` | GET | `/conversation/detail?conversationId` | `chatStore.fetchConversationDetail()` |

**Bentuk penting:**
- `LabelDto` (response): `{ id:number, name:string, colorHex:string }` (`colorHex` selalu `#rrggbb`).
- `LabelRequest` (create/update body): `{ name:string, colorHex:string }`.
- `AssignLabelRequest` (assign body): `{ labelIds:number[] }`.
- Item list conversation kini memuat `labels: LabelDto[]` (kosong `[]` bila tidak ada).
- Detail conversation memuat `labels: LabelDto[]`.

---

## 9. Warna Label & Kontras

- Chip label memakai `colorHex` sebagai **latar**. Warna teks dihitung otomatis agar kontras (hitam/putih) via luminance:
  - `luminance = 0.299·R + 0.587·G + 0.114·B` (0–255) → teks `#ffffff` bila gelap (`< ~140`), `#111827` bila terang.
- Util `labelColor.ts`: `textColorOn(hex)` + `normalizeHex(raw)` + `isValidHex(raw)`.
- Palet preset diletakkan di util/komponen agar konsisten; pengguna tetap boleh input hex kustom.

---

## 10. Real-time & Sinkronisasi

- **Tidak ada topik WebSocket khusus label** di backend MVP. Perubahan label oleh **agen lain** tidak otomatis muncul; tersinkron saat: refetch list (mis. ganti tab/filter/pagination) atau buka ulang detail percakapan.
- **Update optimistic lokal:** setelah assign/unassign oleh pengguna sendiri, chip diperbarui langsung di UI (header detail + baris list terkait) tanpa menunggu round-trip penuh; bila request gagal → rollback + toast.
- **Setelah hapus master label:** label yang terhapus di-cascade backend; FE me-refetch `labelStore.list` dan (opsional) me-refetch inbox tab aktif agar chip lama hilang.
- Catatan: bila di masa depan backend menambah `labels` ke payload event `CONVERSATION_UPDATED`, `chatStore.handleWebSocketEvent` bisa langsung memetakannya (enhancement, di luar MVP).

---

## 11. Komponen (reuse & baru)

**Reuse (`components/common/`):** `Modal`, `ConfirmModal`, `InputCustom`, `MultipleSelectCustom` (filter), `SelectCustom`, `ChipCustom` (basis chip), `DropdownCustom` (picker), `ButtonCustom`, `Table`/`TableCustom` (list di manager, opsional), `Badge` (opsional).

**Baru (`src/modules/chat/components/label/`):** `LabelChips`, `LabelAssignDropdown`, `LabelManagerModal`, `LabelColorPicker` (lihat §4.1). Bila `ChipCustom` cukup fleksibel untuk latar warna kustom, `LabelChips` cukup membungkusnya + logika kontras/overflow "+N".

---

## 12. Validasi & Error Handling

**Validasi client (sebelum request):**
- Buat/ubah label: `name` wajib (trim, non-kosong); `colorHex` wajib & valid `#RRGGBB` (6 digit, `#` opsional) → normalisasi `#rrggbb`.
- Assign: minimal 1 `labelId`.

**Error server** (envelope `success:false`) → toast `alertStore` (pesan dari `data.message`). Kasus khas:
- 400 nama duplikat (case-insensitive) → tampilkan pesan backend ("Label dengan nama tersebut sudah ada di workspace ini"); soroti field nama.
- 400 hex invalid → seharusnya sudah dicegah client; bila lolos, tampilkan pesan backend.
- 400 assign all-or-nothing (labelId asing/beda workspace) → toast; tidak ada chip yang berubah.
- 400 conversation beda workspace → toast (seharusnya tak terjadi karena scoping workspace aktif).
- 401 → interceptor existing hapus token (redirect saat navigasi berikutnya).

**Guard aksi:** tombol simpan/hapus/assign di-disable selama request (`actionLoading`/`saving`) untuk cegah double-submit.

---

## 13. State Kosong / Loading / Error

- **Picker/Manager tanpa label** → empty state + CTA "Buat label pertama".
- **List percakapan** → tanpa perubahan; chip hanya muncul bila `labels` non-kosong.
- **Loading** → `labelStore.loadingList`/`actionLoading` → spinner kecil / disable tombol.
- **Filter tanpa hasil** → empty state inbox existing ("Tidak ada percakapan").
- **Hapus** → tombol menunjukkan loading; toast sukses/gagal.

---

## 14. Format, i18n & Responsive

- Bahasa **Indonesia** (konsisten dashboard). Label UI: "Kelola Label", "Tambah Label", "Warna", "Nama Label", "+ Label".
- Nama label ditampilkan apa adanya (input pengguna). `colorHex` diperlakukan opaque bagi user (dipilih via swatch).
- Responsive: chip boleh membungkus (wrap) / truncate; picker & manager modal usable di layar kecil (mengikuti keterbatasan responsive existing).

---

## 15. Non-Functional Requirement

- **Anti N+1 di UI:** label sudah menyatu di respons list (batch backend) — jangan fetch label per baris.
- **Cache master label:** `labelStore.fetchList()` sekali saat masuk `/chat` (atau saat manager dibuka); refetch hanya setelah CRUD atau ganti workspace.
- **Idempotensi UI:** disable tombol saat in-flight; assign/unassign aman diulang (backend idempotent).
- **Isolasi workspace:** setiap request membawa `workspaceId` aktif; ganti workspace → reset `labelStore.list` & refetch.
- **Konsistensi** dengan komponen `common/` & aksesibilitas dasar (kontras chip terjamin via §9).

---

## 16. Acceptance Criteria

**Read (chip)**
- [ ] Tiap baris percakapan yang memiliki label menampilkan chip berwarna (nama + warna); >N label → chip "+N".
- [ ] Header detail percakapan menampilkan chip label percakapan tsb.
- [ ] Warna teks chip kontras otomatis terhadap latar.

**Assign/Unassign**
- [ ] Tombol "+ Label" pada detail membuka daftar seluruh label workspace; yang terpasang tercentang.
- [ ] Mencentang label meng-assign (chip muncul di detail & baris list); mencentang ulang tidak menduplikasi.
- [ ] Menghapus centang me-unassign (chip hilang); unassign yang tak terpasang tidak error.

**Filter**
- [ ] Filter label (multi-select) menyaring inbox (OR); mengosongkan filter mengembalikan list penuh.
- [ ] Filter aktif berlaku pada tab aktif dan reset ke page 1.

**Kelola Master**
- [ ] Buat label (nama + warna) → muncul di daftar, picker, & filter.
- [ ] Nama duplikat (case-insensitive) ditolak dengan pesan jelas.
- [ ] `colorHex` invalid dicegah di client; sukses tersimpan ternormalisasi `#rrggbb`.
- [ ] Ubah nama/warna mencerminkan ke semua percakapan (karena master).
- [ ] Hapus label (dengan konfirmasi) menghilangkan label dari semua percakapan (chip lama hilang setelah refresh).

**Umum**
- [ ] Semua aksi scoped ke workspace aktif; label workspace lain tak tampak.
- [ ] Semua sukses/gagal memunculkan toast; tombol ter-disable saat in-flight.

---

## 17. Open Question — RESOLVED

| ID | Pertanyaan | Keputusan |
|---|---|---|
| OQ-1 | Di mana master label dikelola? | **RESOLVED:** modal **"Kelola Label"** dari header `ChatList` (bukan halaman/menu Pengaturan baru). Ringan & dekat konteks Chat. |
| OQ-2 | Entry point assign label? | **RESOLVED:** tombol **"+ Label"** di header `ChatDetail` percakapan aktif → dropdown checkbox. (Tanpa aksi per-baris di list untuk MVP.) |
| OQ-3 | Metode input warna? | **RESOLVED:** **palet swatch preset + input hex opsional**, validasi `#RRGGBB` di client, kirim ternormalisasi `#rrggbb`. |
| OQ-4 | Semantik filter multi-label? | **RESOLVED:** **OR** (ikut backend FR-16); kontrol `MultipleSelectCustom` di panel filter existing. |
| OQ-5 | Sinkronisasi real-time label lintas agen? | **RESOLVED:** **di luar MVP** — optimistic update lokal + tersinkron saat refetch/buka ulang detail. |
| OQ-6 | Role yang boleh kelola/assign? | **RESOLVED:** **semua role** (OWNER/ADMIN/CUSTOMER_SERVICE); tanpa gating tambahan (ikut backend). |
| OQ-7 | Bulk assign ke banyak percakapan? | **RESOLVED:** **tidak** di MVP — assign per percakapan aktif. |
| OQ-8 | Store untuk label? | **RESOLVED:** `labelStore` baru di modul chat (CRUD master + assign); `chatStore` hanya menambah field `labels` + param `labelIds`. |

---

*PRD ini adalah dasar implementasi FE Conversation Label. Seluruh endpoint sudah tersedia di backend (Fase 1–6, terverifikasi). Seluruh Open Question telah RESOLVED. Turunan teknis ada di [TDD Frontend](../tdd/conversation-label-frontend.md).*
