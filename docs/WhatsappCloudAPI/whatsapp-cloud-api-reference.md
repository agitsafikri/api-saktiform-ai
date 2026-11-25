# WhatsApp Business Platform: API Reference Lengkap untuk Integrasi

> Disusun 24 Nov 2026 dari **seluruh 66 halaman API Reference resmi Meta** (indeks `reference/llms.txt`), ditambah webhook reference per tipe pesan dan Flows API.
> Sumber utama: https://developers.facebook.com/documentation/business-messaging/whatsapp/ (halaman reference tersedia dalam versi `.md`, tinggal tambahkan `.md` di akhir URL).
> Isinya struktur ulang dan parafrase (endpoint, parameter, enum, response, error), **bukan salinan verbatim**. Batas panjang yang ditandai "guide" atau "pengetahuan umum" tidak tercantum di halaman reference, jadi verifikasi dulu sebelum dipakai sebagai validasi keras.
> Pendamping: `whatsapp-business-platform-api.md` (konsep, pricing, limit, checklist).

---

## Daftar Isi
- **A.** Konvensi umum (base URL, auth, error, pagination)
- **B.** Messages: kirim pesan (semua tipe), read receipt, typing indicator
- **C.** Media: upload, URL, hapus, download
- **D.** Phone Number: detail, update, profile, request/verify code, register, PIN, deregister, settings, block, QR, OBA, encryption, compliance, commerce
- **E.** Calling API
- **F.** Groups API
- **G.** Marketing Messages API (MM API)
- **H.** WhatsApp Business Account: detail, phone_numbers, account number, subscribed_apps, conversational automation, assigned users, extended credits, schedules, activities, solutions
- **I.** Business Portfolio: owned/client WABA, buat WABA, onboarding MM Lite
- **J.** Pre-Verified Phone Numbers
- **K.** Application & Multi-Partner Solutions
- **L.** Node lain: user, message history, migration intent, bot, profile node
- **M.** Message Template API
- **N.** Webhooks payload reference
- **O.** WhatsApp Flows API
- **Z.** Cheat sheet semua endpoint

---

## A. Konvensi Umum

**Base URL**: `https://graph.facebook.com/{Version}`. Versi terbaru saat dokumen ini dibuat adalah **v26.0** (29 Jul 2026). Contoh di dokumentasi umumnya memakai v23–v25.

**Header**
```
Authorization: Bearer <ACCESS_TOKEN>      # system user token / business token (BISU)
Content-Type: application/json            # multipart/form-data untuk upload
User-Agent: <opsional>
```

**ID yang sering dipakai**
| Placeholder | Arti |
|---|---|
| `{Phone-Number-ID}` | ID nomor bisnis (bukan nomor telepon) |
| `{WABA-ID}` | WhatsApp Business Account / Messaging account ID |
| `{Business-ID}` | Business portfolio ID |
| `{Application-ID}` | Meta App ID |
| `{Media-ID}`, `{TEMPLATE_ID}`, `{FLOW-ID}`, `{group_id}`, `{Solution-ID}` | ID resource |

**Format error (GraphAPIError)**
```json
{ "error": { "message": "...", "type": "OAuthException", "code": 131047,
  "error_subcode": 2494010, "error_data": { "messaging_product": "whatsapp", "details": "..." },
  "error_user_title": "...", "error_user_msg": "...", "is_transient": false, "fbtrace_id": "A1b2..." } }
```
- **Bangun logika error handling berdasarkan `code`**, bukan HTTP status atau subcode.
- Kalau `is_transient: true`, request aman di-retry dengan exponential backoff.
- Simpan `fbtrace_id` untuk tiket support.

**Pola error Graph yang muncul di hampir semua endpoint**
| HTTP | code / subcode | Arti |
|---|---|---|
| 400 | 100 | parameter tidak valid |
| 401 | 190 / 463 | token tidak valid atau expired |
| 403 | 200 / 1349174 | tidak punya permission ke aset |
| 404 | 803 | ID tidak ditemukan atau tidak bisa diakses |
| 422 | 100 | request valid tapi tidak bisa diproses dalam state saat ini |
| 429 | 4 / 2446079 (atau 80007, 130429) | rate limit (transient) |
| 500 | 2 | error server (transient) |

**Pagination (cursor)**: query `limit`, `after`, `before`. Response berisi `paging { cursors { before, after }, next, previous }`. Beberapa endpoint memakai `first` / `last`.

**Field selection**: `?fields=a,b,c`. Field nested ditulis `?fields=preview.invalidate(false)`, `qr_image_url.format(SVG)`.

**Permission**: `whatsapp_business_messaging` untuk pesan, media, dan calling; `whatsapp_business_management` untuk akun, template, nomor, dan webhook lain; `business_management` untuk node business.
## B. WhatsApp Business Phone Number: Messaging

### B1. Message API: kirim pesan
`POST /{Version}/{Phone-Number-ID}/messages`
Sumber: `reference/whatsapp-business-phone-number/message-api.md`

**Header**
| Header | Wajib | Nilai |
|---|---|---|
| `Authorization` | Ya | `Bearer <token>` (OAuth / system user token) |
| `Content-Type` | Ya | `application/json` \| `application/x-www-form-urlencoded` \| `multipart/form-data` |
| `User-Agent` | Tidak | identitas client |

**BaseMessageProperties** (field ini berlaku untuk semua tipe pesan)
| Field | Tipe | Wajib | Catatan |
|---|---|---|---|
| `messaging_product` | string | Ya | `"whatsapp"` |
| `recipient_type` | enum `individual` \| `group` | Ya | |
| `to` | string | Ya | nomor telepon (individual) atau group ID (group) |
| `type` | string | Ya | `text`, `image`, `audio`, `video`, `document`, `sticker`, `location`, `contacts`, `template`, `interactive`, `reaction` (+ `address`, dll.) |
| `context` | MessageContext | Tidak | `{ "message_id": "<wamid yang dibalas>" }` |
| `<type>` | object | Ya | objek sesuai `type` (lihat di bawah) |

**TextMessage**: `body` (string, wajib), `preview_url` (boolean)

**MediaObject** (`image`, `audio`, `video`, `document`, `sticker`): wajib salah satu dari `id` (media ID hasil upload) **atau** `link` (URL https). Field opsional: `caption` (image/video/document), `filename` (document).

**InteractiveObject**
| Field | Tipe | Wajib | Catatan |
|---|---|---|---|
| `type` | enum `button`, `list`, `product`, `product_list`, `catalog_message`, `flow`, `call_permission_request` (juga `cta_url`, `location_request_message`, carousel di guide) | Ya | |
| `header` | HeaderObject | Tidak | wajib untuk `product_list`, tidak boleh untuk `product` |
| `body` | `{ text }` | Kondisional | wajib kecuali `product`; boleh emoji + markdown |
| `footer` | `{ text }` | Tidak | boleh emoji, markdown, link |
| `action` | ActionObject | Ya | struktur bergantung pada `type` |

**HeaderObject**: `type` (enum `text` \| `image` \| `video` \| `document`, wajib); `text` (wajib kalau type `text`, emoji boleh, markdown tidak); `sub_text`; `image` / `video` / `document` (MediaObject, wajib sesuai type).

**SectionObject** (list & multi-product): `title` (wajib kalau section > 1, maks 24 karakter), `rows[]`, `product_items[]`.
- **Rows**: `id` (wajib, unik), `title` (wajib), `description`
- **Product_items**: `product_retailer_id` (wajib, ID produk di katalog)

**ActionObject per tipe** (dirangkum dari guide Send Messages):
| interactive.type | Isi `action` |
|---|---|
| `button` | `buttons[]` maks **3**: `{ "type":"reply", "reply": { "id", "title" } }`; ID tidak boleh diawali/diakhiri spasi |
| `list` | `button` (label tombol, maks 20 karakter, wajib, unik) + `sections[]` (maks 10 section / 10 row total) |
| `cta_url` | `{ "name":"cta_url", "parameters": { "display_text", "url" } }` |
| `flow` | `{ "name":"flow", "parameters": { "flow_message_version":"3", "flow_token", "flow_id" / "flow_name", "flow_cta", "flow_action":"navigate"\|"data_exchange", "flow_action_payload": { "screen", "data" } } }` |
| `location_request_message` | `{ "name":"send_location" }` |
| `product` | `catalog_id`, `product_retailer_id` |
| `product_list` | `catalog_id`, `sections[].product_items[]` |
| `catalog_message` | `{ "name":"catalog_message", "parameters": { "thumbnail_product_retailer_id" } }` |
| `call_permission_request` | `{ "name":"call_permission_request" }` |

**LocationMessage**: `latitude`, `longitude` (number, wajib), `name`, `address`

**ReactionMessage**: `message_id` (wamid, wajib), `emoji` (string; string kosong menghapus reaksi)

**ContactObject** (array `contacts[]`, tidak menerima properti tambahan)
| Field | Tipe |
|---|---|
| `name` | NameObject: `formatted_name`, `first_name`, `last_name`, `middle_name`, `prefix`, `suffix` |
| `phones[]` | `phone` (wajib), `type` (`HOME`\|`WORK`), `wa_id` |
| `emails[]` | `email` (wajib, format email), `type` |
| `addresses[]` | `street`, `city`, `state`, `zip`, `country`, `country_code` (ISO 2 huruf), `type` |
| `org` | `company`, `department`, `title` |
| `urls[]` | `url` (wajib, URI), `type` |
| `birthday` | string `YYYY-MM-DD` |

**TemplateMessage**
```json
"template": {
  "name": "<nama>", "language": { "code": "id" },
  "components": [
    { "type": "header", "parameters": [ { "type": "image", "image": { "id": "<MEDIA_ID>" } } ] },
    { "type": "body", "parameters": [
        { "type": "text", "text": "Budi" },                       // POSITIONAL
        { "type": "text", "parameter_name": "order_id", "text": "INV-1" }, // NAMED
        { "type": "currency", "currency": { "fallback_value": "Rp10.000", "code": "IDR", "amount_1000": 10000000 } },
        { "type": "date_time", "date_time": { "fallback_value": "24 Nov 2026" } } ] },
    { "type": "button", "sub_type": "quick_reply", "index": "0", "parameters": [ { "type": "payload", "payload": "YES" } ] },
    { "type": "button", "sub_type": "url", "index": "1", "parameters": [ { "type": "text", "text": "INV1" } ] },
    { "type": "button", "sub_type": "copy_code", "index": "2", "parameters": [ { "type": "coupon_code", "coupon_code": "DISKON10" } ] }
  ] }
```
Tipe parameter: `text`, `currency`, `date_time`, `image`, `video`, `document`, `location`, `payload`, `coupon_code`, `action` (catalog/MPM/flow), `limited_time_offer`. Untuk template authentication, kode OTP dikirim di body dan di button `url` / `copy_code`.

