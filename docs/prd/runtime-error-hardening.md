# PRD — Runtime Error Hardening (NPE & Exception Rawan)

| Field | Value |
|---|---|
| Feature name | Runtime Error Hardening |
| Component | Order (`OrderService`, `OrderEventListener`, `OrderOrchestrationService`), Chat/Webhook (`WhatsappMessageHandler`, `ConversationService`, `ChatService`), Bot (`BotOrchestratorService`, `BotDecisionServiceImpl`, `BotService`, `ContextBuilderService`), Produk (`ProdukService`), Workspace (`WorkspaceService`), Config (`AppConfigService`), `MessageConstructorHelper`, `MessageTemplateService` |
| Status | **Draft** — menunggu keputusan §12 |
| Scope | Backend-only; tanpa perubahan skema DB, tanpa perubahan kontrak API (kecuali pesan error menjadi lebih jelas) |
| Last updated | 2026-10-15 |

> PRD ini menindaklanjuti audit kode statis (pencarian pola + pembacaan konteks) atas ±23 ribu baris di `src/main/java`. Audit **tidak** menjalankan aplikasi dan **tidak** mencakup seluruh baris; daftar di §5 adalah temuan terverifikasi dari pembacaan kode, bukan daftar lengkap semua bug. Nomor baris mengacu pada working tree per 2026-10-15.

---

## 1. Background

Dalam sesi debugging 2026-10-15 muncul dua error produksi berturut-turut:

1. `POST /send/image` ditolak gowa (`multipart: NextPart: EOF`) — sudah diperbaiki (`WhatsappClientHelper` memakai `BufferingClientHttpRequestFactory`).
2. `NullPointerException` di `MediaHelper.saveMediaFromUrl` karena `mimeType` null dari webhook — sudah diperbaiki (kategori dari jenis pesan, alias field di `MediaContentDeserializer`).

Keduanya menunjukkan pola yang sama: kode mengasumsikan data eksternal/DB selalu lengkap. Audit lanjutan menemukan banyak titik serupa, beberapa di antaranya kemungkinan **sudah** memengaruhi user tanpa terlihat (error di thread `@Async` hanya masuk log).

## 2. Problem Statement

- Error pada jalur async (webhook, bot, follow-up order) **gagal diam-diam**: customer tidak dibalas, follow-up tidak terkirim, conversation tidak ter-update — tanpa notifikasi ke admin.
- Error pada jalur sinkron menghasilkan pesan teknis yang membingungkan (`No value present`, `Index 0 out of bounds`, pesan kosong), termasuk di halaman checkout publik.
- Ada bug logika yang menyamar sebagai error akses (perbandingan `Long` dengan `!=`).

## 3. Goals & Non-Goals

### Goals
- G-1: Menghilangkan seluruh temuan **Kritis** dan **Tinggi** (§5.1, §5.2).
- G-2: Data tidak ditemukan menghasilkan pesan yang jelas (Bahasa Indonesia), bukan pesan teknis.
- G-3: Jalur async (webhook, bot, follow-up) tidak lagi crash untuk data tidak lengkap; bila tetap gagal, ter-log dengan konteks (id conversation/order).
- G-4: Setiap perbaikan disertai unit test yang mereproduksi kondisi pemicu.

### Non-Goals
- Refactor arsitektur / penggantian pola exception global (mis. `@ControllerAdvice`) — lihat §12 Q4.
- Perubahan skema DB atau kontrak response API.
- Audit frontend.
- Perbaikan bug yang sudah punya PRD/tiket sendiri (bucket blast — PRD Chat Template Multiple Image §2.4-B).

---

## 4. Metode Klasifikasi

| Tingkat | Kriteria |
|---|---|
| **Kritis** | Terpicu oleh alur normal sehari-hari, dampak ke customer/agent, kemungkinan sudah terjadi di produksi. |
| **Tinggi** | Terpicu oleh data yang tidak lengkap tetapi wajar (workspace lama, master data kosong), dampak ke fitur inti. |
| **Sedang** | Terpicu oleh input tidak valid / konfigurasi salah; dampak berupa pesan error buruk. |
| **Logika** | Bukan exception, tetapi hasilnya salah. |

---

## 5. Temuan

### 5.1 Kritis

