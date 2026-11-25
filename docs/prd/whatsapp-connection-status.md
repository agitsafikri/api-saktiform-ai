# PRD — WhatsApp Connection Status Check

| Field | Value |
|---|---|
| Feature name | WhatsApp Connection Status Check |
| Component | WhatsApp Instance (`WhatsappInstanceService`, `WhatsappClientHelper`, `WhatsappController`, `WhatsappBusinessApiRepository`), Workspace (`WorkspaceService`), Chat (`ChatService`), Blast (sender), WebSocket (`ChatEventPublisher`) |
| Status | **Draft** — menunggu keputusan §12 |
| Scope | Backend; satu kolom DB baru (opsional, §12 Q3); satu endpoint baru; satu scheduled job; satu event WebSocket baru |
| Last updated | 2026-11-23 |

> Nomor baris mengacu pada working tree per 2026-11-23. Kontrak endpoint gowa di §6.1 **belum diverifikasi** terhadap instance yang terpasang — lihat §12 Q1.

---

## 1. Background

Setiap nomor WhatsApp (WABA) didaftarkan sebagai device di server **gowa** (go-whatsapp-web-multidevice, mode multi-device; `WhatsappClientHelper` memakai `/devices`, header `X-Device-Id = waba.id`). Status koneksi disimpan di kolom `whatsapp_business_api.status` (`CONNECTED` / `DISCONNECTED`).

Status tersebut saat ini **tidak pernah disinkronkan dengan gowa**:

| Lokasi | Perilaku |
|---|---|
| `WhatsappInstanceService.registerWhatsappMultiDevice` | Set `DISCONNECTED` saat registrasi. |
| `WhatsappInstanceService.connectMultiDevice` (`:106-110`) | Set `CONNECTED` **hanya** jika gowa mengembalikan error `ALREADY_LOGGED_IN`. |
| `WhatsappController.receiveWebhookV2` / `WebhookEnvelopeV2` | Hanya event `message*`; event lain diabaikan diam-diam (`catch` kosong). |

Padahal status ini dipakai sebagai gate bisnis:

- `WhatsappBusinessApiRepository.getAvailableWhatsapp` (`:47`) — hanya WABA `CONNECTED` yang bisa dipilih.
- `WorkspaceService.upsertWorkspace` (`:58`) dan update workspace (`:140`) — menolak WABA `DISCONNECTED`.

## 2. Problem Statement

- **P-1 — Login sukses tidak tercatat.** Setelah pair code dimasukkan dan login berhasil, status tetap `DISCONNECTED` sampai admin menekan *connect* lagi dan kebetulan mendapat `ALREADY_LOGGED_IN`. Akibatnya WABA tidak muncul di daftar *available* dan tidak bisa dipasang ke workspace.
- **P-2 — Disconnect tidak terdeteksi.** Bila sesi WhatsApp logout dari HP, kedaluwarsa, atau gowa restart, status tetap `CONNECTED`. Pesan keluar (chat agent, bot, follow-up order, blast) gagal dengan error generik ("Terjadi kesalahan saat mengirim pesan ke whatsapp…") dan tidak ada yang tahu penyebabnya.
- **P-3 — Tidak ada cara memeriksa status secara manual.** Admin tidak punya tombol/endpoint untuk memastikan nomor masih tersambung.

## 3. Goals & Non-Goals

### Goals
- G-1: Status koneksi di DB mencerminkan kondisi gowa, dengan keterlambatan maksimum = interval polling (§12 Q2).
- G-2: Admin dapat memeriksa status satu WABA secara on-demand.
- G-3: Login via pair code tercatat `CONNECTED` tanpa langkah manual tambahan.
- G-4: Perubahan status (terutama `CONNECTED → DISCONNECTED`) dikirim real-time ke dashboard.
- G-5: Kegagalan kirim pesan karena WABA terputus menghasilkan pesan error yang jelas.

### Non-Goals
- Auto-reconnect / auto-login ulang (butuh pair code dari HP; tidak bisa otomatis).
- Notifikasi di luar dashboard (email, WhatsApp ke admin) — lihat §12 Q4.
- Monitoring kesehatan server gowa secara umum (CPU, memori, dsb.).
- Perubahan alur registrasi / pair code selain pencatatan status.

---

