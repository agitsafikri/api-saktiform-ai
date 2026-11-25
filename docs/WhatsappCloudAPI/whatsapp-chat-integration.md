# Integrasi Fitur Chat: WhatsApp Cloud API

> Panduan fokus agar **fitur chat (inbox 2 arah)** berjalan: terima pesan, balas, media, status centang, read receipt, typing indicator, reply/quote, reaction, dan fallback template.
> Disusun 26 Nov 2026 dari guide dan reference resmi Meta (`developers.facebook.com/documentation/business-messaging/whatsapp/...`). Batas karakter di dokumen ini **sudah dicek ke guide masing-masing tipe pesan**.
> Versi API: contoh memakai `v26.0`. **v27.0 sudah rilis 4 Nov 2026** dan dipakai di beberapa contoh terbaru dokumentasi. Simpan versi di config (`GRAPH_VERSION`).

---

## 0. Ringkasan: endpoint yang dibutuhkan untuk chat

| # | Kebutuhan | Method & Endpoint |
|---|---|---|
| 1 | Terima pesan & status | **Webhook** (GET verifikasi, POST event), field `messages` |
| 2 | Aktifkan webhook untuk WABA | `POST /{WABA_ID}/subscribed_apps` |
| 3 | Kirim semua jenis pesan | `POST /{PHONE_NUMBER_ID}/messages` |
| 4 | Read receipt + typing indicator | `POST /{PHONE_NUMBER_ID}/messages` (`status: "read"`) |
| 5 | Upload media keluar | `POST /{PHONE_NUMBER_ID}/media` |
| 6 | Ambil URL media masuk | `GET /{MEDIA_ID}` |
| 7 | Download media masuk | `GET <url>` + header Authorization |
| 8 | Template saat window tertutup | `GET /{WABA_ID}/message_templates` (list) + kirim via #3 |
| 9 | Blokir user (opsional) | `POST/GET/DELETE /{PHONE_NUMBER_ID}/block_users` |
| 10 | Info nomor & profil (opsional) | `GET /{PHONE_NUMBER_ID}`, `GET /{PHONE_NUMBER_ID}/whatsapp_business_profile` |

Base URL `https://graph.facebook.com/{GRAPH_VERSION}`, header `Authorization: Bearer <SYSTEM_USER_TOKEN>`. Permission yang dibutuhkan: `whatsapp_business_messaging` (kirim pesan dan menerima webhook pesan) dan `whatsapp_business_management` (template, WABA, subscribed_apps).

---

## 1. Arsitektur yang disarankan

```
WhatsApp ──POST webhook──▶ [Webhook Receiver]
                              │ 1. verifikasi X-Hub-Signature-256
                              │ 2. balas 200 SECEPATNYA
                              ▼
                          [Queue] (Redis/BullMQ, RabbitMQ, dsb.)
                              ▼
                          [Worker]
                              ├─ dedupe (wamid + status)
                              ├─ resolve tenant via metadata.phone_number_id
                              ├─ upsert contact (BSUID)
                              ├─ simpan message / update status
                              ├─ unduh media (URL hanya 5 menit)
                              └─ push realtime ke UI agent (WebSocket/SSE)

UI Agent ──REST──▶ [Send API] ──▶ cek window 24 jam ──▶ POST /messages
                                    └─ tertutup → paksa pilih template
```

Prinsip:
- **Webhook harus balas 200 cepat.** Kalau gagal, Meta retry hingga 7 hari dan duplikat bisa terjadi, jadi worker wajib idempoten.
- **Urutan webhook tidak dijamin.** Urutkan berdasarkan `timestamp`, dan jangan menurunkan status (misalnya dari `read` kembali ke `delivered`).
- **Respons sukses dari `/messages` hanya berarti "diterima"**, belum terkirim. Status sebenarnya datang lewat webhook.

---

## 2. Model data minimum

