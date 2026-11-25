# PRD — Filter Multiple pada List & Export Order (Frontend / Dashboard)

| Field | Value |
|---|---|
| Feature | Filter multiple untuk wilayah, status, dan jenis pembayaran di halaman Pesanan |
| Modul | **Pesanan** (ekstensi modul existing, bukan modul baru) |
| Dokumen terkait | [PRD Backend](./order-multi-filter.md), [FE API Mapping](../frontend/api-mapping.md), [FE Business Modules](../frontend/business-modules.md), [FE Component Inventory](../frontend/component-inventory.md) |
| Stack | Vue 3 (`<script setup>`, Composition API), TypeScript, Vite, Pinia, Axios, SCSS |
| Status | **Ready for Implementation** — backend sudah diimplementasikan |
| Scope | Frontend-only; kontrak API mengikuti PRD Backend |
| Last updated | 2026-11-15 |
| Target pembaca | Frontend Developer, QA, Product |

> PRD ini mendefinisikan perubahan UI/UX filter di halaman Pesanan agar dapat memilih **beberapa** provinsi, kota, kecamatan, status, dan jenis pembayaran, untuk list maupun export. Mengikuti konvensi dashboard yang sudah ada (module-based, Pinia store, helper `apiConfig/method.ts`, komponen `common/`, `alertStore`). Tidak berisi kode final.

---

## 1. Latar Belakang & Tujuan

Halaman Pesanan (`/pesanan`, `src/modules/pesanan/components/listPesanan.vue`) saat ini memfilter order dengan **satu** nilai per filter. Backend akan mendukung banyak nilai per filter (lihat [PRD Backend](./order-multi-filter.md)).

Tujuan:

- User dapat memilih beberapa nilai sekaligus pada lima filter.
- Filter lokasi tidak bisa menghasilkan kombinasi yang ambigu (§4).
- Hasil list dan export selalu sesuai dengan filter yang terlihat di layar.

---

## 2. Scope

### In scope

- Mengganti kontrol filter berikut menjadi multi-select:

  | Filter | Param API | Sumber opsi |
  |---|---|---|
  | Provinsi | `idProvinsi` | `GET /location/province` |
  | Kota | `idKota` | `GET /location/city` (untuk satu provinsi terpilih) |
  | Kecamatan | `idKecamatan` | `GET /location/district` (untuk satu kota terpilih) |
  | Status | `status` | `GET /order/status` |
  | Jenis pembayaran | `jenisPembayaran` | konstanta `COD`, `BANK_TRANSFER` |

- Aturan aktif/nonaktif filter lokasi bertingkat (§4).
- Serialisasi format koma pada `onIndex()` dan `onExport()` (§6).
- Penanganan error 400 untuk list dan export (§8).

### Out of scope

- Filter lain: tanggal, `statusEkspor`, search, `isDouble`. Tetap seperti sekarang.
- Menyimpan filter di URL atau `localStorage`.
- Perubahan pada halaman lain (chat, dashboard, gudang).

---

## 3. Komponen

Gunakan komponen `MultipleSelectCustom` yang sudah ada (`src/components/common/MultipleSelectCustom.vue`). Komponen ini sudah dipakai untuk filter label di Chat, dan sudah mendukung `list`, `selected`, `isSearch`, `disabled`, `loading`, `withClearData`, `message`, serta `messageType`.

| Filter | `isSearch` | Catatan |
|---|---|---|
| Provinsi | ya | ±38 opsi |
| Kota | ya | opsi bisa ratusan |
| Kecamatan | ya | opsi bisa ratusan |
| Status | tidak | sedikit opsi |
| Jenis pembayaran | tidak | 2 opsi |

Tidak diperlukan komponen baru.

---

## 4. Aturan Filter Lokasi Bertingkat

### 4.1 Status aktif / nonaktif

| Provinsi terpilih | Kota | Kecamatan |
|---|---|---|
| 0 | nonaktif, kosong | nonaktif, kosong |
| 1 | **aktif**; opsi = kota di provinsi itu | aktif hanya bila **tepat 1** kota terpilih |
| ≥2 | **nonaktif, dikosongkan** | **nonaktif, dikosongkan** |

