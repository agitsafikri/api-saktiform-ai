# PRD — Biaya COD (Persentase untuk Kurir)

| Field | Value |
|---|---|
| Feature name | Biaya COD berbasis persentase |
| Component | `ProdukPembayaran.config` → `OrderService` (create / update) → `OrderOrchestrationService` → export order |
| Status | **Implemented** — lihat §14 |
| Dokumen terkait | [PRD Frontend — Konfigurasi Biaya COD](./cod-fee-frontend.md) |
| Scope | Per produk (konfigurasi), per order (hasil hitung) |
| Author | — |
| Last updated | 2026-11-15 |

> Semua keputusan owner dicatat di **§3**. Implementasi dan penyimpangan kecil dari draft dicatat di **§14**.

---

## 1. Latar Belakang

Setiap produk dapat memiliki konfigurasi metode pembayaran COD di `ProdukPembayaran.config` (JSON bebas). Untuk biaya COD, config memuat:

| Key | Arti |
|---|---|
| `tipeBiaya` | Dasar perhitungan: `hanya-produk` atau `produk-pengiriman` |
| `persentaseBiaya` | Persentase biaya |
| `minimalBiaya` | Batas bawah nominal biaya |
| `maksimalBiaya` | Batas atas nominal biaya |

Logika perhitungannya ada di dua tempat yang ditulis terpisah:

- `OrderService.createOrderInternal` (`OrderService.java:146-180`)
- `OrderService.updateOrder` (`OrderService.java:267-301`)

Hasilnya ditambahkan ke `order.ongkosKirim`.

---

## 2. Masalah Saat Ini

### 2.1 Fitur tidak pernah berjalan

Kedua tempat memakai syarat yang terbalik:

```java
if (tipeBiaya != null && minimalBiaya != null && maksimalBiaya != null && persentaseBiaya == null)
```

| Kondisi config produk | Akibat sekarang |
|---|---|
| Lengkap (tipe, persen, min, maks) | Biaya **tidak pernah ditambahkan**, tanpa error. |
| Persen kosong, tipe/min/maks terisi | `NullPointerException`: checkout dan edit order gagal. |
| Ada key yang kosong | Diabaikan, tanpa biaya. |
| `config` null | Checkout aman; **edit order NPE** (`getConfig().isEmpty()`, tercatat sebagai T-6 di `runtime-error-hardening.md`). |

Karena biaya tidak pernah ditambahkan, **seluruh `ongkos_kirim` yang sudah ada di database adalah tarif ongkir murni**. Tidak ada data yang perlu dikoreksi.

### 2.2 Masalah lain yang muncul kalau syaratnya sekadar dibalik

1. **Saat create, harga belum terisi.** Biaya dihitung dari `order.getHarga()` (`:159`), padahal harga baru di-set di `:188`, sehingga terjadi NPE.
2. **Diskon tidak konsisten.** Saat create, diskon diabaikan. Saat update, diskon dikurangkan. Order dari agen (`createOrderOnChat`) baru mengisi diskon **setelah** `createOrderInternal`.
3. **Update memakai harga lama.** Biaya dihitung (`:280`) sebelum harga varian baru di-set (`:306`).
4. **Update memakai config produk terbaru**, bukan snapshot order. Perubahan setting produk akan mengubah tagihan order lama yang diedit.
5. **Tidak ada penanda order lama.** Setiap edit menghitung ulang ongkir dari tabel tarif lalu menambahkan biaya. Tanpa penanda, order COD lama akan terkena biaya saat diedit.
6. **Parsing rapuh.** `Integer.valueOf("2500.0")` dan `Double.valueOf("2,5")` melempar exception. Config juga tidak divalidasi saat disimpan.
7. **Pembulatan.** `longValue()` selalu memotong ke bawah.

---

## 3. Keputusan Owner

