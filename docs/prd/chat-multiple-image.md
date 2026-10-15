# PRD — Kirim Multiple Gambar pada Chat (`POST /send-message`)

| Field | Value |
|---|---|
| Feature name | Chat Multiple Image |
| Component | `POST /send-message` → `ChatController.sendMessage` → `ChatService.messageHandler` → `WhatsappClientHelper.sendImage` |
| Status | **Approved** — TDD: [TDD — Chat Multiple Image](../tdd/chat-multiple-image.md) |
| Scope | Per-conversation (workspace-scoped melalui `Conversation → Contact.idWorkspace`) |
| Dokumen terkait | [Impact Analysis](./chat-multiple-image-impact-analysis.md), [PRD Frontend](./chat-multiple-image-frontend.md), [TDD Frontend](../tdd/chat-multiple-image-frontend.md), [API Reference](../api-reference.md) §6.11, [FE API Mapping](../frontend/api-mapping.md) `POST /send-message`, [FE UI Flow](../frontend/ui-flow.md) (Chat detail panel), [PRD Chat Template mediaLink](./chat-template-media-link.md) |
| Author | — |
| Last updated | 2026-10-15 |

> PRD ini mendefinisikan kebutuhan agar agent dapat mengirim **lebih dari satu gambar dalam satu aksi kirim** dari Chat Room. **Belum ada perubahan kode** yang dilakukan berdasarkan dokumen ini. Rekomendasi default untuk setiap keputusan terbuka ditulis di §12 dan dipakai sebagai asumsi di bagian lain.

---

## 1. Background

### 1.1 Alur kirim pesan saat ini

```
FE ChatDetail
  ├── (opsional) chatStore.uploadChatFile(file) → POST /master/upload-file → public URL (bucket chat)
  └── chatStore.sendMessage({ conversationId, messageType, message, mediaLink, repliedMessageId })
        → POST /send-message  (SendMessageDto)
          → ChatService.messageHandler(data, username)   @Transactional
              ├── TEXT     → client.sendMessage(GoWaSendMessageRequest)   (+ reply_message_id)
              ├── IMAGE    → client.sendImage(deviceId, phone, caption=message, image_url=mediaLink)
              ├── VIDEO    → client.sendVideo(...)
              ├── DOCUMENT → client.sendFile(...)
              └── AUDIO    → client.sendAudio(...)
              → jika response.code == SUCCESS:
                  ├── simpan 1 baris Chat (message_id, type, pesan, media = extractPathFromPublicUrl(mediaLink))
                  ├── update Conversation.lastMessage / lastMessageAt / lastMessageType
                  ├── publish ChatAsyncEvent NEW_MESSAGE (ChatListDto)  → WebSocket chat room
                  └── publish ChatAsyncEvent ASSIGNED_CONVERSATION_UPDATED → WebSocket list
```

Karakteristik penting:

- **1 request = 1 pesan WhatsApp = 1 baris `chat`.** Entitas `Chat` hanya punya satu kolom `media` (relative storage path) dan satu `message_id` (ID pesan di provider go-whatsapp multi-device).
- `message_id` dipakai untuk lookup status/receipt dan reply (`ChatRepository.findByMessageId`, `ChatMessageService.findByWhatsappMessageId`, `repliedTo`).
- Endpoint upload (`POST /master/upload-file`) menerima **satu file per request** dan mengembalikan public URL (`storageService.getPublicUrl`). Batas multipart: `max-file-size=10MB`, `max-request-size=15MB`.
- Read path (`GET /conversation/message`, `ChatListDto`) mengembalikan **satu `mediaLink` per item**.
- Provider (`/send/image`) hanya menerima **satu `image_url` per panggilan** — tidak ada endpoint album.
- `BotService` juga membangun `SendMessageDto` (selalu `TEXT`, tanpa media).

### 1.2 Kondisi working tree saat ini

Ada perubahan lokal yang belum di-commit pada `SendMessageDto`:

```diff
-    String mediaLink;
+    List<String> mediaLink;
```

Perubahan ini **belum dibarengi** perubahan di `ChatService` (masih mengoper `data.getMediaLink()` sebagai `String` ke `sendImage/sendVideo/sendFile/sendAudio` dan ke `extractPathFromPublicUrl`), sehingga **project saat ini tidak akan compile**. Selain itu, FE existing masih mengirim `"mediaLink": "string"` — tanpa konfigurasi tambahan Jackson akan gagal deserialisasi (`MismatchedInputException`) → semua kirim media dari FE lama akan 400. PRD ini mengakomodasi arah perubahan tersebut dengan syarat backward compatible (lihat FR-2).

