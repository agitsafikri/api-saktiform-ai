# Impact Analysis — Chat Multiple Image

| Field | Value |
|---|---|
| Feature | Kirim multiple gambar pada `POST /send-message` |
| PRD | [chat-multiple-image.md](./chat-multiple-image.md) |
| Status | Analisis — belum ada perubahan kode. Desain transaksi direvisi oleh [TDD](../tdd/chat-multiple-image.md) §3 (`REQUIRED`, bukan `REQUIRES_NEW`) |
| Last updated | 2026-10-15 |

---

## 1. Kesimpulan

**Feasible — risiko rendah s/d sedang.** Perubahan terlokalisasi di 1 DTO, 1 service method, 1 controller method, ditambah 1 DTO response baru. **Tidak ada** perubahan skema DB, read path, payload WebSocket, `WhatsappClientHelper`, maupun modul Blast/Order/Bot.

Dua hal yang membuatnya "sedang", bukan "rendah":

1. **Transaksi** — `ChatService.messageHandler` harus direstrukturisasi (bukan sekadar dibungkus loop), karena pola `@Transactional` + `AFTER_COMMIT` listener yang sekarang akan menghilangkan gambar yang sudah terkirim jika terjadi gagal di tengah (§4).
2. **HTTP client tanpa timeout** — `WhatsappClientHelper` memakai `new RestTemplate()` (connect/read timeout tak terbatas). Dengan N panggilan beruntun sambil memegang koneksi DB, risiko thread & pool Hikari tertahan naik N kali lipat (§5).

Estimasi effort backend: **~1–1,5 hari** termasuk test. FE: di luar repo ini, ~1 hari (multi-select, upload per file, preview).

---

## 2. Peta Dependensi

```
SendMessageDto ──┬── ChatController.sendMessage        (POST /send-message, dipakai FE)
                 ├── ChatService.messageHandler        (inti perubahan)
                 └── BotService (3 call site: L53, L72, L114) — TEXT saja, tidak set mediaLink

ChatService.messageHandler
  ├── WhatsappClientHelper.sendImage/sendVideo/sendFile/sendAudio  (1 media per call — tidak berubah)
  ├── StorageService.extractPathFromPublicUrl / getPublicUrl        (tidak berubah)
  ├── ChatMessageService.saveChat → ChatRepository.save            (tidak berubah)
  ├── ConversationRepository.save                                  (tidak berubah)
  └── ApplicationEventPublisher → ChatAsyncEvent
        └── ChatWebSocketEventListener  @TransactionalEventListener(AFTER_COMMIT)
              └── ChatEventPublisher → STOMP /topic/...  (payload ChatListDto — tidak berubah)
```

Pemanggil `messageHandler`:

| Caller | Konteks transaksi | Dampak |
|---|---|---|
| `ChatController.sendMessage` | tidak ada (controller) → `messageHandler` membuka tx sendiri | **Berubah**: response `data` diisi ringkasan, mapping partial failure. |
| `BotService.handleBotReply` (×2) | tidak ada → tx milik `messageHandler` | Tidak ada perubahan perilaku (TEXT, 1 item). |
| `BotService.sendConfirmationMessage` | `@Transactional` (outer) → `messageHandler` **join** tx outer | Perlu dicek di TDD: bila save per item jadi `REQUIRES_NEW`, baris chat bot commit lebih awal dari tx outer. Dinilai **aman/lebih benar** (pesan memang sudah terkirim ke WA). |

---

## 3. Dampak per Komponen

| # | File | Jenis | Dampak | Risiko |
|---|---|---|---|---|
| 1 | `model/chat/SendMessageDto.java` | Ubah | `List<String> mediaLink` (sudah di working tree) + `@JsonFormat(with = ACCEPT_SINGLE_VALUE_AS_ARRAY)`. | **Tinggi jika tanpa anotasi** — FE lama kirim string → 400 untuk semua media. Dengan anotasi: rendah. |
| 2 | `service/chat/ChatService.java` | Ubah | Validasi upfront, loop IMAGE, caption index 0, persist+publish per item, update conversation sekali, return `SendMessageResult`; VIDEO/DOCUMENT/AUDIO pakai `get(0)`. | Sedang (transaksi, §4). |
| 3 | `controller/ChatController.java` | Ubah | `rest.setData(result)`; partial → 200 `success=false`. | Rendah. FE saat ini mengabaikan `data`. |
| 4 | `model/chat/SendMessageResult.java` | Baru | `{ total, sent, failed, chatIds }`. | Rendah. |
| 5 | Bean baru `ChatMessagePersister` | Baru | `loadContext` (readOnly), `saveSentChat`, `updateConversationAfterSend` — `@Transactional` propagasi **`REQUIRED`** di bean terpisah (self-invocation di `ChatService` tidak lewat proxy Spring). **Bukan `REQUIRES_NEW`** — lihat revisi di §4. | Sedang. |
| 6 | `service/chat/WhatsappClientHelper.java` | Opsional | Set connect/read timeout pada `RestTemplate`. Berdampak ke **semua** call WA (blast, connect device, dll). | Sedang — sebaiknya tiket terpisah, atau minimal dikonfigurasi longgar (mis. connect 5s, read 30s). |
| 7 | `application.properties` | Opsional | `chat.multi-image.max`, `chat.multi-image.delay-ms`. | Rendah. |
| 8 | `service/chat/bot/BotService.java` | Tidak berubah | Hanya perlu compile ulang. | Rendah. |
| 9 | `ChatListDto`, `ChatRepository`, `ConversationService.getListMessage`, `WhatsappMessageHandler`, `ChatWebSocketEventListener`, `ChatEventPublisher` | Tidak berubah | Setiap gambar = baris `Chat` biasa. | Tidak ada. |
| 10 | Blast (`BlastCampaign*`, `BlastSendTxService`, `BlastConversationService`) | Tidak berubah | Punya `mediaLink` sendiri (String), tidak memakai `SendMessageDto`. | Tidak ada. |
| 11 | `MessageTemplateService` / quick chat | Tidak berubah | Template tetap 1 media; FE yang memasukkan media template ke array jika perlu. | Tidak ada. |
| 12 | Entity `Chat` / DB | Tidak berubah | Tidak ada kolom baru, tidak ada migrasi. | Tidak ada. |
| 13 | `docs/api-reference.md`, `docs/frontend/api-mapping.md` | Ubah | `mediaLink: string \| string[]`, response baru. | — |

