# Review Teknis: Integrasi WhatsApp Cloud API (Dual Provider per Workspace)

| Field | Value |
|---|---|
| Topik | Kelayakan integrasi WhatsApp Cloud API (Meta) berdampingan dengan gateway gowa yang sudah ada |
| Requirement | Setiap workspace dapat memilih **WhatsApp Gateway (gowa)** atau **WhatsApp Cloud API** |
| Status | Review / analisis. **Belum ada perubahan kode** |
| Referensi | `docs/WhatsappCloudAPI/whatsapp-chat-integration.md`, `docs/WhatsappCloudAPI/whatsapp-cloud-api-reference.md`, `docs/architecture.md` |
| PRD terkait | `docs/prd/whatsapp-cloud-api-provider.md` |
| Tanggal | 2026-11-26 |

> Nomor baris mengacu pada working tree per 2026-11-26.

---

## 0. Pembaruan Keputusan (revisi 2, 2026-11-26)

Keputusan dari stakeholder setelah review awal. **Bagian ini menggantikan rekomendasi yang bertentangan di bawahnya** (terutama §5.1, §5.3, §5.5, §6, dan §9).

| # | Keputusan | Dampak ke desain |
|---|---|---|
| D1 | **Dashboard bisa switch antara gowa dan Cloud API per workspace. Cloud API menjadi modul terpisah** | Opsi B (§5.1) **dibatalkan**. Nomor Cloud disimpan di entitas & modul **terpisah** (`whatsapp_cloud_number`, package `whatsappcloud`, menu dashboard sendiri). `Workspace` mendapat `whatsapp_provider` + `cloud_number_id` (di samping `waba_id` gowa). Kedua channel bisa terpasang bersamaan, dan switch cukup mengganti `whatsapp_provider` |
| D2 | **Cloud API sudah terverifikasi dan siap pakai** | Prasyarat Meta (§9) terpenuhi. Embedded Signup **tidak dibutuhkan** dan dikeluarkan dari roadmap |
| D3 | **Admin mendaftarkan nomor. Kredensial Cloud API (client API) diisi di dashboard per nomor** | Kredensial **per nomor**: Phone Number ID, WABA ID, App ID, **App Secret**, access token, dan verify token. Webhook memakai **URL per nomor** (`/whatsapp/cloud/webhook/{webhookKey}`) agar HMAC divalidasi dengan App Secret milik nomor tersebut. Sistem memasang `override_callback_uri` per nomor secara otomatis |
| D4 | **Follow-up order tergantung kecepatan customer mengirim pesan setelah order. Follow-up punya delay setelah order dibuat** | Follow-up dijadwalkan (job persisten, tahan restart) dengan delay per workspace. Saat jatuh tempo: kalau customer sudah chat (window terbuka), kirim **teks bebas `ChatTemplate` (gratis)**. Kalau belum, kirim **template Meta UTILITY (berbayar)**. ⚠️ Di working tree saat ini **tidak ditemukan implementasi delay**: `OrderEventListener.autoFollowup` langsung jalan `@Async` setelah commit. Delay harus dibuat sebagai bagian dari fitur ini |
| D5 | **Biaya ditanggung per workspace** | Pemakaian berbayar dicatat per workspace dari field `pricing` di webhook status (ledger `whatsapp_cloud_usage`), dengan laporan bulanan per kategori dan estimasi biaya dari rate card yang bisa dikonfigurasi |

Dampak tambahan dari D1: karena workspace bisa switch sementara nomor lama masih menerima pesan, **pesan masuk dari channel yang tidak aktif tetap disimpan** (tidak hilang), tetapi semua pesan keluar memakai provider aktif. Lihat PRD FR-12.

---

## 1. Ringkasan Eksekutif

**Kesimpulan: integrasi FEASIBLE, dengan effort menengah–besar.** Pola event-driven yang sudah ada (webhook → `@Async` → `ChatAsyncEvent`/`IncomingChatEvent` → bot/WebSocket) bisa dipakai ulang hampir utuh. Bot, order pipeline, WebSocket, dan dashboard tidak perlu tahu provider mana yang dipakai.