---

## 2. Problem Statement

Agent sering perlu mengirim beberapa foto sekaligus (katalog varian, bukti transfer, foto produk dari beberapa sisi). Saat ini agent harus upload & kirim satu per satu, yang lambat dan menghasilkan caption berulang. Backend belum menerima lebih dari satu media dalam satu request.

---

## 3. Goals

- Agent dapat memilih **N gambar (1..10)** dan mengirimnya dalam **satu request** `POST /send-message` dengan `messageType = IMAGE`.
- Setiap gambar dikirim ke WhatsApp sebagai **pesan gambar terpisah** (sesuai kemampuan provider) dengan urutan sesuai urutan di request.
- Setiap gambar tersimpan sebagai **satu baris `Chat`** dengan `message_id` sendiri, sehingga status/receipt, reply, dan read path existing tetap berfungsi tanpa perubahan skema.
- Caption (`message`) hanya disematkan pada **gambar pertama**.
- **Backward compatible**: FE lama yang mengirim `mediaLink` sebagai string tunggal, serta `BotService`, tetap berjalan.

### Non-Goals

- Album/grouping gambar di sisi WhatsApp penerima (provider tidak mendukung).
- Multiple media untuk `VIDEO`, `DOCUMENT`, `AUDIO` (lihat §12 Q2 — desain dibuat agar mudah diperluas).
- Endpoint upload batch (FE tetap memanggil `POST /master/upload-file` per file, boleh paralel).
- Grouping tampilan "album" di dashboard (FE boleh mengelompokkan berdasarkan pengirim + waktu, tapi bukan requirement backend).
- Perubahan skema tabel `chat`.
- Perubahan alur pesan masuk (webhook) — customer yang mengirim banyak gambar sudah tersimpan sebagai banyak baris `Chat`.

---

## 4. Scope

### Included
- `SendMessageDto.mediaLink` menjadi `List<String>` yang juga menerima string tunggal.
- Validasi request (jumlah, tipe, URL bucket valid) **sebelum** mengirim apa pun ke WhatsApp.
- Loop pengiriman berurutan di `ChatService.messageHandler` untuk `IMAGE`; tipe media lain memakai elemen pertama/satu-satunya.
- Persist per gambar + publish `NEW_MESSAGE` per gambar; `Conversation.lastMessage*` dan `ASSIGNED_CONVERSATION_UPDATED` di-update satu kali setelah loop selesai.
- Penanganan kegagalan parsial (§7).
- Response endpoint mengembalikan ringkasan hasil kirim.
- Update dokumentasi `docs/api-reference.md` dan `docs/frontend/api-mapping.md`.

### Not Included
- Perubahan `WhatsappClientHelper` (tetap 1 gambar per call).
- Perubahan `ChatListDto`, `ChatRepository.getMessageList`, `ConversationService.getListMessage`.
- Perubahan Blast Campaign (`BlastCampaign.mediaLink` tetap tunggal).

---

## 5. Functional Requirements