---

## 4. Analisis Transaksi (temuan kritis)

Kondisi sekarang:

- `messageHandler` = **satu** `@Transactional`.
- Semua `ChatAsyncEvent` dikonsumsi oleh `ChatWebSocketEventListener` dengan `@TransactionalEventListener(phase = AFTER_COMMIT)` → event baru dikirim ke WebSocket **setelah commit**, dan **dibuang** bila rollback.
- `catch (Exception e)` membungkus semua error menjadi `RuntimeException` → memicu rollback.

Jika loop dimasukkan begitu saja ke dalam tx tersebut:

| Skenario (3 gambar) | Di WhatsApp customer | Di DB | Di chat room agent |
|---|---|---|---|
| Semua sukses | 3 gambar | 3 baris | 3 event, muncul setelah commit (sekaligus di akhir) |
| Gagal di gambar ke-2 | **1 gambar** | **0 baris** (rollback) | **Tidak muncul** — event dibuang |

→ Data tidak konsisten dan tidak bisa dipulihkan (message_id gambar 1 hilang).

Desain yang direkomendasikan:

```
messageHandler(data, username)            // TIDAK @Transactional di level loop
  1. load conversation/contact/workspace/waba (read)
  2. validasi: jumlah, tipe, extractPathFromPublicUrl untuk SEMUA url  → gagal = 400, tanpa efek samping
  3. for i in 0..n-1:
       resp = client.sendImage(...)        // HTTP di luar transaksi DB
       if gagal → break
       chatId = chatPersister.saveAndPublish(...)   // @Transactional(REQUIRED), bean terpisah
                                                    // → commit → NEW_MESSAGE langsung terkirim per gambar
  4. if sent > 0: chatPersister.updateConversationAndPublish(lastChat)   // tx sendiri
  5. return SendMessageResult
```

Keuntungan tambahan: koneksi DB **tidak ditahan** selama panggilan HTTP ke provider (saat ini koneksi Hikari dipegang selama call WA berlangsung), dan agent melihat gambar muncul satu per satu secara realtime.

**Revisi (TDD §3):** propagasi yang dipakai adalah `REQUIRED`, bukan `REQUIRES_NEW`:

- Tanpa tx luar (controller, `handleBotReply`) → tiap call commit sendiri, hasilnya sama dengan `REQUIRES_NEW`.
- Dengan tx luar (`BotService.sendConfirmationMessage`) → `REQUIRES_NEW` akan membuat entity `Conversation` di tx luar stale; `escelateToAdmin`/`decreaseBotQuota` lalu `save()` entity stale itu → Hibernate meng-update semua kolom → `last_message*` tertimpa nilai lama. `REQUIRED` join tx luar sehingga perilaku sama dengan sekarang.
- `Conversation.contact` & `Workspace.waba` LAZY dan jalur bot tidak punya OSIV → pembacaan konteks harus di dalam tx (`loadContext` readOnly yang mengembalikan nilai primitif), bukan langsung di `messageHandler` yang tidak lagi `@Transactional`.

---

## 5. Dampak Performa & Resource

| Aspek | Sekarang (1 gambar) | Sesudah (N gambar, N ≤ 10) | Catatan |
|---|---|---|---|
| Call ke provider | 1 | N, sequential | Latensi ≈ N × latensi provider (+ delay opsional). |
| Durasi request HTTP | t | ≈ N·t | Perlu cek timeout FE (Axios) & reverse proxy. |
| Koneksi DB (Hikari max 20) | Dipegang selama call WA | **Turun** dengan desain §4 (hanya saat save) | Perbaikan dibanding sekarang. |
| Thread Tomcat | 1 thread selama t | 1 thread selama N·t | Dengan `RestTemplate` tanpa timeout, provider hang = thread hang tanpa batas. **Rekomendasi: set timeout.** |
| Event WebSocket | 2 | N + 1 | Ringan. |
| Storage | 1 file | N file (di-upload FE terpisah) | Upload per file, `max-file-size=10MB` tetap berlaku per file. |
| Risiko rate-limit/ban nomor | — | Naik ringan | Mitigasi: batas 10 + `delay-ms` configurable. |