Hambatan utamanya ada di tiga tempat:

1. **Kopling langsung ke gowa.** Semua pengiriman memanggil `WhatsappClientHelper` (kelas khusus gowa) secara langsung dari 4 lokasi berbeda. Model webhook (`WebhookEnvelopeV2`) juga khusus gowa. Perlu lapisan abstraksi provider (Strategy/Adapter).
2. **Aturan bisnis Meta yang tidak ada di gowa.** Ada *Customer Service Window* 24 jam, pesan yang dimulai bisnis wajib memakai **template yang disetujui Meta**, ada biaya per template, dan BSUID bisa menggantikan nomor HP. Ini berdampak ke **follow-up order otomatis** dan **blast**, yang saat ini mengirim teks bebas.
3. **Onboarding & kredensial.** Gowa cukup memakai pair code. Cloud API butuh WABA, Phone Number ID, access token, verifikasi bisnis di Meta, webhook HTTPS publik, dan validasi HMAC.

Rekomendasi: kerjakan dalam **3 fase**:

- **Fase 1 (MVP):** abstraksi provider + chat 2 arah + bot via Cloud API, dengan onboarding manual memakai token system user.
- **Fase 2:** template Meta untuk follow-up order, window 24 jam di composer, dan status centang.
- **Fase 3:** blast via template marketing, Embedded Signup (Tech Provider), dan fitur lanjutan.

---

## 2. Kondisi Saat Ini (As-Is)

### 2.1 Komponen yang terhubung ke WhatsApp

| # | Lokasi | Peran | Kopling ke gowa |
|---|---|---|---|
| 1 | `service/chat/WhatsappClientHelper.java:28-183` | HTTP client gowa: `/send/message`, `/send/image`, `/send/audio`, `/send/file`, `/send/video`, `/devices`, `/app/login-with-code` | **Total.** Basic auth global (`whatsapp.api.username/password`), header `X-Device-Id`, base URL tunggal `whatsapp.multidevice.api.url` |
| 2 | `controller/WhatsappController.java:30-42` | `POST /whatsapp/webhook` menerima `WebhookEnvelopeV2` | Body dideserialisasi langsung ke model gowa. Exception ditelan (`catch` kosong) |
| 3 | `service/chat/WhatsappService.java:21-34` | `@Async processWebhook2`, dispatch berdasarkan tipe payload | Tipe payload gowa (`MessagePayload`, `MessageEditedPayload`, dst.) |
| 4 | `service/chat/WhatsappMessageHandler.java:57-181` | Simpan `Contact`, `Conversation`, `Chat`, lalu publish event | Routing tenant memakai `webhook.getDeviceId()` → nomor (`:59`). Media diunduh dari `whatsapp.multidevice.api.url + "/" + path` (`:196`) |
| 5 | `service/chat/ChatService.java:167-185` (`sendToProvider`) | Kirim chat agent **dan bot** (bot memanggil `ChatService.messageHandler`) | Memanggil `client.sendXxx(deviceId, ...)` dengan `GoWaSendMessageRequest` |
| 6 | `service/chat/ChatMessagePersister.java:47-61` | Membentuk `ChatSendContext(deviceId = waba.id)` | `deviceId` adalah konsep gowa |
| 7 | `service/order/OrderEventListener.java:56-79` | Auto follow-up order (teks bebas dari `ChatTemplate`) | `client.sendMessage(waba.id, GoWaSendMessageRequest)` |
| 8 | `service/blast/BlastSenderService.java:29-56` | Blast teks/gambar | `client.sendImage/sendMessage(deviceId, ...)`. Deteksi "device off" berbasis string error gowa (`:70-75`) |
| 9 | `service/blast/BlastCampaignService.java:123-143` | Menentukan `deviceId` campaign (default `workspace.wabaId`) | Konsep `deviceId` |
| 10 | `service/WhatsappInstanceService.java` | Register device, pair code, delete device | Seluruh lifecycle khusus gowa |
| 11 | `entity/WhatsappBusinessApi.java` | `nomor_whatsapp`, `status`, `device_id`, `port`, `api_key`, `api_id` | Tidak ada kolom provider. `port`, `api_key`, dan `api_id` tidak dipakai |
| 12 | `entity/Workspace.java:27-31` | `waba_id` → satu nomor per workspace | Netral (bisa dipakai ulang) |