| Kota terpilih (dengan 1 provinsi) | Kecamatan |
|---|---|
| 0 | nonaktif, kosong |
| 1 | **aktif**; opsi = kecamatan di kota itu |
| ≥2 | **nonaktif, dikosongkan** |

### 4.2 Perilaku saat pilihan berubah

| Kejadian | Aksi |
|---|---|
| Provinsi berubah dari 1 menjadi ≥2 | Kosongkan kota & kecamatan, lalu nonaktifkan keduanya. |
| Provinsi berubah dari ≥2 menjadi 1 | Aktifkan kota (dalam keadaan kosong), lalu muat opsi kota untuk provinsi tersebut. |
| Provinsi yang terpilih diganti (tetap 1, tapi berbeda) | Kosongkan kota & kecamatan, lalu muat ulang opsi kota. |
| Provinsi dikosongkan | Kosongkan dan nonaktifkan kota & kecamatan. |
| Kota berubah dari 1 menjadi ≥2 | Kosongkan & nonaktifkan kecamatan. |
| Kota berubah dari ≥2 menjadi 1 | Aktifkan kecamatan (kosong), lalu muat opsi kecamatan. |
| Kota yang terpilih diganti (tetap 1) | Kosongkan kecamatan, lalu muat ulang opsi. |

**Wajib:** filter yang dinonaktifkan **harus dikosongkan di state**, tidak cukup hanya dinonaktifkan di tampilan. Kalau nilainya tetap ada, nilai itu ikut terkirim ke API sebagai filter yang tidak terlihat oleh user, dan backend akan menolaknya dengan 400 (`FILTER_LOCATION_CONFLICT`).

### 4.3 Petunjuk untuk user

Saat kota atau kecamatan nonaktif karena aturan di atas, tampilkan pesan info pada komponen (`message` + `messageType="info"`):

- Kota: *"Pilih tepat satu provinsi untuk memfilter kota."*
- Kecamatan: *"Pilih tepat satu kota untuk memfilter kecamatan."*

---

## 5. State Management (`usePesananStore`)

Field filter berubah dari nilai tunggal menjadi array:

```ts
filter: {
  idProvinsi: number[]        // sebelumnya: any
  idKota: number[]
  idKecamatan: number[]
  status: string[]            // sebelumnya: string
  jenisPembayaran: string[]
  // filter lain tidak berubah
}
```

- Nilai awal setiap array adalah `[]`.
- Tombol reset filter mengembalikan semua array ke `[]`.
- Setiap perubahan filter mengembalikan halaman ke `page = 1`, lalu memanggil ulang `onIndex()`.
- `onIndex()` dan `onExport()` memakai **objek filter yang sama**, sehingga export selalu sesuai dengan list yang sedang ditampilkan.

---

## 6. Kontrak API & Serialisasi

### 6.1 Format koma (wajib)

Array dikirim sebagai **satu string dipisah koma**:

```
GET /order?workspaceId=1&page=1&limit=10&status=UNPAID,PENDING&idProvinsi=32,33
```

Jangan mengirim array apa adanya ke `getData` / `downloadFile`. Serialisasi bawaan axios menghasilkan `status[]=UNPAID&status[]=PENDING`. Backend **tidak membaca** format `[]` itu dan **tidak memberi error**, sehingga filter diam-diam tidak diterapkan.

### 6.2 Aturan pembentukan param

| Isi array | Param yang dikirim |
|---|---|
| `[]` | **tidak dikirim** (hapus key dari params) |
| `[32]` | `idProvinsi=32` |
| `[32, 33]` | `idProvinsi=32,33` |

Buat satu helper bersama yang dipakai `onIndex()` dan `onExport()`, misalnya `buildOrderFilterParams(filter)`:

- Ubah setiap array menjadi `array.join(',')`.
- Buang key yang array-nya kosong.
- Teruskan filter lain apa adanya.

### 6.3 Endpoint terdampak

| Endpoint | Store action | Komponen |
|---|---|---|
| `GET /order` | `usePesananStore.onIndex()` | `listPesanan.vue` |
| `GET /order/export` | `usePesananStore.onExport()` | `listPesanan.vue` (tombol Export) |

Opsi lokasi memakai endpoint yang sudah ada: `/location/province`, `/location/city`, `/location/district`. Kota dan kecamatan hanya dimuat saat level atasnya **tepat satu** (§4).

---

## 7. Tampilan

