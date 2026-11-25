# PRD — Filter Multiple pada List & Export Order (Backend)

| Field | Value |
|---|---|
| Feature name | Filter multiple untuk wilayah, status, dan jenis pembayaran |
| Component | `GET /order` dan `GET /order/export` → `OrderController` → `OrderService` → `OrderRepository` |
| Dokumen terkait | [PRD Frontend](./order-multi-filter-frontend.md), [PRD Duplicate Order Filter](./order-double-filter.md), [API Reference](../api-reference.md) |
| Status | **Implemented** (backend) — lihat §13 |
| Scope | Workspace-scoped (per tenant) |
| Author | — |
| Last updated | 2026-11-15 |

> Keputusan owner dicatat di **§3**. Implementasi backend dicatat di **§13**. Kebutuhan UI ada di [PRD Frontend](./order-multi-filter-frontend.md).

---

## 1. Latar Belakang

`GET /order` (list, paginated) dan `GET /order/export` (Excel) memakai satu blok filter yang sama, `ORDER_LIST_FILTER` (`OrderRepository.java:30-70`). Saat ini setiap filter di bawah ini hanya menerima **satu nilai**:

| Param | Tipe di controller | Pola query sekarang |
|---|---|---|
| `idProvinsi` | `Integer` | `(:idProvinsi IS NULL OR ord.id_provinsi = :idProvinsi)` |
| `idKota` | `Integer` | `(:idKota IS NULL OR ord.id_kota = :idKota)` |
| `idKecamatan` | `Integer` | `(:idKecamatan IS NULL OR ord.id_kecamatan = :idKecamatan)` |
| `status` | `OrderStatus` | `(:status IS NULL OR ord.status = :status)` |
| `jenisPembayaran` | `String` | `(:jenisPembayaran IS NULL OR ord.pembayaran = :jenisPembayaran)` |

Lokasi kode: `OrderController.java:47-80` (list) dan `:82-116` (export), `OrderService.getOrderList` dan `OrderService.exportOrder`.

Catatan perilaku saat ini:

- Project tidak memiliki `@ControllerAdvice`. Nilai `status` yang tidak dikenal gagal saat konversi parameter, dan respons yang keluar adalah error bawaan Spring, **bukan** format `RestResponse`.
- `jenisPembayaran` tidak divalidasi; nilai yang salah hanya menghasilkan list kosong.

---

## 2. Masalah

Tim operasional perlu melihat atau mengekspor order dari beberapa wilayah, status, atau metode pembayaran sekaligus. Contohnya "semua order UNPAID dan PENDING di Jawa Barat dan Jawa Tengah". Saat ini hal itu hanya bisa dilakukan dengan beberapa kali filter atau export, lalu digabung manual.

---

## 3. Keputusan Owner

| # | Pertanyaan | Keputusan |
|---|---|---|
| D-1 | Filter mana yang dibuat multiple? | `idProvinsi`, `idKota`, `idKecamatan`, `status`, `jenisPembayaran`, pada **list dan export**. |
| D-2 | Arti beberapa nilai | **OR di dalam satu filter, AND antar filter.** |
| D-3 | Filter lokasi bertingkat | Bila **≥2 provinsi** dipilih, kota dan kecamatan tidak boleh diisi. Bila **≥2 kota** dipilih, kecamatan tidak boleh diisi. Ditegakkan di frontend (dinonaktifkan) **dan** backend. |
| D-4 | Kombinasi lokasi yang melanggar D-3 | **Ditolak dengan 400.** |
| D-5 | Format parameter | **Dipisah koma**: `?status=PAID,UNPAID`. |

---

## 4. Goals & Non-Goals

### Goals

- Kelima filter menerima satu atau lebih nilai dalam format koma.
- Perilaku sama persis untuk list dan export.
- **Kompatibel dengan frontend lama:** nilai tunggal (`?status=PAID`) dan param kosong/tidak dikirim tetap bekerja seperti sekarang.
- Input tidak valid ditolak dengan 400 dalam format `RestResponse` + `ErrorDto` yang konsisten.

### Non-Goals

