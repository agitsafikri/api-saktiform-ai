# TDD — Setelan Tampilan Produk: Sembunyikan Label & Sembunyikan Harga (Frontend)

| Field | Value |
|---|---|
| Feature | **Setelan Tampilan Produk** — `hideFormLabel` & `hidePrice` per produk |
| Dokumen terkait | [TDD Konfigurasi Form Produk — Frontend](./produk-form-config-frontend.md), [TDD Backend](./produk-form-config.md) |
| Stack (Dashboard) | Vue 3 `<script setup>` + TypeScript, Pinia Options API, SCSS utility classes |
| Status | Draft for Implementation — **backend sudah terimplementasi & terverifikasi** |
| Last updated | 2026-07-30 |
| Target pembaca | Frontend Developer (Dashboard & Checkout), Reviewer, QA, Product Owner |

> Dokumen ini sengaja dipisah dari TDD Konfigurasi Form Produk karena cakupannya berbeda:
> ini **setelan tingkat produk**, bukan konfigurasi per-field. Keduanya hanya bersinggungan
> pada satu titik — `hideFormLabel` mengubah cara label hasil konfigurasi form dirender.

---

## 1. Ringkasan

Dua *toggle* per produk yang mengubah tampilan halaman checkout:

| Setelan | Efek | Default |
|---|---|---|
| `hideFormLabel` | Menyembunyikan **seluruh** label field pada form checkout | `false` |
| `hidePrice` | Menyembunyikan tampilan harga pada halaman checkout | `false` |

**Backend hanya membawa benderanya.** Seluruh perilaku visual adalah keputusan frontend — tidak ada satu pun perhitungan di server yang berubah. Harga tetap diambil dari varian dan tetap tersimpan pada order; label tetap dikirim lengkap pada `formConfig`.

Itu berarti dokumen ini adalah **satu-satunya tempat** perilaku kedua setelan tersebut didefinisikan. Bila tidak diimplementasikan di frontend, mengaktifkan toggle tidak menghasilkan efek apa pun.

---

## 2. Kontrak API

Terverifikasi terhadap backend yang berjalan.

### 2.1 Menulis — `POST /produk`

```jsonc
{
  "idWorkspace": 2,
  "namaProduk": "…",
  "urlCheckout": "…",
  // … atribut produk lainnya …
  "hideFormLabel": true,
  "hidePrice": true
}
```

Keduanya **opsional**. Tidak mengirimnya sama dengan mengirim `false` — klien lama tidak berubah perilakunya.

### 2.2 Membaca

| Endpoint | Auth | Memuat |
|---|---|---|
| `GET /produk/{id}` | Bearer JWT | `hideFormLabel`, `hidePrice` |
| `GET /produk/checkout?urlCheckout=` | Publik | `hideFormLabel`, `hidePrice` |

```jsonc
// GET /produk/checkout
{
  "success": true,
  "data": {
    "id": "…",
    "namaProduk": "Produk Tanpa Label",
    "atributProduk": [{ "id": "…", "deskripsi": "1 Pcs", "harga": 50000, "berat": 200 }],
    "formConfig": [ /* … */ ],
    "hideFormLabel": true,
    "hidePrice": true
  }
}
```

Perlu dicatat: ketika `hidePrice` aktif, `atributProduk[].harga` **tetap dikirim**. Penyembunyian sepenuhnya di sisi render. Jangan berasumsi harga akan bernilai `null`.

### 2.3 Perilaku lain yang sudah terverifikasi

| Perilaku | Keadaan |
|---|---|
| Copy produk | Kedua setelan ikut tersalin |
| Update sebagian (satu `true`, satu `false`) | Bekerja independen |
| Produk lama tanpa setelan | Terbaca `false`, bukan `null` |

---

## 3. Keputusan yang Harus Diambil Sebelum Implementasi

### 3.1 `hidePrice` — apa persisnya yang disembunyikan?

Ini pertanyaan produk, bukan teknis, dan jawabannya mengubah pekerjaan secara material. Halaman checkout menampilkan nominal di beberapa tempat:

