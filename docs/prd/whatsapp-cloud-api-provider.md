# PRD: Modul WhatsApp Cloud API & Switch Provider per Workspace

| Field | Value |
|---|---|
| Feature name | WhatsApp Cloud API Module + Workspace Provider Switch (Gateway gowa ⇄ Cloud API) |
| Component | **Baru:** modul WhatsApp Cloud (`WhatsappCloudController`, `service/whatsappcloud/*`, `WhatsappCloudNumber`), provider layer (`service/chat/provider/*`), follow-up scheduler. **Diubah:** `Workspace`/`WorkspaceService`, `ChatService`/`ChatMessagePersister`, `WhatsappMessageHandler`, `OrderEventListener`, `BlastCampaignService`, `SecurityConfig` |
| Status | **Draft revisi 2**: keputusan stakeholder sudah masuk (§2.1). Sisa pertanyaan di §13 |
| Scope | Backend + kontrak API untuk frontend dashboard |
| Review teknis | `docs/WhatsappCloudAPI/integration-review.md` (lihat §0 untuk keputusan terbaru) |
| Referensi API | `docs/WhatsappCloudAPI/whatsapp-chat-integration.md`, `docs/WhatsappCloudAPI/whatsapp-cloud-api-reference.md` |
| Last updated | 2026-11-26 |

---

## 1. Background

Saat ini semua komunikasi WhatsApp berjalan lewat **gowa** (gateway open-source yang meniru WhatsApp Web). Gowa murah dan mudah, tetapi tidak resmi: nomor berisiko diblokir, sesi bisa logout sendiri, dan tidak ada status delivered/read yang andal.

Akun **WhatsApp Cloud API (Meta) sudah terverifikasi dan siap dipakai.** Platform harus bisa menjalankan keduanya: setiap workspace bisa **di-switch dari dashboard** antara gowa dan Cloud API.

## 2. Problem Statement

- **P-1:** Pengiriman dan penerimaan terkunci ke gowa. `WhatsappClientHelper` dipanggil langsung dari `ChatService`, `OrderEventListener`, dan `BlastSenderService`, dan webhook hanya memahami `WebhookEnvelopeV2`.
- **P-2:** Belum ada tempat untuk mendaftarkan nomor Cloud API beserta kredensialnya, dan belum ada cara memilih provider per workspace.
- **P-3:** Cloud API punya aturan yang tidak dikenal sistem: window 24 jam, template wajib untuk pesan di luar window, biaya per pesan template, BSUID, retry webhook, dan signature HMAC. Kalau diabaikan, follow-up order gagal dan biaya tidak terkontrol.

### 2.1 Keputusan Stakeholder

| # | Keputusan |
|---|---|
| D1 | Dashboard bisa **switch** antara gowa dan Cloud API per workspace. **Cloud API adalah modul terpisah** (entitas, API, dan menu dashboard sendiri) |
| D2 | Akun Cloud API **sudah terverifikasi & siap pakai**. Embedded Signup tidak dibutuhkan |
| D3 | **Admin** mendaftarkan nomor. Kredensial Cloud API (client API) **diisi di dashboard untuk tiap nomor** |
| D4 | Follow-up order punya **delay** setelah order dibuat. Jenis pesan (gratis/berbayar) **tergantung kecepatan customer** mengirim pesan setelah order |
| D5 | **Biaya Cloud API ditanggung per workspace** |

## 3. Goals & Non-Goals

### Goals
- **G-1:** Admin dapat mendaftarkan, memvalidasi, dan mengelola nomor Cloud API beserta kredensialnya per nomor, di modul terpisah dari gowa.
- **G-2:** Setiap workspace dapat memasang satu nomor gowa **dan/atau** satu nomor Cloud, lalu **switch** provider aktif dari dashboard tanpa kehilangan histori.
- **G-3:** Fitur inti berjalan di kedua provider: inbox 2 arah (teks, gambar, video, dokumen, audio, reply), bot AI, follow-up/konfirmasi order, dan realtime WebSocket.
- **G-4:** Follow-up order dijadwalkan dengan delay. Saat dikirim, sistem memilih teks bebas (gratis) kalau customer sudah chat, atau template Meta (berbayar) kalau belum.
- **G-5:** Pemakaian berbayar Cloud API tercatat dan bisa dilaporkan **per workspace**.
- **G-6:** Perilaku workspace gowa tidak berubah, kecuali follow-up delay (G-4, lihat §13 Q2).

### Non-Goals (rilis ini)
- Embedded Signup / Tech Provider (D2).
- Membuat/mengedit template Meta dari dashboard. Template dibuat di WhatsApp Manager, sistem hanya **menyinkronkan**.
- Blast via Cloud API (Fase 3, §12).
- Penagihan otomatis (invoice/payment) ke workspace. Sistem hanya mencatat dan melaporkan pemakaian.
- Calling, Groups, Flows, katalog, coexistence dengan WhatsApp Business app.
- Lebih dari satu nomor aktif per workspace.

---

## 4. Konsep & Arsitektur

| Istilah | Definisi |
|---|---|
| **Gateway number** | Nomor gowa. Entitas lama `WhatsappBusinessApi` (`whatsapp_business_api`), tidak diubah |
| **Cloud number** | Nomor Cloud API. Entitas **baru** `WhatsappCloudNumber` (`whatsapp_cloud_number`), berisi kredensial per nomor |
| **Provider aktif** | `workspace.whatsapp_provider` = `GOWA` \| `CLOUD_API`. Menentukan channel untuk **semua pesan keluar** |
| **CSW** | Customer Service Window, 24 jam sejak pesan terakhir customer. Di dalam window boleh kirim apa saja (gratis). Di luar window hanya template APPROVED (berbayar) |
| **Template Meta** | Template yang disetujui Meta. Berbeda dari `ChatTemplate` internal |
| **Template binding** | Pemetaan tujuan (`FOLLOWUP-COD`, ...) ke template Meta + mapping variabel, per workspace |
| **Follow-up job** | Jadwal pengiriman follow-up order yang tersimpan di DB (tahan restart) |