## 4. Definisi Status

| Status | Kondisi gowa | Keterangan |
|---|---|---|
| `CONNECTED` | `is_logged_in = true` **dan** `is_connected = true` | Siap kirim/terima. |
| `DISCONNECTED` | `is_logged_in = false`, **atau** device tidak ditemukan di gowa | Perlu login ulang (pair code). |
| *(tidak diubah)* | `is_logged_in = true` tetapi `is_connected = false` | Socket sementara putus; gowa biasanya reconnect sendiri. Lihat FR-4. |
| *(tidak diubah)* | gowa tidak bisa dihubungi (timeout / connection refused / 5xx) | Masalah server gowa, bukan WABA. Lihat FR-5. |

> Nilai `CONNECTED`/`DISCONNECTED` dipertahankan agar query & validasi yang ada (`getAvailableWhatsapp`, `WorkspaceService`) tetap berlaku tanpa perubahan.

---

## 5. User Stories

| ID | Sebagai | Saya ingin | Agar |
|---|---|---|---|
| US-1 | Superadmin | melihat status koneksi terkini di daftar nomor WhatsApp | tahu nomor mana yang perlu login ulang |
| US-2 | Superadmin | menekan tombol "Cek status" pada satu nomor | memastikan status saat itu juga |
| US-3 | Superadmin | setelah memasukkan pair code, status otomatis berubah menjadi `CONNECTED` | bisa langsung memasang nomor ke workspace |
| US-4 | Admin/Agent | diberi tahu saat nomor workspace terputus | tidak mengira customer yang tidak merespons |
| US-5 | Agent | mendapat pesan "WhatsApp terputus" saat kirim pesan gagal karena disconnect | tahu harus menghubungi superadmin, bukan mencoba ulang terus |

---

## 6. Functional Requirements

### 6.1 Integrasi gowa

| ID | Requirement |
|---|---|
| FR-1 | `WhatsappClientHelper` menambah method `getDeviceStatus(String deviceId)` yang memanggil endpoint status gowa dengan header `X-Device-Id = deviceId`. Kontrak yang diasumsikan (**wajib diverifikasi**, §12 Q1): `GET /app/status` → `{ "code": "SUCCESS", "results": { "is_connected": bool, "is_logged_in": bool, "device_id": string } }`. |
| FR-2 | Request status memakai timeout pendek (connect 3 detik, read 5 detik) agar polling tidak tertahan oleh gowa yang hang. Timeout ini tidak boleh mengubah timeout `RestTemplate` yang dipakai untuk kirim media. |

### 6.2 Sinkronisasi status

| ID | Requirement |
|---|---|
| FR-3 | `WhatsappInstanceService.checkStatus(UUID wabaId)` memanggil FR-1, memetakan hasil sesuai §4, menyimpan status baru ke `whatsapp_business_api.status`, mengisi `updated_at`, dan (bila §12 Q3 disetujui) `last_checked_at`. Mengembalikan DTO status (§7.1). |
| FR-4 | `is_logged_in = true` dan `is_connected = false` tidak mengubah status di DB; response FR-3 tetap melaporkan `connected = false` apa adanya. |
| FR-5 | Bila gowa tidak bisa dihubungi (`ResourceAccessException`, 5xx), status DB **tidak** diubah; response berisi `reachable = false` dan pesan "Server WhatsApp tidak dapat dihubungi". Device tidak ditemukan (404 / error code device-not-found dari gowa) dianggap `DISCONNECTED`. |
| FR-6 | Bila status berubah dari nilai sebelumnya, publish event WebSocket `WABA_STATUS_CHANGED` (§7.3) ke workspace pemilik WABA (jika ada) dan ke topik superadmin. Bila status tidak berubah, tidak ada event. |
| FR-7 | Perubahan status dicatat di log level `INFO` (`wabaId`, nomor, status lama → baru). Kegagalan menghubungi gowa dicatat `WARN`. |

### 6.3 Pengecekan on-demand

