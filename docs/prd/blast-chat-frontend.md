# PRD — Blast Chat (Frontend / Dashboard)

| Field | Value |
|---|---|
| Feature | Blast Chat — implementasi UI di Saktiform Dashboard |
| Modul | **Campaign** (mengisi placeholder sub-menu `Campaign` di grup **Chat**, route `/campaign`) |
| Dokumen terkait | [PRD Backend](./blast-chat.md), [TDD Backend](../tdd/blast-chat.md), [FE Project Overview](../frontend/project-overview.md), [FE Business Modules](../frontend/business-modules.md), [FE Component Inventory](../frontend/component-inventory.md) |
| Stack | Vue 3 (`<script setup>`, Composition API), TypeScript, Vite, Pinia, Axios, STOMP/SockJS, SCSS |
| Status | Ready for Implementation |
| Scope | Frontend-only (backend sudah selesai Fase 0–7) |
| Last updated | 2026-07-01 |
| Target pembaca | Frontend Developer, QA, Product |

> PRD ini mendefinisikan kebutuhan UI/UX modul **Campaign (Blast Chat)** pada dashboard, dipetakan ke REST API backend yang **sudah diimplementasikan**. Mengikuti konvensi dashboard existing (module-based, Pinia store, helper `apiConfig/method.ts`, komponen `common/`, `alertStore`, `headerContentStore`, `tableHelper`). Tidak berisi kode final.

---

## Daftar Isi