```
Dashboard ──► Workspace.whatsapp_provider ─┬─ GOWA      → waba_id        → WhatsappBusinessApi (modul lama)
                                           └─ CLOUD_API → cloud_number_id → WhatsappCloudNumber (modul baru)

Outbound:  ChatService / Bot / FollowUpJob / Blast
             → WhatsappProviderRouter.forWorkspace(workspaceId)
                 ├─ GowaProvider     → WhatsappClientHelper (existing)
                 └─ CloudApiProvider → CloudApiClient (Graph API, token milik nomor)

Inbound:   POST /whatsapp/webhook                    → GowaWebhookAdapter  ─┐
           GET|POST /whatsapp-cloud/webhook/{key}    → CloudWebhookAdapter ─┴─► InboundMessage → WhatsappMessageHandler
                                                                                  → Contact/Conversation/Chat → Bot / WS
```

Struktur package (mengikuti layering yang ada):
```
controller/WhatsappCloudController, WhatsappCloudWebhookController
entity/WhatsappCloudNumber, WhatsappMetaTemplate, WhatsappTemplateBinding, OrderFollowupJob, WhatsappCloudUsage, WhatsappCloudRate
repository/…Repository
model/whatsappcloud/…            (DTO request/response, model webhook Meta)
service/whatsappcloud/           CloudApiClient, WhatsappCloudNumberService, CloudWebhookService,
                                 CloudTemplateService, CloudUsageService, TokenCrypto
service/chat/provider/           WhatsappProvider, GowaProvider, CloudApiProvider, WhatsappProviderRouter,
                                 InboundMessage, SendResult
service/order/                   OrderFollowupScheduler (baru), OrderEventListener (diubah)
```

---

## 5. User Stories

| ID | Sebagai | Saya ingin | Agar |
|---|---|---|---|
| US-1 | Admin | menambah nomor Cloud API dengan mengisi Phone Number ID, WABA ID, App ID, App Secret, dan Access Token di dashboard | nomor resmi bisa dipakai |
| US-2 | Admin | sistem memvalidasi kredensial dan memasang webhook otomatis | tidak perlu konfigurasi manual di Meta untuk tiap nomor |
| US-3 | Admin | melihat status nomor Cloud (connected, quality rating, token valid, webhook terverifikasi) | tahu kalau ada masalah |
| US-4 | Admin/Owner | memasang nomor gowa dan nomor Cloud ke workspace, lalu switch provider aktif | bisa pindah provider kapan saja |
| US-5 | Admin/Owner | memetakan template Meta untuk follow-up COD/Transfer dan mengatur delay follow-up | follow-up tetap terkirim walau customer lambat chat |
| US-6 | Agent | membalas chat dengan cara yang sama apa pun providernya | tidak perlu belajar ulang |
| US-7 | Agent (Cloud) | diberi tahu saat window 24 jam tertutup dan bisa mengirim template | pesan tidak gagal diam-diam |
| US-8 | Agent (Cloud) | melihat status ✓ / ✓✓ / ✓✓ biru / gagal | tahu pesan sudah dibaca |
| US-9 | Admin/Owner | melihat laporan pemakaian & estimasi biaya Cloud API per workspace per periode | biaya bisa dibebankan ke workspace |
| US-10 | Customer | menerima follow-up dan balasan bot seperti biasa | pengalaman tidak berubah |

---

## 6. Functional Requirements

### 6.1 Modul WhatsApp Cloud: Manajemen Nomor

**FR-1: Entitas terpisah.** Nomor Cloud disimpan di `whatsapp_cloud_number` (§8). Entitas `WhatsappBusinessApi` (gowa) **tidak diubah**.

**FR-2: Registrasi nomor.** `POST /whatsapp-cloud` (role ADMIN):
```json
{ "label": "CS Toko A", "phoneNumberId": "1234567890", "wabaId": "9876543210",
  "appId": "111222333", "appSecret": "abc...", "accessToken": "EAAG..." }
```
Proses:
1. Validasi kredensial: `GET /{phoneNumberId}?fields=display_phone_number,verified_name,status,quality_rating,name_status,messaging_limit_tier`. Kalau gagal (190/100/200/803), tolak dengan pesan yang jelas (mis. "Access token tidak valid", "Phone Number ID tidak ditemukan atau token tidak punya akses").
2. Normalisasi `display_phone_number` ke format `62...`. Tolak kalau `phoneNumberId` atau nomornya sudah terdaftar di `whatsapp_cloud_number` (aktif). Kalau nomor yang sama masih terdaftar sebagai gateway gowa, tolak dengan pesan "Nomor ini masih terdaftar di WhatsApp Gateway, hapus dahulu" (satu nomor fisik tidak bisa aktif di gowa dan Cloud bersamaan).
3. Generate `webhookKey` (random 32 hex) dan `verifyToken` (random), lalu simpan. `appSecret` dan `accessToken` **dienkripsi** (NFR-3).
4. Pasang webhook (FR-4).
5. Simpan `status = CONNECTED` jika Meta `status = CONNECTED` dan webhook sukses. Selain itu `DISCONNECTED` dengan `statusReason`.
6. Jalankan sinkronisasi template (FR-22) secara async.

Response tidak pernah berisi `appSecret`/`accessToken`. Untuk tampilan cukup `accessTokenLast4`.

**FR-3: Update.** `PUT /whatsapp-cloud/{id}`: ubah label dan/atau kredensial (field kosong = tidak diubah). Kalau kredensial berubah, jalankan ulang validasi FR-2 langkah 1 dan FR-4.

**FR-4: Setup webhook otomatis per nomor.**
1. `POST /{wabaId}/subscribed_apps` → `{ success: true }`.
2. `POST /{phoneNumberId}` dengan body `{ "webhook_configuration": { "override_callback_uri": "{PUBLIC_BASE_URL}/whatsapp-cloud/webhook/{webhookKey}", "verify_token": "{verifyToken}" } }`. Meta akan memanggil GET verifikasi (FR-9) ke URL tersebut.
3. Kalau langkah 2 gagal, nomor tetap tersimpan dengan `statusReason = WEBHOOK_NOT_CONFIGURED`. Dashboard menampilkan **Callback URL** dan **Verify Token** untuk diisi manual di App Dashboard.
4. Endpoint ulang: `POST /whatsapp-cloud/{id}/webhook/setup`.
5. `webhook_verified_at` diisi saat GET verifikasi dari Meta sukses.