| ID | Lokasi | Pemicu | Dampak |
|---|---|---|---|
| K-1 | `OrderService.java:222`, `:340` — `order.getLastHandleBy() != handledBy.getId()` | Akun dengan `id > 127` (di luar cache `Long`) | Agent non-owner ditolak mengubah order yang ia tandai PAID sendiri: "Pesanan ini sudah diproses oleh &lt;dirinya&gt;". |
| K-2 | `OrderService.java:258-261` — `order.setIdContact(...)` lalu `if (contact.getId() != order.getIdContact())` | Selalu | Pengecekan dilakukan **setelah** assignment dan memakai `!=` → `idConversation` tidak pernah di-reset saat nomor WA order diganti; order tetap terhubung ke conversation lama. |
| K-3 | `ConversationService.getOrCreateByContact` (`:214-235`) tidak mengisi `botQuota`; dipanggil `OrderOrchestrationService:151` | Order dibuat dari dashboard sebelum customer chat | Saat customer membalas: NPE di `BotDecisionServiceImpl:27` (`getBotQuota() == 0`) → bot tidak pernah membalas; NPE di `ChatService.decreaseBotQuota:263`. |
| K-4 | `BotOrchestratorService:33` — `botService.isOrderMessage(chat.getPesan())` | Pesan masuk media tanpa caption (`pesan` null) | NPE di thread bot untuk setiap pesan tersebut (log noise; event lain di listener ikut gagal). |
| K-5 | `WhatsappMessageHandler.handleMessagePayload` `:102-120` | Jenis pesan selain teks/media (reaksi, lokasi, kontak, polling) bila dikirim gowa sebagai event message | `newCustChat` = `Chat` kosong tidak tersimpan → `lastMessageAt/lastMessage` conversation di-set null (urutan daftar chat rusak), event WebSocket dengan id null, `IncomingChatEvent(null)` → `findById(null)` error di bot. |
| K-6 | `WhatsappMessageHandler:59-60`, `:219-220` — `waba.getId()` | Webhook dari device tidak terdaftar / format nomor tidak cocok | NPE; webhook gagal. |
| K-7 | `OrderService.getConfirmOrder:815` — `.get(0)` | Customer mengirim teks konfirmasi order, tetapi tidak ada order cocok dalam 20 menit terakhir | `IndexOutOfBoundsException`; cabang `order == null` di `BotService:111` (balasan + eskalasi ke admin) **tidak pernah** tercapai → customer tidak dibalas. |
| K-8 | `ProdukService.getCheckoutProdukDetail:448` — `findByUrlCheckout(url).get(0)` | URL checkout salah / produk dihapus | Halaman checkout publik menampilkan "Index 0 out of bounds…"; `produk == null` sesudahnya dead code; `getIsDeleted()` unboxing null. |

### 5.2 Tinggi

| ID | Lokasi | Pemicu | Dampak |
|---|---|---|---|
| T-1 | `OrderEventListener.autoFollowup:59-78` | Workspace tanpa WABA (`workspace.getWaba()` null); gowa 5xx (exception tidak ditangkap); body response null | Follow-up/konfirmasi order tidak terkirim, diam-diam (async). |
| T-2 | `MessageTemplateService.getFollowUpText:123-126` | Template kategori `FOLLOWUP-*`/`CONFIRMATION-*` tidak ada (workspace lama) | NPE `template.getContent()` → T-1. |
| T-3 | `OrderService.updateOrder:254-255` | Kecamatan tanpa data ongkir (jalur create sudah punya guard di `:120-123`) | NPE saat edit order. |
| T-4 | `MessageConstructorHelper.buildOrderParams:158-222` | (a) `:220` cek `config` tetapi membaca `order.getConfigPembayaran()` (bisa null); (b) `:202` unboxing `getOngkosKirim()`/`getHarga()` null; (c) rantai `findById(...).get()` gudang/wilayah; (d) `findById(order.getIdAtributProduk())` dengan id null | Quick chat, follow-up, dan konteks bot gagal untuk order dengan data tidak lengkap. |
| T-5 | `OrderService.getDetailOrder:528+` | Order tidak ditemukan; order tanpa atribut | NPE / `IllegalArgumentException` saat membuka detail order. |
| T-6 | `OrderService.java:265-266` | `configPembayaran.getConfig()` null | NPE saat update order COD. |