1. [Latar Belakang & Tujuan](#1-latar-belakang--tujuan)
2. [Scope](#2-scope)
3. [Penempatan di Aplikasi & Hak Akses](#3-penempatan-di-aplikasi--hak-akses)
4. [Arsitektur Frontend](#4-arsitektur-frontend)
5. [Peta Layar & Navigasi](#5-peta-layar--navigasi)
6. [Detail Layar](#6-detail-layar)
7. [State Management (campaignStore)](#7-state-management-campaignstore)
8. [Kontrak API (mapping)](#8-kontrak-api-mapping)
9. [Real-time, Polling & Integrasi Chat Room](#9-real-time-polling--integrasi-chat-room)
10. [Komponen (reuse & baru)](#10-komponen-reuse--baru)
11. [Status, Badge & Warna](#11-status-badge--warna)
12. [Validasi & Error Handling](#12-validasi--error-handling)
13. [State Kosong / Loading / Error](#13-state-kosong--loading--error)
14. [Format, i18n & Responsive](#14-format-i18n--responsive)
15. [Non-Functional Requirement](#15-non-functional-requirement)
16. [Enum Reference](#16-enum-reference)
17. [Acceptance Criteria](#17-acceptance-criteria)
18. [Open Question](#18-open-question)

---

## 1. Latar Belakang & Tujuan

Backend Blast Chat sudah lengkap (upload Excel → analisis kontak → campaign → worker kirim WhatsApp massal → attach ke Chat Room → monitoring → report → retry). Frontend perlu menyediakan antarmuka bagi pengguna untuk **membuat, menjalankan, memantau, dan menindaklanjuti** campaign blast dari dashboard.

**Tujuan produk:**
- Alur pembuatan campaign yang terpandu (wizard) dan sulit salah.
- Visibilitas real-ish-time atas progres pengiriman (poll progress) + hasil per-recipient.
- Aksi kontrol (Start/Pause/Resume/Cancel/Retry) dan unduh Report (Excel) yang jelas.
- Konsisten dengan look-and-feel dashboard existing (komponen `common/`, pola tabel/pagination, toast alert).

**Tujuan teknis:**
- Reuse penuh infrastruktur FE existing: `apiConfig/method.ts`, `tableHelper`, `alertStore`, `headerContentStore`, komponen `common/`, `authStore` (workspace & role).
- Modul terisolasi di `src/modules/campaign/` + halaman file-based di `src/pages/campaign/`.
- Tidak menyentuh modul Chat/Inbox existing (integrasi hanya observasional: pesan blast & balasan muncul otomatis di Chat Room via WebSocket yang sudah ada).

---

## 2. Scope

### 2.1 Included (MVP FE)
- **List Campaign** — tabel paginated + search + filter status.
- **Wizard Buat Campaign** (`StepperCustom`): Upload Excel → Analisis (summary) → Pilih Target → Sumber Pesan (Template/Custom) → Review → Start.
- **Detail Campaign** — ringkasan + progress bar + breakdown status + timeline waktu (created/started/finished).
- **Daftar Recipient** — paginated, filter per status pesan (mis. lihat semua `FAILED`), menampilkan balasan pertama.
- **Aksi**: Start, Pause, Resume, Cancel (dengan konfirmasi), Retry semua failed.
- **Unduh Report** Excel (`GET /report`, blob download).
- **Unduh Template Excel** upload.
- Poll progress otomatis saat campaign `QUEUED`/`RUNNING`.

### 2.2 Not Included (MVP FE)
- Scheduled blast UI (kolom `scheduled_at` backend disiapkan; UI menyusul).
- Segmentasi target lanjutan (Tag/Segment/Product).
- Dashboard analytics agregat lintas campaign / chart tren.
- Pemilihan multi-device/session (pakai default WABA workspace).
- Editing campaign setelah dibuat (campaign bersifat immutable setelah Create; hanya kontrol state).

### 2.3 Out of Scope
- Perubahan modul Chat/Inbox, Template Chat, Pengaturan.
- Perubahan backend.

---

## 3. Penempatan di Aplikasi & Hak Akses

### 3.1 Menu & Route
- Grup sidebar **Chat** sudah memiliki sub-menu: **Inbox** (`/chat`), **Template Chat** (`/template-chat`), **Campaign** (`/campaign`).
- **Campaign** saat ini placeholder (`src/pages/campaign/index.vue`) → diisi oleh modul ini.
- Route:
  | Route | Halaman | Keterangan |
  |---|---|---|
  | `/campaign` | `pages/campaign/index.vue` | List campaign |
  | `/campaign/buat` | `pages/campaign/buat.vue` | Wizard buat campaign |
  | `/campaign/:id` | `pages/campaign/[id].vue` | Detail + monitoring + recipient + aksi |
- Layout: `dashboardLayout` (via `<route>` block `meta.layout`), `requiredAuth: 1`.
- Breadcrumb/judul via `useHeaderStore` (mis. "Campaign", "Buat Campaign", "Detail Campaign").

### 3.2 Hak Akses (role)
**Semua role** yang punya akses grup menu Chat (OWNER, ADMIN, CUSTOMER_SERVICE) boleh membuat & menjalankan campaign (OQ-1 resolved). Akuntabilitas ditangani backend: tiap campaign menyimpan `created_by`, dan Conversation hasil blast auto-assign ke pembuat — jadi **tidak ada gating role tambahan**.
- Tidak perlu `meta.rolePermission` khusus (ikut menu Chat existing). Tombol aksi tampil untuk semua role; cukup autentikasi + membership workspace.
- Semua request scoped ke `authStore.user.activeWorkspace.id` (query `workspaceId`).

---

## 4. Arsitektur Frontend

### 4.1 Struktur Modul
```
src/
├── pages/campaign/
│   ├── index.vue          # List (ganti placeholder)
│   ├── buat.vue           # Wizard create
│   └── [id].vue           # Detail + monitoring
└── modules/campaign/
    ├── components/
    │   ├── CampaignList.vue          # tabel + filter + search
    │   ├── CampaignWizard.vue        # StepperCustom container
    │   ├── steps/StepUpload.vue      # upload Excel + validasi client
    │   ├── steps/StepAnalisis.vue    # summary kartu (existing/new/invalid/duplicate)
    │   ├── steps/StepTarget.vue      # pilih ALL_VALID / EXISTING / NEW
    │   ├── steps/StepPesan.vue       # template vs custom + placeholder + media
    │   ├── steps/StepReview.vue      # preview + estimasi durasi + tombol Start
    │   ├── CampaignProgress.vue      # progress bar + breakdown counter
    │   ├── CampaignRecipients.vue    # tabel recipient + filter status + reply
    │   ├── CampaignActions.vue       # Start/Pause/Resume/Cancel/Retry/Report
    │   └── ModalConfirmAction.vue    # (atau reuse ConfirmModal)
    ├── store/campaignStore.ts        # Pinia store (state + actions)
    └── types.ts                      # tipe TS: Campaign, ImportSummary, ProgressDto, MessageItem, dst
```

### 4.2 API Layer
- Semua panggilan lewat helper `src/apiConfig/method.ts`:
  - `getData(url, params)` — GET.
  - `postData(url, body)` — POST JSON.
  - `uploadData(url, formData)` — POST multipart (upload Excel / media).
  - `downloadFile(url, filename)` — GET blob → trigger download (report & template).
  - `errorHelper(err)` → `{ success:false, message }` untuk toast.
- Axios interceptor menambahkan `Bearer <token>` otomatis. Respons `{ success, message, data }`.
- Pagination memakai pola Spring Data `Page` pada `data`: `content[]`, `totalElements`, `number` (0-indexed), `size`, `totalPages`. **Catatan:** parameter `page` dikirim **1-indexed** (backend mengonversi `page-1`).

### 4.3 Prinsip
- Store `campaignStore` memegang: list + pagination, wizard draft state, detail aktif, progress, recipient list, loading flags.
- Semua sukses/gagal aksi → `useAlertStore` toast (auto-hide 3s).
- Gunakan `tableHelper` untuk membentuk objek request tabel (page/limit/search/filter) konsisten modul lain.

---

## 5. Peta Layar & Navigasi

```
/campaign  (List)
   ├── [Buat Campaign] ────────────► /campaign/buat  (Wizard)
   │                                     └── selesai Start ──► /campaign/:id
   └── klik baris ─────────────────► /campaign/:id  (Detail)
                                         ├── tab Ringkasan (progress)
                                         ├── tab Recipient (filter status)
                                         └── aksi: Pause/Resume/Cancel/Retry/Unduh Report
```

**Alur utama pengguna (happy path):**
`/campaign` → Buat → upload `.xlsx` → tunggu analisis (poll) → lihat summary → pilih target → pilih/tulis pesan → review (preview + estimasi) → **Buat & Start** → redirect ke `/campaign/:id` → pantau progress (poll) → setelah selesai, unduh Report / retry failed.

---

## 6. Detail Layar

### 6.1 List Campaign (`/campaign`)
- **Komponen:** `CampaignList` → `TableCustom` + `PaginationCustom` + `InputCustom` (search) + `SelectCustom` (filter status) + `ButtonCustom` ("Buat Campaign").
- **Kolom:** Nama, Status (`Badge`), Total Recipient, Terkirim (`countSent`), Gagal (`countFailed`), Dibalas (`countReplied`), Dibuat (`createdAt`), Aksi (lihat detail).
- **Perilaku:** search by nama (debounce), filter status (enum CampaignStatus), pagination server-side. Klik baris → `/campaign/:id`.
- **API:** `GET /blast/campaign?workspaceId&page&limit&search&status`.
- **Empty state:** "Belum ada campaign. Buat campaign pertama Anda." + tombol Buat.

### 6.2 Wizard Buat Campaign (`/campaign/buat`)
Container `CampaignWizard` memakai `StepperCustom` (5 langkah). Draft disimpan di `campaignStore.draft`; tombol Lanjut/Kembali; tiap langkah punya validasi sebelum lanjut.

**Step 1 — Upload Excel (`StepUpload`)**
- `FileUpload` menerima `.xlsx`/`.xls`. Link "Unduh template" → `downloadFile('/blast/import/template-file', 'blast_template.xlsx')`.
- **Validasi client:** ekstensi `.xlsx/.xls`; ukuran ≤ **2 MB** (tampilkan error sebelum upload). Header kanonik: `phone_number`, `name`.
- Aksi Upload → `uploadData('/blast/import?workspaceId=…', formData)` → simpan `importId` + `totalUpload` ke draft. Status import awal `UPLOADED`.
- Setelah upload sukses → **otomatis lanjut ke Step 2** dan mulai polling analisis.

**Step 2 — Analisis (`StepAnalisis`)**
- Analisis berjalan **async di backend** setelah upload. FE **poll** `GET /blast/import/{importId}` tiap ~1,5s sampai `status = ANALYZED` (atau `FAILED`). Tampilkan loading "Menganalisis kontak…".
- Tombol "Analisis ulang" → `POST /blast/import/{importId}/analyze` (opsional, mis. jika stuck).
- **Tampilkan summary** (kartu `BaseHighlightCard` atau kartu ringkas): Total Upload, Total Valid, Existing, New, Invalid, Duplicate. (invariant: valid = existing + new).
- Opsional: link "Lihat detail" per kategori → panggil `GET /blast/import/{id}/contacts?category=INVALID&page&limit` dalam modal tabel (mis. cek nomor invalid).
- Guard: tidak bisa lanjut bila Total Valid = 0.

**Step 3 — Pilih Target (`StepTarget`)**
- `RadioButton`/`SelectCustom`: `ALL_VALID` (Existing + New), `EXISTING_ONLY`, `NEW_ONLY`.
- Tampilkan proyeksi jumlah recipient per pilihan (dari summary: all=valid, existing=existing, new=new).
- Guard: pilihan menghasilkan ≥1 recipient.

**Step 4 — Sumber Pesan (`StepPesan`)**
- Toggle **Template** vs **Custom**.
  - *Template:* `SelectCustom` daftar template dari `GET /template?workspaceId` (reuse endpoint chat template). Pilih `templateId`; tampilkan preview konten + media template (read-only).
  - *Custom:* `TextAreaCustom` untuk `content` (wajib). Placeholder yang didukung: `{{name}}`, `{{phone}}` — sediakan chip/hint "Sisipkan {{name}} / {{phone}}". Opsional media: `FileUpload` → unggah ke `POST /master/saktiform-media` (pola template-chat) → simpan URL sebagai `mediaLink`.
- Nama Campaign (`InputCustom`, wajib).
- Config lanjutan (`batchSize`/`delayMs`/`maxAttempts`) **disembunyikan** di MVP (OQ-7 resolved): selalu pakai default backend (kirim `config: null`).

**Step 5 — Review (`StepReview`)**
- **Preview & estimasi dihitung di client** (OQ-2 resolved): campaign belum dibuat pada tahap ini, jadi preview pesan = render sample `{{name}}`/`{{phone}}` memakai recipient pertama, dan estimasi durasi ≈ `jumlahRecipient × delayDefault (±1,5 dtk)` diformat manusiawi ("± 25 menit"). Endpoint `GET /review` server-side **tidak dipakai** di wizard (tetap tersedia untuk halaman detail bila perlu).
- Tampilkan: Nama, Jumlah Recipient, Preview pesan, Estimasi durasi.
- **Dua tombol** — Create terjadi di sini agar tidak ada campaign `DRAFT` yatim sebelum user memutuskan:
  - **"Buat & Start"** → `POST /blast/campaign` (body `CreateCampaignRequest`) → `POST /blast/campaign/{id}/start` → redirect `/campaign/:id`, toast "Campaign dijalankan".
  - **"Simpan sebagai Draft"** (OQ-3 resolved: ya) → `POST /blast/campaign` saja → redirect `/campaign/:id` (status `DRAFT`; detail menyediakan tombol Start).
- Setelah Create sukses, reset `campaignStore.draft`. Jika Create gagal (mis. import sudah `CONSUMED`), tetap di Step 5 + toast error.

> **Penting (BR-22 backend):** satu `import` = satu campaign. Jika Create gagal karena import sudah `CONSUMED` (HTTP 400 pesan "Import sudah dipakai"), tampilkan error jelas & arahkan buat upload baru.

### 6.3 Detail Campaign (`/campaign/:id`)
- **Header:** Nama, `Badge` status, tombol aksi kontekstual (`CampaignActions`).
- **Tab / Section:**
  - **Ringkasan** (`CampaignProgress`): progress bar `percentage`, kartu breakdown: Total, Waiting, Sending, Success, Failed, Replied, Skipped. Info: sumber pesan, target, media, dibuat/dimulai/selesai.
  - **Recipient** (`CampaignRecipients`): `TableCustom` + `PaginationCustom` + `SelectCustom` filter status pesan. Kolom: Phone, Nama, Status (`Badge`), Retry count, Error terakhir, Terkirim (`sentAt`), Balasan (`firstReplyMessage` + `repliedAt`). *(Tidak ada retry per-baris di MVP — OQ-5.)*
    - **Campaign `DRAFT`:** recipient (`blast_message`) baru dibuat saat **Start**, jadi `GET /messages` mengembalikan **kosong** untuk DRAFT (bukan error). Tampilkan empty state ramah, mis. *"Recipient akan tersedia setelah campaign dijalankan."* Untuk **pratinjau calon recipient sebelum Start**, gunakan endpoint staging `GET /blast/import/{importId}/contacts?category=…` (`fetchImportContacts`) — `total_recipient` pada DRAFT bersifat proyeksi.
- **API:** `GET /blast/campaign/{id}` (detail+progress awal), `GET /blast/campaign/{id}/progress` (poll), `GET /blast/campaign/{id}/messages?status&page&limit` (recipient).
- **Aksi (`CampaignActions`)** — tampil sesuai status (lihat state machine §11):
  - **Start** (DRAFT) → `POST /start`.
  - **Pause** (RUNNING) → `POST /pause`.
  - **Resume** (PAUSED) → `POST /resume`.
  - **Cancel** (DRAFT/QUEUED/RUNNING/PAUSED) → `ConfirmModal` "Batalkan campaign? Pesan yang belum terkirim akan di-skip." → `POST /cancel`.
  - **Retry Failed** (bila `countFailed`>0) → `ConfirmModal` "Retry semua pesan gagal?" → `POST /retry` **body kosong = semua failed** (OQ-5) → toast "N pesan di-retry" + refresh progress/recipient.
  - **Unduh Report** → `downloadFile('/blast/campaign/{id}/report?workspaceId=…', '<nama>_messages_<tgl>.xlsx')`.

---

## 7. State Management (campaignStore)

`src/modules/campaign/store/campaignStore.ts` (Pinia, `defineStore`).

**State (garis besar):**
- `list: CampaignListItem[]`, `listMeta: { page, limit, total, search, status }`, `loadingList`.
- `draft: { importId, fileName, totalUpload, summary, targetType, messageSource, templateId, content, mediaLink, name, config }`.
- `importStatus`, `analyzing` (untuk polling Step 2).
- `detail: CampaignDetail | null`, `progress: ProgressDto | null`, `polling: boolean`.
- `recipients: MessageItem[]`, `recipientMeta`, `recipientStatusFilter`, `loadingRecipients`.
- `actionLoading` (untuk disable tombol saat request).

**Actions (garis besar):**
- `fetchList(params)`, `fetchTemplateOptions()`.
- `uploadImport(file)`, `pollImport(importId)` / `analyzeImport(importId)`, `fetchImportContacts(importId, category, page)`.
- `createCampaign(payload)`, `startCampaign(id)`, `pause/resume/cancel(id)`, `retryAllFailed(id)`.
- `fetchDetail(id)`, `fetchProgress(id)`, `fetchRecipients(id, status, page)`, `downloadReport(id, filename)`, `downloadTemplate()`.
- `startProgressPolling(id)` / `stopProgressPolling()`.

Semua action menangani error via `errorHelper` + `alertStore`. Reset `draft` setelah campaign berhasil dibuat.

---

## 8. Kontrak API (mapping)

Base: `VITE_BASE_URL`. Semua terproteksi JWT (kecuali webhook — bukan urusan FE). Query `workspaceId` = `authStore.user.activeWorkspace.id`. Envelope `{ success, message, data }`.

| Aksi UI | Method | Endpoint | Store action |
|---|---|---|---|
| Unduh template Excel | GET | `/blast/import/template-file` | `downloadTemplate()` (blob) |
| Upload Excel | POST (multipart) | `/blast/import?workspaceId` | `uploadImport(file)` |
| Poll status/summary import | GET | `/blast/import/{importId}?workspaceId` | `pollImport(id)` |
| Analisis ulang (manual) | POST | `/blast/import/{importId}/analyze?workspaceId` | `analyzeImport(id)` |
| Lihat baris per kategori | GET | `/blast/import/{importId}/contacts?workspaceId&category&page&limit` | `fetchImportContacts(...)` |
| Daftar template pesan | GET | `/template?workspaceId` | `fetchTemplateOptions()` |
| Upload media custom | POST (multipart) | `/master/saktiform-media` | (reuse pola template-chat) |
| Buat campaign | POST | `/blast/campaign?workspaceId` | `createCampaign(payload)` |
| Review campaign *(tidak dipakai di wizard MVP)* | GET | `/blast/campaign/{id}/review?workspaceId` | `fetchReview(id)` |
| List campaign | GET | `/blast/campaign?workspaceId&page&limit&search&status` | `fetchList(params)` |
| Detail campaign | GET | `/blast/campaign/{id}?workspaceId` | `fetchDetail(id)` |
| Progress (poll) | GET | `/blast/campaign/{id}/progress?workspaceId` | `fetchProgress(id)` |
| Daftar recipient | GET | `/blast/campaign/{id}/messages?workspaceId&status&page&limit` | `fetchRecipients(...)` |
| Start | POST | `/blast/campaign/{id}/start?workspaceId` | `startCampaign(id)` |
| Pause | POST | `/blast/campaign/{id}/pause?workspaceId` | `pause(id)` |
| Resume | POST | `/blast/campaign/{id}/resume?workspaceId` | `resume(id)` |
| Cancel | POST | `/blast/campaign/{id}/cancel?workspaceId` | `cancel(id)` |
| Retry semua failed | POST | `/blast/campaign/{id}/retry?workspaceId` (body kosong) | `retryAllFailed(id)` |
| Unduh Report | GET | `/blast/campaign/{id}/report?workspaceId` | `downloadReport(id, filename)` (blob) |

**Bentuk payload/response penting:**
- `CreateCampaignRequest` (POST body): `{ importId, name, targetType, messageSource, templateId?, content?, mediaLink?, deviceId?, config?:{batchSize,delayMs,maxAttempts} }`.
- `CampaignProgressDto`: `{ status, total, waiting, sending, success, failed, replied, skipped, percentage }`.
- `MessageListDto` (recipient): `{ id, phone, name, status, retryCount, lastError, sentAt, repliedAt, firstReplyMessage }`.
- `CampaignDetailDto`: `{ id, name, status, messageSource, targetType, mediaLink, totalRecipient, createdAt, startedAt, finishedAt, progress }`.
- List item: `{ id, name, status, totalRecipient, countSent, countFailed, countReplied, createdAt }`.

---

## 9. Real-time, Polling & Integrasi Chat Room

- **Progress campaign** dipantau via **polling** `GET /progress` (tidak ada topik WebSocket khusus blast). Mulai polling saat masuk `/campaign/:id` bila status ∈ {`QUEUED`,`RUNNING`}; interval ~3–5 detik; **berhenti** saat status terminal (`FINISHED`/`CANCELLED`/`FAILED`) atau saat komponen unmount. Refresh recipient list on-demand (tombol refresh / saat ganti filter).
- **Integrasi Chat Room (observasional, gratis):** setiap pesan blast yang terkirim ditempel ke Conversation dan memancarkan event WebSocket **yang sama** dengan chat biasa (`NEW_MESSAGE`, `UNASSIGNED/ASSIGNED_CONVERSATION_*`). Artinya, saat blast berjalan, **Inbox `/chat` yang sudah ada otomatis menampilkan** percakapan baru & auto-assign ke pembuat campaign — **tanpa perubahan FE**. Balasan customer muncul di thread seperti biasa. Pesan blast tampil dengan `pengirim = "BLAST-<username>"` (bisa dibedakan di bubble bila diinginkan — enhancement opsional).
- **Catatan performa:** saat blast besar, event chat bisa datang banyak (backend MVP emit penuh). Ini perilaku Inbox existing; tidak ada aksi khusus di modul Campaign.

---

## 10. Komponen (reuse & baru)

**Reuse (dari `components/common/`):**
`StepperCustom` (wizard), `TableCustom` + `PaginationCustom` + `TheadCustom`/`FieldCustom`, `InputCustom`, `TextAreaCustom`, `SelectCustom`, `RadioButton`, `FileUpload`, `Badge`, `ChipCustom` (placeholder hint), `ButtonCustom`, `ButtonFile`, `Modal`/`ConfirmModal`, `DropdownCustom`, `BaseHighlightCard` (kartu summary/breakdown), `TabsWrapper` (tab detail), `SwitchButton` (toggle template/custom).

**Baru (di `modules/campaign/components/`):** lihat §4.1. Tidak ada komponen `common/` baru yang wajib; bila progress bar belum ada di `common/`, buat kecil di modul (atau pakai `BaseHighlightCard` + bar SCSS sederhana).

---

## 11. Status, Badge & Warna

**CampaignStatus → Badge:**
| Status | Warna (saran) | Aksi tersedia |
|---|---|---|
| `DRAFT` | abu | Start, Cancel |
| `QUEUED` | biru | Cancel (poll progress) |
| `RUNNING` | biru/primary | Pause, Cancel (poll progress) |
| `PAUSED` | kuning | Resume, Cancel |
| `FINISHED` | hijau | Retry (jika failed>0), Unduh Report |
| `CANCELLED` | merah/abu | Unduh Report |
| `FAILED` | merah | Retry, Unduh Report |

**MessageStatus → Badge (recipient):** `WAITING` abu · `SENDING` biru · `SENT` biru muda · `DELIVERED` hijau muda · `READ` hijau · `REPLIED` hijau tua · `FAILED` merah · `SKIPPED` abu gelap.

Gunakan komponen `Badge`/`ChipCustom` existing dengan mapping warna terpusat (helper `statusColor(status)` di `types.ts`/util modul).

---

## 12. Validasi & Error Handling

**Validasi client (sebelum request):**
- Upload: ekstensi `.xlsx/.xls`, ukuran ≤ 2 MB.
- Create: nama wajib; target terpilih; jika Template → `templateId` wajib; jika Custom → `content` tidak kosong.
- Retry: minimal ada recipient FAILED.

**Error server** (envelope `success:false`) → tampilkan `message` via `alertStore` toast. Kasus khusus untuk pesan yang ramah:
- 400 "Import sudah dipakai" (BR-22) → arahkan upload baru.
- 400 transisi state (mis. start non-DRAFT) → toast + refresh detail.
- 401 → interceptor existing menghapus token (redirect saat navigasi berikutnya).

**Guard aksi:** tombol aksi di-disable selama request (`actionLoading`) untuk cegah double-submit (mis. double-start).

---

## 13. State Kosong / Loading / Error

- **List kosong** → empty state + CTA Buat.
- **Analisis (Step 2)** → skeleton/spinner "Menganalisis kontak…"; jika `FAILED` → pesan + tombol Analisis ulang / upload baru.
- **Detail** → skeleton saat load; recipient tabel punya loading & empty ("Belum ada recipient untuk filter ini").
- **Report** → tombol menunjukkan loading saat generate/download; toast bila gagal.

---

## 14. Format, i18n & Responsive
- Bahasa **Indonesia** (konsisten dashboard). Label enum di-Indonesiakan di UI (mis. `RUNNING`→"Berjalan", `FAILED`→"Gagal") via map; nilai enum tetap dikirim apa adanya ke API.
- Tanggal via `functions/moment.ts` (locale ID). Angka via `functions/delimiter.ts`. Nomor telepon via `functions/formater.ts`.
- Estimasi durasi: format manusiawi ("± 25 menit", "± 4 jam") dari `estimatedDurationSeconds`.
- Responsive: minimal usable di layar kecil (tabel horizontal scroll; wizard satu kolom). Mengikuti keterbatasan responsive existing.

---

## 15. Non-Functional Requirement
- **Polling hemat:** hanya saat status non-terminal & tab detail aktif; hentikan on unmount (hindari kebocoran interval — perhatikan catatan singleton di FE existing).
- **Pagination server-side** untuk list & recipient (jangan load semua — recipient bisa puluhan ribu).
- **Upload:** batasi 2 MB di client sebelum kirim (hemat bandwidth & selaras backend).
- **Idempotensi UI:** disable tombol saat in-flight; setelah Start, jangan izinkan Start lagi.
- **Aksesibilitas dasar & konsistensi** dengan komponen `common/`.

---

## 16. Enum Reference
```
CampaignStatus : DRAFT, QUEUED, RUNNING, PAUSED, FINISHED, CANCELLED, FAILED
MessageStatus  : WAITING, SENDING, SENT, DELIVERED, READ, REPLIED, FAILED, SKIPPED
TargetType     : ALL_VALID, EXISTING_ONLY, NEW_ONLY
MessageSource  : TEMPLATE, CUSTOM
ContactCategory: EXISTING, NEW, INVALID, DUPLICATE   (staging import)
ImportStatus   : UPLOADED, ANALYZING, ANALYZED, CONSUMED, FAILED
```

---

## 17. Acceptance Criteria

**List**
- [ ] Menampilkan campaign workspace aktif (paginated), search nama, filter status berfungsi.
- [ ] Klik baris membuka detail; tombol Buat membuka wizard.

**Wizard**
- [ ] Upload menolak non-xlsx/>2MB di client dengan pesan jelas; sukses → dapat `importId`.
- [ ] Step Analisis poll hingga `ANALYZED` lalu menampilkan summary yang benar (valid = existing + new).
- [ ] Target menampilkan proyeksi recipient; tidak bisa lanjut bila 0.
- [ ] Template menampilkan daftar template workspace; Custom memvalidasi content & mendukung `{{name}}`/`{{phone}}`.
- [ ] Review menampilkan preview + estimasi; "Buat & Start" membuat + men-start campaign lalu redirect ke detail.
- [ ] Import yang sudah `CONSUMED` menghasilkan pesan error yang dipahami.

**Detail & Monitoring**
- [ ] Progress bar & breakdown akurat; polling berjalan saat RUNNING/QUEUED dan berhenti saat terminal/unmount.
- [ ] Recipient list paginated + filter status; balasan pertama tampil pada baris REPLIED.
- [ ] Pause/Resume/Cancel/Retry muncul sesuai status, dengan konfirmasi untuk Cancel/Retry, dan memperbarui UI.
- [ ] Unduh Report mengunduh `.xlsx` dengan nama file dari server; unduh Template berfungsi.

**Integrasi**
- [ ] Saat campaign berjalan, Inbox `/chat` menampilkan percakapan blast & balasan tanpa perubahan (verifikasi manual).

---

## 18. Open Question — RESOLVED

| ID | Pertanyaan | Keputusan |
|---|---|---|
| OQ-1 | Role yang boleh membuat/menjalankan blast? | **RESOLVED:** **semua role** boleh; akuntabilitas via `created_by` (sudah ditangani backend). Tanpa gating role tambahan (§3.2). |
| OQ-2 | Waktu Create campaign & sumber preview | **RESOLVED:** Create terjadi di Step 5 (saat "Buat & Start" / "Simpan Draft"); **preview & estimasi dihitung di client**, `GET /review` tidak dipakai di wizard (§6.2 Step 5). |
| OQ-3 | Perlukah "Simpan sebagai Draft"? | **RESOLVED: Ya** — tombol "Simpan sebagai Draft" (Create tanpa Start); detail menyediakan Start untuk `DRAFT`. |
| OQ-4 | Bedakan visual bubble pesan blast di Inbox? | **RESOLVED:** tampil sebagai **pesan biasa** (tanpa styling khusus). |
| OQ-5 | Retry per-pesan atau semua failed? | **RESOLVED:** **Retry semua failed** saja (body `POST /retry` kosong). Tidak ada retry per-baris. |
| OQ-6 | Interval polling progress | **RESOLVED:** **3–5 detik** saat status `QUEUED`/`RUNNING`, berhenti saat terminal/unmount. |
| OQ-7 | Config lanjutan di wizard | **RESOLVED:** **disembunyikan**; selalu pakai default backend (`config: null`). |

---

*PRD ini adalah dasar implementasi FE modul Campaign (Blast). Seluruh endpoint sudah tersedia di backend (Fase 0–7). Seluruh Open Question telah RESOLVED.*