```sql
-- Kontak: kunci utama BSUID, bukan nomor HP
contacts(
  id, workspace_id, phone_number_id,
  bsuid TEXT NOT NULL,            -- contacts[].user_id / from_user_id
  parent_bsuid TEXT NULL,
  wa_id TEXT NULL,                -- nomor; BISA KOSONG (user pakai username)
  username TEXT NULL, profile_name TEXT,
  last_inbound_at TIMESTAMPTZ,    -- untuk window 24 jam
  fep_expires_at TIMESTAMPTZ NULL,-- free entry point 72 jam (CTWA)
  blocked BOOL DEFAULT false, marketing_opt_out BOOL DEFAULT false,
  UNIQUE(phone_number_id, bsuid)
)

messages(
  id, contact_id, direction ENUM('in','out'),
  wamid TEXT UNIQUE,              -- messages[].id / response messages[0].id
  type TEXT, payload JSONB,       -- simpan raw untuk tipe apa pun
  text TEXT NULL, media_id TEXT NULL, media_path TEXT NULL,
  reply_to_wamid TEXT NULL,       -- context.id / context.message_id
  status ENUM('pending','accepted','sent','delivered','read','played','failed'),
  error_code INT NULL, error_title TEXT NULL,
  pricing JSONB NULL,
  wa_timestamp TIMESTAMPTZ, created_at TIMESTAMPTZ
)

reactions(message_wamid, actor ENUM('user','business'), emoji TEXT NULL, updated_at)
webhook_events(dedupe_key TEXT PRIMARY KEY, received_at)  -- idempotensi
```

**Kenapa BSUID?** Meta menyarankan identifikasi user lewat `user_id` / `from_user_id`. Field `wa_id` dan `from` tidak dikirim kalau user memakai username dan Anda tidak memenuhi syarat untuk melihat nomornya, yaitu dalam 30 hari terakhir Anda pernah mengirim/menerima pesan atau telepon dari nomor itu, atau user ada di contact book.

- Format BSUID: `CC.<≤128 alfanumerik>` (contoh `ID.1349120865530274`). Simpan **persis apa adanya**.
- BSUID unik per pasangan business portfolio dan user, dan **dibuat ulang kalau user ganti nomor**. Tangani webhook `system` (lihat bagian 4).

---

## 3. Webhook: setup & verifikasi

### 3.1 Daftarkan
1. App Dashboard → WhatsApp → Configuration: isi **Callback URL** (HTTPS dengan sertifikat valid) dan **Verify Token**.
2. Subscribe field **`messages`**. Tambahkan `message_template_status_update`, `user_preferences`, dan `account_update` kalau perlu.
3. `POST /{WABA_ID}/subscribed_apps` → `{ "success": true }`. Tanpa langkah ini, webhook WABA tidak terkirim.
   - Opsional, override URL per WABA: body `{ "override_callback_uri": "https://.../wa/hook/<tenant>", "verify_token": "..." }`.
   - Override per nomor: `POST /{PHONE_NUMBER_ID}` dengan `{ "webhook_configuration": { "override_callback_uri": "...", "verify_token": "..." } }`.
   - Prioritas: nomor > WABA > app.
4. App harus dalam mode **Live**.

### 3.2 Verifikasi (GET)
`GET /webhook?hub.mode=subscribe&hub.verify_token=XXX&hub.challenge=12345`: kalau token cocok, balas **200 dengan body `12345`** (plain text).

### 3.3 Event (POST)
Header `X-Hub-Signature-256: sha256=<hex>` berisi HMAC-SHA256 dari **raw body** dengan **App Secret** sebagai key. Bandingkan memakai perbandingan constant-time.

Amplop payload:
```json
{ "object": "whatsapp_business_account",
  "entry": [ { "id": "<WABA_ID>", "changes": [ { "field": "messages", "value": {
      "messaging_product": "whatsapp",
      "metadata": { "display_phone_number": "6281...", "phone_number_id": "<PHONE_NUMBER_ID>" },
      "contacts": [ ... ], "messages": [ ... ]   // pesan masuk
      // ATAU "statuses": [ ... ]                 // status pesan keluar
      // ATAU "errors": [ ... ]                   // error sistem
  } } ] } ] }
```
Satu POST bisa berisi banyak `entry`, `changes`, dan `messages`. Selalu iterasi semuanya.

---

## 4. Webhook pesan masuk (`value.messages[]`)

### 4.1 Identitas
```json
"contacts": [ { "profile": { "name": "Budi", "username": "@budi" },
                "wa_id": "6281234567890",         // bisa tidak ada
                "user_id": "ID.1349120865530274",  // BSUID, selalu ada
                "parent_user_id": "..." } ],
"messages": [ { "from": "6281234567890",           // bisa tidak ada
                "from_user_id": "ID.1349120865530274",
                "id": "wamid.HBgL...", "timestamp": "1764090000", "type": "text", ... } ]
```

