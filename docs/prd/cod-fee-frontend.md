# PRD — Konfigurasi Biaya COD (Frontend / Dashboard)

| Field | Value |
|---|---|
| Feature | Pengaturan biaya COD (persentase untuk kurir) di form produk |
| Modul | **Produk** (ekstensi `FormProduk` existing, bukan modul baru) |
| Dokumen terkait | [PRD Backend — Biaya COD](./cod-fee.md), [API Reference §6.5](../api-reference.md), [FE API Mapping](../frontend/api-mapping.md), [FE Business Modules](../frontend/business-modules.md), [FE Component Inventory](../frontend/component-inventory.md) |
| Stack | Vue 3 (`<script setup>`, Composition API), TypeScript, Vite, Pinia, Axios, SCSS |
| Status | **Ready for Implementation** — backend sudah diimplementasikan |
| Scope | Frontend dashboard saja. Halaman checkout publik **di luar scope** (keputusan D-10 di PRD backend). |
| Last updated | 2026-11-16 |
| Target pembaca | Frontend Developer, QA, Product |

> PRD ini mendefinisikan UI untuk mengatur biaya COD per produk di `FormProduk` (create & edit), dipetakan ke kontrak `PembayaranDto.config` yang **sudah diimplementasikan** di backend. Aturan perhitungan dan keputusan bisnis ada di [PRD Backend](./cod-fee.md); dokumen ini hanya membahas sisi dashboard. Mengikuti konvensi existing (module-based, Pinia store, helper `apiConfig/method.ts`, komponen `common/`, `alertStore`). Tidak berisi kode final.

---

## 1. Latar Belakang & Tujuan

Backend kini menagihkan **biaya COD** (persentase untuk kurir) ke pelanggan pada order COD. Biaya dihitung dari pengaturan di `ProdukPembayaran.config` milik metode `COD` pada tiap produk, lalu ditambahkan ke ongkir order.

Saat ini dashboard tidak punya UI untuk mengisi pengaturan tersebut. Contoh payload yang terdokumentasi selalu mengirim `"config": {}` untuk COD.

Tujuan:

- Owner/admin dapat mengaktifkan, mengubah, dan mematikan biaya COD per produk.
- Input divalidasi di frontend dengan aturan yang sama dengan backend, dan error dari backend dipetakan ke field yang tepat.
- Owner paham dampaknya: biaya ditambahkan ke tagihan pelanggan pada **order baru**.

---

## 2. Ringkasan Aturan Bisnis (dari PRD Backend)

| Aturan | Nilai |
|---|---|
| Penanggung biaya | Pelanggan; biaya **ditambahkan ke ongkir** order |
| Dasar harga | Harga **setelah diskon** |
| Pembulatan | **Ke atas ke kelipatan Rp100**, sebelum batas min/maks |
| Min / maks | **Opsional** |
| Order lama | Tidak terpengaruh |
| Perubahan setting produk | Hanya berlaku untuk **order baru**; order yang sudah ada memakai snapshot saat dibuat |

Tipe biaya:

| `tipeBiaya` (nilai API) | Label UI | Dasar perhitungan |
|---|---|---|
| `hanya-produk` | Persentase dari harga produk | harga − diskon |
| `produk-pengiriman` | Persentase dari harga produk + ongkir | harga − diskon + ongkir |
| `hanya-pengiriman` | Persentase dari ongkir | ongkir |

---

## 3. Scope

### In scope

- Bagian **"Biaya COD"** di dalam pengaturan metode pembayaran COD pada `FormProduk` (create & edit).
- Memuat nilai dari `GET /produk/{id}` dan mengirimnya lewat `POST /produk`.
- Validasi sisi klien dan pemetaan error 400 dari backend.
- Contoh perhitungan langsung di form (§6.4).

### Out of scope

- Halaman checkout publik (estimasi biaya COD tidak ditampilkan; D-10 PRD backend).
- Rincian "Biaya COD" terpisah di detail order atau pesan WhatsApp. Biaya sudah termasuk di angka ongkir (D-6, D-7 PRD backend).
- Konfigurasi biaya untuk metode `BANK_TRANSFER`.

---

## 4. Kontrak API

### 4.1 Struktur data

`AddProdukDto.pembayaran` dan detail produk memakai `PembayaranDto { tipe: string, config: Record<string, any> }`. Untuk `tipe = "COD"`, biaya COD disimpan di `config`:

```json
{
  "tipe": "COD",
  "config": {
    "tipeBiaya": "hanya-produk",
    "persentaseBiaya": 3,
    "minimalBiaya": 2000,
    "maksimalBiaya": 5000
  }
}
```