**Response 200** (`MessageResponsePayload`)
| Field | Tipe |
|---|---|
| `messaging_product` | string |
| `contacts[]` | `input` (string), `wa_id` (string) |
| `messages[]` | `id` (wamid), `message_status` (`accepted` \| `held_for_quality_assessment` \| `paused`) |

### B2. Tandai dibaca & typing indicator
`POST /{Version}/{Phone-Number-ID}/messages`
| Field (MarkMessageRequestPayload) | Wajib | Nilai |
|---|---|---|
| `messaging_product` | Ya | `"whatsapp"` |
| `status` | Ya | `"read"` |
| `message_id` | Ya | wamid pesan masuk |
| `typing_indicator` | Tidak | `{ "type": "text" }` (indikator tampil sampai Anda membalas, maks ~25 detik) |

Response: `{ "success": true }`

---

## C. Media

### C1. Upload media
`POST /{Version}/{Phone-Number-ID}/media` (multipart/form-data)
| Field | Tipe | Catatan |
|---|---|---|
| `messaging_product` | string | `"whatsapp"` |
| `file` | binary | file + MIME, mis. `file=@foto.jpg;type=image/jpeg` |
| `type` | string | MIME type (di guide) |

Response: `{ "id": "<MEDIA_ID>" }`. Media upload berlaku sekitar 30 hari.

### C2. Ambil URL media
`GET /{Version}/{Media-ID}?phone_number_id=<opsional>`

Response: `messaging_product`, `url` (**berlaku 5 menit**), `mime_type`, `sha256`, `file_size`, `id`

### C3. Hapus media
`DELETE /{Version}/{Media-ID}?phone_number_id=<opsional>` → `{ "success": true }`. Kalau `phone_number_id` diisi, media hanya dihapus jika milik nomor tersebut.

### C4. Download media
`GET <MEDIA_URL>` dengan header `Authorization: Bearer <token>` → binary (`application/octet-stream` + header MIME).
Error: 400 parameter salah, 401 token, **404 media tidak ada / URL kedaluwarsa** (ambil ulang URL lewat C2), 500.
Permission: `whatsapp_business_messaging`.

**Batas media** (Supported Media Types, guide):
| Tipe | Format | Maks |
|---|---|---|
| Audio | AAC, AMR, MP3 (mpeg), MP4 audio, OGG (opus) | 16 MB |
| Document | txt, pdf, doc(x), xls(x), ppt(x) | 100 MB |
| Image | JPEG, PNG (8-bit RGB/RGBA) | 5 MB |
| Sticker | WebP statis / animasi | 100 KB / 500 KB |
| Video | MP4, 3GPP (H.264 + AAC) | 16 MB |

---

## D. Phone Number

### D1. Detail nomor
`GET /{Version}/{Phone-Number-ID}?fields=...`

Field default: `id`, `display_phone_number`, `verified_name`, `quality_rating`.
| Field | Nilai |
|---|---|
| `quality_rating` | `GREEN`, `YELLOW`, `RED`, `NA` |
| `code_verification_status` | `VERIFIED`, `UNVERIFIED` |
| `name_status` | `APPROVED`, `AVAILABLE_WITHOUT_REVIEW`, `DECLINED`, `EXPIRED`, `PENDING_REVIEW`, `NONE` |
| lainnya (guide) | `platform_type`, `throughput{level}`, `webhook_configuration`, `is_official_business_account`, `messaging_limit_tier`, `status`, `account_mode`, `last_onboarded_time`, `health_status`, `whatsapp_business_manager_messaging_limit` |

Error: 400, 401, 403, 404, 422, 429, 500 (format `GraphAPIError`).

### D2. Update konfigurasi nomor
`POST /{Version}/{Phone-Number-ID}` (semua field opsional)
| Field | Tipe | Catatan |
|---|---|---|
| `connection_status` | enum | `CONNECTED`, `DISCONNECTED`, `PENDING`, `FLAGGED`, `RESTRICTED`, `RATE_LIMITED`, `MESSAGING_LIMIT_TIER_0`…`_4` |
| `webhook_configuration` | object | `override_callback_uri` (wajib, URI; string kosong untuk menghapus), `verify_token` |
| `webhook_url` | URI | |
| `pin` | string | PIN two-step verification (6 digit) |
| `whatsapp_business_api_data` | object | `pin`, `show_security_notifications`, `notify_user_change_number` |
| `search_visibility` | enum | `VISIBLE`, `HIDDEN` |
| `new_display_name` | string | ajukan nama tampilan baru |
| `username` | string | username bisnis |

Response: `{ "success": true }`

### D3. Business Profile
`GET /{Version}/{Phone-Number-ID}/whatsapp_business_profile?fields=about,address,description,email,profile_picture_url,websites,vertical`
→ `{ "data": [ { "messaging_product":"whatsapp", "about", "address", ... } ] }`

`POST /{Version}/{Phone-Number-ID}/whatsapp_business_profile`
| Field | Tipe | Catatan |
|---|---|---|
| `messaging_product` | `"whatsapp"` | wajib |
| `about` | string | teks "About" (guide: maks 139 karakter) |
| `address` | string | (maks 256) |
| `description` | string | (maks 512) |
| `email` | email | (maks 128) |
| `websites` | array URI | maks 2 |
| `vertical` | enum | `OTHER, AUTO, BEAUTY, APPAREL, EDU, ENTERTAIN, EVENT_PLAN, FINANCE, GROCERY, GOVT, HOTEL, HEALTH, NONPROFIT, PROF_SERVICES, RETAIL, TRAVEL, RESTAURANT, ALCOHOL, ONLINE_GAMBLING, PHYSICAL_GAMBLING, OTC_DRUGS` |
| `profile_picture_handle` | string | handle dari **Resumable Upload API** (`POST /{app-id}/uploads` → `POST /upload:<id>`) |

Response: `{ "success": true }`. Error 422 berarti handle foto tidak valid atau kedaluwarsa.
Panjang maksimal di atas berasal dari guide/pengetahuan umum, karena halaman reference tidak mencantumkannya.
### D4. Request verification code
`POST /{Version}/{Phone-Number-ID}/request_code`
| Field | Tipe | Wajib | Nilai |
|---|---|---|---|
| `code_method` | enum | Ya | `SMS` \| `VOICE` |
| `language` | string | Ya | locale pesan, mis. `id`, `en_US` |

Response `{ "success": true }`. Kode hanya dikirim ke nomor tersebut dan cepat kedaluwarsa. Gagal berulang bisa memicu blokir sementara. Error 422 berarti nomor tidak eligible untuk verifikasi saat ini.

### D5. Verify code
`POST /{Version}/{Phone-Number-ID}/verify_code`
| Field | Tipe | Wajib |
|---|---|---|
| `code` | string | Ya (6 digit dari SMS/voice; berlaku ~10 menit, sekali pakai) |

Response `{ "success": true, "id": "<PHONE_NUMBER_ID>" }`. Error 422 berarti kode salah atau kedaluwarsa.

### D6. Register nomor (wajib sebelum mengirim pesan)
`POST /{Version}/{Phone-Number-ID}/register`
| Field | Tipe | Wajib | Catatan |
|---|---|---|---|
| `messaging_product` | string | Ya | `"whatsapp"` |
| `pin` | string | Ya | 6 digit PIN two-step verification (PIN baru, atau PIN yang sudah ada) |
| `backup` | object | Tidak | `password`, `data` (untuk migrasi dari On-Prem) |
| `data_localization_region` | enum | Tidak (deprecated v21+) | `AE, AU, BH, BR, CA, CH, DE, GB, ID, IN, JP, KR, SG, ZA` (sekarang lewat Settings API) |
| `meta_store_retention_minutes` | int | Tidak (deprecated v21+) | 60 |

Response `{ "success": true }`.
Error umum: 400/100 (format PIN salah), 401/190 subcode 463 (token), 403/200 subcode 1349174 (permission), 404/803 (nomor tidak ditemukan), 422/100 (sudah terdaftar atau state tidak valid), 429/4 subcode 2446079 (transient), 500/2 (transient).

### D7. Two-step verification (set/ganti PIN)
`POST /{Version}/{Phone-Number-ID}` dengan body `{ "pin": "123456" }` → `{ "success": true }`

### D8. Deregister
`POST /{Version}/{Phone-Number-ID}/deregister` (tanpa body) → `{ "success": true }`
Melepas nomor dari Cloud API supaya bisa didaftarkan ulang. Berlaku langsung. Nomor milik SMB (WhatsApp Business app) tidak bisa di-deregister.

### D9. Settings (calling, enkripsi payload, penyimpanan data, perubahan identitas user)
`GET /{Version}/{Phone-Number-ID}/settings?include_sip_credentials=<bool>`

Response:
```
calling: { status: enabled|disabled, call_icon_visibility: visible|hidden,
           ip_addresses: { default: [..] }, callback_permission_status: enabled|disabled,
           srtp_key_exchange_protocol: DTLS-SRTP|SDES-SRTP,
           call_hours: { status, timezone, day_of_week_start, ... },
           call_icons: { restrict_to_user_countries: [..] },
           sip: { status, servers: [ { app_id, hostname, port, password* } ] },
           video: { status }, audio: { status },
           restrictions: { restrictions: [ { type, expiration } ] } }
payload_encryption: { status, client_encryption_key_fingerprint, cloud_encryption_key }
storage_configuration: { status: default|in_country_storage_enabled, data_localization_region }
```
`POST /{Version}/{Phone-Number-ID}/settings`: **satu fitur per request**, pilih salah satu body:
| Body | Isi |
|---|---|
| Calling | `{ "calling": { "status", "call_icon_visibility", "video": {status}, "sip": {status, servers[]}, "srtp_key_exchange_protocol", "call_hours": {...} } }` |
| User identity change | `{ "user_identity_change": { "enabled": true } }` |
| Payload encryption | `{ "payload_encryption": { "status": "enabled", "client_encryption_key": "<base64 public key>" } }` |
| Storage (data localization) | `{ "storage_configuration": { "enabled": true, "region": "ID" } }` |