### 2.2 Titik yang **sudah netral** (tidak perlu diubah)

- `BotOrchestratorService`, `BotDecisionService`, `BotDelayManager`, `BotService`, dan LLM. Bot mengirim lewat `ChatService.messageHandler`, sehingga cukup provider di bawahnya yang diganti.
- `ChatEventPublisher` / WebSocket, `ConversationService`, `ChatMessageService`.
- Deteksi order dari teks (`isOrderMessage` regex). Teks masuk dari Cloud API tetap berupa teks.
- `OrderOrchestrationService.java:114`. Checkout mengembalikan `waba.nomorWhatsapp` untuk link `wa.me` dengan pesan prefilled. Ini **tetap berlaku** dan malah menguntungkan di Cloud API karena pesan dari customer membuka window 24 jam (lihat §4.2).
- Keamanan: `SecurityConfig` sudah `permitAll` untuk `/whatsapp/*/webhook`, sehingga endpoint baru `/whatsapp/cloud/webhook` otomatis publik.

### 2.3 Kekurangan as-is yang relevan
- Status pesan keluar selalu `SENT` (`ChatMessagePersister.saveSentChat`). Tidak ada delivered/read. Event `message.ack` gowa tidak dipetakan di `WebhookEnvelopeV2`.
- Tidak ada idempotensi webhook. Index `idx_chat_message_id` di `Chat` **tidak unique**. Gowa jarang mengirim ulang, tapi Meta **pasti retry** (hingga 7 hari) kalau respons lambat atau gagal.
- Tidak ada konsep "kapan terakhir customer mengirim pesan" di `Conversation`/`Contact`.

---

## 3. Gap Analysis: gowa vs Cloud API

| Aspek | Gowa (sekarang) | WhatsApp Cloud API | Dampak ke sistem |
|---|---|---|---|
| Identitas channel | `device_id` (= `waba.id`), JID `628xx@s.whatsapp.net` | `phone_number_id` + `waba_id` (Meta) | Kolom baru di `WhatsappBusinessApi`. Routing webhook memakai `metadata.phone_number_id` |
| Autentikasi | Basic auth global dari `application.properties` | Bearer token **per WABA/tenant** (system user token / BISU) | Token per baris harus disimpan **terenkripsi** |
| Onboarding nomor | Add device → pair code di HP | Verifikasi bisnis Meta, display name, register nomor (PIN), `subscribed_apps` | Flow registrasi baru. Pair code tidak berlaku |
| Status koneksi | `CONNECTED/DISCONNECTED` (belum sinkron, lihat PRD connection-status) | Status nomor dari `GET /{PHONE_NUMBER_ID}` dan webhook `account_update`, `phone_number_quality_update` | Health check berbeda per provider |
| Webhook in | Endpoint gowa, tanpa signature | `GET` verify challenge + `POST` dengan `X-Hub-Signature-256` (HMAC App Secret, **raw body**) | Controller baru. Body harus dibaca raw sebelum di-parse |
| Format webhook | `{event, device_id, payload}` | `{object, entry[].changes[].value{metadata, contacts, messages \| statuses \| errors}}`, batch | Parser/adapter baru. Wajib iterasi semua entry |
| Retry & duplikat | Praktis tidak ada | Retry hingga 7 hari, urutan tidak dijamin | **Wajib** dedupe berdasarkan `wamid` (+status) |
| Identitas customer | Nomor HP selalu ada | `wa_id` **bisa tidak ada** (user dengan username). BSUID `user_id` selalu ada | `Contact` perlu kolom `bsuid`. Semua logika yang mengandalkan `phoneNumber` harus toleran |
| Kirim teks/media bebas | Kapan saja | **Hanya dalam window 24 jam** sejak pesan terakhir customer (atau FEP 72 jam dari iklan CTWA) | Composer, bot, follow-up, dan blast harus sadar window. Error **131047** |
| Pesan yang dimulai bisnis | Teks bebas | **Wajib template APPROVED** (kategori MARKETING/UTILITY/AUTH) | Follow-up order & blast harus memakai template Meta |
| Biaya | Gratis (risiko banned nomor) | Per pesan template (marketing/utility/auth). Service message dan utility dalam window gratis | Perlu visibilitas biaya. Blast punya biaya nyata |
| Media keluar | URL dikirim ke gowa (gowa yang mengunduh) | `link` publik HTTPS atau upload `POST /{PHONE_ID}/media` → `id` | Bisa pakai URL publik MinIO. **Image hanya JPEG/PNG ≤5 MB** |
| Media masuk | File di server gowa (`path`) | `GET /{MEDIA_ID}` → URL **berlaku 5 menit** → download dengan Bearer | `MediaHelper.saveMediaFromUrl` perlu varian yang memakai header auth |
| Reply/quote | `reply_message_id` | `context.message_id` (wamid) | Pemetaan sederhana. `Chat.messageId` sudah menyimpan ID provider |
| Status pesan | Tidak dipakai | `sent → delivered → read → played`, `failed` + error code | Peluang fitur centang. Update status harus monotonic |
| Rate limit | Delay manual (blast `delayMs`) | 80 msg/s per nomor, messaging limit tier untuk business-initiated, error 130429/131056 | Backoff + antrian. Blast perlu throttle sesuai tier |
| Risiko banned | Tinggi (unofficial) | Rendah, tapi ada **quality rating** | Pantau `phone_number_quality_update`, error 131048 |

