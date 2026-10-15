# TDD — Chat Multiple Image — Backend

| Field | Value |
|---|---|
| Feature name | Chat Multiple Image |
| Dokumen induk | [PRD — Chat Multiple Image](../prd/chat-multiple-image.md) (Status: *Approved*), [Impact Analysis](../prd/chat-multiple-image-impact-analysis.md) |
| Component | `SendMessageDto` · `ChatController` · `ChatService` · **baru:** `ChatMessagePersister`, `SendMessageResult`, `ChatSendContext` |
| Status | **Implemented** — Fase 1–6 selesai, unit test hijau (14/14). Fase 7 (verifikasi manual staging) belum dilakukan |
| Scope | Backend-only; FE di repo terpisah |
| Last updated | 2026-10-15 |
| Target pembaca | Backend Developer, Reviewer, QA, Frontend |

> TDD ini menerjemahkan PRD menjadi desain teknis. Semua Open Question PRD §12 **RESOLVED** mengikuti rekomendasi default. Snippet kode adalah **acuan desain**, bukan kode final.

---

## Daftar Isi

1. [Tujuan & Ruang Lingkup Teknis](#1-tujuan--ruang-lingkup-teknis)
2. [Baseline Codebase](#2-baseline-codebase)
3. [Keputusan Desain — Transaksi](#3-keputusan-desain--transaksi)
4. [Perubahan Model](#4-perubahan-model)
5. [Komponen Baru — `ChatMessagePersister`](#5-komponen-baru--chatmessagepersister)
6. [Perubahan `ChatService.messageHandler`](#6-perubahan-chatservicemessagehandler)
7. [Perubahan Controller](#7-perubahan-controller)
8. [Konfigurasi](#8-konfigurasi)
9. [Traceability PRD → Implementasi](#9-traceability-prd--implementasi)
10. [Error Handling](#10-error-handling)
11. [Testing & Verifikasi](#11-testing--verifikasi)
12. [Rencana Implementasi Bertahap](#12-rencana-implementasi-bertahap)
13. [Analisis Regresi](#13-analisis-regresi)

---

## 1. Tujuan & Ruang Lingkup Teknis

`POST /send-message` menerima `mediaLink` berupa string **atau** array. Untuk `messageType = IMAGE` dengan 1..10 URL, backend mengirim gambar satu per satu ke provider, menyimpan satu baris `Chat` per gambar sukses (commit per gambar), mem-publish `NEW_MESSAGE` per gambar, lalu meng-update `Conversation` sekali di akhir.

**Prinsip:**

- **Tanpa perubahan skema / read path / payload WebSocket.** 1 gambar = 1 baris `Chat` biasa.
- **Validasi dulu, kirim kemudian.** Semua URL divalidasi sebelum call provider pertama.
- **HTTP provider di luar transaksi DB.** Koneksi Hikari tidak ditahan selama menunggu provider.
- **Commit per gambar** saat tidak ada transaksi luar (jalur controller & `handleBotReply`); **join** transaksi luar bila ada (jalur `sendConfirmationMessage`) — perilaku bot tidak berubah.
- **Satu gambar = perilaku identik dengan sekarang** (PRD FR-16).

Out of scope: perubahan `WhatsappClientHelper` (termasuk timeout), multiple media untuk VIDEO/DOCUMENT/AUDIO, reply untuk IMAGE, bug bucket produk, bug `replyChat` di `WhatsappMessageHandler`.

---

## 2. Baseline Codebase

### 2.1 Rantai pemanggilan

```
POST /send-message
  ChatController.sendMessage(SendMessageDto, request)           ChatController.java:163-180
    └── chatService.messageHandler(data, username)              ChatService.java:55-155   @Transactional
          ├── conversationRepository.findById
          ├── conversation.getContact()             ← LAZY (Conversation.java:20)
          ├── workspaceRepository.findById(contact.getIdWorkspace())
          ├── workspace.getWaba()                   ← LAZY (Workspace.java:23)
          ├── client.sendXxx(...)                   ← HTTP, di dalam tx
          ├── chatMessageService.saveChat(chat)
          ├── conversationRepository.save(conversation)
          └── eventPublisher.publishEvent(NEW_MESSAGE, ASSIGNED_CONVERSATION_UPDATED)
                └── ChatWebSocketEventListener   @TransactionalEventListener(AFTER_COMMIT)
```

### 2.2 Pemanggil `messageHandler`

| Caller | Thread | Tx luar | OSIV |
|---|---|---|---|
| `ChatController.sendMessage` | Tomcat request | Tidak | Ya (default `open-in-view=true`) |
| `BotService.handleBotReply` (L57, L76) | `BotDelayManager` scheduler / `@Async` | Tidak | **Tidak** |
| `BotService.sendConfirmationMessage` (L118) | `@Async` listener | **Ya** (`@Transactional` L103) | **Tidak** |

### 2.3 Kondisi working tree

`SendMessageDto.mediaLink` sudah `List<String>` (uncommitted) → `ChatService` tidak compile. Perubahan ini dilanjutkan oleh TDD ini, di-commit bersama.

---

## 3. Keputusan Desain — Transaksi

### 3.1 Masalah

| # | Masalah | Sumber |
|---|---|---|
| T1 | Satu tx untuk seluruh loop → gagal di gambar ke-k me-rollback gambar 1..k-1 yang sudah terkirim; event `AFTER_COMMIT` ikut dibuang. | Impact Analysis §4 |
| T2 | Jika `@Transactional` dihapus begitu saja dari `messageHandler`, akses `conversation.getContact()` / `contact.getPhoneNumber()` di thread bot (tanpa tx, tanpa OSIV) → `LazyInitializationException`. | `Conversation.contact` LAZY |
| T3 | Jika save per item memakai `REQUIRES_NEW`, di jalur `sendConfirmationMessage` entitas `Conversation` di persistence context **tx luar** menjadi stale; `chatService.escelateToAdmin` / `decreaseBotQuota` (L119, L122) kemudian `save()` entitas stale itu → Hibernate meng-update semua kolom (tanpa `@DynamicUpdate`) → `last_message*` tertimpa nilai lama. | Analisis TDD |
| T4 | `@Transactional` pada method di class yang sama tidak aktif saat dipanggil dari `this` (proxy Spring). | Spring AOP |

### 3.2 Keputusan

1. `ChatService.messageHandler` **tidak lagi** `@Transactional`.
2. Bean baru **`ChatMessagePersister`** (class terpisah → lolos T4) dengan tiga method `@Transactional` **propagasi default `REQUIRED`**:
   - `loadContext(conversationId)` — `readOnly`, mengembalikan record `ChatSendContext` berisi nilai primitif (bukan entity) → menyelesaikan T2.
   - `saveSentChat(...)` — simpan 1 `Chat` + publish `NEW_MESSAGE`.
   - `updateConversationAfterSend(...)` — reload `Conversation`, set `last_message*`, save, publish `ASSIGNED_CONVERSATION_UPDATED`.
3. Efek `REQUIRED`:
   - **Tanpa tx luar** (controller, `handleBotReply`) → setiap call = tx sendiri → commit per gambar (T1 ✓), event dikirim setelah tiap commit → gambar muncul satu per satu di chat room.
   - **Dengan tx luar** (`sendConfirmationMessage`) → join tx luar, `findById` mengembalikan entity managed yang sama → tidak ada stale overwrite (T3 ✓); perilaku identik dengan sekarang (TEXT, 1 item).

> **Revisi terhadap Impact Analysis:** rekomendasi `REQUIRES_NEW` di Impact Analysis §3/§4 diganti `REQUIRED` di bean terpisah karena T3. Impact Analysis sudah diperbarui.

### 3.3 Diagram jalur controller (3 gambar, gagal di gambar 3)

```
messageHandler (no tx)
  ├─ persister.loadContext()           [tx#1 readOnly] → ChatSendContext
  ├─ validate(all urls)                                → paths[]
  ├─ client.sendImage(url0, caption)   (HTTP, no tx)  → SUCCESS
  ├─ persister.saveSentChat(#0)        [tx#2 commit]  → WS NEW_MESSAGE #0
  ├─ client.sendImage(url1, "")                        → SUCCESS
  ├─ persister.saveSentChat(#1)        [tx#3 commit]  → WS NEW_MESSAGE #1
  ├─ client.sendImage(url2, "")                        → FAILED → break
  ├─ persister.updateConversationAfterSend(last=#1)  [tx#4 commit] → WS ASSIGNED_CONVERSATION_UPDATED
  └─ return SendMessageResult{total=3, sent=2, failed=1}
```

---

## 4. Perubahan Model

### 4.1 `model/chat/SendMessageDto.java` (ubah)

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SendMessageDto {
    UUID conversationId;
    ChatType messageType;
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    List<String> mediaLink;
    String message;
    UUID repliedMessageId;
}
```

- `"mediaLink": "url"` → `["url"]`; `null` / absen → `null`; `[]` → `[]`.
- Nama JSON tetap `mediaLink` (tidak memutus FE).
- `BotService` tidak memanggil `setMediaLink` → tidak perlu diubah. Constructor `@AllArgsConstructor` berubah signature; tidak ada pemakai (sudah dicek via grep).

### 4.2 `model/chat/SendMessageResult.java` (baru)

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SendMessageResult {
    int total;
    int sent;
    int failed;
    List<UUID> chatIds;
}
```

### 4.3 `model/chat/ChatSendContext.java` (baru)

```java
public record ChatSendContext(
        UUID conversationId,
        Long workspaceId,
        String deviceId,      // workspace.getWaba().getId().toString()
        String phoneNumber,
        String contactName) {}
```

---

## 5. Komponen Baru — `ChatMessagePersister`

Lokasi: `service/chat/ChatMessagePersister.java`.

```java
@Service
public class ChatMessagePersister {
    private final ConversationRepository conversationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ChatMessageService chatMessageService;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    @Transactional(readOnly = true)
    public ChatSendContext loadContext(UUID conversationId) {
        var conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation tidak ditemukan"));
        var contact = conversation.getContact();
        var workspace = workspaceRepository.findById(contact.getIdWorkspace())
                .orElseThrow(() -> new RuntimeException("Workspace tidak ditemukan"));
        return new ChatSendContext(conversation.getId(), workspace.getId(),
                workspace.getWaba().getId().toString(), contact.getPhoneNumber(), contact.getNamaKontak());
    }

    @Transactional
    public Chat saveSentChat(UUID conversationId, String messageId, String type,
                             String pesan, String mediaPath, String username) {
        var chat = new Chat();
        chat.setIdConversation(conversationId);
        chat.setMessageId(messageId);
        chat.setType(type);
        chat.setSentAt(Instant.now());
        chat.setStatus("SENT");
        chat.setPengirim(username);
        chat.setPesan(pesan);
        chat.setMedia(mediaPath);
        var saved = chatMessageService.saveChat(chat);

        var dto = new ChatListDto(saved.getId(), saved.getType(), saved.getPengirim(), saved.getPesan(),
                saved.getMedia() != null ? storageService.getPublicUrl(saved.getMedia()) : null,
                saved.getSentAt());
        eventPublisher.publishEvent(ChatAsyncEvent.builder()
                .eventType(ChatAsyncEvent.EventType.NEW_MESSAGE)
                .conversationId(conversationId)
                .data(dto)
                .timestamp(saved.getSentAt().atZone(ZoneId.of("Asia/Jakarta")).format(formatter))
                .build());
        return saved;
    }

    @Transactional
    public void updateConversationAfterSend(ChatSendContext ctx, Chat lastChat, String lastMessage) {
        var conversation = conversationRepository.findById(ctx.conversationId())
                .orElseThrow(() -> new RuntimeException("Conversation tidak ditemukan"));
        conversation.setLastMessageAt(lastChat.getSentAt());
        conversation.setLastMessage(lastMessage);
        conversation.setLastMessageType(lastChat.getType());
        conversationRepository.save(conversation);

        var data = new ConversationUpdatedData();
        data.setId(conversation.getId());
        data.setLastMessage(lastMessage);
        data.setLastMessageType(lastChat.getType());
        data.setStatus(conversation.getStatus());
        data.setChatStatus(conversation.getChatStatus());
        data.setContactName(ctx.contactName());
        data.setLastMessageTime(lastChat.getSentAt().atZone(ZoneId.of("Asia/Jakarta")).format(formatter));

        eventPublisher.publishEvent(ChatAsyncEvent.builder()
                .eventType(ChatAsyncEvent.EventType.ASSIGNED_CONVERSATION_UPDATED)
                .workspaceId(ctx.workspaceId())
                .data(data)
                .timestamp(data.getLastMessageTime())
                .build());
    }
}
```

Catatan:
- Isi `saveSentChat` & `updateConversationAfterSend` adalah **pemindahan** kode `ChatService.java:100-149`, bukan logika baru. Format tanggal, zona, dan event identik.
- `ChatService` tetap menyimpan `formatter` miliknya untuk `takeoverConversation`.

---

## 6. Perubahan `ChatService.messageHandler`

### 6.1 Signature

```java
public SendMessageResult messageHandler(SendMessageDto data, String username)   // tidak @Transactional
```

Return type berubah dari `void`. Pemanggil di `BotService` mengabaikan return → tetap compile.

### 6.2 Dependensi baru

- Inject `ChatMessagePersister`.
- `@Value("${chat.multi-image.max:10}") int maxImages`
- `@Value("${chat.multi-image.delay-ms:0}") long delayMs`

### 6.3 Alur

```java
public SendMessageResult messageHandler(SendMessageDto data, String username) {
    var ctx = persister.loadContext(data.getConversationId());
    var type = data.getMessageType();
    var mediaPaths = validateAndExtract(type, data.getMediaLink());   // List<String> path, index = index url
    var mediaUrls  = cleanMediaLinks(data.getMediaLink());

    int total = type == ChatType.TEXT ? 1 : mediaUrls.size();
    List<Chat> sentChats = new ArrayList<>();

    for (int i = 0; i < total; i++) {
        if (i > 0 && delayMs > 0) sleepQuietly(delayMs);

        String caption = i == 0 ? data.getMessage() : "";
        String url = type == ChatType.TEXT ? null : mediaUrls.get(i);

        WhatsappResponse<SendResults> response;
        try {
            response = sendToProvider(ctx, type, caption, url, i == 0 ? data.getRepliedMessageId() : null);
        } catch (Exception e) {
            log.error("Gagal kirim pesan index {} conversation {}", i, ctx.conversationId(), e);
            response = null;
        }
        if (response == null || !"SUCCESS".equals(response.getCode())) break;

        sentChats.add(persister.saveSentChat(ctx.conversationId(),
                response.getResults().getMessage_id(), type.name(),
                i == 0 ? data.getMessage() : null,
                type == ChatType.TEXT ? null : mediaPaths.get(i),
                username));
    }

    if (sentChats.isEmpty()) {
        throw new RuntimeException("Terjadi kesalahan saat mengirim pesan ke whatsapp, silahkan coba lagi.");
    }

    var last = sentChats.getLast();
    var lastMessage = last.getPesan() != null ? last.getPesan() : sentChats.getFirst().getPesan();
    persister.updateConversationAfterSend(ctx, last, lastMessage);

    return new SendMessageResult(total, sentChats.size(), total - sentChats.size(),
            sentChats.stream().map(Chat::getId).toList());
}
```

### 6.4 `validateAndExtract`

```java
private List<String> validateAndExtract(ChatType type, List<String> rawLinks) {
    if (type == null) throw new IllegalArgumentException("Tipe pesan wajib diisi");
    if (type == ChatType.TEXT) return List.of();

    var links = cleanMediaLinks(rawLinks);              // buang null & blank (FR-3)
    if (type == ChatType.IMAGE) {
        if (links.isEmpty()) throw new IllegalArgumentException("Gambar wajib diisi");
        if (links.size() > maxImages)
            throw new IllegalArgumentException("Maksimal " + maxImages + " gambar per pengiriman");
    } else {
        if (links.isEmpty()) throw new IllegalArgumentException("File wajib diisi");
        if (links.size() > 1)
            throw new IllegalArgumentException("Tipe " + type.name() + " hanya mendukung 1 file");
    }
    return links.stream().map(storageService::extractPathFromPublicUrl).toList();  // throw → 400 (FR-7)
}
```

> **Perubahan perilaku kecil:** sekarang VIDEO/DOCUMENT/AUDIO/IMAGE tanpa media tidak ditolak di awal (gagal di provider → pesan generik). Setelah perubahan ditolak dengan pesan spesifik. Dinilai sebagai perbaikan.

### 6.5 `sendToProvider`

Ekstraksi dari blok `if/else` existing (`ChatService.java:67-91`), memakai `ctx.deviceId()` & `ctx.phoneNumber()`:

| Type | Call |
|---|---|
| TEXT | `client.sendMessage(deviceId, GoWaSendMessageRequest{message, phone, reply_message_id?})` — `repliedMessageId` → `chatMessageService.findById(...).getMessageId()` (existing) |
| IMAGE | `client.sendImage(deviceId, phone, caption, url)` |
| VIDEO | `client.sendVideo(deviceId, phone, caption, url)` |
| DOCUMENT | `client.sendFile(deviceId, phone, caption, url)` |
| AUDIO | `client.sendAudio(deviceId, phone, url)` |

Caption gambar ke-2 dst dikirim `""` (bukan `null`) agar `FormHttpMessageConverter` tidak menerima nilai null.

`assert response != null` existing dihapus (assertion nonaktif di produksi); diganti pengecekan eksplisit.

---

## 7. Perubahan Controller

`ChatController.sendMessage` (`ChatController.java:163-180`):

```java
var result = chatService.messageHandler(data, username);
if (result.getFailed() > 0) {
    rest.setSuccess(false);
    rest.setMessage(result.getSent() + " dari " + result.getTotal()
            + " gambar terkirim, sisanya gagal. Silahkan kirim ulang gambar yang gagal.");
} else {
    rest.setSuccess(true);
    rest.setMessage("Success");
}
rest.setData(result);
return ResponseEntity.ok(rest);
```

Blok `catch` existing tidak berubah → validasi & gagal total tetap `400` dengan `e.getMessage()`.

---

## 8. Konfigurasi

`application.properties` (opsional; default di `@Value`):

```properties
chat.multi-image.max=${CHAT_MULTI_IMAGE_MAX:10}
chat.multi-image.delay-ms=${CHAT_MULTI_IMAGE_DELAY_MS:0}
```

---

## 9. Traceability PRD → Implementasi

| PRD | Implementasi |
|---|---|
| FR-1, FR-2 | §4.1 `@JsonFormat(ACCEPT_SINGLE_VALUE_AS_ARRAY)` |
| FR-3 | §6.4 `cleanMediaLinks` |
| FR-4, FR-5, FR-6 | §6.4 |
| FR-7 | §6.4 — extract semua URL sebelum loop |
| FR-8 | §6.3 loop sequential |
| FR-9 | §6.3 `caption = i == 0 ? message : ""` |
| FR-10, FR-11 | §5 `saveSentChat` per item |
| FR-12 | §5 `updateConversationAfterSend` sekali setelah loop |
| FR-13 | §6.3 `repliedMessageId` hanya index 0; hanya dipakai TEXT (existing) |
| FR-14 | §3, §6.3 `break` + commit per item |
| FR-15 | §7 |
| FR-16 | 1 item → 1 call, 1 save, 1 update; event identik |
| FR-17 | `BotService` tidak diubah; §2.2 & §3.2 |
| NFR-1 | Tidak ada perubahan entity/repository/DTO read |
| NFR-2 | §8 `delay-ms` |

---

## 10. Error Handling

| Kondisi | Lokasi | HTTP | `message` |
|---|---|---|---|
| Conversation tidak ada | `loadContext` | 400 | `Conversation tidak ditemukan` |
| `messageType` null | `validateAndExtract` | 400 | `Tipe pesan wajib diisi` |
| IMAGE tanpa media | `validateAndExtract` | 400 | `Gambar wajib diisi` |
| IMAGE > max | `validateAndExtract` | 400 | `Maksimal 10 gambar per pengiriman` |
| Non-IMAGE > 1 file | `validateAndExtract` | 400 | `Tipe VIDEO hanya mendukung 1 file` |
| URL bukan bucket valid | `extractPathFromPublicUrl` | 400 | `URL tidak valid untuk bucket ini` |
| Provider gagal di item pertama | `messageHandler` | 400 | `Terjadi kesalahan saat mengirim pesan ke whatsapp, silahkan coba lagi.` (existing) |
| Provider gagal di item ke-k > 0 | controller | 200, `success=false` | `k dari n gambar terkirim, …` |
| DB gagal saat `saveSentChat` | exception keluar dari loop | 400 | pesan exception. Gambar sebelumnya sudah ter-commit; gambar yang baru terkirim ke WA tidak tersimpan (dicatat di log). Risiko diterima — sama dengan perilaku sekarang. |

Bot: exception dari `messageHandler` merambat ke `BotService` seperti sekarang.

---

## 11. Testing & Verifikasi

Tidak ada DB test (H2/Testcontainers) di project → test otomatis berupa **unit test Mockito** (`spring-boot-starter-test` sudah ada). Perilaku transaksi diverifikasi manual (§11.3).

### 11.1 `SendMessageDtoTest` (Jackson `ObjectMapper`)

| Case | Input | Expected |
|---|---|---|
| string | `"mediaLink":"a"` | `["a"]` |
| array | `"mediaLink":["a","b"]` | `["a","b"]` |
| null | `"mediaLink":null` | `null` |
| absen | — | `null` |
| kosong | `"mediaLink":[]` | `[]` |

### 11.2 `ChatServiceTest` (mock `WhatsappClientHelper`, `StorageService`, `ChatMessagePersister`, `ChatMessageService`)

| # | Case | Assert |
|---|---|---|
| 1 | IMAGE 3 URL, semua sukses | `sendImage` 3× berurutan; caption `[msg, "", ""]`; `saveSentChat` 3× dengan `pesan [msg, null, null]`; `updateConversationAfterSend` 1× dengan chat terakhir & `lastMessage = msg`; result `3/3/0` |
| 2 | IMAGE 1 URL (string tunggal) | 1× `sendImage`, 1× save, 1× update — setara perilaku lama |
| 3 | IMAGE 11 URL | `IllegalArgumentException`; `sendImage` 0× |
| 4 | URL index 1 invalid | exception dari `extractPathFromPublicUrl`; `sendImage` 0× |
| 5 | IMAGE `[]` / `null` / `["", null]` | `Gambar wajib diisi`; 0 call |
| 6 | VIDEO 2 URL | `Tipe VIDEO hanya mendukung 1 file` |
| 7 | Gagal (code ≠ SUCCESS) di index 1 dari 3 | `sendImage` 2×; save 1×; update 1×; result `3/1/2` |
| 8 | Exception provider di index 0 | `RuntimeException` pesan existing; save 0×; update 0× |
| 9 | TEXT dengan `repliedMessageId` | `sendMessage` dengan `reply_message_id`; `mediaLink` diabaikan |
| 10 | `delayMs > 0` | (opsional) tidak sleep sebelum item pertama |

### 11.3 Verifikasi manual (staging, device WA test)

1. Kirim 3 gambar dari dashboard → 3 gambar muncul satu per satu di chat room & di HP penerima, urutan sama, caption hanya di gambar pertama.
2. Reload chat → `GET /conversation/message` menampilkan 3 item IMAGE.
3. Simulasi gagal tengah (mis. URL ke-2 mengarah ke object yang sudah dihapus di MinIO) → gambar 1 tetap ada setelah reload; response `success=false`, `sent=1`.
4. Kirim single image dari FE lama (string) → normal.
5. Regression: TEXT, reply TEXT, VIDEO, DOCUMENT, AUDIO.
6. Bot: chat customer biasa (balasan bot muncul) & pesan konfirmasi order (`sendConfirmationMessage`) → `conversation.last_message` & `bot_quota` benar (memverifikasi T2 & T3).

---

## 12. Rencana Implementasi Bertahap

| Fase | Pekerjaan | Selesai bila |
|---|---|---|
| 1 | `SendMessageDto` (+anotasi), `SendMessageResult`, `ChatSendContext` | compile model |
| 2 | `ChatMessagePersister` (pindahkan kode save/publish/update) | compile |
| 3 | Refactor `messageHandler` (hapus `@Transactional`, validasi, loop, `sendToProvider`) | `mvnw.cmd package -DskipTests` sukses |
| 4 | `ChatController.sendMessage` | compile |
| 5 | Unit test §11.1–§11.2 | `mvnw.cmd test -Dtest=ChatServiceTest,SendMessageDtoTest` hijau |
| 6 | Update `docs/api-reference.md` §6.11, `docs/frontend/api-mapping.md` | — |
| 7 | Verifikasi manual §11.3 | semua skenario lulus |

Fase 1–5 di-commit bersama (working tree saat ini tidak compile).

---

## 13. Analisis Regresi

| Area | Risiko | Alasan |
|---|---|---|
| Kirim TEXT dari dashboard | Rendah | Jalur sama, hanya dipindah ke `sendToProvider` + persister. |
| Kirim media tunggal | Rendah | FR-16; anotasi Jackson menjaga format string. |
| Bot `handleBotReply` | Rendah | Tanpa tx luar → persister commit sendiri; `loadContext` mencegah lazy error. |
| Bot `sendConfirmationMessage` | Rendah | `REQUIRED` join tx luar → identik dengan sekarang. |
| WebSocket chat room | Rendah | Payload `ChatListDto` & `ConversationUpdatedData` identik. |
| Penggunaan koneksi DB | Membaik | HTTP provider tidak lagi di dalam tx. |
| Blast, Order, Template, Webhook masuk | Tidak ada | Tidak menyentuh `messageHandler`. |