| # | Tempat | Contoh |
|---|---|---|
| 1 | Harga produk di bagian atas | `Rp 89.000` |
| 2 | Harga pada tiap pilihan varian | `1 Pcs — Rp 89.000` |
| 3 | Subtotal | `Rp 89.000` |
| 4 | Ongkos kirim | `Rp 12.000` |
| 5 | Biaya tambahan COD | (dari `ProdukPembayaran.config`) |
| 6 | Total | `Rp 101.000` |
| 7 | Teks tombol, bila memuat nominal | `Pesan Sekarang — Rp 101.000` |

Tiga opsi:

| Opsi | Yang disembunyikan | Konsekuensi |
|---|---|---|
| **A — sembunyikan semua nominal** (rekomendasi) | 1–7 | Pelanggan memesan tanpa melihat nominal apa pun. Konsisten dan tidak ambigu. Cocok bila harga memang disampaikan lewat WhatsApp (lihat §7) |
| B — sembunyikan harga produk saja | 1, 2, 3 | Total tetap terlihat. Ganjil: pelanggan melihat total tetapi tidak tahu asalnya |
| C — sembunyikan harga produk, tampilkan ongkir & total | 1, 2, 3 | Sama seperti B |

**Rekomendasi: opsi A.** Alasannya, opsi B dan C menghasilkan halaman yang secara logis tidak konsisten — menampilkan hasil penjumlahan sambil menyembunyikan sukunya. Bila tujuannya menyembunyikan harga, menyembunyikannya setengah-setengah tidak mencapai tujuan itu sekaligus membingungkan.

> **Konsekuensi opsi A yang wajib disetujui Product Owner:** pelanggan menekan tombol pesan **tanpa mengetahui nominal yang akan ditagih**. Itu keputusan bisnis. Bila tidak dapat diterima, pilih opsi B/C dan dokumen ini disesuaikan.

Sampai keputusan diambil, implementasikan opsi A di balik satu konstanta agar mudah diubah:

```ts
/** Lihat TDD §3.1. Ubah di sini bila kebijakan berubah. */
const HIDE_PRICE_SCOPE = {
  productPrice: true,
  variantPrice: true,
  subtotal: true,
  shipping: true,
  total: true,
  buttonAmount: true,
}
```

### 3.2 `hideFormLabel` — bagaimana menandai field wajib?

Tanda `*` lazimnya menempel pada label. Bila label disembunyikan, tanda itu ikut hilang.

| Opsi | Cara | Catatan |
|---|---|---|
| **A — sisipkan ke placeholder** (rekomendasi) | `placeholder="Masukkan nama lengkap *"` | Tidak menambah elemen; terlihat pada field kosong — persis saat pelanggan perlu tahu |
| B — tanda `*` kecil di sudut input | elemen absolut di dalam kotak input | Perlu penyesuaian CSS per tipe input; berisiko menutupi teks |
| C — tidak ditandai sama sekali | — | Pelanggan baru tahu setelah submit gagal |

Rekomendasi **opsi A**, dan hanya diterapkan ketika `hideFormLabel` aktif.

---

## 4. Dashboard — Implementasi

### 4.1 Penempatan

Kedua toggle adalah setelan **produk**, bukan konfigurasi field. Menempatkannya di dalam tab "Konfigurasi Form" akan menyesatkan — pengguna akan mengira setelan itu bagian dari daftar field.

Penempatan yang ditetapkan: seksi baru **"Tampilan Checkout"** pada tab **Informasi Produk**, di dekat `narasiTombol` dan `warnaTombol` yang juga bersifat tampilan.

```
┌────────────────────────────────────────────────────────────┐
│  Tampilan Checkout                                         │
│                                                            │
│  [ ○——]  Sembunyikan semua label form                      │
│          Pelanggan hanya melihat placeholder sebagai        │
│          petunjuk pengisian.                               │
│                                                            │
│  [ ○——]  Sembunyikan harga                                 │
│          Harga, ongkir, dan total tidak ditampilkan di      │
│          halaman checkout.                                 │
└────────────────────────────────────────────────────────────┘
```

Teks penjelas di bawah tiap toggle bersifat wajib, bukan hiasan — keduanya punya konsekuensi yang tidak terduga dari namanya saja.