---

## 4. Analisis Dampak per Alur Bisnis

### 4.1 Chat agent (inbox)
- **Kirim:** `ChatService.sendToProvider` diganti dengan `WhatsappProviderRouter.resolve(waba).send(...)`. Kalau window tertutup (Cloud), composer teks harus diblokir dan agent diarahkan memilih template Meta.
- **Multi-image** (`chat-multiple-image`): Cloud API tidak punya album, jadi tetap dikirim satu per satu (perilakunya sama seperti sekarang). Caption hanya di gambar pertama, ≤1024 karakter.
- **Reply:** `repliedMessage.getMessageId()` → `context.message_id`.
- **Tipe ChatType** (TEXT/IMAGE/VIDEO/DOCUMENT/AUDIO) semuanya didukung Cloud API.

### 4.2 Order follow-up otomatis: dampak terbesar
- `OrderOrchestrationService.java:94-96` mempublikasikan `FOLLOWUP-COD/TRANSFER` **saat checkout di web**. Pada saat itu customer **belum pernah mengirim pesan**, sehingga window tertutup.
- `OrderEventListener.autoFollowup` mengirim teks bebas dari `ChatTemplate` (`MessageTemplateService.getFollowUpText:122-127`). Di Cloud API ini akan **gagal dengan error 131047**.
- **Solusi:** untuk workspace Cloud, follow-up memakai **template Meta kategori UTILITY** yang dipetakan ke tujuan (`FOLLOWUP-COD`, `FOLLOWUP-TRANSFER`, ...). Variabel template diisi dari `MessageConstructorHelper.buildOrderParams` (`nama_customer`, `nama_produk`, `harga_produk`, dst.).
- Alternatif yang lebih hemat biaya: checkout sudah mengarahkan customer ke `wa.me` dengan pesan prefilled. Pesan itu **dikirim customer**, sehingga window terbuka dan konfirmasi bot (`BotService.sendConfirmationMessage`) gratis dan bebas format. Opsi konfigurasi: *"kirim follow-up template hanya jika customer belum chat dalam X menit"*. Lihat PRD §12.
- `CONFIRMATION-*` (`:172-174`, order dibuat dari chat) biasanya terjadi dalam window. Tetap perlu fallback ke template kalau window tertutup.

### 4.3 Bot AI
- Bot hanya membalas pesan masuk, sehingga window **selalu terbuka**. Tidak ada perubahan logika.
- Nilai tambah opsional: kirim `read` + `typing_indicator` saat bot memproses (debounce `BotDelayManager`), agar terasa natural. Typing indicator hilang setelah 25 detik.
- Delay bot yang panjang tetap aman (jauh di bawah 24 jam).