| ID | Requirement |
|---|---|
| FR-1 | `SendMessageDto.mediaLink` bertipe `List<String>` (nama field JSON tetap `mediaLink`). |
| FR-2 | Deserialisasi MUST menerima **string tunggal maupun array** (`"mediaLink": "url"` ≡ `"mediaLink": ["url"]`), mis. via `@JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)`. `null` / tidak dikirim / `[]` diperlakukan sebagai "tanpa media". |
| FR-3 | Elemen `null` atau blank di dalam list MUST dibuang sebelum diproses. |
| FR-4 | `messageType = IMAGE` MUST memiliki **1..10** media link. 0 → 400 `"Gambar wajib diisi"`; > 10 → 400 `"Maksimal 10 gambar per pengiriman"`. |
| FR-5 | `messageType` `VIDEO` / `DOCUMENT` / `AUDIO` MUST memiliki tepat **1** media link; > 1 → 400 `"Tipe <TYPE> hanya mendukung 1 file"`. |
| FR-6 | `messageType = TEXT` mengabaikan `mediaLink` (perilaku existing). |
| FR-7 | Semua media link MUST divalidasi dengan `storageService.extractPathFromPublicUrl()` **sebelum** pesan pertama dikirim. Jika salah satu invalid → 400 dengan pesan `StorageService` dan **tidak ada** pesan yang terkirim. |
| FR-8 | Untuk `IMAGE`, gambar dikirim **berurutan** (sequential, sesuai index list) via `client.sendImage(...)`, satu call per gambar. |
| FR-9 | Caption (`message`) dikirim hanya pada gambar **index 0**; gambar berikutnya dikirim dengan caption kosong. |
| FR-10 | Setiap gambar yang sukses (`response.code == SUCCESS`) MUST disimpan sebagai satu baris `Chat`: `type=IMAGE`, `message_id` dari response, `media` = path hasil ekstraksi, `pesan` = caption untuk index 0 dan `null` untuk lainnya, `pengirim = username`, `status=SENT`, `sentAt = Instant.now()` (monoton naik sehingga urutan tampilan terjaga). |
| FR-11 | `NEW_MESSAGE` (`ChatListDto`) MUST dipublish untuk **setiap** gambar yang sukses, sesuai urutan. |
| FR-12 | `Conversation.lastMessageAt/lastMessage/lastMessageType` dan event `ASSIGNED_CONVERSATION_UPDATED` di-update **satu kali** setelah loop, merepresentasikan gambar terakhir yang sukses. `lastMessage` = caption jika ada, selain itu tetap mengikuti perilaku existing (`pesan` gambar terakhir). |
| FR-13 | `repliedMessageId` (jika ada) hanya berlaku untuk pesan pertama. *(Catatan: saat ini reply hanya diterapkan pada TEXT; perilaku untuk IMAGE tidak berubah kecuali diputuskan lain di §12 Q5.)* |
| FR-14 | Kegagalan parsial: proses **berhenti** pada gambar pertama yang gagal; gambar yang sudah sukses **tetap tersimpan** (tidak di-rollback) dan sudah dipublish. Lihat §7. |
| FR-15 | Response `data` endpoint berisi ringkasan: `{ "total": n, "sent": k, "failed": n-k, "chatIds": [UUID...] }`. Jika `sent == 0` → HTTP 400 (perilaku existing). Jika `0 < sent < total` → HTTP 200 dengan `success=false` dan pesan `"<k> dari <n> gambar terkirim, sisanya gagal. Silahkan kirim ulang gambar yang gagal."` |
| FR-16 | Request dengan satu gambar MUST menghasilkan perilaku identik dengan kondisi sebelum fitur (1 call provider, 1 baris `Chat`, event sama). |
| FR-17 | `BotService` (TEXT tanpa media) tetap berfungsi tanpa perubahan perilaku. |

---

## 6. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | Tidak ada perubahan skema DB; read path & WebSocket payload existing tidak berubah. |
| NFR-2 | Pengiriman sequential, tanpa paralelisme, agar urutan di WhatsApp penerima terjaga dan mengurangi risiko rate-limit/ban nomor. Jeda antar gambar dapat dikonfigurasi (`chat.multi-image.delay-ms`, default `0`; lihat §12 Q4). |
| NFR-3 | Latensi: request tetap synchronous; untuk 10 gambar, total waktu ≈ 10 × latensi provider. Timeout HTTP client existing harus cukup — dicatat sebagai risiko (§10). |
| NFR-4 | Response tetap memakai envelope `RestResponse<T>`. |
| NFR-5 | Isolasi tenant tetap dari `conversation → contact.idWorkspace → workspace.waba` (tidak berubah). |
| NFR-6 | Pesan error ke client dalam Bahasa Indonesia, konsisten dengan pesan existing. |

---

## 7. Transaksi & Kegagalan Parsial

Masalah pada desain sekarang: `messageHandler` adalah satu `@Transactional` dan `catch (Exception)` membungkus semuanya menjadi `RuntimeException`. Bila diterapkan apa adanya pada loop, gagal di gambar ke-3 akan **me-rollback baris `Chat` gambar 1–2 yang sudah benar-benar terkirim di WhatsApp**. Karena `ChatWebSocketEventListener` memakai `@TransactionalEventListener(phase = AFTER_COMMIT)`, event `NEW_MESSAGE` gambar 1–2 juga **dibuang** → gambar sudah diterima customer tetapi sama sekali tidak terlihat di chat room agent (baik realtime maupun saat reload). Lihat [Impact Analysis](./chat-multiple-image-impact-analysis.md) §4.