### 4.2 Tipe pesan yang harus ditangani UI chat
| `type` | Field | Tampilan di inbox |
|---|---|---|
| `text` | `text.body` | bubble teks |
| `image` | `image { id, mime_type, sha256, caption, url? }` | thumbnail + caption |
| `video` | `video { id, mime_type, sha256, caption, url? }` | player |
| `audio` | `audio { id, mime_type, sha256, voice, url? }` | `voice: true` berarti voice note |
| `document` | `document { id, mime_type, sha256, filename, caption, url? }` | ikon file + nama |
| `sticker` | `sticker { id, mime_type, sha256, animated, url? }` | stiker |
| `location` | `location { latitude, longitude, name?, address?, url? }` | peta |
| `contacts` | `contacts[] { name, phones[], emails[], org, urls[], addresses[], birthday }` | kartu kontak |
| `interactive` | `button_reply { id, title }` / `list_reply { id, title, description }` / `nfm_reply { name, body, response_json }` | jawaban tombol / list / form Flow |
| `button` | `button { payload, text }` | klik quick reply pada **template** |
| `reaction` | `reaction { message_id, emoji }` (tanpa `emoji` = reaksi dihapus) | emoji di bubble target |
| `order` | `order { catalog_id, text, product_items[] }` | kartu pesanan |
| `system` | `system { type: user_changed_number \| user_changed_user_id, body, wa_id, user_id, previous_user_id, ... }` | info: "user ganti nomor" (**update kontak!**) |
| `request_welcome` | | user membuka chat pertama kali (kalau welcome message aktif) |
| `unsupported` | `errors[]` (131051 / 131060) | "Pesan tidak didukung" |

### 4.3 Konteks
- `context { from, id }`: user **membalas/quote** pesan `id` (atau klik tombol pada pesan itu).
- `context { forwarded: true }` / `{ frequently_forwarded: true }`: label "Diteruskan".
- `context { referred_product { catalog_id, product_retailer_id } }`: user bertanya soal produk dari katalog.
- `referral { source_url, source_type, headline, body, ctwa_clid, ... }`: user datang dari **iklan Click-to-WhatsApp**. Buka window gratis 72 jam (FEP) **kalau Anda membalas dalam 24 jam**.

### 4.4 Yang wajib dilakukan setiap ada pesan masuk
1. Dedupe berdasarkan `messages[].id`.
2. Upsert kontak berdasarkan `(phone_number_id, from_user_id)`. Update `wa_id` dan `profile.name` kalau ada.
3. **`last_inbound_at = timestamp`**, yang membuka atau mereset window 24 jam.
4. Kalau pesan berisi media: unduh segera (bagian 7).
5. Kalau `type = system` dengan `user_changed_user_id`: cari kontak lewat `previous_user_id`, lalu ganti BSUID-nya.
6. Push ke UI.

---

## 5. Webhook status (`value.statuses[]`)

```json
"statuses": [ { "id": "wamid.HBgL...", "status": "delivered", "timestamp": "1764090100",
  "recipient_id": "6281...", "recipient_user_id": "ID.1349...",
  "biz_opaque_callback_data": "msg_8812",
  "pricing": { "billable": true, "pricing_model": "PMP", "type": "regular", "category": "utility" },
  "errors": [ { "code": 131047, "title": "Re-engagement message", "error_data": { "details": "..." } } ] } ]
```

| `status` | Centang di UI |
|---|---|
| `sent` | ✓ |
| `delivered` | ✓✓ |
| `read` | ✓✓ biru |
| `played` | voice note sudah diputar (sejak 17 Mar 2026) |
| `failed` | ⚠️ tampilkan `errors[0].title` + tombol "kirim ulang / pakai template" |

Aturan update: rank `failed` > `played` > `read` > `delivered` > `sent`, dan status hanya boleh naik. Status `read` bisa datang tanpa `delivered` sebelumnya.
Pakai `biz_opaque_callback_data` (isi ID internal saat kirim) supaya pencocokan tidak bergantung pada wamid.
Kalau tidak ada `delivered` sampai TTL (default 30 hari), anggap pesan hilang.

---

## 6. Mengirim pesan: `POST /{PHONE_NUMBER_ID}/messages`