### 4.4 Blast campaign
- Blast adalah pesan yang dimulai bisnis, sehingga **wajib template MARKETING** yang APPROVED. Konten bebas (`messageContent`) dan `ChatTemplate` internal tidak bisa dipakai.
- Ada biaya per pesan, messaging limit tier (jumlah user unik per 24 jam), throughput 80 msg/s, serta error 131049 (ditahan Meta) dan 131050 (user opt-out).
- `BlastSenderService.looksLikeDeviceOff` (berbasis string) diganti dengan klasifikasi berdasarkan `error.code`.
- `countReplied` (`BlastStatusService.markReplied`) tetap jalan karena didorong oleh pesan masuk.
- **Rekomendasi:** blast via Cloud masuk **Fase 3**. Di Fase 1–2, blast untuk workspace Cloud **dinonaktifkan** dengan pesan yang jelas.

### 4.5 Media
- **Masuk:** webhook berisi `media.id` (dan kadang `url`). Unduh segera (URL hanya berlaku 5 menit) dengan header `Authorization`, verifikasi `sha256`, lalu simpan ke MinIO lewat `MediaHelper`/`StorageService` yang sama.
- **Keluar:** `ChatService` sudah punya URL publik MinIO. Cloud API mendukung `link`, tapi MinIO harus HTTPS dan bisa diakses Meta. Link di-cache 10 menit. Upload (`/media`) lebih andal.
- ⚠️ **WebP:** `StorageService.uploadConverted` (`:243-254`) mengonversi gambar upload ke **WebP**. Cloud API **image message hanya menerima JPEG/PNG** (WebP hanya untuk sticker). Gambar produk/template yang di-share ke chat akan gagal (131053), jadi perlu konversi ke JPEG sebelum dikirim via Cloud.
- Batas ukuran: image 5 MB, video/audio 16 MB, dokumen 100 MB, caption 1024, teks 4096.

### 4.6 Kontak & identitas
- Upsert kontak di `WhatsappMessageHandler:67-78` memakai nomor HP. Di Cloud, cari kontak lewat `bsuid` dulu, lalu fallback ke `wa_id`/nomor. Kontak lama (dari era gowa) dicocokkan lewat nomor, lalu `bsuid` diisi.
- Kalau `wa_id` tidak ada (user dengan username), `Contact.phoneNumber` kosong. Dampaknya: pencocokan order ↔ kontak (`order.nomorWhatsapp`), export, dan blast berbasis nomor. Mitigasi: kirim ke `recipient` = BSUID, dan minta nomor via `request_contact_info` kalau dibutuhkan.
- Tangani `system.user_changed_number` / `user_changed_user_id` (update BSUID/nomor).

### 4.7 Status koneksi & manajemen nomor
- `WhatsappBusinessApiRepository.getAvailableWhatsapp` (`:40-47`) dan validasi `WorkspaceService:58,140` bergantung pada `status = CONNECTED`. Untuk Cloud, status diturunkan dari hasil `GET /{PHONE_NUMBER_ID}` (`status`, `quality_rating`, `name_status`) dan webhook `account_update`. Nilai `CONNECTED/DISCONNECTED` dipertahankan agar query lama tetap berlaku.
- `WhatsappInstanceService.deleteWhatsapp` memanggil `client.removeDevice`. Untuk Cloud cukup hapus kredensial (dan opsional `DELETE /{WABA}/subscribed_apps`). Nomor tidak di-deregister otomatis.
- PRD `whatsapp-connection-status.md` (polling gowa) perlu diselaraskan: scheduler memanggil `provider.checkHealth()`.

### 4.8 Template internal (`ChatTemplate`) vs template Meta
- `ChatTemplate` adalah template **internal** (teks + media + placeholder `{nama_customer}`), dipakai untuk quick reply, follow-up, dan blast. Template ini **tidak dikenal Meta**.
- Cloud membutuhkan entitas terpisah, **`WhatsappMetaTemplate`**, yang disinkronkan dari `GET /{WABA_ID}/message_templates` per WABA. Dua konsep ini harus tetap terpisah: `ChatTemplate` tetap dipakai di dalam window (Cloud) dan kapan saja (gowa).
- Pembuatan template Meta bisa dilakukan di WhatsApp Manager. API create (`POST message_templates`) cukup sebagai opsi Fase 3.