| # | Pertanyaan | Keputusan |
|---|---|---|
| D-1 | Siapa yang menanggung biaya COD? | **Ditambahkan ke tagihan pelanggan.** |
| D-2 | Dasar harga produk: sebelum atau sesudah diskon? | **Sesudah diskon** (`harga − diskon`). |
| D-3 | Order COD lama (sebelum fitur ini) | **Dibiarkan**, tidak dihitung ulang dan tidak diberi biaya. |
| D-4 | Tipe biaya tambahan | **Tambah satu tipe: persentase terhadap ongkir saja.** Nama tipe: `hanya-pengiriman`. |
| D-5 | Pembulatan | **Ke atas ke kelipatan Rp100** (contoh: 4.127,5 → 4.200). |
| D-6 | Penyimpanan biaya | **Digabung ke `ongkos_kirim`**, sehingga `{total}`, template pesan, dan rumus total tidak perlu diubah. Ditambah kolom `biaya_cod` sebagai catatan dan penanda (§6). |
| D-7 | Template pesan | **Tidak diubah.** Nilai variabel diisi ulang dari data order setiap kali pesan dikirim (`MessageConstructorHelper.buildOrderParams`), sehingga `{ongkir}` dan `{total}` otomatis sudah termasuk biaya COD. |
| D-8 | Export Excel | **Tidak ada kolom `cod_fee`**; format kolom export tetap. `original_shipping_cost` = `ongkos_kirim − biaya_cod`. |
| D-9 | `minimalBiaya` / `maksimalBiaya` wajib? | **Opsional.** Bila tidak diisi, batas tersebut tidak diterapkan. |
| D-10 | Estimasi biaya COD di halaman checkout publik | **Dibiarkan.** Pelanggan melihat biaya (dalam angka ongkir) di pesan WhatsApp. |
| D-11 | Total pesan memakai harga varian saat ini, bukan `order.harga` | **Diperbaiki sekalian** (§9.1). |

---

## 4. Goals & Non-Goals

### Goals

- Biaya COD benar-benar ditagihkan sesuai config produk.
- Tiga tipe dasar perhitungan (§5.1), dihitung dari satu fungsi yang sama untuk create dan update.
- Biaya masuk ke `ongkos_kirim`. Nominalnya juga dicatat di `biaya_cod`, sehingga tarif ongkir murni selalu bisa diturunkan.
- Order lama tidak berubah tagihannya.
- Config divalidasi saat produk disimpan.
- Harga dan total di pesan WhatsApp memakai harga yang tersimpan di order (D-11).

### Non-Goals

- Rincian "Biaya COD" terpisah di pesan WhatsApp atau detail order (D-6, D-7).
- Menampilkan estimasi biaya COD di halaman checkout publik (D-10).
- Rekonsiliasi dengan tagihan kurir.
- Mengubah laporan pendapatan, dashboard-matrix, dashboard-order, atau counter produk.

---

## 5. Aturan Perhitungan

### 5.1 Tipe biaya

Notasi:

- `harga` = `order.harga` (harga varian)
- `diskon` = `order.diskon` (null dianggap 0)
- `tarifOngkir` = **tarif ongkir murni** dari tabel ongkir, **tanpa** biaya COD (§6.2)

| `tipeBiaya` | Dasar perhitungan | Status |
|---|---|---|
| `hanya-produk` | `harga − diskon` | ada, dasar diubah (D-2) |
| `produk-pengiriman` | `harga − diskon + tarifOngkir` | ada, dasar diubah (D-2) |
| `hanya-pengiriman` | `tarifOngkir` | **baru** (D-4) |

`tipeBiaya` dicocokkan case-insensitive. Nilai lain dianggap config tidak valid (§8).

### 5.2 Rumus

```
dasar       = sesuai tipeBiaya (§5.1); bila < 0 → 0
biayaMentah = dasar × persentaseBiaya / 100
biaya       = bulatkan ke atas ke kelipatan 100(biayaMentah)   // D-5
biaya       = max(biaya, minimalBiaya)   bila minimalBiaya ada    // D-9: opsional
biaya       = min(biaya, maksimalBiaya)  bila maksimalBiaya ada   // D-9: opsional
```

- Perhitungan memakai `BigDecimal`, bukan `Double`, agar tidak ada galat floating point.
- Pembulatan dilakukan **sebelum** batas min/maks, sehingga nominal min/maks dari owner selalu dihormati persis. Akibatnya, bila `maksimalBiaya` bukan kelipatan 100 (misalnya 4.150), hasil yang terkena batas maks juga bukan kelipatan 100.

### 5.3 Kapan biaya = 0

- Metode pembayaran bukan COD.
- `config` null, kosong, atau tidak memiliki `tipeBiaya` / `persentaseBiaya`.
- `persentaseBiaya` = 0.

### 5.4 Contoh

Harga 100.000, diskon 10.000, tarif ongkir 20.000, persen 3%, min 2.000, maks 5.000:

| Tipe | Dasar | Mentah | Dibulatkan | Setelah min/maks | `ongkos_kirim` disimpan |
|---|---|---|---|---|---|
| `hanya-produk` | 90.000 | 2.700 | 2.700 | **2.700** | 22.700 |
| `produk-pengiriman` | 110.000 | 3.300 | 3.300 | **3.300** | 23.300 |
| `hanya-pengiriman` | 20.000 | 600 | 600 | **2.000** (minimal) | 22.000 |

Contoh pembulatan: harga 137.583, diskon 0, `hanya-produk` 3% → mentah 4.127,49 → **4.200**.

Total tagihan untuk baris pertama: 100.000 − 10.000 + 22.700 = **112.700**. Rumus total yang sudah ada tidak berubah.

---

## 6. Model Data

### 6.1 Kolom

| Kolom | Isi |
|---|---|
| `ongkos_kirim` (sudah ada) | `tarifOngkir + biaya_cod` |
| `biaya_cod` (**baru**, `BIGINT`, nullable) | Nominal biaya COD yang sudah termasuk di `ongkos_kirim` |

Arti nilai `biaya_cod`:

| Nilai | Arti |
|---|---|
| `NULL` | Order dibuat **sebelum** fitur ini. Tidak pernah dihitung (D-3). |
| `0` | Order baru tanpa biaya COD (transfer, atau COD tanpa config biaya). |
| `> 0` | Biaya COD yang ditagihkan. |

Kolom ditambahkan otomatis oleh `ddl-auto=update`. Semua baris lama otomatis bernilai `NULL`, sehingga D-3 terpenuhi **tanpa migrasi data**.

### 6.2 Tarif ongkir murni

```
tarifOngkir = ongkos_kirim − COALESCE(biaya_cod, 0)
```

Dipakai di:

- Perhitungan ulang biaya (§7), supaya biaya COD tidak terhitung dua kali.
- Kolom export `original_shipping_cost` (§9).

### 6.3 Snapshot config

`order.config_pembayaran` (sudah ada) tetap menjadi snapshot config pada saat order dibuat, dan menjadi sumber perhitungan saat order diedit (§7.3).

---

## 7. Titik Perhitungan

Semua titik memanggil **satu** fungsi kalkulator yang murni tanpa akses database (mudah di-unit-test), misalnya `CodFeeCalculator.calculate(pembayaran, config, harga, diskon, tarifOngkir)`. Setelahnya selalu diterapkan:

```
order.biayaCod    = biaya
order.ongkosKirim = tarifOngkir + biaya
```

### 7.1 `OrderService.createOrderInternal` (checkout form, abandoned, chat)

- Dihitung **setelah** harga varian dan tarif ongkir di-set (memperbaiki §2.2-1).
- Diskon pada titik ini = 0, karena checkout publik tidak memiliki diskon.
- `biaya_cod` selalu terisi (minimal 0).

### 7.2 `OrderOrchestrationService.createOrderOnChat` (order oleh agen)

- Agen mengisi diskon **setelah** `createOrderInternal`, sehingga biaya **dihitung ulang** setelah `order.setDiskon(...)`.
- Memakai `tarifOngkir = ongkos_kirim − biaya_cod` (§6.2) dan snapshot config order.

### 7.3 `OrderService.updateOrder` (edit order)

Tarif ongkir sudah dihitung ulang dari tabel tarif di awal update (`OrderService.java:257`), sehingga nilainya selalu murni.

| # | Kondisi | Perlakuan |
|---|---|---|
| 1 | `order.biayaCod == null` (order lama) | **Tidak dihitung.** `biaya_cod` tetap `NULL`; `ongkos_kirim` = tarif ongkir saja. Berlaku juga bila metode pembayaran diganti ke COD (D-3). |
| 2 | Metode pembayaran **dan produk** tidak berubah | Hitung ulang dengan **snapshot** `order.configPembayaran`, memakai harga, diskon, dan tarif ongkir **baru**. |
| 3 | Metode pembayaran **atau produk** berubah | Ambil config produk saat ini, simpan sebagai snapshot baru, lalu hitung. Snapshot produk lama tidak relevan untuk produk baru. |

Perhitungan dilakukan **setelah** harga varian baru, diskon, dan tarif ongkir di-set (memperbaiki §2.2-3).

> Catatan perilaku saat ini: `updateOrder` selalu menimpa `configPembayaran` dengan config produk terbaru (`OrderService.java:303`). Baris #2 mengubah perilaku ini agar perubahan setting produk tidak mengubah tagihan order yang sudah ada.