### 6.1 Field umum
```json
{ "messaging_product": "whatsapp",
  "recipient_type": "individual",
  "to": "+6281234567890",           // ATAU "recipient": "ID.1349..." (BSUID)
  "context": { "message_id": "wamid..." },   // opsional: reply/quote
  "biz_opaque_callback_data": "msg_8812",    // opsional: tracking
  "type": "<tipe>", "<tipe>": { ... } }
```
- **Kirim ke BSUID**: isi `recipient` dan hapus `to`. Kalau keduanya diisi, `to` yang dipakai. BSUID dari portfolio lain akan gagal. Template authentication (one-tap, zero-tap, copy code) **tidak bisa** dikirim ke BSUID (error **131062**).
- Nomor: sertakan `+` dan kode negara. Kalau tanpa kode negara, kode negara nomor bisnis ditambahkan otomatis, sehingga pesan bisa salah kirim.
- `messaging_account_id`: hanya kalau token punya akses ke lebih dari satu Messaging account di nomor yang sama.

Response:
```json
{ "messaging_product": "whatsapp",
  "contacts": [ { "input": "+6281234567890", "wa_id": "6281234567890" } ],
  "messages": [ { "id": "wamid.HBgL..." } ] }
```
Kalau dikirim ke BSUID, `contacts` berisi `input` dan `user_id` (tanpa `wa_id`). Untuk template yang sedang di-pacing, response juga berisi `message_status`.

### 6.2 Payload per tipe (batas sesuai guide resmi)

**Text**: `body` maksimal **4096** karakter. Preview hanya untuk URL pertama yang diawali `http(s)://`.
```json
"type": "text", "text": { "preview_url": true, "body": "Halo Kak, pesanan sudah kami proses https://contoh.id/o/1" }
```

**Image**: JPEG/PNG (8-bit RGB/RGBA), **≤5 MB**, caption **≤1024**
```json
"type": "image", "image": { "id": "<MEDIA_ID>", "caption": "Foto produk" }
```
**Video**: MP4/3GPP, **≤16 MB**, H.264 + AAC (gunakan profil Main/Baseline tanpa B-frame dan `-movflags faststart`), caption ≤1024
```json
"type": "video", "video": { "id": "<MEDIA_ID>", "caption": "Tutorial" }
```
**Audio**: AAC/AMR/MP3/M4A/OGG, **≤16 MB**. **Voice note** harus `.ogg` OPUS mono dengan `"voice": true`. Ikon play muncul kalau file ≤512 KB.
```json
"type": "audio", "audio": { "id": "<MEDIA_ID>", "voice": true }
```
**Document**: txt, pdf, doc(x), xls(x), ppt(x), **≤100 MB**, caption ≤1024. `filename` menentukan ikon file.
```json
"type": "document", "document": { "id": "<MEDIA_ID>", "filename": "invoice-INV001.pdf", "caption": "Invoice" }
```
**Sticker**: WebP statis ≤100 KB / animasi ≤500 KB
```json
"type": "sticker", "sticker": { "id": "<MEDIA_ID>" }
```
Untuk semua media, gunakan `id` (hasil upload) lebih dulu. `link` publik juga bisa, tapi di-cache 10 menit (tambahkan query string acak untuk memaksa refresh).

**Location**: `latitude` dan `longitude` wajib (desimal), `name` dan `address` opsional
```json
"type": "location", "location": { "latitude": "-6.8593", "longitude": "107.9238", "name": "Toko", "address": "Jl. ..." }
```

**Reaction**: hanya ke pesan **dari user** yang berumur ≤30 hari, ada di thread, tidak dihapus, dan bukan reaction. Hanya webhook `sent` yang datang (tidak ada delivered/read). Kalau gagal, webhook berisi error **131009**.
```json
"type": "reaction", "reaction": { "message_id": "wamid...", "emoji": "👍" }
```

**Reply / quote**: tambahkan `context.message_id`. Tidak berlaku untuk reaction. Bubble quote tidak tampil di template, dan tidak tampil kalau pesan asli dihapus atau sudah diarsip (±30 hari).