---

## 5. Arsitektur yang Diusulkan

### 5.1 Di mana pilihan provider disimpan?

> **Revisi:** keputusan akhir adalah **provider di `Workspace` + modul Cloud terpisah** (lihat §0 D1 dan PRD §4). Tabel di bawah dipertahankan sebagai catatan analisis awal.

| Opsi | Penjelasan | Penilaian |
|---|---|---|
| A. `provider` di `Workspace` | Workspace memilih GOWA/CLOUD. Kredensial tetap di `WhatsappBusinessApi` | Bisa tidak konsisten (workspace CLOUD dipasangi nomor gowa) |
| **B. `provider` di `WhatsappBusinessApi` (channel)** ✅ | Setiap nomor bertipe GOWA atau CLOUD_API. Provider workspace = provider nomor yang dipasang (`workspace.waba.provider`) | **Direkomendasikan.** Satu sumber kebenaran. Ganti provider = ganti nomor yang dipasang. UI workspace tetap menampilkan pilihan "Jenis koneksi" yang memfilter daftar nomor |

Dengan opsi B, kebutuhan "workspace bisa memilih gateway atau Cloud API" terpenuhi lewat langkah: pilih jenis koneksi → pilih nomor dengan jenis tersebut.

### 5.2 Lapisan abstraksi

```
                ┌──────────────────────────── outbound ────────────────────────────┐
ChatService ─┐                                                                    │
OrderEventListener ─┼─► WhatsappMessagingService (cek window, pilih template/free) │
BlastSenderService ─┘            │                                                │
                                 ▼                                                │
                      WhatsappProviderRouter.resolve(waba)                         │
                        ├─ GowaProvider      → WhatsappClientHelper (existing)     │
                        └─ CloudApiProvider  → CloudApiClient (Graph API, token)   │
                                                                                   │
                ┌──────────────────────────── inbound ──────────────────────────────┘
POST /whatsapp/webhook        → GowaWebhookAdapter  ─┐
GET|POST /whatsapp/cloud/webhook → CloudWebhookAdapter ─┼─► InboundMessage / StatusUpdate (model kanonik)
                                                       └─► WhatsappMessageHandler (refactor: terima model kanonik)
                                                              → Contact/Conversation/Chat → ChatAsyncEvent → Bot / WS
```

Kontrak inti (ilustratif):
```java
public interface WhatsappProvider {
    WhatsappProviderType type();                       // GOWA | CLOUD_API
    SendResult sendText(ChannelRef ch, Recipient to, String text, String replyToMessageId);
    SendResult sendMedia(ChannelRef ch, Recipient to, ChatType type, String mediaUrl, String caption);
    SendResult sendTemplate(ChannelRef ch, Recipient to, TemplateMessage tpl);   // gowa: UnsupportedOperation / render teks
    void markRead(ChannelRef ch, String messageId, boolean typing);              // gowa: no-op
    ChannelHealth checkHealth(ChannelRef ch);
}
record SendResult(boolean success, String messageId, String errorCode, String errorMessage,
                  boolean retryable, boolean windowClosed) {}
```
`SendResult` menggantikan `WhatsappResponse<SendResults>` (khusus gowa) di semua pemanggil, sehingga logika `"SUCCESS".equals(code)` hilang dari service bisnis.

### 5.3 Webhook Cloud API
1. `GET /whatsapp/cloud/webhook`: cocokkan `hub.verify_token`, balas `hub.challenge` (plain text).
2. `POST /whatsapp/cloud/webhook`: baca **raw body** (`@RequestBody byte[]` atau `String`), validasi `X-Hub-Signature-256` dengan App Secret (constant-time), **balas 200 segera**, lalu proses `@Async`.
3. Dedupe dengan tabel `whatsapp_webhook_event(dedupe_key PK)`: `in:{wamid}` dan `st:{wamid}:{status}`.
4. Routing tenant: `metadata.phone_number_id` → `WhatsappBusinessApi.cloudPhoneNumberId` → `Workspace`.
5. Satu Meta App bisa melayani banyak WABA. Satu callback URL cukup (opsional `override_callback_uri` per WABA).