Response `{ "success": true }`.

### D10. Block users
| Method | Path | Body / Response |
|---|---|---|
| POST | `/{Version}/{Phone-Number-ID}/block_users` | `{ "messaging_product":"whatsapp", "block_users":[ { "user":"628..." } ] }` → `block_users.added_users[] { input, wa_id }` |
| GET | `/{Version}/{Phone-Number-ID}/block_users` | → `data[] { messaging_product, wa_id }` + `paging.cursors` |
| DELETE | `/{Version}/{Phone-Number-ID}/block_users` | body sama dengan POST → `block_users.removed_users[]` |

Aturan dari guide: hanya user yang pernah mengirim pesan dalam 24 jam terakhir yang bisa diblokir, dan daftar blokir dibatasi (≈64 ribu user).

### D11. QR code & deep link (`wa.me/message/...`)
| Method | Path | Keterangan |
|---|---|---|
| GET | `/{Version}/{Phone-Number-ID}/message_qrdls` | list. Query: `fields` (`code,prefilled_message,deep_link_url,qr_image_url.format(SVG\|PNG)`), `code`, `limit` (1–25), `after`, `before` |
| POST | `/{Version}/{Phone-Number-ID}/message_qrdls` | **create**: `{ "prefilled_message": "<≤140 karakter>", "generate_qr_image": "PNG"\|"SVG" }`; **update**: `{ "code": "<14 char>", "prefilled_message": "..." }` |
| GET | `/{Version}/{Phone-Number-ID}/message_qrdls/{QR-Code-ID}` | detail 1 QR (`fields` sama dengan list) |
| DELETE | `/{Version}/{Phone-Number-ID}/message_qrdls/{QR-Code-ID}` | hapus permanen (deep link langsung tidak berlaku) |

Response QR: `code` (14 karakter), `prefilled_message`, `deep_link_url`, `qr_image_url` (kalau diminta), `creation_time` (hanya untuk first-party app).

### D12. Official Business Account (centang biru)
| Method | Path | Keterangan |
|---|---|---|
| GET | `/{Version}/{Phone-Number-ID}/official_business_account?fields=oba_status,status_message` | `oba_status`: `PENDING`, `APPROVED`, `REJECTED`, `UNDER_REVIEW`, `EXPIRED`, `CANCELLED` |
| POST | `/{Version}/{Phone-Number-ID}/official_business_account` | ajukan, ajukan ulang, atau update aplikasi OBA |

Body POST: `business_website_url` (wajib), `primary_country_of_operation` (wajib), `primary_language`, `parent_business_or_brand`, `supporting_links[]` (5–10 URL bukti notability), `additional_supporting_information`.
Response: `success`, `message`, `updated_status`, `tracking_id`. Error 409 berarti transisi status tidak valid. Aplikasi baru hanya bisa diajukan kalau tidak ada aplikasi aktif. Pengajuan ulang hanya setelah ditolak.

### D13. Business Encryption (WhatsApp Flows data channel)
| Method | Path | Keterangan |
|---|---|---|
| GET | `/{Version}/{Phone-Number-ID}/whatsapp_business_encryption` | → `data[] { business_public_key, business_public_key_signature_status: VALID\|MISMATCH }` |
| POST | `/{Version}/{Phone-Number-ID}/whatsapp_business_encryption` (multipart) | `business_public_key` = RSA public key PEM → `{ success }`. Satu key aktif per nomor (upload baru menggantikan key lama) |

### D14. Business Compliance Info (wajib untuk bisnis di India)
| Method | Path |
|---|---|
| GET | `/{Version}/{Phone-Number-ID}/business_compliance_info?fields=...` |
| POST | `/{Version}/{Phone-Number-ID}/business_compliance_info` |

Body POST: `messaging_product` (`whatsapp`), `entity_name` (2–128 karakter), `entity_type` (`LIMITED_LIABILITY_PARTNERSHIP`, `SOLE_PROPRIETORSHIP`, `PARTNERSHIP`, `PUBLIC_COMPANY`, `PRIVATE_COMPANY`, `OTHER`), `entity_type_custom` (wajib kalau `OTHER`), `is_registered` (hanya untuk OTHER/PARTNERSHIP), `grievance_officer_details { name, email, mobile_number, landline_number }`, `customer_care_details { email, mobile_number, landline_number }`.

### D15. Commerce Settings (katalog & keranjang)
| Method | Path | Keterangan |
|---|---|---|
| GET | `/{Version}/{Phone-Number-ID}/whatsapp_commerce_settings` | → `data[] { id, is_cart_enabled, is_catalog_visible }` |
| POST | `/{Version}/{Phone-Number-ID}/whatsapp_commerce_settings?is_cart_enabled=true&is_catalog_visible=true` | setting dikirim sebagai **query param** → `{ success }` |
## E. Calling API

### E1. Cek izin panggilan
`GET /{Version}/{Phone-Number-ID}/call_permissions?user_wa_id=<WA_ID>`

Response:
```
messaging_product
permission: { status: granted|pending|denied|expired, expiration_time }
actions[]: { action_name: start_call|send_call_permission_request, can_perform_action,
             limits[]: { time_period, current_usage, max_allowed, limit_expiration_time } }
```

### E2. Kelola panggilan
`POST /{Version}/{Phone-Number-ID}/calls`
| Field | Tipe | Wajib | Catatan |
|---|---|---|---|
| `messaging_product` | string | Ya | `whatsapp` |
| `to` | string | Ya (untuk connect) | nomor user |
| `call_id` | string | Ya (untuk pre_accept/accept/reject/terminate) | ID panggilan dari webhook `calls` |
| `action` | enum | Ya | `connect` (business memanggil user), `pre_accept`, `accept`, `reject`, `terminate`, `media_update` |
| `session` | object | Kondisional | `{ "sdp_type": "offer" (connect) \| "answer" (accept/pre_accept), "sdp": "<SDP RFC 8866>" }` |
| `biz_opaque_callback_data` | string | Tidak | maks 512 karakter, dikembalikan di webhook terminate |
| `messaging_account_id` | string | Kondisional | wajib kalau token punya akses ke >1 Messaging account di nomor yang sama (changelog Nov 2026) |

Response: `{ "messaging_product":"whatsapp", "calls":[ { "id":"<CALL_ID>" } ] }` atau `{ "success": true }`.
Error **138006**: user belum memberi izin panggilan. Minta izin lewat pesan interactive `call_permission_request` atau template call permission.
Calling diaktifkan per nomor lewat Settings API (`calling.status`). Event panggilan dikirim lewat webhook field `calls`.

---

## F. Groups API

| Method | Path | Body / Query | Response |
|---|---|---|---|
| GET | `/{Version}/{Phone-Number-ID}/groups` | `limit` (1–1024), `after`, `before` | `data.groups[] { id, subject, created_at }`, `paging` |
| POST | `/{Version}/{Phone-Number-ID}/groups` | `messaging_product`, `subject` (wajib, ≤128), `description` (≤2048), `join_approval_mode` (`approval_required` \| `auto_approve`) | `{ messaging_product, request_id }` (hasil asinkron lewat webhook group) |
| GET | `/{Version}/{group_id}?fields=...` | | `id, messaging_product, join_approval_mode, subject, description, suspended, creation_timestamp, participants[] { wa_id }, total_participant_count` (tidak termasuk bisnis) |
| POST | `/{Version}/{group_id}` | JSON: `messaging_product`, `subject`, `description`; atau multipart: `file` (foto JPEG ≤5 MB, persegi, ≥192×192) | 200 |
| DELETE | `/{Version}/{group_id}` | | `{ success }` (grup dihapus dan semua anggota, termasuk bisnis, dikeluarkan) |
| POST | `/{Version}/{group_id}/participants` | `messaging_product`, `participants[] { user }` (≤8) | tambah anggota |
| DELETE | `/{Version}/{group_id}/participants` | sama (≤8) | keluarkan anggota |
| POST | `/{Version}/{group_id}/invite_link` | `messaging_product` | `{ messaging_product, invite_link }` (buat atau reset link) |
| DELETE | `/{Version}/{group_id}/invite_link` | `messaging_product` | `{ success }` |
| GET | `/{Version}/{group_id}/join_requests` | | `data[] { join_request_id, wa_id, creation_timestamp }`, `paging` |
| POST | `/{Version}/{group_id}/join_requests` | `messaging_product`, `join_requests[]` (ID) | approve → `approved_join_requests[]`, `failed_join_requests[] { join_request_id, errors[] }`, `errors[]` |
| DELETE | `/{Version}/{group_id}/join_requests` | sama | reject → `rejected_join_requests[]`, `failed_join_requests[]` |

Kirim pesan ke grup: `POST /{Phone-Number-ID}/messages` dengan `"recipient_type":"group"` dan `"to":"<group_id>"`.
Error object di Groups: `code`, `message`, `title`, `error_data.details`.

---

## G. Marketing Messages API for WhatsApp (MM API)

`POST /{Version}/{Phone-Number-ID}/marketing_messages`
| Field | Tipe | Wajib | Nilai |
|---|---|---|---|
| `messaging_product` | enum | Ya | `whatsapp` |
| `recipient_type` | enum | Ya | `individual` |
| `to` | string | Ya | nomor / WA ID |
| `type` | enum | Ya | `template` |
| `template` | object | Ya | `name`, `language { code }`, `components[]` (format sama dengan template di Message API) |
| `product_policy` | enum | Tidak | `CLOUD_API_FALLBACK` (kalau tidak eligible MM API, dikirim lewat Cloud API) \| `STRICT` |
| `message_activity_sharing` | boolean | Tidak | aktif/nonaktifkan pembagian aktivitas pesan untuk optimasi |

