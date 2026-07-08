# PRD — Conversation Label

| Field | Value |
|---|---|
| Feature name | Conversation Label |
| Component | Modul baru `label` (entity, repository, model, service, controller) + integrasi `Conversation`/`ConversationService` |
| Status | Ready for TDD |
| Scope | Per-workspace; backend-only |
| Author | — |
| Last updated | 2026-07-08 |
| Target pembaca | Backend Developer, Reviewer, QA, Frontend |

> PRD ini mendefinisikan fitur **Label** untuk menandai (tag) sebuah conversation. Label bersifat **master/reusable** (satu label dapat dipakai di banyak conversation), berupa **teks + kode warna hex**, dan **scope-nya terikat pada workspace**. Satu conversation dapat memiliki **lebih dari satu** label (many-to-many). Seluruh Open Question sudah **RESOLVED** (lihat §12) dan tercermin di bagian-bagian di bawah.

---

## 1. Background

Platform ini adalah conversational commerce multi-tenant berbasis WhatsApp. Percakapan pelanggan direpresentasikan oleh entity `Conversation` (tabel `conversation`, id `UUID`), yang **selalu terikat pada satu workspace** melalui contact/relasi workspace. Conversation diakses agen/admin lewat:

```
GET /chat/conversation/assigned    → ConversationService.getAssignedChat(...)
GET /chat/conversation/unassigned  → ConversationService.getUnassignedChat(...)
GET /chat/conversation/detail      → ConversationService.getConversationDetail(...)
```

Response list saat ini memakai interface projection `ConversationDto` (id, contactName, lastMessage, status, chatStatus, unreadMessageCount, dsb.). Saat ini **belum ada** konsep label/tag di seluruh codebase.

Konvensi codebase yang relevan (diwarisi oleh fitur ini):

- Semua resource **scoped ke workspace** via kolom/parameter `idWorkspace`/`workspaceId` (tidak ada thread-local tenant context).
- Entity: Lombok `@Getter @Setter @Entity @Table(name="…")`; id `UUID` (`GenerationType.UUID`) atau `Long` (`GenerationType.IDENTITY`); audit `Instant createdAt/updatedAt` **di-set manual di service**.
- Controller mengembalikan `RestResponse<T>` (`success`, `message`, `data`); error tervalidasi lewat `ErrorResponse`/`ErrorDto`.
- Format timestamp di layer DTO: `yyyy-MM-dd HH:mm`, zona `Asia/Jakarta`.
- Skema DB auto-update via Hibernate `ddl-auto=update` (penambahan tabel/kolom bersifat additive & aman).

---

## 2. Problem Statement

Agen/admin butuh cara untuk **mengkategorikan dan memfilter** percakapan (mis. "Prospek", "Komplain", "Follow-up", "VIP") secara visual dan konsisten. Saat ini tidak ada mekanisme untuk:

1. Membuat kumpulan label standar yang bisa dipakai ulang lintas conversation di dalam satu workspace.
2. Menempelkan satu atau lebih label ke sebuah conversation.
3. Menampilkan label pada daftar/detail conversation dan memfilter berdasarkan label.

Label harus **masterized** (dibuat sekali, dipakai berkali-kali) agar warna & penamaan konsisten, dan **terisolasi per workspace** agar tenant tidak saling melihat label satu sama lain.

---

## 3. Goals

- Menyediakan **master label** per workspace: teks (nama) + kode warna hex.
- CRUD master label (create, list, update, delete) dengan isolasi workspace.
- **Assign / unassign** satu atau banyak label ke sebuah conversation (relasi many-to-many).
- Menampilkan label yang terpasang pada **response list & detail conversation**.
- Memungkinkan **filter daftar conversation berdasarkan label**.

### Non-Goals

- Label untuk entity selain `Conversation` (mis. Order, Contact) — di luar scope.
- Hierarki/nested label, aturan otomatis (auto-labelling oleh bot/rule engine).
- Frontend/UI (PRD ini backend-only; UI dibahas terpisah bila diperlukan).
- Sharing label lintas workspace.