Untuk volume saat ini, `@Async` + tabel dedupe sudah memadai. Queue eksternal (Redis/RabbitMQ) belum diperlukan, tapi pertimbangkan kalau volume webhook > ratusan per detik.

### 5.4 Customer Service Window
- Tambah `conversation.last_inbound_at` (diupdate setiap pesan CUSTOMER masuk, berlaku untuk kedua provider) dan `conversation.fep_expires_at` (referral CTWA).
- `windowOpen = now < last_inbound_at + 24h || now < fep_expires_at`. Hanya berlaku untuk CLOUD_API. Gowa selalu `true`.
- Expose `windowOpen` dan `windowExpiresAt` di detail percakapan agar frontend bisa mengunci composer.
- Error 131047 dari Meta adalah sumber kebenaran terakhir: tangkap, lalu kembalikan error terstruktur `WINDOW_CLOSED`.

### 5.5 Penyimpanan kredensial
- Access token (system user token / BISU) disimpan terenkripsi (AES-GCM, key dari env `WA_CLOUD_TOKEN_ENC_KEY`), tidak pernah dikembalikan ke API response, dan dimasking di log.
- App Secret, Verify Token, App ID, dan `GRAPH_VERSION` (mis. `v26.0`) disimpan di env/`application.properties` (satu Meta App untuk seluruh platform).

---

## 6. Perubahan Data Model (ringkas)

| Tabel | Perubahan |
|---|---|
| `whatsapp_business_api` | + `provider` (`GOWA` default, `CLOUD_API`), `cloud_phone_number_id` (unique), `cloud_waba_id`, `cloud_business_id`, `cloud_access_token_enc`, `cloud_token_expires_at`, `display_name`, `quality_rating`, `messaging_limit_tier`, `last_health_check_at` |
| `contact` | + `bsuid`, `wa_username`. Index `(id_workspace, bsuid)` |
| `conversation` | + `last_inbound_at`, `fep_expires_at` |
| `chat` | + `error_code`, `error_message`, `status_updated_at`. Nilai `status`: `PENDING/SENT/DELIVERED/READ/PLAYED/FAILED` |
| **baru** `whatsapp_webhook_event` | `dedupe_key` PK, `received_at` (retensi ±14 hari) |
| **baru** `whatsapp_meta_template` | `id_waba`, `meta_template_id`, `name`, `language`, `category`, `status`, `parameter_format`, `components` (jsonb), `quality_score`, `rejected_reason`, `synced_at`. Unique `(id_waba, name, language)` |
| **baru** `whatsapp_template_binding` | `id_workspace`, `purpose` (`FOLLOWUP-COD`, `FOLLOWUP-TRANSFER`, `CONFIRMATION-COD`, `CONFIRMATION-TRANSFER`), `id_meta_template`, `param_mapping` (jsonb, contoh `["nama_customer","nama_produk"]`) |

Semua kolom baru nullable dengan default aman. `ddl-auto=update` bisa menambahkannya. Data existing otomatis menjadi `provider = GOWA`, **tanpa migrasi data**.

---

## 7. Risiko & Mitigasi