| ID | Requirement |
|---|---|
| FR-8 | Endpoint baru `GET /whatsapp/{wabaId}/status` (terautentikasi) menjalankan FR-3 dan mengembalikan `RestResponse<WabaStatusDto>`. WABA tidak ditemukan → `400` dengan pesan "Nomor WhatsApp tidak ditemukan". |
| FR-9 | Frontend memanggil FR-8 secara berulang (setiap 3 detik, maksimal 2 menit) setelah pair code ditampilkan, berhenti saat `status = CONNECTED`. Ini menutup P-1 tanpa bergantung pada error `ALREADY_LOGGED_IN`. |
| FR-10 | `connectMultiDevice` tetap mempertahankan perilaku `ALREADY_LOGGED_IN → CONNECTED` yang ada. |

### 6.4 Pengecekan berkala

| ID | Requirement |
|---|---|
| FR-11 | Job `@Scheduled` (`WabaStatusMonitor`) menjalankan FR-3 untuk semua WABA yang belum dihapus (`deleted_at IS NULL`) setiap `whatsapp.status.check-interval-ms` (default 120000 / 2 menit, §12 Q2). |
| FR-12 | Kegagalan satu WABA tidak menghentikan pengecekan WABA lain (try/catch per item). |
| FR-13 | Bila gowa tidak bisa dihubungi untuk WABA pertama, job boleh menghentikan putaran tersebut (server-level failure) dan mencatat satu `WARN`, bukan satu per WABA. |
| FR-14 | Job dapat dimatikan via `whatsapp.status.check-enabled=false` (default `true`). |

### 6.5 Pemakaian status

| ID | Requirement |
|---|---|
| FR-15 | `GET /whatsapp` (list) menampilkan status dari DB (tanpa memanggil gowa per baris) ditambah `lastCheckedAt` bila §12 Q3 disetujui. |
| FR-16 | Saat pengiriman pesan gagal (`ChatService.messageHandler`, blast sender, `OrderEventListener`) **dan** status WABA di DB `DISCONNECTED`, pesan error menjadi "Nomor WhatsApp workspace terputus, silakan hubungi admin untuk login ulang." Pengecekan memakai data DB; **tidak** memanggil gowa sebelum setiap kirim. |
| FR-17 | (Opsional, §12 Q5) Bila pengiriman gagal dan status DB masih `CONNECTED`, picu `checkStatus` secara async agar disconnect terdeteksi lebih cepat dari interval polling. |

---

## 7. Kontrak API & Event

### 7.1 `GET /whatsapp/{wabaId}/status`

```json
{
  "success": true,
  "message": "success",
  "data": {
    "wabaId": "6f1c…",
    "nomorWhatsapp": "628123456789",
    "status": "CONNECTED",
    "previousStatus": "DISCONNECTED",
    "loggedIn": true,
    "connected": true,
    "reachable": true,
    "checkedAt": "2026-11-23 10:15"
  }
}
```

`reachable = false` (FR-5): `status` berisi nilai DB yang tidak diubah, `loggedIn`/`connected` bernilai `null`, `message` berisi penjelasan.

### 7.2 `GET /whatsapp` (list)

`WabaListDto` ditambah field `lastCheckedAt` (string `yyyy-MM-dd HH:mm`, nullable) — hanya bila §12 Q3 disetujui. Field lain tidak berubah.

### 7.3 Event WebSocket `WABA_STATUS_CHANGED`

Dipublish lewat `ChatEventPublisher` ke `/topic/workspace/{workspaceId}` (jika WABA terpasang ke workspace) dan topik superadmin (§12 Q4):

```json
{
  "eventType": "WABA_STATUS_CHANGED",
  "workspaceId": 12,
  "data": { "wabaId": "6f1c…", "nomorWhatsapp": "628123456789", "status": "DISCONNECTED", "previousStatus": "CONNECTED" },
  "timestamp": "2026-11-23 10:15"
}
```

---

## 8. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | Satu putaran polling untuk ≤ 50 WABA selesai < 30 detik (sekuensial, timeout FR-2). |
| NFR-2 | Polling tidak menambah beban DB berarti: satu `findAll` + `save` hanya bila status berubah atau untuk `last_checked_at`. |
| NFR-3 | Tidak ada panggilan gowa pada jalur kirim pesan (FR-16), kecuali async FR-17. |
| NFR-4 | Kredensial basic auth gowa tidak muncul di log. |

---

## 9. Acceptance Criteria

