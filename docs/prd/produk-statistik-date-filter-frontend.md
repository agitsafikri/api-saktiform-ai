# PRD — Filter Tanggal Statistik pada List Produk (Frontend / Dashboard)

| Field | Value |
|---|---|
| Feature | Filter rentang tanggal order masuk untuk kolom statistik di halaman Produk |
| Modul | **Produk** (ekstensi modul existing, bukan modul baru) |
| Dokumen terkait | [API Reference §6.5](../api-reference.md), [FE API Mapping — `GET /produk`](../frontend/api-mapping.md), [FE Business Modules](../frontend/business-modules.md), [FE Component Inventory](../frontend/component-inventory.md) |
| Stack | Vue 3 (`<script setup>`, Composition API), TypeScript, Vite, Pinia, Axios, SCSS |
| Status | **Ready for Implementation**. Backend sudah diimplementasikan dan diuji |
| Scope | Frontend saja; kontrak API mengikuti backend |
| Last updated | 2026-11-22 |
| Target pembaca | Frontend Developer, QA, Product |

> PRD ini menambahkan filter **periode** di halaman Produk. Filter ini hanya mengubah angka statistik setiap produk (Total Order, Dibayar, Rasio Dibayar, Terjual) supaya sesuai periode yang dipilih. Daftar produk tidak ikut tersaring. PRD ini tidak berisi kode final.

---

## 1. Latar Belakang & Tujuan

Tabel produk (`/produk`, `src/modules/produk/components/ListProduk.vue`) menampilkan statistik setiap produk. Saat ini angkanya selalu total **sepanjang waktu**, sehingga user tidak bisa melihat performa produk untuk periode tertentu, misalnya bulan ini atau kampanye minggu lalu.

Backend `GET /produk` sekarang menerima `tanggalAwalOrder` dan `tanggalAkhirOrder`.

Tujuan:

- User bisa memilih periode dan melihat statistik setiap produk untuk periode itu.
- Arti angkanya jelas: patokannya adalah **tanggal order masuk**.

---

## 2. Aturan Bisnis (dari backend, wajib dipahami)

| # | Aturan |
|---|---|
| BR-1 | Filter **hanya** memengaruhi `totalOrder`, `totalDibayar`, `totalTerjual`, dan `rasioDibayar`. Daftar produk, urutan, pencarian, dan jumlah halaman **tidak berubah**. |
| BR-2 | Patokannya **hanya tanggal order masuk**. Tanggal pembayaran tidak dipakai. |
| BR-3 | `totalOrder` = order yang masuk dalam periode, kecuali yang dihapus (`DELETE`). |
| BR-4 | `totalDibayar` / `totalTerjual` = order yang masuk dalam periode **dan sekarang berstatus `PAID`**, kapan pun dibayarnya. Contoh untuk periode November: order masuk 29 Nov lalu dibayar 3 Des **dihitung**; order masuk 31 Okt lalu dibayar 2 Nov **tidak dihitung**. |
| BR-5 | `rasioDibayar` = `totalDibayar / totalOrder`, tidak pernah lebih dari 100%. |
| BR-6 | Produk tanpa order dalam periode tetap tampil dengan `0` / `"0%"`. |
| BR-7 | Tanpa periode, angka adalah total sepanjang waktu (perilaku sekarang). |

Karena BR-4, angka "Dibayar" untuk periode yang baru berjalan (mis. bulan ini) **bisa bertambah** setelah periode itu lewat, seiring order lama dibayar. Ini disengaja, bukan bug.

---

## 3. Scope

### In scope

- `DateRangePicker` di toolbar `ListProduk.vue`.
- Mengirim `tanggalAwalOrder` / `tanggalAkhirOrder` di `useProdukStore.onIndex()`.
- Keterangan periode pada header kolom statistik (§6).
- Penanganan error 400 (§8).

### Out of scope

- **Halaman Chat** (`src/pages/chat/tambah-pesanan`, `src/pages/chat/edit-pesanan`) juga memanggil `GET /produk`, tapi **tidak mengirim tanggal** dan **tidak boleh diubah**. Statistik di sana tetap total sepanjang waktu.
- Mengurutkan produk berdasarkan statistik periode (mis. "terlaris bulan ini"). Backend belum mendukung.
- Filter berdasarkan tanggal bayar.
- Menyimpan periode di URL atau `localStorage`.

---

## 4. Komponen

Gunakan `DateRangePicker` (`src/components/common/DateRangePicker.vue`), komponen yang sama dengan filter tanggal di Dashboard dan Pesanan.

| Prop | Nilai |
|---|---|
| `v-model` | `filter.periode` (`{ start, end } \| null`) |
| `calendarMode` | `'date'`. User memilih tanggal saja; jamnya ditentukan saat serialisasi (§7). |
| `placeholder` | *"Semua periode"* |
| `label` | *"Periode order masuk"* |

Tidak diperlukan komponen baru.

Kalau komponen belum punya tombol "hapus pilihan", tambahkan tombol **Reset periode** di sebelahnya. Komponen sudah mengembalikan `null` saat pilihan dikosongkan.

---

## 5. State Management (`useProdukStore`)

```ts
filter: {
  // … filter existing (page, limit, search)
  periode: { start: string; end: string } | null   // awal: null
}
```

| Kejadian | Aksi |
|---|---|
| Periode dipilih / diubah / dikosongkan | `page = 1`, lalu `onIndex()` |
| Search berubah | periode **dipertahankan** |
| Workspace aktif berubah | periode di-reset ke `null` |
| Kembali ke halaman Produk dari halaman lain | periode di-reset ke `null` (tidak disimpan) |