### 4.2 Komponen

Pakai `SwitchButton` yang sudah ada. **Gotcha:** komponen ini mengemisikan `input`, bukan `update:modelValue`, sehingga **tidak dapat dipakai dengan `v-model`**:

```vue
<SwitchButton
  :value="produk.hideFormLabel"
  label="Sembunyikan semua label form"
  @input="produk.hideFormLabel = $event"
/>
<p class="fz-px-12 color-grey">
  Pelanggan hanya melihat placeholder sebagai petunjuk pengisian.
</p>

<SwitchButton
  :value="produk.hidePrice"
  label="Sembunyikan harga"
  @input="produk.hidePrice = $event"
/>
<p class="fz-px-12 color-grey">
  Harga, ongkir, dan total tidak ditampilkan di halaman checkout.
</p>
```

### 4.3 `produkStore.ts`

| # | Perubahan |
|---|---|
| 1 | `initProduk` — tambahkan `hideFormLabel: false`, `hidePrice: false` |
| 2 | Payload `POST /produk` — sertakan keduanya |
| 3 | `onShow` (detail produk) — isi dari respons; `?? false` untuk produk lama |

```ts
// initProduk
hideFormLabel: false,
hidePrice: false,

// saat memuat detail
this.produk.hideFormLabel = data.hideFormLabel ?? false
this.produk.hidePrice     = data.hidePrice ?? false
```

Tipe pada model produk: `hideFormLabel: boolean` dan `hidePrice: boolean` — bukan `boolean | null`. Normalisasi `?? false` dilakukan sekali saat memuat, sehingga sisa kode tidak perlu menangani `null`.

### 4.4 Peringatan proaktif ketika `hideFormLabel` aktif

Ketika label disembunyikan, `placeholder` menjadi satu-satunya petunjuk. Field aktif yang `placeholder`-nya kosong akan tampil sebagai kotak isian tanpa keterangan apa pun — pelanggan tidak tahu harus mengisi apa.

Dashboard **wajib** memperingatkan hal ini, karena pemilik produk tidak akan menyadarinya sampai ada pesanan yang salah:

```
⚠ 3 field aktif belum memiliki placeholder. Ketika label disembunyikan,
  field tersebut akan tampil tanpa petunjuk apa pun.
  → Nomor WhatsApp, Provinsi, Catatan Tambahan          [Perbaiki]
```

Peringatan muncul di seksi "Tampilan Checkout" ketika `hideFormLabel === true`, dihitung dari `formConfigStore.fields`:

```ts
const fieldsWithoutPlaceholder = computed(() =>
  formConfigStore.fields
    .filter(f => f.isActive && !f.placeholder?.trim())
    .map(f => f.label),
)
```

Tombol "Perbaiki" membuka tab Konfigurasi Form. Peringatan bersifat informatif — **jangan** memblokir penyimpanan; ada kasus sah di mana form memang tidak memerlukan petunjuk.

### 4.5 Pratinjau form

`FormPreview.vue` (pada tab Konfigurasi Form) sebaiknya menghormati `hideFormLabel`, agar pemilik produk dapat melihat akibatnya tanpa membuka halaman checkout. Ini juga cara paling efektif menyampaikan konsekuensi setelan tersebut.

Karena pratinjau hidup di komponen berbeda dari toggle, nilainya dibaca dari `produkStore`, bukan diteruskan lewat props berlapis.

---

## 5. Checkout — Implementasi

### 5.1 `hideFormLabel`

**Sembunyikan secara visual, bukan dari pohon aksesibilitas.** Ini ketentuan yang tidak bisa ditawar: memakai `display: none` atau tidak merender elemen `<label>` sama sekali akan membuat form tidak terbaca pembaca layar — pengguna tunanetra kehilangan seluruh kemampuan mengisi form.

```vue
<label :for="fieldId" :class="hideFormLabel ? 'sr-only' : 'form-label'">
  {{ field.label }}<span v-if="field.isRequired" aria-hidden="true"> *</span>
</label>

<input
  :id="fieldId"
  :placeholder="effectivePlaceholder(field)"
  :aria-required="field.isRequired"
/>
```