**FR-5: List / detail / available.**
- `GET /whatsapp-cloud?page&limit&search`: label, nomor, displayName, status, statusReason, qualityRating, messagingLimitTier, workspace pemakai, webhookVerified.
- `GET /whatsapp-cloud/{id}`: detail + Callback URL + Verify Token (untuk setup manual).
- `GET /whatsapp-cloud/available`: nomor `CONNECTED` yang belum dipakai workspace lain.

**FR-6: Hapus.** `POST /whatsapp-cloud/delete` `{ id }`. Ditolak kalau nomor dipakai workspace mana pun (sebagai `cloud_number_id`), dengan pesan "Nomor sedang digunakan oleh workspace X". Kalau boleh: soft delete (`deleted_at`), hapus kredensial terenkripsi, dan coba `DELETE /{wabaId}/subscribed_apps` (best effort).

**FR-7: Health check.** `POST /whatsapp-cloud/{id}/check` (on-demand) dan scheduler (mis. tiap 15 menit) menjalankan `GET /{phoneNumberId}` → update `status`, `qualityRating`, `messagingLimitTier`, `last_health_check_at`. Error 190 berarti `DISCONNECTED` (`TOKEN_INVALID`). Error 10/200 berarti `DISCONNECTED` (`PERMISSION`). Perubahan status dipublikasikan via WebSocket ke workspace pemakai (event yang sama dengan `docs/prd/whatsapp-connection-status.md`).

### 6.2 Workspace: Pemasangan Nomor & Switch Provider

**FR-8: Kolom workspace.** `workspace.whatsapp_provider` (`GOWA` default) dan `workspace.cloud_number_id` (FK nullable, unik antar workspace). `waba_id` (gowa) tetap.

**FR-9a: Pengaturan WhatsApp workspace.** `GET /workspace/{id}/whatsapp-setting`:
```json
{ "provider": "GOWA",
  "gateway": { "wabaId": "uuid", "nomor": "62812...", "status": "CONNECTED" },
  "cloud":   { "cloudNumberId": "uuid", "nomor": "62813...", "status": "CONNECTED", "qualityRating": "GREEN" },
  "followup": { "delaySeconds": 300,
                "bindings": [ { "purpose": "FOLLOWUP-COD", "metaTemplateId": "uuid", "templateName": "followup_cod",
                                "paramMapping": { "body": ["nama_customer","nama_produk","harga_produk"] } } ] },
  "canSwitchToCloud": false, "switchBlockers": ["FOLLOWUP_TEMPLATE_NOT_SET:FOLLOWUP-TRANSFER"] }
```

**FR-9b: Pasang nomor.** `PUT /workspace/{id}/whatsapp-setting/channel` dengan body `{ "wabaId": "uuid|null", "cloudNumberId": "uuid|null" }`. Nomor harus `CONNECTED` dan belum dipakai workspace lain. Nomor yang sedang aktif (sesuai `provider`) tidak boleh dilepas. Pemasangan `wabaId` lewat form workspace lama (`upsertWorkspace`) tetap berfungsi.

**FR-10: Switch provider.** `PUT /workspace/{id}/whatsapp-setting/provider` dengan body `{ "provider": "CLOUD_API" }`. Validasi:
- Nomor tujuan sudah terpasang dan `CONNECTED`.
- Switch ke `CLOUD_API` mensyaratkan binding `FOLLOWUP-COD` dan `FOLLOWUP-TRANSFER` sudah diatur dengan template `APPROVED` (§13 Q5). Kalau gagal, `400` dengan daftar `switchBlockers`.

Efek:
- Semua pesan keluar berikutnya (agent, bot, follow-up job yang jatuh tempo setelah switch) memakai provider baru.
- Event WebSocket `WHATSAPP_PROVIDER_CHANGED` ke `/topic/workspace/{workspaceId}`.
- Dicatat di log audit (siapa, kapan, dari → ke).
- Campaign blast gowa yang sedang `RUNNING` tetap berjalan di nomor gowa-nya sampai selesai (campaign menyimpan `deviceId` sendiri).

**FR-11: Info provider di response.** Detail/list workspace dan detail percakapan menambahkan `whatsappProvider`.

**FR-12: Pesan masuk dari channel tidak aktif.** Setelah switch, customer mungkin masih mengirim ke nomor lama. Pesan tersebut **tetap diproses dan disimpan** ke percakapan yang sama (tidak hilang), ditandai `chat.channel_provider` dan ditampilkan dengan badge "via nomor lama". Bot **tidak** membalas otomatis pesan dari channel tidak aktif. Percakapan ditandai untuk agent (§13 Q1).

### 6.3 Provider Layer (internal)

**FR-13:** Interface `WhatsappProvider` dengan `GowaProvider` (membungkus `WhatsappClientHelper` **tanpa mengubah perilaku**) dan `CloudApiProvider`. `WhatsappProviderRouter.forWorkspace(workspaceId)` membaca `whatsapp_provider` dan mengembalikan provider + channel.

**FR-14:** Pemanggil langsung `WhatsappClientHelper.sendXxx` dipindah ke router:
- `ChatService.sendToProvider` (`ChatService.java:167-185`)
- `OrderEventListener.autoFollowup` (`OrderEventListener.java:59-79`), yang sekarang dijalankan dari follow-up job (FR-27)
- `BlastSenderService.processJob` (`BlastSenderService.java:29-56`), khusus gowa

`ChatSendContext` mengganti `deviceId` dengan `provider` + `channelId`.

**FR-15:** Hasil kirim memakai `SendResult { success, messageId, errorCode, errorMessage, retryable, windowClosed }`. Pengecekan `"SUCCESS".equals(response.getCode())` di service bisnis dihapus.

### 6.4 Webhook Cloud API (per nomor)