**Default periode: kosong (semua periode).** Ini sengaja berbeda dengan Dashboard yang default ke bulan berjalan, supaya halaman Produk tetap terlihat sama seperti sekarang saat dibuka.

> `onIndex()` dipakai bersama halaman Chat. Jangan menambahkan periode ke parameter default `onIndex()`. Teruskan sebagai argumen opsional, atau pakai action terpisah, supaya halaman Chat tidak ikut mengirim periode yang tersisa di store.

---

## 6. Tampilan

- **Posisi:** toolbar di atas tabel, di sebelah kotak pencarian.
- **Keterangan periode:** saat periode aktif, tampilkan teks kecil di atas tabel, misalnya *"Statistik order masuk 1 Nov 2026 – 30 Nov 2026"*. Saat tidak aktif: *"Statistik sepanjang waktu"*.
- **Tooltip** pada header kolom *Dibayar* / *Rasio Dibayar*: *"Order yang masuk dalam periode dan sudah dibayar, kapan pun pembayarannya."*
- **Loading:** pakai state loading tabel yang sudah ada saat `onIndex()` berjalan.
- Kolom dan format angka tidak berubah. `rasioDibayar` tetap string dari backend (mis. `"25.00%"`), tidak dihitung ulang di frontend.

---

## 7. Kontrak API & Serialisasi

```
GET /produk?workspaceId=1&page=1&limit=10&search=kopi
           &tanggalAwalOrder=2026-11-01 00:00:00
           &tanggalAkhirOrder=2026-11-30 23:59:59
```

| Field | Format | Nilai |
|---|---|---|
| `tanggalAwalOrder` | `yyyy-MM-dd HH:mm:ss` | tanggal awal + **`00:00:00`** |
| `tanggalAkhirOrder` | `yyyy-MM-dd HH:mm:ss` | tanggal akhir + **`23:59:59`**. Tanpa ini, order di hari terakhir periode tidak ikut terhitung. |

Aturan:

- `periode = null` → **jangan kirim** kedua param.
- Format harus persis `yyyy-MM-dd HH:mm:ss`, sama dengan filter tanggal di `GET /order`. Format lain (ISO dengan `T`/`Z`) ditolak backend dengan 400. Kalau sudah ada helper format tanggal di modul Pesanan, pakai helper yang sama.
- Backend juga menerima salah satu tanggal saja, tetapi `DateRangePicker` selalu mengirim keduanya, jadi kasus itu tidak perlu ditangani di UI.

Respons tidak berubah bentuknya (Spring Page dengan `content[]` berisi `totalOrder`, `totalDibayar`, `totalTerjual`, `rasioDibayar`).

---

## 8. Validasi & Error Handling

| Kondisi | Penanganan |
|---|---|
| Tanggal awal > tanggal akhir | Seharusnya dicegah `DateRangePicker`. Kalau tetap terkirim, backend membalas `400` dengan `message`; tampilkan lewat `alertStore`. |
| Format tanggal salah | Bug serialisasi (§7). Backend membalas `400`; tampilkan `message` lewat `alertStore`. |
| Gagal jaringan | Alert error yang sudah ada. Tabel tetap menampilkan data terakhir. |

---

## 9. Acceptance Criteria

| # | Kriteria |
|---|---|
| AC-1 | Tanpa periode, halaman Produk tampil **persis seperti sekarang** (angka sepanjang waktu), dan request tidak membawa `tanggalAwalOrder` / `tanggalAkhirOrder`. |
| AC-2 | Memilih periode mengubah angka Total Order, Dibayar, Rasio Dibayar, dan Terjual, tapi **daftar produk, urutan, dan total halaman tetap sama**. |
| AC-3 | Produk tanpa order di periode itu tampil `0` dan `0%`, dan tidak hilang dari tabel. |
| AC-4 | Order yang masuk pada hari terakhir periode ikut terhitung (`tanggalAkhirOrder` berakhiran `23:59:59`). |
| AC-5 | Request di DevTools memakai format `yyyy-MM-dd HH:mm:ss`. |
| AC-6 | Mengubah atau mengosongkan periode mengembalikan tabel ke halaman 1. |
| AC-7 | Periode tetap aktif saat user mengetik pencarian atau berpindah halaman tabel. |
| AC-8 | Keterangan periode di atas tabel sesuai dengan periode yang dipilih. |
| AC-9 | **Regresi:** form tambah/edit pesanan di Chat tetap menampilkan produk dengan statistik sepanjang waktu walaupun periode sedang aktif di halaman Produk. |
| AC-10 | Berpindah workspace mengosongkan periode. |
| AC-11 | Error 400 tampil sebagai alert berisi `message` dari backend. |

Contoh data uji untuk QA, satu produk dengan periode **1–30 Nov 2026**:

| Order masuk | Status sekarang | Dibayar | Dihitung Order? | Dihitung Dibayar? |
|---|---|---|---|---|
| 31 Okt | PAID | 2 Nov | ✗ | ✗ |
| 5 Nov | PAID | 5 Nov | ✓ | ✓ |
| 10 Nov | UNPAID | n/a | ✓ | ✗ |
| 15 Nov | DELETE | n/a | ✗ | ✗ |
| 29 Nov | PAID | 3 Des | ✓ | ✓ |
| 1 Des | PAID | 1 Des | ✗ | ✗ |

Hasil yang diharapkan: Total Order **3**, Dibayar **2**, Rasio **66.67%**.

---

## 10. Ketergantungan & Rollout

1. Backend sudah siap dan kompatibel ke belakang: tanpa param tanggal, respons sama seperti sebelumnya.
2. Frontend bisa di-deploy kapan saja setelah backend.
3. Perbarui `docs/frontend/business-modules.md` (`ListProduk`: filter periode) bersamaan dengan implementasi.