**Interactive reply buttons**: maksimal **3 tombol**, label **≤20**, id **≤256** (unik), body **≤1024**, footer **≤60**. Header bisa `text`, `image`, `video`, atau `document`.
```json
"type": "interactive", "interactive": { "type": "button",
  "body": { "text": "Konfirmasi pesanan #INV001?" }, "footer": { "text": "Saktiform" },
  "action": { "buttons": [
    { "type": "reply", "reply": { "id": "confirm:INV001", "title": "Ya, proses" } },
    { "type": "reply", "reply": { "id": "cancel:INV001",  "title": "Batalkan" } } ] } }
```
**Interactive list**: tombol **≤20**, body **≤4096**, header teks **≤60**, footer ≤60, **1–10 section** dengan judul ≤24, **total ≤10 row**. Row: id ≤200, title ≤24, description ≤72.
```json
"type": "interactive", "interactive": { "type": "list",
  "header": { "type": "text", "text": "Pilih Pengiriman" },
  "body": { "text": "Mau dikirim pakai apa?" },
  "action": { "button": "Pilih kurir", "sections": [
    { "title": "Reguler", "rows": [ { "id": "jne_reg", "title": "JNE REG", "description": "2–3 hari" } ] },
    { "title": "Instan",  "rows": [ { "id": "gosend", "title": "GoSend Instant", "description": "Hari ini" } ] } ] } }
```
**CTA URL**: label ≤20, body ≤1024, footer ≤60, header (text ≤60 atau media lewat `link`)
```json
"type": "interactive", "interactive": { "type": "cta_url",
  "body": { "text": "Cek status pesanan Anda" },
  "action": { "name": "cta_url", "parameters": { "display_text": "Lacak", "url": "https://contoh.id/t/INV001" } } }
```
**Minta lokasi**: body ≤1024. Balasan user datang sebagai webhook `type: location`.
```json
"type": "interactive", "interactive": { "type": "location_request_message",
  "body": { "text": "Boleh kirim lokasi pengiriman?" }, "action": { "name": "send_location" } }
```
**Minta nomor HP** (untuk user yang memakai username): kirim interactive `request_contact_info` atau template dengan tombol `REQUEST_CONTACT_INFO`. Hasilnya datang sebagai webhook `contacts` dengan `origin: contact_request`.

**Template** (wajib dipakai kalau window 24 jam tertutup):
```json
"type": "template", "template": { "name": "follow_up_order", "language": { "code": "id" },
  "components": [ { "type": "body", "parameters": [
      { "type": "text", "text": "Budi" }, { "type": "text", "text": "INV001" } ] } ] }
```

### 6.3 Read receipt + typing indicator
```json
POST /{PHONE_NUMBER_ID}/messages
{ "messaging_product": "whatsapp", "status": "read", "message_id": "wamid...<pesan masuk>",
  "typing_indicator": { "type": "text" } }   // opsional
→ { "success": true }
```
- Menandai satu pesan juga **menandai semua pesan sebelumnya** di chat 1:1 itu. Di grup, setiap pesan harus ditandai satu per satu.
- Pesan harus ditandai dalam **30 hari**. ID yang salah menghasilkan error **131009**.
- Typing indicator hilang saat Anda membalas, atau setelah **25 detik**. Kirim hanya kalau memang akan membalas.
- Pola di inbox: kirim `read` saat agent membuka chat, dan kirim `read` + typing saat agent mulai mengetik atau bot sedang memproses.

---

## 7. Media

### 7.1 Kirim (upload dulu)
```bash
curl -X POST "https://graph.facebook.com/v26.0/<PHONE_NUMBER_ID>/media" \
  -H "Authorization: Bearer $TOKEN" \
  -F "messaging_product=whatsapp" -F "file=@invoice.pdf;type=application/pdf"
# → { "id": "<MEDIA_ID>" }   (berlaku ±30 hari; bisa dipakai berulang)
```
Hapus: `DELETE /{MEDIA_ID}`.

### 7.2 Terima (unduh segera)
```
GET /{MEDIA_ID}            → { url, mime_type, sha256, file_size, id }   ← url hanya berlaku 5 MENIT
GET <url> + Authorization  → biner file  (404 berarti url kedaluwarsa; minta url baru)
```
Webhook media yang lebih baru sudah menyertakan `url` langsung. Tetap sediakan fallback lewat `GET /{MEDIA_ID}`.
Simpan file ke storage sendiri (S3/MinIO/disk VPS), verifikasi `sha256`, lalu simpan path-nya di `messages.media_path`.
Kalau file dari user tidak bisa diunduh, Meta mengirim error **131052**. Minta user mengirim ulang.

---

## 8. Customer Service Window (wajib di logika "kirim")

```
window_open = now() < last_inbound_at + 24h     (atau now() < fep_expires_at)
if window_open:   boleh kirim tipe apa pun (gratis; template utility juga gratis)
else:             HANYA template yang disetujui  → UI: kunci composer, tampilkan pemilih template
```
- Setiap pesan atau telepon masuk dari user mereset timer 24 jam.
- Kalau mengirim non-template saat window tertutup, hasilnya error **131047**. Tangkap error ini, tandai window tertutup, lalu minta agent memilih template.
### 8.1 API daftar template (untuk pemilih template di composer)

