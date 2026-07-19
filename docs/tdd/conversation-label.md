# TDD — Conversation Label (Backend)

| Field | Value |
|---|---|
| Feature name | Conversation Label |
| Dokumen induk | [PRD — Conversation Label](../prd/conversation-label.md) (Status: *Ready for TDD*) |
| Component | Modul baru package `label` (entity, repository, model, service, controller) + integrasi `ConversationService`/`ConversationRepository` + `LabelSchemaInitializer` |
| Status | Implemented (Fase 1–6 selesai & terverifikasi end-to-end) |
| Scope | Per-workspace; backend-only |
| Last updated | 2026-07-18 |
| Target pembaca | Backend Developer (acuan implementasi langsung), Reviewer, QA, Frontend |

> TDD ini menerjemahkan PRD Conversation Label menjadi desain teknis konkret yang **selaras dengan konvensi codebase Saktiform**. Seluruh Open Question pada PRD sudah **RESOLVED**. Snippet kode bersifat **acuan desain** (skeleton), bukan kode final yang harus disalin verbatim.

---

## Daftar Isi

1. [Tujuan & Ruang Lingkup Teknis](#1-tujuan--ruang-lingkup-teknis)
2. [Arsitektur Modul](#2-arsitektur-modul)
3. [Konvensi yang Diwarisi dari Codebase](#3-konvensi-yang-diwarisi-dari-codebase)
4. [Strategi Skema & Migrasi DB](#4-strategi-skema--migrasi-db)
5. [Entity (JPA)](#5-entity-jpa)
6. [Repository](#6-repository)
7. [Model / DTO](#7-model--dto)
8. [Service Layer](#8-service-layer)
9. [Integrasi Conversation (Read & Filter)](#9-integrasi-conversation-read--filter)
10. [Controller & REST](#10-controller--rest)
11. [Security](#11-security)
12. [Konkurensi, Idempotency & Transaksi](#12-konkurensi-idempotency--transaksi)
13. [Error Handling](#13-error-handling)
14. [Testing Strategy](#14-testing-strategy)
15. [Rencana Implementasi Bertahap](#15-rencana-implementasi-bertahap)
16. [Appendix — Skeleton](#16-appendix--skeleton)

---

## 1. Tujuan & Ruang Lingkup Teknis

Mengimplementasikan fitur Label sesuai PRD: master label per-workspace (teks + warna hex) → assign/unassign banyak label ke conversation (many-to-many) → tampil di list/detail conversation → filter list by label (OR).

**Prinsip desain teknis:**

- **Reuse konvensi existing**: entity Lombok, `RestResponse`, isolasi tenant via `id_workspace`, format timestamp `Asia/Jakarta`, pola controller `try/catch → badRequest`.
- **Additive only**: dua tabel baru (`conversation_label`, `conversation_label_link`); satu perubahan pada query list conversation existing (predikat filter).
- **Cascade dikelola di service** (bukan FK fisik), konsisten pendekatan dual-field/logikal codebase.
- **Unique `lower(name)`** dibuat post-startup (`LabelSchemaInitializer`, pola `BlastSchemaInitializer`).

Out of scope (PRD §3): label untuk entity selain Conversation, hierarki/auto-labelling, UI, sharing lintas workspace.

---

## 2. Arsitektur Modul

### 2.1 Struktur Package

```
com.saktiform.api/
├── entity/
│   ├── ConversationLabel.java
│   └── ConversationLabelLink.java
├── repository/
│   ├── ConversationLabelRepository.java
│   └── ConversationLabelLinkRepository.java
├── model/label/
│   ├── request/
│   │   ├── LabelRequest.java          (create/update: name, colorHex)
│   │   └── AssignLabelRequest.java    (labelIds: List<Long>)
│   └── response/
│       ├── LabelDto.java              (id, name, colorHex)
│       └── ConversationLabelProjection.java  (batch fetch: conversationId + label fields)
├── service/label/
│   ├── ConversationLabelService.java  (CRUD master, assign/unassign, batch fetch)
│   └── HexColor.java                  (util validasi & normalisasi hex)
├── configuration/
│   └── LabelSchemaInitializer.java    (unique index lower(name) post-startup)
└── controller/
    └── ConversationLabelController.java
```

Integrasi (ubah file existing):
- `service/chat/ConversationService.java` — sertakan `labels` di list & detail, dukung filter `labelId`.
- `repository/ConversationRepository.java` — tambah predikat filter label pada `getConversation`.
- `model/account/ConversationDetail.java` — tambah field `labels`.
- Item list conversation dibungkus DTO baru `model/chat/ConversationListItemDto.java` (projection + labels).

### 2.2 Alur Komponen (ringkas)

```
ConversationLabelController
  → ConversationLabelService.create/list/update/delete           (master label, guard workspace)
  → ConversationLabelService.assign/unassign/listForConversation (link, guard workspace)

ChatController /conversation/assigned|unassigned
  → ConversationService.getAssignedChat/getUnassignedChat
      → ConversationRepository.getConversation(... labelFilter, labelIds ...)   (filter OR)
      → ConversationLabelService.labelsByConversationIds(ids)                    (batch, anti N+1)
      → map ke Page<ConversationListItemDto> (projection + labels)

ChatController /conversation/detail
  → ConversationService.getConversationDetail
      → ConversationLabelService.listForConversation(conversationId) → ConversationDetail.labels
```

---

## 3. Konvensi yang Diwarisi dari Codebase

Acuan: `Conversation`, `Contact`, `ConversationRepository`, `ConversationService`, `ChatController`, `BlastSchemaInitializer`, `BlastCampaignService`.

| Aspek | Konvensi | Diterapkan di Label |
|---|---|---|
| ID Long | `@GeneratedValue(strategy = GenerationType.IDENTITY)` | `conversation_label`, `conversation_label_link` |
| Anotasi entity | `@Getter @Setter @Entity @Table(name="…")` (Lombok) | semua |
| Kolom | `@Column(name="snake_case")`; teks bebas `length = Integer.MAX_VALUE` | `name` |
| Audit | `Instant createdAt/updatedAt`, **di-set manual di service** | semua |
| Isolasi tenant | filter `id_workspace` (list conversation via `contact.id_workspace`) | semua query |
| Response | `RestResponse(success, message, data)` | semua controller |
| DTO request | `@Getter @Setter @NoArgsConstructor @AllArgsConstructor` + JSR-303 | request model |
| DTO response | interface projection (native) / concrete DTO | `LabelDto`, projection |
| Timestamp | format `Asia/Jakarta`, pola `yyyy-MM-dd HH:mm` di DTO | bila diekspos |
| Controller | `try { … RestResponse ok } catch { badRequest }` | `ConversationLabelController` |
| Post-startup index | `@EventListener(ApplicationReadyEvent.class)` + `JdbcTemplate` + `IF NOT EXISTS` | `LabelSchemaInitializer` |
| Cascade | dikelola di service (query `DELETE`), bukan FK fisik | hapus label |

> **Catatan penting:** `Conversation` **tidak** punya kolom `id_workspace`; tenant di-resolve lewat `conversation.contact.id_workspace` (lihat `ConversationRepository.getConversation` yang join `contact ct ON ct.id = c.id_contact` lalu `WHERE ct.id_workspace = :idWorkspace`). Karena itu tabel `conversation_label_link` **men-denormalisasi** `id_workspace` agar filter & guard tidak perlu join contact tiap kali.

---

## 4. Strategi Skema & Migrasi DB

Mengikuti pola codebase: `spring.jpa.hibernate.ddl-auto=update` membuat tabel & index biasa dari anotasi entity. Fitur ini **fitur baru** → tidak ada migrasi data.

**Keputusan:**

1. Tabel `conversation_label` & `conversation_label_link` dibuat **Hibernate `ddl-auto=update`** dari entity. Tidak perlu DDL manual.
2. Index biasa & unique constraint `(conversation_id, label_id)` dideklarasikan via `@Table(indexes=…, uniqueConstraints=…)`.
3. **Unique index case-insensitive `lower(name)` per workspace** tidak bisa dihasilkan Hibernate (functional index). Dibuat oleh **`LabelSchemaInitializer`** (`@Component`, `ApplicationReadyEvent`, idempotent `CREATE UNIQUE INDEX IF NOT EXISTS`), persis pola `BlastSchemaInitializer`.

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uq_conversation_label_ws_name
    ON conversation_label (id_workspace, lower(name));
```

> Bila terdapat pelanggaran unik di runtime (dua request paralel membuat nama sama), `INSERT` akan gagal constraint → service menangkap `DataIntegrityViolationException` dan mengembalikan pesan duplikat (§13).

---

## 5. Entity (JPA)

### 5.1 `ConversationLabel` (master)

```java
@Getter @Setter @Entity
@Table(name = "conversation_label",
       indexes = @Index(name = "idx_label_ws", columnList = "id_workspace, name"))
public class ConversationLabel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "id_workspace", nullable = false)
    private Long idWorkspace;

    @Column(name = "name", length = Integer.MAX_VALUE, nullable = false)
    private String name;

    @Column(name = "color_hex", length = 7, nullable = false)
    private String colorHex;          // tersimpan ternormalisasi: #rrggbb

    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
}
```

> Unique `(id_workspace, lower(name))` **tidak** dideklarasikan di `@Table` (butuh functional index) — dibuat `LabelSchemaInitializer` (§4).

### 5.2 `ConversationLabelLink` (join many-to-many)

```java
@Getter @Setter @Entity
@Table(name = "conversation_label_link",
       uniqueConstraints = @UniqueConstraint(name = "uq_link_conversation_label",
                                             columnNames = {"conversation_id", "label_id"}),
       indexes = {
           @Index(name = "idx_link_conversation", columnList = "conversation_id"),
           @Index(name = "idx_link_label", columnList = "label_id"),
           @Index(name = "idx_link_ws", columnList = "id_workspace")
       })
public class ConversationLabelLink {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "label_id", nullable = false)
    private Long labelId;

    @Column(name = "id_workspace", nullable = false)
    private Long idWorkspace;          // denormalized (Conversation tak punya id_workspace)

    @Column(name = "created_at") private Instant createdAt;
}
```

> Relasi dipetakan sebagai **kolom eksplisit** (bukan `@ManyToOne`), konsisten pola isolasi-tenant & menghindari lazy-loading tak perlu. Join ke master untuk kebutuhan DTO dilakukan via query batch (§6.2).

---

## 6. Repository

### 6.1 `ConversationLabelRepository`

```java
public interface ConversationLabelRepository extends JpaRepository<ConversationLabel, Long> {

    Optional<ConversationLabel> findByIdAndIdWorkspace(Long id, Long idWorkspace);

    List<ConversationLabel> findByIdWorkspaceOrderByNameAsc(Long idWorkspace);

    // Semua label milik workspace yang id-nya termasuk daftar (validasi assign all-or-nothing)
    List<ConversationLabel> findByIdWorkspaceAndIdIn(Long idWorkspace, Collection<Long> ids);

    // Cek duplikat nama case-insensitive (selain diri sendiri saat update)
    @Query("""
        SELECT COUNT(l) > 0 FROM ConversationLabel l
         WHERE l.idWorkspace = :idWorkspace
           AND lower(l.name) = lower(:name)
           AND (:excludeId IS NULL OR l.id <> :excludeId)
        """)
    boolean existsByWorkspaceAndName(@Param("idWorkspace") Long idWorkspace,
                                     @Param("name") String name,
                                     @Param("excludeId") Long excludeId);
}
```

### 6.2 `ConversationLabelLinkRepository`

```java
public interface ConversationLabelLinkRepository extends JpaRepository<ConversationLabelLink, Long> {

    boolean existsByConversationIdAndLabelId(UUID conversationId, Long labelId);

    // Unassign (idempotent — deleteBy tak error bila tidak ada baris)
    @Modifying
    void deleteByConversationIdAndLabelId(UUID conversationId, Long labelId);

    // Cascade hapus master label
    @Modifying
    void deleteByLabelId(Long labelId);

    // Label pada satu conversation (JOIN ke master) — detail
    @Query("""
        SELECT l FROM ConversationLabel l
         WHERE l.id IN (SELECT k.labelId FROM ConversationLabelLink k
                         WHERE k.conversationId = :conversationId)
         ORDER BY l.name ASC
        """)
    List<ConversationLabel> findLabelsByConversationId(@Param("conversationId") UUID conversationId);

    // Batch fetch untuk list (anti N+1): kembalikan pasangan conversationId + field label
    @Query("""
        SELECT k.conversationId AS conversationId,
               l.id AS id, l.name AS name, l.colorHex AS colorHex
          FROM ConversationLabelLink k
          JOIN ConversationLabel l ON l.id = k.labelId
         WHERE k.conversationId IN (:conversationIds)
         ORDER BY l.name ASC
        """)
    List<ConversationLabelProjection> findLabelsByConversationIds(
            @Param("conversationIds") Collection<UUID> conversationIds);
}
```

`ConversationLabelProjection` (interface projection):
```java
public interface ConversationLabelProjection {
    UUID getConversationId();
    Long getId();
    String getName();
    String getColorHex();
}
```

### 6.3 `ConversationRepository` (ubah — predikat filter label)

Tambahkan **dua parameter** ke `getConversation(...)` (main query + countQuery) dan satu predikat OR:

```sql
-- tambahkan sebelum ORDER BY, di main query DAN countQuery:
AND (
    :labelFilter = false
    OR EXISTS (
        SELECT 1 FROM conversation_label_link ll
         WHERE ll.conversation_id = c.id
           AND ll.label_id IN (:labelIds)
    )
)
```

Signature bertambah:
```java
Page<ConversationDto> getConversation(..., 
        @Param("labelFilter") boolean labelFilter,
        @Param("labelIds") Collection<Long> labelIds,
        Pageable pageable);
```

> **Aturan pemakaian:** karena native `IN (:labelIds)` **tidak boleh kosong** di PostgreSQL/Hibernate, service **selalu** mengirim `labelIds` non-empty: bila tidak memfilter → `labelFilter=false` + `labelIds = List.of(-1L)` (sentinel, tak pernah match). Predikat `:labelFilter = false OR …` men-short-circuit sehingga sentinel tak berpengaruh. Filter OR otomatis karena `IN`.

---

## 7. Model / DTO

### 7.1 Request

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class LabelRequest {
    @NotBlank private String name;
    @NotBlank private String colorHex;   // #RRGGBB (divalidasi & dinormalisasi di service)
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AssignLabelRequest {
    @NotEmpty private List<Long> labelIds;
}
```

### 7.2 Response

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class LabelDto {
    private Long id;
    private String name;
    private String colorHex;

    public static LabelDto from(ConversationLabel l) {
        return new LabelDto(l.getId(), l.getName(), l.getColorHex());
    }
    public static LabelDto from(ConversationLabelProjection p) {
        return new LabelDto(p.getId(), p.getName(), p.getColorHex());
    }
}
```

### 7.3 Item list conversation (projection + labels)

`ConversationDto` saat ini adalah **interface projection** yang dikembalikan langsung `Page<ConversationDto>` dari native query — tidak bisa menampung `labels`. Solusi: DTO concrete pembungkus.

```java
@Getter @Setter
public class ConversationListItemDto {
    private UUID id;
    private String contactName;
    private String lastMessage;
    private String lastMessageType;
    private String lastMessageTime;   // sudah terformat Asia/Jakarta
    private String status;
    private String chatStatus;
    private Integer unreadMessageCount;
    private List<LabelDto> labels;

    public ConversationListItemDto(ConversationDto p, List<LabelDto> labels) {
        this.id = p.getId();
        this.contactName = p.getContactName();
        this.lastMessage = p.getLastMessage();
        this.lastMessageType = p.getLastMessageType();
        this.lastMessageTime = p.getLastMessageTime();      // default formatter projection
        this.status = p.getStatus();
        this.chatStatus = p.getChatStatus();
        this.unreadMessageCount = p.getUnreadMessageCount();
        this.labels = labels == null ? List.of() : labels;
    }
}
```

> Bentuk JSON tetap **superset** dari yang sekarang (field lama identik + tambahan `labels`) → backward compatible (NFR-5). `ConversationDetail` cukup ditambah field `private List<LabelDto> labels;`.

---

## 8. Service Layer

### 8.1 `HexColor` (util)

```java
public final class HexColor {
    private static final Pattern P = Pattern.compile("^#?[0-9a-fA-F]{6}$");
    private HexColor() {}

    /** Validasi #RRGGBB (6-digit); kembalikan bentuk ternormalisasi #rrggbb. Throw bila invalid. */
    public static String normalize(String raw) {
        if (raw == null || !P.matcher(raw.trim()).matches()) {
            throw new IllegalArgumentException("colorHex harus format #RRGGBB (6 digit heksadesimal)");
        }
        String hex = raw.trim();
        if (!hex.startsWith("#")) hex = "#" + hex;
        return hex.toLowerCase();
    }
}
```

### 8.2 `ConversationLabelService`

```java
@Service
public class ConversationLabelService {

    private final ConversationLabelRepository labelRepository;
    private final ConversationLabelLinkRepository linkRepository;
    private final ConversationRepository conversationRepository;

    // ctor injection…

    // ---- master label ----

    @Transactional
    public LabelDto create(LabelRequest req, Long workspaceId) {
        String name = req.getName().trim();
        String color = HexColor.normalize(req.getColorHex());
        if (labelRepository.existsByWorkspaceAndName(workspaceId, name, null)) {
            throw new IllegalStateException("Label dengan nama tersebut sudah ada di workspace ini");
        }
        Instant now = Instant.now();
        ConversationLabel l = new ConversationLabel();
        l.setIdWorkspace(workspaceId);
        l.setName(name);
        l.setColorHex(color);
        l.setCreatedAt(now);
        l.setUpdatedAt(now);
        return LabelDto.from(labelRepository.save(l));
    }

    @Transactional(readOnly = true)
    public List<LabelDto> list(Long workspaceId) {
        return labelRepository.findByIdWorkspaceOrderByNameAsc(workspaceId)
                .stream().map(LabelDto::from).toList();
    }

    @Transactional
    public LabelDto update(Long labelId, LabelRequest req, Long workspaceId) {
        ConversationLabel l = labelRepository.findByIdAndIdWorkspace(labelId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Label tidak ditemukan"));
        String name = req.getName().trim();
        String color = HexColor.normalize(req.getColorHex());
        if (labelRepository.existsByWorkspaceAndName(workspaceId, name, labelId)) {
            throw new IllegalStateException("Label dengan nama tersebut sudah ada di workspace ini");
        }
        l.setName(name);
        l.setColorHex(color);
        l.setUpdatedAt(Instant.now());
        return LabelDto.from(labelRepository.save(l));
    }

    @Transactional
    public void delete(Long labelId, Long workspaceId) {
        ConversationLabel l = labelRepository.findByIdAndIdWorkspace(labelId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Label tidak ditemukan"));
        linkRepository.deleteByLabelId(l.getId());   // cascade di service (FR-6)
        labelRepository.delete(l);
    }

    // ---- assignment ----

    @Transactional
    public List<LabelDto> assign(UUID conversationId, List<Long> labelIds, Long workspaceId) {
        UUID convWs = requireConversationInWorkspace(conversationId, workspaceId);
        List<Long> ids = labelIds.stream().distinct().toList();

        // all-or-nothing (FR-11): semua id harus milik workspace
        List<ConversationLabel> labels = labelRepository.findByIdWorkspaceAndIdIn(workspaceId, ids);
        if (labels.size() != ids.size()) {
            throw new IllegalArgumentException("Sebagian labelId tidak ditemukan / bukan milik workspace ini");
        }
        Instant now = Instant.now();
        for (ConversationLabel l : labels) {
            if (!linkRepository.existsByConversationIdAndLabelId(conversationId, l.getId())) { // idempotent (FR-10)
                ConversationLabelLink link = new ConversationLabelLink();
                link.setConversationId(conversationId);
                link.setLabelId(l.getId());
                link.setIdWorkspace(workspaceId);
                link.setCreatedAt(now);
                linkRepository.save(link);
            }
        }
        return listForConversation(conversationId, workspaceId);
    }

    @Transactional
    public void unassign(UUID conversationId, Long labelId, Long workspaceId) {
        requireConversationInWorkspace(conversationId, workspaceId);
        linkRepository.deleteByConversationIdAndLabelId(conversationId, labelId); // idempotent no-op (FR-12)
    }

    @Transactional(readOnly = true)
    public List<LabelDto> listForConversation(UUID conversationId, Long workspaceId) {
        requireConversationInWorkspace(conversationId, workspaceId);
        return linkRepository.findLabelsByConversationId(conversationId)
                .stream().map(LabelDto::from).toList();
    }

    // ---- batch (dipakai ConversationService, anti N+1) ----

    @Transactional(readOnly = true)
    public Map<UUID, List<LabelDto>> labelsByConversationIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Map<UUID, List<LabelDto>> map = new HashMap<>();
        for (ConversationLabelProjection p : linkRepository.findLabelsByConversationIds(ids)) {
            map.computeIfAbsent(p.getConversationId(), k -> new ArrayList<>()).add(LabelDto.from(p));
        }
        return map;
    }

    // ---- helper ----

    /** Validasi conversation ada & milik workspace (via contact.id_workspace). */
    private UUID requireConversationInWorkspace(UUID conversationId, Long workspaceId) {
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation tidak ditemukan"));
        Long ws = c.getContact() != null ? c.getContact().getIdWorkspace() : null;
        if (ws == null || !ws.equals(workspaceId)) {
            throw new IllegalArgumentException("Conversation bukan milik workspace ini");
        }
        return conversationId;
    }
}
```

> `requireConversationInWorkspace` memakai `conversation.getContact().getIdWorkspace()`. `Contact` di-load lazy; karena dipanggil dalam method `@Transactional`, akses relasi aman. (Bila ingin hemat, tambah derived query `existsByIdAndContact_IdWorkspace` di `ConversationRepository` sebagai optimasi — opsional.)

---

## 9. Integrasi Conversation (Read & Filter)

### 9.1 List (assigned / unassigned)

`ConversationService.getAssignedChat/getUnassignedChat` diubah agar:
1. Menerima `List<Long> labelIds` (opsional) dari controller.
2. Menghitung `labelFilter` & sentinel:
   ```java
   boolean labelFilter = labelIds != null && !labelIds.isEmpty();
   Collection<Long> ids = labelFilter ? labelIds : List.of(-1L);
   ```
3. Memanggil `conversationRepository.getConversation(..., labelFilter, ids, pageable)` → `Page<ConversationDto>`.
4. Batch-hydrate label & map ke DTO baru:
   ```java
   Page<ConversationDto> page = conversationRepository.getConversation(...);
   List<UUID> convIds = page.getContent().stream().map(ConversationDto::getId).toList();
   Map<UUID, List<LabelDto>> byConv = labelService.labelsByConversationIds(convIds);
   return page.map(p -> new ConversationListItemDto(p, byConv.get(p.getId())));
   ```
   Return type kedua method berubah `Page<ConversationDto>` → `Page<ConversationListItemDto>`.

> **Anti N+1 (NFR-3):** satu query batch per halaman untuk seluruh `conversationId`, bukan per-item.

### 9.2 Detail

`getConversationDetail(conversationId)` — karena signature existing tak membawa `workspaceId`, tambahkan overload/parameter. Rekomendasi minimal-invasif: tetap gunakan conversation yang sudah di-load untuk mengambil `idWorkspace` dari contact, lalu:
```java
detail.setLabels(labelService.listForConversation(conversationId,
        conversation.getContact().getIdWorkspace()));
```
(Karena workspace diturunkan dari conversation itu sendiri, guard tetap konsisten tanpa mengubah kontrak endpoint detail.)

---

## 10. Controller & REST

`ConversationLabelController` (base `/chat/label` untuk master; assignment di bawah `/chat/conversation/{conversationId}/label`). Pola `try/catch → RestResponse` mengikuti `ChatController`/`BlastCampaignController`.

| Method & Path | Body / Param | Service |
|---|---|---|
| `POST /chat/label?workspaceId=` | `LabelRequest` | `create` |
| `GET /chat/label?workspaceId=` | — | `list` |
| `PUT /chat/label/{labelId}?workspaceId=` | `LabelRequest` | `update` |
| `DELETE /chat/label/{labelId}?workspaceId=` | — | `delete` |
| `POST /chat/conversation/{conversationId}/label?workspaceId=` | `AssignLabelRequest` | `assign` |
| `DELETE /chat/conversation/{conversationId}/label/{labelId}?workspaceId=` | — | `unassign` |
| `GET /chat/conversation/{conversationId}/label?workspaceId=` | — | `listForConversation` |

Perubahan `ChatController`:
- `GET /conversation/assigned` & `/unassigned` tambah `@RequestParam(required = false) List<Long> labelId` → diteruskan ke service. (Spring mem-bind `?labelId=1&labelId=2` menjadi `List<Long>`.)

Skeleton (ringkas):
```java
@RestController
@RequestMapping("/chat/label")
public class ConversationLabelController {
    private final ConversationLabelService service;
    // ctor…

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody LabelRequest req, @RequestParam Long workspaceId) {
        return exec(() -> service.create(req, workspaceId));
    }
    @GetMapping
    public ResponseEntity<?> list(@RequestParam Long workspaceId) {
        return exec(() -> service.list(workspaceId));
    }
    @PutMapping("/{labelId}")
    public ResponseEntity<?> update(@PathVariable Long labelId, @Valid @RequestBody LabelRequest req,
                                    @RequestParam Long workspaceId) {
        return exec(() -> service.update(labelId, req, workspaceId));
    }
    @DeleteMapping("/{labelId}")
    public ResponseEntity<?> delete(@PathVariable Long labelId, @RequestParam Long workspaceId) {
        return exec(() -> { service.delete(labelId, workspaceId); return "deleted"; });
    }
    // assignment endpoints boleh ditaruh di controller ini juga dgn @RequestMapping method-level path
    // atau di controller terpisah; contoh assign:
    @PostMapping("/../conversation/{conversationId}/label")  // → gunakan path absolut yang benar
    public ResponseEntity<?> assign(@PathVariable UUID conversationId,
                                    @Valid @RequestBody AssignLabelRequest req,
                                    @RequestParam Long workspaceId) {
        return exec(() -> service.assign(conversationId, req.getLabelIds(), workspaceId));
    }

    private ResponseEntity<?> exec(java.util.function.Supplier<Object> action) {
        RestResponse rest = new RestResponse();
        try { rest.setSuccess(true); rest.setMessage("Success"); rest.setData(action.get());
              return ResponseEntity.ok(rest); }
        catch (Exception e) { rest.setSuccess(false); rest.setMessage(e.getMessage()); rest.setData(null);
              return ResponseEntity.badRequest().body(rest); }
    }
}
```

> **Catatan routing:** endpoint assignment memakai prefix path berbeda (`/chat/conversation/...`). Implementasi bersih: buat method-nya di controller dengan anotasi path **absolut** `@PostMapping("/chat/conversation/{conversationId}/label")` pada method (bukan mewarisi `@RequestMapping("/chat/label")`), **atau** pisahkan ke controller khusus assignment. Pilih salah satu saat implementasi; hindari path relatif `..` (ilustratif di skeleton).

---

## 11. Security

- Semua endpoint label berada di bawah `/chat/**` → **butuh autentikasi JWT** (tidak termasuk daftar public endpoint di `SecurityConfig`). Tidak ada endpoint publik baru.
- **Otorisasi role (OQ-9): semua role** (SUPERADMIN/ADMIN/AGENT) diizinkan CRUD master & assign/unassign → tidak perlu pengecekan role tambahan; cukup autentikasi + guard workspace.
- **Isolasi tenant** ditegakkan di service: master via `findByIdAndIdWorkspace`; assignment via `requireConversationInWorkspace` + `findByIdWorkspaceAndIdIn`.
- `workspaceId` diterima sebagai `@RequestParam` konsisten endpoint lain (mis. `getAssignedChat`).

---

## 12. Konkurensi, Idempotency & Transaksi

- **Assign idempotent (FR-10):** guard `existsByConversationIdAndLabelId` + **unique constraint** `(conversation_id, label_id)` sebagai backstop. Bila dua request paralel menembus guard, `INSERT` kedua kena constraint → tangani `DataIntegrityViolationException` sebagai sukses idempotent (atau abaikan baris tsb).
- **Unassign idempotent (FR-12):** `deleteByConversationIdAndLabelId` tidak error bila tak ada baris → 200 no-op.
- **Create/update nama unik (FR-2):** cek `existsByWorkspaceAndName` + unique index `lower(name)` backstop; race → `DataIntegrityViolationException` → pesan duplikat.
- **Delete label (FR-6):** `deleteByLabelId` lalu `delete(label)` dalam **satu transaksi** → link tak pernah yatim.
- Semua operasi tulis `@Transactional`; read `@Transactional(readOnly = true)`.

---

## 13. Error Handling

Mengikuti pola controller existing: exception → `catch` → `RestResponse(success=false, message=e.getMessage())` + **400 Bad Request**.

| Kondisi | Exception | HTTP |
|---|---|---|
| `name` kosong / `labelIds` kosong | Bean Validation (`@NotBlank`/`@NotEmpty`) | 400 |
| `colorHex` invalid | `IllegalArgumentException` (dari `HexColor.normalize`) | 400 |
| Nama duplikat (workspace) | `IllegalStateException` / `DataIntegrityViolationException` | 400 |
| Label tak ditemukan / beda workspace | `IllegalArgumentException` | 400 |
| Assign berisi labelId invalid (all-or-nothing) | `IllegalArgumentException` | 400 |
| Conversation tak ditemukan / beda workspace | `IllegalArgumentException` | 400 |

> Konsisten dengan codebase yang memetakan semua error domain ke `badRequest` (bukan 404/409 terpisah). Pesan Bahasa Indonesia, informatif.

---

## 14. Testing Strategy

**Unit (service):**
- `HexColor.normalize`: `#AABBCC`/`aabbcc` → `#aabbcc`; `red`, `#12`, `#GGGGGG`, `#AABBCCDD` → throw.
- `create`: sukses; duplikat nama (case-insensitive) → throw.
- `assign`: all-or-nothing (satu id asing → throw, tidak ada baris tersimpan); idempotent (assign ulang tak menambah baris); cross-workspace ditolak.
- `unassign`: label tak terpasang → no-op (tidak throw).
- `delete`: link ikut terhapus.
- `labelsByConversationIds`: grouping benar; input kosong → `Map.of()`.

**Integration (repository, `@DataJpaTest` / native):**
- Unique `(conversation_id, label_id)` mencegah duplikat.
- `findLabelsByConversationIds` mengembalikan pasangan benar untuk banyak conversation.
- `getConversation` dengan `labelFilter=true` + `labelIds` → hanya conversation ber-label tsb (OR); `labelFilter=false` → hasil identik seperti sebelum fitur (regresi nol).

**Controller / e2e (opsional):**
- CRUD label + assign/unassign happy path; response envelope `RestResponse`.
- List conversation menyertakan `labels`; filter `?labelId=` bekerja.

**Regresi:**
- Endpoint `assigned`/`unassigned` tanpa `labelId` mengembalikan bentuk & isi sama (kecuali tambahan field `labels`).

---

## 15. Rencana Implementasi Bertahap

| Fase | Isi | Verifikasi | Status |
|---|---|---|---|
| 1 | Entity `ConversationLabel`, `ConversationLabelLink` + `LabelSchemaInitializer` | App boot; tabel & unique index terbentuk | ✅ Selesai |
| 2 | Repository (kedua) + `HexColor` + DTO request/response | Unit test `HexColor`; `@DataJpaTest` link | ✅ Selesai |
| 3 | `ConversationLabelService` (CRUD master) + `ConversationLabelController` (master) | CRUD label via REST | ✅ Selesai |
| 4 | Assignment (assign/unassign/listForConversation) + endpoint | Assign/unassign idempotent & all-or-nothing | ✅ Selesai |
| 5 | Integrasi read: `labelsByConversationIds` → `ConversationListItemDto` di list; `labels` di `ConversationDetail` | List/detail memuat `labels` | ✅ Selesai |
| 6 | Filter `labelId` pada `getConversation` (main + countQuery) + wiring `ChatController` | Filter OR; regresi nol tanpa filter | ✅ Selesai |

Setiap fase additive & dapat di-deploy independen. Fase 1 aman lebih dulu (skema), fitur belum aktif sampai controller ada.

### 15.1 Catatan Implementasi (aktual)

Beberapa detail final yang berbeda / perlu dicatat dari skeleton di atas:

- **Path endpoint list/detail conversation TIDAK berprefix `/chat`.** `ChatController` memakai `@RequestMapping("")`, sehingga path aktualnya `/conversation/assigned`, `/conversation/unassigned`, `/conversation/detail`. Endpoint label master tetap `/chat/label` dan assignment `/chat/conversation/{conversationId}/label` (controller terpisah dengan path absolut).
- **Assignment dipisah ke controller sendiri** `ConversationLabelAssignmentController` (`@RequestMapping("/chat/conversation/{conversationId}/label")`), bukan digabung ke `ConversationLabelController` — menghindari path relatif yang diilustrasikan di §10.
- **`getConversationDetail` tidak menambah parameter `workspaceId`** di kontrak endpoint; workspace diturunkan dari `conversation.getContact().getIdWorkspace()` lalu diteruskan ke `labelService.listForConversation(...)` (guard tetap konsisten, backward compatible).
- **Filter label** memakai `boolean labelFilter` + sentinel `List.of(-1L)` saat tak memfilter (native `IN (:labelIds)` tak boleh kosong); predikat `:labelFilter = false OR EXISTS(...)` men-short-circuit. Ditambahkan di **main query & countQuery** sehingga `totalElements` ikut terfilter.
- **Verifikasi end-to-end (2026-07-18)** terhadap Postgres live: CRUD master, normalisasi hex (`22C55E`→`#22c55e`), nama duplikat case-insensitive ditolak, assign all-or-nothing & idempotent, unassign no-op, cascade delete, filter OR (`labelId=3` → 1 hasil; `labelId=3&labelId=4` → 2 hasil; `labelId=999` → 0), serta `labels` muncul di list & detail. Semua sesuai Acceptance Criteria PRD.

---

## 16. Appendix — Skeleton

### 16.1 `LabelSchemaInitializer`

```java
@Component
public class LabelSchemaInitializer {
    private static final Logger log = LoggerFactory.getLogger(LabelSchemaInitializer.class);
    private final JdbcTemplate jdbcTemplate;
    public LabelSchemaInitializer(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        try {
            jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_conversation_label_ws_name
                    ON conversation_label (id_workspace, lower(name))
                """);
            log.info("Label schema: unique index uq_conversation_label_ws_name ensured");
        } catch (Exception e) {
            log.error("Label schema: gagal membuat unique index nama label", e);
        }
    }
}
```

### 16.2 Contoh response list conversation (superset)

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "content": [
      {
        "id": "…uuid…",
        "contactName": "Budi",
        "lastMessage": "Halo",
        "lastMessageType": "text",
        "lastMessageTime": "2026-07-09 10:15",
        "status": "ASSIGNED",
        "chatStatus": "open",
        "unreadMessageCount": 0,
        "labels": [
          { "id": 1, "name": "Prospek", "colorHex": "#22c55e" },
          { "id": 2, "name": "VIP", "colorHex": "#f59e0b" }
        ]
      }
    ],
    "totalElements": 1
  }
}
```

### 16.3 Contoh request

```json
// POST /chat/label?workspaceId=1
{ "name": "Komplain", "colorHex": "#EF4444" }

// POST /chat/conversation/{conversationId}/label?workspaceId=1
{ "labelIds": [1, 2, 3] }
```
