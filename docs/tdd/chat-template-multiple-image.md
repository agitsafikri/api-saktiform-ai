# TDD — Chat Template Multiple Image — Backend

| Field | Value |
|---|---|
| Feature name | Chat Template Multiple Image |
| Dokumen induk | [PRD — Chat Template Multiple Image](../prd/chat-template-multiple-image.md) (Status: *Approved*) |
| Component | `ChatTemplate` · `AddChatTemplateDto` · `DetailTemplate` · `ChatTemplateListDto` · `QuickChatResponse` · `ChatTemplateRepository` · `MessageTemplateService` · `ConversationService` · `StorageService` · `ChatService` · **baru:** `ChatTemplateMediaMapper`, `ChatTemplateSchemaInitializer` |
| Status | **Implemented** — Fase 1–5 selesai, unit test hijau (37/37: mapper 12, service 6, storage 4, ChatService 10, SendMessageDto 5). Fase 6 (verifikasi manual staging) belum dilakukan |
| Scope | Backend-only; FE di repo terpisah (PRD/TDD FE menyusul, PRD §12 Q9) |
| Last updated | 2026-10-15 |
| Target pembaca | Backend Developer, Reviewer, QA, Frontend |

> Semua keputusan PRD §12 **RESOLVED** mengikuti rekomendasi default: `jsonb` + dual-write (Q1), maks 10 (Q2), fallback `mediaLink` me-replace daftar (Q3), tanpa dedupe (Q4), quick chat mengembalikan `mediaLinks` (Q5), **copy bucket produk → bucket chat di `send-message` masuk scope** (Q6), blast tetap 1 gambar (Q7), bug blast bucket tiket terpisah (Q8). Snippet kode adalah **acuan desain**, bukan kode final.

---

## Daftar Isi