**FR-16: Verifikasi.** `GET /whatsapp-cloud/webhook/{webhookKey}?hub.mode=subscribe&hub.verify_token=..&hub.challenge=..`. Cari nomor lewat `webhookKey`. Kalau `verify_token` cocok, balas `200` dengan body `challenge` (text/plain) dan set `webhook_verified_at`. Selain itu `403`.

**FR-17: Terima event.** `POST /whatsapp-cloud/webhook/{webhookKey}`:
1. Cari nomor lewat `webhookKey`. Kalau tidak ada, `404`.
2. Baca **raw body**. Validasi `X-Hub-Signature-256` = `sha256=HMAC_SHA256(appSecret nomor tsb, rawBody)` dengan perbandingan constant-time. Kalau invalid, `401` + log WARN.
3. Balas `200` dalam **< 1 detik**. Proses `@Async`.
4. Iterasi **semua** `entry[].changes[]`. Pastikan `value.metadata.phone_number_id` sama dengan `phoneNumberId` nomor (defense in depth). Proses `messages[]`, `statuses[]`, dan `errors[]`. Field lain dicatat.
5. Tenant = workspace dengan `cloud_number_id` = nomor ini. Kalau tidak dipasang ke workspace mana pun, catat dan abaikan.

`SecurityConfig`: tambahkan `permitAll` untuk `/whatsapp-cloud/webhook/**`. Pola `/whatsapp/*/webhook` yang ada **tidak mencakup** path ini.

**FR-18: Idempotensi.** Insert `dedupe_key` ke `whatsapp_webhook_event` sebelum diproses (`in:{wamid}`, `st:{wamid}:{status}`). Kalau konflik, lewati. Retensi 14 hari.

**FR-19: Pemetaan pesan masuk** ke model kanonik `InboundMessage`, lalu diproses oleh `WhatsappMessageHandler` yang direfactor (dipakai bersama gowa):

| Cloud `type` | `ChatType` | Catatan |
|---|---|---|
| `text` | TEXT | `text.body` |
| `image`, `sticker` | IMAGE | unduh media + caption |
| `video` | VIDEO | |
| `audio` | AUDIO | `voice: true` = voice note |
| `document` | DOCUMENT | simpan `filename` |
| `button` | TEXT | `button.text` (klik quick reply template) |
| `interactive` | TEXT | `button_reply.title` / `list_reply.title` |
| `location`, `contacts` | TEXT | dirender sebagai teks ringkas |
| `reaction` | – | diabaikan (Fase 2) |
| `system` | – | update kontak (FR-19c) |
| lainnya / `unsupported` | TEXT | "[Pesan tidak didukung]" |

- **FR-19a: Kontak.** Upsert berdasarkan `(id_workspace, bsuid)`. Fallback ke nomor (`wa_id`, dinormalisasi `PhoneNumberUtil`) untuk mencocokkan kontak lama dari era gowa, lalu isi `bsuid`. Nama dari `contacts[].profile.name`.
- **FR-19b: Media.** Unduh segera: `GET /{media_id}` → `url` (berlaku 5 menit) → `GET url` dengan Bearer. Verifikasi `sha256`, lalu simpan ke MinIO via `MediaHelper`. Kalau gagal, chat disimpan dengan teks "[Media gagal diunduh]".
- **FR-19c: System.** `user_changed_user_id` → ganti `bsuid` (cari via `previous_user_id`). `user_changed_number` → update `phone_number`.
- **FR-19d: Reply.** `context.id` → `chatMessageService.findByWhatsappMessageId`.
- **FR-19e: Window.** Setiap pesan customer (kedua provider) meng-update `conversation.last_inbound_at`. Kalau ada `referral` (iklan CTWA), `fep_expires_at = timestamp + 72 jam`.
- Event `ChatAsyncEvent` / `IncomingChatEvent` dipublikasikan sama persis seperti sekarang. Pengecualian FR-12: pesan dari channel tidak aktif tidak memicu bot.

**FR-20: Status pesan.** `statuses[]` meng-update `chat.status` secara **monotonic** (`SENT < DELIVERED < READ < PLAYED`, `FAILED` menang) lewat `chat.message_id`. Simpan `error_code`/`error_message` saat `failed`. Publikasikan event WebSocket ke `/topic/chatroom/{conversationId}`:
```json
{ "type": "MESSAGE_STATUS_UPDATED", "data": { "chatId": "uuid", "status": "READ", "errorCode": null } }
```
Kalau status membawa `pricing`, teruskan ke pencatatan biaya (FR-31).

### 6.5 Outbound (Cloud API)

**FR-21: Kirim pesan bebas (dalam window).**
- Text: `type: text`, `preview_url: true`, body ≤4096 karakter.
- Image: konversi ke **JPEG** kalau sumbernya WebP atau format lain (gambar di MinIO hasil `StorageService.uploadConverted` berformat WebP), maksimal 5 MB, upload `POST /{phoneNumberId}/media`, lalu kirim dengan `id`. Caption ≤1024.
- Video (MP4 ≤16 MB), Audio (≤16 MB), Document (≤100 MB, dengan `filename`) dikirim lewat upload, lalu `id`.
- Reply: `context.message_id = repliedChat.messageId`.
- `to = +62...` dari `contact.phoneNumber`. Kalau kosong, `recipient = contact.bsuid`.
- `biz_opaque_callback_data` = id chat internal.
- `messages[0].id` (wamid) disimpan sebagai `chat.message_id`, dengan status awal `SENT`.

**FR-22: Sinkronisasi template Meta.** `GET /{wabaId}/message_templates?fields=id,name,language,category,status,components,parameter_format,quality_score,rejected_reason&limit=100`. Ikuti `paging.next` sampai habis, lalu upsert ke `whatsapp_meta_template`. Template yang hilang dari Meta ditandai `DELETED`. Pemicu:
- setelah registrasi,
- `POST /whatsapp-cloud/{id}/templates/sync`,
- scheduler harian.

List untuk UI: `GET /whatsapp-cloud/{id}/templates?status=APPROVED&category=` (dari DB). Response berisi jumlah variabel per komponen (HEADER/BODY/BUTTON URL).

