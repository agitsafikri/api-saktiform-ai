# PRD — Filter Produk (`idProduk`) pada List & Export Pesanan

| Field | Value |
|---|---|
| Feature name | Filter Produk pada Order List |
| Component | `GET /order` dan `GET /order/export` → `OrderController` → `OrderListFilterParser` → `OrderService` → `OrderRepository` |
| Status | **Implemented** |
| Scope | Workspace-scoped (per tenant) |
| Last updated | 2026-11-22 |

Melanjutkan [PRD — Filter Multiple](./order-multi-filter.md): satu filter multi-nilai baru, `idProduk`, mengikuti pola dan aturan validasi yang sama. Sisi dashboard: [PRD Frontend](./order-product-filter-frontend.md).

---

## 1. Keputusan

| # | Pertanyaan | Keputusan |
|---|---|---|
| D-1 | Satu produk atau multi-nilai? | **Multi-nilai**, UUID dipisah koma — konsisten dengan filter lain. |
| D-2 | Batas jumlah nilai? | **100** (bukan 500). UUID 36 karakter; 100 nilai ≈ 3,7 KB sehingga URL tetap di bawah batas header Tomcat (±8 KB). |
| D-3 | Export? | Ikut mendukung `idProduk` dengan semantik identik. Efek `markAsExported` hanya menandai baris hasil filter — diterima; frontend mengirim ID produk yang dicentang. |
| D-4 | Produk milik workspace lain / tidak ada? | **Bukan error** — tidak cocok dengan order mana pun (hasil kosong). Query sudah dibatasi `prod.id_workspace`. |

## 2. Kontrak API

```
GET /order?workspaceId=1&page=1&limit=10&idProduk=<uuid1>,<uuid2>
GET /order/export?workspaceId=1&idProduk=<uuid1>
```

| Param | Type | Required | Keterangan |
|---|---|---|---|
| `idProduk` | `String` (UUID dipisah koma) | Tidak | Hanya order dengan `order.id_produk` salah satu nilai. Nilai dalam param di-OR, antar-param di-AND. |

Sumber pilihan produk di frontend: `GET /produk/list-dropdown?workspaceId=&search=&page=&limit=` — mendukung pencarian nama dan paginasi.

### Dropdown produk: search & paginasi

`GET /produk/list-dropdown` mendapat tiga param opsional:

| Param | Keterangan |
|---|---|
| `search` | Nama produk mengandung kata kunci, tidak peka huruf; di-trim. Kosong = semua. |
| `page` | 1-based. **Bila dikirim**, respons berupa Spring `Page`; bila tidak, respons tetap array penuh (bentuk lama) agar `ModalEditPesanan.vue` tidak rusak. |
| `limit` | Default 10, maks 100. `page < 1` atau `limit` di luar 1..100 → `400`. |

Urutan nama produk lalu `id` (stabil antar-halaman). Produk terhapus dan produk workspace lain tidak pernah muncul.

## 3. Validasi

| Input | Hasil |
|---|---|
| null / kosong / hanya spasi / `,,` | tanpa filter |
| Spasi, token kosong, duplikat | dibuang; huruf besar/kecil dinormalisasi |
| Token bukan UUID kanonik `8-4-4-4-12` | `400`, `field=idProduk`, `code=INVALID_FILTER_VALUE`, token disebut dalam pesan |
| > 100 nilai unik | `400`, `field=idProduk`, `code=FILTER_TOO_MANY_VALUES` |

Error dikumpulkan bersama pelanggaran filter lain dalam satu respons `data: [ErrorDto]`.

## 4. Implementasi

- `OrderListFilter` — komponen `Set<UUID> idProduk` + `idProdukCsv()`.
- `OrderListFilterParser.parse(..., idProduk)` — `parseUuids` dengan regex kanonik (karena `UUID.fromString` menerima bentuk longgar seperti `1-1-1-1-1`), batas `MAX_PRODUK_VALUES = 100`.
- `OrderRepository.ORDER_LIST_FILTER` (dipakai data, count, dan export):
  ```sql
  AND (:idProduk = '' OR ord.id_produk = ANY(CAST(string_to_array(:idProduk, ',') AS uuid[])))
  ```
- Tidak ada perubahan skema. Index `idx_order_produk_created_at (id_produk, created_at DESC)` sudah menopang filter ini.

## 5. Acceptance Criteria

- `idProduk` kosong → hasil sama dengan sebelum fitur ini.
- `idProduk=A` → hanya order produk A; `idProduk=A,B` → order produk A atau B.
- Non-owner tetap tidak melihat order `DELETE`.
- `idProduk` milik workspace lain → kosong; dicampur dengan produk sendiri → hanya order produk sendiri.
- Kombinasi dengan provinsi/status/pembayaran/tanggal/search/`isDouble` → AND.
- Jumlah baris export = `totalElements` list untuk filter yang sama.

Diverifikasi oleh `OrderListFilterParserTest` dan `OrderMultiFilterIntegrationTest`.