Response sama dengan Message API: `contacts[]`, `messages[] { id, message_status }`.
Hanya untuk template **MARKETING**. Bisnis harus sudah onboard ke MM API (lihat I. Partner Onboarding to MM Lite). Template yang dikirim lewat endpoint ini bisa diberi `send_type: campaign|direct` saat dibuat.
## H. WhatsApp Business Account (WABA / Messaging account)

### H1. Detail & update WABA
`GET /{Version}/{WABA-ID}?fields=...` (default `id,name`)
| Field | Nilai |
|---|---|
| `id`, `name`, `timezone_id`, `message_template_namespace`, `country`, `primary_business_location` | string |
| `account_review_status` | `APPROVED`, `DEFERRED`, `PENDING`, `REJECTED` |
| `business_verification_status` | `EXPIRED, FAILED, INELIGIBLE, NOT_VERIFIED, PENDING, PENDING_NEED_MORE_INFO, PENDING_SUBMISSION, REJECTED, REVOKED, VERIFIED` |
| `ownership_type` | `CLIENT_OWNED`, `ON_BEHALF_OF`, `SELF` |

`POST /{Version}/{WABA-ID}` body: `name`, `timezone_id` (keduanya opsional) → `{ success }`

### H2. Daftar & tambah nomor di WABA
`GET /{Version}/{WABA-ID}/phone_numbers`
| Query | Keterangan |
|---|---|
| `fields` | `id, display_phone_number, verified_name, status, quality_rating, country_code, country_dial_code, code_verification_status, unified_cert_status, account_mode, host_platform, messaging_limit_tier, is_official_business_account` |
| `filtering` | JSON array `[{ "field":"account_mode", "operator":"EQUAL", "value":"LIVE" }]`; field yang bisa difilter: `account_mode`, `messaging_limit_tier`, `is_official_business_account` |
| `sort` | `creation_time.asc\|desc`, `last_onboarded_time.asc\|desc` |
| `limit` (1–100), `after`, `before` | pagination |

Enum penting:
- `status`: `BANNED, CONNECTED, DELETED, DISCONNECTED, FLAGGED, MIGRATED, PENDING, RATE_LIMITED, RESTRICTED`
- `quality_rating`: `GREEN, YELLOW, RED, UNKNOWN`
- `code_verification_status`: `EXPIRED, NOT_VERIFIED, VERIFIED`
- `unified_cert_status`: `APPROVED, NAME_PENDING_REVIEW, NAME_NOT_APPROVED, ACCOUNT_REVIEW_NOT_STARTED, LIMITED_ACCESS`
- `account_mode`: `LIVE, SANDBOX`
- `host_platform`: `CLOUD_API, ON_PREMISE, NOT_APPLICABLE`
- `messaging_limit_tier`: `TIER_50, TIER_250, TIER_1K, TIER_10K, TIER_100K, TIER_UNLIMITED`

`POST /{Version}/{WABA-ID}/phone_numbers`: tambah nomor
| Field | Wajib | Catatan |
|---|---|---|
| `phone_number` | Ya | E.164 tanpa `+` (boleh tanpa kode negara kalau `cc` diisi) |
| `cc` | Tidak | kode negara, mis. `62` |
| `verified_name` | Ya | nama bisnis yang akan direview |
| `migrate_phone_number` | Tidak | `true` untuk migrasi dari On-Prem |
| `preverified_id` | Tidak | ID nomor pre-verified (BSP) |

Response `{ "id": "<PHONE_NUMBER_ID>" }`. Error 409 berarti nomor sudah terdaftar, 403 berarti slot nomor habis.
**Alur nomor baru**: POST phone_numbers → `request_code` → `verify_code` → `register` (PIN) → subscribe app → kirim pesan.

### H3. WhatsApp Account Number (node nomor di model akun baru)
`GET /{Version}/{WhatsApp-Account-Number-ID}?fields=...` (default `id,display_phone_number,status`)

Field yang tersedia: `id, display_phone_number, verified_name, status, quality_rating, country_code, country_dial_code, code_verification_status, name_status, messaging_limit_tier (+TIER_NOT_SET), account_mode, certificate, is_official_business_account, host_platform, is_on_biz_app, is_preverified_number, last_onboarded_time, new_certificate, new_display_name, new_name_status, platform_type`
- `status` tambahan: `UNKNOWN`, `UNVERIFIED`
- `name_status`: `APPROVED, AVAILABLE_WITHOUT_REVIEW, DECLINED, EXPIRED, NON_EXISTS, NONE, PENDING_REVIEW`
- `is_on_biz_app = true` berarti nomor dipakai bersama WhatsApp Business app (coexistence)

### H4. Subscribed Apps (wajib supaya webhook WABA terkirim ke app Anda)
| Method | Path | Body / Query | Response |
|---|---|---|---|
| POST | `/{Version}/{WABA-ID}/subscribed_apps` | opsional `{ "override_callback_uri": "https://...", "verify_token": "..." }` (override webhook per WABA) | `{ success }` |
| GET | `/{Version}/{WABA-ID}/subscribed_apps?fields=id,name,link` | | `data[] { whatsapp_business_api_data { id, name, link }, override_callback_uri }` |
| DELETE | `/{Version}/{WABA-ID}/subscribed_apps` | | `{ success }`; webhook langsung berhenti |

Error 422 saat POST berarti callback URL tidak bisa dihubungi atau tidak valid (harus lolos verifikasi GET `hub.challenge`).
**Urutan override webhook**: nomor (`webhook_configuration` di D2) > WABA (`override_callback_uri`) > default app.

### H5. Conversational Automation (welcome message, ice breakers, commands)
`POST /{Version}/{Phone-Number-ID}/conversational_automation`
| Field | Tipe | Catatan |
|---|---|---|
| `enable_welcome_message` | boolean | memicu webhook `messages` dengan `type: request_welcome` saat user membuka chat pertama kali |
| `prompts` | string[] | ice breakers (guide: maks 4, masing-masing ≤80 karakter) |
| `commands` | `[ { "command_name": "help", "command_description": "..." } ]` | tanpa `/`, unik per nomor (guide: maks 30; nama ≤32, deskripsi ≤256) |

Response `{ success }`. Baca konfigurasi: `GET /{Phone-Number-ID}?fields=conversational_automation`.

### H6. Assigned Users (akses user ke WABA)
| Method | Path | Body / Query |
|---|---|---|
| GET | `/{Version}/{WABA-ID}/assigned_users?business=<BUSINESS_ID>` | `fields` (`id,name,business,user_type`), `limit` 1–100 (default 25), `after`, `before` → `data[]`, `paging`, `summary.total_count` |
| POST | `/{Version}/{WABA-ID}/assigned_users` | form: `user=<user_id>`, `tasks=["MANAGE","MESSAGING",...]` |
| DELETE | `/{Version}/{WABA-ID}/assigned_users` | form: `user=<user_id>` |

`tasks`: `MANAGE, DEVELOP, MANAGE_TEMPLATES, MANAGE_PHONE, VIEW_COST, MANAGE_EXTENSIONS, VIEW_PHONE_ASSETS, MANAGE_PHONE_ASSETS, VIEW_TEMPLATES, MESSAGING`
`user_type`: `BUSINESS_USER, SYSTEM_USER, PERSONAL_USER`. Error 422 saat POST berarti user sudah di-assign.

### H7. Extended Credits (credit line, partner)
`GET /{Version}/{Business-ID}/extendedcredits?fields=...&order_by_is_owned_credential=true`

Field: `id, credit_type (ADS_BUSINESS|ADS_SEQUENTIAL|APP2USER|PAY_LATER|UNSPECIFIED|WHATSAPP_BUSINESS), balance, credit_available, max_balance, online_max_balance, allocated_amount, partition_from, owner_business{id,name}, owner_business_name, is_automated_experience, legal_entity_name, send_bill_to_biz_name, liable_biz_name, receiving_credit_allocation_config{id,partition_type,currency_amount}, is_access_revoked, is_shared_credit_line_enabled_for_mpa`

`CurrencyAmount`: `amount`, `amount_in_hundredths`, `currency` (ISO 4217), `offsetted_amount`.
Share credit line ke WABA customer (guide): `POST /{EXTENDED_CREDIT_ID}/whatsapp_credit_sharing_and_attach?waba_id=<ID>&waba_currency=USD` (atau IDR untuk Indonesia). Rate limit Credit Line API: 5.000 request/jam per app.

### H8. Schedules (jadwal kampanye template)
| Method | Path | Body / Query |
|---|---|---|
| GET | `/{Version}/{WABA-ID}/schedules` | `fields` (`id,name,description,delivery_time,status`), `limit` 1–100, cursor. `status`: `COMPLETED, FAILED, SCHEDULED, SENDING` |
| POST | `/{Version}/{WABA-ID}/schedules` | **semua wajib**: `hsm_id` (template ID), `audience_id`, `waba_cs_id`, `name`, `description`, `delivery_time` (Unix) → `{ id }` |

### H9. Activities (audit log WABA)
`GET /{Version}/{WABA-ID}/activities?fields=id,actor_business_id,actor_id,category,event_type,timestamp&limit=100`
- `category`: `ACCOUNT, APP, BILLING, BUSINESS_PROFILE, MESSAGE_TEMPLATE, PAYMENT_CONFIGURATION, PHONE_NUMBER, USER, WHATSAPP_BUSINESS_PAYMENT_TOS`
- `event_type` (pilihan): `ACCOUNT_VERIFIED, ACCOUNT_REVIEW_STATUS_UPDATED, APP_INSTALLED/UNINSTALLED, BILLING_ADDED/UPDATED/DELETED, BUSINESS_PROFILE_UPDATED, CALLING_STATUS_CHANGED, MESSAGE_TEMPLATE_ADDED/EDITED/DELETED, OTP_REQUESTED/VERIFIED, PARTNER_ADDED/REMOVED, PHONE_NUMBER_ADDED/DELETED, PHONE_NUMBER_REGISTERED_ON_CLOUD_API, PHONE_NUMBER_DEREGISTERED_FROM_CLOUD_API, PHONE_NUMBER_OBA_APPROVED, PHONE_MIGRATION_STARTED/COMPLETED, TWO_STEP_VERIFICATION_PIN_CHANGED/REMOVED/RESET_REQUESTED, USER_ADDED/DELETED, USER_PERMISSION_ADDED/UPDATED, WELCOME_MESSAGE_SEQUENCE_ADDED/UPDATED/DELETED, ...` (total ±50 nilai)