---

## 6. Kompatibilitas

| Konsumen | Kompatibel? | Syarat |
|---|---|---|
| FE lama (`mediaLink: "url"`) | Ya | `ACCEPT_SINGLE_VALUE_AS_ARRAY` pada field. |
| FE lama (`mediaLink: null`, TEXT) | Ya | — |
| FE lama membaca response | Ya | FE tidak memakai `data`. Partial failure → `success=false` + HTTP 200: pastikan FE menampilkan `message` saat `success=false` (cek handler di `chatStore.sendMessage`). |
| BotService | Ya | Tidak set `mediaLink`. |
| `GET /conversation/message` & WebSocket consumer | Ya | Struktur item tidak berubah. |
| Swagger / API docs | Perlu update | Tipe `mediaLink` jadi array. |

---

## 7. Kondisi Working Tree Saat Ini

`SendMessageDto.mediaLink` sudah diubah ke `List<String>` (uncommitted) tanpa perubahan pemakai → **build gagal** di `ChatService` (5 titik: `sendImage`, `sendVideo`, `sendFile`, `sendAudio`, `extractPathFromPublicUrl`). Perubahan DTO ini **jangan di-commit sendiri**; gabungkan dengan implementasi `ChatService`.

---

## 8. Risiko & Mitigasi

| # | Risiko | Kemungkinan | Dampak | Mitigasi |
|---|---|---|---|---|
| R1 | FE lama 400 karena string vs array | Tinggi tanpa mitigasi | Tinggi | `ACCEPT_SINGLE_VALUE_AS_ARRAY` + test deserialisasi. |
| R2 | Rollback menghapus gambar yang sudah terkirim | Pasti terjadi bila tx tidak diubah | Tinggi | Desain §4 (`REQUIRES_NEW` per item, HTTP di luar tx). |
| R3 | Self-invocation membuat `@Transactional` tidak aktif | Sedang | Tinggi | Method di bean terpisah (`ChatMessagePersister`); verifikasi manual TDD §11.3. |
| R4 | Request timeout untuk 10 gambar | Sedang | Sedang | Batas 10; timeout `RestTemplate`; loading state di FE. Future: proses async. |
| R5 | Urutan tampil tertukar (`sentAt` sama) | Rendah | Rendah | Insert sequential; bila perlu offset 1 ms per index. |
| R6 | Rate-limit / ban nomor WA | Rendah | Tinggi | `delay-ms` configurable, batas 10. |
| R7 | Gambar bucket produk tampil rusak di chat | Sudah ada (existing) | Sedang | Di luar scope; tiket terpisah (PRD Q7). |
| R8 | Stale overwrite `last_message*` di `sendConfirmationMessage` | Tinggi jika `REQUIRES_NEW` | Sedang | Pakai `REQUIRED` (TDD §3). |
| R9 | `LazyInitializationException` di jalur bot setelah `@Transactional` dihapus dari `messageHandler` | Pasti jika tidak ditangani | Tinggi | `loadContext` readOnly → `ChatSendContext` (TDD §5). |

---

## 9. Testing yang Dibutuhkan

Saat ini hanya ada `ApiApplicationTests` (context load) — belum ada test untuk chat. Minimal:

1. **Unit — DTO**: deserialisasi `mediaLink` string, array, `null`, `[]`.
2. **Unit — ChatService** (mock `WhatsappClientHelper`, `StorageService`, repository):
   - 3 gambar sukses → 3× `sendImage`, caption hanya call pertama, 3× save, conversation update 1×.
   - URL invalid di index 1 → 0 call provider.
   - Gagal di index 1 → 1 tersimpan, index 2 tidak dikirim, result `sent=1`.
   - >10 gambar, `VIDEO` dengan 2 file, `IMAGE` tanpa media → exception validasi.
   - Single string → perilaku sama seperti sebelumnya.
3. **Verifikasi transaksi**: project belum punya DB test (tanpa H2/Testcontainers) → diverifikasi manual di staging (TDD §11.3): baris gambar sukses **tetap ada** setelah kegagalan di tengah, dan jalur bot tidak error.
4. **Regression manual**: kirim TEXT, reply TEXT, VIDEO, DOCUMENT, AUDIO, balasan bot, quick chat.

---

## 10. Rekomendasi Langkah

1. Setujui PRD §12 (terutama Q1, Q8, Q9).
2. Tulis TDD `docs/tdd/chat-multiple-image.md` berdasarkan desain §4.
3. Implementasi backend dalam satu commit (DTO + service + controller + test).
4. (Terpisah, disarankan) timeout `RestTemplate` di `WhatsappClientHelper`.
5. FE: multi-select + upload per file + kirim array.