`ProdukPembayaran` yang tidak ditemukan atau `config` null tidak lagi menyebabkan NPE; keduanya dianggap biaya 0 (sekaligus menutup T-6).

### 7.4 `OrderService.updateOrderStatus` (bulk status)

Tidak menghitung ulang. Perubahan status tidak memengaruhi biaya COD.

---

## 8. Validasi Config Saat Produk Disimpan

Di `ProdukService` (`:239-250`), untuk entri pembayaran `COD` yang memiliki config biaya:

| Aturan | Error |
|---|---|
| `tipeBiaya` ∈ {`hanya-produk`, `produk-pengiriman`, `hanya-pengiriman`} | `tipeBiaya` tidak valid |
| `persentaseBiaya` angka, `0 ≤ x ≤ 100` | persentase tidak valid |
| `minimalBiaya`, `maksimalBiaya` (bila ada) angka bulat `≥ 0` | nominal tidak valid |
| `minimalBiaya ≤ maksimalBiaya` (bila keduanya ada) | min > maks |

Error dikembalikan dengan format `ErrorResponse` / `ErrorDto` yang sudah ada.

Parsing angka bersifat toleran: menerima `Number` JSON maupun string numerik (`"2500"`, `"2500.0"`, `"2.5"`).

Config produk yang **sudah tersimpan** dan tidak valid tidak membuat checkout gagal. Saat dihitung, config tersebut dianggap biaya 0 dan dicatat di log sebagai warning.

---

## 9. Dampak ke Tampilan & Output

| Lokasi | Perubahan |
|---|---|
| Pesan WhatsApp (`MessageConstructorHelper`) | Untuk biaya COD tidak ada perubahan: `{ongkir}` menampilkan tarif + biaya COD dan `{total}` otomatis benar (D-7). Sumber harga diperbaiki (§9.1). |
| Detail order (`DetailOrderDto.ongkir`) | Tidak ada perubahan kode; `ongkir` sudah termasuk biaya COD. |
| Export Excel (`generateUserExcel`) | Format kolom tetap, tanpa `cod_fee` (D-8). |
| Export `shipping_cost` (kolom 16) | `ongkos_kirim` (sudah termasuk biaya COD). Tidak ada perubahan kode. |
| Export `gross_revenue` (kolom 17) | Rumus tetap (`harga − diskon + ongkos_kirim`); otomatis sudah termasuk biaya COD. |
| Export `net_revenue` (kolom 18) | Tetap `harga − diskon`. |
| Export `original_shipping_cost` (kolom 25) | **Berubah** menjadi `ongkos_kirim − COALESCE(biaya_cod, 0)` (tarif ongkir murni). |
| Query export (`OrderRepository.java:126-142`) | Tambah `ord.biaya_cod AS biayaCod`. |
| `docs/api-reference.md`, `docs/frontend/api-mapping.md` | Dokumentasikan tipe `hanya-pengiriman`, aturan validasi, dan arti `ongkir` yang sudah termasuk biaya COD. |
| Frontend admin (form produk) | Tambah opsi `hanya-pengiriman`; tampilkan error validasi. |

### 9.1 Perbaikan sumber harga di pesan WhatsApp (D-11)

Saat ini `MessageConstructorHelper.buildOrderParams` (`:181`, `:204`) memakai `atributProduk.getHarga()`, yaitu harga varian **saat ini**, untuk `{harga_produk}` dan `{total}`. Kalau harga varian diubah setelah order dibuat, angka di pesan berbeda dengan tagihan order.

Perubahan:

| Variabel | Sekarang | Menjadi |
|---|---|---|
| `{harga_produk}` | `atributProduk.getHarga()` | `order.getHarga()` |
| `{total}` | `atributProduk.getHarga() + ongkos_kirim − diskon` | `order.getHarga() + ongkos_kirim − diskon` |

Bila `order.harga` null (data sangat lama), dipakai `atributProduk.getHarga()` sebagai fallback. `{atribut_produk}` dan `{berat_produk}` tidak berubah.

---

## 10. Yang Tidak Terdampak

- Laporan pendapatan (`SUM(harga)`), dashboard-matrix, dashboard-order.
- Counter produk (`orderCount`, `soldCount`).
- Abandoned order (hanya menyimpan config, tidak menghitung biaya).
- Template pesan dan rumus total.

---

## 11. Rencana Test

### Unit test `CodFeeCalculator`