### H10. Solutions di WABA (Multi-Partner Solution)
`GET /{Version}/{WABA-ID}/solutions?fields=id,name,status,status_for_pending_request,owner_app,owner_permissions`
- `status`: `DRAFT, INITIATED, ACTIVE, REJECTED, DEACTIVATED`
- `status_for_pending_request`: `PENDING_ACTIVATION, PENDING_DEACTIVATION, NONE`

### H11. Halaman yang sedang 404 di sisi Meta (24 Nov 2026)
- WhatsApp Extension Preview API (`reference/whatsapp-business-account/whatsapp-extension-preview-api`)
- WhatsApp Flow Metric Data Point API (`reference/whatsapp-business-account/whatsapp-flow-metric-data-point-api`)

Keduanya tercantum di indeks, tapi halamannya tidak tersedia. Dari guide Flows: metrik flow diambil dengan `GET /{FLOW_ID}?fields=metric.name(ENDPOINT_REQUEST_COUNT).granularity(DAY).since(...).until(...)`.
## I. Business Portfolio (node `Business-ID`)

### I1. Detail business portfolio
`GET /{Version}/{Business-ID}?fields=id,name,timezone_id` → `id`, `name`, `timezone_id` (integer)

### I2. WABA milik / di-share ke business
| Method | Path | Kegunaan |
|---|---|---|
| GET | `/{Version}/{Business-ID}/owned_whatsapp_business_accounts` | WABA yang **dimiliki**. Query: `business_type` (`ENTERPRISE`,`SMB`), `find` (WABA ID), `first`/`after`, `last`/`before` (1–100). Response: `id, name, message_template_namespace, timezone_id` |
| GET | `/{Version}/{Business-ID}/client_whatsapp_business_accounts` | WABA **customer yang di-share** ke Anda (partner). Query: `fields`, `business_type` (`STANDARD`,`PREMIUM`,`ENTERPRISE`), `limit` 1–100 (default 25), `after`, `before`, `find` |
| GET | `/{Version}/{Business-ID}/whatsapp_business_accounts` | list WABA portfolio. Query: `fields`, `business_type`, `limit`, cursor, `find` |
| POST | `/{Version}/{Business-ID}/whatsapp_business_accounts` | **buat WABA baru** |

Field `client_whatsapp_business_accounts`: `id, name, account_review_status (APPROVED|PENDING|REJECTED|RESTRICTED), purchase_order_number, audiences, ownership_type (SELF_OWNED|CLIENT_OWNED|AGENCY_OWNED), subscribed_apps[] {id,name}, business_verification_status (VERIFIED|UNVERIFIED|PENDING|REJECTED), country, currency, timezone_id, on_behalf_of_business_info {id,name}, schedules[] {day_of_week, open_time, close_time}, is_enabled_for_insights, dcc_config {enabled, policy_url}, message_templates[] {id,name,status}, phone_numbers[] {id,display_phone_number,verified_name}`

Body POST buat WABA:
| Field | Wajib | Catatan |
|---|---|---|
| `name` | Ya | unik di dalam portfolio |
| `primary_funding_id` | Tidak | kartu kredit / extended credit |
| `currency` | Tidak | ISO 4217 (mis. `IDR`, `USD`) |
| `timezone_id` | Tidak | integer |
| `business_type` | Tidak | `ENTERPRISE` \| `SMB` |
| `purchase_order_number` | Tidak | |
| `on_behalf_of_business_id` | Tidak | |

Response: `{ "id": "<WABA_ID>", "payment_account_id": "..." }`. WABA baru berstatus pending sampai verifikasi selesai.

### I3. Onboarding partner ke MM Lite (MM API)
`POST /{Version}/{Business-ID}/onboard_partners_to_mm_lite?solution_id=<opsional>` (tanpa body)
→ `{ "request_id": "..." }`. Membuat business agreement request, dan untuk BSP juga menyetel OBO mobility intent. Endpoint ini idempoten: kalau sudah ada request pending, ID yang sama dikembalikan. Tech Provider hanya bisa membuat request.

## J. Pre-Verified Phone Numbers (pool nomor milik partner/BSP)

| Method | Path | Body / Query | Response |
|---|---|---|---|
| POST | `/{Version}/{Business-ID}/add_phone_numbers` | `{ "phone_number": "+62812..." }` (dinormalisasi otomatis; harus bisa menerima SMS dan belum terdaftar) | `{ id }` (pre-verified ID). 422 berarti batas jumlah nomor terlampaui |
| GET | `/{Version}/{Business-ID}/preverified_numbers` | `fields`, `phone_number`, `code_verification_status`, `limit`, cursor | `data[] { id, code_verification_status, code_verification_time, owner_business{id,name}, phone_number, verification_expiry_time }` |
| POST | `/{Version}/{Business-ID}/share_preverified_numbers` | `{ "preverified_id", "partner_business_id" }` | `{ success }` (kepemilikan tetap di business asal) |
| GET | `/{Version}/{Pre-Verified-Phone-Number-ID}?fields=...` | | detail (field sama dengan list) |
| DELETE | `/{Version}/{Pre-Verified-Phone-Number-ID}` | | `{ success }` (permanen; 422 kalau nomor sedang dipakai) |
| POST | `/{Version}/{Pre-Verified-Phone-Number-ID}/request_code` | `{ "code_method": "SMS"\|"VOICE"\|"IVR", "language": "id_ID" }` (fallback `en_US`) | `{ success }` |
| POST | `/{Version}/{Pre-Verified-Phone-Number-ID}/verify_code` | `{ "code": "123456" }` | `{ success }` (maks **125 request/jam**; kode sekali pakai) |
| GET | `/{Version}/{Pre-Verified-Phone-Number-ID}/partners` | `fields` (default `id,name`), `limit` 1–100, cursor | `data[] { id, name }` |

`code_verification_status`: `EXPIRED`, `NOT_VERIFIED`, `VERIFIED`. Nomor pre-verified dipakai di Embedded Signup lewat `preverified_id` (lihat H2).
## K. Application & Multi-Partner Solutions (Tech Provider / Solution Partner)

| Method | Path | Body / Query | Response / Aturan |
|---|---|---|---|
| GET | `/{Version}/{Application-ID}/connected_client_businesses` | `fields` (`id,name`), `limit` 1–100, cursor | business customer yang terhubung ke app (lewat Embedded Signup) |
| GET | `/{Version}/{Application-ID}/whatsapp_business_solutions` | `role` (`OWNER`\|`PARTNER`), `fields`, `limit`, cursor | daftar solution |
| POST | `/{Version}/{Application-ID}/whatsapp_business_solution` | `solution_name` (wajib), `partner_app_id` (wajib), `owner_permissions[]`, `partner_permissions[]` (nilai hanya `MESSAGING`, isi `[]` kalau tidak ada) | `{ solution_id }`, status awal `INITIATED`. **Tepat satu** pihak harus memegang `MESSAGING` (kalau keduanya atau tidak ada yang memegang, error 422) |
| GET | `/{Version}/{Solution-ID}?fields=...` | `id,name,status,status_for_pending_request,owner_app,owner_permissions` | detail |
| POST | `/{Version}/{Solution-ID}/accept` | | partner menerima undangan (solution harus `INITIATED`) → `{ success }` |
| POST | `/{Version}/{Solution-ID}/reject` | | owner menolak request pending (permanen) → `{ success }` |
| POST | `/{Version}/{Solution-ID}/send_deactivation_request` | | ajukan nonaktif (dari ACTIVE/INITIATED) → status pending `PENDING_DEACTIVATION` |
| POST | `/{Version}/{Solution-ID}/accept_deactivation_request` | `fields` | ACTIVE → DEACTIVATED (tagihan harus lunas, kampanye selesai) |
| POST | `/{Version}/{Solution-ID}/reject_deactivation_request` | `fields` | tetap ACTIVE, pending kembali ke `NONE` |
| GET | `/{Version}/{Solution-ID}/access_token?business_id=<CUSTOMER_BUSINESS_ID>` | | `{ access_token (BISU granular), expires_at }`. Refresh sebelum kedaluwarsa. 422 berarti customer belum share WABA |

Enum solution:
- `status`: `DRAFT, INITIATED, ACTIVE, REJECTED, DEACTIVATED`
- `status_for_pending_request`: `PENDING_ACTIVATION, PENDING_DEACTIVATION, NONE`
- `owner_permissions`: `DEVELOP, MANAGE, MANAGE_EXTENSIONS, MANAGE_PHONE, MANAGE_PHONE_ASSETS, MANAGE_TEMPLATES, MESSAGING, VIEW_COST, VIEW_PHONE_ASSETS, VIEW_TEMPLATES`
- Nama solution: 2–75 karakter

## L. Node lain

### L1. User → WABA yang di-assign
`GET /{Version}/{User-ID}/assigned_whatsapp_business_accounts?fields=id,name&limit=25` → `data[] { id, name }`, `paging`. Dengan `User-ID` = `me`, endpoint ini berguna untuk cek WABA yang bisa diakses token.

### L2. Message History events (status delivery historis)
`GET /{Version}/{Message-History-ID}/events`
| Query | Nilai |
|---|---|
| `delivery_status` | `ACCEPTED, DELIVERED, ERROR, READ, SENT` |
| `fields` | `cursor, node{id, delivery_status, error_description, occurrence_timestamp, status_timestamp, application, webhook_update_state, webhook_uri}` |
| `limit` 1–100 (default 25), `after`, `before` | |

`webhook_update_state`: `ERROR` \| `SUCCESS`. Field ini menunjukkan apakah webhook status berhasil dikirim ke URI Anda, sehingga berguna untuk debug webhook yang hilang.

### L3. Migration Intent (pindah WABA antar partner)
`GET /{Version}/{Migration-Intent-ID}?fields=id,status,destination_waba,solution,waba`
`status`: `INITIATED, ACCEPTED, IN_PROGRESS, READY_TO_COMPLETE, COMPLETED, FAILED, FAILED_TO_COMPLETE, REJECTED`