**List**: `GET /{WABA_ID}/message_templates`
| Query | Keterangan |
|---|---|
| `fields` | daftar field, dipisah koma. Untuk pemilih template cukup `id,name,language,category,status,components,parameter_format,quality_score,rejected_reason,last_updated_time` |
| `limit` | jumlah per halaman (min 1) |
| `after` / `before` | cursor pagination |
| `status` | filter status, mis. `status=approved` (didokumentasikan di guide Template Management) |

```bash
curl "https://graph.facebook.com/v26.0/<WABA_ID>/message_templates?fields=id,name,language,category,status,components,parameter_format&status=approved&limit=100" \
  -H "Authorization: Bearer $TOKEN"
```
```json
{ "data": [ { "id": "1387372356726668", "name": "follow_up_order", "language": "id",
      "category": "UTILITY", "status": "APPROVED", "parameter_format": "POSITIONAL",
      "components": [ { "type": "BODY", "text": "Halo {{1}}, pesanan {{2}} ...",
                        "example": { "body_text": [["Budi","INV001"]] } } ] } ],
  "paging": { "cursors": { "before": "QVFIU...", "after": "QVFIU..." }, "next": "https://graph.facebook.com/..." } }
```
- Ikuti `paging.next` sampai habis. Satu template punya **satu entri per bahasa** (nama sama, `language` berbeda).
- Filter `name`, `category`, `language`, dan `summary` **tidak terdokumentasi** sebagai query param. Lakukan filter di sisi aplikasi.
- Sejak v24, cursor yang kedaluwarsa atau tidak valid mengembalikan error khusus. Ulangi dari halaman pertama.
- Error umum: 100 (parameter), 190 (token), 200 (tidak punya akses), 803 (WABA tidak ditemukan), 2 (transient).

**Detail satu template**: `GET /{TEMPLATE_ID}?fields=...` (field sama dengan list).

**Enum penting untuk UI**:
- `status`: `APPROVED, PENDING, REJECTED, PAUSED, DISABLED, IN_APPEAL, LIMIT_EXCEEDED, ARCHIVED, DELETED, PENDING_DELETION`. **Hanya `APPROVED` yang bisa dikirim.**
- `category`: `MARKETING, UTILITY, AUTHENTICATION, FREE_SERVICE`
- `quality_score.score`: `GREEN, YELLOW, RED, UNKNOWN`
- `parameter_format`: `POSITIONAL` (`{{1}}`) \| `NAMED` (`{{nama}}`)

**Pola cache yang disarankan**: sinkronkan semua template per WABA ke database (cron atau saat startup), lalu perbarui otomatis lewat webhook `message_template_status_update`, `message_template_quality_update`, dan `template_category_update`. Pemilih template cukup membaca dari database. Parse `components` untuk menghitung jumlah variabel `{{n}}` di HEADER, BODY, dan tombol URL, lalu buat form isian sebelum kirim. Ini mencegah error 132000 dan 132012.

- Hanya kirim ke user yang sudah **opt-in**. Hormati webhook `user_preferences` (`value: "stop"` untuk marketing) dan error **131050**.

---

## 9. Error yang penting untuk chat

| Code | Arti | Tindakan di aplikasi |
|---|---|---|
| 131047 | Lebih dari 24 jam sejak pesan terakhir user | paksa pakai template |
| 131026 | Tidak bisa dikirim (bukan user WA, belum menerima ToS, app terlalu lama) | tandai kontak tidak terjangkau |
| 131009 | Nilai parameter tidak valid (mis. wamid salah untuk read/reaction) | log, jangan retry |
| 131008 | Parameter wajib tidak ada | perbaiki payload |
| 131051 | Tipe pesan tidak didukung | tampilkan "tidak didukung" |
| 131052 / 131053 | Gagal unduh / upload media | minta kirim ulang / cek format |
| 131056 | Terlalu banyak pesan ke user yang sama dalam waktu singkat | jeda, lalu retry |
| 130429 | Throughput nomor terlampaui (default 80 msg/s) | antrikan + backoff |
| 131048 | Dibatasi karena spam / kualitas buruk | hentikan blast, cek quality |
| 131049 | Ditahan untuk menjaga engagement (marketing) | jangan kirim ulang < 24 jam |
| 131050 | User berhenti berlangganan marketing | set `marketing_opt_out` |
| 131062 | Tipe pesan tidak bisa dikirim ke BSUID | kirim ke nomor HP |
| 130403 | Bisnis memblokir user ini | unblock dulu |
| 131021 | Pengirim = penerima | validasi input |
| 132000 / 132001 / 132012 | Jumlah parameter template salah / template tidak ada untuk bahasa itu / format variabel salah | validasi template sebelum kirim |
| 132015 / 132016 | Template di-pause / dinonaktifkan karena kualitas rendah | ganti template |
| 131000 / 131016 / 1 / 2 | Error tidak dikenal / layanan sementara tidak tersedia | retry dengan backoff |
| 0 / 190 | Token tidak valid / expired | ganti token, alert admin |
| 10 / 200 | Permission tidak ada | cek assignment system user |
| 368 / 131031 | Akun dibatasi atau dinonaktifkan | alert admin |