| # | Skenario | Hasil yang diharapkan |
|---|---|---|
| AC-1 | WABA baru, admin memasukkan pair code di HP | Dalam ≤ 3 detik setelah login sukses, FR-8 mengembalikan `CONNECTED`; WABA muncul di `/whatsapp/available`. |
| AC-2 | WABA `CONNECTED`, sesi di-logout dari HP | Dalam ≤ 1 interval polling, status DB `DISCONNECTED` dan event `WABA_STATUS_CHANGED` diterima dashboard. |
| AC-3 | Server gowa dimatikan | Status DB semua WABA **tidak** berubah; FR-8 mengembalikan `reachable = false`; log berisi satu `WARN` per putaran polling. |
| AC-4 | gowa melaporkan `is_logged_in = true, is_connected = false` | Status DB tidak berubah. |
| AC-5 | Agent mengirim pesan dari workspace yang WABA-nya `DISCONNECTED` dan kirim gagal | Error yang tampil: "Nomor WhatsApp workspace terputus, …". |
| AC-6 | Status tidak berubah antar dua putaran polling | Tidak ada event WebSocket. |
| AC-7 | `whatsapp.status.check-enabled=false` | Job tidak memanggil gowa. |

Unit test minimal: pemetaan status (§4) untuk semua kombinasi; FR-5 (gowa tidak dapat dihubungi); FR-6 (event hanya saat berubah); FR-12 (satu WABA error tidak menghentikan yang lain).

---

## 10. Risiko

| ID | Risiko | Mitigasi |
|---|---|---|
| R-1 | Kontrak endpoint gowa berbeda dari asumsi FR-1 (versi gowa berbeda) | Verifikasi dulu (§12 Q1) sebelum implementasi; mapping respons diisolasi di `WhatsappClientHelper`. |
| R-2 | Status "berkedip" saat socket putus sebentar | FR-4: hanya `is_logged_in` yang menentukan `DISCONNECTED`. |
| R-3 | gowa down membuat semua WABA tampak `DISCONNECTED` lalu workspace tidak bisa disetel | FR-5: status tidak diubah bila gowa tidak terjangkau. |
| R-4 | Status DB lama yang salah (`DISCONNECTED` padahal tersambung) langsung terkoreksi saat rilis → WABA tiba-tiba muncul di daftar available | Diinginkan; umumkan ke superadmin. |
| R-5 | Polling menambah beban gowa | Interval default 2 menit, sekuensial, dapat dimatikan (FR-14). |

---

## 11. Rencana Rilis

1. Verifikasi kontrak endpoint status gowa (§12 Q1) — **blocker**.
2. Backend: FR-1–FR-8, FR-10 + unit test.
3. Backend: FR-11–FR-14 (scheduled job), FR-6 (event).
4. Frontend: tombol "Cek status", polling setelah pair code (FR-9), badge status & toast `WABA_STATUS_CHANGED`.
5. Backend: FR-16 (pesan error) dan FR-17 bila disetujui.

---

## 12. Open Questions (dengan rekomendasi default)

| # | Pertanyaan | Rekomendasi default |
|---|---|---|
| Q1 | Versi gowa yang terpasang dan kontrak endpoint status-nya (`GET /app/status` vs `GET /devices/{id}` / lainnya)? | Cek dokumentasi/OpenAPI instance di `WHATSAPP_MULTIDEVICE_API_URL` sebelum implementasi. |
| Q2 | Interval polling? | 2 menit (konfigurasi `whatsapp.status.check-interval-ms`). |
| Q3 | Tambah kolom `last_checked_at` di `whatsapp_business_api`? | Ya — membantu admin membedakan "status lama" vs "baru dicek". `ddl-auto=update` menambah kolom otomatis. |
| Q4 | Siapa yang menerima notifikasi disconnect? Hanya dashboard superadmin, atau juga admin/agent workspace terkait? Perlu kanal di luar dashboard? | Dashboard superadmin + admin workspace terkait; tanpa kanal eksternal di fase ini. |
| Q5 | Aktifkan FR-17 (cek status async saat kirim gagal)? | Ya — murah dan mempercepat deteksi. |
| Q6 | Apakah gowa versi terpasang mengirim webhook event koneksi (login/logout)? Bila ya, tangani di `WebhookEnvelopeV2` sebagai pelengkap polling. | Fase berikutnya; polling tetap menjadi sumber utama. |