- Filter multiple untuk param lain (tanggal, `statusEkspor`, `search`, `isDouble`).
- Filter wilayah "bertingkat campuran" (misalnya Jabar → hanya Bandung, Jateng → seluruh provinsi). Dicegah oleh D-3.
- Validasi bahwa kota benar-benar berada di provinsi yang dipilih. Dengan AND, kombinasi yang tidak cocok cukup menghasilkan list kosong.
- Filter pada dashboard, laporan, atau abandoned order.
- Memindahkan endpoint ke POST.

---

## 5. Kontrak API

Berlaku untuk `GET /order` dan `GET /order/export`. Parameter lain tidak berubah.

### 5.1 Parameter

| Param | Isi | Nilai yang diterima |
|---|---|---|
| `idProvinsi` | daftar ID provinsi, dipisah koma | bilangan bulat positif |
| `idKota` | daftar ID kota, dipisah koma | bilangan bulat positif |
| `idKecamatan` | daftar ID kecamatan, dipisah koma | bilangan bulat positif |
| `status` | daftar status, dipisah koma | `UNPAID`, `PAID`, `CANCELLED`, `PENDING`, `DELETE` (enum `OrderStatus`) |
| `jenisPembayaran` | daftar metode, dipisah koma | `COD`, `BANK_TRANSFER` (enum `JenisPembayaran`) |

### 5.2 Aturan parsing

| Aturan | Contoh | Hasil |
|---|---|---|
| Param tidak dikirim atau kosong berarti tanpa filter | `?status=` | semua status |
| Spasi di sekitar nilai diabaikan | `?status=PAID, UNPAID` | `PAID`, `UNPAID` |
| Token kosong diabaikan | `?idKota=1,,2,` | `1`, `2` |
| Nilai duplikat digabung | `?status=PAID,PAID` | `PAID` |
| Enum tidak peka huruf besar/kecil | `?status=paid` | `PAID` |
| Nilai tidak valid ditolak | `?idKota=1,abc` / `?status=LUNAS` | 400 (§6) |
| Maksimal **500 nilai** per param | 501 ID kecamatan | 400 (§6) |

Batas 500 nilai menjaga URL tetap di bawah batas header Tomcat (±8 KB).

### 5.3 Arti filter

```
(provinsi ∈ idProvinsi    bila diisi)
AND (kota ∈ idKota        bila diisi)
AND (kecamatan ∈ idKecamatan bila diisi)
AND (status ∈ status      bila diisi)
AND (pembayaran ∈ jenisPembayaran bila diisi)
AND (filter lain yang sudah ada)
```

Aturan visibilitas DELETE yang sudah ada tetap berlaku: non-owner tidak pernah melihat order DELETE (`:isOwner = TRUE OR ord.status <> 'DELETE'`). Non-owner yang memfilter `status=DELETE` mendapat list kosong, sama seperti sekarang.

### 5.4 Contoh

```
GET /order?workspaceId=1&page=1&limit=10&status=UNPAID,PENDING&idProvinsi=32,33
GET /order?workspaceId=1&page=1&limit=10&idProvinsi=32&idKota=3273,3204&jenisPembayaran=COD
GET /order/export?workspaceId=1&idProvinsi=32&idKota=3273&idKecamatan=327301,327302
```

---

## 6. Validasi & Error

Semua pelanggaran dikumpulkan dan dikembalikan sekaligus, bukan hanya yang pertama.

### 6.1 Aturan

| # | Kondisi | `field` | `code` |
|---|---|---|---|
| V-1 | Nilai bukan bilangan bulat positif | `idProvinsi` / `idKota` / `idKecamatan` | `INVALID_FILTER_VALUE` |
| V-2 | Nilai bukan anggota enum | `status` / `jenisPembayaran` | `INVALID_FILTER_VALUE` |
| V-3 | Lebih dari 500 nilai | nama param | `FILTER_TOO_MANY_VALUES` |
| V-4 | `idProvinsi` ≥2 nilai **dan** `idKota` diisi | `idKota` | `FILTER_LOCATION_CONFLICT` |
| V-5 | `idProvinsi` ≥2 nilai **dan** `idKecamatan` diisi | `idKecamatan` | `FILTER_LOCATION_CONFLICT` |
| V-6 | `idKota` ≥2 nilai **dan** `idKecamatan` diisi | `idKecamatan` | `FILTER_LOCATION_CONFLICT` |

Jumlah nilai dihitung **setelah** parsing (spasi, token kosong, dan duplikat sudah dibuang).