**FR-23: Window check.** Sebelum mengirim pesan non-template di workspace `CLOUD_API`:
- `windowOpen = now < last_inbound_at + 24h || now < fep_expires_at`.
- Kalau tertutup, `409` dengan `RestResponse.success=false`, `message="Sesi 24 jam sudah berakhir, gunakan template"`, `data.code="WINDOW_CLOSED"`, **tanpa** memanggil Meta.
- Error Meta 131047 diperlakukan sama.
- Bot: kalau window tertutup (kasus langka), jangan kirim, lalu escalate ke agent.

**FR-24: Detail percakapan** menambahkan `{ "provider": "CLOUD_API", "windowOpen": true, "windowExpiresAt": "..." }`. Untuk gowa: `windowOpen = true`, `windowExpiresAt = null`.

**FR-25: Kirim template dari inbox.** `POST /chat/send-template` dengan body `{ conversationId, metaTemplateId, parameters: { header?: [...], body: ["Budi","INV001"], buttons?: [...] } }`. Hanya untuk workspace `CLOUD_API`. Jumlah parameter divalidasi terhadap template (mencegah 132000). Chat disimpan dengan `type = TEXT`, `pesan` = hasil render body, dan `send_source = TEMPLATE_MANUAL`.

**FR-26: Error handling.** Klasifikasi berdasarkan `error.code`:

| Code | Tindakan |
|---|---|
| 131047 | `WINDOW_CLOSED` (FR-23) |
| 130429, 131056, 131000, 131016, 1, 2, HTTP 5xx, `is_transient` | retry backoff (1 s, 4 s, 16 s), maksimal 3 kali |
| 0, 190 | nomor → `DISCONNECTED` (`TOKEN_INVALID`) + event WebSocket |
| 10, 200, 368, 131031 | nomor → `DISCONNECTED` (`PERMISSION` / `RESTRICTED`) + event |
| 131026, 131050, 131062, 132xxx | gagal permanen, simpan `error_code`, tidak retry |

Pesan ke agent: "Gagal mengirim: {error_title} ({code})". `fbtrace_id` dicatat di log.

### 6.6 Follow-up Order dengan Delay

> ⚠️ Di working tree saat ini follow-up **langsung** dikirim (`OrderEventListener.autoFollowup`, `@Async` + `AFTER_COMMIT`, tanpa delay). FR ini membuat delay tersebut secara eksplisit.

**FR-27: Penjadwalan.** Saat `OrderCreatedEvent` dengan tipe `FOLLOWUP-*` (dari `OrderOrchestrationService.java:94-96` dan `:200-202`), `OrderEventListener` **tidak langsung mengirim**. Listener membuat `order_followup_job` dengan:
- `due_at = order.createdAt + workspace.followup_delay_seconds`
- `status = PENDING`

Kalau sudah ada job `PENDING` untuk order + purpose yang sama (mis. nomor WA order diubah), job lama dibatalkan (`CANCELLED`) dan diganti yang baru.

`CONFIRMATION-*` (order dari chat, `:172-174`, `:224-226`) tetap dikirim langsung, karena customer sedang chat dan window terbuka. Kalau window ternyata tertutup di Cloud, pakai binding `CONFIRMATION-*` kalau ada. Kalau tidak ada, gagal dengan log.

**FR-28: Eksekusi.** `OrderFollowupScheduler` (`@Scheduled`, tiap 15 detik) mengambil job `PENDING` yang `due_at <= now` (`FOR UPDATE SKIP LOCKED`, batch 50, aman untuk multi-instance), lalu menentukan provider aktif workspace **saat eksekusi**:

| Provider | Kondisi | Aksi | Status job |
|---|---|---|---|
| GOWA | – | kirim teks `ChatTemplate` (perilaku sekarang) | `SENT_FREE` |
| CLOUD_API | customer **sudah** chat (window terbuka) | kirim teks `ChatTemplate`. Gratis (service message) | `SENT_FREE` |
| CLOUD_API | customer **belum** chat (window tertutup) | kirim template Meta dari binding `purpose`, variabel dari `MessageConstructorHelper.buildOrderParams`. Berbayar (UTILITY) | `SENT_TEMPLATE` |
| CLOUD_API | window tertutup **dan** binding tidak ada | tidak kirim. `OrderHistory`: "Follow-up WhatsApp tidak terkirim: template belum diatur" | `FAILED` |

- Error transient: `attempt+1`, `due_at` dimundurkan (backoff), maksimal 3 kali, lalu `FAILED`.
- Order yang sudah dibatalkan/dihapus sebelum jatuh tempo → `SKIPPED`.
- Setelah sukses, sisa logika `autoFollowup` tetap berjalan: membuat/menautkan `Conversation`, menyimpan `Chat` (`pengirim = BOT`, `send_source = FOLLOWUP`), set `activeOrderId`, dan publish event WebSocket.

**FR-29: Pengaturan follow-up.** `PUT /workspace/{id}/whatsapp-setting/followup`:
```json
{ "delaySeconds": 300,
  "bindings": [ { "purpose": "FOLLOWUP-COD", "metaTemplateId": "uuid",
                  "paramMapping": { "body": ["nama_customer","nama_produk","harga_produk"] } } ] }
```
- `delaySeconds`: 0–86400 (0 = langsung). Default: §13 Q2.
- `purpose`: `FOLLOWUP-COD`, `FOLLOWUP-TRANSFER`, `CONFIRMATION-COD`, `CONFIRMATION-TRANSFER`.
- Template harus milik nomor Cloud workspace dan berstatus `APPROVED`. Peringatan kalau kategorinya `MARKETING` (lebih mahal dibanding UTILITY).
- Setiap key `paramMapping` harus salah satu key `buildOrderParams` (`nama_customer`, `telepon_customer`, `nama_produk`, `atribut_produk`, `harga_produk`, ...). Jumlah variabel harus sama dengan template.

**FR-30: Visibilitas.** Detail order menampilkan status follow-up (`PENDING` + `dueAt`, `SENT_FREE`, `SENT_TEMPLATE`, `FAILED` + alasan).

### 6.7 Biaya per Workspace