---

## 4. Scope

### Included

- Entity master **`ConversationLabel`** (workspace-scoped): `name`, `colorHex`.
- Entity join **`ConversationLabelLink`** (many-to-many: conversation ↔ label).
- Repository + Service + Controller untuk master label & assignment.
- Integrasi baca: label ikut muncul di response `assigned`/`unassigned`/`detail`.
- Filter opsional `labelId` pada endpoint list conversation.

### Not Included

- Migrasi data (fitur baru, tidak ada data lama).
- Perubahan pada alur bot/RAG, order, atau pengiriman WhatsApp.

---

## 5. Functional Requirements

### 5.1 Master Label

| ID | Requirement |
|---|---|
| FR-1 | Sistem MUST dapat membuat master label dengan `name` (teks, wajib) dan `colorHex` (kode warna, wajib), terikat pada satu `workspaceId`. Semua role (SUPERADMIN/ADMIN/AGENT) MUST diizinkan membuat, meng-update, dan menghapus master label. |
| FR-2 | `name` label MUST unik per workspace (case-insensitive). Duplikat MUST ditolak dengan **400/409** dan pesan jelas. Unik ditegakkan lewat **unique index `lower(name)`** yang dibuat post-startup (pola `BlastSchemaInitializer`). |
| FR-3 | `colorHex` MUST divalidasi sebagai kode warna hex `#RRGGBB` (**6 digit** heksadesimal, prefix `#` opsional pada input). Format `#RGB`/`#RRGGBBAA` MUST ditolak. Nilai invalid MUST ditolak **400**. Nilai MUST dinormalkan ke `#rrggbb` (lowercase, dengan prefix `#`) sebelum disimpan. |
| FR-4 | Sistem MUST dapat me-list seluruh master label milik satu workspace (untuk picker & filter). |
| FR-5 | Sistem MUST dapat meng-update `name` dan/atau `colorHex` sebuah master label (dalam workspace yang sama). Update `name` tetap tunduk aturan unik (FR-2). |
| FR-6 | Sistem MUST dapat menghapus sebuah master label. Saat dihapus, seluruh assignment label tsb ke conversation MUST ikut terhapus (unassign otomatis / cascade). |
| FR-7 | Seluruh operasi master label MUST tervalidasi kepemilikan workspace; label workspace lain MUST tidak dapat dibaca/diubah/dihapus (**404** bila tidak ditemukan dalam workspace). |

### 5.2 Assignment ke Conversation

| ID | Requirement |
|---|---|
| FR-8 | Sistem MUST dapat menempelkan (assign) satu **atau lebih** label ke sebuah conversation dalam satu request. |
| FR-9 | Sebuah conversation MUST dapat memiliki banyak label (many-to-many); sebuah label MUST dapat dipakai di banyak conversation. |
| FR-10 | Assignment MUST idempotent: menempelkan label yang sudah terpasang MUST tidak membuat duplikat (tidak error, atau di-skip). |
| FR-11 | Assignment MUST bersifat **all-or-nothing**: bila salah satu `labelId` tidak dikenal atau bukan milik workspace conversation tsb, **seluruh** request MUST ditolak (**400**) dan tidak ada label yang ter-assign. |
| FR-12 | Sistem MUST dapat melepas (unassign) sebuah label dari sebuah conversation. Unassign label yang tidak terpasang MUST **idempotent**: kembalikan **200** tanpa efek (no-op), bukan error. |
| FR-13 | Sistem MUST dapat me-list label yang terpasang pada sebuah conversation. |

### 5.3 Integrasi Read & Filter

