# TDD — Blast Chat (Backend)

| Field | Value |
|---|---|
| Feature name | Blast Chat |
| Dokumen induk | [PRD — Blast Chat](../prd/blast-chat.md) (Status: *Ready for TDD*) |
| Component | Modul baru `com.saktiform.api` package `blast` (entity, repository, model, service, worker, controller) |
| Status | Draft for Implementation |
| Scope | Per-workspace; backend-only |
| Last updated | 2026-07-01 |
| Target pembaca | Backend Developer (acuan implementasi langsung), Reviewer, QA |

> Technical Design Document ini menerjemahkan PRD Blast Chat menjadi desain teknis konkret yang **selaras dengan konvensi codebase Saktiform existing**. Seluruh keputusan Open Question pada PRD sudah RESOLVED dan diasumsikan final. Dokumen ini menyertakan desain kelas, anotasi JPA, query native, kontrak service, dan rencana implementasi bertahap. Snippet kode bersifat **acuan desain** (skeleton), bukan kode final yang harus disalin verbatim.

---

## Daftar Isi

1. [Tujuan & Ruang Lingkup Teknis](#1-tujuan--ruang-lingkup-teknis)
2. [Arsitektur Modul](#2-arsitektur-modul)
3. [Konvensi yang Diwarisi dari Codebase](#3-konvensi-yang-diwarisi-dari-codebase)
4. [Strategi Skema & Migrasi DB](#4-strategi-skema--migrasi-db)
5. [Entity (JPA)](#5-entity-jpa)
6. [Enum](#6-enum)
7. [Repository](#7-repository)
8. [Model / DTO](#8-model--dto)
9. [Service Layer](#9-service-layer)
10. [Placeholder Engine](#10-placeholder-engine)
11. [Background Worker](#11-background-worker)
12. [Integrasi Conversation (Reuse & Refactor)](#12-integrasi-conversation-reuse--refactor)
13. [Controller & REST](#13-controller--rest)
14. [Security](#14-security)
15. [Konfigurasi & Properties](#15-konfigurasi--properties)
16. [Konkurensi, Idempotency & Transaksi](#16-konkurensi-idempotency--transaksi)
17. [Error Handling](#17-error-handling)
18. [Observability & Logging](#18-observability--logging)
19. [Testing Strategy](#19-testing-strategy)
20. [Rencana Implementasi Bertahap](#20-rencana-implementasi-bertahap)
21. [Item Verifikasi Provider](#21-item-verifikasi-provider)
22. [Appendix — DDL Migration & Skeleton](#22-appendix--ddl-migration--skeleton)

---

## 1. Tujuan & Ruang Lingkup Teknis

Mengimplementasikan fitur Blast Chat sesuai PRD: upload Excel → staging & analisis kontak → create campaign → start → background worker mengirim WhatsApp massal (batch, delay, retry, idempotent, multi-worker) → menempel ke Conversation → monitoring & history.

**Prinsip desain teknis:**

- **Reuse maksimal** komponen existing (`PhoneNumberUtil`, `WhatsappClientHelper`, `ConversationService`, `StorageService`, `AppConfigService`, event system) — tidak membuat jalur baru.
- **Database Queue (PostgreSQL `FOR UPDATE SKIP LOCKED`)** sebagai mekanisme antrian — tidak menambah broker.
- **Additive only** terhadap skema existing; satu-satunya perubahan tabel existing adalah penambahan unique index pada `contact` (OQ-20).
- Mengikuti pola layer existing: `controller → service → repository`, response `RestResponse`, isolasi tenant via `id_workspace`.

Out of scope (sesuai PRD §4.3): engine bot/RAG, frontend, pengelolaan device WA.

---

## 2. Arsitektur Modul

### 2.1 Struktur Package

```
com.saktiform.api/
├── entity/
│   ├── BlastImport.java
│   ├── BlastImportContact.java
│   ├── BlastCampaign.java
│   ├── BlastMessage.java
│   ├── BlastJob.java
│   └── BlastMessageEvent.java
├── repository/
│   ├── BlastImportRepository.java
│   ├── BlastImportContactRepository.java
│   ├── BlastCampaignRepository.java
│   ├── BlastMessageRepository.java
│   ├── BlastJobRepository.java
│   └── BlastMessageEventRepository.java
├── model/blast/
│   ├── request/   (UploadResultDto, CreateCampaignRequest, RetryRequest, …)
│   └── response/  (ImportSummaryDto, CampaignListDto, CampaignProgressDto, MessageListDto, …)
├── service/blast/
│   ├── BlastImportService.java
│   ├── BlastAnalysisService.java
│   ├── BlastImportAnalyzeListener.java   (Fase 8: listener @Async analisis, tx bersih cross-bean)
│   ├── BlastCampaignService.java
│   ├── BlastCampaignStartListener.java   (Fase 8: listener @Async generate, tx bersih cross-bean)
│   ├── BlastFailSafeService.java         (Fase 8: markFailed REQUIRES_NEW — anti state nyangkut)
│   ├── BlastRetryService.java
│   ├── BlastStatusService.java
│   ├── BlastSenderService.java
│   ├── BlastSendTxService.java   (transaksi singkat per job: beginSend/completeSuccess/completeFailure)
│   ├── BlastConversationService.java  (attach Conversation + auto-assign, REQUIRES_NEW)
│   ├── BlastReportService.java   (export Excel per campaign — FR-14)
│   ├── ExcelParser.java
│   ├── queue/
│   │   ├── QueuePort.java
│   │   └── DbQueueAdapter.java
│   ├── placeholder/
│   │   ├── PlaceholderEngine.java
│   │   ├── PlaceholderResolver.java
│   │   ├── BlastMessageContext.java
│   │   └── resolver/ (NameResolver, PhoneResolver, …)
│   └── worker/
│       ├── BlastQueuePoller.java
│       ├── BlastWorkerExecutor.java
│       ├── BlastJobReaper.java
│       └── BlastCounterReconciler.java
├── controller/
│   ├── BlastImportController.java
│   ├── BlastCampaignController.java
│   └── BlastWebhookController.java
└── util/
    └── BlastPhoneMask.java   (Fase 8: masking nomor untuk log — PII)

> **Catatan implementasi (deviasi TDD awal):** `ChatService.messageHandler` **tidak** direfactor demi menjaga fitur chat live. Logika record-outbound diimplementasi ulang di `BlastConversationService.recordOutboundChat(...)` (meniru semantik & event yang sama). Penyatuan dengan `ChatService` = follow-up rendah-risiko.
```

### 2.2 Alur Komponen (ringkas)

```
BlastImportController → BlastImportService → ExcelParser(POI) → BlastImportContactRepository (staging)
                                           → BlastAnalysisService (@Async, klasifikasi) 
BlastCampaignController → BlastCampaignService (create DRAFT / start QUEUED) 
                        → BlastCampaignService.generateRecipientsAndQueue (@Async)
[Scheduler] BlastQueuePoller (@Scheduled) → claim job (SKIP LOCKED) 
          → BlastWorkerExecutor (@Async) → BlastSenderService 
              → PlaceholderEngine.render → WhatsappClientHelper.sendImage/sendMessage
              → ChatMessageService.recordOutboundChat (find-or-create contact+conversation, attach, auto-assign)
[Scheduler] BlastJobReaper (@Scheduled) → reclaim lease expired
[Scheduler] BlastCounterReconciler (@Scheduled) → koreksi drift counter
BlastWebhookController (public) → BlastStatusService → update delivery status
WhatsappMessageHandler (existing) → hook reply detection → BlastStatusService.markReplied
```

---

## 3. Konvensi yang Diwarisi dari Codebase

Acuan diambil dari `Chat`, `Contact`, `Conversation`, `ChatTemplate`, `OrderRepository`, `ConversationRepository`, `ChatService`.

| Aspek | Konvensi | Diterapkan di Blast |
|---|---|---|
| ID UUID | `@GeneratedValue(strategy = GenerationType.UUID)` | `blast_campaign` |
| ID Long | `@GeneratedValue(strategy = GenerationType.IDENTITY)` | `blast_import`, `blast_import_contact`, `blast_message`, `blast_job`, `blast_message_event` |
| Anotasi entity | `@Getter @Setter @Entity @Table(name="...")` (Lombok) | semua |
| Kolom | `@Column(name="snake_case")`; teks bebas pakai `length = Integer.MAX_VALUE` | semua |
| Audit | `Instant createdAt`/`updatedAt`, **di-set manual di service** (tidak ada `@PrePersist`) | semua |
| Soft delete | `Boolean isDeleted` (opsional) | tidak dipakai (campaign punya state machine sendiri) |
| FK | dual-field: relasi `@ManyToOne/@OneToOne(insertable=false, updatable=false)` + kolom `@Column` eksplisit | seperlunya (audit), mayoritas snapshot |
| Optimistic lock | `@Version` (didukung JPA) | `blast_campaign`, `blast_import` |
| Response | `RestResponse(success, message, data)`; error `ErrorResponse(code, message)` + `ErrorDto(field, message)` | semua controller |
| DTO request | `@Getter @Setter @NoArgsConstructor @AllArgsConstructor` + JSR-303 (`@NotNull`,`@NotBlank`) | request model |
| DTO response | constructor-projection (JPQL `SELECT new`) **atau** interface projection (native) | list/progress |
| Repository | `JpaRepository<T,ID>`; derived query; `@Query(nativeQuery=true)` + `countQuery` + `@Param`; `Pageable` | semua |
| Native filter | workspace via `WHERE x.id_workspace = :idWorkspace`; `ILIKE` case-insensitive; `(:p IS NULL OR …)` | list/search |
| Timezone | format `Asia/Jakarta`, pola `yyyy-MM-dd HH:mm` di layer DTO | response timestamp |
| Async/event | `@Async` + `@TransactionalEventListener(phase = AFTER_COMMIT)` (lihat `OrderEventListener`) | analisis, generate, event |
| Send WA | `client.sendMessage(waba.getId().toString(), GoWaSendMessageRequest)` / `client.sendImage(...)`; cek `response.getCode().equals("SUCCESS")`, ambil `response.getResults().getMessage_id()` | sender |
| Config | `AppConfigService.getConfig("key")` → String, parse manual | default worker config |

---

## 4. Strategi Skema & Migrasi DB

**Temuan penting:** codebase memakai `spring.jpa.hibernate.ddl-auto=update` **dan** Flyway aktif (`src/main/resources/db/migration/`, contoh `V1__add_is_disabled_to_province.sql`). Keduanya berjalan saat startup.

> **⚠️ Koreksi ordering (ditemukan saat implementasi):** Flyway berjalan **sebelum** Hibernate `ddl-auto` pada startup (EntityManagerFactory depends-on Flyway). Maka migrasi Flyway yang menyentuh tabel `blast_*` akan **gagal** (tabel belum dibuat Hibernate) dan membuat aplikasi tidak bisa boot. Karena setiap startup Flyway selalu lebih dulu, partial index `blast_job` **tidak akan pernah** bisa dibuat via Flyway. Karena itu strategi index dikoreksi seperti di bawah.

**Keputusan TDD (dikoreksi):**

1. **Pembuatan tabel `blast_*`** mengikuti pola codebase: **dibuat oleh Hibernate `ddl-auto=update`** dari anotasi entity. Tidak perlu DDL `CREATE TABLE` manual.
2. **Index biasa & unique constraint** `blast_*` dideklarasikan via `@Table(indexes=…, uniqueConstraints=…)` pada entity → dibuat Hibernate. Ini mencakup `uq_message_campaign_phone (campaign_id, phone)` & `uq_job_message_attempt (message_id, attempt)`.
3. **Partial index** klaim job (`… WHERE status='READY'`) **tidak** bisa dihasilkan Hibernate **maupun** Flyway (ordering). Dibuat oleh **`BlastSchemaInitializer`** — sebuah `@Component` yang mendengarkan `ApplicationReadyEvent` (dipastikan berjalan **setelah** Hibernate membuat tabel) dan mengeksekusi `CREATE INDEX IF NOT EXISTS idx_job_claim … WHERE status='READY'` secara idempotent (Fase 1/4). Pola "post-startup init" konsisten dengan `InitializerSeeder` existing.
4. **Unique index `contact (id_workspace, phone_number)`** (OQ-20) = perubahan tabel **existing** (`contact` sudah ada saat Flyway jalan, jadi ordering aman). **Namun** butuh **dedup data existing + reassign FK** lebih dulu; jika ada duplikat, `CREATE UNIQUE INDEX` gagal → app tidak boot. Karena destruktif & bergantung data produksi, **JANGAN diterapkan otomatis**: jalankan sebagai langkah migrasi terkonfirmasi (Flyway `V2__contact_unique_phone.sql`) setelah DBA memverifikasi/membersihkan duplikat (§22.3). **Di-hold sampai dikonfirmasi.**

> Catatan: `ddl-auto=update` tidak menghapus kolom/index → semua additive aman. Skrip `CREATE INDEX IF NOT EXISTS` idempotent.

---

## 5. Entity (JPA)

> Konvensi tipe: kolom teks bebas (`name`, `message_content`, `last_error`, `rendered_message`, `raw_*`) pakai `length = Integer.MAX_VALUE` (→ PostgreSQL `text`) selaras codebase. Kolom enum-string/identitas pendek (`status`, `category`, `phone`, `dedup_key`) pakai `@Column(length = N)`. `Instant` untuk timestamp, di-set di service.

### 5.1 `BlastImport`

```java
@Getter @Setter @Entity
@Table(name = "blast_import",
       indexes = @Index(name = "idx_blast_import_ws", columnList = "id_workspace, created_at"))
public class BlastImport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "id_workspace", nullable = false)
    private Long idWorkspace;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "file_name", length = Integer.MAX_VALUE)
    private String fileName;

    @Column(name = "file_path", length = Integer.MAX_VALUE)
    private String filePath;          // path MinIO (opsional)

    @Column(name = "status", length = 32)
    private String status;            // ImportStatus enum name

    @Column(name = "total_upload")    private Integer totalUpload = 0;
    @Column(name = "total_valid")     private Integer totalValid = 0;
    @Column(name = "total_invalid")   private Integer totalInvalid = 0;
    @Column(name = "total_duplicate") private Integer totalDuplicate = 0;
    @Column(name = "total_existing")  private Integer totalExisting = 0;
    @Column(name = "total_new")       private Integer totalNew = 0;

    @Version @Column(name = "version") private Long version;   // cegah race create-campaign (BR-22)

    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
}
```

### 5.2 `BlastImportContact` (staging)

```java
@Getter @Setter @Entity
@Table(name = "blast_import_contact",
       indexes = {
           @Index(name = "idx_bic_import_category", columnList = "import_id, category"),
           @Index(name = "idx_bic_ws_phone", columnList = "id_workspace, normalized_phone")
       })
public class BlastImportContact {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "import_id", nullable = false)  private Long importId;
    @Column(name = "id_workspace", nullable = false) private Long idWorkspace;
    @Column(name = "row_number")   private Integer rowNumber;
    @Column(name = "raw_name", length = Integer.MAX_VALUE)  private String rawName;
    @Column(name = "raw_phone", length = 64)                private String rawPhone;
    @Column(name = "normalized_phone", length = 20)         private String normalizedPhone;
    @Column(name = "category", length = 16)                 private String category;       // ContactCategory
    @Column(name = "invalid_reason", length = 128)          private String invalidReason;
    @Column(name = "contact_id")   private Long contactId;  // jika EXISTING (audit)
    @Column(name = "created_at")   private Instant createdAt;
}
```

### 5.3 `BlastCampaign`

```java
@Getter @Setter @Entity
@Table(name = "blast_campaign",
       indexes = {
           @Index(name = "idx_campaign_ws_status", columnList = "id_workspace, status, created_at"),
           @Index(name = "idx_campaign_ws_name", columnList = "id_workspace, name")
       })
public class BlastCampaign {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "id_workspace", nullable = false) private Long idWorkspace;
    @Column(name = "import_id")     private Long importId;     // 1:1 (BR-22), nullable untuk future segment
    @Column(name = "created_by")    private Long createdBy;    // wajib non-null untuk campaign yang mengirim (OQ-21)
    @Column(name = "name", length = 150, nullable = false) private String name;
    @Column(name = "status", length = 16, nullable = false) private String status;          // CampaignStatus
    @Column(name = "message_source", length = 16) private String messageSource;             // TEMPLATE|CUSTOM
    @Column(name = "source_template_id")          private UUID sourceTemplateId;            // audit ref
    @Column(name = "message_content", length = Integer.MAX_VALUE) private String messageContent; // snapshot
    @Column(name = "media_link", length = Integer.MAX_VALUE)      private String mediaLink;      // snapshot path
    @Column(name = "target_type", length = 16)    private String targetType;                // TargetType
    @Column(name = "device_id", length = 64)      private String deviceId;                  // default WABA workspace (OQ-4)

    @Column(name = "batch_size")   private Integer batchSize;    // override; null = global default
    @Column(name = "delay_ms")     private Integer delayMs;
    @Column(name = "max_attempts") private Integer maxAttempts;
    @Column(name = "scheduled_at") private Instant scheduledAt;  // future

    @Column(name = "total_recipient") private Integer totalRecipient = 0;
    @Column(name = "count_waiting")   private Integer countWaiting = 0;
    @Column(name = "count_sending")   private Integer countSending = 0;
    @Column(name = "count_sent")      private Integer countSent = 0;
    @Column(name = "count_failed")    private Integer countFailed = 0;
    @Column(name = "count_replied")   private Integer countReplied = 0;
    @Column(name = "count_skipped")   private Integer countSkipped = 0;

    @Version @Column(name = "version") private Long version;     // cegah double-start (BR-13)
    @Column(name = "started_at")  private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "created_at")  private Instant createdAt;
    @Column(name = "updated_at")  private Instant updatedAt;
}
```

### 5.4 `BlastMessage`

```java
@Getter @Setter @Entity
@Table(name = "blast_message",
       uniqueConstraints = @UniqueConstraint(name = "uq_message_campaign_phone",
                                             columnNames = {"campaign_id", "phone"}),
       indexes = {
           @Index(name = "idx_message_campaign_status", columnList = "campaign_id, status"),
           @Index(name = "idx_message_provider_msgid", columnList = "provider_message_id"),
           @Index(name = "idx_message_ws_phone", columnList = "id_workspace, phone")
       })
public class BlastMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_workspace", nullable = false) private Long idWorkspace;
    @Column(name = "campaign_id", nullable = false)  private UUID campaignId;
    @Column(name = "contact_id")       private Long contactId;        // diisi saat find-or-create di worker
    @Column(name = "conversation_id")  private UUID conversationId;   // attach on send (FR-12.8)
    @Column(name = "chat_id")          private UUID chatId;
    @Column(name = "phone", length = 20, nullable = false)  private String phone;   // snapshot
    @Column(name = "name", length = Integer.MAX_VALUE)      private String name;    // snapshot
    @Column(name = "status", length = 16, nullable = false) private String status; // MessageStatus
    @Column(name = "retry_count")      private Integer retryCount = 0;
    @Column(name = "rendered_message", length = Integer.MAX_VALUE) private String renderedMessage;
    @Column(name = "provider_message_id", length = 128) private String providerMessageId;
    @Column(name = "device_id", length = 64) private String deviceId;
    @Column(name = "last_error", length = 512) private String lastError;

    @Column(name = "waiting_at")   private Instant waitingAt;
    @Column(name = "sending_at")   private Instant sendingAt;
    @Column(name = "sent_at")      private Instant sentAt;
    @Column(name = "delivered_at") private Instant deliveredAt;
    @Column(name = "read_at")      private Instant readAt;
    @Column(name = "replied_at")   private Instant repliedAt;   // = first_reply_created_at di Report
    @Column(name = "failed_at")    private Instant failedAt;
    @Column(name = "skipped_at")   private Instant skippedAt;

    // First reply (balasan pertama user) — disimpan denormalized untuk Report (FR-13, BR-23)
    @Column(name = "first_reply_chat_id")    private UUID firstReplyChatId;       // FK chat(id)
    @Column(name = "first_reply_message", length = Integer.MAX_VALUE) private String firstReplyMessage;
    @Column(name = "first_reply_media_type", length = 16)  private String firstReplyMediaType;
    @Column(name = "first_reply_media_link", length = Integer.MAX_VALUE) private String firstReplyMediaLink;

    @Column(name = "created_at")   private Instant createdAt;
    @Column(name = "updated_at")   private Instant updatedAt;
}
```

> `is_replied` & `first_reply_sent_by`/`first_reply_sent_by_name` di Report **diturunkan** (derived): `is_replied = repliedAt != null`; `first_reply_sent_by = contactId`; `first_reply_sent_by_name = name`. Tidak perlu kolom tambahan.

### 5.5 `BlastJob`

```java
@Getter @Setter @Entity
@Table(name = "blast_job",
       uniqueConstraints = @UniqueConstraint(name = "uq_job_message_attempt",
                                             columnNames = {"message_id", "attempt"}),
       indexes = {
           @Index(name = "idx_job_campaign", columnList = "campaign_id, status"),
           @Index(name = "idx_job_lease", columnList = "status, locked_until")
       })
public class BlastJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_workspace", nullable = false) private Long idWorkspace;
    @Column(name = "campaign_id", nullable = false)  private UUID campaignId;
    @Column(name = "message_id", nullable = false)   private Long messageId;
    @Column(name = "status", length = 16, nullable = false) private String status;   // JobStatus
    @Column(name = "attempt", nullable = false)      private Integer attempt;
    @Column(name = "max_attempts", nullable = false) private Integer maxAttempts;
    @Column(name = "priority")        private Short priority = 0;
    @Column(name = "dedup_key", length = 128, nullable = false) private String dedupKey;
    @Column(name = "available_at", nullable = false)  private Instant availableAt;
    @Column(name = "locked_until")    private Instant lockedUntil;
    @Column(name = "locked_by", length = 64) private String lockedBy;
    @Column(name = "last_error", length = 512) private String lastError;
    @Column(name = "created_at")  private Instant createdAt;
    @Column(name = "updated_at")  private Instant updatedAt;
}
```

> Partial index `idx_job_claim (status, available_at, priority DESC, id) WHERE status='READY'` dibuat via Flyway (§22.2) karena Hibernate tidak menghasilkan partial index.

### 5.6 `BlastMessageEvent` (append-only)

```java
@Getter @Setter @Entity
@Table(name = "blast_message_event",
       indexes = @Index(name = "idx_event_message", columnList = "message_id, created_at"))
public class BlastMessageEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false) private Long messageId;
    @Column(name = "id_workspace", nullable = false) private Long idWorkspace;
    @Column(name = "from_status", length = 16) private String fromStatus;
    @Column(name = "to_status", length = 16, nullable = false) private String toStatus;
    @Column(name = "source", length = 32, nullable = false) private String source;   // WORKER|WEBHOOK|REPLY|USER|SYSTEM
    @Column(name = "attempt")  private Integer attempt;
    @Column(name = "detail", length = 512) private String detail;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
```

> `blast_audit_log` (PRD §10.7) bersifat opsional; jika diimplementasi, ikuti pola yang sama. MVP boleh menunda dan mengandalkan `blast_message_event` + log aplikasi.

---

## 6. Enum

Disimpan sebagai **String** di DB (kolom `varchar`), divalidasi di service sebelum dipakai pada native query (cegah SQL injection lewat status). Ditempatkan di `model/blast/enums/`.

```java
public enum CampaignStatus { DRAFT, QUEUED, RUNNING, PAUSED, FINISHED, CANCELLED, FAILED }
public enum MessageStatus  { WAITING, SENDING, SENT, DELIVERED, READ, REPLIED, FAILED, SKIPPED }
public enum JobStatus      { READY, CLAIMED, PROCESSING, DONE, RETRYING, DEAD, CANCELLED }
public enum ImportStatus   { UPLOADED, ANALYZING, ANALYZED, CONSUMED, FAILED }
public enum ContactCategory{ EXISTING, NEW, INVALID, DUPLICATE }
public enum TargetType      { ALL_VALID, EXISTING_ONLY, NEW_ONLY }
public enum MessageSource   { TEMPLATE, CUSTOM }
```

**Status rank** (untuk webhook out-of-order, BR-14) didefinisikan sebagai map statis di `BlastStatusService`:
`WAITING=0, SENDING=1, SENT=2, DELIVERED=3, READ=4, REPLIED=5`.

---

## 7. Repository

Semua extends `JpaRepository`. Derived query untuk lookup sederhana; native query (`nativeQuery=true` + `countQuery`) untuk list/claim/batch.

### 7.1 BlastImportRepository / BlastImportContactRepository

```java
public interface BlastImportRepository extends JpaRepository<BlastImport, Long> {
    Optional<BlastImport> findByIdAndIdWorkspace(Long id, Long idWorkspace);
    Page<BlastImport> findByIdWorkspaceOrderByCreatedAtDesc(Long idWorkspace, Pageable pageable);
}

public interface BlastImportContactRepository extends JpaRepository<BlastImportContact, Long> {
    Page<BlastImportContact> findByImportIdAndCategory(Long importId, String category, Pageable pageable);
    long countByImportIdAndCategoryIn(Long importId, Collection<String> categories);

    // Analisis: tandai EXISTING via JOIN ke contact (set-based, scoped workspace)
    @Modifying
    @Query(value = """
        UPDATE blast_import_contact bic
           SET category = 'EXISTING', contact_id = c.id
          FROM contact c
         WHERE bic.import_id = :importId
           AND bic.id_workspace = :idWorkspace
           AND bic.category = 'NEW'
           AND c.id_workspace = bic.id_workspace
           AND c.phone_number = bic.normalized_phone
        """, nativeQuery = true)
    int markExistingByWorkspace(@Param("importId") Long importId, @Param("idWorkspace") Long idWorkspace);
}
```

### 7.2 BlastCampaignRepository

```java
public interface BlastCampaignRepository extends JpaRepository<BlastCampaign, UUID> {
    Optional<BlastCampaign> findByIdAndIdWorkspace(UUID id, Long idWorkspace);

    @Query(value = """
        SELECT bc.id AS id, bc.name AS name, bc.status AS status,
               bc.total_recipient AS totalRecipient, bc.count_sent AS countSent,
               bc.count_failed AS countFailed, bc.count_replied AS countReplied,
               bc.created_at AS createdAtRaw
          FROM blast_campaign bc
         WHERE bc.id_workspace = :idWorkspace
           AND (:status IS NULL OR bc.status = :status)
           AND (:keyword IS NULL OR bc.name ILIKE CONCAT('%', :keyword, '%'))
         ORDER BY bc.created_at DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM blast_campaign bc
         WHERE bc.id_workspace = :idWorkspace
           AND (:status IS NULL OR bc.status = :status)
           AND (:keyword IS NULL OR bc.name ILIKE CONCAT('%', :keyword, '%'))
        """, nativeQuery = true)
    Page<CampaignListProjection> search(@Param("idWorkspace") Long idWorkspace,
                                        @Param("status") String status,
                                        @Param("keyword") String keyword,
                                        Pageable pageable);

    // Atomic counter updates (increment relatif — cegah lost update, §9.4 PRD)
    @Modifying @Query(value = """
        UPDATE blast_campaign
           SET count_waiting = count_waiting - 1, count_sending = count_sending + 1, updated_at = now()
         WHERE id = :id""", nativeQuery = true)
    void onSending(@Param("id") UUID id);

    @Modifying @Query(value = """
        UPDATE blast_campaign
           SET count_sending = count_sending - 1, count_sent = count_sent + 1, updated_at = now()
         WHERE id = :id""", nativeQuery = true)
    void onSent(@Param("id") UUID id);

    @Modifying @Query(value = """
        UPDATE blast_campaign
           SET count_sending = count_sending - 1, count_failed = count_failed + 1, updated_at = now()
         WHERE id = :id""", nativeQuery = true)
    void onFailed(@Param("id") UUID id);
    // … onReplied, onSkipped serupa

    // Tandai FINISHED bila tidak ada lagi pekerjaan
    @Modifying @Query(value = """
        UPDATE blast_campaign SET status = 'FINISHED', finished_at = now(), updated_at = now()
         WHERE id = :id AND status = 'RUNNING' AND count_waiting = 0 AND count_sending = 0""",
        nativeQuery = true)
    int markFinishedIfDone(@Param("id") UUID id);
}
```

### 7.3 BlastMessageRepository (generate recipient + reply lookup)

```java
public interface BlastMessageRepository extends JpaRepository<BlastMessage, Long> {
    Optional<BlastMessage> findByIdAndIdWorkspace(Long id, Long idWorkspace);
    Optional<BlastMessage> findFirstByProviderMessageId(String providerMessageId);
    List<BlastMessage> findByCampaignIdAndStatus(UUID campaignId, String status);

    // Reply detection: recipient campaign aktif untuk nomor & workspace, dalam window
    @Query(value = """
        SELECT * FROM blast_message
         WHERE id_workspace = :idWorkspace AND phone = :phone
           AND status IN ('SENT','DELIVERED','READ')
           AND sent_at >= :windowStart
         ORDER BY sent_at DESC LIMIT 1
        """, nativeQuery = true)
    Optional<BlastMessage> findRepliable(@Param("idWorkspace") Long idWorkspace,
                                         @Param("phone") String phone,
                                         @Param("windowStart") Instant windowStart);

    // Generate recipient set-based dengan dedup (Appendix C PRD)
    @Modifying @Query(value = """
        INSERT INTO blast_message
            (id_workspace, campaign_id, contact_id, phone, name, status, retry_count, waiting_at, created_at)
        SELECT bic.id_workspace, :campaignId, bic.contact_id, bic.normalized_phone, bic.raw_name,
               'WAITING', 0, now(), now()
          FROM blast_import_contact bic
         WHERE bic.import_id = :importId
           AND bic.category IN (:categories)
        ON CONFLICT (campaign_id, phone) DO NOTHING
        """, nativeQuery = true)
    int generateRecipients(@Param("campaignId") UUID campaignId,
                           @Param("importId") Long importId,
                           @Param("categories") Collection<String> categories);

    // Rekonsiliasi counter
    @Query(value = """
        SELECT status AS status, COUNT(*) AS cnt
          FROM blast_message WHERE campaign_id = :campaignId GROUP BY status
        """, nativeQuery = true)
    List<StatusCountProjection> countByStatus(@Param("campaignId") UUID campaignId);

    // Report (FR-14): stream baris per recipient untuk export Excel.
    // Stream (bukan List) agar campaign besar tidak memuat semua ke memori — wajib @Transactional(readOnly=true)
    // di service + cursor/fetch-size. Alternatif: paging Pageable per chunk.
    @QueryHints(@QueryHint(name = HINT_FETCH_SIZE, value = "500"))
    @Query("""
        SELECT m FROM BlastMessage m
         WHERE m.campaignId = :campaignId
         ORDER BY m.id ASC
        """)
    Stream<BlastMessage> streamReportRows(@Param("campaignId") UUID campaignId);
}
```

> Kolom Report `sent_by`/`sent_by_name` berasal dari WABA/workspace campaign (di-resolve sekali di service, bukan per-baris). `first_reply_sent_by`/`name` diturunkan dari `contactId`/`name` baris itu sendiri (lihat Appendix F PRD), jadi query report cukup dari `blast_message` saja.

### 7.4 BlastJobRepository (queue core — SKIP LOCKED)

```java
public interface BlastJobRepository extends JpaRepository<BlastJob, Long> {

    // CLAIM: ambil id job READY siap proses untuk campaign RUNNING, lewati yang terkunci
    @Query(value = """
        SELECT j.id FROM blast_job j
         WHERE j.status = 'READY' AND j.available_at <= now()
           AND j.campaign_id IN (SELECT id FROM blast_campaign WHERE status IN ('RUNNING','QUEUED'))
         ORDER BY j.priority DESC, j.id ASC
         LIMIT :batchSize
         FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Long> findClaimableIds(@Param("batchSize") int batchSize);

    @Modifying @Query(value = """
        UPDATE blast_job
           SET status = 'CLAIMED', locked_by = :workerId,
               locked_until = now() + (:leaseMs || ' milliseconds')::interval, updated_at = now()
         WHERE id IN (:ids) AND status = 'READY'
        """, nativeQuery = true)
    int claim(@Param("ids") List<Long> ids, @Param("workerId") String workerId, @Param("leaseMs") long leaseMs);

    // Generate queue idempotent (Appendix C PRD)
    @Modifying @Query(value = """
        INSERT INTO blast_job
            (id_workspace, campaign_id, message_id, status, attempt, max_attempts, priority,
             dedup_key, available_at, created_at)
        SELECT m.id_workspace, :campaignId, m.id, 'READY', 1, :maxAttempts, 0,
               :campaignId || ':' || m.id || ':1', now(), now()
          FROM blast_message m
         WHERE m.campaign_id = :campaignId AND m.status = 'WAITING'
        ON CONFLICT (message_id, attempt) DO NOTHING
        """, nativeQuery = true)
    int generateQueue(@Param("campaignId") UUID campaignId, @Param("maxAttempts") int maxAttempts);

    // Reaper: kembalikan lease kedaluwarsa
    @Modifying @Query(value = """
        UPDATE blast_job
           SET status = 'READY', locked_by = NULL, locked_until = NULL, updated_at = now()
         WHERE status IN ('CLAIMED','PROCESSING') AND locked_until < now()
        """, nativeQuery = true)
    int reapExpired();

    // Cancel: batalkan job pending campaign
    @Modifying @Query(value = """
        UPDATE blast_job SET status = 'CANCELLED', updated_at = now()
         WHERE campaign_id = :campaignId AND status IN ('READY','RETRYING')
        """, nativeQuery = true)
    int cancelPending(@Param("campaignId") UUID campaignId);
}
```

> **Catatan transaksi klaim:** `findClaimableIds` (FOR UPDATE SKIP LOCKED) dan `claim(...)` harus berada dalam **satu transaksi singkat** (`@Transactional` pada method poller). Lock baris dilepas saat commit; kepemilikan logikal dipertahankan kolom `status=CLAIMED` + `locked_until`.

---

## 8. Model / DTO

### 8.1 Request

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CreateCampaignRequest {
    @NotNull  private Long importId;
    @NotBlank private String name;
    @NotNull  private TargetType targetType;
    @NotNull  private MessageSource messageSource;
    private UUID templateId;       // wajib jika messageSource=TEMPLATE (validasi service)
    private String content;        // wajib jika CUSTOM
    private String mediaLink;
    private String deviceId;       // opsional; default WABA workspace
    private CampaignConfig config; // batchSize, delayMs, maxAttempts (nullable)
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RetryRequest { private List<Long> messageIds; } // null/empty = semua FAILED
```

### 8.2 Response

- `ImportSummaryDto` (totalUpload, totalValid, totalInvalid, totalDuplicate, existingContact, newContact, status).
- `CampaignListProjection` (interface projection, getter sesuai alias native; `getCreatedAtRaw():Instant` + default formatter `Asia/Jakarta`).
- `CampaignProgressDto` (status, total, waiting, sending, success, failed, replied, skipped, percentage) — dihitung dari counter.
- `MessageListDto` (phone, name, status, retryCount, lastError, sentAt, repliedAt).
- `ReviewDto` (name, recipientCount, previewMessage, estimatedDurationSeconds, status).

Semua dibungkus `RestResponse`. Format timestamp di layer DTO (`yyyy-MM-dd HH:mm`, `Asia/Jakarta`) — konsisten `ChatListDto`/`ConversationDto`.

---

## 9. Service Layer

### 9.1 `BlastImportService`

- `handleUpload(MultipartFile file, Long workspaceId, Long accountId) → ImportSummaryDto`
  1. Validasi ekstensi/MIME/parseability/ukuran/jumlah baris (lihat §17). Ukuran via property multipart (§15).
  2. (Opsional) simpan file ke MinIO via `storageService.upload(file)` → `filePath`.
  3. `INSERT blast_import (status=UPLOADED, createdBy, createdAt=now)`.
  4. `ExcelParser.parse(file)` → list `(rowNumber, rawName, rawPhone)`; normalisasi via `PhoneNumberUtil.normalizeToIndonesianFormat`.
  5. Batch insert `blast_import_contact` (status awal kategori belum final; set `INVALID` jika gagal normalisasi/format).
  6. Trigger analisis **otomatis & async** (OQ-1) → publish `BlastImportUploadedEvent`, di-handle `@Async @TransactionalEventListener(AFTER_COMMIT)`.

- `ExcelParser`: gunakan POI; untuk file besar pakai **streaming** (`XSSFReader`/SAX event API) sesuai mitigasi R-9. Pemetaan header case-insensitive (Appendix A PRD).

### 9.2 `BlastAnalysisService`

- `analyze(Long importId, Long workspaceId)`:
  1. Set `blast_import.status = ANALYZING`.
  2. Tandai `DUPLICATE` (nomor ternormalisasi sama, kemunculan ke-2+) — window function di native query (`ROW_NUMBER() OVER (PARTITION BY normalized_phone ORDER BY id)`), baris >1 → `DUPLICATE`.
  3. Tandai `INVALID` (BR-3: kosong / bukan prefix `62` / panjang di luar 10–15).
  4. Sisanya default `NEW`, lalu `markExistingByWorkspace(...)` (§7.1) men-set `EXISTING` via JOIN `contact`.
  5. Hitung summary → simpan di `blast_import`; status `ANALYZED`.
- Idempotent: aman dijalankan ulang (endpoint `analyze` re-run manual).

### 9.3 `BlastCampaignService`

- `create(CreateCampaignRequest, workspaceId, accountId) → campaignId`:
  - Validasi import `ANALYZED` & milik workspace & **belum `CONSUMED`** (BR-22, else 409). Validasi template milik workspace bila TEMPLATE. Validasi proyeksi recipient ≥1 (BR-17).
  - Snapshot `messageContent`+`mediaLink` (jika TEMPLATE: baca `chat_template.content`/`media_link`). Set `deviceId` = `workspace.getWaba().getId().toString()` jika tidak dikirim (OQ-4).
  - `INSERT blast_campaign (DRAFT, totalRecipient=proyeksi)`; lalu `blast_import.status = CONSUMED` (BR-22, dilindungi `@Version` import).
- `review(campaignId, workspaceId) → ReviewDto`: render sample via `PlaceholderEngine` (data recipient pertama / dummy), estimasi `recipient × delayMs`.
- `start(campaignId, workspaceId)`:
  - Muat campaign, cek `status == DRAFT` (else 409). Transisi `DRAFT→QUEUED` di-guard `@Version` (BR-13). Recipient ≥1 (BR-17).
  - Picu `generateRecipientsAndQueue(campaignId)` **async** (OQ-12); kembalikan `QUEUED` segera.
- `generateRecipientsAndQueue(campaignId)` (`@Async`, transaksional):
  - `blastMessageRepository.generateRecipients(...)` → `blastJobRepository.generateQueue(...)` → set `count_waiting = total_recipient`. (Worker yang akan memindahkan campaign ke `RUNNING` saat job pertama diproses — lihat §11.)
- `pause/resume/cancel(campaignId, workspaceId)`: validasi transisi state machine (PRD §14.1); cancel memanggil `cancelPending` + set message `WAITING→SKIPPED` + counter.

### 9.4 `BlastSenderService`

`processJob(Long jobId)` (dipanggil executor; transaksi pendek per langkah):
1. Muat job + message. **Guard idempotency**: jika `message.status NOT IN (WAITING)` → set job `DONE`, return (tidak kirim).
2. `message → SENDING` (+event, `campaignRepo.onSending`), `job → PROCESSING`. Pindahkan campaign `QUEUED→RUNNING` bila masih QUEUED.
3. Render pesan: `PlaceholderEngine.render(campaign.messageContent, context)`.
4. Kirim: jika `mediaLink != null` → `client.sendImage(deviceId, phone, rendered, storageService.getPublicUrl(mediaLink))`; else `client.sendMessage(deviceId, new GoWaSendMessageRequest(phone, rendered, ...))` (OQ-17). `deviceId` = `campaign.deviceId`.
5. Cek `response.getCode().equals("SUCCESS")`:
   - **Sukses:** simpan `providerMessageId = response.getResults().getMessage_id()`, `rendered_message`, `message → SENT` (+event, `onSent`), `job → DONE`. Lalu `chatMessageService.recordOutboundChat(...)` (§12).
   - **Gagal/exception:** klasifikasi error (§17). Retriable & `attempt < maxAttempts` → `job → RETRYING` + insert job baru (`attempt+1`, `available_at = now + backoff`). Else `message → FAILED` (+event, `onFailed`), `job → DEAD`.
6. Setelah job selesai: `campaignRepo.markFinishedIfDone(campaignId)`.

### 9.5 `BlastRetryService`

`retry(campaignId, messageIds?, workspaceId)`: validasi campaign != CANCELLED; untuk tiap message `FAILED`: `retryCount++`, `FAILED→WAITING` (+event source=USER), insert `blast_job` baru (`attempt=retryCount+1`, dedup_key baru). Update counter (`failed--`, `waiting++`). Jika campaign `FINISHED` → `RUNNING`.

### 9.6 `BlastStatusService`

- `applyDeliveryStatus(providerMessageId, status, ts)` (webhook): map → rank; terapkan hanya bila `rank(to) > rank(current)` (BR-14); update message + timestamp + event source=WEBHOOK.
- `markReplied(idWorkspace, phone, ReplyPayload reply)` (dipanggil dari hook pesan masuk): `findRepliable(...)` dalam window 7 hari (OQ-10); jika ketemu & belum `REPLIED`:
  - set `REPLIED` + `repliedAt = reply.createdAt` + `onReplied` (+event source=REPLY).
  - **Simpan balasan pertama (FR-13, BR-23):** isi `firstReplyChatId`, `firstReplyMessage`, `firstReplyMediaType`, `firstReplyMediaLink` dari `reply`. **Idempotent**: hanya jika `firstReplyChatId == null` (guard) — balasan ke-2+ tidak menimpa.
  - `ReplyPayload` = ringkasan `Chat` masuk (chatId, message, mediaType, mediaPath, createdAt) yang dirakit di hook (§12.3).

### 9.7 `BlastReportService` (Generate Report — FR-14)

- `generateReport(UUID campaignId, Long workspaceId, OutputStream out)`:
  1. Validasi campaign milik workspace (404/403).
  2. Tulis Excel **streaming** via POI `SXSSFWorkbook` (mis. `rowAccessWindowSize=100`), sheet `Messages`, header sesuai **Appendix F PRD** (22 kolom, urutan tetap).
  3. Stream baris dari `blastMessageRepository.streamReportRows(campaignId)` (lihat §7.3) — gunakan `Stream<…>`/`Pageable` chunk agar tidak memuat semua ke memori.
  4. Map tiap baris ke kolom Appendix F; nilai turunan: `media_type` (text bila tanpa media), `is_replied = repliedAt != null`, `sent_by`/`sent_by_name` dari WABA/workspace, `sent_by_type="campaigns"`, URL media via `storageService.getPublicUrl(path)`.
  5. **Sanitasi anti formula injection** (FR-14.6): setiap nilai string sel diawali `= + - @` → prefix `'`. Sediakan helper `sanitizeCell(String)`.
- Controller (§13) men-set `Content-Disposition` dengan nama file `{sanitizedCampaignName}_messages_{yyyy-MM-dd}.xlsx` dan menulis langsung ke `response.getOutputStream()` (tidak buffer ke memori/disk).
- Tidak mengubah state campaign; mencerminkan `blast_message` saat generate (snapshot).

---

## 10. Placeholder Engine

Sintaks **`{{key}}`** (OQ-6). Registry resolver extensible (Open/Closed).

```java
public interface PlaceholderResolver {
    String key();                              // mis. "name", "phone"
    String resolve(BlastMessageContext ctx);
}

@Getter @AllArgsConstructor
public class BlastMessageContext {
    private final String recipientName;
    private final String recipientPhone;
    // future: Order order; Product product; Tracking tracking; …
}

@Component
public class PlaceholderEngine {
    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");
    private final Map<String, PlaceholderResolver> resolvers;

    public PlaceholderEngine(List<PlaceholderResolver> list) {
        this.resolvers = list.stream().collect(Collectors.toMap(PlaceholderResolver::key, r -> r));
    }

    public String render(String template, BlastMessageContext ctx) {
        Matcher m = TOKEN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            PlaceholderResolver r = resolvers.get(key);
            if (r == null) {                       // unknown token (OQ-6)
                log.warn("Unknown placeholder {{{}}} dibiarkan apa adanya", key);
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));   // biarkan apa adanya
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(
                        Objects.toString(r.resolve(ctx), "")));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
```

Resolver MVP: `NameResolver` (`{{name}}`), `PhoneResolver` (`{{phone}}`).

**Adapter ke template existing:** `MessageConstructorHelper.fillTemplate` memakai sintaks `{key}` (single brace). Saat sumber pesan TEMPLATE memuat `{key}` legacy, sediakan pre-pass adapter yang mengonversi/menjembatani sesuai kebutuhan; placeholder Blast baru tetap `{{key}}`. Rendering dilakukan **per-recipient di worker** (bukan saat Start) — PRD §5.4.

---

## 11. Background Worker

### 11.1 Aktivasi Scheduling

`@EnableScheduling` **belum ada** di codebase → **tambahkan** ke `ApiApplication`:

```java
@EnableAsync
@EnableScheduling          // BARU — diperlukan @Scheduled worker Blast
@SpringBootApplication(scanBasePackages = "com.saktiform")
public class ApiApplication { … }
```

### 11.2 Executor

> **Koreksi desain (ditemukan saat implementasi):** JANGAN mengekspos `ThreadPoolTaskExecutor`/`Executor` sebagai bean Spring. Codebase belum punya custom `TaskExecutor`; jika satu-satunya `TaskExecutor` bean ditambahkan, Spring otomatis menjadikannya **default `@Async`** untuk seluruh method `@Async` existing (`OrderEventListener.autoFollowup`, `BotIncomingChatListener.handleIncomingChat`, `WhatsappService.processWebhook2`) — mengubah/merusak perilaku async yang sudah berjalan (pool fixed + `queueCapacity=0` bisa menolak task mereka).

Pola yang benar = **mengelola `ExecutorService` sendiri di dalam worker**, persis seperti `BotDelayManager` (yang membuat `Executors.newScheduledThreadPool(max(4, cores*2))` di constructor, bukan bean Spring). Dengan begitu executor Blast terisolasi dan tidak menyentuh resolusi `@Async` global.

```java
@Component
public class BlastWorkerExecutor {
    private final ExecutorService pool =
        Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors() * 2),
            r -> { Thread t = new Thread(r, "blast-worker-" + counter.incrementAndGet()); t.setDaemon(true); return t; });

    public void submit(Runnable task) { pool.submit(task); }

    @PreDestroy
    public void shutdown() { pool.shutdown(); /* graceful: tunggu in-flight, lease menjaga sisanya */ }
}
```

`BlastQueuePoller` meng-inject `BlastWorkerExecutor` dan memanggil `executor.submit(...)`. Tidak ada `@Bean TaskExecutor`, jadi `@Async` default tetap `SimpleAsyncTaskExecutor` seperti sekarang.

### 11.3 `BlastQueuePoller`

```java
@Component
public class BlastQueuePoller {
    @Scheduled(fixedDelayString = "${blast.worker.poll-interval-ms:1000}")
    public void poll() {
        List<Long> ids = claimBatch();                 // transaksi singkat (findClaimableIds + claim)
        for (Long jobId : ids) executor.submit(() -> sender.processJob(jobId));
    }

    @Transactional
    protected List<Long> claimBatch() {
        List<Long> ids = jobRepo.findClaimableIds(batchSize);
        if (!ids.isEmpty()) jobRepo.claim(ids, workerId, leaseMs);
        return ids;
    }
}
```

- `workerId` = `hostname:thread` (atau UUID per-instance) untuk tracing kepemilikan lease.
- **Anti-ban delay:** di-`sleep(delayMs)` di dalam `processJob` **setelah** melepaskan koneksi DB (jangan menahan koneksi Hikari selama sleep — §16). Karena delay per-session, untuk MVP single-session set `blastExecutor` core kecil atau serialisasi per `device_id`.

### 11.4 `BlastJobReaper`

```java
@Scheduled(fixedDelayString = "${blast.worker.reaper-interval-ms:30000}")
@Transactional
public void reap() { int n = jobRepo.reapExpired(); if (n>0) log.info("Reaped {} expired jobs", n); }
```

### 11.5 `BlastCounterReconciler` (OQ-9)

```java
@Scheduled(fixedDelayString = "${blast.worker.reconcile-interval-ms:300000}")  // 5 menit
@Transactional
public void reconcile() {
    for (UUID campaignId : campaignRepo.findRunningIds()) {
        // hitung ulang dari blast_message.countByStatus → set absolut counter campaign
    }
}
```

---

## 12. Integrasi Conversation (Reuse & Refactor)

### 12.1 Refactor: ekstrak `recordOutboundChat`

Logika "record outbound Chat + update conversation + publish event" pada `ChatService.messageHandler` (blok sukses, baris ~95–144) **di-ekstrak** menjadi method reusable. Letakkan di `ChatMessageService` agar bisa dipanggil `ChatService` (refactor) **dan** `BlastSenderService`.

```java
// ChatMessageService (BARU/EXTRACT)
@Transactional
public RecordOutboundResult recordOutboundChat(RecordOutboundCommand cmd) {
    // cmd: idWorkspace, phone, name, providerMessageId, type, message, mediaPath,
    //      pengirim, source ("BLAST"), assigneeAccountId (campaign.createdBy), suppressEvents(false)
    // 1) find-or-create Contact (scoped workspace) via conversationService.findContactByPhoneNumberAndIdWorkspace
    //    → jika null: buat (ON CONFLICT aman karena unique index OQ-20), set id_workspace/phone/name
    // 2) find-or-create Conversation via conversationService.findByIdContact(contactId)
    //    → jika null: buat (status=UNASSIGNED, chatStatus=OPEN, botQuota dari AppConfig, source="BLAST", createdAt=now)
    // 3) AUTO-ASSIGN (FR-12.9/BR-20): jika conversation baru / status UNASSIGNED →
    //       status=ASSIGNED, handledBy=assigneeAccountId, handleByBot=false
    //       + publish event takeover-like (UNASSIGNED_REMOVED + ASSIGNED_CREATED)
    //    else (sudah ASSIGNED) → jangan ubah assignment/bot (FR-12.10/BR-21)
    // 4) save Chat keluar (idConversation, messageId=providerMessageId, type, pengirim, pesan, media path,
    //       status="SENT", sentAt=now)
    // 5) update conversation lastMessage/lastMessageType/lastMessageAt (TIDAK menaikkan unreadMessageCount — FR-12.6)
    // 6) publish ChatAsyncEvent.NEW_MESSAGE + ASSIGNED_CONVERSATION_UPDATED (emit penuh — OQ-18)
    return new RecordOutboundResult(conversationId, chatId);
}
```

`ChatService.messageHandler` di-refactor untuk memanggil method ini (perilaku live-chat tidak berubah). `BlastSenderService` memanggilnya **setelah kirim sukses** dengan:
- `pengirim = "BLAST-" + <kode pembuat>` (OQ-19) — format `BLAST-USER001`; "kode pembuat" diturunkan dari `Account` (`createdBy`) — gunakan `username`/kode akun yang stabil & human-readable.
- `source = "BLAST"`, `assigneeAccountId = campaign.createdBy`.
- Set `blast_message.conversationId` & `chatId` dari hasil.

### 12.2 Isolasi & idempotency

- Workspace isolation mengalir via `Contact.idWorkspace` (BR-19); `deviceId`/WABA dari workspace campaign.
- Find-or-create idempotent (BR-18/FR-12.7); unique index `contact(id_workspace, phone_number)` (OQ-20) + tangani `ON CONFLICT`/retry-read pada race antar worker.
- Event WebSocket di MVP **emit penuh** (OQ-18); `suppressEvents` flag disiapkan untuk backlog FE-Throttle (tidak diaktifkan MVP).

### 12.3 Reply detection hook

Pada `WhatsappMessageHandler.handleMessagePayload` (alur pesan masuk existing), tambahkan hook ringan (idealnya via event `@Async @TransactionalEventListener(AFTER_COMMIT)` agar tidak memperlambat alur masuk) yang memanggil `BlastStatusService.markReplied(idWorkspace, phone, replyPayload)`. Karena pesan blast sudah menempel ke Conversation kontak yang sama, balasan otomatis masuk ke conversation tersebut (FR-12.6).

Hook merakit `ReplyPayload` dari `Chat` masuk yang baru disimpan: `chatId`, `message` (`pesan`), `mediaType` (`type`), `mediaPath` (`media`), `createdAt` (`sentAt`). `markReplied` lalu menandai `REPLIED` **dan menyimpan balasan pertama** ke `blast_message` (FR-13, BR-23) — idempotent, hanya balasan pertama. Riwayat balasan lengkap tetap sebagai `Chat` pada `Conversation`.

---

## 13. Controller & REST

Base path `/blast`. `workspaceId` query param. Return `ResponseEntity<RestResponse>`. Mapping ke PRD §11.

| Method | Path | Service |
|---|---|---|
| POST | `/blast/import` (multipart `file`) | `BlastImportService.handleUpload` |
| POST | `/blast/import/{importId}/analyze` | `BlastAnalysisService.analyze` |
| GET | `/blast/import/{importId}` (`?category&page&limit`) | summary + sample baris |
| POST | `/blast/campaign` | `BlastCampaignService.create` |
| GET | `/blast/campaign/{id}/review` | `review` |
| POST | `/blast/campaign/{id}/start` | `start` |
| GET | `/blast/campaign` (`?page&limit&search&status`) | `search` |
| GET | `/blast/campaign/{id}` | detail + progress |
| GET | `/blast/campaign/{id}/progress` | counter (NFR-2) |
| GET | `/blast/campaign/{id}/messages` (`?status&page&limit`) | list recipient |
| GET | `/blast/campaign/{id}/report` | `BlastReportService.generateReport` (stream `.xlsx`) |
| POST | `/blast/campaign/{id}/retry` | `BlastRetryService.retry` |
| POST | `/blast/campaign/{id}/pause`\|`resume`\|`cancel` | state transitions |
| POST | `/blast/webhook/status` (**public**) | `BlastStatusService.applyDeliveryStatus` |
| GET | `/blast/import/template-file` | unduh Excel contoh |

Controller mengikuti pola try/catch `MasterController.uploadFile` (set `RestResponse` success/data/message; map exception → HTTP status sesuai tabel error PRD §11). Validasi `@Valid` pada request body; error JSR-303 → `ErrorDto` list.

**Report endpoint** (`GET /…/report`) berbeda dari endpoint JSON lain: tidak membungkus `RestResponse`, melainkan menulis biner `.xlsx` langsung ke `HttpServletResponse.getOutputStream()` dengan `Content-Type` spreadsheet + `Content-Disposition: attachment; filename="{campaign}_messages_{yyyy-MM-dd}.xlsx"` (FR-14.5). Error (404/403) tetap dikembalikan sebagai `RestResponse` JSON sebelum stream dimulai.

---

## 14. Security

`SecurityConfig` (lines ~41–59) — tambahkan webhook ke `permitAll`:

```java
.requestMatchers(
    /* existing… */,
    "/blast/webhook/**"          // BARU — public, diverifikasi shared-secret/HMAC
).permitAll()
```

- Endpoint `/blast/**` lain tetap di belakang `JwtAuthenticationFilter`.
- Webhook memverifikasi **shared secret / HMAC signature** header sebelum memproses; selalu balas `200` (acknowledge) agar provider tidak retry berlebihan.
- Otorisasi: service memvalidasi akun ter-assign ke `workspaceId` (cek membership `account_workspace`) — coarse-grained, konsisten existing (OQ-15). Tidak menambah `@PreAuthorize`.

---

## 15. Konfigurasi & Properties

Tambahkan ke `application.properties` (pola `${ENV:default}`):

```properties
# Multipart — belum ada di codebase; WAJIB diset untuk batas upload Blast (PRD: 2 MB)
spring.servlet.multipart.max-file-size=2MB
spring.servlet.multipart.max-request-size=4MB

# Blast worker
blast.worker.poll-interval-ms=${BLAST_POLL_INTERVAL_MS:1000}
blast.worker.batch-size=${BLAST_BATCH_SIZE:100}
blast.worker.delay-ms=${BLAST_DELAY_MS:1500}
blast.worker.max-attempts=${BLAST_MAX_ATTEMPTS:3}
blast.worker.lease-duration-ms=${BLAST_LEASE_MS:60000}
blast.worker.reaper-interval-ms=${BLAST_REAPER_MS:30000}
blast.worker.reconcile-interval-ms=${BLAST_RECONCILE_MS:300000}
blast.worker.backoff-base-ms=${BLAST_BACKOFF_BASE_MS:30000}
blast.upload.max-rows=${BLAST_MAX_ROWS:20000}
blast.reply.window-days=${BLAST_REPLY_WINDOW_DAYS:7}
blast.webhook.secret=${BLAST_WEBHOOK_SECRET:}
```

Default per-campaign override (batch/delay/maxAttempts) dibaca dari `blast_campaign`; fallback ke properties/`AppConfig`. Nilai `bot.default.quota` (untuk `botQuota` conversation baru) sudah ada via `AppConfigService.getConfig("bot.default.quota")` — reuse.

---

## 16. Konkurensi, Idempotency & Transaksi

| Mekanisme | Implementasi |
|---|---|
| Klaim disjoint multi-worker | `FOR UPDATE SKIP LOCKED` + `UPDATE … WHERE status='READY'` dalam 1 transaksi singkat (BR-10). |
| **Race in-flight vs Cancel/Pause** (Fase 8) | `beginSend` re-cek status campaign setelah claim: `CANCELLED` → message `WAITING→SKIPPED` + job `CANCELLED` (tidak dikirim); `PAUSED` → job dilepas ke `READY` (diklaim ulang setelah Resume); selain RUNNING/QUEUED → job `DONE` tanpa kirim. |
| **Kegagalan generate/analisis** (Fase 8) | Dijalankan pada tx sendiri (listener cross-bean). Bila gagal → rollback bersih, lalu `BlastFailSafeService.markCampaignFailed/markImportFailed` (`REQUIRES_NEW`) menandai FAILED tanpa terpengaruh rollback → tidak nyangkut di `QUEUED`/`ANALYZING`. |
| Lease / graceful restart | `locked_until` di-set saat claim; `BlastJobReaper` mengembalikan job kedaluwarsa (BR-11). |
| Idempotency kirim | Guard `message.status == WAITING` sebelum kirim; setelah sukses simpan `provider_message_id` & `SENT` → re-claim melihat non-WAITING → ack tanpa kirim (BR-12). |
| Dedup recipient | `UNIQUE(campaign_id, phone)`; generate `ON CONFLICT DO NOTHING`. |
| Dedup job | `UNIQUE(message_id, attempt)`; generate/retry `ON CONFLICT DO NOTHING`. |
| Double-start | `@Version` pada `BlastCampaign` + status guard `DRAFT→QUEUED` (BR-13). |
| Single import→campaign | `@Version` pada `BlastImport` + status guard `ANALYZED→CONSUMED` (BR-22). |
| Counter akurat | Increment relatif via native `UPDATE` atomik (§7.2) + `BlastCounterReconciler` (OQ-9). |
| Contact race | `UNIQUE(id_workspace, phone_number)` + `ON CONFLICT`/retry-read (OQ-20). |

**Transaksi:** klaim, pemrosesan tiap job, dan update status bersifat **transaksi pendek**. Koneksi Hikari (max 20) **tidak ditahan** selama `sleep(delayMs)` — lepaskan koneksi, sleep di thread, buka transaksi baru untuk update status (§18.2 PRD).

**Jendela duplikasi (R-3):** crash setelah kirim sebelum commit `SENT` — tulis `SENDING`+`provider_message_id` sedini mungkin; MVP tidak bergantung idempotency key provider (OQ-13). Risiko kecil & terdokumentasi.

---

## 17. Error Handling

### 17.1 Upload/Analisis

| Kasus | Penanganan |
|---|---|
| Bukan Excel / korup | 415/422; `blast_import.status=FAILED` (atau tidak simpan). |
| Header wajib hilang | 422 + daftar kolom kurang. |
| Sel rusak/kosong | baris → `INVALID` + `invalid_reason`; proses lanjut (partial success). |
| Melebihi batas baris/ukuran | 422/413 — tolak seluruh file (OQ-5). |

### 17.2 Pengiriman (worker)

| Kasus | Klasifikasi | Aksi |
|---|---|---|
| Timeout / 5xx WA API | retriable | `RETRYING` + backoff (`base × 2^attempt`). |
| 4xx nomor invalid | non-retriable | `FAILED` + `last_error`. |
| Device off/disconnected | retriable | `RETRYING` + alert; eskalasi campaign `FAILED` bila down > threshold (OQ-14). |
| Exception render placeholder | non-retriable (message itu) | `FAILED`, job lain tak terdampak. |
| DB error saat update | — | rollback; lease expired → re-claim (at-least-once + idempotency guard). |
| Body provider tak terduga | retriable | `RETRYING` + log raw. |

### 17.3 Format

Validasi → `ErrorResponse{code,message}` / `List<ErrorDto>` (400/422). Error state bisnis → `RestResponse(success=false, message)` + HTTP sesuai tabel PRD §11. Error worker tidak dikembalikan real-time; terekam di `blast_message.last_error` + `blast_message_event`, terlihat di Detail Campaign.

---

## 18. Observability & Logging

- Korelasi id pada log worker: `campaignId`, `messageId`, `attempt`, `providerMessageId`, `workerId`.
- **Jangan log nomor lengkap di level INFO** (PII, PRD §17) — mask (mis. `62812****890`).
- `blast_message_event` = timeline audit append-only (sumber kebenaran).
- Reaper & reconciler menulis ringkasan koreksi (jumlah job/counter terkoreksi) di INFO.

---

## 19. Testing Strategy

| Lapisan | Uji |
|---|---|
| Unit — `PhoneNumberUtil` | sudah ada; verifikasi kasus normalisasi dipakai analisis. |
| Unit — `PlaceholderEngine` | `{{name}}`/`{{phone}}`, unknown token dibiarkan + log, nested/whitespace. |
| Unit — `BlastAnalysisService` | klasifikasi EXISTING/NEW/INVALID/DUPLICATE + invariant summary. |
| Unit — state machine | transisi valid/invalid campaign/message/job (PRD §14). |
| Integration — repository (`@DataJpaTest` + Testcontainers Postgres) | `generateRecipients`/`generateQueue` `ON CONFLICT`; `findClaimableIds` SKIP LOCKED (2 koneksi paralel klaim disjoint); `reapExpired`; counter atomik. |
| Integration — sender | mock `WhatsappClientHelper` (sukses/4xx/5xx/timeout) → status & retry benar; idempotency (proses ulang job SENT tidak kirim ulang). |
| Integration — conversation attach | find-or-create idempotent; auto-assign hanya saat baru/UNASSIGNED; no-replace saat ASSIGNED; unread tak naik. |
| Integration — upload | file valid/invalid/oversize/header hilang (`phone_number`/`name` + alias); staging terisi benar. |
| Integration — first reply | `markReplied` mengisi `first_reply_*` pada balasan pertama; **idempotent** (balasan ke-2 tidak menimpa); di luar window tidak menandai. |
| Integration — report | `generateReport` menghasilkan sheet `Messages` dengan **22 kolom urutan tepat** (Appendix F); baris replied memuat `first_reply_*`; `is_replied` benar; sanitasi formula injection (sel `=…` → `'=…`); streaming tidak OOM untuk N besar. |
| E2E (manual) | lihat §20; bandingkan output dengan `docs/Blast template/report.xlsx`. |

Fokus uji konkurensi: dua poller paralel tidak memproses job sama; crash-recovery via lease.

---

## 20. Rencana Implementasi Bertahap

| Fase | Deliverable | Catatan |
|---|---|---|
| **0. Infra** ✅ | `@EnableScheduling` (di `ApiApplication`) + properties (`spring.servlet.multipart.*` dinaikkan ke 10MB, `blast.worker.*`, `blast.upload.*`, `blast.reply.*`, `blast.webhook.secret`) | **DONE.** Executor **bukan** bean Spring (lihat §11.2) → digeser ke Fase 4. Index `blast_*`/contact digeser ke Fase 1 / langkah terkonfirmasi (lihat §4). |
| **1. Skema** | 6 entity + 6 repository + enum + `BlastSchemaInitializer` (partial index via `ApplicationReadyEvent`) | ddl-auto membuat tabel + index/unique dari `@Table`. **Unique index `contact` (OQ-20) di-hold** sampai dedup dikonfirmasi DBA. |
| **2. Upload & Analisis** | `BlastImportService`, `ExcelParser`, `BlastAnalysisService`, `BlastImportController` (upload/analyze/get) | bisa di-demo tanpa worker |
| **3. Campaign** | `BlastCampaignService` (create/review/start/pause/resume/cancel), `BlastCampaignController`, generate recipient+queue | |
| **4. Worker** | `QueuePort`/`DbQueueAdapter`, `BlastQueuePoller`, `BlastWorkerExecutor`, `BlastSenderService`, `PlaceholderEngine`+resolver, `BlastJobReaper` | inti pengiriman |
| **5. Conversation** | refactor `recordOutboundChat`, integrasi sender, auto-assign, label `BLAST-<creator>` | |
| **6. Status, Balasan & Monitoring** | `BlastStatusService` (webhook + reply hook **simpan balasan pertama**, FR-13), kolom `first_reply_*`, progress/detail/messages endpoint, `BlastCounterReconciler` | balasan tersimpan di `blast_message` |
| **7. Report, Retry & History** | `BlastReportService` (export `.xlsx` SXSSF, FR-14, Appendix F) + endpoint `/report`, `BlastRetryService`, list/search/filter, download template | report per campaign |
| **8. Hardening** ✅ (sebagian) | **DONE:** (1) tandai FAILED via `BlastFailSafeService` `REQUIRES_NEW` + pisah listener `BlastCampaignStartListener`/`BlastImportAnalyzeListener` (tx bersih cross-bean — cegah state nyangkut/partial commit); (2) `beginSend` re-cek status campaign (CANCELLED→SKIP, PAUSED→lepas klaim ke READY) — cegah kirim untuk campaign non-RUNNING pasca-claim; (3) PII masking log via `BlastPhoneMask`; (4) klasifikasi & alert device-off (WARN, OQ-14). **Belum:** unique index `contact` (OQ-20, butuh dedup DBA), uji konkurensi otomatis (Testcontainers — belum ada infra tes), partitioning tabel (opsional). |

---

## 21. Item Verifikasi Provider

Dua keputusan PRD bersifat **desain + fallback** dan butuh verifikasi kapabilitas provider WA multi-device saat implementasi (tidak mengubah arsitektur):

- **OQ-7 (webhook delivery):** konfirmasi apakah provider mengirim callback DELIVERED/READ + formatnya. Jika tidak → status berhenti di `SENT` (endpoint generik + adapter tetap disiapkan).
- **OQ-13 (idempotency key):** konfirmasi apakah API mendukung client-side idempotency key. Jika ya → pakai untuk eliminasi total R-3 (additive).

---

## 22. Appendix — DDL Migration & Skeleton

### 22.1 Catatan

Tabel `blast_*` + index/unique constraint biasa dibuat **Hibernate** (`ddl-auto=update`) dari `@Table(indexes=…, uniqueConstraints=…)` pada entity §5. Partial index `blast_job` dibuat **`BlastSchemaInitializer`** (post-`ApplicationReadyEvent`). **Flyway tidak dipakai untuk tabel `blast_*`** karena ordering (lihat §4). Flyway hanya untuk perubahan tabel **existing** (`contact`, terkonfirmasi).

### 22.2 `BlastSchemaInitializer` — partial index (post-startup)

```java
@Component
@RequiredArgsConstructor
public class BlastSchemaInitializer {
    private final JdbcTemplate jdbc;

    @EventListener(ApplicationReadyEvent.class)   // setelah Hibernate buat tabel
    public void ensureIndexes() {
        jdbc.execute("""
            CREATE INDEX IF NOT EXISTS idx_job_claim
                ON blast_job (status, available_at, priority DESC, id)
                WHERE status = 'READY'
        """);
    }
}
```

> `uq_message_campaign_phone (campaign_id, phone)` & `uq_job_message_attempt (message_id, attempt)` dibuat Hibernate via `@Table(uniqueConstraints=…)` (§5.4/§5.5) — tidak perlu skrip terpisah.

### 22.3 `V2__contact_unique_phone.sql` (OQ-20 — perubahan tabel existing, **DI-HOLD**)

> **⚠️ JANGAN commit migrasi ini sebelum DBA mengonfirmasi.** `CREATE UNIQUE INDEX` gagal (→ app tidak boot) bila masih ada duplikat `(id_workspace, phone_number)` di `contact`. Diperlukan oleh find-or-create blast (`ON CONFLICT`, Fase 5), bukan Fase 0/1. Cek duplikat dulu: `SELECT id_workspace, phone_number, COUNT(*) FROM contact GROUP BY 1,2 HAVING COUNT(*)>1;`

```sql
-- LANGKAH 1: dedup data contact existing per workspace (pertahankan id terkecil).
--            Reassign referensi (conversation.id_contact, dll) ke id yang dipertahankan
--            SEBELUM menghapus duplikat — sesuaikan dengan FK aktual.
WITH ranked AS (
    SELECT id, id_workspace, phone_number,
           ROW_NUMBER() OVER (PARTITION BY id_workspace, phone_number ORDER BY id) AS rn
      FROM contact
)
-- (jalankan reassignment referensi di sini bila ada FK ke contact)
DELETE FROM contact c USING ranked r
 WHERE c.id = r.id AND r.rn > 1;

-- LANGKAH 2: unique index
CREATE UNIQUE INDEX IF NOT EXISTS uq_contact_ws_phone
    ON contact (id_workspace, phone_number);
```

> ⚠️ Langkah dedup harus ditinjau DBA terhadap FK aktual yang menunjuk `contact` (mis. `conversation.id_contact`, `order`, `blast_import_contact.contact_id`). Reassign referensi ke id yang dipertahankan sebelum DELETE agar tidak melanggar FK / kehilangan data.

### 22.4 Native claim — referensi (lihat §7.4)

Query klaim, generate recipient/queue, reaper sudah tercantum di §7.3/§7.4 dan konsisten dengan Appendix C PRD.

---

*TDD ini turunan langsung dari [PRD Blast Chat](../prd/blast-chat.md). Snippet kode = acuan desain (skeleton); penamaan & signature final mengikuti review kode. Mulai implementasi dari Fase 0 (§20).*
