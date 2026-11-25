# PRD — Filter Produk pada List & Export Pesanan (Frontend / Dashboard)

| Field | Value |
|---|---|
| Feature | Filter produk (multi-select, dengan pencarian & paginasi opsi) di halaman Pesanan |
| Modul | **Pesanan** (ekstensi modul existing, bukan modul baru) |
| Dokumen terkait | [PRD Backend](./order-product-filter.md), [PRD Frontend Filter Multiple](./order-multi-filter-frontend.md), [FE API Mapping](../frontend/api-mapping.md), [FE Component Inventory](../frontend/component-inventory.md) |
| Stack | Vue 3 (`<script setup>`, Composition API), TypeScript, Vite, Pinia, Axios, SCSS |
| Status | **Ready for Implementation**. Backend sudah diimplementasikan |
| Scope | Frontend saja; kontrak API mengikuti PRD Backend |
| Last updated | 2026-11-22 |
| Target pembaca | Frontend Developer, QA, Product |

> PRD ini menambahkan filter **Produk** ke panel filter halaman Pesanan. Filter ini dipakai untuk list dan export. Pola dan serialisasinya sama dengan [Filter Multiple](./order-multi-filter-frontend.md). Bedanya, opsi produk dimuat dari server dengan pencarian dan paginasi, karena jumlah produk dalam satu workspace bisa banyak. PRD ini tidak berisi kode final.

---

## 1. Latar Belakang & Tujuan

Backend sekarang menerima parameter `idProduk` (UUID produk, bisa lebih dari satu, dipisah koma) pada `GET /order` dan `GET /order/export`. Endpoint opsi `GET /produk/list-dropdown` juga sudah mendukung `search` dan paginasi.

Tujuan:

- User bisa memfilter pesanan berdasarkan satu atau beberapa produk.
- User bisa menemukan produk dengan mengetik nama, walaupun workspace punya ratusan produk.
- Hasil export selalu sama dengan list yang sedang tampil.

---

## 2. Scope

### In scope

- Filter baru **Produk** (multi-select) di panel filter `listPesanan.vue`.
- Opsi dimuat dari server: pencarian nama dengan debounce, lalu halaman berikutnya dimuat saat dropdown di-scroll sampai bawah (infinite scroll).
- Param `idProduk` ikut dikirim di `onIndex()` dan `onExport()` melalui helper bersama `buildOrderFilterParams` (§6).
- Perluasan kecil yang **tetap kompatibel** pada `MultipleSelectCustom` (§3.2).
- Penanganan error 400 untuk `idProduk` (§8).

### Out of scope

- `ModalEditPesanan.vue`. Modal ini tetap memanggil `GET /produk/list-dropdown` **tanpa `page`**, dan backend tetap mengembalikan array penuh seperti sebelumnya. **Tidak perlu diubah.**
- Filter lain (lokasi, status, pembayaran, tanggal, search, `isDouble`) tidak berubah.
- Menyimpan filter di URL atau `localStorage`.

---

## 3. Komponen

### 3.1 Pemakaian

Gunakan `MultipleSelectCustom` (`src/components/common/MultipleSelectCustom.vue`), sama seperti filter lain di panel ini.

| Prop | Nilai |
|---|---|
| `list` | opsi produk hasil server, digabung dengan produk yang sudah dipilih (§5.2) |
| `selected` | `filter.idProduk` (array UUID) |
| `isSearch` | `true` |
| `loading` | `true` saat memuat halaman pertama atau berikutnya |
| `withClearData` | `true` |
| `placeholder` | *"Semua produk"* |

### 3.2 Perluasan komponen (kompatibel ke belakang)

Saat ini `MultipleSelectCustom` menyaring `list` di sisi klien dan tidak punya event untuk infinite scroll. Tambahkan:

| Tambahan | Tipe | Default | Perilaku |
|---|---|---|---|
| prop `remoteSearch` | `boolean` | `false` | Bila `true`, komponen **tidak** menyaring `list` di klien. Teks pencarian dikirim lewat emit `search`, dan induk komponen yang memuat ulang `list`. |
| emit `search` | `(keyword: string)` | n/a | Dipancarkan setiap teks pencarian berubah, hanya bila `remoteSearch` aktif. |
| emit `scrollEnd` | `()` | n/a | Dipancarkan saat daftar opsi di-scroll hingga sekitar 40px dari bawah. |
| prop `hasMore` | `boolean` | `false` | Bila `true` dan `loading`, tampilkan indikator *"Memuat…"* di bawah daftar opsi. |

Default yang ada tidak berubah, jadi pemakaian lain (filter label Chat, user management, provinsi diblokir) tidak terdampak.

