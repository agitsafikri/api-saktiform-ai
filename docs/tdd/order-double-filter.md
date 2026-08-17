# TDD — Duplicate Order Filter (`isDouble`) — Backend

| Field | Value |
|---|---|
| Feature name | Duplicate Order Filter |
| Dokumen induk | [PRD — Duplicate Order Filter](../prd/order-double-filter.md) (Status: *Approved — ready for TDD*) |
| Component | `OrderController` · `OrderService` · `OrderRepository` (modifikasi 3 file existing) |
| Status | Draft — belum diimplementasikan |
| Scope | Per-workspace; backend-only; additive |
| Last updated | 2026-08-18 |
| Target pembaca | Backend Developer (acuan implementasi langsung), Reviewer, QA, Frontend |

> TDD ini menerjemahkan PRD menjadi desain teknis konkret yang **selaras dengan konvensi codebase Saktiform**. Seluruh Open Question pada PRD sudah **RESOLVED** (PRD §11). Snippet kode bersifat **acuan desain**, bukan kode final yang harus disalin verbatim.
>
> **Tidak ada file baru.** Fitur ini murni menambah satu parameter pada jalur kode yang sudah ada — tidak ada entity, DTO, service, atau controller baru.

---

## Daftar Isi

1. [Tujuan & Ruang Lingkup Teknis](#1-tujuan--ruang-lingkup-teknis)
2. [Baseline Codebase (kondisi existing)](#2-baseline-codebase-kondisi-existing)
3. [Konvensi yang Diwarisi dari Codebase](#3-konvensi-yang-diwarisi-dari-codebase)
4. [Keputusan Desain — Strategi Query Duplikat](#4-keputusan-desain--strategi-query-duplikat)
5. [Perubahan Repository](#5-perubahan-repository)
6. [Perubahan Service](#6-perubahan-service)
7. [Perubahan Controller](#7-perubahan-controller)
8. [Strategi Sorting (FR-13)](#8-strategi-sorting-fr-13)
9. [Indeks & Performa](#9-indeks--performa)
10. [Traceability PRD → Implementasi](#10-traceability-prd--implementasi)
11. [Error Handling & Edge Case](#11-error-handling--edge-case)
12. [Testing & Verifikasi](#12-testing--verifikasi)
13. [Rencana Implementasi Bertahap](#13-rencana-implementasi-bertahap)
14. [Dampak & Analisis Regresi](#14-dampak--analisis-regresi)
15. [Appendix — SQL Final](#15-appendix--sql-final)

---

## 1. Tujuan & Ruang Lingkup Teknis

Menambahkan satu parameter opsional `isDouble` (`Boolean`) pada `GET /order` dan `GET /order/export`. Saat bernilai `true`, hasil dibatasi pada order yang `nomor_whatsapp`-nya dipakai oleh **≥ 2 order** dalam workspace yang sama, dan hasil diurutkan berkelompok per nomor.

**Prinsip desain teknis:**

- **Additive & minimal-diff.** Tiga file existing dimodifikasi; tidak ada file baru. Bentuk response tidak berubah sama sekali (PRD FR-15).
- **Reuse pola yang sudah terbukti.** Predikat duplikat memakai pola `:flag = false OR EXISTS (…)` yang **persis sama** dengan filter label pada `ConversationRepository.getConversation` — pola tersebut sudah terbukti bekerja di produksi, termasuk penerapannya di `countQuery`.
- **Short-circuit saat filter mati.** Karena `:isDouble = false` berada di sisi kiri `OR`, PostgreSQL tidak akan mengeksekusi SubPlan `EXISTS` sama sekali ketika filter tidak aktif → nol biaya tambahan pada request normal.
- **Isolasi tenant konsisten.** Subquery duplikat ikut join `produk` dan memfilter `p2.id_workspace`, sama seperti query utama.

Out of scope (PRD §3): `GET /order/abandoned`, backfill nomor telepon, field `duplicateCount`, aksi apa pun terhadap order duplikat.

---

## 2. Baseline Codebase (kondisi existing)

### 2.1 Rantai pemanggilan

```
GET /order
  OrderController.getOrderList(...)                          OrderController.java:47-76
    → OrderService.getOrderList(...)                         OrderService.java:375-397
        → OrderRepository.getOrderList(...)                  OrderRepository.java:19-125
            (native query + countQuery, Page<OrderListDto>)

GET /order/export
  OrderController.exportOrder(...)                           OrderController.java:78-108
    → OrderService.exportOrder(...)                          OrderService.java:551-577
        → OrderRepository.exportOrder(...)                   OrderRepository.java:129-207
            (native query, List<ExportOrderListDto>)
        → OrderService.generateUserExcel(...)
        → OrderRepository.markAsExported(ids)
```

Masing-masing method service hanya punya **satu pemanggil** (controller), sehingga perubahan signature aman dan tidak berdampak ke modul lain. Sudah diverifikasi dengan pencarian `.getOrderList(` / `.exportOrder(` di seluruh `src`.

### 2.2 Yang sudah ada dan dipakai ulang

| Aspek | Kondisi existing |
|---|---|
| Alias tabel order | `FROM public.order ord` pada kedua query |
| Join tenant | `JOIN produk prod ON ord.id_produk = prod.id` → `WHERE prod.id_workspace = :idWorkspace` |
| Pola filter opsional | `AND (:param IS NULL OR ord.kolom = :param)` |
| Sorting list | `PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "created_at"))` di service |
| Sorting export | **tidak ada `ORDER BY` sama sekali** — urutan baris tidak deterministik |
| `countQuery` | duplikat terpisah dari main query; setiap predikat harus ditulis dua kali |
| Kolom nomor | `ord.nomor_whatsapp` (`String`), sudah di-`SELECT` pada kedua query |

### 2.3 Temuan penting — alias qualification pada `Sort`

Tabel `produk` **dan** tabel `order` sama-sama punya kolom `created_at` (`Produk.java:70`, `Order.java:91`). Query list menjoin keduanya, namun `Sort.by(DESC, "created_at")` tetap bekerja di produksi.

Ini membuktikan Spring Data **melakukan alias qualification** — `detectAlias()` mengenali `ord` dari `FROM public.order ord` lalu menghasilkan `ORDER BY ord.created_at DESC`. Kalau tidak, PostgreSQL akan menolak dengan `column reference "created_at" is ambiguous`.

**Konsekuensi untuk desain:** menambah `nomor_whatsapp` ke `Sort` aman — akan menjadi `ord.nomor_whatsapp`. Lagi pula kolom itu hanya ada di `ord` di antara tabel yang dijoin, jadi tetap tidak ambigu seandainya qualification gagal. Sorting list **tidak perlu** dipindah ke dalam query.

---

## 3. Konvensi yang Diwarisi dari Codebase

Acuan: `OrderRepository`, `ConversationRepository` (filter label), `OrderService`, `OrderController`.

| Aspek | Konvensi | Diterapkan di fitur ini |
|---|---|---|
| Predikat flag boolean | `:flag = false OR EXISTS (…)` dengan **primitive `boolean`** (bukan `Boolean`) — lihat `ConversationRepository.getConversation` param `labelFilter` | `:isDouble = false OR (…)` |
| Normalisasi null di service | Service mengubah `Boolean` nullable dari controller menjadi primitive sebelum ke repository | `Boolean.TRUE.equals(isDouble)` |
| countQuery | Setiap predikat baru wajib ditulis juga di `countQuery` | ya |
| Isolasi tenant | Filter `produk.id_workspace` pada setiap akses ke `order` | subquery ikut join `produk` |
| Controller | `@RequestParam(required = false)`, `try { … ok } catch { badRequest }` | ya |
| Response | `RestResponse(success, message, data)` — tidak berubah | ya |
| Penempatan param baru | Ditambahkan di **akhir** daftar parameter (sebelum `Pageable`) agar tidak menggeser argumen posisional yang sudah ada | ya |

> **Catatan konvensi:** parameter repository memakai **primitive `boolean`**, bukan `Boolean`. Ini disengaja dan mengikuti `labelFilter`: primitive tidak bisa null sehingga PostgreSQL selalu bisa menyimpulkan tipe parameter, menghindari galat `could not determine data type of parameter`. Normalisasi null dilakukan sekali di service.

---

## 4. Keputusan Desain — Strategi Query Duplikat

Tiga strategi dipertimbangkan. Konteks penting: PRD OQ-1 memilih **Interpretasi 1** (populasi duplikat dihitung se-workspace, lepas dari filter lain), dan PRD OQ-6 memutuskan **tidak** ada `duplicateCount` — jadi kita hanya perlu jawaban ya/tidak, bukan angka.

### Strategi A — `EXISTS` korelasi ✅ **DIPILIH**

```sql
AND (
    :isDouble = false
    OR (
        COALESCE(TRIM(ord.nomor_whatsapp), '') <> ''
        AND EXISTS (
            SELECT 1
            FROM public.order o2
            JOIN produk p2 ON o2.id_produk = p2.id
            WHERE p2.id_workspace = :idWorkspace
              AND o2.id <> ord.id
              AND o2.nomor_whatsapp = ord.nomor_whatsapp
        )
    )
)
```

| Kelebihan | Kekurangan |
|---|---|
| Berhenti pada kecocokan pertama — tidak menghitung seluruh grup | Dievaluasi per baris kandidat |
| Ramah indeks `(nomor_whatsapp)` | — |
| Short-circuit total saat `:isDouble = false` (SubPlan tak dieksekusi) | — |
| Struktur query existing tidak berubah; mudah diduplikasi ke `countQuery` | — |
| Pola identik dengan filter label yang sudah terbukti | — |

### Strategi B — `IN (SELECT … GROUP BY … HAVING COUNT(*) > 1)`

```sql
AND (:isDouble = false OR ord.nomor_whatsapp IN (
        SELECT o2.nomor_whatsapp FROM public.order o2
        JOIN produk p2 ON o2.id_produk = p2.id
        WHERE p2.id_workspace = :idWorkspace
        GROUP BY o2.nomor_whatsapp HAVING COUNT(*) > 1))
```

**Ditolak.** Selalu memateralisasi seluruh himpunan nomor duplikat workspace, walaupun request lain sudah menyempitkan hasil ke beberapa baris saja. Juga perlu guard tambahan untuk nomor kosong karena `''` bisa lolos `HAVING`. Tidak ada keuntungan dibanding A pada kasus pemakaian nyata (list terpaginasi 10 baris).

### Strategi C — Window function `COUNT(*) OVER (PARTITION BY nomor_whatsapp)`

**Ditolak.** Mengharuskan seluruh query dibungkus subselect agar hasil window bisa difilter, sehingga:
- struktur main query **dan** `countQuery` berubah total (risiko regresi tinggi),
- window dihitung untuk semua baris walaupun filter mati (melanggar NFR-3),
- window mem-partisi hasil *setelah* filter lain diterapkan → itu Interpretasi 2, **bertentangan dengan OQ-1**.

Strategi C baru relevan bila `duplicateCount` diperlukan — dan itu sudah ditolak di OQ-6.

---

## 5. Perubahan Repository

File: `src/main/java/com/saktiform/api/repository/OrderRepository.java`

### 5.1 `getOrderList` — main query

Sisipkan blok berikut **setelah** predikat `search` (blok `AND (... ILIKE ...)`), sebelum penutup `"""`:

```sql
          AND (
                :isDouble = false
                OR (
                    COALESCE(TRIM(ord.nomor_whatsapp), '') <> ''
                    AND EXISTS (
                        SELECT 1
                        FROM public.order o2
                        JOIN produk p2 ON o2.id_produk = p2.id
                        WHERE p2.id_workspace = :idWorkspace
                          AND o2.id <> ord.id
                          AND o2.nomor_whatsapp = ord.nomor_whatsapp
                    )
                )
          )
```

### 5.2 `getOrderList` — countQuery

**Blok yang sama persis** disisipkan di `countQuery` (PRD FR-12). Tanpa ini `totalElements` akan menghitung seluruh order dan pagination frontend rusak.

> Ini adalah kesalahan paling mudah terjadi pada perubahan ini — query dan countQuery pada `OrderRepository` sepenuhnya terpisah dan mudah luput. Jadikan poin wajib saat review.

### 5.3 `getOrderList` — signature

```java
Page<OrderListDto> getOrderList(@Param("idWorkspace") Long idWorkspace,
                                // … parameter existing tidak berubah …
                                @Param("search") String search,
                                @Param("isDouble") boolean isDouble,   // BARU — primitive
                                Pageable pageable);
```

### 5.4 `exportOrder` — query, ORDER BY, dan signature

Predikat yang sama disisipkan setelah blok `search`. Karena `exportOrder` **tidak punya `Pageable`**, sorting harus ditulis langsung di query:

```sql
        ORDER BY
            CASE WHEN :isDouble = true THEN ord.nomor_whatsapp END ASC,
            ord.created_at DESC
```

Saat `:isDouble = false`, ekspresi `CASE` menghasilkan `NULL` untuk semua baris (tanpa `ELSE`, implisit NULL) sehingga kunci pertama menjadi konstan dan pengurutan efektif hanya `created_at DESC`.

```java
List<ExportOrderListDto> exportOrder(@Param("idWorkspace") Long idWorkspace,
                                     // … parameter existing tidak berubah …
                                     @Param("search") String search,
                                     @Param("isDouble") boolean isDouble);   // BARU
```

> **Perubahan perilaku tercatat:** `exportOrder` saat ini tidak punya `ORDER BY`, sehingga urutan baris `.xlsx` tidak deterministik (bergantung rencana eksekusi PostgreSQL). Setelah perubahan ini urutan menjadi deterministik — `created_at DESC`, atau dikelompokkan per nomor saat `isDouble=true`. Ini perbaikan insidental yang aman, tetapi perlu disebutkan ke QA agar perbedaan urutan file hasil export tidak dilaporkan sebagai bug.

---

## 6. Perubahan Service

File: `src/main/java/com/saktiform/api/service/order/OrderService.java`

### 6.1 `getOrderList` (baris 375-397)

```java
public Page<OrderListDto> getOrderList(Long idWorkspace, Integer page, Integer limit,
                                       Integer idProvinsi, Integer idKota, Integer idKecamatan,
                                       String status, String jenisPembayaran, Boolean statusEkspor,
                                       LocalDateTime tanggalAwalPaid, LocalDateTime tanggalAkhirPaid,
                                       LocalDateTime tanggalAwalOrder, LocalDateTime tanggalAkhirOrder,
                                       String search,
                                       Boolean isDouble) {                       // BARU

    // OQ-3: hanya TRUE yang mengaktifkan filter; null dan false diperlakukan sama.
    boolean doubleFilter = Boolean.TRUE.equals(isDouble);

    // FR-13: kelompokkan per nomor hanya saat filter aktif.
    var sort = doubleFilter
            ? Sort.by(Sort.Order.asc("nomor_whatsapp"), Sort.Order.desc("created_at"))
            : Sort.by(Sort.Direction.DESC, "created_at");

    var pageable = PageRequest.of(page - 1, limit, sort);
    var tomorow  = LocalDateTime.now().plus(1, ChronoUnit.DAYS);
    var sentinel = LocalDateTime.of(1970, 1, 1, 0, 0, 0);

    return orderRepository.getOrderList(idWorkspace, idProvinsi, idKota, idKecamatan,
            status, jenisPembayaran, statusEkspor,
            tanggalAwalOrder, tanggalAkhirOrder, tanggalAwalPaid, tanggalAkhirPaid,
            sentinel, tomorow, search,
            doubleFilter,                                                        // BARU
            pageable);
}
```

### 6.2 `exportOrder` (baris 551-577)

```java
public byte[] exportOrder(Long idWorkspace, /* … existing … */ String search,
                          Boolean isDouble) throws IOException {                 // BARU

    boolean doubleFilter = Boolean.TRUE.equals(isDouble);
    // … sentinel & tomorow tidak berubah …

    var listOrder = orderRepository.exportOrder(idWorkspace, /* … */ search,
            doubleFilter);                                                       // BARU

    List<UUID> ids = listOrder.stream().map(ExportOrderListDto::getId).toList();
    var file = generateUserExcel(listOrder);
    orderRepository.markAsExported(ids);   // efek samping existing, tidak berubah (OQ-5)
    return file;
}
```

Import tambahan yang mungkin diperlukan: `org.springframework.data.domain.Sort` (cek — `Sort` sudah dipakai di file ini pada `Sort.by(...)` existing, jadi kemungkinan besar sudah ada).

---

## 7. Perubahan Controller

File: `src/main/java/com/saktiform/api/controller/OrderController.java`

Tambahkan satu `@RequestParam` pada kedua endpoint dan teruskan ke service. Tidak ada perubahan lain — blok `try/catch` dan `RestResponse` tetap.

```java
@GetMapping("")
public ResponseEntity<?> getOrderList(@RequestParam Long workspaceId,
                                      // … parameter existing …
                                      @RequestParam(required = false) String search,
                                      @RequestParam(required = false) Boolean isDouble) {   // BARU
    RestResponse rest = new RestResponse();
    try {
        var listOrder = orderService.getOrderList(workspaceId, page, limit,
                idProvinsi, idKota, idKecamatan,
                status == null ? null : status.name(),
                jenisPembayaran == null ? null : jenisPembayaran.name(),
                statusEkspor, tanggalAwalPaid, tanggalAkhirPaid,
                tanggalAwalOrder, tanggalAkhirOrder, search,
                isDouble);                                                                  // BARU
        // … tidak berubah …
```

Perubahan serupa pada `exportOrder` (baris 78-108).

**Binding invalid.** Nilai seperti `?isDouble=maybe` gagal di-convert Spring menjadi `Boolean` dan melempar `MethodArgumentTypeMismatchException` **sebelum** body method berjalan — sehingga tidak tertangkap `catch` di dalam method. Ini perilaku yang sudah berlaku untuk `statusEkspor` (juga `Boolean`) dan untuk semua param enum/tanggal existing. Tidak ada penanganan khusus yang ditambahkan; konsisten dengan endpoint lain. Catatan: Spring memperlakukan `?isDouble=` (kosong) sebagai `null` → filter mati, sesuai FR-2.

---

## 8. Strategi Sorting (FR-13)

| Kondisi | Mekanisme | Hasil `ORDER BY` |
|---|---|---|
| List, `isDouble` ≠ true | `Sort.by(DESC, "created_at")` — **tidak berubah** | `ord.created_at DESC` |
| List, `isDouble = true` | `Sort.by(asc("nomor_whatsapp"), desc("created_at"))` | `ord.nomor_whatsapp ASC, ord.created_at DESC` |
| Export, `isDouble` ≠ true | `CASE` di query | `NULL, ord.created_at DESC` → efektif `created_at DESC` |
| Export, `isDouble = true` | `CASE` di query | `ord.nomor_whatsapp ASC, ord.created_at DESC` |

**Mengapa list memakai `Sort` (service) dan export memakai `ORDER BY` (query)?** Karena list punya `Pageable` dan sudah memakai mekanisme `Sort` hari ini — memindahkannya ke query akan mengubah kode yang bekerja tanpa alasan. Export tidak punya `Pageable` sama sekali, jadi `ORDER BY` di query adalah satu-satunya pilihan.

**Stabilitas paging.** `nomor_whatsapp` + `created_at` belum tentu unik secara absolut (dua order dari nomor yang sama pada detik yang sama). Bila QA menemukan baris berpindah antar-halaman, tambahkan `ord.id` sebagai tie-breaker terakhir. Tidak dimasukkan sekarang karena menambah kolom ke `ORDER BY` yang belum terbukti perlu, dan pola existing (`created_at DESC` saja) pun tidak punya tie-breaker.

---

## 9. Indeks & Performa

### 9.1 Rencana eksekusi

Subquery `EXISTS` mencari `o2.nomor_whatsapp = ord.nomor_whatsapp` lalu memverifikasi workspace lewat join ke `produk`. Tanpa indeks, tiap baris kandidat memicu sequential scan pada tabel `order` → O(n²) pada workspace bervolume besar.

### 9.2 Indeks yang direkomendasikan

```sql
CREATE INDEX IF NOT EXISTS idx_order_nomor_whatsapp
    ON public."order" (nomor_whatsapp);
```

Opsi lebih kuat, membuat join ke `produk` bisa dilayani langsung dari indeks:

```sql
CREATE INDEX IF NOT EXISTS idx_order_nomor_whatsapp_produk
    ON public."order" (nomor_whatsapp, id_produk);
```

**Rekomendasi: mulai dari indeks kolom tunggal.** Naikkan ke komposit hanya bila `EXPLAIN ANALYZE` pada data produksi menunjukkan biaya lookup `produk` signifikan.

### 9.3 Cara membuat indeks

| Opsi | Cara | Catatan |
|---|---|---|
| **A (utama)** | Deklarasikan di entity: `@Table(name = "\"order\"", indexes = @Index(name = "idx_order_nomor_whatsapp", columnList = "nomor_whatsapp"))` | Paling idiomatik; `ddl-auto=update` membuatnya otomatis. **Wajib diverifikasi** setelah deploy — pembuatan indeks pada tabel yang sudah ada oleh Hibernate `update` tidak selalu dapat diandalkan lintas versi |
| **B (fallback)** | `OrderSchemaInitializer` mengikuti pola `BlastSchemaInitializer` / `LabelSchemaInitializer` (`@EventListener(ApplicationReadyEvent.class)` + `JdbcTemplate` + `CREATE INDEX IF NOT EXISTS`) | Deterministik dan idempotent; dipakai bila Opsi A tidak menghasilkan indeks |

Verifikasi setelah deploy:
```sql
\d public."order"
-- atau
SELECT indexname FROM pg_indexes
WHERE tablename = 'order' AND indexname LIKE 'idx_order_nomor%';
```

### 9.4 Biaya saat filter tidak aktif

Nol. `:isDouble = false` berada di sisi kiri `OR`; PostgreSQL mengevaluasi argumen `OR` berurutan dan berhenti pada `TRUE` pertama, sehingga SubPlan `EXISTS` tidak pernah dieksekusi. Memenuhi NFR-3.

---

## 10. Traceability PRD → Implementasi

| PRD | Requirement | Diwujudkan di |
|---|---|---|
| FR-1 | Param `isDouble` di `GET /order` | §7 controller |
| FR-2 | `null`/`false` = tanpa filter | §6 `Boolean.TRUE.equals(isDouble)` + `:isDouble = false` di SQL |
| FR-3 | Hanya order dengan nomor ≥2 kemunculan | §5.1 `EXISTS` |
| FR-4 | Scope per workspace | §5.1 `p2.id_workspace = :idWorkspace` di subquery |
| FR-5 | Populasi duplikat lepas dari filter lain | §4 Strategi A — subquery tidak membawa parameter filter apa pun |
| FR-6 | Semua status ikut dihitung | §5.1 — subquery tanpa predikat `status` |
| FR-7 | Tanpa batas waktu | §5.1 — subquery tanpa predikat tanggal |
| FR-8 | Kunci grup = nomor saja | §5.1 — subquery tanpa predikat `id_produk` |
| FR-9 | Exact match | §5.1 `o2.nomor_whatsapp = ord.nomor_whatsapp` (tanpa normalisasi) |
| FR-10 | Nomor NULL/kosong dikecualikan | §5.1 `COALESCE(TRIM(…), '') <> ''` |
| FR-11 | Komposisi `AND` dengan filter lain | §5.1 — disisipkan sebagai `AND (…)` |
| FR-12 | Pagination benar | §5.2 predikat sama di `countQuery` |
| FR-13 | Sorting berkelompok | §8 |
| FR-14 | Export ikut mendukung | §5.4, §6.2, §7 |
| FR-15 | Response shape tidak berubah | Tidak ada perubahan pada `OrderListDto` / `ExportOrderListDto` |
| NFR-2 | Performa | §9 indeks + short-circuit |
| NFR-4 | Isolasi tenant | §5.1 join `produk` di subquery |

---

## 11. Error Handling & Edge Case

| Kasus | Perilaku | Mekanisme |
|---|---|---|
| `isDouble` tidak dikirim | Filter mati, hasil & urutan identik hari ini | `Boolean.TRUE.equals(null)` → `false` |
| `isDouble=false` | Sama dengan tidak dikirim | idem |
| `isDouble=` (kosong) | Spring bind ke `null` → filter mati | binding default Spring |
| `isDouble=maybe` | HTTP 400 dari Spring (sebelum masuk method) | konsisten dengan param `Boolean`/enum existing |
| `nomor_whatsapp` NULL | Tidak pernah dianggap duplikat | `COALESCE(TRIM(…), '') <> ''` |
| `nomor_whatsapp` `''` atau spasi | Tidak pernah dianggap duplikat | idem |
| Nomor sama di workspace lain | Tidak dianggap duplikat | `p2.id_workspace = :idWorkspace` |
| Nomor sama, order sudah CANCELLED | Tetap dianggap duplikat | subquery tanpa filter status (OQ-2) |
| Nomor format lama (`08…` vs `628…`) | **Tidak** terdeteksi duplikat | keterbatasan diterima, PRD §12 poin 1 |
| Hasil duplikat 0 baris | `Page` kosong, `totalElements = 0`, `success: true` | tidak ada penanganan khusus |
| Export dengan `isDouble=true` | Baris hasil ditandai `status_ekspor = true` | efek samping existing `markAsExported` (OQ-5) |

Tidak ada exception baru, tidak ada pesan error baru, tidak ada perubahan pada `RestResponse`.

---

## 12. Testing & Verifikasi

Repositori ini **tidak memiliki suite test otomatis** (hanya `ApiApplicationTests.java` bawaan Spring Initializr). Konsisten dengan fitur-fitur sebelumnya (lihat `tdd/conversation-label.md` §15.1), verifikasi dilakukan **end-to-end terhadap PostgreSQL live** dan didokumentasikan hasilnya.

### 12.1 Data uji

Siapkan pada satu workspace uji:

| Order | `nomor_whatsapp` | Status | Produk | Keterangan |
|---|---|---|---|---|
| A | `628111` | PAID | Serum | pasangan B |
| B | `628111` | UNPAID | Sabun | pasangan A, **produk berbeda** (uji FR-8) |
| C | `628222` | PAID | Serum | tunggal — harus selalu tersaring keluar |
| D | `628333` | UNPAID | Serum | pasangan E |
| E | `628333` | CANCELLED | Serum | uji FR-6 |
| F | `NULL` | PAID | Serum | uji FR-10 |
| G | `''` | PAID | Serum | uji FR-10 |
| H | `08123456789` | PAID | Serum | uji FR-9 — pasangan I tapi format beda |
| I | `628123456789` | PAID | Serum | uji FR-9 |
| X | `628222` | PAID | Serum | **di workspace lain** — uji FR-4 |

### 12.2 Matriks verifikasi REST

| # | Request | Ekspektasi |
|---|---|---|
| 1 | `?workspaceId=W` (tanpa `isDouble`) | Semua order, urut `created_at DESC` — identik sebelum perubahan |
| 2 | `?isDouble=false` | Identik hasil #1 |
| 3 | `?isDouble=true` | A, B, D, E — **tanpa** C, F, G, H, I |
| 4 | `?isDouble=true` | `totalElements = 4` |
| 5 | `?isDouble=true` | A & B berdampingan; D & E berdampingan |
| 6 | `?isDouble=true&status=PAID` | Hanya A (Interpretasi 1) |
| 7 | `?isDouble=true&status=CANCELLED` | Hanya E |
| 8 | `?isDouble=true&limit=2&page=1` lalu `page=2` | Tidak ada baris ganda / terlewat |
| 9 | `?isDouble=true&search=<kode order A>` | Hanya A |
| 10 | `/order/export?isDouble=true` | `.xlsx` berisi tepat A, B, D, E |
| 11 | Login workspace lain, `?isDouble=true` | X tidak muncul di hasil workspace W |

### 12.3 Verifikasi SQL langsung

Kebenaran himpunan duplikat, independen dari layer aplikasi:

```sql
-- Nomor yang seharusnya terdeteksi duplikat pada workspace W
SELECT o.nomor_whatsapp, COUNT(*) AS jumlah
FROM public."order" o
JOIN produk p ON p.id = o.id_produk
WHERE p.id_workspace = :W
  AND COALESCE(TRIM(o.nomor_whatsapp), '') <> ''
GROUP BY o.nomor_whatsapp
HAVING COUNT(*) > 1
ORDER BY jumlah DESC;
```

Jumlah total baris yang seharusnya dikembalikan `isDouble=true` (harus sama dengan `totalElements`):

```sql
SELECT SUM(jumlah) FROM (
    SELECT COUNT(*) AS jumlah
    FROM public."order" o
    JOIN produk p ON p.id = o.id_produk
    WHERE p.id_workspace = :W
      AND COALESCE(TRIM(o.nomor_whatsapp), '') <> ''
    GROUP BY o.nomor_whatsapp
    HAVING COUNT(*) > 1
) t;
```

Mengukur dampak keterbatasan FR-9 pada data produksi — berapa banyak baris berformat lama:

```sql
SELECT COUNT(*) FROM public."order" o
JOIN produk p ON p.id = o.id_produk
WHERE p.id_workspace = :W
  AND o.nomor_whatsapp IS NOT NULL
  AND o.nomor_whatsapp NOT LIKE '62%';
```

> Jalankan query terakhir **sebelum** rilis. Bila hasilnya besar, laporkan ke product owner — keputusan OQ-4 (tanpa backfill) dibuat dengan asumsi jumlahnya kecil, dan angka nyata bisa mengubah keputusan itu.

### 12.4 Verifikasi performa

```sql
EXPLAIN ANALYZE <main query dengan :isDouble = true>;
```

Yang dicek:
- Subquery memakai **Index Scan** pada `idx_order_nomor_whatsapp`, bukan Seq Scan.
- Dengan `:isDouble = false`, SubPlan tidak muncul / tidak dieksekusi pada rencana.
- Waktu eksekusi list halaman pertama sebanding dengan sebelum perubahan saat filter mati.

### 12.5 Regresi wajib

- `GET /order` tanpa `isDouble`: bentuk response, isi, urutan, dan `totalElements` **identik** dengan sebelum perubahan.
- Seluruh kombinasi filter existing (provinsi/kota/kecamatan, status, jenis pembayaran, status ekspor, rentang tanggal order & paid, search) tetap bekerja.
- `GET /order/export` tanpa `isDouble`: isi file sama; **urutan baris kini deterministik** (lihat catatan §5.4).
- `markAsExported` tetap menandai persis baris yang diekspor.

---

## 13. Rencana Implementasi Bertahap

| Fase | Isi | Verifikasi | Status |
|---|---|---|---|
| 1 | Indeks `idx_order_nomor_whatsapp` (§9.3) | `\d public."order"` menampilkan indeks | ⬜ |
| 2 | `OrderRepository.getOrderList` — predikat di main query **dan** countQuery + signature | Query jalan; `isDouble=false` → hasil identik | ⬜ |
| 3 | `OrderService.getOrderList` — normalisasi null + sort kondisional | Matriks §12.2 #1–#9 | ⬜ |
| 4 | `OrderController.getOrderList` — `@RequestParam` | Endpoint list lolos §12.2 #1–#9, #11 | ⬜ |
| 5 | `exportOrder` — repository (predikat + ORDER BY), service, controller | §12.2 #10 | ⬜ |
| 6 | `EXPLAIN ANALYZE` + verifikasi regresi | §12.4, §12.5 | ⬜ |
| 7 | Update `docs/api-reference.md` §6.6 | Param terdokumentasi di kedua endpoint | ⬜ |

Fase 1 aman di-deploy lebih dulu dan berdiri sendiri (indeks tidak mengubah perilaku). Fase 2–4 menyelesaikan endpoint list; fase 5 menambah export. Fitur tidak aktif bagi klien sampai fase 4 karena parameter belum ada di kontrak HTTP.

---

## 14. Dampak & Analisis Regresi

### 14.1 File yang disentuh

| File | Jenis perubahan |
|---|---|
| `repository/OrderRepository.java` | 2 method: predikat baru (main + countQuery), ORDER BY pada export, 2 signature |
| `service/order/OrderService.java` | 2 method: 1 param baru masing-masing, normalisasi null, sort kondisional |
| `controller/OrderController.java` | 2 method: 1 `@RequestParam` masing-masing, 1 argumen pada pemanggilan service |
| `entity/Order.java` | opsional — anotasi `@Index` bila memakai Opsi A (§9.3) |
| `docs/api-reference.md` | dokumentasi param baru |

### 14.2 Yang **tidak** berubah

- `OrderListDto`, `ExportOrderListDto` — bentuk response identik.
- Kolom dan header file Excel.
- Alur `createOrder` / `updateOrder` / event listener / WhatsApp follow-up.
- `GET /order/abandoned`, `GET /order/{id}`, `GET /order/{id}/logs`, `/order/status`.
- Skema tabel (kecuali penambahan indeks, yang tidak mengubah data).

### 14.3 Risiko

| Risiko | Dampak | Mitigasi |
|---|---|---|
| Predikat lupa disalin ke `countQuery` | `totalElements` salah → pagination frontend rusak | Checklist review wajib (§5.2); verifikasi §12.2 #4 |
| Argumen posisional tergeser saat menambah param | Filter salah tanpa error kompilasi (tipe kebetulan cocok) | Param baru ditaruh di **akhir**; periksa satu-satunya pemanggil di controller |
| Indeks tidak terbentuk oleh `ddl-auto=update` | List lambat pada workspace besar | Verifikasi eksplisit §9.3; fallback initializer |
| Data produksi banyak bernomor format lama | Duplikat nyata tidak terdeteksi | Ukur dengan query §12.3 sebelum rilis; eskalasi ke PO bila besar |
| Frontend mengirim `isDouble=false` dari checkbox | Tidak ada — sudah diantisipasi OQ-3 | FR-2 |

---

## 15. Appendix — SQL Final

### 15.1 Predikat duplikat (identik untuk main query, countQuery, dan export)

```sql
AND (
    :isDouble = false
    OR (
        COALESCE(TRIM(ord.nomor_whatsapp), '') <> ''
        AND EXISTS (
            SELECT 1
            FROM public.order o2
            JOIN produk p2 ON o2.id_produk = p2.id
            WHERE p2.id_workspace = :idWorkspace
              AND o2.id <> ord.id
              AND o2.nomor_whatsapp = ord.nomor_whatsapp
        )
    )
)
```

### 15.2 ORDER BY export

```sql
ORDER BY
    CASE WHEN :isDouble = true THEN ord.nomor_whatsapp END ASC,
    ord.created_at DESC
```

### 15.3 Indeks

```sql
CREATE INDEX IF NOT EXISTS idx_order_nomor_whatsapp
    ON public."order" (nomor_whatsapp);
```

### 15.4 Contoh request

```
GET /order?workspaceId=1&page=1&limit=10&isDouble=true
GET /order?workspaceId=1&page=1&limit=10&isDouble=true&status=PAID
GET /order?workspaceId=1&page=1&limit=10&isDouble=false      → sama dengan tanpa param
GET /order/export?workspaceId=1&isDouble=true
```

Response: envelope `RestResponse<Page<OrderListDto>>` tanpa perubahan bentuk.