| ID | Requirement |
|---|---|
| FR-14 | Response list conversation (`assigned`, `unassigned`) MUST menyertakan daftar label terpasang tiap conversation (`id`, `name`, `colorHex`). |
| FR-15 | Response detail conversation MUST menyertakan daftar label terpasang. |
| FR-16 | Endpoint list conversation MUST menerima parameter filter opsional `labelId` (satu atau banyak). Bila lebih dari satu `labelId` dikirim, semantiknya **OR** (conversation yang memiliki **salah satu** label tsb ikut terjaring). Bila tidak dikirim, perilaku list tidak berubah (backward compatible). |
| FR-17 | `colorHex` pada seluruh response MUST dikembalikan dalam format konsisten (`#RRGGBB`). |

---

## 6. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | Isolasi tenant: seluruh query MUST difilter `id_workspace` mengikuti konvensi codebase. |
| NFR-2 | Response tetap memakai envelope `RestResponse<T>`; error mengikuti pola existing (`badRequest` + message / `ErrorResponse`). |
| NFR-3 | Penambahan label ke response list MUST tidak menimbulkan N+1 query yang signifikan (mis. batch-fetch label per halaman conversation). |
| NFR-4 | Skema additive (tabel baru); tidak ada perubahan destruktif pada tabel existing. Aman dengan `ddl-auto=update`. |
| NFR-5 | Backward compatible: klien lama yang tidak mengirim `labelId` dan tidak membaca field label tetap berfungsi. |
| NFR-6 | Timestamp (bila diekspos) memakai format `yyyy-MM-dd HH:mm`, zona `Asia/Jakarta`. |

---

## 7. Data Model (conceptual)

Dua tabel baru, keduanya di-generate Hibernate dari anotasi entity.

### 7.1 `conversation_label` (master)

| Kolom | Tipe | Keterangan |
|---|---|---|
| `id` | `bigint` (IDENTITY) | PK |
| `id_workspace` | `bigint` | scope tenant (wajib) |
| `name` | `varchar` | teks label (wajib) |
| `color_hex` | `varchar(7)` | kode warna `#RRGGBB` (wajib) |
| `created_at` | `timestamp` | di-set di service |
| `updated_at` | `timestamp` | di-set di service |

- **Unique index**: `(id_workspace, lower(name))` untuk memaksa FR-2. Karena Hibernate tidak menghasilkan functional index, index ini dibuat **post-startup** oleh komponen `ApplicationReadyEvent` (pola `BlastSchemaInitializer`) via `CREATE UNIQUE INDEX IF NOT EXISTS … ON conversation_label (id_workspace, lower(name))` (idempotent).
- Index: `(id_workspace)`.

### 7.2 `conversation_label_link` (join many-to-many)

| Kolom | Tipe | Keterangan |
|---|---|---|
| `id` | `bigint` (IDENTITY) | PK |
| `conversation_id` | `uuid` | FK ke `conversation.id` |
| `label_id` | `bigint` | FK ke `conversation_label.id` |
| `id_workspace` | `bigint` | denormalized untuk filter cepat & guard tenant |
| `created_at` | `timestamp` | kapan di-assign |

- **Unique constraint**: `(conversation_id, label_id)` untuk memaksa idempotency (FR-10).
- Index: `(label_id)` (filter by label / cascade delete), `(conversation_id)` (list label per conversation).
- Penghapusan master label (FR-6): cascade **dikelola di service** — `ConversationLabelService.delete()` menjalankan `DELETE FROM conversation_label_link WHERE label_id = :id` lebih dulu, lalu menghapus baris master. Tidak memakai FK fisik `ON DELETE CASCADE` (selaras pendekatan dual-field/logikal codebase).

> Alternatif desain (ditolak untuk MVP): kolom array/JSON label di tabel `conversation`. Ditolak karena menyulitkan filter/join, konsistensi warna, dan aturan unik master. Model master + join lebih selaras konvensi relasional codebase.

---

## 8. API Requirements (high level)

Seluruh endpoint mengembalikan `RestResponse<T>`. Base path mengikuti konvensi chat existing (`/chat/...`) — final path dikonfirmasi saat TDD (OQ-5).