```scss
/* Terlihat oleh pembaca layar, tidak oleh mata. */
.sr-only {
  position: absolute;
  width: 1px; height: 1px;
  padding: 0; margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
```

Placeholder efektif, sesuai keputusan §3.2:

```ts
function effectivePlaceholder(field: FormFieldCheckout): string {
  const base = field.placeholder ?? ''
  if (!hideFormLabel.value) return base
  if (!field.isRequired) return base
  return base ? `${base} *` : `${field.label} *`
}
```

Baris terakhir adalah jaring pengaman: bila `placeholder` kosong, pakai `label` sebagai placeholder. Lebih baik daripada kotak kosong tanpa petunjuk. Dashboard sudah memperingatkan kasus ini (§4.4), namun renderer tetap harus tahan terhadapnya.

**`helpText` tetap ditampilkan** meski label disembunyikan. `hideFormLabel` menyembunyikan label, bukan seluruh teks pembantu — dan `helpText` justru menjadi lebih berharga ketika label hilang.

**Pesan galat tetap memakai label lengkap.** Server mengembalikan `"Nomor WhatsApp wajib diisi."` Menampilkannya apa adanya, di dekat field yang bersangkutan, tetap benar — pelanggan memahami field mana yang dimaksud dari posisinya. Jangan mencoba menulis ulang pesan agar tanpa label.

### 5.2 `hidePrice`

Mengikuti opsi A (§3.1) — seluruh nominal disembunyikan.

| Elemen | Ketika `hidePrice` aktif |
|---|---|
| Harga produk | Tidak dirender |
| Pilihan varian | Hanya `deskripsi` (`1 Pcs`), tanpa nominal |
| Baris subtotal, ongkir, biaya COD | Tidak dirender |
| Baris total | Tidak dirender |
| Teks tombol | `narasiTombol` apa adanya, tanpa penambahan nominal |

Yang **tidak** berubah:

| Aspek | Alasan |
|---|---|
| Pemilihan varian tetap wajib | `idAtributProduk` tetap dikirim ke `POST /order/create` |
| Cascading lokasi & pemilihan kecamatan tetap wajib | Ongkir tetap dihitung server meski tidak ditampilkan |
| Galat `SHIPPING_RATE_NOT_FOUND` tetap ditampilkan | Kecamatan tanpa data ongkir tetap memblokir pesanan; pelanggan tetap perlu tahu |
| Payload `POST /order/create` | Tidak ada perubahan sama sekali |

Poin ketiga layak diperhatikan: meski nominal disembunyikan, kegagalan perhitungan ongkir tetap membuat pesanan gagal. Pesan galatnya tidak menyebut nominal, jadi aman ditampilkan apa adanya.

**Varian tanpa harga harus tetap dapat dibedakan.** Bila `deskripsi` varian selama ini mengandalkan harga untuk membedakan (mis. dua varian sama-sama bernama "Paket"), menyembunyikan harga membuat pilihan menjadi ambigu. Ini bukan sesuatu yang dapat diperbaiki renderer — sarankan pada dashboard agar `deskripsi` varian dibuat deskriptif ketika `hidePrice` aktif.

---

## 6. Edge Case

| ID | Skenario | Perilaku |
|---|---|---|
| EC-1 | `hideFormLabel` aktif, ada field aktif tanpa placeholder | Renderer memakai `label` sebagai placeholder (§5.1); dashboard memperingatkan (§4.4) |
| EC-2 | `hideFormLabel` aktif, field lokasi (`PROVINCE`/`CITY`/`DISTRICT`) | Placeholder bawaan sudah bermakna ("Pilih provinsi") — tidak perlu perlakuan khusus |
| EC-3 | `hideFormLabel` aktif, pembaca layar | Label tetap terbaca lewat `.sr-only`; wajib diuji, bukan diasumsikan |
| EC-4 | `hideFormLabel` aktif, galat validasi muncul | Pesan galat memakai label lengkap dan ditampilkan di dekat field |
| EC-5 | `hidePrice` aktif, varian tidak dapat dibedakan tanpa harga | Renderer menampilkan `deskripsi` apa adanya; perbaikan ada di sisi data produk |
| EC-6 | `hidePrice` aktif, kecamatan tanpa data ongkir | Galat tetap ditampilkan; pesanan tetap gagal |
| EC-7 | `hidePrice` aktif, metode COD punya biaya tambahan | Biaya tetap dihitung server dan tersimpan pada order; tidak ditampilkan |
| EC-8 | Kedua toggle aktif bersamaan | Independen — tidak ada interaksi di antara keduanya |
| EC-9 | Produk lama (kolom `null` di DB) | Terbaca `false` dari API; tidak perlu penanganan khusus |
| EC-10 | Produk hasil copy | Setelan ikut tersalin (terverifikasi backend) |
| EC-11 | `hidePrice` aktif, pelanggan menerima pesan WhatsApp | **Harga dan total tetap muncul di pesan** — lihat §7 |