**FR-31: Pencatatan pemakaian.** Untuk setiap pesan keluar Cloud, saat webhook status pertama yang membawa `pricing { billable, pricing_model, type, category }` diterima, insert satu baris ke `whatsapp_cloud_usage` (unik per `wamid`) dengan:
- `id_workspace` (workspace pemilik conversation **saat pesan dikirim**), `id_cloud_number`, `chat_id`
- `category` (`marketing` / `utility` / `authentication` / `service`), `pricing_type`, `billable`
- `send_source` (`CHAT_AGENT` / `BOT` / `FOLLOWUP` / `TEMPLATE_MANUAL` / `BLAST`)
- `estimated_cost` (dari rate card FR-32), `currency`, `occurred_at`

**FR-32: Rate card.** Tabel `whatsapp_cloud_rate (category, price, currency, effective_from)` dikelola ADMIN (`GET/PUT /whatsapp-cloud/rates`). Dipakai untuk estimasi biaya (harga per pesan Meta untuk Indonesia, diisi manual). Tagihan resmi tetap dari Meta.

**FR-33: Laporan.** `GET /whatsapp-cloud/usage?workspaceId=&from=&to=&groupBy=day|month` (ADMIN untuk semua workspace, OWNER untuk workspace sendiri):
```json
{ "workspaceId": 12, "from": "2026-11-01", "to": "2026-11-30", "currency": "IDR",
  "summary": [ { "category": "utility", "billable": 820, "free": 1350, "estimatedCost": 0 },
               { "category": "service", "billable": 0, "free": 5120, "estimatedCost": 0 } ],
  "bySource": [ { "source": "FOLLOWUP", "billable": 820 } ],
  "series": [ { "period": "2026-11-01", "billable": 30, "estimatedCost": 0 } ] }
```
Export Excel: `GET /whatsapp-cloud/usage/export` (pakai Apache POI yang sudah ada).

### 6.8 Blast

**FR-34:** Membuat campaign untuk workspace dengan provider `CLOUD_API` ditolak dengan pesan "Blast untuk WhatsApp Cloud API belum tersedia" (Fase 3: template marketing, throttle sesuai tier, biaya tercatat di FR-31).

---

## 7. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | Webhook Cloud membalas `200` < 1 detik (p99). Pemrosesan async |
| NFR-2 | Tidak ada chat duplikat atau balasan bot ganda akibat retry webhook |
| NFR-3 | `app_secret` dan `access_token` dienkripsi AES-256-GCM at-rest (key env `WA_CLOUD_TOKEN_ENC_KEY`), tidak pernah muncul di response atau log |
| NFR-4 | Signature webhook wajib divalidasi, tanpa mode bypass di production |
| NFR-5 | Versi Graph API bisa dikonfigurasi (default `v26.0`) |
| NFR-6 | Workspace gowa: perilaku chat, bot, dan blast tidak berubah. Semua test existing lulus |
| NFR-7 | Timeout Graph: connect 5 s, read 30 s (upload media 60 s) |
| NFR-8 | Follow-up job tahan restart aplikasi dan aman dijalankan di lebih dari satu instance |
| NFR-9 | Log panggilan Graph yang gagal mencatat `code`, `fbtrace_id`, dan `cloudNumberId`. Nomor customer dimasking |

### Konfigurasi baru
```properties
app.public-base-url=${APP_PUBLIC_BASE_URL:}                      # basis Callback URL webhook (HTTPS)
whatsapp.cloud.graph-base-url=${WA_CLOUD_GRAPH_URL:https://graph.facebook.com}
whatsapp.cloud.graph-version=${WA_CLOUD_GRAPH_VERSION:v26.0}
whatsapp.cloud.token-enc-key=${WA_CLOUD_TOKEN_ENC_KEY:}
whatsapp.cloud.health-check.cron=${WA_CLOUD_HEALTH_CRON:0 */15 * * * *}
order.followup.default-delay-seconds=${ORDER_FOLLOWUP_DELAY_SECONDS:300}
```
App ID, App Secret, dan Verify Token **tidak** global. Semuanya disimpan per nomor (D3).

---

## 8. Data Model