Kebutuhan:

1. Validasi (FR-4/5/7) terjadi di luar/ sebelum transaksi pengiriman — gagal validasi = tidak ada efek samping.
2. Persist setiap gambar sukses harus **commit independen** (mis. transaksi per item / `REQUIRES_NEW`, atau `messageHandler` tidak lagi satu transaksi besar). Detail mekanisme diserahkan ke TDD.
3. Event `ChatAsyncEvent` untuk gambar sukses tidak boleh "menggantung" untuk data yang ter-rollback.
4. Setelah gagal, gambar sisanya **tidak dikirim** (fail-fast) agar urutan tetap konsisten dan agent bisa mengirim ulang sisanya.

---

## 8. Affected Components

| Layer | Element | Perubahan |
|---|---|---|
| Model | `SendMessageDto` | `List<String> mediaLink` + `@JsonFormat(ACCEPT_SINGLE_VALUE_AS_ARRAY)` (sudah dimulai di working tree). |
| Model (baru) | `SendMessageResult` | `{ total, sent, failed, chatIds }` untuk response. |
| Service | `ChatService.messageHandler` | Validasi, loop `IMAGE`, caption hanya index 0, persist & publish per item, update conversation sekali, penanganan partial failure, return `SendMessageResult`. Untuk VIDEO/DOCUMENT/AUDIO pakai `mediaLink.get(0)`. |
| Controller | `ChatController.sendMessage` | Set `data` = `SendMessageResult`; mapping status partial (FR-15). |
| Service | `BotService` | Tidak ada perubahan (tidak set `mediaLink`). Diverifikasi compile. |
| Config | `application.properties` | (opsional) `chat.multi-image.max=10`, `chat.multi-image.delay-ms=0`. |
| Docs | `api-reference.md` §6.11, `frontend/api-mapping.md` | `mediaLink: string | string[]`, response baru. |
| Frontend (di luar repo ini) | `ChatDetail`, `chatStore.uploadChatFile/sendMessage` | Multi-select file, upload per file (boleh paralel), kirim array `mediaLink`, preview & hapus sebelum kirim, tampilkan pesan partial failure. |

---

## 9. API Requirements

### Request — `POST /send-message`

```json
{
  "conversationId": "7c1e…",
  "messageType": "IMAGE",
  "mediaLink": [
    "https://<public-base>/<bucket-chat>/2026/10/a.jpg",
    "https://<public-base>/<bucket-chat>/2026/10/b.jpg",
    "https://<public-base>/<bucket-chat>/2026/10/c.jpg"
  ],
  "message": "Ini pilihan warnanya kak",
  "repliedMessageId": null
}
```

Tetap valid (backward compatible):

```json
{ "conversationId": "7c1e…", "messageType": "IMAGE", "mediaLink": "https://…/a.jpg", "message": "caption" }
```

### Response — sukses semua (200)

```json
{ "success": true, "message": "Success",
  "data": { "total": 3, "sent": 3, "failed": 0, "chatIds": ["…", "…", "…"] } }
```

### Response — sebagian gagal (200, `success=false`)

```json
{ "success": false, "message": "2 dari 3 gambar terkirim, sisanya gagal. Silahkan kirim ulang gambar yang gagal.",
  "data": { "total": 3, "sent": 2, "failed": 1, "chatIds": ["…", "…"] } }
```

### Response — validasi gagal / semua gagal (400)

```json
{ "success": false, "message": "Maksimal 10 gambar per pengiriman", "data": null }
```

> FE saat ini mengabaikan `data` response dan menunggu pesan via WebSocket — penambahan `data` tidak memutus FE lama.

---

## 10. Acceptance Criteria