### L4. WhatsApp Business Bot
`GET /{Version}/{WABA-Bot-ID}?fields=id,prompts,commands,enable_welcome_message` → `prompts[]`, `commands[] { command_name, command_description }`, `enable_welcome_message`

### L5. WhatsApp Business Profile node
| Method | Path | Catatan |
|---|---|---|
| GET | `/{Version}/{WhatsApp-Business-Profile-ID}?fields=id,description,email,about,address,vertical,websites,profile_picture_url,verified_name,messaging_product` | |
| POST | `/{Version}/{WhatsApp-Business-Profile-ID}` | body sama dengan D3 (`messaging_product` wajib) → `{ id, success }` |

`vertical` di node ini lebih lengkap: `ALCOHOL, APPAREL, AUTO, BEAUTY, EDU, ENTERTAIN, EVENT_PLAN, FINANCE, GOVT, GROCERY, HEALTH, HOTEL, MATRIMONY_SERVICE, NONPROFIT, NOT_A_BIZ, ONLINE_GAMBLING, OTHER, OTC_DRUGS, PHYSICAL_GAMBLING, PROF_SERVICES, RESTAURANT, RETAIL, TRAVEL, UNDEFINED`
## M. Message Template API

Sumber: `reference/whatsapp-business-account/message-template-api.md`

| Method | Path | Kegunaan |
|---|---|---|
| POST | `/{Version}/{WABA-ID}/message_templates` | buat template |
| GET | `/{Version}/{WABA-ID}/message_templates` | list template. Query resmi di reference: `fields`, `limit`, `after`, `before`. Guide Template Management juga mendokumentasikan filter `status` (mis. `status=approved`, `status=rejected`). Filter lain (`name`, `category`, `language`, `summary`) **tidak terdokumentasi**, jadi filter di sisi aplikasi |
| GET | `/{Version}/{TEMPLATE_ID}?fields=...` | detail 1 template |
| POST | `/{Version}/{TEMPLATE_ID}` | edit template (hanya status APPROVED/REJECTED/PAUSED; 1x/hari, 10x/bulan) |
| DELETE | `/{Version}/{WABA-ID}/message_templates?name=<nama>` | hapus semua bahasa |
| DELETE | `...?name=<nama>&hsm_id=<id>` | hapus 1 bahasa |
| DELETE | `...?hsm_ids=["id1","id2"]` | hapus banyak sekaligus (maks 100) |

### M1. Body create (POST message_templates)
| Field | Tipe | Wajib | Catatan |
|---|---|---|---|
| `name` | string | Ya | huruf kecil, angka, `_`; maks 512 karakter |
| `language` | string | Ya | mis. `id`, `en_US` |
| `category` | enum | Ya | `MARKETING`, `UTILITY`, `AUTHENTICATION` (`FREE_SERVICE` khusus) |
| `components` | array | Ya (praktisnya) | lihat M3 |
| `parameter_format` | enum | Tidak | `POSITIONAL` (`{{1}}`) \| `NAMED` (`{{nama}}`) |
| `allow_category_change` | bool | Tidak | izinkan Meta mengubah kategori secara otomatis |
| `sub_category` | enum | Tidak | `ORDER_DETAILS, ORDER_STATUS, RICH_ORDER_STATUS, BOOKING_STATUS, FRAUD_ALERT, FLIGHT_DELAY_AND_GATE_CHANGE_ALERT, CALL_PERMISSIONS_REQUEST` |
| `message_send_ttl_seconds` | int | Tidak | TTL pengiriman (authentication/utility/marketing) |
| `cta_url_link_tracking_opted_out` | bool | Tidak | matikan click tracking pada URL button |
| `display_format` | enum | Tidak | `ORDER_DETAILS` |
| `is_primary_device_delivery_only` | bool | Tidak | |
| `send_type` | enum | Tidak | `campaign` \| `direct` (MM API) |
| `library_template_name` | string | Tidak | buat template dari Template Library |
| `library_template_body_inputs` | object | Tidak | input body untuk template library |
| `library_template_button_inputs` | array | Tidak | input tombol untuk template library |

Response: `{ "id": "<TEMPLATE_ID>", "status": "PENDING|APPROVED|REJECTED", "category": "UTILITY" }`
Batas: maksimal 100 template dibuat per jam per WABA.

### M2. Field objek MessageTemplate (GET)
`id, name, language, category, previous_category, correct_category, sub_category, status, rejected_reason, components[], parameter_format, quality_score { score, reason, reasons[], date }, health_status { can_send_message }, last_updated_time, message_send_ttl_seconds, cta_url_link_tracking_opted_out, display_format, is_primary_device_delivery_only, is_sms_fallback_enabled, library_template_name, source (auto_generated|manual), bid_spec { bid_strategy, bid_amount }, degrees_of_freedom_spec, ad_account_id, ad_campaign_id, ad_adset_id, ad_id`

Enum:
- `status`: `APPROVED, PENDING, REJECTED, PAUSED, DISABLED, IN_APPEAL, LIMIT_EXCEEDED, ARCHIVED, DELETED, PENDING_DELETION`
- `quality_score.score`: `GREEN, YELLOW, RED, UNKNOWN`
- `rejected_reason`: `ABUSIVE_CONTENT, CATEGORY_NOT_AVAILABLE, INCORRECT_CATEGORY, INVALID_FORMAT, NONE, PROMOTIONAL, SCAM, TAG_CONTENT_MISMATCH`

Response list: `data[]`, `paging { cursors { before, after }, next, previous }`.

### M3. Components
| `type` | Field |
|---|---|
| `HEADER` | `format`: `TEXT` \| `IMAGE` \| `VIDEO` \| `DOCUMENT` \| `LOCATION`; `text` (maks 60 karakter, 1 variabel); `example.header_text[]` atau `example.header_handle[]` (handle dari Resumable Upload) |
| `BODY` | `text` (maks 1024 karakter); `example.body_text[[...]]` (positional) / `example.body_text_named_params[]` (named); authentication: `add_security_recommendation` |
| `FOOTER` | `text` (maks 60 karakter); authentication: `code_expiration_minutes` (1–90) |
| `BUTTONS` | `buttons[]` (lihat bawah) |
| `CAROUSEL` | `cards[]` yang masing-masing berisi components header/body/buttons (maks 10 card) |
| `LIMITED_TIME_OFFER` | `limited_time_offer { text, has_expiration }` |

**Buttons** (`type`):
| type | Field | Catatan |
|---|---|---|
| `QUICK_REPLY` | `text` (≤25) | |
| `URL` | `text`, `url` (boleh 1 variabel di akhir, `example[]`) | maks 2 per template |
| `PHONE_NUMBER` | `text`, `phone_number` | maks 1 |
| `COPY_CODE` | `example` (kode) | |
| `OTP` | `otp_type`: `COPY_CODE` \| `ONE_TAP` \| `ZERO_TAP`; `autofill_text`, `package_name`, `signature_hash`, `supported_apps[]` | khusus authentication |
| `FLOW` | `flow_id` \| `flow_name` \| `flow_json`, `flow_action` (`navigate`\|`data_exchange`), `navigate_screen` | |
| `CATALOG` / `MPM` | `text` | template katalog / multi-product |
| `VOICE_CALL` / call request | `text` | |

Total tombol maksimal 10 per template. Kalau quick reply digabung dengan tombol lain, tombol perlu dikelompokkan.

### M4. Contoh: template authentication (OTP copy code)
```json
{ "name": "otp_login", "language": "id", "category": "AUTHENTICATION",
  "components": [
    { "type": "BODY", "add_security_recommendation": true },
    { "type": "FOOTER", "code_expiration_minutes": 5 },
    { "type": "BUTTONS", "buttons": [ { "type": "OTP", "otp_type": "COPY_CODE", "text": "Salin kode" } ] } ] }
```
Kirim:
```json
"template": { "name": "otp_login", "language": { "code": "id" }, "components": [
  { "type": "body", "parameters": [ { "type": "text", "text": "483920" } ] },
  { "type": "button", "sub_type": "url", "index": "0", "parameters": [ { "type": "text", "text": "483920" } ] } ] }
```

### M5. Webhook terkait template
- `message_template_status_update` (lihat N4)
- `message_template_quality_update` (`previous_quality_score`, `new_quality_score`)
- `message_template_components_update` (komponen berubah)
- `template_category_update` (Meta mengubah kategori: `previous_category`, `new_category`, `correct_category`)
## N. Webhooks Reference (payload)

### N0. Amplop umum
```json
{ "object": "whatsapp_business_account",
  "entry": [ { "id": "<WABA_ID>", "time": 1732451234,
    "changes": [ { "field": "<FIELD>", "value": { ... } } ] } ] }
```
`changes[].field` untuk pesan: `messages`. Untuk grup: `group_lifecycle_update`, `group_settings_update`, `group_participant_update`. Untuk field lain, nama field sama dengan topik yang di-subscribe.

`value` untuk field `messages` bisa berupa salah satu dari:
- **IncomingMessageValueGeneral**: `messaging_product`, `metadata`, `contacts[]`, `messages[]`
- **IncomingMessageValueSystem**: `messaging_product`, `metadata`, `messages[]` (tanpa `contacts`)
- **StatusMessageValue**: `messaging_product`, `metadata`, `statuses[]`
- **ErrorValue**: `messaging_product`, `metadata`, `errors[]`

`metadata`: `display_phone_number`, `phone_number_id`. **Gunakan `phone_number_id` untuk routing multi-nomor / multi-tenant.**

### N1. `contacts[]` dan identitas user (BSUID)
| Field | Keterangan |
|---|---|
| `profile.name` | nama profil WhatsApp user |
| `profile.username` | hanya ada kalau user memakai username |
| `wa_id` | WhatsApp ID (nomor). **Bisa tidak ada** kalau user memakai username dan nomornya tidak dibagikan |
| `user_id` | **BSUID (business-scoped user ID)**, selalu ada |
| `parent_user_id` | hanya kalau parent BSUID aktif |
| `identity_key_hash` | hanya kalau identity change check aktif (Settings `user_identity_change`) |