- Kelima filter berada di panel filter `listPesanan.vue` yang sudah ada, dengan urutan: Provinsi → Kota → Kecamatan → Status → Jenis Pembayaran.
- Pilihan tampil sebagai chip yang dapat dihapus (bawaan `MultipleSelectCustom`).
- `withClearData` aktif agar user bisa mengosongkan satu filter sekaligus.
- Saat opsi kota/kecamatan sedang dimuat, gunakan `loading`.

---

## 8. Validasi & Error Handling

Backend mengembalikan 400 dengan format:

```json
{ "success": false, "message": "…", "data": [{ "field": "idKota", "code": "FILTER_LOCATION_CONFLICT", "message": "…" }] }
```

| `code` | Penyebab di frontend | Penanganan |
|---|---|---|
| `FILTER_LOCATION_CONFLICT` | Bug: nilai filter nonaktif tidak dikosongkan (§4.2) | Tampilkan `message` lewat `alertStore`. Seharusnya tidak pernah terjadi bila §4 diikuti. |
| `INVALID_FILTER_VALUE` | Bug: nilai di luar opsi | Tampilkan `message` lewat `alertStore`. |
| `FILTER_TOO_MANY_VALUES` | Lebih dari 500 nilai pada satu filter | Tampilkan `message`. Praktis hanya mungkin pada kecamatan. |

### Export

`downloadFile` memakai `responseType: 'blob'`, sehingga body error 400 juga diterima sebagai **`Blob`**, bukan objek JSON. `onExport()` harus:

1. Memeriksa status respons atau `Content-Type` (`application/json` berarti error).
2. Membaca blob (`await blob.text()`), lalu `JSON.parse`.
3. Menampilkan `message` lewat `alertStore`. **Jangan** mengunduh blob error sebagai file `Pesanan.xlsx`.

---

## 9. State Kosong / Loading

| Kondisi | Tampilan |
|---|---|
| Filter menghasilkan 0 order | Empty state tabel yang sudah ada |
| Opsi kota/kecamatan sedang dimuat | `loading` pada `MultipleSelectCustom` |
| Gagal memuat opsi lokasi | Alert error yang sudah ada; filter tetap bisa dipakai tanpa level tersebut |

---

## 10. Acceptance Criteria

| # | Kriteria |
|---|---|
| AC-1 | User dapat memilih lebih dari satu provinsi, status, dan jenis pembayaran. List hanya menampilkan order yang cocok (OR di dalam filter, AND antar filter). |
| AC-2 | Dengan 1 provinsi terpilih, user dapat memilih lebih dari satu kota dari provinsi tersebut. |
| AC-3 | Dengan 1 kota terpilih, user dapat memilih lebih dari satu kecamatan dari kota tersebut. |
| AC-4 | Saat provinsi ≥2, kota dan kecamatan **nonaktif dan kosong**, dan pesan petunjuk tampil. |
| AC-5 | Saat kota ≥2, kecamatan **nonaktif dan kosong**, dan pesan petunjuk tampil. |
| AC-6 | Mengurangi pilihan kembali menjadi satu mengaktifkan level di bawahnya dalam keadaan kosong. |
| AC-7 | Request yang terkirim (cek di DevTools) memakai format koma (`status=PAID,UNPAID`), tidak ada `[]`, dan filter kosong tidak dikirim. |
| AC-8 | Export menghasilkan baris yang sama dengan list untuk filter yang sama (jumlah baris export = `totalElements` list). |
| AC-9 | Perubahan filter mengembalikan list ke halaman 1. |
| AC-10 | Reset filter mengosongkan kelima filter dan menampilkan semua order. |
| AC-11 | Error 400 pada list dan export tampil sebagai alert berisi `message` dari backend; export yang gagal tidak mengunduh file. |
| AC-12 | Memilih satu nilai per filter memberi hasil yang sama dengan perilaku sebelumnya. |

---

## 11. Ketergantungan & Rollout

1. Backend ([PRD Backend](./order-multi-filter.md)) di-deploy lebih dulu. Backend tetap menerima nilai tunggal, jadi frontend lama tidak rusak.
2. Frontend di-deploy setelahnya.
3. `docs/frontend/api-mapping.md` (`GET /order`, `GET /order/export`) diperbarui bersamaan dengan implementasi frontend.