---

## 7. Konsistensi dengan Pesan WhatsApp

Temuan yang wajib dipahami sebelum implementasi.

Templat pesan pada `MessageConstructorHelper` memuat variabel `{harga_produk}` dan `{total}` — dipakai pada pesan konfirmasi maupun follow-up COD/Transfer, termasuk kalimat seperti *"kakak bisa menyiapkan {total}"* dan *"Silahkan transfer senilai {total}"*.

Artinya: **`hidePrice` hanya menyembunyikan harga di halaman checkout, tidak di WhatsApp.** Pelanggan yang tidak melihat nominal apa pun saat memesan akan menerima harga dan total beberapa saat kemudian lewat pesan.

Dua kemungkinan pembacaan, dan keduanya masuk akal:

1. **Memang disengaja** — model bisnisnya menyampaikan harga lewat percakapan, bukan di halaman. Bila demikian, tidak ada yang perlu diubah, dan perilaku ini justru inti fiturnya.
2. **Tidak disengaja** — pemilik produk mengira harga tersembunyi sepenuhnya. Bila demikian, templat pesan perlu varian tanpa nominal ketika `hidePrice` aktif, dan itu **pekerjaan backend** yang berada di luar cakupan dokumen ini.

Perlu konfirmasi Product Owner. Sampai terkonfirmasi, dashboard sebaiknya menyampaikannya secara jujur pada teks penjelas toggle:

> Harga, ongkir, dan total tidak ditampilkan di halaman checkout. Harga tetap dikirim
> pada pesan WhatsApp konfirmasi pesanan.

Kalimat kedua itu mencegah kesalahpahaman yang baru ketahuan setelah pelanggan komplain.

---

## 8. Checklist QA

### 8.1 Dashboard

| # | Kasus | Ekspektasi |
|---|---|---|
| 1 | Buat produk baru dengan kedua toggle mati | `hideFormLabel:false`, `hidePrice:false` tersimpan |
| 2 | Aktifkan kedua toggle lalu simpan | Tersimpan `true`/`true`; muat ulang halaman → tetap `true` |
| 3 | Aktifkan satu, matikan satu | Bekerja independen |
| 4 | Buka produk lama (dibuat sebelum fitur ini) | Kedua toggle tampil mati, tanpa galat |
| 5 | Copy produk yang toggle-nya aktif | Salinan mewarisi kedua setelan |
| 6 | `hideFormLabel` aktif + ada field tanpa placeholder | Peringatan muncul beserta daftar nama field |
| 7 | Perbaiki placeholder lalu kembali | Peringatan hilang |
| 8 | Peringatan tidak memblokir simpan | Produk tetap tersimpan |
| 9 | Pratinjau form mengikuti `hideFormLabel` | Label hilang pada pratinjau |
| 10 | `SwitchButton` memakai `@input`, bukan `v-model` | Nilai benar-benar berubah (kesalahan ini gagal secara senyap) |

### 8.2 Checkout — `hideFormLabel`