> **Perlu dicek oleh developer:** ada emit `input` di komponen. Pastikan apakah emit itu sudah membawa teks pencarian. Kalau sudah, boleh dipakai sebagai pengganti `search`. Yang penting, penyaringan di klien harus bisa dimatikan.

---

## 4. Perilaku Pencarian & Paginasi Opsi

| Kejadian | Aksi |
|---|---|
| Dropdown dibuka pertama kali | Muat `page=1`, `limit=20`, `search=''`. |
| User mengetik | **Debounce 300 ms**, lalu muat ulang dari `page=1` dengan `search=<teks>` dan ganti seluruh opsi. |
| Scroll sampai bawah (`scrollEnd`) | Kalau `!last` dan tidak sedang memuat, muat `page+1` lalu **tambahkan** ke opsi. |
| Respons lama datang setelah respons yang lebih baru | **Abaikan.** Gunakan nomor urut request atau `AbortController`, supaya hasil pencarian "ko" tidak menimpa hasil "kopi". |
| Teks pencarian dikosongkan | Muat ulang dari `page=1` tanpa `search`. |
| Dropdown ditutup lalu dibuka lagi | Pakai opsi yang sudah ada. Muat ulang hanya bila workspace berubah. |
| Workspace aktif berubah | Kosongkan opsi, cache nama (§5.2), dan `filter.idProduk`. |

Request:

```
GET /produk/list-dropdown?workspaceId=1&search=kopi&page=1&limit=20
```

Respons (`data`) berupa Spring Page:

```json
{
  "content": [{ "id": "3f2a1c4e-…", "namaProduk": "Kopi Arabika" }],
  "totalElements": 42, "totalPages": 3, "last": false,
  "pageable": { "pageNumber": 0, "pageSize": 20 }
}
```

Petakan setiap item menjadi `{ name: namaProduk, value: id }`. Gunakan `last` untuk menentukan `hasMore`. Opsi sudah diurutkan A–Z oleh server.

---

## 5. State Management

### 5.1 `usePesananStore`

```ts
filter: {
  // … filter existing
  idProduk: string[]          // UUID; awal []
}
```

- Nilai awal `[]`. Tombol reset filter mengembalikannya ke `[]`.
- Setiap perubahan filter mengembalikan list ke `page = 1`, lalu memanggil `onIndex()`.
- `onIndex()` dan `onExport()` memakai objek filter yang sama.

### 5.2 Opsi & cache nama produk terpilih

State opsi boleh disimpan di store, misalnya `useProdukStore.onDropdownPage()`, atau di composable lokal, misalnya `useProdukOptions()`:

```ts
options: { name: string; value: string }[]
page: number
last: boolean
keyword: string
loading: boolean
selectedNames: Record<string, string>   // id → namaProduk
```

Masalah yang harus ditangani: setelah user mencari "teh", produk "Kopi Arabika" yang sudah dipilih tidak lagi ada di `options`, sehingga chip kehilangan namanya. Karena itu:

- Saat user memilih produk, simpan `selectedNames[id] = namaProduk`.
- Prop `list` yang dikirim ke komponen = produk terpilih (dari `selectedNames`) **digabung** dengan `options`, tanpa duplikat `id`.
- Saat produk dihapus dari pilihan (`onRemove` atau clear), hapus juga entrinya dari `selectedNames`.

### 5.3 Batas 100 produk

Backend menolak lebih dari 100 `idProduk`. Saat sudah 100 produk dipilih:

- Opsi yang belum dipilih **tidak bisa dipilih lagi**. Cara paling sederhana: abaikan pilihan ke-101 di handler `select`.
- Tampilkan pesan info pada komponen (`message`, `messageType="info"`): *"Maksimal 100 produk dapat dipilih."*

---

## 6. Kontrak API & Serialisasi

Tambahkan `idProduk` ke helper `buildOrderFilterParams(filter)` dari PRD Filter Multiple §6.2:

| `filter.idProduk` | Param yang dikirim |
|---|---|
| `[]` | **tidak dikirim** |
| `["a…"]` | `idProduk=a…` |
| `["a…", "b…"]` | `idProduk=a…,b…` |

Jangan kirim dalam bentuk array mentah (`idProduk[]=…`). Backend **tidak membaca** bentuk itu dan **tidak memberi error**, sehingga filter diam-diam tidak diterapkan.

| Endpoint | Store action | Komponen |
|---|---|---|
| `GET /order` | `usePesananStore.onIndex()` | `listPesanan.vue` |
| `GET /order/export` | `usePesananStore.onExport()` | `listPesanan.vue` (tombol Export) |
| `GET /produk/list-dropdown` (dengan `page`) | baru, mis. `useProdukStore.onDropdownPage()` | `listPesanan.vue` (filter Produk) |
| `GET /produk/list-dropdown` (tanpa `page`) | `useProdukStore.onList()` (**tidak berubah**) | `ModalEditPesanan.vue` |