- Ketiga tipe, dengan contoh §5.4.
- Pembulatan ke atas ke kelipatan 100: 4.127,49 → 4.200; 4.200 → 4.200; 4.200,01 → 4.300.
- Batas minimal dan maksimal, termasuk saat salah satu atau keduanya tidak ada, dan maks yang bukan kelipatan 100.
- Diskon lebih besar dari harga (dasar dibatasi menjadi 0).
- Persen 0, config null atau kosong, metode transfer: hasilnya 0.
- Parsing: `Number`, `"2500"`, `"2500.0"`, `"2.5"`; nilai tidak valid: 0 dengan warning.

### Integration test (Testcontainers, pola yang sama dengan test dashboard)

- Create COD: `biaya_cod` terisi dan `ongkos_kirim = tarif + biaya_cod`.
- Create transfer: `biaya_cod = 0` dan `ongkos_kirim = tarif`.
- Order agen dengan diskon: biaya dihitung dari harga setelah diskon, dan biaya tidak terhitung dua kali di `ongkos_kirim`.
- Update order baru: harga, varian, atau diskon berubah, lalu biaya dihitung ulang dari **snapshot** config.
- Update setelah config produk diubah: biaya order **tidak** ikut berubah.
- Update dengan ganti metode pembayaran: memakai config produk terbaru.
- Update order lama (`biaya_cod NULL`): tetap NULL, dan `ongkos_kirim` = tarif saja.
- Export: `original_shipping_cost` = tarif murni, termasuk untuk order lama.
- Validasi config saat simpan produk.
- Pesan WhatsApp: setelah harga varian diubah, `{harga_produk}` dan `{total}` tetap memakai `order.harga`; fallback ke harga varian bila `order.harga` null.

---

## 12. Keputusan Teknis yang Perlu Dikonfirmasi

Tidak ada. Semua pertanyaan sudah dijawab dan dicatat di §3 (D-9, D-10, D-11).

---

## 13. Rollout

1. Deploy backend. Kolom `biaya_cod` dibuat otomatis (nullable), dan baris lama menjadi `NULL` (order lama dibiarkan).
2. Tidak ada migrasi data, karena `ongkos_kirim` lama sudah berisi tarif murni (§2.1).
3. Deploy frontend admin (opsi tipe `hanya-pengiriman` dan validasi).
4. Owner meninjau config COD tiap produk. Produk yang selama ini punya config lengkap **akan mulai menagih biaya COD** pada order baru setelah deploy. Ini perlu dikomunikasikan ke owner sebelum rilis.

---

## 14. Implementasi

| Bagian | Lokasi |
|---|---|
| Kalkulator & validasi config | `service/order/CodFeeCalculator.java` (`calculate`, `validateConfig`) |
| Kolom `biaya_cod` | `entity/Order.java` (`biayaCod`) |
| Create order | `OrderService.createOrderInternal` → `applyCodFee` |
| Order agen (diskon) | `OrderOrchestrationService.createOrderOnChat` → `OrderService.recalculateCodFee` |
| Edit order | `OrderService.updateOrder` (snapshot config, order lama dilewati) |
| Validasi saat simpan produk | `ProdukService.validatePembayaranConfig`; `ProdukController.addProduk` mengembalikan `data: [ErrorDto]` |
| Kode galat | `FormConfigErrorCode.INVALID_COD_FEE_CONFIG` |
| Export `original_shipping_cost` | `OrderService.generateUserExcel`; query `OrderRepository.exportOrder` menambah `biayaCod` |
| Harga di pesan WhatsApp (D-11) | `MessageConstructorHelper.buildOrderParams` |
| Unit test | `CodFeeCalculatorTest` |
| Integration test | `CodFeeOrderIntegrationTest` (Testcontainers) |

Penyimpangan dari draft:

- **§7.3:** pergantian **produk** saat edit juga memicu snapshot config baru, sama seperti pergantian metode pembayaran.
- **`ProdukPembayaran` tidak ditemukan saat create:** `order.pembayaran` diisi dari metode yang diminta dan biaya = 0. Sebelumnya terjadi `NullPointerException`.
- **Copy produk (`ProdukService.copyProduk`):** copy melewati validasi `saveProduct`. Agar copy tetap berhasil untuk produk lama yang config biaya COD-nya tidak valid, `CodFeeCalculator.sanitizeForCopy` membuang keempat key biaya (`tipeBiaya`, `persentaseBiaya`, `minimalBiaya`, `maksimalBiaya`) pada **produk salinan** saja. Biaya COD salinan menjadi nonaktif; key lain dan produk asal tidak berubah.