Di `messages[]`: `from` (nomor, bisa tidak ada), `from_user_id` (BSUID), `from_parent_user_id`.
**Rekomendasi Meta: identifikasi user dengan `user_id` / `from_user_id`, bukan `wa_id` / `from`.**

### N2. `messages[]` per tipe
Field dasar: `from`, `from_user_id`, `id` (wamid), `timestamp` (string Unix), `type`, `context` (opsional), `referral` (opsional), `group_id` (pesan grup).

| `type` | Objek |
|---|---|
| `text` | `text.body` |
| `image` | `image { caption, mime_type, sha256, id, url* }` |
| `video` | `video { caption, mime_type, sha256, id, url* }` |
| `audio` | `audio { voice (true = voice note), mime_type, sha256, id, url* }` |
| `document` | `document { caption, filename, mime_type, sha256, id, url* }` |
| `sticker` | `sticker { mime_type, sha256, id, animated, url* }` |
| `location` | `location { latitude, longitude, name, address, url }` |
| `contacts` | `contacts[] { name{formatted_name,first_name,last_name,middle_name,prefix,suffix}, phones[]{phone,wa_id,type}, emails[], addresses[], org{company,department,title}, urls[], birthday }` |
| `button` | `button { payload, text }`: klik quick reply di **template** (`context.id` = wamid template) |
| `interactive` | `interactive.type`: `button_reply { id, title }` \| `list_reply { id, title, description }` \| `nfm_reply { name: "flow"\|"address_message", body: "Sent", response_json: "<string JSON>" }` (Flow selesai; parse `response_json`, berisi `flow_token`) \| `call_permission_reply` |
| `reaction` | `reaction { message_id, emoji }`. `emoji` tidak ada berarti reaksi dihapus. Hanya untuk pesan ≤30 hari |
| `order` | `order { catalog_id, text, product_items[] { product_retailer_id, quantity, item_price, currency } }` |
| `system` | `system { body, type: user_changed_number\|user_changed_user_id, wa_id, user_id, parent_user_id, previous_user_id, previous_parent_user_id }` (tanpa `contacts`) |
| `request_welcome` | user membuka chat pertama kali (kalau `enable_welcome_message` aktif) |
| `edit` | `edit { original_message_id, message { context, type, <type> } }` (coexistence; edit dalam 15 menit) |
| `revoke` | pesan dihapus user (coexistence) |
| `unsupported` | `errors[]` berisi code **131051** (tipe tidak didukung) / **131060** (pesan tidak tersedia) |

`*url`: URL unduhan langsung (memerlukan token). Field ini mulai diluncurkan bertahap sejak 12 Nov 2025, jadi tetap siapkan fallback lewat `GET /{media-id}`.

**`context`**
- Balasan / klik tombol: `{ from, id }`
- Tombol "Message business" di katalog: `{ from, id, referred_product { catalog_id, product_retailer_id } }`
- Diteruskan (forwarded): `{ forwarded: true }` (≤5 kali) atau `{ frequently_forwarded: true }` (>5 kali)

**`referral`** (dari Click-to-WhatsApp Ads): `source_url, source_id, source_type (ad|post), body, headline, media_type (image|video), image_url, video_url, thumbnail_url, ctwa_clid, welcome_message { text }`. Simpan `ctwa_clid` untuk Conversions API.

### N3. `statuses[]`
| Field | Kapan ada | Nilai |
|---|---|---|
| `id` | selalu | wamid |
| `status` | selalu | `sent`, `delivered`, `read`, `played` (voice note pertama kali diputar), `failed` |
| `timestamp` | selalu | Unix (string) |
| `recipient_id` | selalu | nomor user, atau group ID |
| `recipient_user_id` | BSUID | |
| `recipient_type` | grup | `group` |
| `recipient_participant_id` | grup | nomor anggota |
| `recipient_identity_key_hash` | identity check aktif | |
| `biz_opaque_callback_data` | kalau diisi saat kirim | |
| `conversation { id, expiration_timestamp, origin.type }` | sent/delivered/read | `origin.type`: `authentication, authentication_international, marketing, marketing_lite, referral_conversion, service, utility`. **Sejak v24, objek ini hanya muncul kalau pesan berada di free entry point window** |
| `pricing { billable (deprecated), pricing_model, type, category, subtype }` | sent/delivered/read | `pricing_model`: `PMP` \| `CBP`; `type`: `regular, free_customer_service, free_entry_point, free_group_customer_service`; `category`: `authentication, authentication-international, marketing, marketing_lite, utility, service, referral_conversion, group_marketing, group_utility, group_service`; `subtype`: `paid_exempt` |
| `errors[] { code, title, message, error_data.details, href }` | `failed` | |

Catatan:
- Status `read` berarti pesan sudah delivered, dan webhook `delivered` bisa terlewat kalau user langsung membaca.
- Objek `conversation` dan `pricing` hanya ada di salah satu webhook (sent **atau** delivered/read), tidak di keduanya.

### N4. `message_template_status_update`
`value`: `event` (`APPROVED, REJECTED, PENDING, PAUSED, DISABLED, FLAGGED, IN_APPEAL, LIMIT_EXCEEDED, LOCKED, REINSTATED, ARCHIVED, UNARCHIVED, DELETED, PENDING_DELETION`), `message_template_id`, `message_template_name`, `message_template_language`, `message_template_category`, `reason` (`ABUSIVE_CONTENT, INCORRECT_CATEGORY, INVALID_FORMAT, NONE, PROMOTIONAL, SCAM, TAG_CONTENT_MISMATCH, ...`)
Field kondisional: `disable_info { disable_date }`; `other_info { title: FIRST_PAUSE|SECOND_PAUSE|RATE_LIMITING_PAUSE|UNPAUSE|DISABLED, description }`; `rejection_info { reason, recommendation }`.

### N5. `account_update`
`value.event`: `ACCOUNT_DELETED, ACCOUNT_RESTRICTION, ACCOUNT_VIOLATION, AD_ACCOUNT_LINKED, AUTH_INTL_PRICE_ELIGIBILITY_UPDATE, BUSINESS_PRIMARY_LOCATION_COUNTRY_UPDATE, DISABLED_UPDATE, MM_LITE_TERMS_SIGNED, PARTNER_ADDED, PARTNER_APP_INSTALLED, PARTNER_APP_UNINSTALLED, PARTNER_CLIENT_CERTIFICATION_STATUS_UPDATE, PARTNER_REMOVED, VOLUME_BASED_PRICING_TIER_UPDATE, ACCOUNT_OFFBOARDED, ACCOUNT_RECONNECTED`

Objek kondisional:
- `waba_info { waba_id, owner_business_id, partner_app_id, solution_id, solution_partner_business_ids[], ad_account_linked }`
- `violation_info { violation_type }`
- `ban_info { waba_ban_state: DISABLE|REINSTATE|SCHEDULE_FOR_DISABLE, waba_ban_date }`
- `restriction_info[] { restriction_type, expiration, remediation }`. `restriction_type`: `RESTRICTED_BIZ_INITIATED_MESSAGING, RESTRICTED_CUSTOMER_INITIATED_MESSAGING, RESTRICTED_ADD_PHONE_NUMBER_ACTION, RESTRICTED_UTILITY_TEMPLATES, RESTRICTED_DIRECT_SEND_UTILITY_TEMPLATES`, beberapa nilai `..._CALLING`
- `volume_tier_info { tier_update_time, pricing_category, tier ("min:max"), effective_month (YYYY-MM), region }`
- `disconnection_info { reason: ACCOUNT_DISCONNECTED|BUSINESS_DOWNGRADE|CHANGE_NUMBER|COMPANION_INACTIVITY|PRIMARY_INACTIVITY|USER_RE_REGISTERED, initiated_by: SYSTEM|USER }`
- `partner_client_certification_info { client_business_id, status: APPROVED|DISCARDED|FAILED|PENDING|REVOKED, rejection_reasons[] }`
- `auth_international_rate_eligibility { start_time, exception_countries[] }`
- `phone_number`, `country`

### N6. `phone_number_quality_update`
`value`: `display_phone_number`, `event` (`ONBOARDING` \| `THROUGHPUT_UPGRADE`), `max_daily_conversations_per_business` (`TIER_50, TIER_250, TIER_2K, TIER_10K, TIER_100K, TIER_UNLIMITED, TIER_NOT_SET`). Field `old_limit` dan `current_limit` deprecated sejak Feb 2026.

### N7. `user_preferences` (user stop/resume marketing)
`value.user_preferences[] { wa_id, user_id, parent_user_id, category: "marketing_messages", value: "stop"|"resume", detail, timestamp }` + `contacts[]`.
**Wajib dihormati**: jangan kirim marketing ke user dengan `value = stop`.

### N8. `smb_message_echoes` (coexistence: pesan yang dikirim dari WhatsApp Business app)
`value.message_echoes[] { from (nomor bisnis), to (user), id, timestamp, type: text|image|video|document|revoke|edit, <type>: {...} }`. Untuk `revoke` dan `edit`, objeknya berisi `original_message_id`.

### N9. Grup (`group_*`)
`value.groups[] { timestamp, group_id, type: group_create|group_delete|group_settings_update|group_add_participants|group_remove_participants, request_id, subject, description, added_participants[] {input, wa_id}, removed_participants[], profile_picture { mime_type, sha256 } }`. Field `request_id` dipakai untuk mencocokkan dengan response `POST /groups`.

### N10. `history` (coexistence onboarding)
Riwayat chat dikirim per fase: 0 (hari 0–1), 1 (hari 1–90), 2 (hari 90–180), dalam beberapa potongan. Pesan grup tidak disertakan. Media hanya untuk 14 hari pertama dan dikirim di webhook terpisah.

### N11. `calls`
Halaman reference `webhooks/reference/calls` **sedang 404** (24 Nov 2026). Dari guide Calling: `value.calls[] { id, from, to, event: connect|terminate, timestamp, direction: USER_INITIATED|BUSINESS_INITIATED, session { sdp_type, sdp }, status, start_time, end_time, duration, biz_opaque_callback_data }`. Status panggilan yang dimulai bisnis dikirim di `value.statuses[]` (`RINGING`, `ACCEPTED`, `REJECTED`).