> Export menandai order yang diekspor sebagai `statusEkspor = true`. Dengan filter produk aktif, hanya order dari produk terpilih yang ditandai. Ini perilaku yang diharapkan.

---

## 7. Tampilan

- Posisi filter Produk: **paling depan** di panel filter, sebelum Provinsi, karena produk adalah dimensi filter yang paling sering dipakai. Kalau tata letak panel tidak muat, letakkan setelah Jenis Pembayaran.
- Pilihan tampil sebagai chip yang bisa dihapus. Kalau lebih dari 3 produk dipilih, ringkas tampilannya menjadi *"3 produk +N"* bila komponen mendukungnya. Kalau belum didukung, biarkan chip turun ke baris berikutnya (wrap).
- Daftar opsi kosong karena pencarian: *"Produk tidak ditemukan."*
- Workspace belum punya produk: *"Belum ada produk."*

---

## 8. Validasi & Error Handling

Format error 400 sama dengan filter lain (`data: [ErrorDto]`):

| `field` / `code` | Penyebab di frontend | Penanganan |
|---|---|---|
| `idProduk` / `INVALID_FILTER_VALUE` | Bug: nilai yang terkirim bukan UUID | Tampilkan `message` lewat `alertStore`. |
| `idProduk` / `FILTER_TOO_MANY_VALUES` | Lebih dari 100 produk; seharusnya dicegah oleh §5.3 | Tampilkan `message` lewat `alertStore`. |

Untuk `GET /produk/list-dropdown`:

| Kondisi | Penanganan |
|---|---|
| 400 (`page`/`limit` tidak valid) | Bug. Tampilkan alert; jangan ulangi request otomatis. |
| Gagal jaringan saat memuat opsi | Alert error yang sudah ada. Opsi yang sudah dimuat tetap ditampilkan, dan scroll ke bawah boleh mencoba lagi. |

Export: error 400 diterima sebagai `Blob`. Tangani dengan cara yang sama seperti PRD Filter Multiple §8: baca `blob.text()`, `JSON.parse`, lalu tampilkan alert, dan **jangan** unduh file.

---

## 9. Acceptance Criteria

| # | Kriteria |
|---|---|
| AC-1 | User bisa memilih satu atau beberapa produk. List hanya menampilkan order dari produk terpilih. |
| AC-2 | Filter produk digabung dengan filter lain dengan logika AND (mis. produk A + status PAID → hanya order PAID dari produk A). |
| AC-3 | Mengetik di pencarian memuat opsi dari server setelah ±300 ms, tanpa peduli huruf besar/kecil (mis. "kopi" menemukan "Kopi Arabika"). |
| AC-4 | Scroll sampai bawah daftar opsi memuat halaman berikutnya, dan berhenti saat `last = true`. |
| AC-5 | Mengetik cepat tidak menampilkan hasil pencarian lama (tidak ada race condition). |
| AC-6 | Produk yang sudah dipilih tetap tampil dengan namanya walaupun tidak ada di hasil pencarian saat ini. |
| AC-7 | Tidak bisa memilih lebih dari 100 produk, dan pesan info tampil. |
| AC-8 | Request di DevTools memakai format koma (`idProduk=a,b`), tanpa `[]`, dan `idProduk` tidak dikirim bila kosong. |
| AC-9 | Jumlah baris export = `totalElements` list untuk filter yang sama. |
| AC-10 | Perubahan filter produk mengembalikan list ke halaman 1. Reset filter mengosongkan filter produk. |
| AC-11 | `ModalEditPesanan` tetap menampilkan **semua** produk seperti sebelumnya (regresi). |
| AC-12 | Pemakaian lain `MultipleSelectCustom` (filter label Chat, user management, provinsi diblokir) tidak berubah perilakunya (regresi). |

---

## 10. Ketergantungan & Rollout

1. Backend ([PRD Backend](./order-product-filter.md)) di-deploy lebih dulu. Semua perubahan backend kompatibel ke belakang: `idProduk` opsional, dan `list-dropdown` tanpa `page` tetap mengembalikan array.
2. Frontend di-deploy setelahnya.
3. Perbarui `docs/frontend/component-inventory.md` (`MultipleSelectCustom`: `remoteSearch`, `hasMore`, `search`, `scrollEnd`) dan `docs/frontend/business-modules.md` (`listPesanan`: filter produk) bersamaan dengan implementasi.