1. [Ruang Lingkup Teknis](#1-ruang-lingkup-teknis)
2. [Baseline Codebase](#2-baseline-codebase)
3. [Perubahan Data Model](#3-perubahan-data-model)
4. [Perubahan DTO](#4-perubahan-dto)
5. [Komponen Baru — `ChatTemplateMediaMapper`](#5-komponen-baru--chattemplatemediamapper)
6. [Perubahan `MessageTemplateService`](#6-perubahan-messagetemplateservice)
7. [Perubahan `ChatTemplateRepository` (list)](#7-perubahan-chattemplaterepository-list)
8. [Perubahan `ConversationService.getQuickChat`](#8-perubahan-conversationservicegetquickchat)
9. [Backfill — `ChatTemplateSchemaInitializer`](#9-backfill--chattemplateschemainitializer)
10. [Fase 2 — Copy Bucket Produk → Chat di `send-message`](#10-fase-2--copy-bucket-produk--chat-di-send-message)
11. [Konfigurasi](#11-konfigurasi)
12. [Error Handling](#12-error-handling)
13. [Traceability PRD → Implementasi](#13-traceability-prd--implementasi)
14. [Testing & Verifikasi](#14-testing--verifikasi)
15. [Rencana Implementasi Bertahap](#15-rencana-implementasi-bertahap)
16. [Analisis Regresi](#16-analisis-regresi)

---

## 1. Ruang Lingkup Teknis

**Masuk:**
- Penyimpanan 0..10 path gambar berurutan per template (`chat_template.media_links jsonb`) + dual-write `media_link` = gambar pertama.
- `POST /template` menerima `mediaLinks: string[]` (fallback `mediaLink`).
- `GET /template`, `GET /template/{id}`, `POST /conversation/quick-chat` mengembalikan `mediaLinks` (public URL bucket produk) + `mediaLink` (elemen pertama) untuk kompatibilitas.
- Backfill data lama saat startup.
- `POST /send-message`: URL bucket produk di-copy ke bucket chat sebelum path disimpan di `Chat.media` (perbaikan PRD §2.4-A).

**Tidak masuk:** blast multi-image, perbaikan dugaan bug bucket blast (PRD §2.4-B), cleanup file MinIO, bug kategori `QUICK_REPLY` vs `"Quick Reply"`.

---

## 2. Baseline Codebase

| File | Kondisi sekarang |
|---|---|
| `entity/ChatTemplate.java` | `String mediaLink` (`media_link text`). |
| `model/template/AddChatTemplateDto.java` | `id, idWorkspace, namaTemplate, content, mediaLink`. |
| `model/template/DetailTemplate.java` | `id, namaTemplate, content, mediaLink`. |
| `model/template/ChatTemplateListDto.java` | `id, namaTemplate, kategori, mediaLink`; dibangun via JPQL constructor expression. |
| `model/chat/QuickChatResponse.java` | `message` saja. Hanya di-instansiasi di `ConversationService.getQuickChat` (no-args). |
| `repository/ChatTemplateRepository.getListChatTemplate` | JPQL `SELECT new ...ChatTemplateListDto(ct.id, ct.namaTemplate, ct.category, ct.mediaLink)`; hanya dipakai `MessageTemplateService.getListTemplate`. |
| `service/MessageTemplateService` | Konstruktor `(ChatTemplateRepository, MessageConstructorHelper, StorageService)`; `upsertMessageTemplate` tanpa `@Transactional`, extract 1 path. |
| `service/chat/ConversationService.getQuickChat` | Mengembalikan teks saja. Sudah punya `StorageService` & `ChatTemplateRepository`. |
| `service/StorageService` | Bucket chat (`minio.bucket`, default `saktiform-media`) & produk (`minio.bucket.produk`, default `produk-media`); keduanya memakai path `media/...`. `copySameFolder` hanya copy di dalam bucket produk. `buildObjectName` (private) membentuk `media/<tahun>/<bulan>/<uuid><ext>`. |
| `service/chat/ChatService.messageHandler` | `mediaPaths = mediaUrls.stream().map(storageService::extractPathFromPublicUrl)` sebelum loop kirim; URL asli dikirim ke provider. |
| `service/blast/BlastCampaignService.create` | Membaca `tpl.getMediaLink()` → **tidak diubah**. |

Pola acuan:
- JSON column: `ProdukFormConfig.options` (`@JdbcTypeCode(SqlTypes.JSON) List<OptionDto>`).
- DDL tambahan idempotent saat startup: `LabelSchemaInitializer` (`@EventListener(ApplicationReadyEvent.class)` + `JdbcTemplate`, error di-log).

---

## 3. Perubahan Data Model

### 3.1 `entity/ChatTemplate.java` (ubah)

```java
@Column(name = "media_link", length = Integer.MAX_VALUE)
private String mediaLink;          // tetap: path gambar pertama (kompatibilitas blast/client lama)

/** Path relatif bucket produk, berurutan. null bila tidak ada gambar. */
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "media_links")
private List<String> mediaLinks;
```

- `ddl-auto=update` menambah `media_links jsonb NULL` (dialek PostgreSQL memetakan `SqlTypes.JSON` → `jsonb`).
- **Invarian:** setiap penulisan lewat service menjaga `mediaLink == (mediaLinks kosong ? null : mediaLinks.get(0))`.
- Kosong disimpan sebagai `null` (bukan `[]`) agar konsisten dengan backfill `WHERE media_links IS NULL`.

---

## 4. Perubahan DTO

| DTO | Perubahan |
|---|---|
| `AddChatTemplateDto` | + `List<String> mediaLinks`. `mediaLink` tetap. Tidak ada pemakai `@AllArgsConstructor` (cek `grep "new AddChatTemplateDto"` → kosong). |
| `DetailTemplate` | + `List<String> mediaLinks` (public URL). |
| `ChatTemplateListDto` | + `List<String> mediaLinks` (public URL). Tidak lagi dibangun JPQL (lihat §7). |
| `QuickChatResponse` | + `List<String> mediaLinks` (public URL, `[]` bila tidak ada). |

Semua DTO response mengembalikan `mediaLinks` sebagai list **non-null** (`[]` bila kosong) agar FE tidak perlu null-check; `mediaLink` tetap `null` bila kosong (perilaku lama).

---

## 5. Komponen Baru — `ChatTemplateMediaMapper`

`service/ChatTemplateMediaMapper.java` (`@Component`). Dipakai `MessageTemplateService` **dan** `ConversationService` → menghindari duplikasi dan dependensi silang antar service.

```java
@Component
public class ChatTemplateMediaMapper {
    private final StorageService storageService;

    @Value("${chat.template.max-images:10}")
    private int maxImages;

    /** Request → daftar path. Validasi seluruhnya sebelum return (tidak ada partial). */
    public List<String> toPaths(AddChatTemplateDto data) {
        List<String> raw = data.getMediaLinks() != null
                ? data.getMediaLinks()
                : (data.getMediaLink() != null ? List.of(data.getMediaLink()) : List.of());

        var urls = raw.stream().filter(u -> u != null && !u.isBlank()).toList();
        if (urls.size() > maxImages) {
            throw new IllegalArgumentException("Maksimal " + maxImages + " gambar per template");
        }
        return urls.stream().map(storageService::extractPathFromPublicUrl).toList();
    }

    /** Entity → public URL (prioritas media_links, fallback media_link untuk data belum ter-backfill). */
    public List<String> toPublicUrls(ChatTemplate t) {
        List<String> paths = t.getMediaLinks() != null && !t.getMediaLinks().isEmpty()
                ? t.getMediaLinks()
                : (t.getMediaLink() != null && !t.getMediaLink().isBlank() ? List.of(t.getMediaLink()) : List.of());
        return paths.stream().map(storageService::getProdukPublicUrl).toList();
    }
}
```

Catatan:
- `List.of(data.getMediaLink())` aman karena sudah dicek non-null; elemen `null` di `mediaLinks` difilter sebelum `.toList()`.
- FR-8 (tanpa dedupe): tidak ada `distinct()`.
- `extractPathFromPublicUrl` melempar `IllegalArgumentException("URL tidak valid untuk bucket ini")` → propagasi ke controller (400).

---

## 6. Perubahan `MessageTemplateService`

### 6.1 Dependensi

Konstruktor menjadi `(ChatTemplateRepository, MessageConstructorHelper, StorageService, ChatTemplateMediaMapper)`. Setelah refactor, `StorageService` tidak lagi dipakai langsung oleh service ini (semua konversi lewat mapper) → **hapus** dari konstruktor bila tidak ada pemakaian tersisa.

### 6.2 `upsertMessageTemplate`

```java
public void upsertMessageTemplate(AddChatTemplateDto data) {
    var paths = chatTemplateMediaMapper.toPaths(data);   // validasi DULU, sebelum entity disentuh

    ChatTemplate chatTemplate;
    if (data.getId() != null) {
        chatTemplate = chatTemplateRepository.findById(data.getId()).get();
    } else {
        chatTemplate = new ChatTemplate();
        chatTemplate.setCategory("QUICK_REPLY");
    }

    chatTemplate.setContent(data.getContent());
    chatTemplate.setNamaTemplate(data.getNamaTemplate());
    chatTemplate.setIdWorkspace(data.getIdWorkspace());
    chatTemplate.setMediaLinks(paths.isEmpty() ? null : new ArrayList<>(paths));
    chatTemplate.setMediaLink(paths.isEmpty() ? null : paths.get(0));

    chatTemplateRepository.save(chatTemplate);
}
```

- Validasi sebelum `findById` → bila gagal, entity managed (OSIV) tidak pernah dimodifikasi, sehingga tidak ada risiko ter-flush.
- `new ArrayList<>(paths)` — hindari menyimpan list immutable ke atribut entity (Hibernate dirty-check membandingkan nilai JSON; list mutable lebih aman untuk serialisasi/dirty-check).
- Update = replace penuh (FR-7, Q3).

### 6.3 `getDetail`

```java
var urls = chatTemplateMediaMapper.toPublicUrls(template);
detailTemplate.setMediaLinks(urls);
detailTemplate.setMediaLink(urls.isEmpty() ? null : urls.get(0));
```

### 6.4 `getListTemplate`

```java
var result = chatTemplateRepository.findListChatTemplate(idWorkspace, pageable, search);
return result.map(t -> {
    var urls = chatTemplateMediaMapper.toPublicUrls(t);
    return new ChatTemplateListDto(t.getId(), t.getNamaTemplate(), t.getCategory(),
            urls.isEmpty() ? null : urls.get(0), urls);
});
```

`Page.map` mempertahankan metadata paging (`totalPages`, dll.) sehingga shape response tidak berubah.

---

## 7. Perubahan `ChatTemplateRepository` (list)

Constructor expression JPQL untuk atribut JSON tidak dijamin didukung (PRD R-2) → ganti dengan query entity; filter dan sort identik.

```java
@Query("""
    SELECT ct FROM ChatTemplate ct
    WHERE ct.idWorkspace = :idWorkspace
      AND (:search IS NULL OR ct.namaTemplate LIKE CONCAT('%', :search, '%'))
    """)
Page<ChatTemplate> findListChatTemplate(@Param("idWorkspace") Long idWorkspace, Pageable pageable,
                                        @Param("search") String search);
```

- Method lama `getListChatTemplate` **dihapus** (satu-satunya pemakai adalah service).
- Tetap 1 query data + 1 query count (NFR-3). Mapper hanya membaca kolom skalar/JSON entity; relasi LAZY `waba`/`workspace` **tidak** disentuh → tanpa N+1.
- Sort `createdAt DESC` tetap dari `PageRequest` di service.

---

## 8. Perubahan `ConversationService.getQuickChat`

Inject `ChatTemplateMediaMapper` lewat konstruktor, lalu:

```java
QuickChatResponse response = new QuickChatResponse();
// ... logika message existing TIDAK berubah ...
response.setMediaLinks(chatTemplateMediaMapper.toPublicUrls(template));
return response;
```

- Validasi existing (`TEMPLATE_MESSAGE` tanpa order aktif → error) tetap berjalan sebelum media dibaca.
- Cek cycle DI: `ChatTemplateMediaMapper` hanya bergantung pada `StorageService` → aman.

---

## 9. Backfill — `ChatTemplateSchemaInitializer`

`configuration/ChatTemplateSchemaInitializer.java`, pola `LabelSchemaInitializer`:

```java
@EventListener(ApplicationReadyEvent.class)
public void backfillMediaLinks() {
    try {
        int n = jdbcTemplate.update("""
                UPDATE chat_template
                   SET media_links = jsonb_build_array(media_link)
                 WHERE media_links IS NULL
                   AND media_link IS NOT NULL
                   AND media_link <> ''
                """);
        log.info("Chat template schema: backfill media_links {} baris", n);
    } catch (Exception e) {
        log.error("Chat template schema: gagal backfill media_links — read tetap fallback ke media_link", e);
    }
}
```

- `ApplicationReadyEvent` dijamin setelah `ddl-auto` menambah kolom.
- Idempotent; aman dijalankan di banyak instance (PRD R-3).
- Bila gagal, read tetap benar karena fallback di `toPublicUrls` (FR-12).

---

## 10. Fase 2 — Copy Bucket Produk → Chat di `send-message`

### 10.1 Masalah

`Chat.media` menyimpan path tanpa informasi bucket, lalu read path chat (`ConversationService`) membangun URL dengan `getPublicUrl` (bucket chat). Gambar template (bucket produk) yang dikirim lewat chat jadi link rusak. Kedua bucket memakai prefix `media/` sehingga bucket tidak dapat ditebak dari path.

### 10.2 Keputusan

Saat `send-message` menerima URL **bucket produk**, backend menyalin objek ke **bucket chat** dan menyimpan path baru. Read path chat, WebSocket payload, dan skema `chat` **tidak berubah**. Riwayat chat juga tidak terpengaruh bila gambar template kemudian diganti.

### 10.3 `StorageService` (tambah)

```java
public boolean isProdukBucketUrl(String publicUrl) {
    return publicUrl != null
            && publicUrl.startsWith(normalize(publicBaseUrl) + "/" + bucketProduk + "/");
}

/** Path di bucket chat untuk URL publik: copy bila dari bucket produk, extract bila sudah bucket chat. */
public String toChatBucketPath(String publicUrl) {
    if (!isProdukBucketUrl(publicUrl)) {
        return extractPathFromPublicUrl(publicUrl);           // perilaku lama, termasuk error URL tidak valid
    }
    String sourcePath = extractPathFromPublicUrl(publicUrl);
    String destinationPath = buildObjectName(sourcePath);       // media/<tahun>/<bulan>/<uuid><ext>
    try {
        minioClient.copyObject(CopyObjectArgs.builder()
                .source(CopySource.builder().bucket(bucketProduk).object(sourcePath).build())
                .bucket(bucket)
                .object(destinationPath)
                .build());
    } catch (Exception e) {
        throw new RuntimeException("Gagal menyalin media template ke penyimpanan chat", e);
    }
    return destinationPath;
}
```

- `buildObjectName(sourcePath)` mengambil ekstensi dari path sumber (method existing, private — dipanggil dari dalam kelas yang sama).
- `copyObject` server-side MinIO: tanpa transfer byte lewat aplikasi; metadata (`Content-Type`) ikut tersalin secara default.
- Cek `isProdukBucketUrl` memakai `startsWith` prefix lengkap, bukan `contains`, agar tidak salah deteksi.

### 10.4 `ChatService.messageHandler` (ubah 1 baris)

```java
var mediaPaths = mediaUrls.stream().map(storageService::toChatBucketPath).toList();
```

- Tetap dijalankan **sebelum** mengirim apa pun → copy gagal = error 400, tidak ada pesan terkirim (selaras prinsip validasi-dulu PRD Chat Multiple Image).
- URL yang dikirim ke provider tetap URL asli (publik, dapat diunduh provider) — tidak berubah.
- Konsekuensi: bila pengiriman gagal di tengah, salinan untuk gambar yang belum terkirim menjadi file yatim di bucket chat (diterima; selaras non-goal cleanup).
- Berlaku juga untuk `BotService` (memanggil `messageHandler` yang sama) — tanpa perubahan di bot.

---

## 11. Konfigurasi

`application.properties`:

```properties
chat.template.max-images=${CHAT_TEMPLATE_MAX_IMAGES:10}
```

---

## 12. Error Handling

| Kondisi | Sumber | Pesan | HTTP |
|---|---|---|---|
| > maks gambar | `ChatTemplateMediaMapper.toPaths` | `Maksimal N gambar per template` | 400 (`ChatTemplateController` catch) |
| URL bukan bucket valid | `StorageService.extractPathFromPublicUrl` | `URL tidak valid untuk bucket ini` | 400 |
| Template tidak ditemukan (detail/update) | `findById().get()` existing | `No value present` (perilaku lama) | 400 |
| Copy MinIO gagal saat send | `StorageService.toChatBucketPath` | `Gagal menyalin media template ke penyimpanan chat` | 400 (`ChatController` catch) |
| Backfill gagal | `ChatTemplateSchemaInitializer` | log error, startup lanjut | — |

---

## 13. Traceability PRD → Implementasi

| PRD | Implementasi |
|---|---|
| FR-1, FR-2 | `AddChatTemplateDto.mediaLinks`; prioritas di `ChatTemplateMediaMapper.toPaths` |
| FR-3, FR-4, FR-5, FR-8 | `toPaths` (filter blank, cek maks, extract semua sebelum simpan, tanpa dedupe) |
| FR-6, FR-7 | `upsertMessageTemplate` (dual-write, replace penuh) |
| FR-9 | `getDetail` + `DetailTemplate.mediaLinks` |
| FR-10 | `findListChatTemplate` + `Page.map` → `ChatTemplateListDto.mediaLinks` |
| FR-11 | `getQuickChat` + `QuickChatResponse.mediaLinks` |
| FR-12, FR-13 | `toPublicUrls` fallback + `ChatTemplateSchemaInitializer` |
| FR-14 | `BlastCampaignService` tidak diubah (membaca `media_link` = gambar pertama) |
| FR-15, FR-16 | `StorageService.toChatBucketPath` + `ChatService` (§10) |
| NFR-3 | Query entity tanpa menyentuh relasi LAZY (§7) |
| NFR-5 | `chat.template.max-images` |

---

## 14. Testing & Verifikasi

### 14.1 `ChatTemplateMediaMapperTest` (unit, mock `StorageService`)

| # | Kasus | Ekspektasi |
|---|---|---|
| 1 | `mediaLinks` 3 URL | 3 path, urutan sama |
| 2 | `mediaLinks = null`, `mediaLink` ada | 1 path |
| 3 | Keduanya null | `[]` |
| 4 | `mediaLinks` diisi + `mediaLink` lain | hanya `mediaLinks` yang dipakai |
| 5 | `["a", "", null, "b"]` | `[pathA, pathB]` |
| 6 | 11 URL (maks 10) | `IllegalArgumentException("Maksimal 10 gambar per template")`, `extractPathFromPublicUrl` tidak dipanggil |
| 7 | URL ke-2 invalid | exception propagasi |
| 8 | Duplikat `["a","a"]` | 2 path |
| 9 | `toPublicUrls`: `mediaLinks` terisi | URL produk berurutan |
| 10 | `toPublicUrls`: hanya `mediaLink` (belum backfill) | 1 URL |
| 11 | `toPublicUrls`: kosong semua | `[]` |

### 14.2 `MessageTemplateServiceTest` (mock repository, mapper)

| # | Kasus | Ekspektasi |
|---|---|---|
| 1 | Create 3 gambar | entity tersimpan `mediaLinks` 3 path, `mediaLink` = path pertama, kategori `QUICK_REPLY` |
| 2 | Update dengan `mediaLinks: []` | `mediaLinks = null`, `mediaLink = null` |
| 3 | Mapper melempar error | `findById` & `save` tidak dipanggil |
| 4 | `getDetail` | `mediaLinks` & `mediaLink` sesuai mapper |
| 5 | `getListTemplate` | `Page.map` mempertahankan `totalElements`; item berisi `kategori` & `mediaLinks` |

### 14.3 `StorageServiceTest` (mock `MinioClient`, set field via `ReflectionTestUtils`)

| # | Kasus | Ekspektasi |
|---|---|---|
| 1 | URL bucket chat | return path hasil extract; `copyObject` **tidak** dipanggil |
| 2 | URL bucket produk | `copyObject` dipanggil (source bucket produk → dest bucket chat); return path `media/<tahun>/<bulan>/<uuid>.webp` |
| 3 | URL bukan bucket valid | `IllegalArgumentException("URL tidak valid untuk bucket ini")` |
| 4 | `copyObject` melempar | `RuntimeException("Gagal menyalin media template ke penyimpanan chat")` |

### 14.4 `ChatServiceTest` (update existing)

- Ganti stub `extractPathFromPublicUrl(...)` → `toChatBucketPath(...)` (baris ~58 dan ~147).
- Tambah kasus: `toChatBucketPath` melempar pada URL ke-2 → tidak ada call ke provider, tidak ada `saveSentChat`.

### 14.5 Verifikasi manual (staging)

1. Startup: log backfill muncul; template lama punya `media_links = ["<path>"]`.
2. Buat template 3 gambar via Swagger/FE → cek DB (`media_link` = elemen pertama) dan `GET /template`, `GET /template/{id}`.
3. Edit hanya dengan `mediaLink` (simulasi FE lama) → daftar menjadi 1 (perilaku Q3 disetujui).
4. `POST /conversation/quick-chat` → `mediaLinks` 3 URL.
5. Kirim 3 URL tersebut via `/send-message` (`IMAGE`) → gambar sampai ke WA, riwayat chat menampilkan gambar (URL bucket chat), objek baru ada di bucket chat.
6. Ganti gambar template → riwayat chat langkah 5 tetap tampil.
7. Buat campaign blast dari template 3 gambar → `blast_campaign.media_link` = path pertama. Sekaligus verifikasi dugaan bug PRD §2.4-B (catat hasil di tiket terpisah).

---

## 15. Rencana Implementasi Bertahap

| Fase | Pekerjaan | Bisa rilis sendiri? |
|---|---|---|
| 1 | Entity `mediaLinks`, `ChatTemplateSchemaInitializer`, property config | Ya (aditif) |
| 2 | `ChatTemplateMediaMapper` + DTO + `MessageTemplateService` + repository list + test 14.1/14.2 | Ya |
| 3 | `QuickChatResponse.mediaLinks` + `ConversationService` | Ya |
| 4 | `StorageService.toChatBucketPath` + `ChatService` + test 14.3/14.4 | Ya |
| 5 | Update `docs/api-reference.md` & `docs/frontend/api-mapping.md` (`/template`, `/template/{id}`, `/conversation/quick-chat`) | — |
| 6 | Verifikasi staging §14.5 | — |

Build: `mvnw.cmd package -DskipTests`; test: `mvnw.cmd test -Dtest=ChatTemplateMediaMapperTest,MessageTemplateServiceTest,StorageServiceTest,ChatServiceTest`.

---

## 16. Analisis Regresi

| Area | Risiko | Alasan aman / mitigasi |
|---|---|---|
| FE lama (template form) | Rendah | Masih kirim/baca `mediaLink`; response tetap memuat `mediaLink`. Risiko terpotong saat edit (PRD R-1) diterima. |
| FE lama (quick chat) | Tidak ada | Field `mediaLinks` tambahan diabaikan. |
| Blast | Tidak ada | Tetap baca `media_link` yang di-dual-write. |
| Order follow-up (`getFollowUpText`) | Tidak ada | Hanya membaca `content`. |
| Seeder `WorkspaceService` | Tidak ada | Template sistem tanpa media → kedua kolom `null`. |
| List template | Rendah | Query berubah dari DTO projection ke entity; filter/sort/paging identik; verifikasi `totalPages`. |
| `send-message` URL bucket chat | Tidak ada | `toChatBucketPath` jatuh ke `extractPathFromPublicUrl` yang sama. |
| `send-message` URL bucket produk | Positif | Sebelumnya link rusak di riwayat; kini objek disalin. Tambahan latensi 1 copy server-side per gambar. |
| Bot (`BotService` → `messageHandler`) | Rendah | Bot mengirim URL yang sama seperti sebelumnya; bila bucket produk, kini ikut disalin. |