Retry yang disarankan: exponential backoff (mis. 1 s, 4 s, 16 s, 64 s) hanya untuk error transient (`is_transient: true`, 130429, 131056, 131000, 131016, 1, 2, HTTP 5xx).

---

## 10. Contoh kode (Node.js / Express)

```js
// config: GRAPH_VERSION=v26.0, WA_TOKEN, APP_SECRET, VERIFY_TOKEN
import express from 'express';
import crypto from 'node:crypto';
const G = `https://graph.facebook.com/${process.env.GRAPH_VERSION}`;
const H = { Authorization: `Bearer ${process.env.WA_TOKEN}`, 'Content-Type': 'application/json' };
const app = express();

// ---------- 1. Verifikasi webhook ----------
app.get('/wa/webhook', (req, res) => {
  const { 'hub.mode': m, 'hub.verify_token': t, 'hub.challenge': c } = req.query;
  return m === 'subscribe' && t === process.env.VERIFY_TOKEN ? res.status(200).send(c) : res.sendStatus(403);
});

// ---------- 2. Terima event ----------
app.post('/wa/webhook', express.raw({ type: 'application/json' }), async (req, res) => {
  const sig = req.get('x-hub-signature-256') || '';
  const exp = 'sha256=' + crypto.createHmac('sha256', process.env.APP_SECRET).update(req.body).digest('hex');
  if (sig.length !== exp.length || !crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(exp))) return res.sendStatus(401);
  res.sendStatus(200);                          // balas cepat
  await queue.add('wa-event', JSON.parse(req.body.toString('utf8')));
});

// ---------- 3. Worker ----------
export async function handleEvent(body) {
  for (const entry of body.entry ?? []) for (const ch of entry.changes ?? []) {
    if (ch.field !== 'messages') continue;
    const v = ch.value, phoneId = v.metadata.phone_number_id;     // → tenant/workspace
    const byUser = Object.fromEntries((v.contacts ?? []).map(c => [c.user_id ?? c.wa_id, c]));

    for (const m of v.messages ?? []) {
      if (!(await dedupe(`in:${m.id}`))) continue;
      const uid = m.from_user_id ?? m.from;
      const contact = await upsertContact(phoneId, uid, byUser[uid]);   // BSUID + wa_id + nama
      if (m.type === 'system') { await handleSystem(phoneId, m.system); continue; }
      if (m.type === 'reaction') { await saveReaction(m.reaction); continue; }
      await touchWindow(contact.id, Number(m.timestamp));              // reset 24 jam
      if (m.referral) await openFreeEntryPoint(contact.id, m.referral);
      const media = m[m.type]?.id ? await downloadMedia(m[m.type].id) : null;
      await saveInbound(contact.id, m, media);                         // simpan + push UI
    }
    for (const s of v.statuses ?? []) {
      if (!(await dedupe(`st:${s.id}:${s.status}`))) continue;
      await updateStatusMonotonic(s.id, s.status, s.timestamp, s.errors?.[0], s.pricing);
    }
    for (const e of v.errors ?? []) log.error('wa system error', e);
  }
}