### 5.3 Sedang

| ID | Lokasi | Pemicu | Dampak |
|---|---|---|---|
| S-1 | 22× `findById(...).get()` (mis. `AccountService:86`, `ConversationService:127,280`, `ProdukService:91,337,546,691,701`, `WorkspaceService:157,220,236`, `WhatsappInstanceService:99,106`, `MessageTemplateService:44,80,88`, `OrderService:825,831`, `DomainService:29`, `ChatMessageService:34`, `MessageConstructorHelper:158`) | ID tidak ditemukan | Response `No value present`. |
| S-2 | `AppConfigService.getConfig` — `findAppConfigByConfigNameContainingIgnoreCase` mengembalikan 1 entity | Dua nama config sama-sama mengandung kata kunci | `IncorrectResultSizeDataAccessException`. |
| S-3 | `Integer.valueOf(appConfigService.getConfig(...))` di `BotOrchestratorService:42`, `WhatsappMessageHandler:87`, `OrderEventListener:90` | Nilai config bukan angka | `NumberFormatException`; untuk `:87` seluruh pesan masuk dari kontak baru gagal. |
| S-4 | `WorkspaceService:58`, `:140` — `waba.getStatus().equals(...)` | Status WABA null | NPE saat simpan workspace. |
| S-5 | `ContextBuilderService:28` — `messages.get(0)` | Belum ada pesan teks customer | `IndexOutOfBoundsException` di bot. |

### 5.4 Bug Logika

| ID | Lokasi | Perilaku salah |
|---|---|---|
| L-1 | `WorkspaceService:135` — `checkWorkspace.getWabaId().equals(data.getWabaId())` | Workspace ditemukan berdasarkan `wabaId` yang sama → perbandingan selalu true → validasi "WABA sudah digunakan" tidak pernah aktif saat update; satu WABA bisa dipasang di dua workspace. |
| L-2 | `WhatsappMessageHandler:103` — `getUnreadMessageCount()==null ? 0 : n + 1` | Pesan pertama conversation baru tercatat 0 unread. |
| L-3 | `WhatsappMessageHandler:173` — `replyChat.getMedia() != null ? getPublicUrl(newCustChat.getMedia())` | Pratinjau pesan yang dibalas menampilkan media pesan baru, bukan media pesan asli. |
| L-4 | `MessageTemplateService:58` (`"QUICK_REPLY"`) vs `ConversationService.getQuickChat:198` (`"Quick Reply"`) | Template quick reply selalu masuk cabang isi placeholder order; bila tidak ada order aktif, placeholder tidak terisi. |

---

## 6. Functional Requirements

### 6.1 Order

| ID | Requirement | Temuan |
|---|---|---|
| FR-1 | Semua perbandingan ID boxed (`Long`, `Integer`, `UUID`) memakai `Objects.equals`. | K-1, K-2 |
| FR-2 | Pengecekan perubahan contact di `updateOrder` dilakukan **sebelum** `setIdContact`; bila contact berubah, `idConversation` di-reset `null`. | K-2 |
| FR-3 | `getConfirmOrder` mengembalikan `null` bila hasil pencarian kosong (bukan exception), sehingga cabang balasan + eskalasi admin di `BotService` berjalan. | K-7 |
| FR-4 | `updateOrder` memakai guard ongkir yang sama dengan jalur create (pesan error identik). | T-3 |
| FR-5 | `getDetailOrder` melempar error jelas `Pesanan tidak ditemukan` bila order null; atribut produk null ditangani tanpa exception (field atribut kosong). | T-5 |
| FR-6 | Konfigurasi pembayaran COD dengan `config` null diperlakukan sebagai "tanpa biaya COD tambahan". | T-6 |

### 6.2 Webhook & Conversation