| Key | Tipe | Wajib | Aturan |
|---|---|---|---|
| `tipeBiaya` | string | ya, bila biaya aktif | `hanya-produk` \| `produk-pengiriman` \| `hanya-pengiriman` |
| `persentaseBiaya` | number | ya, bila biaya aktif | `0 ≤ x ≤ 100`, boleh desimal (mis. `2.5`) |
| `minimalBiaya` | number | tidak | bilangan bulat `≥ 0` |
| `maksimalBiaya` | number | tidak | bilangan bulat `≥ 0`; `minimalBiaya ≤ maksimalBiaya` bila keduanya diisi |

### 4.2 Biaya nonaktif

Biaya dianggap nonaktif bila `config` **tidak memuat** keempat key di atas. Saat user mematikan biaya, frontend **menghapus** keempat key tersebut dari `config`. Key lain tidak boleh disentuh (§5.3).

### 4.3 Endpoint

| Aksi | Endpoint | Store |
|---|---|---|
| Muat detail produk | `GET /produk/{id}` → `pembayaran: PembayaranDto[]` | `useProdukStore.onDetail()` |
| Simpan produk | `POST /produk` (body `AddProdukDto`) | `useProdukStore.onStore()` |

Tidak ada endpoint baru.

### 4.4 Error 400 dari backend

```json
{
  "success": false,
  "message": "Persentase biaya COD harus berupa angka antara 0 dan 100.",
  "data": [
    { "field": "pembayaran[0].config.persentaseBiaya",
      "code": "INVALID_COD_FEE_CONFIG",
      "message": "Persentase biaya COD harus berupa angka antara 0 dan 100." }
  ]
}
```

- `code` selalu `INVALID_COD_FEE_CONFIG` untuk pelanggaran config biaya COD.
- `field` berbentuk `pembayaran[<index>].config.<key>`. **`<index>` adalah posisi entri di array `pembayaran` pada payload yang dikirim**, bukan ID.
- Semua pelanggaran dikembalikan sekaligus.

---

## 5. State Management

### 5.1 State form

Di `FormProduk` (atau composable kecil, misalnya `useCodFeeForm`), simpan state UI terpisah dari payload:

```ts
interface CodFeeForm {
  aktif: boolean
  tipeBiaya: 'hanya-produk' | 'produk-pengiriman' | 'hanya-pengiriman' | null
  persentaseBiaya: number | null
  minimalBiaya: number | null
  maksimalBiaya: number | null
}
```

### 5.2 Memuat (edit mode)

Dari entri `pembayaran` dengan `tipe === 'COD'`:

| Kondisi `config` | `aktif` | Field lain |
|---|---|---|
| `null`, `{}`, atau tanpa `tipeBiaya` dan `persentaseBiaya` | `false` | `null` |
| Ada `tipeBiaya` atau `persentaseBiaya` | `true` | dari `config`, dikonversi (§5.4) |

Nilai dari data lama bisa berupa **string** (`"3"`, `"2500.0"`). Backend menerimanya, jadi frontend wajib mengonversi ke `number` saat memuat agar input angka tidak kosong.

Bila `tipeBiaya` berisi nilai yang tidak dikenal (data lama), set `tipeBiaya = null` dan tampilkan error field: *"Tipe biaya tidak dikenal, silakan pilih ulang."*

### 5.3 Menyimpan

Saat membentuk payload `pembayaran` untuk COD:

1. Mulai dari **salinan `config` yang dimuat** (bukan objek kosong), supaya key lain, misalnya `deskripsi`, tetap terjaga.
2. Bila `aktif = false`: hapus `tipeBiaya`, `persentaseBiaya`, `minimalBiaya`, `maksimalBiaya`.
3. Bila `aktif = true`:
   - set `tipeBiaya` dan `persentaseBiaya` sebagai `number`;
   - set `minimalBiaya` / `maksimalBiaya` bila diisi, dan **hapus key-nya** bila kosong (jangan kirim `null` atau `""`).
4. Catat index entri COD di array `pembayaran` yang dikirim, untuk pemetaan error (§8).

### 5.4 Konversi angka

| Input | Hasil |
|---|---|
| `""`, `null`, `undefined` | `null` (tidak diisi) |
| `"3"`, `3` | `3` |
| `"2.5"`, `"2,5"` | `2.5` (koma desimal diterima di UI, dikirim sebagai titik) |
| `"2500.0"` | `2500` |
| teks bukan angka | tidak valid (§7) |

---

## 6. Tampilan

### 6.1 Posisi