// ---------- 4. Kirim ----------
export async function send(phoneId, to, payload, { replyTo, ref } = {}) {
  const isBsuid = /^[A-Z]{2}\.(ENT\.)?[A-Za-z0-9]+$/.test(to);
  const body = { messaging_product: 'whatsapp', recipient_type: 'individual',
    ...(isBsuid ? { recipient: to } : { to }),
    ...(replyTo && { context: { message_id: replyTo } }),
    ...(ref && { biz_opaque_callback_data: ref }), ...payload };
  const r = await fetch(`${G}/${phoneId}/messages`, { method: 'POST', headers: H, body: JSON.stringify(body) });
  const j = await r.json();
  if (!r.ok) throw Object.assign(new Error(j.error?.message), { code: j.error?.code, transient: j.error?.is_transient });
  return j.messages[0].id;                     // wamid → simpan
}
// contoh: await send(phoneId, contact.bsuid, { type: 'text', text: { body: 'Halo!' } }, { ref: msgId });

export const markRead = (phoneId, wamid, typing = false) =>
  fetch(`${G}/${phoneId}/messages`, { method: 'POST', headers: H, body: JSON.stringify({
    messaging_product: 'whatsapp', status: 'read', message_id: wamid,
    ...(typing && { typing_indicator: { type: 'text' } }) }) });

// ---------- 5. Media ----------
export async function uploadMedia(phoneId, buf, mime, filename) {
  const fd = new FormData();
  fd.append('messaging_product', 'whatsapp');
  fd.append('file', new Blob([buf], { type: mime }), filename);
  const r = await fetch(`${G}/${phoneId}/media`, { method: 'POST', headers: { Authorization: H.Authorization }, body: fd });
  return (await r.json()).id;
}
export async function downloadMedia(mediaId) {
  const meta = await (await fetch(`${G}/${mediaId}`, { headers: H })).json();      // url berlaku 5 menit
  const bin = Buffer.from(await (await fetch(meta.url, { headers: { Authorization: H.Authorization } })).arrayBuffer());
  if (crypto.createHash('sha256').update(bin).digest('hex') !== meta.sha256) throw new Error('sha mismatch');
  return { mime: meta.mime_type, path: await storage.put(mediaId, bin, meta.mime_type) };
}
```

---

## 11. Checklist "chat jalan"

- [ ] System user token permanen dengan permission `whatsapp_business_messaging` + `whatsapp_business_management`, sudah di-assign ke WhatsApp account **dan** Messaging account
- [ ] Nomor sudah `register` (PIN) dan berstatus CONNECTED. Display name sudah disetujui
- [ ] `POST /{WABA_ID}/subscribed_apps` sukses. Field `messages` sudah di-subscribe. App dalam mode Live
- [ ] Endpoint webhook: HTTPS valid, GET challenge, validasi HMAC, **balas 200 < 1 detik**, proses lewat queue
- [ ] Idempotensi (dedupe `wamid` dan `wamid+status`), status hanya boleh naik
- [ ] Routing multi-tenant berdasarkan `metadata.phone_number_id`
- [ ] Kontak memakai **BSUID** sebagai kunci. `wa_id` opsional. Webhook `system` (ganti nomor / ganti BSUID) ditangani
- [ ] Semua tipe pesan masuk di 4.2 bisa dirender, termasuk fallback "tidak didukung"
- [ ] Media masuk diunduh < 5 menit dan disimpan sendiri. Media keluar diupload lalu dikirim dengan `id`
- [ ] Logika window 24 jam dan FEP 72 jam. Composer terkunci dan pemilih template muncul saat window tertutup. Error 131047 ditangani
- [ ] Read receipt saat chat dibuka. Typing indicator saat membalas
- [ ] Reply (context) dan reaction (dua arah)
- [ ] Error handling dan retry sesuai tabel bagian 9. Error 0/190/10/200/368 memicu alert admin
- [ ] Rate limit sisi aplikasi: maks 80 msg/s per nomor, jeda antar pesan ke user yang sama
- [ ] Opt-out marketing (`user_preferences`, 131050) dihormati

---

### Sumber (guide & reference resmi Meta)
`messages/send-messages` · `messages/text-messages` · `messages/image-messages` · `messages/video-messages` · `messages/audio-messages` · `messages/document-messages` · `messages/sticker-messages` · `messages/location-messages` · `messages/reaction-messages` · `messages/contextual-replies` · `messages/mark-message-as-read` · `typing-indicators` · `messages/interactive-reply-buttons-messages` · `messages/interactive-list-messages` · `messages/interactive-cta-url-messages` · `messages/location-request-messages` · `business-scoped-user-ids` · `webhooks/overview` · `webhooks/reference/messages/*` · `business-phone-numbers/media` · `support/error-codes` · `reference/whatsapp-business-phone-number/message-api` (prefix: `https://developers.facebook.com/documentation/business-messaging/whatsapp/`)