### N12. Field lain yang bisa di-subscribe
`account_alerts`, `account_review_update`, `business_capability_update` (perubahan limit / kapabilitas), `phone_number_name_update` (hasil review display name), `security` (PIN dan perubahan keamanan), `message_template_quality_update`, `message_template_components_update`, `template_category_update`, `payment_configuration_update`, `partner_solutions`, `automatic_events` (konversi dari CTWA: pembelian / lead), `smb_app_state_sync` (sinkron kontak di coexistence).
## O. WhatsApp Flows API

Sumber: `flows/guides/flowsapi`

| Method | Path | Body / Query | Response |
|---|---|---|---|
| POST | `/{WABA-ID}/flows` | `name`, `categories[]` (wajib ≥1: `SIGN_UP, SIGN_IN, APPOINTMENT_BOOKING, LEAD_GENERATION, CONTACT_US, CUSTOMER_SUPPORT, SURVEY, OTHER`), `flow_json` (string), `publish` (bool, perlu `flow_json` valid), `clone_flow_id`, `endpoint_uri` | `{ id, success, validation_errors[] }` |
| GET | `/{WABA-ID}/flows` | | `data[] { id, name, status, categories, validation_errors }`, `paging` |
| GET | `/{FLOW-ID}?fields=...` | `id, name, categories, preview, status, validation_errors, json_version, data_api_version, endpoint_uri, whatsapp_business_account, application, health_status` (+ `health_status.phone_number(<PHONE_ID>)`) | detail |
| POST | `/{FLOW-ID}` | `name`, `categories`, `endpoint_uri`, `application_id` | `{ success }` |
| POST | `/{FLOW-ID}/assets` (multipart) | `file` (flow.json, ≤10 MB), `name=flow.json`, `asset_type=FLOW_JSON` | `{ success, validation_errors[] }` |
| GET | `/{FLOW-ID}/assets` | | `data[] { name, asset_type, download_url }` |
| GET | `/{FLOW-ID}?fields=preview.invalidate(false)` | `invalidate(true)` untuk membuat link baru | `preview { preview_url, expires_at }` (berlaku 30 hari) |
| POST | `/{FLOW-ID}/publish` | | `{ success }` (validation errors harus sudah bersih) |
| POST | `/{FLOW-ID}/deprecate` | | `{ success }` (flow yang sudah publish tidak bisa dihapus, hanya bisa dideprecate) |
| DELETE | `/{FLOW-ID}` | | `{ success }` (hanya status DRAFT) |
| POST | `/{DEST_WABA_ID}/migrate_flows?source_waba_id=<ID>&source_flow_names=[...]` | maks 100 flow, dalam business yang sama | `migrated_flows[] { source_name, source_id, migrated_id }`, `failed_flows[] { source_name, error_code, error_message }` |

`status` flow:
- `DRAFT`: hanya bisa dikirim dengan `"mode":"draft"`
- `PUBLISHED`: bisa dikirim, tidak bisa diedit atau dihapus
- `DEPRECATED`: tidak bisa dikirim atau dibuka
- `BLOCKED`: endpoint tidak sehat
- `THROTTLED`: maks 10 pesan/jam

`health_status.can_send_message` per node (`FLOW`, `WABA`, `BUSINESS`, `APP`): `AVAILABLE`, `LIMITED`, `BLOCKED`, disertai `errors[] { error_code, error_description, possible_solution }`.

Parameter preview URL: `interactive`, `flow_token`, `flow_action` (`navigate`\|`data_exchange`), `flow_action_payload` (URL-encoded JSON), `phone_number`, `debug`.

**Flow dengan endpoint (data_exchange)**: wajib menyetel `endpoint_uri`, menghubungkan `application_id`, dan meng-upload public key lewat Business Encryption API (D13). Request dari WhatsApp ke endpoint dienkripsi (RSA + AES-GCM). Endpoint menerima action `ping`, `INIT`, `data_exchange`, `BACK`, dan error notification.

**Kirim flow**: lewat pesan `interactive` tipe `flow` (B1) atau tombol `FLOW` di template (M3).
**Terima hasil**: webhook `messages` → `interactive.nfm_reply.response_json` (N2). Response ini tidak menyertakan Flow ID, jadi gunakan `flow_token` untuk mengidentifikasi flow.
## Z. Cheat Sheet Semua Endpoint

Prefix: `https://graph.facebook.com/{Version}`

### Phone number (`/{Phone-Number-ID}`)
| Method | Path | Fungsi |
|---|---|---|
| POST | `/messages` | kirim pesan / read receipt / typing indicator |
| POST | `/marketing_messages` | kirim marketing (MM API) |
| POST | `/media` | upload media |
| GET/POST | (root) | detail / update nomor, set PIN, webhook override, display name |
| GET/POST | `/whatsapp_business_profile` | profil bisnis |
| POST | `/request_code` · `/verify_code` | verifikasi nomor |
| POST | `/register` · `/deregister` | registrasi Cloud API |
| GET/POST | `/settings` | calling, payload encryption, storage, identity change |
| GET/POST/DELETE | `/block_users` | blokir user |
| GET/POST | `/message_qrdls` · GET/DELETE `/message_qrdls/{code}` | QR & deep link |
| GET/POST | `/official_business_account` | OBA |
| GET/POST | `/whatsapp_business_encryption` | public key Flows |
| GET/POST | `/business_compliance_info` | compliance (India) |
| GET/POST | `/whatsapp_commerce_settings` | katalog / keranjang |
| GET | `/call_permissions` · POST `/calls` | Calling |
| GET/POST | `/groups` | Groups |
| POST | `/conversational_automation` | welcome, ice breakers, commands |

### Media / Template / Group / Flow node
| Method | Path |
|---|---|
| GET/DELETE | `/{Media-ID}` · GET `<media_url>` |
| GET/POST | `/{TEMPLATE_ID}` |
| GET/POST/DELETE | `/{group_id}` · POST/DELETE `/{group_id}/participants` · POST/DELETE `/{group_id}/invite_link` · GET/POST/DELETE `/{group_id}/join_requests` |
| GET/POST/DELETE | `/{FLOW-ID}` · GET/POST `/{FLOW-ID}/assets` · POST `/{FLOW-ID}/publish` · POST `/{FLOW-ID}/deprecate` |

### WABA (`/{WABA-ID}`)
| Method | Path |
|---|---|
| GET/POST | (root) |
| GET/POST | `/phone_numbers` |
| GET/POST/DELETE | `/message_templates` |
| GET/POST/DELETE | `/subscribed_apps` |
| GET/POST/DELETE | `/assigned_users` |
| GET/POST | `/schedules` |
| GET | `/activities` · `/solutions` |
| GET/POST | `/flows` · POST `/migrate_flows` |

### Business (`/{Business-ID}`)
| Method | Path |
|---|---|
| GET | (root) · `/owned_whatsapp_business_accounts` · `/client_whatsapp_business_accounts` · `/extendedcredits` · `/preverified_numbers` |
| GET/POST | `/whatsapp_business_accounts` |
| POST | `/add_phone_numbers` · `/share_preverified_numbers` · `/onboard_partners_to_mm_lite` |

### Pre-verified / App / Solution / lainnya
| Method | Path |
|---|---|
| GET/DELETE | `/{Pre-Verified-Phone-Number-ID}` · POST `/request_code` · POST `/verify_code` · GET `/partners` |
| GET | `/{Application-ID}/connected_client_businesses` · `/{Application-ID}/whatsapp_business_solutions` |
| POST | `/{Application-ID}/whatsapp_business_solution` |
| GET | `/{Solution-ID}` · `/{Solution-ID}/access_token` |
| POST | `/{Solution-ID}/accept` · `/reject` · `/send_deactivation_request` · `/accept_deactivation_request` · `/reject_deactivation_request` |
| GET | `/{User-ID}/assigned_whatsapp_business_accounts` |
| GET | `/{Message-History-ID}/events` |
| GET | `/{Migration-Intent-ID}` · `/{WABA-Bot-ID}` · `/{WhatsApp-Account-Number-ID}` |
| GET/POST | `/{WhatsApp-Business-Profile-ID}` |

---

### Alur integrasi standar (direct developer)
1. Buat app (use case WhatsApp) → hubungkan ke Messaging account (WABA).
2. Buat **system user** → assign ke WhatsApp account + Messaging account → generate token (`whatsapp_business_messaging`, `whatsapp_business_management`).
3. Tambah nomor: `POST /{WABA}/phone_numbers` → `request_code` → `verify_code` → `register` (PIN).
4. `POST /{WABA}/subscribed_apps` → set Callback URL + Verify Token di App Dashboard → subscribe field `messages` (+ field lain yang diperlukan).
5. Buat template: `POST /{WABA}/message_templates` → tunggu webhook `message_template_status_update = APPROVED`.
6. Kirim: `POST /{Phone}/messages` (template di luar 24 jam; bebas di dalam CSW).
7. Terima: webhook `messages` → `statuses[]` / `messages[]` → untuk media, unduh lewat `GET /{media-id}` lalu `GET url`.

### Alur partner (Tech Provider, multi-tenant)
Embedded Signup → dapat `code` → tukar ke **business token** → `GET /debug_token` untuk melihat `granular_scopes` (WABA ID dan Phone ID) → `POST /{WABA}/subscribed_apps` (opsional `override_callback_uri`) → `register` nomor customer → share credit line kalau Anda Solution Partner (H7) → kirim pesan atas nama customer.

---

### Status capture (24 Nov 2026)
- ✅ 64 dari 66 halaman API Reference berhasil diambil dan dirangkum.
- ⚠️ 404 di sisi Meta: *WhatsApp Extension Preview API*, *WhatsApp Flow Metric Data Point API*, dan *Calls webhook reference* (diganti ringkasan dari guide).
- ⚠️ Halaman reference Message API tidak merinci schema tiap tipe pesan (text, template, interactive, dan seterusnya), jadi bagian B1 dilengkapi dari guide *Send messages* dan pengetahuan umum.
- Webhook per tipe pesan (text, image, audio, document, contacts, location, reaction, order, system, button, interactive, edit, group, errors, status) dan webhook akun utama (template status, account_update, quality, user_preferences, smb_message_echoes) diambil dari halaman webhook reference.