Di section **metode pembayaran** `FormProduk`, bagian "Biaya COD" muncul **hanya bila metode COD dipilih/aktif** untuk produk tersebut. Bila COD dihapus dari produk, bagian ini hilang dan state-nya tidak ikut terkirim.

### 6.2 Layout

```
┌─ Metode Pembayaran ─────────────────────────────────────────┐
│ [x] COD    [x] Bank Transfer                                │
│                                                             │
│ ┌─ Biaya COD ─────────────────────────────────────────────┐ │
│ │ [●━━] Tagihkan biaya COD ke pelanggan                   │ │
│ │                                                         │ │
│ │ Dasar perhitungan                                       │ │
│ │ (•) Persentase dari harga produk                        │ │
│ │ ( ) Persentase dari harga produk + ongkir               │ │
│ │ ( ) Persentase dari ongkir                              │ │
│ │                                                         │ │
│ │ Persentase *        Minimal biaya      Maksimal biaya   │ │
│ │ [ 3        ] %      Rp [ 2000     ]    Rp [ 5000     ]  │ │
│ │                     (opsional)         (opsional)       │ │
│ │                                                         │ │
│ │ ⓘ Contoh: varian termurah Rp100.000, ongkir Rp20.000    │ │
│ │   → biaya COD Rp3.000, ongkir ke pelanggan Rp23.000.    │ │
│ │                                                         │ │
│ │ ⓘ Biaya dibulatkan ke atas ke kelipatan Rp100 dan       │ │
│ │   ditambahkan ke ongkir pelanggan pada order baru.      │ │
│ │   Order yang sudah ada tidak berubah.                   │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 6.3 Komponen

| Elemen | Komponen existing | Catatan |
|---|---|---|
| Toggle aktif | `SwitchButton` | Memakai emit `input`, bukan `v-model` |
| Tipe biaya | `RadioButton` | 3 opsi dengan label §2 |
| Persentase | `InputCustom` (`type="text"`, `inputmode="decimal"`) | Suffix `%`; maksimal 2 angka desimal |
| Minimal / maksimal | `InputCustom` (`type="text"`, `inputmode="numeric"`) | Prefix `Rp`; tampil dengan pemisah ribuan |
| Pesan error/info | `message` + `messageType` pada `InputCustom` | |

Tidak diperlukan komponen baru.

### 6.4 Contoh perhitungan

Teks contoh dihitung langsung di frontend setiap kali input berubah, untuk membantu owner memahami angka yang akan ditagihkan:

- **Harga:** harga varian **termurah** di `atributProduk` (diskon = 0). Bila belum ada varian, pakai Rp100.000.
- **Ongkir:** nilai ilustrasi tetap Rp20.000, ditandai jelas sebagai contoh.
- **Rumus:** sama dengan backend:
  ```
  dasar = sesuai tipe (minimal 0)
  biaya = ceil(dasar × persen / 100 / 100) × 100     // ke atas ke kelipatan 100
  biaya = max(biaya, minimal)  bila minimal diisi
  biaya = min(biaya, maksimal) bila maksimal diisi
  ```
- Hanya tampil bila tipe dan persentase sudah valid.

Ini **hanya ilustrasi**. Perhitungan resmi tetap di backend (`CodFeeCalculator`). Bila rumus di frontend dan backend suatu saat berbeda, backend yang berlaku.

---

## 7. Validasi Sisi Klien

Dijalankan saat field kehilangan fokus dan sebelum submit. Bila ada error, submit dibatalkan dan error tampil di field. Aturannya **sama dengan backend** (§4.1):

| Field | Aturan | Pesan |
|---|---|---|
| `tipeBiaya` | wajib bila `aktif` | *"Pilih dasar perhitungan biaya COD."* |
| `persentaseBiaya` | wajib bila `aktif`; angka `0–100` | *"Persentase biaya COD harus berupa angka antara 0 dan 100."* |
| `minimalBiaya` | opsional; bilangan bulat `≥ 0` | *"Minimal biaya COD harus berupa angka bulat >= 0."* |
| `maksimalBiaya` | opsional; bilangan bulat `≥ 0` | *"Maksimal biaya COD harus berupa angka bulat >= 0."* |
| min vs maks | `minimalBiaya ≤ maksimalBiaya` bila keduanya diisi | *"Minimal biaya COD tidak boleh lebih besar dari maksimal biaya COD."* (di field minimal) |

Field yang disembunyikan karena `aktif = false` tidak divalidasi.

---

## 8. Error Handling

### 8.1 Error 400 dari `POST /produk`

1. Bila `data` berisi array `ErrorDto`, petakan setiap item dengan `field` berpola `pembayaran[<i>].config.<key>`:
   - `<i>` sama dengan index entri COD yang dicatat saat menyusun payload (§5.3 langkah 4) → tampilkan `message` di input `<key>` pada bagian Biaya COD.
   - Scroll ke bagian Biaya COD dan fokuskan field pertama yang error.
2. Error yang tidak bisa dipetakan ke field ditampilkan lewat `alertStore` dengan `message` dari backend.
3. Form **tidak** direset; input user tetap ada.

### 8.2 Error lain

Tetap memakai penanganan existing (`errorHelper` + `alertStore`).

---

## 9. Edge Cases

| # | Kasus | Perilaku |
|---|---|---|
| EC-1 | Produk lama dengan `config: {}` | Toggle mati; tidak ada perubahan saat disimpan |
| EC-2 | `config` lama berisi string (`"3"`) | Dikonversi ke number saat memuat (§5.4) |
| EC-3 | `config` berisi key lain (mis. `deskripsi`) | Key lain dipertahankan saat menyimpan (§5.3) |
| EC-4 | `tipeBiaya` lama tidak dikenal | Dikosongkan + error field (§5.2) |
| EC-5 | User mematikan toggle lalu menyimpan | Keempat key dihapus; biaya berhenti untuk order baru |
| EC-6 | User menghapus metode COD dari produk | Bagian Biaya COD hilang; tidak ada config COD yang dikirim |
| EC-7 | Persentase `0` | Valid; biaya selalu Rp0 (kecuali minimal diisi) |
| EC-8 | Maksimal bukan kelipatan 100 (mis. 4.150) | Valid; contoh perhitungan menampilkan hasil batas maks apa adanya |
| EC-9 | Fitur "Copy produk" (`GET /produk/copy`) | Config COD ikut tersalin oleh backend; tidak ada aksi frontend. Bila config biaya produk asal **tidak valid** (data lama), backend membuang keempat key biaya pada produk salinan, sehingga biaya COD salinan **nonaktif**. Copy tetap berhasil, dan produk asal tidak berubah. |

---

## 10. Acceptance Criteria

| # | Kriteria |
|---|---|
| AC-1 | Bagian "Biaya COD" hanya tampil bila produk menerima metode COD. |
| AC-2 | Mengaktifkan biaya, memilih tipe, mengisi persentase, lalu menyimpan mengirim `config` COD dengan `tipeBiaya` dan `persentaseBiaya` bertipe number (cek di DevTools). |
| AC-3 | Min/maks yang dikosongkan **tidak** terkirim sebagai key (bukan `null` / `""`). |
| AC-4 | Membuka ulang produk (edit mode) menampilkan kembali nilai yang tersimpan, termasuk untuk data lama bernilai string. |
| AC-5 | Mematikan toggle lalu menyimpan menghapus keempat key biaya, dan key lain di `config` tetap ada. |
| AC-6 | Ketiga tipe biaya dapat dipilih, termasuk "Persentase dari ongkir" (`hanya-pengiriman`). |
| AC-7 | Validasi klien mencegah submit untuk persentase di luar 0–100, min/maks bukan bilangan bulat, dan min > maks, dengan pesan di field terkait. |
| AC-8 | Error 400 `INVALID_COD_FEE_CONFIG` dari backend tampil di field yang sesuai, dan input user tidak hilang. |
| AC-9 | Contoh perhitungan sesuai rumus backend: harga 100.000, ongkir 20.000, 3%, min 2.000, maks 5.000 menghasilkan 3.000 (`hanya-produk`), 3.600 (`produk-pengiriman`), dan 2.000 (`hanya-pengiriman`). |
| AC-10 | Teks info menjelaskan bahwa biaya ditambahkan ke ongkir pelanggan pada order baru, dan order lama tidak berubah. |

> Catatan AC-9: contoh di §6.4 memakai diskon 0, sehingga `produk-pengiriman` = 3% × 120.000 = 3.600. Angka ini berbeda dengan contoh §5.4 PRD backend yang memakai diskon 10.000.

---

## 11. Ketergantungan & Rollout

1. Backend biaya COD (PRD backend §14) di-deploy lebih dulu. Frontend lama tetap berfungsi karena terus mengirim `config: {}`.
2. **Sebelum rilis, owner perlu diberi tahu:** produk yang sudah memiliki config biaya COD lengkap (bila ada, dari data lama) akan langsung menagih biaya pada order baru setelah backend di-deploy, meskipun UI ini belum dirilis.
3. Deploy frontend.
4. Perbarui `docs/frontend/api-mapping.md` bagian `GET /produk/{id}` agar `pembayaran` terdokumentasi sebagai `PembayaranDto[]`, bukan `["any"]`.