- **Given** request `IMAGE` dengan 3 URL valid dan caption, **when** dikirim, **then** provider menerima 3 call `/send/image` berurutan, hanya call pertama membawa caption, tersimpan 3 baris `chat` (`type=IMAGE`, `message_id` berbeda, `pesan` hanya terisi di baris pertama), 3 event `NEW_MESSAGE` terpublish sesuai urutan, dan `conversation.last_message_type = IMAGE`.
- **Given** request dengan `mediaLink` string tunggal, **when** dikirim, **then** perilaku identik dengan sebelum fitur.
- **Given** 11 URL, **then** 400 `Maksimal 10 gambar per pengiriman` dan tidak ada call ke provider.
- **Given** 3 URL dengan URL ke-2 bukan URL bucket valid, **then** 400 `URL tidak valid untuk bucket ini` dan tidak ada call ke provider.
- **Given** `VIDEO` dengan 2 URL, **then** 400 `Tipe VIDEO hanya mendukung 1 file`.
- **Given** `IMAGE` dengan `mediaLink = []` atau `null`, **then** 400 `Gambar wajib diisi`.
- **Given** 3 URL dan provider gagal pada gambar ke-2, **then** gambar 1 tersimpan & dipublish, gambar 3 tidak dikirim, response `sent=1, failed=2`, `success=false`, HTTP 200.
- **Given** provider gagal pada gambar pertama, **then** tidak ada baris `chat` baru dan HTTP 400 (perilaku existing).
- **Given** bot membalas pesan (TEXT), **then** tidak ada perubahan perilaku.
- **Given** `GET /conversation/message` setelah kirim 3 gambar, **then** muncul 3 item `IMAGE` dengan `mediaLink` public URL masing-masing, urut sesuai `sentAt`.

---

## 11. Edge Cases & Risiko

- **URL duplikat dalam list** — dikirim apa adanya (agent bisa sengaja mengirim ulang); tidak di-dedupe. *(lihat §12 Q6)*
- **Gambar dari bucket produk** (mis. hasil template / `saktiform-media`) — `extractPathFromPublicUrl` menerima bucket chat maupun produk, tetapi read path chat memakai `getPublicUrl` (bucket chat). Gambar bucket produk akan tersimpan path-nya namun dibangun ulang dengan bucket chat saat dibaca → link rusak. Ini **bug existing** yang juga berlaku untuk single image; dicatat, tidak diperbaiki dalam scope ini kecuali diputuskan (§12 Q7).
- **Timeout** — 10 gambar × latensi provider bisa melewati timeout gateway/FE. Mitigasi: batas 10, dan FE menampilkan loading. Opsi async ditandai sebagai future work.
- **Rate limit / ban nomor** — pengiriman beruntun ke satu nomor; jeda opsional (NFR-2).
- **Ukuran file** — tiap file tetap dibatasi `max-file-size=10MB` di upload endpoint; karena upload per file, `max-request-size` tidak menjadi masalah.
- **Urutan `sentAt`** — `Instant.now()` sequential monoton; bila sama persis (resolusi jam), urutan tampilan bisa tertukar. TDD boleh menambah offset kecil atau mengandalkan urutan insert.
- **Compile break saat ini** (§1.2) — harus diselesaikan bersama implementasi, bukan di-commit terpisah.
- **Bug existing terkait (di luar scope):** `WhatsappMessageHandler` baris ~159 memakai `newCustChat.getMedia()` untuk media pesan yang di-reply (seharusnya `replyChat.getMedia()`).

---

## 12. Resolved Decisions

Semua rekomendasi default di bawah disetujui oleh product owner (2026-10-15).

| # | Pertanyaan | Keputusan |
|---|---|---|
| Q1 | Batas maksimal gambar per kirim? | **10** (configurable). |
| Q2 | Apakah multiple juga berlaku untuk VIDEO/DOCUMENT? | **Tidak** untuk fase ini; validasi 1 file. Desain loop dibuat generik agar mudah diperluas. |
| Q3 | Caption di gambar pertama atau terakhir? | **Pertama** (pola umum WhatsApp). |
| Q4 | Perlu jeda antar gambar? | Default **0 ms**, configurable; naikkan jika ada indikasi rate-limit. |
| Q5 | Apakah `repliedMessageId` perlu didukung untuk IMAGE? | Di luar scope; tetap seperti sekarang (reply hanya TEXT). |
| Q6 | Dedupe URL yang sama? | **Tidak** dedupe. |
| Q7 | Perbaiki sekalian bug bucket produk vs chat pada read path? | **Tidak** dalam PRD ini; buat tiket terpisah. |
| Q8 | Kegagalan parsial: fail-fast (stop) atau lanjutkan sisa gambar? | **Fail-fast**, simpan yang sukses, laporkan ringkasan. |
| Q9 | Partial success → HTTP 200 `success=false` atau 207/400? | **200 + `success=false`** agar FE lama tetap menerima pesan dan WebSocket tetap mengalirkan gambar yang terkirim. |