Kombinasi berikut **tidak** ditolak, karena tidak ambigu:

- Kota tanpa provinsi.
- Kecamatan tanpa kota.
- 1 provinsi + ≥2 kota.
- 1 kota + ≥2 kecamatan.

### 6.2 Format respons

Sama dengan pola `ValidationException` yang sudah ada (`POST /produk`):

```json
{
  "success": false,
  "message": "Kota tidak dapat difilter bila lebih dari satu provinsi dipilih.",
  "data": [
    { "field": "idKota", "code": "FILTER_LOCATION_CONFLICT",
      "message": "Kota tidak dapat difilter bila lebih dari satu provinsi dipilih." }
  ]
}
```

Untuk `GET /order/export`, respons 400 tetap berupa JSON di atas, bukan file. Frontend perlu membaca body error dari `Blob` (lihat PRD Frontend §8).

---

## 7. Desain Teknis

### 7.1 Controller

Kelima param diterima sebagai **`String`** (`required = false`) dan di-parse sendiri, **bukan** `List<Integer>` / `List<OrderStatus>`. Alasannya:

- Tanpa `@ControllerAdvice`, kegagalan konversi tipe di Spring menghasilkan error bawaan, bukan `RestResponse` + `ErrorDto` (§1).
- Parsing sendiri memberi kontrol penuh atas aturan §5.2 dan pesan error §6.

Controller menangkap `ValidationException` sebelum `catch (Exception)`, lalu mengembalikan 400 dengan `data = e.getErrors()`. Pola ini sama dengan `ProdukController.addProduk`.

### 7.2 Parser filter

Kelas baru, misalnya `OrderListFilterParser`, mengubah lima string mentah menjadi value object `OrderListFilter`:

```
OrderListFilter {
  Set<Integer> idProvinsi, idKota, idKecamatan
  Set<String>  status, jenisPembayaran     // sudah dinormalisasi ke UPPERCASE
}
```

Parser menjalankan aturan §5.2 dan §6.1, lalu melempar satu `ValidationException` berisi semua pelanggaran. Parser murni tanpa akses database, sehingga mudah di-unit-test.

### 7.3 Service

`getOrderList` dan `exportOrder` menerima `OrderListFilter` menggantikan lima param tunggal. Untuk query, setiap set diubah menjadi string koma (`"32,33"`), dan set kosong menjadi `""`.

### 7.4 Query

`ORDER_LIST_FILTER` memakai array PostgreSQL. Polanya sama dengan filter `orderCode` yang sudah ada (`OrderRepository.java:53`):

```sql
AND (:idProvinsi = ''      OR ord.id_provinsi  = ANY(CAST(string_to_array(:idProvinsi, ',')  AS int[])))
AND (:idKota = ''          OR ord.id_kota      = ANY(CAST(string_to_array(:idKota, ',')      AS int[])))
AND (:idKecamatan = ''     OR ord.id_kecamatan = ANY(CAST(string_to_array(:idKecamatan, ',') AS int[])))
AND (:status = ''          OR ord.status       = ANY(string_to_array(:status, ',')))
AND (:jenisPembayaran = '' OR ord.pembayaran   = ANY(string_to_array(:jenisPembayaran, ',')))
```

Alasan tidak memakai `IN (:list)` pada native query PostgreSQL:

- List kosong menghasilkan `IN ()`, yang error sintaks.
- `:list IS NULL` gagal bila list berisi lebih dari satu elemen.
- Jumlah placeholder ikut berubah sesuai panjang list, sehingga query plan tidak dapat dipakai ulang.

Isi string sudah divalidasi parser (hanya angka atau enum), sehingga `CAST(... AS int[])` tidak bisa gagal karena input pengguna.

Signature `getOrderList` dan `exportOrder` di repository: tipe kelima param berubah dari `Integer`/`String` tunggal menjadi `String` (daftar koma).

### 7.5 Performa

- `ord.status = ANY(...)` tetap dapat memakai index `idx_order_status_paid_at`.
- Kolom wilayah tidak memiliki index, tetapi baris sudah dipersempit oleh filter workspace (`produk.id_workspace`). Biayanya setara dengan filter tunggal sekarang.
- Count query (`countQuery`) memakai blok filter yang sama, sehingga total halaman tetap konsisten.

---