| # | Risiko | Level | Mitigasi |
|---|---|---|---|
| R1 | Follow-up order gagal (131047) karena window tertutup | **Tinggi** | Template UTILITY wajib dikonfigurasi sebelum workspace Cloud bisa aktif. Validasi di aktivasi |
| R2 | Gambar WebP ditolak Cloud API | **Tinggi** | Konversi ke JPEG saat kirim via Cloud (helper `imgscalr`/ImageIO sudah ada di pom) |
| R3 | Duplikat chat & bot membalas dua kali karena retry webhook Meta | **Tinggi** | Tabel dedupe + cek `messageId` sebelum insert. Respons 200 < 1 detik |
| R4 | Token bocor / expired | Tinggi | Enkripsi at-rest, masking. Error 190 → status `DISCONNECTED` + event WebSocket ke admin |
| R5 | Kontak tanpa nomor HP (BSUID-only) merusak alur order/export | Sedang | Kolom `bsuid`. Kirim via `recipient`. UI tampilkan "@username" |
| R6 | Biaya Meta tak terkendali (blast, follow-up) | Sedang | Blast Cloud ditunda ke Fase 3. Tampilkan kategori template. Opsi follow-up hanya bila customer belum chat |
| R7 | Onboarding lambat (verifikasi bisnis, display name, approval template) | Sedang | Fase 1 pakai input manual (direct developer). Embedded Signup di Fase 3 |
| R8 | MinIO tidak HTTPS publik sehingga `link` media gagal | Sedang | Default **upload** ke `/media` (bukan `link`) |
| R9 | Migrasi nomor yang sama dari gowa ke Cloud | Sedang | Nomor harus logout dari gowa, lalu register di Cloud API. Histori percakapan tetap (kontak berbasis nomor) |
| R10 | Regresi alur gowa saat refactor | Sedang | `GowaProvider` membungkus `WhatsappClientHelper` tanpa mengubah perilaku. Uji regresi chat/bot/follow-up/blast |
| R11 | `WhatsappController` menelan exception, sehingga error Cloud tak terlihat | Rendah | Logging terstruktur + metrik di controller baru |

---

## 8. Estimasi Effort (backend, kasar)

| Fase | Lingkup | Estimasi |
|---|---|---|
| 1 | Abstraksi provider (refactor 4 pemanggil), `GowaProvider`, `CloudApiClient`, webhook Cloud (verify, HMAC, dedupe, parser), inbound text/media, outbound text/media (upload + JPEG), registrasi nomor Cloud manual, health check, routing per workspace | 8–12 hari |
| 2 | `last_inbound_at` + window di API, sinkron template Meta, template binding follow-up/confirmation, kirim template dari composer, status pesan (centang), read receipt/typing | 6–9 hari |
| 3 | Blast via template marketing (throttle, error code), Embedded Signup (Tech Provider), interactive buttons/list, reaction, webhook quality/account | 8–12 hari |

Belum termasuk frontend, QA, dan waktu tunggu proses di sisi Meta (verifikasi bisnis dan approval template bisa memakan beberapa hari).

---

## 9. Prasyarat Non-Teknis (di sisi Meta)

- [ ] Meta Business Portfolio sudah **terverifikasi**.
- [ ] Meta App (use case WhatsApp) dalam mode **Live**, dengan App Secret dan Verify Token.
- [ ] Untuk setiap tenant: WABA, nomor (tidak sedang aktif di WhatsApp app/gowa), display name disetujui, nomor sudah di-`register`.
- [ ] System user token permanen (`whatsapp_business_messaging`, `whatsapp_business_management`), di-assign ke WABA.
- [ ] Callback URL **HTTPS** dengan sertifikat valid yang mengarah ke backend (`/whatsapp/cloud/webhook`).
- [ ] Template UTILITY untuk follow-up order sudah APPROVED (bahasa `id`).
- [ ] Metode pembayaran di WABA (untuk template berbayar).
- [ ] Keputusan model bisnis: **direct developer** (tiap klien punya app/token sendiri, dimasukkan manual) atau **Tech Provider** (Embedded Signup, butuh App Review). Lihat PRD §12.

---

## 10. Rekomendasi

1. **Lanjutkan** dengan pendekatan *dual provider* dan provider melekat di `WhatsappBusinessApi` (opsi B).
2. Mulai dengan **refactor abstraksi provider tanpa perubahan perilaku** (gowa tetap jalan) sebagai PR terpisah, agar risiko regresi terisolasi.
3. Jangan izinkan workspace Cloud **aktif** sebelum template follow-up terpasang (R1). Ini guard bisnis paling penting.
4. Tunda blast Cloud dan Embedded Signup ke Fase 3.
5. Detail requirement ada di `docs/prd/whatsapp-cloud-api-provider.md`.