| ID | Requirement | Temuan |
|---|---|---|
| FR-7 | Webhook dari device/WABA tidak terdaftar diabaikan dengan log `warn` (deviceId), tanpa exception. | K-6 |
| FR-8 | Pesan masuk dengan jenis yang tidak didukung diabaikan **sebelum** conversation diubah (tidak mengubah `lastMessage*`, unread, maupun publish event), dengan log `info` jenis pesan. | K-5 |
| FR-9 | `getOrCreateByContact` mengisi `botQuota` dari config `bot.default.quota` dan field default lain (`chatStatus`, `unreadMessageCount`, `isUnread`) konsisten dengan `WhatsappMessageHandler`. | K-3 |
| FR-10 | Bot memperlakukan `botQuota == null` sebagai quota default (data lama yang sudah terlanjur null) dan `handleByBot == null` sebagai `false`. | K-3 |
| FR-11 | Pesan pertama conversation baru tercatat `unreadMessageCount = 1`. | L-2 |
| FR-12 | Pratinjau pesan yang dibalas memakai media pesan asli. | L-3 |

### 6.3 Bot

| ID | Requirement | Temuan |
|---|---|---|
| FR-13 | `isOrderMessage(null)` mengembalikan `false`. | K-4 |
| FR-14 | `ContextBuilderService.build` dengan daftar pesan kosong mengembalikan konteks kosong / bot tidak membalas, tanpa exception. | S-5 |

### 6.4 Order follow-up & template

| ID | Requirement | Temuan |
|---|---|---|
| FR-15 | `autoFollowup` tidak crash bila workspace tanpa WABA, template follow-up tidak ada, atau gowa error: kondisi di-log `warn` dengan `orderId` dan event selesai normal. | T-1, T-2 |
| FR-16 | `buildOrderParams` toleran terhadap data tidak lengkap: field yang tidak tersedia diisi string kosong; `deskripsi_transfer_bank` membaca config yang sudah dicek; nilai numerik null dianggap 0. | T-4 |

### 6.5 Produk & Workspace & Config

| ID | Requirement | Temuan |
|---|---|---|
| FR-17 | Checkout publik dengan URL tidak valid / produk terhapus mengembalikan `Produk tidak ditemukan` (perilaku HTTP existing controller dipertahankan). | K-8 |
| FR-18 | `findById(...).get()` diganti `orElseThrow` dengan pesan `<Entitas> tidak ditemukan`. | S-1 |
| FR-19 | Pembacaan config memakai pencocokan nama **persis** (case-insensitive); nilai numerik yang tidak valid jatuh ke default yang terdokumentasi + log `warn`. | S-2, S-3 |
| FR-20 | Pengecekan status WABA null-safe (`"DISCONNECTED".equals(waba.getStatus())`). | S-4 |
| FR-21 | Validasi "WABA sudah digunakan workspace lain" saat update membandingkan **id workspace**, bukan `wabaId`. | L-1 |
| FR-22 | Kategori quick reply diseragamkan (lihat §12 Q3). | L-4 |

---

## 7. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | Tanpa perubahan skema DB dan kontrak response sukses. |
| NFR-2 | Setiap FR punya minimal satu unit test yang gagal sebelum perbaikan dan lulus sesudahnya. |
| NFR-3 | Log pada jalur async menyertakan id konteks (conversationId / orderId / deviceId) dan tidak mencetak stack trace untuk kondisi yang diharapkan (data tidak ada), hanya untuk kondisi tak terduga. |
| NFR-4 | Perubahan per area dapat dirilis terpisah (lihat §10). |

---

## 8. Acceptance Criteria (ringkas)

- **K-1:** akun dengan id ≥ 128 dapat mengubah order yang ia tandai PAID; akun lain tetap ditolak.
- **K-2:** mengubah nomor WA order ke kontak lain me-reset `idConversation`; nomor sama tidak me-reset.
- **K-3:** order dibuat dari dashboard → customer membalas → bot membalas sesuai aturan dan quota berkurang dari nilai default.
- **K-4:** gambar tanpa caption dari customer tidak menghasilkan error di log bot.
- **K-5:** reaksi/lokasi dari customer tidak mengubah `lastMessage`/`lastMessageAt` conversation.
- **K-6:** webhook dengan `deviceId` tidak dikenal → 200/diabaikan, log warn, tanpa stack trace.
- **K-7:** teks konfirmasi tanpa order cocok → customer menerima balasan "pesanan akan diproses" dan conversation dieskalasi ke admin.
- **K-8:** `GET` checkout dengan URL acak → pesan `Produk tidak ditemukan`.
- **T-1/T-2:** order di workspace tanpa template follow-up → tidak ada exception; log warn berisi `orderId`.
- **T-3:** edit order ke kecamatan tanpa ongkir → pesan error yang sama dengan jalur create.
- **S-1:** detail entity dengan id acak → pesan `<Entitas> tidak ditemukan`.
- **L-1:** update workspace A memakai WABA milik workspace B → ditolak.