```sql
CREATE TABLE whatsapp_cloud_number (
  id UUID PRIMARY KEY,
  label TEXT,
  nomor_whatsapp TEXT NOT NULL,              -- normalisasi 62...
  display_name TEXT,
  phone_number_id TEXT NOT NULL,
  waba_id TEXT NOT NULL,
  app_id TEXT NOT NULL,
  app_secret_enc TEXT NOT NULL,
  access_token_enc TEXT NOT NULL,
  access_token_last4 TEXT,
  verify_token TEXT NOT NULL,
  webhook_key TEXT NOT NULL UNIQUE,
  webhook_verified_at TIMESTAMPTZ,
  status TEXT NOT NULL,                      -- CONNECTED | DISCONNECTED
  status_reason TEXT,                        -- TOKEN_INVALID | PERMISSION | RESTRICTED | WEBHOOK_NOT_CONFIGURED | ...
  quality_rating TEXT, messaging_limit_tier TEXT,
  last_health_check_at TIMESTAMPTZ,
  created_by BIGINT, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ, deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_wcn_phone_number_id ON whatsapp_cloud_number(phone_number_id) WHERE deleted_at IS NULL;

ALTER TABLE workspace
  ADD COLUMN whatsapp_provider VARCHAR(20) NOT NULL DEFAULT 'GOWA',
  ADD COLUMN cloud_number_id UUID REFERENCES whatsapp_cloud_number(id),
  ADD COLUMN followup_delay_seconds INT;
CREATE UNIQUE INDEX uq_workspace_cloud_number ON workspace(cloud_number_id) WHERE cloud_number_id IS NOT NULL;

ALTER TABLE contact ADD COLUMN bsuid TEXT, ADD COLUMN wa_username TEXT;
CREATE INDEX idx_contact_ws_bsuid ON contact(id_workspace, bsuid);

ALTER TABLE conversation ADD COLUMN last_inbound_at TIMESTAMPTZ, ADD COLUMN fep_expires_at TIMESTAMPTZ;

ALTER TABLE chat
  ADD COLUMN channel_provider VARCHAR(20),   -- GOWA | CLOUD_API (null = data lama/gowa)
  ADD COLUMN send_source VARCHAR(30),        -- CHAT_AGENT | BOT | FOLLOWUP | TEMPLATE_MANUAL | BLAST
  ADD COLUMN error_code TEXT, ADD COLUMN error_message TEXT, ADD COLUMN status_updated_at TIMESTAMPTZ;

CREATE TABLE whatsapp_webhook_event (dedupe_key TEXT PRIMARY KEY, received_at TIMESTAMPTZ NOT NULL DEFAULT now());

CREATE TABLE whatsapp_meta_template (
  id UUID PRIMARY KEY,
  id_cloud_number UUID NOT NULL REFERENCES whatsapp_cloud_number(id),
  meta_template_id TEXT NOT NULL, name TEXT NOT NULL, language TEXT NOT NULL,
  category TEXT, status TEXT, parameter_format TEXT, components JSONB,
  quality_score TEXT, rejected_reason TEXT, synced_at TIMESTAMPTZ,
  UNIQUE (id_cloud_number, name, language)
);

CREATE TABLE whatsapp_template_binding (
  id BIGSERIAL PRIMARY KEY,
  id_workspace BIGINT NOT NULL REFERENCES workspace(id),
  purpose TEXT NOT NULL,
  id_meta_template UUID NOT NULL REFERENCES whatsapp_meta_template(id),
  param_mapping JSONB NOT NULL,
  created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ,
  UNIQUE (id_workspace, purpose)
);

CREATE TABLE order_followup_job (
  id BIGSERIAL PRIMARY KEY,
  id_order UUID NOT NULL REFERENCES "order"(id),
  id_workspace BIGINT NOT NULL,
  purpose TEXT NOT NULL,                     -- FOLLOWUP-COD | FOLLOWUP-TRANSFER
  due_at TIMESTAMPTZ NOT NULL,
  status TEXT NOT NULL,                      -- PENDING | PROCESSING | SENT_FREE | SENT_TEMPLATE | SKIPPED | CANCELLED | FAILED
  provider_used VARCHAR(20), attempt INT NOT NULL DEFAULT 0, last_error TEXT, id_chat UUID,
  created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ
);
CREATE INDEX idx_followup_due ON order_followup_job(status, due_at);

CREATE TABLE whatsapp_cloud_usage (
  id BIGSERIAL PRIMARY KEY,
  wamid TEXT NOT NULL UNIQUE,
  id_workspace BIGINT NOT NULL, id_cloud_number UUID NOT NULL, id_chat UUID,
  category TEXT, pricing_type TEXT, pricing_model TEXT, billable BOOLEAN NOT NULL,
  send_source VARCHAR(30), estimated_cost NUMERIC(14,2), currency VARCHAR(3),
  occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_usage_ws_time ON whatsapp_cloud_usage(id_workspace, occurred_at);

CREATE TABLE whatsapp_cloud_rate (
  id BIGSERIAL PRIMARY KEY, category TEXT NOT NULL, price NUMERIC(14,4) NOT NULL,
  currency VARCHAR(3) NOT NULL, effective_from DATE NOT NULL
);
```
Catatan:
- Tabel order bernama `"order"` (sesuai `@Table` di entity `Order`). `@EnableScheduling` sudah aktif di `ApiApplication`.
- Dengan `ddl-auto=update`, kolom dan tabel dibuat dari entity. Partial unique index, default `GOWA`, dan backfill butuh skrip `docs/sql/whatsapp-cloud-module.sql`.
- Backfill `conversation.last_inbound_at = MAX(chat.sent_at) WHERE pengirim='CUSTOMER'`.

---

## 9. API Summary

| Method | Path | Role | Keterangan |
|---|---|---|---|
| GET | `/whatsapp-cloud/webhook/{key}` | public | verifikasi Meta (FR-16) |
| POST | `/whatsapp-cloud/webhook/{key}` | public + HMAC | event (FR-17) |
| GET | `/whatsapp-cloud` | ADMIN | list nomor (FR-5) |
| GET | `/whatsapp-cloud/{id}` | ADMIN | detail + Callback URL/Verify Token |
| GET | `/whatsapp-cloud/available` | ADMIN/OWNER | nomor siap dipasang |
| POST | `/whatsapp-cloud` | ADMIN | registrasi (FR-2) |
| PUT | `/whatsapp-cloud/{id}` | ADMIN | update label/kredensial (FR-3) |
| POST | `/whatsapp-cloud/{id}/webhook/setup` | ADMIN | pasang ulang webhook (FR-4) |
| POST | `/whatsapp-cloud/{id}/check` | ADMIN | health check (FR-7) |
| POST | `/whatsapp-cloud/delete` | ADMIN | hapus (FR-6) |
| GET | `/whatsapp-cloud/{id}/templates` | ADMIN/OWNER | template Meta (FR-22) |
| POST | `/whatsapp-cloud/{id}/templates/sync` | ADMIN/OWNER | sinkron template |
| GET/PUT | `/whatsapp-cloud/rates` | ADMIN | rate card (FR-32) |
| GET | `/whatsapp-cloud/usage`, `/usage/export` | ADMIN/OWNER | laporan biaya (FR-33) |
| GET | `/workspace/{id}/whatsapp-setting` | ADMIN/OWNER | pengaturan (FR-9a) |
| PUT | `/workspace/{id}/whatsapp-setting/channel` | ADMIN | pasang nomor (FR-9b) |
| PUT | `/workspace/{id}/whatsapp-setting/provider` | ADMIN/OWNER | **switch provider** (FR-10) |
| PUT | `/workspace/{id}/whatsapp-setting/followup` | ADMIN/OWNER | delay + binding (FR-29) |
| POST | `/chat/send-template` | auth | kirim template (FR-25) |
| POST | `/chat/send-message` | auth | + error `WINDOW_CLOSED` (FR-23) |
| WS | `/topic/chatroom/{id}` | – | + `MESSAGE_STATUS_UPDATED` |
| WS | `/topic/workspace/{id}` | – | + `WHATSAPP_PROVIDER_CHANGED`, status nomor |

Endpoint gowa yang ada (`/whatsapp/**`) tidak berubah.

---