### 8.1 Master Label

| Method & Path | Body / Param | Keterangan |
|---|---|---|
| `POST /chat/label?workspaceId=` | `{ "name": "...", "colorHex": "#RRGGBB" }` | Buat master label. |
| `GET /chat/label?workspaceId=` | — | List seluruh master label workspace. |
| `PUT /chat/label/{labelId}?workspaceId=` | `{ "name": "...", "colorHex": "..." }` | Update label. |
| `DELETE /chat/label/{labelId}?workspaceId=` | — | Hapus label + unassign semua. |

### 8.2 Assignment

| Method & Path | Body / Param | Keterangan |
|---|---|---|
| `POST /chat/conversation/{conversationId}/label?workspaceId=` | `{ "labelIds": [1, 2, 3] }` | Assign satu/banyak label (idempotent). |
| `DELETE /chat/conversation/{conversationId}/label/{labelId}?workspaceId=` | — | Unassign satu label. |
| `GET /chat/conversation/{conversationId}/label?workspaceId=` | — | List label pada conversation. |

### 8.3 Integrasi List/Detail

- `GET /chat/conversation/assigned` & `/unassigned` mendapat parameter opsional `labelId` (repeatable, mis. `?labelId=1&labelId=2`).
- Response item conversation memuat array `labels`:
  ```json
  {
    "id": "…uuid…",
    "contactName": "…",
    "lastMessage": "…",
    "labels": [
      { "id": 1, "name": "Prospek", "colorHex": "#22C55E" },
      { "id": 2, "name": "VIP",     "colorHex": "#F59E0B" }
    ]
  }
  ```
- `GET /chat/conversation/detail` juga memuat `labels` dengan bentuk sama.

Contoh request create label:
```json
{ "name": "Komplain", "colorHex": "#EF4444" }
```

---

## 9. Affected Components

| Layer | Element | Change |
|---|---|---|
| Entity | `ConversationLabel` (baru) | Tabel `conversation_label`. |
| Entity | `ConversationLabelLink` (baru) | Tabel `conversation_label_link` (join). |
| Repository | `ConversationLabelRepository`, `ConversationLabelLinkRepository` (baru) | CRUD + query filter/batch. |
| Model | `LabelRequest`, `AssignLabelRequest`, `LabelDto` (baru) | Request/response DTO + validasi hex. |
| Service | `ConversationLabelService` (baru) | CRUD master, assign/unassign, guard workspace. |
| Service | `ConversationService` (ubah) | Sertakan `labels` di list/detail; dukung filter `labelId`; hindari N+1 (batch fetch). |
| Model | `ConversationDto` (ubah/tambah) | Ekspos `labels`. Karena projection interface saat ini, mungkin butuh penambahan langkah hydrate label setelah query (lihat catatan). |
| Controller | `ConversationLabelController` (baru) | Endpoint §8 di bawah base `/chat/label` & `/chat/conversation/{id}/label`. |
| Config | `LabelSchemaInitializer` (baru) | Buat unique index `lower(name)` post-startup (`ApplicationReadyEvent`), pola `BlastSchemaInitializer`. |
| Repository | `ConversationRepository` (ubah) | Query list menerima filter `labelId` (join ke link). |

> **Catatan `ConversationDto`:** DTO list saat ini adalah *interface projection* native. Menyisipkan array label ke projection tidak natural. Pendekatan yang disarankan: setelah mengambil halaman conversation, ambil label untuk seluruh `conversationId` pada halaman itu dalam **satu query batch** (`WHERE conversation_id IN (...)`), lalu gabungkan ke DTO respons (bungkus projection dengan DTO baru yang memuat `labels`). Ini menghindari N+1 (NFR-3).

---

## 10. Acceptance Criteria