---

## 9. Risiko

| ID | Risiko | Mitigasi |
|---|---|---|
| R-1 | Perubahan K-2 mengubah perilaku: order yang dipindah ke kontak lain kini terlepas dari conversation lama. | Memang perilaku yang dimaksud kode; konfirmasi §12 Q2. |
| R-2 | FR-8 (abaikan jenis pesan tak didukung) menyembunyikan pesan customer yang sebelumnya tercatat kosong. | Sebelumnya juga tidak tersimpan (Chat kosong tidak di-save); log info menjaga jejak. Alternatif simpan sebagai TEXT placeholder — §12 Q5. |
| R-3 | FR-19 pencocokan persis dapat memutus config yang selama ini "kebetulan" cocok lewat `contains`. | Inventaris nama config di DB produksi sebelum rilis. |
| R-4 | FR-10 menganggap `botQuota` null = default → conversation lama yang selama ini tidak dibalas bot akan mulai dibalas. | Diinginkan; umumkan ke admin. |
| R-5 | Audit tidak lengkap; bug serupa bisa tersisa. | §12 Q4 (penanganan global) + temuan baru ditambahkan ke PRD ini. |

---

## 10. Rencana Rilis

| Fase | Isi | Alasan |
|---|---|---|
| 1 | K-1, K-2, K-3 (+FR-10), K-7 | Dampak langsung ke agent/customer; perubahan kecil. |
| 2 | K-4, K-5, K-6, L-2, L-3 | Jalur webhook & bot. |
| 3 | K-8, T-1…T-6 | Checkout publik, follow-up, order. |
| 4 | S-1…S-5, L-1, L-4 | Kualitas pesan error & validasi. |

Setiap fase: unit test + verifikasi staging dengan device WA test.

---

## 11. Di Luar Scope (sudah ditangani / tiket lain)

| Item | Status |
|---|---|
| gowa menolak multipart chunked (`multipart: NextPart: EOF`) | Diperbaiki — `WhatsappClientHelper` buffering + `WhatsappClientHelperTest` |
| NPE `mimeType` null di `MediaHelper` + dokumen `application/*` ditolak + `file_name`/`filename` | Diperbaiki — `MediaHelperTest`, `MediaContentDeserializerTest` |
| Dugaan bug bucket blast dari template | PRD Chat Template Multiple Image §2.4-B (tiket terpisah) |

---

## 12. Open Questions (dengan rekomendasi default)

| # | Pertanyaan | Rekomendasi default |
|---|---|---|
| Q1 | Perlu cek data produksi untuk mengukur dampak K-1/K-3 (jumlah akun id ≥ 128, conversation `bot_quota IS NULL`) sebelum rilis? | **Ya** — query read-only, hasil dicatat di PRD; juga menentukan perlu/tidaknya backfill `bot_quota`. |
| Q2 | K-2: bila nomor WA order diganti ke kontak lain, order dilepas dari conversation lama? | **Ya** (sesuai maksud kode existing). |
| Q3 | L-4: kategori baku quick reply? | **`QUICK_REPLY`** (nilai yang disimpan), ubah pengecekan `getQuickChat`; data lama tidak perlu migrasi bila tidak ada baris `"Quick Reply"` (verifikasi di DB). |
| Q4 | Tambah `@RestControllerAdvice` global untuk memetakan `NoSuchElementException`/`IllegalArgumentException` → 400/404 dengan pesan rapi? | **Tidak di PRD ini** — controller existing sudah catch per endpoint; cukup perbaiki sumber error. Pertimbangkan PRD terpisah. |
| Q5 | K-5: pesan jenis tak didukung diabaikan atau disimpan sebagai placeholder (mis. TEXT "[Lokasi]")? | **Abaikan + log** di fase ini; placeholder sebagai enhancement terpisah. |
| Q6 | Backfill `bot_quota` null di DB, atau cukup fallback di kode (FR-10)? | **Fallback di kode** + backfill opsional bila Q1 menunjukkan jumlah signifikan. |