## 8. Kompatibilitas

| Permintaan | Sebelum | Sesudah |
|---|---|---|
| `?status=PAID` | filter PAID | sama |
| `?status=` / tidak dikirim | tanpa filter | sama |
| `?idProvinsi=32` | filter provinsi 32 | sama |
| `?status=LUNAS` | error bawaan Spring (bukan `RestResponse`) | 400 `RestResponse` + `INVALID_FILTER_VALUE` |
| `?jenisPembayaran=XYZ` | list kosong | **400** `INVALID_FILTER_VALUE` |
| `?idProvinsi[]=32&idProvinsi[]=33` (default axios) | param diabaikan | param **tetap diabaikan**, tanpa error. Frontend wajib mengirim format koma. |

---

## 9. Yang Tidak Terdampak

- Filter lain: tanggal, `statusEkspor`, `search` / `searchBy`, `isDouble`.
- Urutan data dan pagination.
- Format file export dan kolomnya.
- `markAsExported` (tetap menandai semua baris hasil export).
- Dashboard, laporan, dan counter produk.

---

## 10. Rencana Test

### Unit test parser

- Nilai tunggal, banyak nilai, spasi, token kosong, duplikat, dan huruf kecil pada enum.
- Param null atau kosong menghasilkan set kosong.
- V-1 sampai V-6, termasuk beberapa pelanggaran sekaligus dalam satu respons.
- Batas tepat 500 (lolos) dan 501 (ditolak).
- Kombinasi yang diizinkan (§6.1): tidak ditolak.

### Integration test (Testcontainers)

- Data: order di beberapa provinsi, kota, dan kecamatan, dengan status dan metode pembayaran yang beragam.
- Setiap filter multiple mengembalikan gabungan (OR) yang benar.
- Kombinasi antar filter (AND).
- Nilai tunggal memberi hasil yang sama dengan perilaku lama.
- `totalElements` pada list sama dengan jumlah baris export untuk filter yang sama.
- Non-owner dengan `status=DELETE,PAID`: hanya PAID yang muncul.
- Isolasi workspace tetap terjaga.

### Controller

- 400 dengan `data: [ErrorDto]` untuk V-1 sampai V-6, pada list dan export.

---

## 11. Dokumentasi yang Diperbarui

- `docs/api-reference.md`: param baru, format koma, kode error.
- `docs/frontend/api-mapping.md`: `GET /order` dan `GET /order/export`.
- `docs/prd/order-double-filter.md` §1: tabel param yang sudah ada kini mendukung banyak nilai.

---

## 12. Rollout

1. Deploy backend. Frontend lama tetap berfungsi (§8).
2. Deploy frontend sesuai [PRD Frontend](./order-multi-filter-frontend.md).
3. Tidak ada migrasi data atau perubahan skema.

---

## 13. Implementasi

| Bagian | Lokasi |
|---|---|
| Value object filter | `model/Order/OrderListFilter.java` (`idProvinsiCsv()`, …, `none()`) |
| Kode galat | `model/Order/OrderFilterErrorCode.java` |
| Parser & validasi (§5.2, §6) | `service/order/OrderListFilterParser.java` (`MAX_VALUES = 500`) |
| Controller | `OrderController.getOrderList` / `exportOrder`: param `String`, `catch (ValidationException)` → `filterValidationError` (400, `Content-Type: application/json`) |
| Service | `OrderService.getOrderList` / `exportOrder` menerima `OrderListFilter` |
| Query | `OrderRepository.ORDER_LIST_FILTER` (`= ANY(string_to_array(...))`) |
| Unit test parser | `OrderListFilterParserTest` |
| Integration test | `OrderMultiFilterIntegrationTest` (Testcontainers) |
| Test kontrak HTTP | `OrderControllerFilterTest` (MockMvc standalone) |

Catatan:

- Karena param diterima sebagai `String`, Spring menggabungkan param yang diulang (`?status=A&status=B`) menjadi `"A,B"`. Format diulang ikut berfungsi, tetapi format resmi tetap koma (D-5).
- Bila provinsi ≥2 **dan** kota ≥2 sekaligus diisi bersama kecamatan, hanya satu error `FILTER_LOCATION_CONFLICT` yang dilaporkan untuk `idKecamatan`, agar tidak ada dua pesan untuk field yang sama.