## 10. Dampak Frontend (ringkas)

- **Menu baru "WhatsApp Cloud API"** (terpisah dari menu WhatsApp Gateway): list nomor, form tambah/edit kredensial, status (connected/quality/webhook), tombol Cek Status, Setup Webhook, Sinkron Template, daftar template, dan halaman **Rate & Pemakaian**.
- **Workspace → Pengaturan WhatsApp:**
  - Slot nomor Gateway dan nomor Cloud.
  - **Switch provider aktif** dengan konfirmasi ("Pesan keluar akan dikirim dari nomor X").
  - Daftar blocker switch.
  - Pengaturan delay follow-up.
  - Binding template follow-up: pilih template, lalu petakan setiap `{{n}}` ke variabel order.
- **Inbox (Cloud):**
  - Indikator sisa window. Composer terkunci saat `windowOpen=false`, dengan tombol "Kirim template".
  - Modal pilih template + isi variabel.
  - Centang status pesan dan badge "via nomor lama".
- **Detail order:** status follow-up.
- **Blast:** nonaktif untuk workspace Cloud.

Detail frontend dibuat terpisah di `docs/prd/whatsapp-cloud-api-provider-frontend.md`.

---

## 11. Acceptance Criteria

- AC-1: Admin mendaftarkan nomor Cloud dengan kredensial valid. Status menjadi `CONNECTED`, webhook terverifikasi otomatis, dan template tersinkron.
- AC-2: Kredensial salah ditolak dengan pesan spesifik dan tidak ada data tersimpan.
- AC-3: Workspace A (gowa) dan B (Cloud) berjalan bersamaan. Pesan masuk tampil di inbox yang benar.
- AC-4: Switch workspace dari gowa ke Cloud membuat balasan agent dan bot berikutnya keluar lewat Cloud, dengan histori utuh. Switch ditolak kalau binding follow-up belum lengkap.
- AC-5: Webhook yang sama dikirim 3x hanya menghasilkan 1 `Chat` dan 1 balasan bot. Signature dengan App Secret yang salah ditolak `401`.
- AC-6: Order di workspace Cloud dengan delay 5 menit, lalu customer chat dalam 2 menit: follow-up terkirim sebagai **teks bebas** (`SENT_FREE`) dan usage tercatat `billable=false`.
- AC-7: Skenario yang sama tetapi customer tidak chat: follow-up terkirim sebagai **template UTILITY** (`SENT_TEMPLATE`) dan usage tercatat `billable=true` di workspace tersebut.
- AC-8: Aplikasi restart saat job follow-up masih `PENDING`. Job tetap terkirim setelah aplikasi kembali, tanpa duplikat.
- AC-9: Agent mengirim teks > 24 jam setelah pesan terakhir customer, lalu menerima `WINDOW_CLOSED` tanpa panggilan ke Meta.
- AC-10: Gambar produk WebP terkirim via Cloud sebagai JPEG.
- AC-11: Token dicabut di Meta. Nomor menjadi `DISCONNECTED` (`TOKEN_INVALID`) dan dashboard menerima event.
- AC-12: Laporan usage per workspace per bulan cocok dengan jumlah baris `billable=true`, dengan estimasi biaya dari rate card.

---

## 12. Rencana Rilis

| Fase | Isi | Estimasi backend |
|---|---|---|
| **0: Refactor** | FR-13–15. Provider layer + `GowaProvider`, `WhatsappMessageHandler` menerima model kanonik. **Tanpa perubahan perilaku** | 3–4 hari |
| **1: Go-live Cloud** | Modul nomor Cloud (FR-1–7), workspace setting & switch (FR-8–12), webhook per nomor + dedupe + status (FR-16–20), outbound + window + template (FR-21–26), follow-up delay job (FR-27–30), pencatatan usage (FR-31), blokir blast (FR-34) | 15–20 hari |
| **2: Biaya & UX** | Rate card + laporan/export (FR-32–33), read receipt & typing indicator, reaction, webhook `message_template_status_update` / quality / account | 5–7 hari |
| **3: Blast Cloud** | Blast via template marketing, throttle tier, opt-out 131050 | 6–8 hari |

Belum termasuk frontend dan QA.

---

## 13. Open Questions

| # | Pertanyaan | Rekomendasi |
|---|---|---|
| Q1 | Pesan masuk ke **nomor yang tidak aktif** setelah switch: apakah bot tetap membalas (dari nomor aktif, berbeda dengan nomor yang dihubungi customer)? | Simpan, tampilkan dengan badge, bot **tidak** membalas, dan tandai untuk agent (FR-12) |
| Q2 | Berapa **default delay** follow-up, dan apakah delay juga berlaku untuk workspace **gowa**? (Kode saat ini mengirim langsung, tanpa delay) | Default 300 detik, bisa diatur per workspace, berlaku di kedua provider agar perilaku konsisten. Mohon konfirmasi nilai delay yang selama ini diharapkan |
| Q3 | Kalau customer sudah mengirim pesan order (prefilled `wa.me`) dan bot sudah mengirim konfirmasi sebelum delay habis, apakah follow-up **tetap dikirim**? | Tetap dikirim (sama dengan perilaku sekarang). Opsi lain: `SKIPPED` kalau bot sudah konfirmasi order yang sama, lebih hemat dan tidak dobel |
| Q4 | Rate card & batas biaya: perlu **limit/budget bulanan per workspace** (mis. blokir template saat melewati budget)? | Fase 1–2 hanya laporan + estimasi. Budget limit sebagai PRD lanjutan |
| Q5 | Switch ke Cloud wajib binding follow-up lengkap (blok) atau cukup peringatan? | **Blok** (FR-10), supaya follow-up tidak hilang diam-diam |
| Q6 | Siapa yang boleh switch provider: hanya ADMIN, atau OWNER workspace juga? | ADMIN + OWNER workspace terkait. Pemasangan nomor hanya ADMIN |
| Q7 | Nomor yang sama dipindah dari gowa ke Cloud: perlu alur migrasi khusus? | Tidak. Hapus dari gateway, daftarkan di Cloud, lalu switch. Kontak tetap cocok karena berbasis nomor |