- Diberikan request create label dengan `name` unik dan `colorHex` valid, maka label tersimpan di workspace tsb dan muncul di `GET /chat/label`.
- Diberikan create label dengan `name` yang sudah ada (case-insensitive) di workspace yang sama, maka operasi ditolak dengan pesan duplikat.
- Diberikan `colorHex` invalid (mis. `red`, `#12`, `#GGGGGG`), maka operasi ditolak **400**.
- Diberikan dua workspace berbeda, label workspace A tidak muncul/terakses dari workspace B.
- Diberikan assign `labelIds=[1,2]` ke sebuah conversation, maka `GET …/label` conversation itu mengembalikan label 1 & 2; mengulang assign label 1 tidak membuat duplikat.
- Diberikan unassign label 1, maka conversation hanya menyisakan label 2.
- Diberikan sebuah master label dihapus, maka label tsb hilang dari semua conversation yang sebelumnya memilikinya.
- Diberikan `GET /chat/conversation/assigned?labelId=1`, maka hanya conversation yang memiliki label 1 yang dikembalikan.
- Diberikan list/detail conversation, maka tiap conversation menyertakan array `labels` (kosong bila tidak ada) dengan `id`, `name`, `colorHex`.
- Klien lama yang tidak mengirim `labelId` menerima list yang perilakunya identik seperti sebelum fitur ini.

---

## 11. Edge Cases

- **`name` kosong / hanya spasi** → tolak **400** (wajib, trim dulu).
- **`colorHex` tanpa `#`** → diterima bila 6 digit hex valid; sistem menormalkan output ke `#rrggbb`.
- **`colorHex` 3-digit (`#RGB`) / 8-digit (`#RRGGBBAA`)** → **ditolak 400**; hanya 6-digit didukung.
- **Assign label ke conversation workspace berbeda** → tolak (isolasi tenant, FR-11).
- **Assign `labelIds` berisi id tak dikenal / bukan milik workspace** → **tolak seluruh request 400** (all-or-nothing); tidak ada label yang ter-assign.
- **Hapus label yang sedang dipakai banyak conversation** → tetap boleh; seluruh assignment ikut terhapus (FR-6, cascade di service).
- **Unassign label yang tidak terpasang** → **200 no-op** (idempotent), bukan error.
- **Rename label** → semua conversation yang memakainya otomatis mencerminkan nama baru (karena master, bukan snapshot).

---

## 12. Resolved Decisions

Seluruh Open Question sudah dijawab product owner dan tercermin di bagian-bagian di atas.

1. **Format & validasi `colorHex`** — **RESOLVED:** hanya `#RRGGBB` (6-digit), input prefix `#` opsional, disimpan ternormalisasi `#rrggbb` (lowercase). `#RGB`/`#RRGGBBAA` ditolak.
2. **Enforcement unik `name`** — **RESOLVED:** unique index `lower(name)` per workspace, dibuat **post-startup** (pola `BlastSchemaInitializer`) via `CREATE UNIQUE INDEX IF NOT EXISTS`.
3. **FK fisik vs. logikal** — **RESOLVED:** cascade **dikelola di service** (query `DELETE FROM conversation_label_link WHERE label_id = :id`), tanpa FK fisik `ON DELETE CASCADE`.
4. **Semantik unassign label yang tidak terpasang** — **RESOLVED:** **idempotent 200, no-op**.
5. **Path & lokasi endpoint** — **RESOLVED:** `/chat/label` (master) + `/chat/conversation/{conversationId}/label` (assignment).
6. **Assign parsial vs. all-or-nothing** — **RESOLVED:** **all-or-nothing** — bila ada `labelId` invalid, tolak seluruh request (**400**).
7. **Filter multi-label** — **RESOLVED:** **OR** (conversation yang memiliki salah satu label terjaring).
8. **Batas jumlah label per workspace / per conversation** — **RESOLVED:** **tanpa limit**.
9. **Otorisasi role** — **RESOLVED:** **semua role** (SUPERADMIN/ADMIN/AGENT) boleh CRUD master label maupun assign/unassign.