| # | Kasus | Ekspektasi |
|---|---|---|
| 11 | Toggle mati | Tampilan identik dengan sebelum fitur ini |
| 12 | Toggle aktif | Seluruh label hilang secara visual |
| 13 | **Pembaca layar** (NVDA/VoiceOver) | Setiap field tetap dibacakan labelnya |
| 14 | Inspeksi DOM | `<label>` tetap ada dengan kelas `.sr-only`; **bukan** `display:none` |
| 15 | Field wajib | Placeholder berakhiran ` *` |
| 16 | Field wajib tanpa placeholder | Placeholder memakai label + ` *` |
| 17 | `helpText` | Tetap tampil |
| 18 | Submit dengan field wajib kosong | Pesan galat memakai label lengkap, tampil di dekat field |
| 19 | Field lokasi | Placeholder "Pilih provinsi/kota/kecamatan" tetap tampil |
| 20 | Navigasi keyboard (Tab) | Urutan fokus tidak berubah |

### 8.3 Checkout — `hidePrice`

| # | Kasus | Ekspektasi |
|---|---|---|
| 21 | Toggle mati | Seluruh nominal tampil seperti sebelumnya |
| 22 | Toggle aktif | Harga produk, harga varian, subtotal, ongkir, dan total tidak tampil |
| 23 | Pilihan varian | Hanya `deskripsi`, tanpa nominal |
| 24 | Teks tombol | `narasiTombol` apa adanya, tanpa nominal |
| 25 | Pemilihan varian tetap wajib | Submit tanpa varian tetap ditolak |
| 26 | Cascading lokasi tetap wajib | Kecamatan tetap harus dipilih |
| 27 | Kecamatan tanpa ongkir | Galat `SHIPPING_RATE_NOT_FOUND` tetap tampil |
| 28 | Order berhasil dibuat | `harga`, `ongkos_kirim` pada tabel `order` **tetap terisi benar** |
| 29 | Pesan WhatsApp setelah order | Harga & total tetap muncul (§7) — dipastikan sesuai keputusan PO |
| 30 | Inspeksi respons API | `atributProduk[].harga` tetap dikirim — penyembunyian murni di render |

Butir 28 dan 30 penting: keduanya memastikan `hidePrice` benar-benar hanya kosmetik dan tidak diam-diam merusak data.

---

## 9. Ringkasan Pekerjaan

| Area | Berkas | Sifat |
|---|---|---|
| Dashboard | `modules/produk/stores/produkStore.ts` | Tambah 2 atribut pada `initProduk`, payload, dan pemuatan detail |
| Dashboard | `modules/produk/components/FormProduk.vue` | Seksi "Tampilan Checkout" berisi 2 `SwitchButton` + teks penjelas |
| Dashboard | `modules/produk/components/form-config/FormPreview.vue` | Hormati `hideFormLabel` |
| Dashboard | seksi peringatan placeholder | Baru; bersifat informatif |
| Checkout | *renderer* form | `.sr-only` pada label + placeholder efektif |
| Checkout | blok harga & ringkasan | Kondisional atas `hidePrice` |
| Checkout | SCSS | Tambah kelas `.sr-only` bila belum ada |

Tidak ada perubahan backend yang diperlukan — seluruh kontrak sudah tersedia dan terverifikasi.

## 10. Open Questions

| ID | Pertanyaan | Usulan default | Penanggung jawab |
|---|---|---|---|
| OQ-1 | Cakupan `hidePrice`: seluruh nominal, atau harga produk saja? | Seluruh nominal (opsi A, §3.1) | Product Owner |
| OQ-2 | Apakah harga di pesan WhatsApp perlu ikut disembunyikan? | Tidak pada rilis ini; sampaikan apa adanya pada teks penjelas toggle (§7) | Product Owner |
| OQ-3 | Cara menandai field wajib ketika label disembunyikan | Sufiks ` *` pada placeholder (opsi A, §3.2) | Product Owner, Frontend |
| OQ-4 | Apakah kedua toggle perlu dibatasi peran tertentu? | Ikut aturan simpan produk yang berlaku sekarang | Product Owner |

## 11. Riwayat Dokumen

| Versi | Tanggal | Perubahan | Penulis |
|---|---|---|---|
| 0.1 | 2026-07-30 | Draf awal. Kontrak API terverifikasi terhadap backend berjalan; tiga keputusan produk diangkat beserta rekomendasi; 30 butir checklist QA; temuan konsistensi harga pada pesan WhatsApp | Frontend & System Analysis |
