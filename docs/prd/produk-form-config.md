# PRD — Konfigurasi Form Produk (Product Form Builder)

| Field | Value |
|---|---|
| Feature name | Konfigurasi Form Produk (System Field + Custom Field) |
| Component | `ProdukFormConfig` (extend), `OrderCustomField` (baru), `ProdukService`, `OrderService`, `OrderOrchestrationService`, `ProdukController`, `OrderController`, halaman Konfigurasi Produk & halaman Checkout |
| Status | Draft — Ready for Review |
| Scope | Per-Produk, di dalam Workspace (multi-tenant); backend + frontend dashboard + frontend checkout |
| Author | Product Management & System Analysis |
| Last updated | 2026-07-28 |
| Target pembaca | Product Manager, System Analyst, Backend Developer, Frontend Developer, QA Engineer |

> **Ringkasan Eksekutif.** Dokumen ini mendefinisikan fitur **Konfigurasi Form Produk**, yang memungkinkan setiap Produk memiliki definisi form checkout sendiri. Form terdiri atas dua kategori field: **System Field** (enam field bawaan yang selalu ada, dapat diubah tampilannya namun tidak dapat diubah kontrak teknisnya) dan **Custom Field** (field tambahan bebas milik masing-masing produk). Frontend checkout hanya membaca **satu** sumber konfigurasi form dan merender secara dinamis. Data System Field tetap disimpan pada tabel `order` agar seluruh proses bisnis existing (ongkir, WhatsApp, export Excel, dashboard, duplicate checking, reporting, pencarian) tidak terganggu; data Custom Field disimpan pada tabel baru `order_custom_field` dengan mekanisme **snapshot** label agar perubahan konfigurasi di masa depan tidak mengubah makna order lama.
>
> **Catatan penting hasil analisis codebase.** Tabel `produk_form_config` dan entity `ProdukFormConfig` **sudah ada** pada codebase saat ini, namun implementasinya belum lengkap dan belum memiliki konsep `field_key` maupun kategori SYSTEM/CUSTOM. Fitur ini karena itu bukan pembangunan dari nol, melainkan **evolusi terkontrol atas struktur existing** yang membawa konsekuensi migrasi dan kompatibilitas. Rincian kondisi existing dibahas pada §1.3 dan §22.

---

## 1. Latar Belakang

### 1.1 Konteks Produk

SaktiForm adalah platform *conversational commerce* multi-tenant. Setiap sumber daya berada di bawah satu `Workspace` (tenant). Alur bisnis utama yang relevan dengan dokumen ini adalah **alur checkout produk**: pelanggan membuka halaman checkout publik sebuah produk (`GET /produk/checkout?urlCheckout=...`), mengisi form data pengiriman, lalu mengirimkan pesanan (`POST /order/create`). Pesanan yang tercatat kemudian memicu pengiriman pesan WhatsApp konfirmasi/follow-up melalui `OrderCreatedEvent` → `OrderEventListener`.

Kedua endpoint tersebut merupakan **endpoint publik tanpa autentikasi** (`/produk/checkout/**` dan `/order/create/**` terdaftar pada whitelist `SecurityConfig`). Fakta ini menjadi salah satu pertimbangan desain terpenting pada dokumen ini, khususnya pada bagian Validasi (§18) dan Security Consideration (§23).

### 1.2 Kondisi Form Checkout Saat Ini

Form checkout saat ini memiliki enam input bawaan yang bersifat wajib (*mandatory*):

| No | Field (tampilan saat ini) | Kolom pada tabel `order` | Tipe data |
|---|---|---|---|
| 1 | Nama | `nama_penerima` | `text` |
| 2 | Nomor WhatsApp | `nomor_whatsapp` | `text` |
| 3 | Alamat | `alamat` | `text` |
| 4 | Provinsi | `id_provinsi` | `integer` (FK `province`) |
| 5 | Kota | `id_kota` | `integer` (FK `city`) |
| 6 | Kecamatan | `id_kecamatan` | `integer` (FK `district`) |

Keenam field ini tidak berdiri sendiri sebagai data tampilan; keenamnya adalah **input bagi proses bisnis inti**:

| Proses bisnis | Ketergantungan | Bukti pada codebase |
|---|---|---|
| Perhitungan ongkos kirim | `id_kecamatan` (dipasangkan dengan kota gudang) | `OrderService.createOrderInternal()` → `ongkirRepository.findByIdOriginCityAndIdDistrict(gudang.getIdKota(), data.getIdKecamatan())` |
| Integrasi WhatsApp | `nomor_whatsapp` (dinormalisasi), `nama_penerima` (isi pesan) | `PhoneNumberUtil.normalizeToIndonesianFormat()`, `MessageConstructorHelper.confirmationMessage(namaProduk, namaPenerima)` |
| Pembuatan/penautan Contact & Conversation | `nomor_whatsapp`, `nama_penerima` | `OrderService.getOrCreateContact(phoneNumber, namaLengkap, idWorkspace)` |
| Export Excel | seluruh enam field | projection `ExportOrderListDto` (`getNamaCustomer`, `getNomorWhatsapp`, `getAlamat`, `getProvinsi`, `getKota`, `getKecamatan`) |
| Dashboard & Reporting | `id_provinsi`, `id_kota`, `id_kecamatan` | filter pada `GET /order`, view `TotalOrderReportView`, `TotalPendapatanReportView` |
| Duplicate checking (abandoned order) | `nama_penerima` + 4 digit terakhir `nomor_whatsapp` | `abandonedOrderRepository.deletedAbandonedOrder(namaLengkap, nomorWhatsapp.substring(len-4))` |
| Pencarian data | `nama_penerima`, `nomor_whatsapp` | parameter `search` pada `GET /order` |

Konsekuensi arsitektural: **keenam field ini tidak dapat diperlakukan sebagai field dinamis biasa.** Field tersebut wajib tetap menempati kolom bertipe kuat (*strongly typed*) pada tabel `order`, karena dipakai oleh *native query*, *interface projection*, *join* ke tabel master lokasi, dan agregasi laporan. Memindahkannya ke penyimpanan generik berbentuk *key–value* akan memaksa penulisan ulang seluruh query di atas dan menurunkan performa serta keterbacaan secara signifikan.

Adapun label dan placeholder keenam field tersebut saat ini bersifat **statis** — ditentukan di sisi frontend (atau tersimpan tanpa kontrak yang jelas, lihat §1.3), sehingga seluruh produk pada seluruh workspace menampilkan teks yang sama.

### 1.3 Kondisi Existing pada Codebase (Baseline Teknis)

Hasil pembacaan codebase menunjukkan bahwa fondasi fitur ini **sudah ada sebagian** dan wajib diperhitungkan oleh implementor.

**a. Entity dan tabel sudah ada** — `com.saktiform.api.entity.ProdukFormConfig`, tabel `produk_form_config`, dengan kolom:

| Kolom | Tipe | Catatan |
|---|---|---|
| `id` | `bigint` IDENTITY | PK |
| `id_produk` | `uuid` | relasi `@ManyToOne` ke `Produk` (`insertable=false, updatable=false`) + kolom skalar |
| `tipe_field` | `text` | tipe field, belum tervalidasi terhadap daftar tipe tertentu |
| `label` | `text` | — |
| `placeholder` | `text` | — |
| `orders` | `integer` | urutan tampil; **nama kolom fisik adalah `orders`**, dipetakan ke atribut Java `order` |
| `is_mandatory` | `boolean` | penanda wajib |
| `created_at` | `timestamp` | — |
| `updated_at` | `timestamp` | — |

**b. Repository sudah ada** — `ProdukFormConfigRepository` dengan `getProdukFormConfigsByIdProduk(UUID)` dan `deleteProdukFormConfigByIdProduk(UUID)`.

**c. Konfigurasi dibawa inline oleh payload produk** — `AddProdukDto` memiliki `List<ProdukFormConfigDto> formConfig` dengan anotasi `@NotNull(message = "Form Config Wajib Diisi.")`. `ProdukFormConfigDto` adalah `@Value` (immutable) berisi `tipeField`, `label`, `placeholder`, `order`, `isMandatory`.

**d. Pola simpan saat ini adalah *delete-and-reinsert*** — pada `ProdukService.saveProduct()`, ketika produk di-update, seluruh baris konfigurasi dihapus (`deleteProdukFormConfigByIdProduk`) lalu disisipkan ulang dari payload. Selain itu, blok penyimpanan yang ada **hanya mengisi** `idProduk`, `label`, `placeholder`, dan `tipeField` — kolom `orders`, `is_mandatory`, `created_at`, dan `updated_at` **tidak pernah diisi** saat penyimpanan, meskipun keempatnya dibaca kembali pada `getDetailProduk`, `getCheckoutProduk`, dan `copyProduk`. Akibatnya nilai-nilai tersebut selalu `NULL` pada data yang tersimpan.

**e. Belum ada `field_key`, kategori, maupun atribut lanjutan** — tidak ada identitas field yang stabil, tidak ada pembeda SYSTEM/CUSTOM, dan tidak ada `options`, `default_value`, `help_text`, atau `is_active`.

**f. Belum ada penyimpanan nilai field dinamis** — tabel `order_custom_field` belum ada, dan `CreateOrderDto` hanya menerima sembilan field tetap (`idProduk`, `idAtributProduk`, `namaLengkap`, `nomorWhatsapp`, `alamat`, `idProvinsi`, `idKota`, `idKecamatan`, `metodePembayaran`, `source`).

**Implikasi.** Konfigurasi form yang tersimpan hari ini adalah daftar field tanpa identitas — tidak mungkin diketahui secara pasti baris mana yang mewakili "Nomor WhatsApp" selain dengan menebak dari teks `label`. Karena itu fitur ini **wajib** memperkenalkan `field_key` dan menjalankan **backfill** atas data existing (§22).

### 1.4 Kebutuhan Bisnis

Pemilik produk (Workspace Owner/Admin) menyampaikan dua kebutuhan:

1. **Penyesuaian bahasa form per produk.** Produk hadiah membutuhkan "Nama Penerima"; produk jasa membutuhkan "Nama Lengkap Customer"; produk B2B membutuhkan "Nama PIC". Satu label global tidak dapat melayani semuanya.
2. **Pengumpulan data spesifik produk.** Produk kaos memerlukan Ukuran dan Warna; produk *event* memerlukan Tanggal Acara; produk cetak memerlukan Upload Desain; produk *reseller* memerlukan akun Instagram. Saat ini kebutuhan tersebut dipaksa masuk ke kolom Alamat atau dikumpulkan manual melalui percakapan WhatsApp, yang meningkatkan beban agen dan risiko kesalahan input.

---

## 2. Permasalahan

| ID | Permasalahan | Dampak Bisnis | Dampak Teknis |
|---|---|---|---|
| P-1 | Label dan placeholder form bersifat statis dan global | Form terasa tidak relevan dengan konteks produk; menurunkan tingkat konversi checkout; pelanggan salah mengisi kolom | Teks tertanam di frontend/konfigurasi tanpa kontrak, tidak dapat dikelola per produk |
| P-2 | Tidak tersedia mekanisme menambah field baru per produk | Data spesifik produk (ukuran, warna, tanggal acara) tidak terkumpul di titik checkout | Setiap kebutuhan field baru menuntut perubahan skema dan rilis backend — *time to market* lambat |
| P-3 | Data tambahan dikumpulkan lewat percakapan manual | Beban kerja agen naik, waktu pemrosesan pesanan bertambah, risiko *human error* dan pesanan salah kirim | Data tidak terstruktur, tidak dapat diekspor maupun dilaporkan |
| P-4 | Field tambahan sering "dititipkan" pada kolom `alamat` atau `notes` | Kualitas data alamat menurun; berisiko mengganggu akurasi pengiriman | Kolom bertipe kuat tercemar data heterogen; pencarian dan pelaporan menjadi tidak akurat |
| P-5 | Konfigurasi form existing tidak memiliki identitas field yang stabil | Perubahan label berpotensi mengubah makna data historis | Tidak ada `field_key`; pemetaan ke logika bisnis hanya dapat ditebak dari teks label |
| P-6 | Pola *delete-and-reinsert* pada penyimpanan produk | Konfigurasi berisiko hilang saat produk disimpan ulang dari layar yang tidak memuat form config | `id` baris selalu berubah; referensi apa pun ke `produk_form_config.id` menjadi tidak stabil |
| P-7 | Frontend berpotensi harus membaca dua sumber konfigurasi (bawaan vs tambahan) | — | Duplikasi logika *render*, urutan tampil tidak konsisten, biaya pemeliharaan berlipat |

---

## 3. Tujuan

### 3.1 Tujuan Bisnis

| ID | Tujuan | Metrik Keberhasilan |
|---|---|---|
| G-1 | Setiap produk dapat memiliki form checkout yang relevan dengan karakternya | ≥ 60% produk aktif memodifikasi minimal satu label System Field dalam 90 hari setelah rilis |
| G-2 | Data spesifik produk terkumpul terstruktur pada saat checkout | ≥ 40% produk aktif memiliki minimal satu Custom Field aktif dalam 90 hari |
| G-3 | Mengurangi percakapan susulan untuk melengkapi data pesanan | Penurunan ≥ 25% pesan agen berkategori "permintaan kelengkapan data" |
| G-4 | Mempercepat pemenuhan kebutuhan field baru | Waktu pemenuhan kebutuhan field baru turun dari hitungan rilis (mingguan) menjadi hitungan menit (konfigurasi mandiri) |

### 3.2 Tujuan Teknis

| ID | Tujuan |
|---|---|
| G-5 | Menyediakan **satu** sumber konfigurasi form (`produk_form_config`) yang memuat System Field maupun Custom Field, sehingga frontend hanya melakukan satu kali pembacaan dan satu kali *render loop* |
| G-6 | Menjamin **stabilitas kontrak** melalui `field_key` yang bersifat *immutable* untuk System Field |
| G-7 | Menjamin **integritas historis** melalui *snapshot* `field_key` dan `field_label` pada `order_custom_field` |
| G-8 | Menjaga **kompatibilitas mundur penuh** (*backward compatibility*) bagi klien dan integrasi yang belum mengenal Custom Field |
| G-9 | Menjamin **nol dampak** pada proses bisnis yang bergantung pada kolom System Field di tabel `order` (ongkir, WhatsApp, export, dashboard, duplicate checking, reporting, search) |
| G-10 | Menormalkan struktur `produk_form_config` existing tanpa kehilangan data dan tanpa operasi DDL yang destruktif (kompatibel dengan `spring.jpa.hibernate.ddl-auto=update`) |

### 3.3 Non-Goals

Lihat §5 (Out of Scope) untuk daftar lengkap beserta alasannya.

---

## 4. Scope

### 4.1 In Scope — Backend

| Item | Keterangan |
|---|---|
| Ekstensi entity `ProdukFormConfig` | Penambahan `field_key`, `field_category`, `options`, `default_value`, `help_text`, `is_active`, penormalan `is_mandatory` dan `orders` |
| Entity baru `OrderCustomField` | Penyimpanan nilai Custom Field per order, dengan *snapshot* `field_key`, `field_label`, `field_type` |
| Endpoint konfigurasi form | `GET /produk/{id}/form-config`, `PUT /produk/{id}/form-config` |
| Seeding otomatis System Field | Enam System Field dibuat otomatis pada setiap produk baru dan pada seluruh produk existing melalui *schema initializer* |
| Ekstensi payload checkout | `ProdukCheckoutDto.formConfig` diperkaya atribut baru; urut berdasarkan `sortOrder` |
| Ekstensi pembuatan order | `CreateOrderDto` menerima `customFields`; validasi dinamis berbasis konfigurasi produk |
| Ekstensi detail order | `DetailOrderDto` menyertakan `customFields` hasil *snapshot* |
| Validasi dinamis server-side | Validasi *required*, tipe, panjang, dan kesesuaian `options` berdasarkan konfigurasi aktif produk |
| Penyesuaian `ProdukService.saveProduct()` | Mengganti *delete-and-reinsert* menjadi *upsert by field_key* agar System Field tidak pernah hilang |
| Migrasi & backfill | Seeding System Field, pemetaan data legacy, normalisasi nilai `NULL` |

### 4.2 In Scope — Frontend Dashboard

- Halaman/tab **Konfigurasi Form** pada form Produk: dua seksi (System Field dan Custom Field), *drag & drop* pengurutan lintas kedua seksi, editor atribut per field, editor `options`.
- Penonaktifan kontrol yang tidak boleh diubah pada System Field (field key, tipe, required, hapus, aktif/nonaktif).
- Pratinjau (*preview*) form hasil konfigurasi.
- Tampilan Custom Field pada halaman Detail Order.

### 4.3 In Scope — Frontend Checkout

- *Render* form sepenuhnya dinamis berdasarkan `formConfig` dari `GET /produk/checkout`.
- Validasi sisi klien yang mencerminkan konfigurasi (tanpa menggantikan validasi server).
- Pengiriman `customFields` pada `POST /order/create`.

### 4.4 In Scope — QA

Uji unit untuk validator dinamis, uji integrasi untuk `PUT form-config` dan `POST /order/create`, uji regresi untuk ongkir/WhatsApp/export/dashboard/search, serta uji migrasi pada salinan basis data produksi.

---

## 5. Out of Scope

| ID | Item | Alasan |
|---|---|---|
| OOS-1 | Custom Field pada tabel `abandoned_order` | Abandoned order menangkap data parsial sebelum submit final; menambah *branch* penyimpanan meningkatkan kompleksitas tanpa manfaat langsung. Direncanakan pada Fase 2 (§24) |
| OOS-2 | *Conditional logic* antar field (tampilkan B jika A = X) | Menuntut mesin aturan dan evaluator ekspresi di dua sisi (server & klien); nilai bisnisnya belum terverifikasi |
| OOS-3 | Field *multi-step* / *wizard* / *section grouping* | Perubahan besar pada UX checkout; belum menjadi kebutuhan yang disampaikan |
| OOS-4 | Perhitungan harga berbasis Custom Field (mis. biaya tambahan bordir) | Bersinggungan dengan modul `AtributProduk`, `ProdukEkstra`, dan `ProdukPembayaran`; wajib dirancang sebagai fitur harga tersendiri |
| OOS-5 | Custom Field sebagai template lintas produk (*form template library*) | Fitur produktivitas; ditunda hingga pola pemakaian nyata terlihat |
| OOS-6 | Penambahan, penghapusan, atau perubahan makna System Field | Setiap perubahan daftar System Field adalah perubahan skema `order` dan perubahan logika bisnis, bukan konfigurasi |
| OOS-7 | Kolom Custom Field pada Export Excel order | Export saat ini memakai *interface projection* dengan kolom tetap; kolom dinamis menuntut desain ulang generator Excel. Direncanakan Fase 2 (§24) |
| OOS-8 | Kemampuan bot AI membaca Custom Field sebagai konteks RAG | Tidak menghalangi nilai inti fitur; dapat menyusul |
| OOS-9 | Versioning konfigurasi form beserta riwayat perubahannya | Kebutuhan audit belum dinyatakan; mitigasi minimal sudah tercakup oleh mekanisme *snapshot* |
| OOS-10 | Internasionalisasi (i18n) label per bahasa | Basis pengguna saat ini tunggal bahasa (Indonesia) |

---

## 6. Business Rules

Bagian ini adalah rujukan normatif utama bagi Developer dan QA. Setiap aturan diberi identitas `BR-n` dan wajib memiliki kasus uji yang bersesuaian.

### 6.1 Aturan Umum

| ID | Aturan | Rationale |
|---|---|---|
| BR-1 | Seluruh konfigurasi form berlaku **per Produk**. Tidak ada konfigurasi tingkat Workspace yang menimpa konfigurasi produk | Kebutuhan yang disampaikan bersifat per produk; konfigurasi tingkat workspace akan menimbulkan ambiguitas presedensi |
| BR-2 | Satu produk memiliki **tepat satu** himpunan konfigurasi form, tersimpan pada satu tabel `produk_form_config`, memuat kategori SYSTEM maupun CUSTOM | Memenuhi G-5: frontend membaca satu konfigurasi dan melakukan satu *render loop* |
| BR-3 | Setiap baris konfigurasi memiliki `field_category` bernilai `SYSTEM` atau `CUSTOM`. Nilai lain ditolak | Diskriminator eksplisit lebih aman daripada menyimpulkan kategori dari `field_key` |
| BR-4 | `field_key` **unik per produk** (case-insensitive), lintas kategori | Mencegah tabrakan kunci antara Custom Field dan System Field, serta menjamin pemetaan nilai yang tidak ambigu |
| BR-5 | `sort_order` menentukan urutan *render* form, dihitung dalam **satu ruang urutan bersama** untuk SYSTEM dan CUSTOM | Memungkinkan Custom Field ditempatkan di antara System Field (mis. "Ukuran Baju" setelah "Nama") |
| BR-6 | Perubahan konfigurasi form **tidak mengubah** data order yang sudah tersimpan | Integritas historis (G-7); order adalah catatan transaksi, bukan tampilan |
| BR-7 | Konfigurasi form hanya dapat dibaca/diubah oleh pengguna yang berhak atas Workspace pemilik produk | Isolasi tenant sesuai konvensi codebase |
| BR-8 | Endpoint checkout publik hanya mengembalikan field dengan `is_active = true`, terurut menurut `sort_order` naik | Field nonaktif adalah field yang dipensiunkan; tidak boleh tampil pada checkout baru |

### 6.2 Aturan System Field

| ID | Aturan | Rationale |
|---|---|---|
| BR-9 | Terdapat **tepat enam** System Field, dengan `field_key`: `customer_name`, `phone_number`, `address`, `province`, `city`, `district` | Daftar tertutup yang mencerminkan kolom pada tabel `order` |
| BR-10 | Keenam System Field **selalu ada** pada setiap produk. Sistem menjamin keberadaannya melalui seeding otomatis saat produk dibuat dan melalui *self-healing* saat konfigurasi dibaca | Menjamin form checkout selalu dapat memenuhi kebutuhan proses bisnis inti |
| BR-11 | System Field **tidak dapat dihapus**. Permintaan penghapusan ditolak dengan galat `SYSTEM_FIELD_NOT_DELETABLE` | Penghapusan akan melumpuhkan ongkir, WhatsApp, dan pengiriman |
| BR-12 | `field_key` System Field **tidak dapat diubah** | `field_key` adalah kontrak antara frontend, validator, dan *mapper* ke kolom `order` |
| BR-13 | Atribut System Field yang **dapat** dikonfigurasi hanya: `label`, `placeholder`, `sort_order`, `help_text` | Sesuai kebutuhan yang disampaikan; membatasi permukaan perubahan menekan risiko |
| BR-14 | Atribut System Field yang **tidak dapat** diubah: `field_key`, `field_type`, `is_required`, `is_active`, `options`, `default_value`, pemetaan ke kolom `order`, dan validasi utamanya | Seluruhnya terikat pada logika bisnis dan tipe kolom basis data |
| BR-15 | `is_required` System Field **terkunci bernilai `true`** | Ongkir memerlukan `district`; WhatsApp memerlukan `phone_number`; pengiriman memerlukan `address`; `province` dan `city` diperlukan untuk konsistensi hierarki lokasi dan pelaporan. Menjadikannya opsional akan menghasilkan order yang tidak dapat diproses. Lihat Keputusan D-4 (§26) |
| BR-16 | `is_active` System Field **terkunci bernilai `true`**; System Field tidak dapat dinonaktifkan | Konsekuensi langsung dari BR-10 dan BR-15 |
| BR-17 | Perubahan `label` System Field **tidak** mengubah `field_key` maupun kolom penyimpanan. Contoh: label "Nama" diubah menjadi "Nama Penerima" — `field_key` tetap `customer_name`, penyimpanan tetap `order.nama_penerima` | Inti dari pemisahan lapisan presentasi dan lapisan data |
| BR-18 | Nilai System Field pada saat submit **tetap disimpan ke tabel `order`** pada kolom bertipe kuat, **bukan** ke `order_custom_field` | Menjaga seluruh proses bisnis di §1.2 tetap bekerja tanpa perubahan |
| BR-19 | Percobaan mengirim `field_key` System Field di dalam koleksi `customFields` pada `POST /order/create` ditolak dengan galat `SYSTEM_FIELD_IN_CUSTOM_PAYLOAD` | Mencegah jalur ganda penulisan nilai System Field yang dapat menimbulkan inkonsistensi dan celah keamanan |

### 6.3 Aturan Custom Field

| ID | Aturan | Rationale |
|---|---|---|
| BR-20 | Custom Field dapat ditambahkan tanpa batas fungsional, dengan **batas teknis 50 field aktif per produk** | Tanpa batas, endpoint publik terbuka terhadap penggelembungan *payload* dan degradasi *render*. Angka 50 jauh di atas kebutuhan nyata sekaligus menjaga performa. Lihat D-6 (§26) |
| BR-21 | `field_key` Custom Field dihasilkan sistem melalui *slugify* atas `label` (huruf kecil, `snake_case`, ASCII), dengan sufiks numerik bila terjadi tabrakan (`warna`, `warna_2`) | Menghindari kesalahan pengguna dalam menetapkan kunci teknis, sekaligus menjamin BR-4 |
| BR-22 | `field_key` Custom Field bersifat **immutable setelah dibuat**, meskipun `label`-nya diubah | Kunci adalah identitas; label adalah presentasi. Mengubah kunci akan memutus keterhubungan dengan data order historis |
| BR-23 | Custom Field **dapat dihapus permanen (*hard delete*) hanya apabila belum pernah dipakai** oleh order mana pun (tidak ada baris `order_custom_field` dengan `field_key` tersebut pada produk tersebut) | Menjaga integritas historis |
| BR-24 | Custom Field yang **sudah pernah dipakai** tidak dapat dihapus permanen; sistem menawarkan **penonaktifan** (`is_active = false`). Permintaan hapus ditolak dengan galat `FIELD_IN_USE` beserta jumlah pemakaian | *Soft retirement* mempertahankan riwayat sekaligus memenuhi kebutuhan pengguna untuk menghentikan pemakaian field |
| BR-25 | Field dengan `is_active = false` **tidak ditampilkan** pada checkout baru, **tidak divalidasi**, dan **tidak disimpan** untuk order baru; namun nilainya pada order lama tetap dapat dibaca dan ditampilkan | Memenuhi kebutuhan "Field Inactive tidak ditampilkan pada checkout baru" tanpa merusak data historis |
| BR-26 | `is_required` Custom Field **dapat dikonfigurasi** oleh pengguna (`true`/`false`) | Custom Field tidak menopang logika bisnis inti, sehingga aman untuk dijadikan opsional |
| BR-27 | Tipe Custom Field terbatas pada: `TEXT`, `TEXTAREA`, `NUMBER`, `EMAIL`, `SELECT`, `RADIO`, `CHECKBOX`, `DATE`, `FILE` | Daftar tertutup; tipe di luar daftar ditolak `400` |
| BR-28 | `options` **wajib** dan berisi minimal satu entri untuk tipe `SELECT`, `RADIO`, `CHECKBOX`; **wajib kosong/null** untuk tipe lainnya | Mencegah konfigurasi yang tidak dapat dirender |
| BR-29 | `option.value` **unik** di dalam satu field (case-insensitive) | Nilai ganda membuat pemilihan menjadi ambigu |
| BR-30 | `default_value` (bila diisi) **wajib valid** terhadap tipe dan `options` field tersebut | Mencegah form ter-*render* dalam keadaan tidak valid sejak awal |
| BR-31 | Tipe `CHECKBOX` bersifat **multi-nilai**; nilainya disimpan sebagai larik JSON. Tipe lainnya bersifat nilai tunggal | Semantik alami tipe *checkbox group* |
| BR-32 | Nilai Custom Field disimpan ke tabel `order_custom_field`, satu baris per field, beserta ***snapshot*** `field_key`, `field_label`, dan `field_type` pada saat order dibuat | Memenuhi G-7. Bila label kelak berubah dari "Ukuran Baju" menjadi "Ukuran (S/M/L)", order lama tetap menampilkan label sebagaimana dilihat pelanggan saat memesan |
| BR-33 | Custom Field dengan nilai kosong dan `is_required = false` **tidak menghasilkan baris** pada `order_custom_field` | Menghindari baris kosong yang tidak bermakna dan menekan ukuran tabel |
| BR-34 | Penghapusan produk (`is_deleted = true`) tidak menghapus baris `order_custom_field` milik order-order produk tersebut | Order adalah catatan keuangan; harus tetap dapat diaudit |
| BR-35 | Penyalinan produk (`GET /produk/copy`) menyalin **seluruh** konfigurasi form (SYSTEM dan CUSTOM) beserta `field_key`, `options`, dan `sort_order` | Konsisten dengan perilaku `copyProduk` existing yang menyalin konfigurasi form; menyalin `field_key` tetap aman karena keunikan berlaku per produk (BR-4) |

### 6.4 Aturan Submit Order

| ID | Aturan | Rationale |
|---|---|---|
| BR-36 | Validasi submit dievaluasi terhadap **konfigurasi aktif produk pada saat submit**, bukan terhadap konfigurasi yang dikirim klien | Klien tidak boleh menjadi otoritas validasi; endpoint bersifat publik |
| BR-37 | `customFields` yang memuat `field_key` tidak dikenal, nonaktif, atau bukan milik produk tersebut **diabaikan secara senyap atau ditolak**, sesuai mode yang ditetapkan pada §18.4 | Mencegah penulisan data liar oleh pihak ketiga |
| BR-38 | Kegagalan validasi Custom Field menyebabkan **seluruh** pembuatan order dibatalkan (*all-or-nothing*, satu transaksi) | Order dan nilai field-nya adalah satu kesatuan makna |
| BR-39 | Order yang dibuat melalui jalur chat (`source = CST_CHAT`) dan jalur abandoned (`source` mengandung `abandoned`) **tidak diwajibkan** mengirim Custom Field, meskipun field tersebut `required` | Kedua jalur tersebut adalah entri oleh agen/sistem, bukan pengisian form oleh pelanggan. Memaksakan *required* akan memblokir operasional agen. Lihat D-7 (§26) |
| BR-40 | Nilai Custom Field bertipe `FILE` disimpan sebagai URL objek hasil unggah, bukan sebagai *binary* | Konsisten dengan pola `StorageService`/MinIO existing |

---

## 7. User Story

### 7.1 Persona

| Persona | Peran | Kepentingan |
|---|---|---|
| **Rina** — Workspace Owner / Admin | Mengelola katalog produk dan konfigurasi checkout | Menginginkan form checkout yang sesuai karakter produk dan mengumpulkan data yang ia butuhkan |
| **Dedi** — Agent | Menangani percakapan dan membuat order atas nama pelanggan | Menginginkan data tambahan pesanan tanpa harus bertanya ulang kepada pelanggan |
| **Sari** — Customer | Pembeli akhir yang mengisi form checkout | Menginginkan form yang jelas, singkat, dan tidak membingungkan |
| **Bagas** — Backend Developer | Mengimplementasikan dan memelihara sistem | Menginginkan kontrak data yang stabil dan tidak merusak proses existing |
| **Tika** — QA Engineer | Memverifikasi fitur | Menginginkan aturan yang tegas dan dapat diuji |

### 7.2 Daftar User Story

| ID | Sebagai | Saya ingin | Sehingga | Prioritas |
|---|---|---|---|---|
| US-1 | Admin | mengubah label System Field per produk | teks form sesuai konteks produk (misalnya "Nama Penerima" untuk produk hadiah) | Must |
| US-2 | Admin | mengubah placeholder System Field per produk | pelanggan memperoleh contoh pengisian yang tepat | Must |
| US-3 | Admin | menambahkan help text pada System Field | pelanggan memahami instruksi khusus (misalnya "Gunakan nomor yang aktif WhatsApp") | Should |
| US-4 | Admin | mengatur urutan tampil seluruh field | alur pengisian form terasa alami | Must |
| US-5 | Admin | melihat dengan jelas bahwa System Field tidak dapat dihapus atau diubah tipenya | saya tidak merusak konfigurasi tanpa sengaja | Must |
| US-6 | Admin | menambahkan Custom Field beserta tipe, label, dan placeholder | saya dapat mengumpulkan data spesifik produk | Must |
| US-7 | Admin | menetapkan sebuah Custom Field sebagai wajib atau opsional | data kritikal pasti terisi, sementara data pelengkap tidak menghambat konversi | Must |
| US-8 | Admin | mendefinisikan daftar pilihan untuk field `SELECT`, `RADIO`, dan `CHECKBOX` | pelanggan memilih dari opsi baku sehingga data seragam | Must |
| US-9 | Admin | menetapkan nilai bawaan (default value) sebuah field | pengisian menjadi lebih cepat bagi pelanggan | Should |
| US-10 | Admin | menonaktifkan Custom Field yang sudah tidak dipakai | field tersebut berhenti tampil pada checkout baru tanpa menghapus riwayat order | Must |
| US-11 | Admin | menghapus permanen Custom Field yang belum pernah dipakai | konfigurasi tetap bersih dari percobaan yang gagal | Must |
| US-12 | Admin | melihat pratinjau form hasil konfigurasi | saya yakin tampilan sudah benar sebelum dipublikasikan | Should |
| US-13 | Admin | memperoleh peringatan ketika menghapus field yang sudah dipakai | saya tidak kehilangan data historis | Must |
| US-14 | Customer | melihat form checkout yang labelnya sesuai produk yang saya beli | saya tidak salah mengisi kolom | Must |
| US-15 | Customer | memperoleh pesan galat yang jelas ketika pengisian saya tidak valid | saya dapat memperbaikinya dengan cepat | Must |
| US-16 | Customer | mengunggah berkas (misalnya desain atau foto) pada form checkout | pesanan kustom saya dapat diproses tanpa percakapan tambahan | Could |
| US-17 | Agent | melihat nilai Custom Field pada halaman Detail Order | saya dapat memproses pesanan tanpa bertanya ulang kepada pelanggan | Must |
| US-18 | Agent | melihat label Custom Field sebagaimana yang dilihat pelanggan saat memesan | saya tidak salah menafsirkan data order lama | Must |
| US-19 | Admin | menyalin produk beserta konfigurasi form-nya | saya tidak perlu menyusun ulang form untuk produk sejenis | Should |
| US-20 | Backend Developer | memperoleh `field_key` yang stabil dan tidak berubah | mapper, validator, dan integrasi tidak rusak ketika admin mengubah label | Must |

---

## 8. Acceptance Criteria

Ditulis dalam format Given–When–Then. Setiap kriteria wajib memiliki kasus uji otomatis atau manual yang bersesuaian.

### AC-1 — Seeding System Field pada produk baru

- **Given** seorang Admin membuat produk baru melalui `POST /produk`
- **When** produk berhasil tersimpan
- **Then** sistem membuat tepat enam baris `produk_form_config` berkategori `SYSTEM`, dengan `field_key` berturut-turut `customer_name`, `phone_number`, `address`, `province`, `city`, `district`, `sort_order` 1–6, `is_required = true`, `is_active = true`, serta label dan placeholder bawaan.

### AC-2 — Pembacaan konfigurasi form

- **Given** produk P memiliki enam System Field dan dua Custom Field aktif
- **When** Admin memanggil `GET /produk/{id}/form-config`
- **Then** respons memuat delapan field, terurut menurut `sortOrder` naik, masing-masing menyertakan `fieldKey`, `fieldCategory`, `fieldType`, `label`, `placeholder`, `helpText`, `isRequired`, `isActive`, `defaultValue`, `options`, `sortOrder`, `usageCount`, dan `editableAttributes`.

### AC-3 — Perubahan label System Field

- **Given** produk P memiliki System Field `customer_name` berlabel "Nama"
- **When** Admin mengirim `PUT /produk/{id}/form-config` dengan `label = "Nama Penerima"`
- **Then** respons `200`, nilai `label` tersimpan menjadi "Nama Penerima", **dan** `field_key` tetap `customer_name`, `field_type` tetap `TEXT`, `is_required` tetap `true`.

### AC-4 — Penolakan perubahan atribut terkunci System Field

- **Given** produk P memiliki System Field `phone_number`
- **When** Admin mengirim `PUT /produk/{id}/form-config` yang mengubah `fieldKey` menjadi `wa_number`, atau `fieldType` menjadi `TEXTAREA`, atau `isRequired` menjadi `false`, atau `isActive` menjadi `false`
- **Then** respons `400` dengan kode galat `SYSTEM_FIELD_IMMUTABLE_ATTRIBUTE`, dan tidak ada perubahan yang tersimpan (*all-or-nothing*).

### AC-5 — Penolakan penghapusan System Field

- **Given** produk P memiliki enam System Field
- **When** Admin mengirim `PUT /produk/{id}/form-config` yang menghilangkan salah satu System Field dari daftar
- **Then** respons `400` dengan kode `SYSTEM_FIELD_NOT_DELETABLE`, dan keenam System Field tetap utuh di basis data.

### AC-6 — Penambahan Custom Field

- **Given** produk P belum memiliki Custom Field
- **When** Admin menambahkan field berlabel "Ukuran Baju" bertipe `SELECT` dengan `options` `[S, M, L, XL]` dan `isRequired = true`
- **Then** respons `200`, tersimpan satu baris berkategori `CUSTOM` dengan `field_key = ukuran_baju` (hasil *slugify* sesuai §11.5), dan field tersebut muncul pada respons `GET /produk/checkout`.

### AC-7 — Keunikan field_key

- **Given** produk P telah memiliki Custom Field berlabel "Warna" (`field_key = warna`)
- **When** Admin menambahkan Custom Field baru yang juga berlabel "Warna"
- **Then** sistem menghasilkan `field_key = warna_2` dan kedua field tersimpan tanpa galat.

### AC-8 — Validasi options wajib

- **Given** Admin menambahkan Custom Field bertipe `RADIO`
- **When** `options` dikirim kosong atau `null`
- **Then** respons `400` dengan kode `OPTIONS_REQUIRED_FOR_TYPE`.

### AC-9 — Validasi options terlarang

- **Given** Admin menambahkan Custom Field bertipe `TEXT`
- **When** `options` dikirim berisi entri
- **Then** respons `400` dengan kode `OPTIONS_NOT_ALLOWED_FOR_TYPE`.

### AC-10 — Urutan tampil lintas kategori

- **Given** produk P memiliki System Field pada `sortOrder` 1–6
- **When** Admin menempatkan Custom Field "Ukuran Baju" pada `sortOrder = 2` dan menggeser field lainnya
- **Then** `GET /produk/checkout` mengembalikan urutan `customer_name` (1), `ukuran_baju` (2), `phone_number` (3), dan seterusnya secara berkesinambungan tanpa celah.

### AC-11 — Field nonaktif tidak tampil pada checkout

- **Given** produk P memiliki Custom Field `warna` dengan `isActive = false`
- **When** halaman checkout memanggil `GET /produk/checkout?urlCheckout=...`
- **Then** `formConfig` pada respons tidak memuat `warna`.

### AC-12 — Field nonaktif tidak divalidasi dan tidak disimpan

- **Given** produk P memiliki Custom Field `warna` dengan `isRequired = true` namun `isActive = false`
- **When** pelanggan mengirim `POST /order/create` tanpa menyertakan `warna`
- **Then** order berhasil dibuat, dan tidak ada baris `order_custom_field` untuk `warna`.

### AC-13 — Penyimpanan nilai System Field

- **Given** produk P memiliki System Field `customer_name` berlabel "Nama Penerima"
- **When** pelanggan mengirim order dengan `namaLengkap = "Sari Dewi"`
- **Then** nilai tersimpan pada `order.nama_penerima = 'Sari Dewi'`, dan tidak ada baris `order_custom_field` bagi `customer_name`.

### AC-14 — Penyimpanan nilai Custom Field beserta snapshot

- **Given** produk P memiliki Custom Field `ukuran_baju` berlabel "Ukuran Baju" bertipe `SELECT`
- **When** pelanggan mengirim order dengan `customFields = [{ "fieldKey": "ukuran_baju", "value": "L" }]`
- **Then** tersimpan satu baris `order_custom_field` dengan `id_order` order tersebut, `field_key = 'ukuran_baju'`, `field_label = 'Ukuran Baju'`, `field_type = 'SELECT'`, `field_value = 'L'`, dan `sort_order` sesuai konfigurasi saat submit.

### AC-15 — Integritas snapshot terhadap perubahan konfigurasi

- **Given** order O telah dibuat ketika label field adalah "Ukuran Baju"
- **When** Admin mengubah label field tersebut menjadi "Ukuran (S/M/L)"
- **Then** `GET /order/{O}` tetap menampilkan `fieldLabel = "Ukuran Baju"`, sedangkan order yang dibuat setelah perubahan menampilkan "Ukuran (S/M/L)".

### AC-16 — Validasi Custom Field wajib

- **Given** produk P memiliki Custom Field `tanggal_acara` (`DATE`, `isRequired = true`, `isActive = true`)
- **When** pelanggan mengirim order tanpa `tanggal_acara` atau dengan nilai kosong
- **Then** respons `400` dengan `errors[0].code = REQUIRED_FIELD_MISSING` dan `errors[0].field = "tanggal_acara"`, dan tidak ada order yang tersimpan.

### AC-17 — Validasi nilai di luar options

- **Given** produk P memiliki Custom Field `ukuran_baju` (`SELECT`, options `[S, M, L]`)
- **When** pelanggan mengirim `value = "XXL"`
- **Then** respons `400` dengan kode `VALUE_NOT_IN_OPTIONS`.

### AC-18 — Penolakan System Field di dalam payload customFields

- **Given** produk P memiliki System Field `customer_name`
- **When** pelanggan mengirim `customFields = [{ "fieldKey": "customer_name", "value": "nilai sisipan" }]`
- **Then** respons `400` dengan kode `SYSTEM_FIELD_IN_CUSTOM_PAYLOAD`, dan `order.nama_penerima` tidak terpengaruh.

### AC-19 — Penghapusan Custom Field yang belum dipakai

- **Given** Custom Field `instagram` pada produk P belum pernah dipakai order mana pun
- **When** Admin menghilangkan field tersebut dari daftar pada `PUT /produk/{id}/form-config`
- **Then** respons `200` dan baris `produk_form_config` tersebut terhapus permanen.

### AC-20 — Penolakan penghapusan Custom Field yang sudah dipakai

- **Given** Custom Field `ukuran_baju` pada produk P telah dipakai oleh 12 order
- **When** Admin menghilangkan field tersebut dari daftar
- **Then** respons `400` dengan kode `FIELD_IN_USE`, `meta.usageCount = 12`, dan pesan yang menyarankan penonaktifan.

### AC-21 — Batas jumlah Custom Field

- **Given** produk P telah memiliki 50 Custom Field aktif
- **When** Admin menambahkan Custom Field ke-51
- **Then** respons `400` dengan kode `CUSTOM_FIELD_LIMIT_EXCEEDED`.

### AC-22 — Kompatibilitas mundur klien lama

- **Given** klien checkout versi lama yang tidak mengirim atribut `customFields`
- **When** klien tersebut mengirim `POST /order/create` untuk produk yang tidak memiliki Custom Field wajib
- **Then** order berhasil dibuat sebagaimana perilaku sebelum fitur ini dirilis.

### AC-23 — Isolasi tenant

- **Given** produk P milik Workspace A
- **When** pengguna Workspace B memanggil `GET` atau `PUT /produk/{P}/form-config`
- **Then** respons `404`, bukan `403`, sehingga keberadaan sumber daya tidak terungkap.

### AC-24 — Nol regresi pada proses bisnis existing

- **Given** produk P dengan seluruh label System Field telah diubah dan lima Custom Field aktif
- **When** order dibuat melalui checkout publik
- **Then** ongkos kirim terhitung benar, pesan WhatsApp konfirmasi terkirim, order tampil pada `GET /order` beserta filter provinsi/kota/kecamatan, tercakup pada Export Excel, terhitung pada dashboard dan report, serta dapat ditemukan melalui parameter `search`.

### AC-25 — Penyalinan produk

- **Given** produk P memiliki enam System Field terkonfigurasi dan tiga Custom Field beserta options
- **When** Admin memanggil `GET /produk/copy?idProduk={P}`
- **Then** produk hasil salinan memiliki sembilan baris konfigurasi dengan `field_key`, `options`, `sort_order`, `is_required`, dan `is_active` yang identik.

---

## 9. Functional Requirements

### 9.1 Manajemen Konfigurasi Form

| ID | Requirement |
|---|---|
| FR-1 | Sistem MUST menyediakan `GET /produk/{id}/form-config` yang mengembalikan seluruh baris konfigurasi (SYSTEM dan CUSTOM, aktif dan nonaktif) untuk keperluan layar konfigurasi, terurut menurut `sortOrder` naik. |
| FR-2 | Sistem MUST menyediakan `PUT /produk/{id}/form-config` yang menerima keseluruhan daftar konfigurasi (semantik *full replace* dengan *upsert by* `field_key`) dan menerapkannya secara atomik. |
| FR-3 | Sistem MUST melakukan seeding enam System Field secara otomatis pada setiap produk baru, dengan label, placeholder, tipe, dan urutan bawaan sesuai §11.4. |
| FR-4 | Sistem MUST bersifat *self-healing*: bila pada saat pembacaan ditemukan System Field yang belum ada pada sebuah produk, sistem MUST membuatnya dengan nilai bawaan sebelum mengembalikan respons. |
| FR-5 | Sistem MUST menolak perubahan atas `fieldKey`, `fieldType`, `isRequired`, `isActive`, `options`, dan `defaultValue` pada baris berkategori `SYSTEM`. |
| FR-6 | Sistem MUST mengizinkan perubahan atas `label`, `placeholder`, `helpText`, dan `sortOrder` pada baris berkategori `SYSTEM`. |
| FR-7 | Sistem MUST menolak permintaan yang mengakibatkan hilangnya salah satu dari enam System Field. |
| FR-8 | Sistem MUST mengizinkan penambahan Custom Field dengan atribut `label` (wajib), `fieldType` (wajib), `placeholder`, `helpText`, `isRequired`, `defaultValue`, `options`, `sortOrder`, dan `isActive`. |
| FR-9 | Sistem MUST menghasilkan `fieldKey` Custom Field secara otomatis melalui *slugify* atas `label`, dengan penyelesaian tabrakan berbasis sufiks numerik. |
| FR-10 | Sistem MUST mempertahankan `fieldKey` Custom Field yang sudah ada meskipun `label`-nya diubah. |
| FR-11 | Sistem MUST mengizinkan penghapusan permanen Custom Field hanya bila `usageCount = 0`; bila lebih besar dari nol, permintaan MUST ditolak beserta informasi `usageCount`. |
| FR-12 | Sistem MUST mengizinkan penonaktifan (`isActive = false`) Custom Field kapan pun, termasuk yang sudah dipakai. |
| FR-13 | Sistem MUST menormalkan `sortOrder` menjadi rentang berurutan 1..N setelah setiap penyimpanan, untuk mencegah nilai ganda maupun celah urutan. |
| FR-14 | Sistem MUST membatasi jumlah Custom Field aktif per produk maksimum 50. |
| FR-15 | Sistem MUST memvalidasi kelengkapan dan keunikan `options` sesuai BR-28 dan BR-29. |
| FR-16 | Sistem MUST memvalidasi `defaultValue` terhadap tipe dan `options` field sesuai BR-30. |
| FR-17 | Sistem MUST menerapkan seluruh perubahan `PUT /produk/{id}/form-config` dalam satu transaksi; kegagalan pada satu baris MUST membatalkan seluruh permintaan. |
| FR-18 | Sistem MUST memvalidasi bahwa produk yang dirujuk berada di bawah Workspace yang berhak diakses pemanggil; bila tidak, MUST mengembalikan `404`. |

### 9.2 Penyajian Konfigurasi ke Halaman Checkout

| ID | Requirement |
|---|---|
| FR-19 | `GET /produk/checkout?urlCheckout=...` MUST mengembalikan `formConfig` yang hanya memuat field dengan `isActive = true`, terurut menurut `sortOrder` naik. |
| FR-20 | Setiap entri `formConfig` pada respons checkout MUST menyertakan `fieldKey`, `fieldCategory`, `fieldType`, `label`, `placeholder`, `helpText`, `isRequired`, `defaultValue`, `options`, `sortOrder`, dan `validation` (aturan turunan seperti `maxLength`, `min`, `max`, `pattern`, `accept`, `maxFileSizeKb`). |
| FR-21 | Respons checkout MUST TIDAK menyertakan `id` internal baris konfigurasi maupun atribut administratif (`createdAt`, `updatedAt`, `usageCount`). |
| FR-22 | Untuk System Field lokasi (`province`, `city`, `district`), respons MUST menyertakan `dataSource` yang menunjuk endpoint master lokasi agar frontend mengetahui cara memuat opsinya secara berantai (*cascading*). |
| FR-23 | Struktur respons checkout MUST tetap kompatibel dengan klien lama: atribut existing (`tipeField`, `label`, `placeholder`, `order`, `isMandatory`) MUST tetap tersedia sebagai alias selama satu siklus deprekasi. |

### 9.3 Pembuatan Order

| ID | Requirement |
|---|---|
| FR-24 | `CreateOrderDto` MUST menerima atribut opsional `customFields` berupa larik objek `{ fieldKey, value }`, di mana `value` dapat berupa string, angka, boolean, atau larik string. |
| FR-25 | Sistem MUST memvalidasi `customFields` terhadap konfigurasi aktif produk pada saat submit, bukan terhadap metadata yang dikirim klien. |
| FR-26 | Sistem MUST menolak permintaan yang memuat `fieldKey` berkategori SYSTEM di dalam `customFields`. |
| FR-27 | Sistem MUST menyimpan setiap Custom Field bernilai ke `order_custom_field`, satu baris per field, beserta snapshot `field_key`, `field_label`, `field_type`, dan `sort_order`. |
| FR-28 | Sistem MUST TIDAK membuat baris untuk Custom Field yang bernilai kosong dan tidak wajib. |
| FR-29 | Sistem MUST menjalankan penyimpanan order dan penyimpanan `order_custom_field` dalam satu transaksi. |
| FR-30 | Sistem MUST TIDAK memberlakukan validasi *required* Custom Field untuk `source` bernilai `CST_CHAT`, `ADM_ABANDONED`, maupun varian `abandoned`. |
| FR-31 | Sistem MUST menormalkan nilai sebelum penyimpanan: *trim* untuk teks, normalisasi tanggal ke `yyyy-MM-dd`, normalisasi angka, dan penyusunan larik JSON untuk tipe `CHECKBOX`. |

### 9.4 Pembacaan Order

| ID | Requirement |
|---|---|
| FR-32 | `GET /order/{id}` (`DetailOrderDto`) MUST menyertakan `customFields` berupa larik `{ fieldKey, fieldLabel, fieldType, value, sortOrder }`, terurut menurut `sortOrder`. |
| FR-33 | Nilai `fieldLabel` yang ditampilkan MUST berasal dari snapshot pada `order_custom_field`, bukan dari konfigurasi produk saat ini. |
| FR-34 | Pemuatan `customFields` pada daftar order (bila kelak diperlukan) MUST menggunakan *batch fetch* untuk menghindari kueri N+1. |
| FR-35 | Order yang tidak memiliki Custom Field MUST mengembalikan `customFields` sebagai larik kosong, bukan `null`. |

### 9.5 Penyesuaian atas Perilaku Existing

| ID | Requirement |
|---|---|
| FR-36 | `ProdukService.saveProduct()` MUST TIDAK LAGI menghapus seluruh baris `produk_form_config` saat produk di-update. Penyimpanan MUST berupa *upsert* berbasis `(id_produk, field_key)`. |
| FR-37 | `ProdukService.saveProduct()` MUST mengisi `sortOrder`, `isRequired`, `createdAt`, dan `updatedAt` yang pada implementasi saat ini tidak pernah diisi. |
| FR-38 | Anotasi `@NotNull(message = "Form Config Wajib Diisi.")` pada `AddProdukDto.formConfig` MUST dilonggarkan menjadi opsional, karena System Field kini dijamin oleh seeding sistem dan bukan lagi tanggung jawab klien. |
| FR-39 | `ProdukService.copyProduk()` MUST menyalin seluruh atribut konfigurasi baru (`field_key`, `field_category`, `options`, `default_value`, `help_text`, `is_active`). |
| FR-40 | Sistem MUST menyediakan komponen inisialisasi skema (mengikuti pola `BlastSchemaInitializer` dan `LabelSchemaInitializer`) untuk membuat *unique index* fungsional dan menjalankan *backfill* secara idempoten pada `ApplicationReadyEvent`. |

---

## 10. Non Functional Requirements

| ID | Kategori | Requirement | Target Terukur |
|---|---|---|---|
| NFR-1 | Performa | `GET /produk/checkout` MUST tidak mengalami degradasi berarti akibat penambahan atribut konfigurasi | p95 latensi tetap di bawah 400 ms untuk produk dengan 20 field |
| NFR-2 | Performa | `POST /order/create` MUST menambah paling banyak satu operasi tulis *batch* untuk seluruh Custom Field | penambahan p95 latensi maksimum 80 ms untuk 20 Custom Field |
| NFR-3 | Performa | Pembacaan konfigurasi MUST bebas kueri N+1 | satu kueri untuk konfigurasi, satu kueri agregat untuk `usageCount` |
| NFR-4 | Skalabilitas | Tabel `order_custom_field` MUST dirancang untuk pertumbuhan tinggi (jumlah order dikali jumlah field) | terindeks pada `id_order`; kueri detail order sepenuhnya berbasis indeks |
| NFR-5 | Ketersediaan | Kegagalan pemuatan `usageCount` MUST TIDAK menggagalkan pembacaan konfigurasi | `usageCount` bersifat informatif; degradasi anggun (*graceful degradation*) |
| NFR-6 | Kompatibilitas | Perubahan skema MUST bersifat aditif dan kompatibel dengan `spring.jpa.hibernate.ddl-auto=update` | tidak ada `DROP`, tidak ada `RENAME`, tidak ada perubahan tipe yang mempersempit |
| NFR-7 | Kompatibilitas | Klien lama yang tidak mengenal Custom Field MUST tetap berfungsi | AC-22 terpenuhi |
| NFR-8 | Keterpeliharaan | Enum tipe field, daftar System Field, dan pemetaannya ke kolom `order` MUST terpusat pada satu titik definisi | satu enum `SystemFormField` dan satu enum `FormFieldType` |
| NFR-9 | Konsistensi API | Seluruh endpoint MUST mengembalikan `RestResponse<T>`; galat validasi MUST memakai `ErrorResponse`/`ErrorDto` | sesuai konvensi codebase |
| NFR-10 | Konsistensi format | Timestamp pada lapisan DTO MUST memakai `yyyy-MM-dd HH:mm` zona `Asia/Jakarta` | sesuai konvensi codebase |
| NFR-11 | Keamanan | Seluruh nilai yang berasal dari input publik MUST tersanitasi dan dibatasi panjangnya | lihat §23 |
| NFR-12 | Observability | Kegagalan validasi Custom Field pada endpoint publik MUST tercatat pada log beserta `idProduk` dan `fieldKey`, tanpa memuat nilai sensitif secara utuh | log level `WARN` |
| NFR-13 | Auditability | Setiap baris `order_custom_field` MUST memuat cukup informasi untuk direkonstruksi tanpa bergantung pada konfigurasi produk saat ini | snapshot `field_key`, `field_label`, `field_type` |
| NFR-14 | Usability | Layar konfigurasi MUST menyampaikan secara visual atribut mana yang terkunci pada System Field | kontrol dalam keadaan *disabled* beserta *tooltip* penjelas |
| NFR-15 | Migrasi | Proses *backfill* MUST idempoten dan aman dijalankan berulang pada setiap *startup* | dijaga oleh pemeriksaan keberadaan baris dan `CREATE ... IF NOT EXISTS` |
| NFR-16 | Batasan payload | Ukuran payload `PUT form-config` dan `POST /order/create` MUST dibatasi | maksimum 256 KB per permintaan |

---

## 11. Database Design

### 11.1 Prinsip Desain

Empat prinsip menuntun rancangan basis data pada fitur ini.

**Prinsip 1 — Satu tabel konfigurasi, dua kategori.** System Field dan Custom Field disatukan pada `produk_form_config` dan dibedakan oleh kolom diskriminator `field_category`. Alternatif yang dipertimbangkan adalah dua tabel terpisah (`produk_system_field_config` dan `produk_custom_field`). Alternatif tersebut ditolak karena memaksa frontend membaca dan menggabungkan dua sumber, menimbulkan dua ruang `sort_order` yang tidak dapat disatukan tanpa logika tambahan, dan menduplikasi lima belas kolom yang sebenarnya identik. Biaya dari pendekatan satu tabel adalah adanya beberapa kolom yang selalu `NULL` untuk kategori SYSTEM (`options`, `default_value`) — biaya yang jauh lebih murah dibandingkan duplikasi struktur.

**Prinsip 2 — Nilai System Field tetap pada kolom bertipe kuat.** Nilai keenam System Field tetap ditulis ke kolom `order.nama_penerima`, `order.nomor_whatsapp`, `order.alamat`, `order.id_provinsi`, `order.id_kota`, dan `order.id_kecamatan`. Ini adalah keputusan desain terpenting pada dokumen ini: memindahkannya ke penyimpanan generik akan memaksa penulisan ulang seluruh *native query*, *interface projection*, *join* master lokasi, dan agregasi laporan yang telah dirinci pada §1.2 — dengan risiko regresi tinggi dan tanpa manfaat bisnis.

**Prinsip 3 — Nilai Custom Field disimpan sebagai *entity–attribute–value* dengan *snapshot*.** Tabel `order_custom_field` menyimpan satu baris per field per order, lengkap dengan salinan `field_key`, `field_label`, dan `field_type` sebagaimana berlaku pada saat order dibuat. Alternatif berupa satu kolom `jsonb` pada tabel `order` dipertimbangkan dan ditolak; alasannya dirinci pada Keputusan D-3 (§26).

**Prinsip 4 — Perubahan skema bersifat aditif.** Proyek menjalankan `spring.jpa.hibernate.ddl-auto=update`, yang **hanya** menambahkan tabel dan kolom; ia tidak pernah menghapus, mengganti nama, maupun mengubah tipe kolom. Seluruh rancangan di bawah karena itu dibuat aditif, dan konsekuensinya diuraikan pada §11.3.

### 11.2 Tabel `produk_form_config` (modifikasi atas tabel existing)

Kolom bertanda **[ADA]** sudah ada pada tabel saat ini; kolom bertanda **[BARU]** ditambahkan oleh fitur ini.

| Kolom | Tipe | Null | Default | Status | Keterangan & alasan desain |
|---|---|---|---|---|---|
| `id` | `bigserial` | NOT NULL | — | [ADA] | **PK**. Tipe `bigint` IDENTITY mengikuti pola entity ber-id numerik pada codebase (`ConversationLabel`, `ProdukPembayaran`). Bukan dipakai sebagai referensi lintas tabel — lihat §11.6 |
| `id_produk` | `uuid` | NOT NULL | — | [ADA] | **FK** → `produk.id`. Pemilik konfigurasi. Wajib; tidak ada konfigurasi tanpa produk |
| `field_key` | `varchar(64)` | NOT NULL | — | [BARU] | **Identitas stabil field.** Kontrak antara frontend, validator, *mapper* ke kolom `order`, dan *snapshot* pada `order_custom_field`. Panjang 64 memadai untuk *slug* dan menjaga ukuran indeks tetap kecil |
| `field_category` | `varchar(16)` | NOT NULL | `'CUSTOM'` | [BARU] | Diskriminator: `SYSTEM` \| `CUSTOM`. Disimpan sebagai `varchar` + `CHECK`, bukan enum PostgreSQL, karena Hibernate memetakan `@Enumerated(EnumType.STRING)` ke `varchar` dan penambahan nilai enum PostgreSQL memerlukan DDL tersendiri. Default `'CUSTOM'` dipilih agar baris legacy yang belum ter-*backfill* tidak dianggap SYSTEM secara keliru |
| `tipe_field` | `varchar(32)` | NOT NULL | `'TEXT'` | [ADA] | Tipe field. **Nama kolom existing dipertahankan** demi menghindari operasi *rename* yang tidak didukung `ddl-auto=update`. Dipetakan ke atribut Java `fieldType` bertipe enum `FormFieldType`. Nilai: `TEXT`, `TEXTAREA`, `NUMBER`, `EMAIL`, `SELECT`, `RADIO`, `CHECKBOX`, `DATE`, `FILE`, `PROVINCE`, `CITY`, `DISTRICT`, `PHONE` |
| `label` | `varchar(150)` | NOT NULL | — | [ADA] | Teks label yang tampil. Tipe existing adalah `text`; batas 150 diberlakukan pada lapisan validasi aplikasi, bukan melalui perubahan tipe kolom (agar tetap aditif) |
| `placeholder` | `varchar(200)` | NULL | — | [ADA] | Teks placeholder. Idem: batas panjang ditegakkan di lapisan aplikasi |
| `help_text` | `varchar(300)` | NULL | — | [BARU] | Teks bantuan di bawah input. Dipisahkan dari `placeholder` karena keduanya memiliki fungsi UX berbeda: placeholder hilang saat pengguna mengetik, help text menetap |
| `is_mandatory` | `boolean` | NOT NULL | `false` | [ADA] | Penanda wajib. **Nama kolom existing dipertahankan**, dipetakan ke atribut Java `isRequired`. Untuk kategori SYSTEM nilainya selalu `true` (BR-15) |
| `is_active` | `boolean` | NOT NULL | `true` | [BARU] | Penanda aktif. `false` berarti field dipensiunkan: tidak dirender, tidak divalidasi, tidak disimpan untuk order baru (BR-25). Untuk kategori SYSTEM selalu `true` (BR-16) |
| `orders` | `integer` | NOT NULL | `999` | [ADA] | Urutan tampil. **Nama kolom existing dipertahankan**, dipetakan ke atribut Java `sortOrder`. Default `999` memastikan baris tanpa urutan jatuh di akhir alih-alih menghasilkan `NULL` yang tidak dapat diurutkan secara deterministik |
| `options` | `jsonb` | NULL | — | [BARU] | Daftar pilihan untuk `SELECT`, `RADIO`, `CHECKBOX`, berbentuk `[{"label": "...", "value": "..."}]`. Memakai `jsonb` mengikuti preseden codebase yang telah menggunakan `@JdbcTypeCode(SqlTypes.JSON)` pada `Order.configPembayaran` dan `ProdukPembayaran.config`. Alternatif tabel `produk_form_config_option` tersendiri ditolak — lihat D-5 (§26) |
| `default_value` | `varchar(500)` | NULL | — | [BARU] | Nilai bawaan. Disimpan sebagai teks; untuk `CHECKBOX` berisi larik JSON dalam bentuk string. Batas 500 mencegah penyalahgunaan sebagai penyimpanan teks besar |
| `validation_rule` | `jsonb` | NULL | — | [BARU] | Aturan validasi tambahan opsional (`maxLength`, `min`, `max`, `pattern`, `accept`, `maxFileSizeKb`). Berbentuk `jsonb` agar aturan baru dapat ditambahkan tanpa DDL. Untuk kategori SYSTEM diisi sistem dan bersifat baca-saja |
| `created_at` | `timestamp` | NULL | — | [ADA] | Di-set manual di service, mengikuti konvensi codebase (bukan `@CreationTimestamp`) |
| `updated_at` | `timestamp` | NULL | — | [ADA] | Idem |

#### Constraint

| Nama | Jenis | Definisi | Alasan |
|---|---|---|---|
| `pk_produk_form_config` | PRIMARY KEY | `(id)` | — |
| `fk_pfc_produk` | FOREIGN KEY | `(id_produk)` → `produk(id)` `ON DELETE CASCADE` | Konfigurasi tidak bermakna tanpa produk. Perlu dicatat bahwa penghapusan produk pada aplikasi ini bersifat *soft delete* (`produk.is_deleted`), sehingga `CASCADE` praktis tidak pernah terpicu; ia berfungsi sebagai jaring pengaman terhadap penghapusan manual |
| `uq_pfc_produk_field_key` | UNIQUE INDEX | `(id_produk, lower(field_key))` | Menegakkan BR-4. Bersifat **fungsional** (memakai `lower()`), sehingga **tidak dapat dihasilkan Hibernate** dan wajib dibuat oleh *schema initializer* mengikuti pola `LabelSchemaInitializer` |
| `ck_pfc_category` | CHECK | `field_category IN ('SYSTEM','CUSTOM')` | Mencegah nilai diskriminator liar pada level basis data |
| `ck_pfc_type` | CHECK | `tipe_field IN ('TEXT','TEXTAREA','NUMBER','EMAIL','SELECT','RADIO','CHECKBOX','DATE','FILE','PROVINCE','CITY','DISTRICT','PHONE')` | Pertahanan berlapis; validasi utama tetap di lapisan aplikasi agar pesan galat informatif |
| `ck_pfc_system_locked` | CHECK | `field_category <> 'SYSTEM' OR (is_mandatory = true AND is_active = true)` | Menegakkan BR-15 dan BR-16 pada level basis data, sehingga jalur penulisan apa pun — termasuk skrip manual — tidak dapat melanggarnya |

#### Index

| Nama | Kolom | Alasan |
|---|---|---|
| `idx_pfc_produk_active_sort` | `(id_produk, is_active, orders)` | Melayani kueri utama halaman checkout: "seluruh field aktif milik produk X terurut menurut `orders`". Indeks komposit ini memungkinkan pemindaian indeks murni (*index-only scan*) tanpa penyortiran tambahan |
| `uq_pfc_produk_field_key` | `(id_produk, lower(field_key))` | Ganda fungsi: menegakkan keunikan (BR-4) sekaligus mempercepat *upsert by field_key* pada `PUT form-config` |
| `idx_pfc_category` | `(field_category)` | Melayani kueri administratif dan *backfill* (mis. "hitung produk yang belum memiliki enam System Field") |

### 11.3 Catatan Kritis: Kolom Existing yang Tidak Diganti Nama

Rancangan di atas **sengaja mempertahankan** tiga nama kolom yang secara semantik kurang ideal:

| Nama kolom fisik | Nama atribut Java | Nama yang "ideal" | Mengapa tidak diganti |
|---|---|---|---|
| `orders` | `sortOrder` | `sort_order` | `ddl-auto=update` tidak dapat mengganti nama kolom. Menambahkan `sort_order` sebagai kolom baru akan menghasilkan dua kolom urutan, mengharuskan *backfill*, dan meninggalkan kolom `orders` sebagai kolom mati yang berisi data lama — sumber kebingungan permanen |
| `is_mandatory` | `isRequired` | `is_required` | Idem |
| `tipe_field` | `fieldType` | `field_type` | Idem |

Pemetaan dilakukan pada lapisan entity melalui `@Column(name = "...")`, sehingga **seluruh kode aplikasi, DTO, dan kontrak API memakai penamaan yang bersih** (`sortOrder`, `isRequired`, `fieldType`) sementara nama fisik kolom tetap seperti apa adanya. Penyelarasan nama fisik, bila dikehendaki, harus dilakukan melalui skrip migrasi manual terjadwal di luar `ddl-auto` dan berada di luar cakupan dokumen ini.

### 11.4 Definisi Baku Enam System Field

Tabel ini adalah **satu-satunya sumber kebenaran** bagi seeding, validasi, dan pemetaan. Implementasi wajib mewujudkannya sebagai satu enum `SystemFormField`.

| `sort_order` | `field_key` | `tipe_field` | Label bawaan | Placeholder bawaan | Kolom tujuan pada `order` | Validasi utama (tidak dapat diubah) |
|---|---|---|---|---|---|---|
| 1 | `customer_name` | `TEXT` | Nama | Masukkan nama lengkap | `nama_penerima` | wajib; 2–150 karakter |
| 2 | `phone_number` | `PHONE` | Nomor WhatsApp | Contoh: 08123456789 | `nomor_whatsapp` | wajib; dinormalisasi oleh `PhoneNumberUtil.normalizeToIndonesianFormat()` |
| 3 | `address` | `TEXTAREA` | Alamat | Masukkan alamat lengkap | `alamat` | wajib; 10–500 karakter |
| 4 | `province` | `PROVINCE` | Provinsi | Pilih provinsi | `id_provinsi` | wajib; wajib merupakan `province.id` yang valid |
| 5 | `city` | `CITY` | Kota | Pilih kota | `id_kota` | wajib; wajib merupakan `city.id` yang valid dan berada di bawah `province` terpilih |
| 6 | `district` | `DISTRICT` | Kecamatan | Pilih kecamatan | `id_kecamatan` | wajib; wajib merupakan `district.id` yang valid, berada di bawah `city` terpilih, dan memiliki data ongkir |

Tipe `PHONE`, `PROVINCE`, `CITY`, dan `DISTRICT` diperkenalkan sebagai tipe khusus System Field. Alasannya: keempatnya bukan sekadar `TEXT` atau `SELECT` biasa, melainkan memiliki perilaku *render* dan validasi khusus (normalisasi nomor telepon, pemuatan berantai master lokasi). Menyampaikan tipe ini secara eksplisit membebaskan frontend dari keharusan memeriksa `field_key` secara *hardcoded* untuk memutuskan cara merender — kontrak menjadi berbasis tipe, bukan berbasis kunci. Keempat tipe ini **tidak** tersedia bagi Custom Field (BR-27).

### 11.5 Aturan Pembentukan `field_key` Custom Field

Algoritma *slugify* yang wajib diimplementasikan:

1. Ubah `label` menjadi huruf kecil.
2. Normalisasi Unicode ke ASCII (menghilangkan diakritik).
3. Ganti setiap rangkaian karakter non-alfanumerik dengan satu garis bawah (`_`).
4. Pangkas garis bawah di awal dan akhir.
5. Potong hingga maksimum 56 karakter (menyisakan ruang bagi sufiks).
6. Bila hasilnya kosong (misalnya label hanya berisi emoji), gunakan `field`.
7. Bila hasilnya berbentuk `field_key` System Field (`customer_name`, `phone_number`, `address`, `province`, `city`, `district`), tambahkan awalan `custom_` untuk mencegah tabrakan semantik.
8. Bila `field_key` telah dipakai pada produk yang sama, tambahkan sufiks `_2`, `_3`, dan seterusnya hingga unik.

Contoh:

| Label | `field_key` yang dihasilkan |
|---|---|
| Ukuran Baju | `ukuran_baju` |
| Warna | `warna` |
| Warna (duplikat) | `warna_2` |
| Catatan Tambahan? | `catatan_tambahan` |
| Instagram | `instagram` |
| Tanggal Acara | `tanggal_acara` |
| Alamat | `custom_alamat` (karena `address`/`alamat` berisiko rancu — lihat langkah 7 dan catatan di bawah) |
| 🎉 | `field` |

Catatan: langkah 7 membandingkan hasil *slugify* terhadap daftar `field_key` System Field yang berbahasa Inggris. Karena label bawaan System Field berbahasa Indonesia, *slugify* atas label seperti "Alamat" menghasilkan `alamat` — yang tidak identik dengan `address` sehingga secara teknis tidak bertabrakan. Untuk mencegah kebingungan operasional, implementasi **wajib** memelihara daftar kata terlarang (*reserved words*) yang memuat baik `field_key` System Field maupun padanan bahasa Indonesianya: `nama`, `nama_penerima`, `nomor_whatsapp`, `alamat`, `provinsi`, `kota`, `kecamatan`.

### 11.6 Tabel `order_custom_field` (tabel baru)

| Kolom | Tipe | Null | Default | Keterangan & alasan desain |
|---|---|---|---|---|
| `id` | `bigserial` | NOT NULL | — | **PK**. `bigint` dipilih (bukan `uuid`) karena tabel ini bersifat *append-only* dengan volume tinggi; kunci sekuensial menjaga lokalitas indeks B-tree dan mencegah fragmentasi yang timbul dari UUID acak |
| `id_order` | `uuid` | NOT NULL | — | **FK** → `order.id`. Kunci akses utama |
| `id_produk` | `uuid` | NOT NULL | — | **Denormalisasi.** FK → `produk.id`. Disimpan agar kueri `usageCount` ("berapa order memakai field X pada produk P") dapat dijawab **tanpa** *join* ke tabel `order`. Karena `usageCount` dipanggil pada setiap pembukaan layar konfigurasi, penghematan ini bersifat langsung dan terukur |
| `field_key` | `varchar(64)` | NOT NULL | — | ***Snapshot*** `field_key` pada saat order dibuat |
| `field_label` | `varchar(150)` | NOT NULL | — | ***Snapshot*** `label` pada saat order dibuat. Inti dari BR-32: order lama menampilkan label sebagaimana yang dilihat pelanggan |
| `field_type` | `varchar(32)` | NOT NULL | — | ***Snapshot*** tipe field. Diperlukan agar frontend dapat merender nilai dengan benar (mis. `CHECKBOX` sebagai daftar, `FILE` sebagai tautan) tanpa membaca konfigurasi produk saat ini |
| `field_value` | `text` | NULL | — | **Representasi kanonik nilai dalam bentuk teks.** Untuk `CHECKBOX` berisi nilai-nilai tergabung dipisahkan `", "` demi keperluan tampilan dan pencarian; nilai terstrukturnya berada pada `field_value_json`. `text` dipilih (bukan `varchar` berbatas) karena `TEXTAREA` dan `FILE` dapat menghasilkan nilai panjang; batas praktis ditegakkan lapisan aplikasi |
| `field_value_json` | `jsonb` | NULL | — | Nilai terstruktur untuk tipe multi-nilai (`CHECKBOX`) dan metadata untuk `FILE` (`{"url": "...", "fileName": "...", "sizeKb": 128, "contentType": "image/png"}`). Kolom ini mencegah *parsing* teks yang rapuh di sisi konsumen |
| `sort_order` | `integer` | NOT NULL | `999` | ***Snapshot*** urutan tampil, sehingga tampilan detail order mencerminkan urutan form sebagaimana saat pemesanan, meskipun konfigurasi kemudian berubah |
| `created_at` | `timestamp` | NOT NULL | — | Di-set manual di service, mengikuti konvensi codebase |

Perlu dicatat bahwa tabel ini **tidak memiliki** kolom FK ke `produk_form_config.id`. Ini adalah keputusan yang disengaja: pola *delete-and-reinsert* pada implementasi existing membuat `produk_form_config.id` tidak stabil (P-6), dan BR-23 mengizinkan penghapusan permanen field yang belum dipakai. FK ke `id` yang tidak stabil akan menjadi sumber kegagalan integritas. Keterhubungan logis dipelihara melalui pasangan `(id_produk, field_key)` yang dijamin stabil oleh BR-12 dan BR-22, sekaligus dilengkapi *snapshot* yang membuat baris ini tetap dapat dibaca sepenuhnya bahkan ketika konfigurasi asalnya sudah tidak ada.

#### Constraint

| Nama | Jenis | Definisi | Alasan |
|---|---|---|---|
| `pk_order_custom_field` | PRIMARY KEY | `(id)` | — |
| `fk_ocf_order` | FOREIGN KEY | `(id_order)` → `order(id)` `ON DELETE CASCADE` | Nilai field tidak bermakna tanpa order induknya. `CASCADE` sesuai karena penghapusan order adalah operasi administratif penuh |
| `fk_ocf_produk` | FOREIGN KEY | `(id_produk)` → `produk(id)` | Menjaga integritas kolom denormalisasi. **Tanpa** `CASCADE` — penghapusan produk tidak boleh menghapus data order (BR-34) |
| `uq_ocf_order_field` | UNIQUE INDEX | `(id_order, field_key)` | Satu field menghasilkan paling banyak satu baris per order. Menjadikan operasi tulis idempoten dan mencegah duplikasi akibat *retry* atau pengiriman ganda |
| `ck_ocf_value_present` | CHECK | `field_value IS NOT NULL OR field_value_json IS NOT NULL` | Menegakkan BR-33: tidak ada baris tanpa nilai |

#### Index

| Nama | Kolom | Alasan |
|---|---|---|
| `idx_ocf_order` | `(id_order, sort_order)` | Kueri terpanas: memuat seluruh Custom Field satu order dalam urutan tampil. Mendukung *batch fetch* `WHERE id_order IN (...)` untuk NFR-3 dan FR-34 |
| `idx_ocf_produk_field` | `(id_produk, field_key)` | Melayani perhitungan `usageCount` pada `GET form-config` dan penegakan BR-23/BR-24 pada `PUT form-config`, tanpa *join* ke `order` |
| `uq_ocf_order_field` | `(id_order, field_key)` | Keunikan (lihat tabel constraint) |

Estimasi volume: dengan asumsi 100.000 order per bulan dan rata-rata tiga Custom Field terisi per order, tabel bertambah sekitar 300.000 baris per bulan (± 30 MB per bulan termasuk indeks). Angka ini berada dalam batas nyaman PostgreSQL tanpa partisi. Bila volume kelak melampaui puluhan juta baris, partisi berdasarkan rentang `created_at` merupakan langkah lanjutan yang wajar (§24).

### 11.7 Ringkasan DDL

Skrip berikut disertakan sebagai rujukan bagi DBA dan untuk verifikasi hasil `ddl-auto=update`. Bagian `CREATE UNIQUE INDEX` yang memakai fungsi `lower()` **wajib** dijalankan oleh *schema initializer* aplikasi karena Hibernate tidak menghasilkannya.

```sql
-- ============================================================
-- 1. produk_form_config : penambahan kolom (aditif, idempoten)
-- ============================================================
ALTER TABLE produk_form_config ADD COLUMN IF NOT EXISTS field_key       varchar(64);
ALTER TABLE produk_form_config ADD COLUMN IF NOT EXISTS field_category  varchar(16) DEFAULT 'CUSTOM';
ALTER TABLE produk_form_config ADD COLUMN IF NOT EXISTS help_text       varchar(300);
ALTER TABLE produk_form_config ADD COLUMN IF NOT EXISTS is_active       boolean     DEFAULT true;
ALTER TABLE produk_form_config ADD COLUMN IF NOT EXISTS options         jsonb;
ALTER TABLE produk_form_config ADD COLUMN IF NOT EXISTS default_value   varchar(500);
ALTER TABLE produk_form_config ADD COLUMN IF NOT EXISTS validation_rule jsonb;

-- Normalisasi nilai NULL pada kolom existing yang sebelumnya tidak pernah diisi
UPDATE produk_form_config SET orders       = 999    WHERE orders       IS NULL;
UPDATE produk_form_config SET is_mandatory = false  WHERE is_mandatory IS NULL;
UPDATE produk_form_config SET tipe_field   = 'TEXT' WHERE tipe_field   IS NULL;
UPDATE produk_form_config SET is_active    = true   WHERE is_active    IS NULL;
UPDATE produk_form_config SET created_at   = now()  WHERE created_at   IS NULL;

-- Constraint diberlakukan SETELAH backfill field_key selesai (lihat §22)
ALTER TABLE produk_form_config
  ADD CONSTRAINT ck_pfc_category CHECK (field_category IN ('SYSTEM','CUSTOM'));
ALTER TABLE produk_form_config
  ADD CONSTRAINT ck_pfc_system_locked
  CHECK (field_category <> 'SYSTEM' OR (is_mandatory = true AND is_active = true));

CREATE UNIQUE INDEX IF NOT EXISTS uq_pfc_produk_field_key
  ON produk_form_config (id_produk, lower(field_key));
CREATE INDEX IF NOT EXISTS idx_pfc_produk_active_sort
  ON produk_form_config (id_produk, is_active, orders);
CREATE INDEX IF NOT EXISTS idx_pfc_category
  ON produk_form_config (field_category);

-- ============================================================
-- 2. order_custom_field : tabel baru
-- ============================================================
CREATE TABLE IF NOT EXISTS order_custom_field (
    id               bigserial     PRIMARY KEY,
    id_order         uuid          NOT NULL,
    id_produk        uuid          NOT NULL,
    field_key        varchar(64)   NOT NULL,
    field_label      varchar(150)  NOT NULL,
    field_type       varchar(32)   NOT NULL,
    field_value      text,
    field_value_json jsonb,
    sort_order       integer       NOT NULL DEFAULT 999,
    created_at       timestamp     NOT NULL DEFAULT now(),
    CONSTRAINT fk_ocf_order  FOREIGN KEY (id_order)  REFERENCES "order" (id) ON DELETE CASCADE,
    CONSTRAINT fk_ocf_produk FOREIGN KEY (id_produk) REFERENCES produk (id),
    CONSTRAINT ck_ocf_value_present CHECK (field_value IS NOT NULL OR field_value_json IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ocf_order_field
  ON order_custom_field (id_order, field_key);
CREATE INDEX IF NOT EXISTS idx_ocf_order
  ON order_custom_field (id_order, sort_order);
CREATE INDEX IF NOT EXISTS idx_ocf_produk_field
  ON order_custom_field (id_produk, field_key);
```

### 11.8 Struktur `options` dan `validation_rule`

```jsonc
// kolom options — hanya untuk SELECT, RADIO, CHECKBOX
[
  { "label": "Small",  "value": "S" },
  { "label": "Medium", "value": "M" },
  { "label": "Large",  "value": "L" }
]

// kolom validation_rule — seluruh atribut bersifat opsional
{
  "maxLength": 200,          // TEXT, TEXTAREA
  "minLength": 3,            // TEXT, TEXTAREA
  "min": 1,                  // NUMBER
  "max": 100,                // NUMBER
  "pattern": "^[0-9]{5}$",   // TEXT — regex, dibatasi panjang & kompleksitasnya (§23)
  "minDate": "2026-01-01",   // DATE
  "maxDate": "2027-12-31",   // DATE
  "accept": ["image/png", "image/jpeg", "application/pdf"],  // FILE
  "maxFileSizeKb": 2048,     // FILE
  "minSelected": 1,          // CHECKBOX
  "maxSelected": 3           // CHECKBOX
}
```

Pemisahan `options` dan `validation_rule` menjadi dua kolom `jsonb` — alih-alih satu kolom `config` serbaguna — dilakukan karena keduanya memiliki daur hidup dan aturan validasi yang berbeda: `options` menentukan *apa yang dirender*, sedangkan `validation_rule` menentukan *apa yang diterima*. Pemisahan ini membuat validator lebih sederhana dan pesan galat lebih tepat sasaran.

---

## 12. API Contract

### 12.1 Ringkasan Endpoint

| # | Method | Path | Auth | Peran | Status | Keterangan |
|---|---|---|---|---|---|---|
| 1 | `GET` | `/produk/{id}/form-config` | Bearer JWT | SUPERADMIN, ADMIN, AGENT | **Baru** | Membaca konfigurasi lengkap untuk layar konfigurasi |
| 2 | `PUT` | `/produk/{id}/form-config` | Bearer JWT | SUPERADMIN, ADMIN | **Baru** | Menyimpan keseluruhan konfigurasi (*full replace*) |
| 3 | `GET` | `/produk/checkout?urlCheckout={slug}` | Publik | — | **Diperluas** | Menyertakan `formConfig` yang diperkaya, hanya field aktif |
| 4 | `GET` | `/produk/{id}` | Bearer JWT | SUPERADMIN, ADMIN, AGENT | **Diperluas** | `formConfig` pada detail produk diperkaya |
| 5 | `POST` | `/order/create` | Publik | — | **Diperluas** | Menerima `customFields` |
| 6 | `GET` | `/order/{id}` | Bearer JWT | SUPERADMIN, ADMIN, AGENT | **Diperluas** | Menyertakan `customFields` hasil *snapshot* |
| 7 | `POST` | `/produk/form-config/upload` | Publik (dengan pembatasan) | — | **Baru** | Mengunggah berkas bagi Custom Field bertipe `FILE` |
| 8 | `POST` | `/produk` | Bearer JWT | SUPERADMIN, ADMIN | **Diperluas** | `formConfig` inline menjadi opsional; seeding System Field otomatis |

Catatan mengenai konvensi jalur: dokumen kebutuhan menyebut `/products/{id}/form-config` dan `POST /orders`. Rancangan ini memakai `/produk/...` dan `/order/create` demi konsistensi dengan seluruh endpoint existing pada codebase (`ProdukController` memakai `@RequestMapping("/produk")`, `OrderController` memakai `@RequestMapping("/order")`). Memperkenalkan jalur berbahasa Inggris untuk fitur tunggal akan menciptakan inkonsistensi permanen pada permukaan API.

### 12.2 Envelope Respons

Seluruh endpoint mengikuti konvensi codebase:

```jsonc
// Sukses
{ "success": true, "message": "Success", "data": { /* ... */ } }

// Galat umum
{ "success": false, "message": "<pesan>", "data": null }

// Galat validasi (ErrorResponse + daftar ErrorDto)
{
  "success": false,
  "message": "Validation failed",
  "errors": [
    { "field": "ukuran_baju", "code": "REQUIRED_FIELD_MISSING", "message": "Ukuran Baju wajib diisi." }
  ]
}
```

### 12.3 Skema Objek Bersama

**`FormFieldConfigDto`** — dipakai pada endpoint 1, 2, 3, 4.

| Atribut | Tipe | Wajib pada request | Ada pada respons konfigurasi | Ada pada respons checkout | Keterangan |
|---|---|---|---|---|---|
| `fieldKey` | `string` | Ya untuk field existing; kosongkan untuk field baru | Ya | Ya | Dibangkitkan sistem bila kosong |
| `fieldCategory` | `enum` | Ya | Ya | Ya | `SYSTEM` \| `CUSTOM` |
| `fieldType` | `enum` | Ya untuk CUSTOM; diabaikan untuk SYSTEM | Ya | Ya | Lihat §11.4 dan BR-27 |
| `label` | `string` | Ya | Ya | Ya | 1–150 karakter |
| `placeholder` | `string` | Tidak | Ya | Ya | maksimum 200 karakter |
| `helpText` | `string` | Tidak | Ya | Ya | maksimum 300 karakter |
| `isRequired` | `boolean` | Tidak (default `false`) | Ya | Ya | Diabaikan untuk SYSTEM (selalu `true`) |
| `isActive` | `boolean` | Tidak (default `true`) | Ya | **Tidak** (implisit `true`) | Diabaikan untuk SYSTEM (selalu `true`) |
| `defaultValue` | `string` \| `string[]` | Tidak | Ya | Ya | — |
| `options` | `Option[]` | Wajib untuk `SELECT`/`RADIO`/`CHECKBOX` | Ya | Ya | `{ label, value }` |
| `sortOrder` | `integer` | Tidak | Ya | Ya | Dinormalkan sistem menjadi 1..N |
| `validation` | `object` | Tidak | Ya | Ya | Lihat §11.8 |
| `dataSource` | `string` | — | Ya | Ya | Hanya untuk `PROVINCE`/`CITY`/`DISTRICT` |
| `usageCount` | `integer` | — | Ya | **Tidak** | Jumlah order yang memakai field ini |
| `editableAttributes` | `string[]` | — | Ya | **Tidak** | Daftar atribut yang boleh diubah — memungkinkan frontend menonaktifkan kontrol secara *data-driven*, bukan *hardcoded* |
| `deletable` | `boolean` | — | Ya | **Tidak** | `false` untuk SYSTEM dan untuk CUSTOM dengan `usageCount > 0` |

Kehadiran `editableAttributes` dan `deletable` pada respons merupakan keputusan desain yang penting: frontend **tidak boleh** menentukan sendiri atribut mana yang terkunci berdasarkan `fieldCategory`, sebab aturan tersebut dapat berkembang (misalnya bila kelak `isRequired` System Field dibuka sebagian). Dengan menyampaikan izin secara eksplisit dari server, aturan bisnis tetap berada di satu tempat.

---

## 13. Request Response API

### 13.1 `GET /produk/{id}/form-config`

**Request**

```http
GET /produk/9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f/form-config HTTP/1.1
Host: api.saktiform.id
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Response `200 OK`**

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "idProduk": "9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
    "namaProduk": "Kaos Polos Premium",
    "totalField": 8,
    "totalCustomFieldActive": 2,
    "customFieldLimit": 50,
    "fields": [
      {
        "fieldKey": "customer_name",
        "fieldCategory": "SYSTEM",
        "fieldType": "TEXT",
        "label": "Nama Penerima",
        "placeholder": "Masukkan nama penerima paket",
        "helpText": "Nama akan tercetak pada label pengiriman",
        "isRequired": true,
        "isActive": true,
        "defaultValue": null,
        "options": null,
        "sortOrder": 1,
        "validation": { "minLength": 2, "maxLength": 150 },
        "dataSource": null,
        "usageCount": 1842,
        "editableAttributes": ["label", "placeholder", "helpText", "sortOrder"],
        "deletable": false
      },
      {
        "fieldKey": "ukuran_baju",
        "fieldCategory": "CUSTOM",
        "fieldType": "SELECT",
        "label": "Ukuran Baju",
        "placeholder": "Pilih ukuran",
        "helpText": "Lihat tabel ukuran pada deskripsi produk",
        "isRequired": true,
        "isActive": true,
        "defaultValue": "M",
        "options": [
          { "label": "Small",  "value": "S" },
          { "label": "Medium", "value": "M" },
          { "label": "Large",  "value": "L" },
          { "label": "XL",     "value": "XL" }
        ],
        "sortOrder": 2,
        "validation": null,
        "dataSource": null,
        "usageCount": 312,
        "editableAttributes": ["label", "placeholder", "helpText", "sortOrder", "isRequired", "isActive", "defaultValue", "options"],
        "deletable": false
      },
      {
        "fieldKey": "phone_number",
        "fieldCategory": "SYSTEM",
        "fieldType": "PHONE",
        "label": "Nomor WhatsApp",
        "placeholder": "Contoh: 08123456789",
        "helpText": "Gunakan nomor yang aktif WhatsApp",
        "isRequired": true,
        "isActive": true,
        "defaultValue": null,
        "options": null,
        "sortOrder": 3,
        "validation": { "pattern": "^(\\+62|62|0)8[1-9][0-9]{6,11}$" },
        "dataSource": null,
        "usageCount": 1842,
        "editableAttributes": ["label", "placeholder", "helpText", "sortOrder"],
        "deletable": false
      },
      {
        "fieldKey": "address",
        "fieldCategory": "SYSTEM",
        "fieldType": "TEXTAREA",
        "label": "Alamat Lengkap",
        "placeholder": "Nama jalan, nomor rumah, RT/RW, patokan",
        "helpText": null,
        "isRequired": true,
        "isActive": true,
        "defaultValue": null,
        "options": null,
        "sortOrder": 4,
        "validation": { "minLength": 10, "maxLength": 500 },
        "dataSource": null,
        "usageCount": 1842,
        "editableAttributes": ["label", "placeholder", "helpText", "sortOrder"],
        "deletable": false
      },
      {
        "fieldKey": "province",
        "fieldCategory": "SYSTEM",
        "fieldType": "PROVINCE",
        "label": "Provinsi",
        "placeholder": "Pilih provinsi",
        "helpText": null,
        "isRequired": true,
        "isActive": true,
        "defaultValue": null,
        "options": null,
        "sortOrder": 5,
        "validation": null,
        "dataSource": "/location/province",
        "usageCount": 1842,
        "editableAttributes": ["label", "placeholder", "helpText", "sortOrder"],
        "deletable": false
      },
      {
        "fieldKey": "city",
        "fieldCategory": "SYSTEM",
        "fieldType": "CITY",
        "label": "Kota / Kabupaten",
        "placeholder": "Pilih kota",
        "helpText": null,
        "isRequired": true,
        "isActive": true,
        "defaultValue": null,
        "options": null,
        "sortOrder": 6,
        "validation": null,
        "dataSource": "/location/city?idProvince={province}",
        "usageCount": 1842,
        "editableAttributes": ["label", "placeholder", "helpText", "sortOrder"],
        "deletable": false
      },
      {
        "fieldKey": "district",
        "fieldCategory": "SYSTEM",
        "fieldType": "DISTRICT",
        "label": "Kecamatan",
        "placeholder": "Pilih kecamatan",
        "helpText": "Ongkos kirim dihitung berdasarkan kecamatan",
        "isRequired": true,
        "isActive": true,
        "defaultValue": null,
        "options": null,
        "sortOrder": 7,
        "validation": null,
        "dataSource": "/location/district?idCity={city}",
        "usageCount": 1842,
        "editableAttributes": ["label", "placeholder", "helpText", "sortOrder"],
        "deletable": false
      },
      {
        "fieldKey": "instagram",
        "fieldCategory": "CUSTOM",
        "fieldType": "TEXT",
        "label": "Akun Instagram",
        "placeholder": "@username",
        "helpText": "Opsional — untuk repost testimoni",
        "isRequired": false,
        "isActive": true,
        "defaultValue": null,
        "options": null,
        "sortOrder": 8,
        "validation": { "maxLength": 60 },
        "dataSource": null,
        "usageCount": 0,
        "editableAttributes": ["label", "placeholder", "helpText", "sortOrder", "isRequired", "isActive", "defaultValue", "fieldType"],
        "deletable": true
      }
    ]
  }
}
```

**Response `404 Not Found`** — produk tidak ada atau berada di luar Workspace pemanggil (AC-23):

```json
{ "success": false, "message": "Produk tidak ditemukan.", "data": null }
```

### 13.2 `PUT /produk/{id}/form-config`

Semantik: ***full replace* dengan *upsert by field_key***. Daftar `fields` yang dikirim merepresentasikan keadaan akhir yang dikehendaki. Baris dengan `fieldKey` yang sudah ada akan diperbarui; entri tanpa `fieldKey` dianggap field baru; baris existing yang tidak muncul pada daftar dianggap permintaan penghapusan dan diproses menurut BR-11, BR-23, dan BR-24.

**Request**

```http
PUT /produk/9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f/form-config HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json
```

```json
{
  "fields": [
    {
      "fieldKey": "customer_name",
      "fieldCategory": "SYSTEM",
      "label": "Nama Penerima",
      "placeholder": "Masukkan nama penerima paket",
      "helpText": "Nama akan tercetak pada label pengiriman",
      "sortOrder": 1
    },
    {
      "fieldKey": "ukuran_baju",
      "fieldCategory": "CUSTOM",
      "fieldType": "SELECT",
      "label": "Ukuran Baju",
      "placeholder": "Pilih ukuran",
      "isRequired": true,
      "isActive": true,
      "defaultValue": "M",
      "options": [
        { "label": "Small",  "value": "S" },
        { "label": "Medium", "value": "M" },
        { "label": "Large",  "value": "L" },
        { "label": "XL",     "value": "XL" }
      ],
      "sortOrder": 2
    },
    { "fieldKey": "phone_number", "fieldCategory": "SYSTEM", "label": "Nomor WhatsApp", "placeholder": "Contoh: 08123456789", "sortOrder": 3 },
    { "fieldKey": "address",      "fieldCategory": "SYSTEM", "label": "Alamat Lengkap", "placeholder": "Nama jalan, nomor rumah, RT/RW", "sortOrder": 4 },
    { "fieldKey": "province",     "fieldCategory": "SYSTEM", "label": "Provinsi",       "placeholder": "Pilih provinsi",  "sortOrder": 5 },
    { "fieldKey": "city",         "fieldCategory": "SYSTEM", "label": "Kota / Kabupaten", "placeholder": "Pilih kota",    "sortOrder": 6 },
    { "fieldKey": "district",     "fieldCategory": "SYSTEM", "label": "Kecamatan",      "placeholder": "Pilih kecamatan", "sortOrder": 7 },
    {
      "fieldCategory": "CUSTOM",
      "fieldType": "DATE",
      "label": "Tanggal Acara",
      "placeholder": "Pilih tanggal",
      "helpText": "Pesanan diproses maksimal H-3",
      "isRequired": true,
      "isActive": true,
      "sortOrder": 8,
      "validation": { "minDate": "2026-08-01", "maxDate": "2027-12-31" }
    }
  ]
}
```

**Response `200 OK`** — mengembalikan keadaan akhir konfigurasi, termasuk `fieldKey` yang baru dibangkitkan sistem, sehingga frontend dapat langsung menyinkronkan *state*-nya tanpa memanggil `GET` ulang:

```json
{
  "success": true,
  "message": "Konfigurasi form berhasil disimpan.",
  "data": {
    "idProduk": "9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
    "totalField": 9,
    "created": ["tanggal_acara"],
    "updated": ["customer_name", "ukuran_baju", "phone_number", "address", "province", "city", "district"],
    "deleted": ["instagram"],
    "deactivated": [],
    "fields": [ "...daftar lengkap seperti pada §13.1..." ]
  }
}
```

**Response `400 Bad Request`** — beberapa contoh:

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": [
    {
      "field": "fields[0].isRequired",
      "code": "SYSTEM_FIELD_IMMUTABLE_ATTRIBUTE",
      "message": "Atribut 'isRequired' pada System Field 'customer_name' tidak dapat diubah."
    }
  ]
}
```

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": [
    {
      "field": "fields",
      "code": "SYSTEM_FIELD_NOT_DELETABLE",
      "message": "System Field 'district' tidak dapat dihapus.",
      "meta": { "missingSystemFields": ["district"] }
    }
  ]
}
```

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": [
    {
      "field": "instagram",
      "code": "FIELD_IN_USE",
      "message": "Field 'Akun Instagram' sudah dipakai oleh 312 pesanan sehingga tidak dapat dihapus. Nonaktifkan field bila Anda tidak ingin menampilkannya lagi.",
      "meta": { "usageCount": 312, "suggestedAction": "DEACTIVATE" }
    }
  ]
}
```

### 13.3 `GET /produk/checkout?urlCheckout={slug}` (publik, diperluas)

**Request**

```http
GET /produk/checkout?urlCheckout=kaos-polos-premium HTTP/1.1
```

**Response `200 OK`** (dipendekkan pada bagian yang tidak relevan)

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
    "namaProduk": "Kaos Polos Premium",
    "gambarProduk": ["https://cdn.saktiform.id/produk/kaos-1.jpg"],
    "poinFitur": ["Bahan cotton combed 30s", "Sablon plastisol"],
    "atributProduk": [
      { "id": "1a2b3c4d-...", "deskripsi": "1 Pcs", "harga": 89000, "berat": 250 }
    ],
    "metodePembayaran": ["COD", "TRANSFER"],
    "narasiTombol": "Pesan Sekarang",
    "formConfig": [
      {
        "fieldKey": "customer_name",
        "fieldCategory": "SYSTEM",
        "fieldType": "TEXT",
        "label": "Nama Penerima",
        "placeholder": "Masukkan nama penerima paket",
        "helpText": "Nama akan tercetak pada label pengiriman",
        "isRequired": true,
        "defaultValue": null,
        "options": null,
        "sortOrder": 1,
        "validation": { "minLength": 2, "maxLength": 150 },
        "dataSource": null,

        "tipeField": "TEXT",
        "order": 1,
        "isMandatory": true
      },
      {
        "fieldKey": "ukuran_baju",
        "fieldCategory": "CUSTOM",
        "fieldType": "SELECT",
        "label": "Ukuran Baju",
        "placeholder": "Pilih ukuran",
        "helpText": "Lihat tabel ukuran pada deskripsi produk",
        "isRequired": true,
        "defaultValue": "M",
        "options": [
          { "label": "Small",  "value": "S" },
          { "label": "Medium", "value": "M" },
          { "label": "Large",  "value": "L" },
          { "label": "XL",     "value": "XL" }
        ],
        "sortOrder": 2,
        "validation": null,
        "dataSource": null,

        "tipeField": "SELECT",
        "order": 2,
        "isMandatory": true
      },
      {
        "fieldKey": "district",
        "fieldCategory": "SYSTEM",
        "fieldType": "DISTRICT",
        "label": "Kecamatan",
        "placeholder": "Pilih kecamatan",
        "helpText": "Ongkos kirim dihitung berdasarkan kecamatan",
        "isRequired": true,
        "defaultValue": null,
        "options": null,
        "sortOrder": 7,
        "validation": null,
        "dataSource": "/location/district?idCity={city}",

        "tipeField": "DISTRICT",
        "order": 7,
        "isMandatory": true
      }
    ]
  }
}
```

Tiga atribut terakhir pada setiap entri (`tipeField`, `order`, `isMandatory`) adalah **alias kompatibilitas** bagi klien checkout versi lama, sesuai FR-23. Ketiganya wajib dipertahankan selama satu siklus deprekasi dan dihapus melalui rilis terjadwal setelah seluruh klien bermigrasi.

### 13.4 `POST /order/create` (publik, diperluas)

**Request**

```http
POST /order/create HTTP/1.1
Content-Type: application/json
```

```json
{
  "idProduk": "9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
  "idAtributProduk": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "namaLengkap": "Sari Dewi",
  "nomorWhatsapp": "08123456789",
  "alamat": "Jl. Merdeka No. 10, RT 003 RW 005, dekat Masjid Al-Ikhlas",
  "idProvinsi": 11,
  "idKota": 1101,
  "idKecamatan": 110101,
  "metodePembayaran": "COD",
  "source": "CHECKOUT",
  "customFields": [
    { "fieldKey": "ukuran_baju",   "value": "L" },
    { "fieldKey": "warna",         "value": ["Hitam", "Putih"] },
    { "fieldKey": "tanggal_acara", "value": "2026-09-15" },
    { "fieldKey": "instagram",     "value": "@saridewi" },
    {
      "fieldKey": "upload_desain",
      "value": "https://cdn.saktiform.id/form-upload/2026/07/28/a1b2c3d4-desain.png",
      "meta": { "fileName": "desain-final.png", "sizeKb": 842, "contentType": "image/png" }
    }
  ]
}
```

Perlu dicatat bahwa atribut nama pada payload tetap memakai penamaan existing (`namaLengkap`, `nomorWhatsapp`, `alamat`, `idProvinsi`, `idKota`, `idKecamatan`) dan **tidak** berubah menjadi `field_key` System Field. Ini adalah keputusan yang disengaja: mengubah nama atribut pada payload endpoint publik akan memutus kompatibilitas seluruh klien checkout yang sudah berjalan, tanpa memberi manfaat fungsional apa pun. `field_key` berperan sebagai kontrak *konfigurasi dan render*, sedangkan payload submit System Field tetap memakai kontrak lamanya. Pemetaan antara keduanya dipelihara oleh enum `SystemFormField` (§11.4).

**Response `200 OK`**

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "phoneNumber": "6281100001111",
    "message": "Halo, saya sudah melakukan pemesanan Kaos Polos Premium, atas nama Sari Dewi. Mohon segera diproses ya ..."
  }
}
```

Struktur respons ini **tidak berubah** dari perilaku existing (`OrderCreatedResponse`), sehingga klien lama tidak memerlukan penyesuaian apa pun.

**Response `400 Bad Request`** — kegagalan validasi Custom Field:

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": [
    {
      "field": "tanggal_acara",
      "code": "REQUIRED_FIELD_MISSING",
      "message": "Tanggal Acara wajib diisi."
    },
    {
      "field": "ukuran_baju",
      "code": "VALUE_NOT_IN_OPTIONS",
      "message": "Nilai 'XXL' tidak tersedia pada pilihan Ukuran Baju.",
      "meta": { "allowedValues": ["S", "M", "L", "XL"] }
    },
    {
      "field": "customer_name",
      "code": "SYSTEM_FIELD_IN_CUSTOM_PAYLOAD",
      "message": "Field 'customer_name' merupakan System Field dan tidak dapat dikirim melalui customFields."
    }
  ]
}
```

Seluruh galat divalidasi dan dikembalikan **sekaligus** (bukan berhenti pada galat pertama), sehingga pelanggan dapat memperbaiki seluruh kesalahan dalam satu putaran pengisian — sejalan dengan US-15.

### 13.5 `GET /order/{id}` (diperluas)

**Response `200 OK`**

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "7c8d9e0f-1a2b-3c4d-5e6f-7a8b9c0d1e2f",
    "idProduk": "9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
    "namaProduk": "Kaos Polos Premium",
    "atributProduk": { "deskripsi": "1 Pcs", "harga": 89000, "berat": 250 },
    "diskon": 0,
    "ongkir": 12000,
    "metodePembayaran": "COD",
    "namaPenerima": "Sari Dewi",
    "nomorWhatsapp": "6281234567890",
    "alamat": "Jl. Merdeka No. 10, RT 003 RW 005, dekat Masjid Al-Ikhlas",
    "provinsi": { "id": 11, "nama": "DKI Jakarta" },
    "kota": { "id": 1101, "nama": "Jakarta Selatan" },
    "kecamatan": { "id": 110101, "nama": "Kebayoran Baru" },
    "status": "UNPAID",
    "tanggalOrder": "2026-07-28 14:32",
    "handleBy": null,
    "notes": "",
    "customFields": [
      {
        "fieldKey": "ukuran_baju",
        "fieldLabel": "Ukuran Baju",
        "fieldType": "SELECT",
        "value": "L",
        "sortOrder": 2
      },
      {
        "fieldKey": "warna",
        "fieldLabel": "Warna",
        "fieldType": "CHECKBOX",
        "value": ["Hitam", "Putih"],
        "displayValue": "Hitam, Putih",
        "sortOrder": 3
      },
      {
        "fieldKey": "tanggal_acara",
        "fieldLabel": "Tanggal Acara",
        "fieldType": "DATE",
        "value": "2026-09-15",
        "sortOrder": 8
      },
      {
        "fieldKey": "upload_desain",
        "fieldLabel": "Upload Desain",
        "fieldType": "FILE",
        "value": "https://cdn.saktiform.id/form-upload/2026/07/28/a1b2c3d4-desain.png",
        "meta": { "fileName": "desain-final.png", "sizeKb": 842, "contentType": "image/png" },
        "sortOrder": 9
      }
    ]
  }
}
```

Nilai `fieldLabel` pada respons ini berasal dari kolom *snapshot* `order_custom_field.field_label`. Bila Admin kemudian mengubah label "Ukuran Baju" menjadi "Ukuran (S/M/L)", respons untuk order ini **tetap** menampilkan "Ukuran Baju" (AC-15).

### 13.6 `POST /produk/form-config/upload` (publik, terbatas)

Endpoint pendukung bagi Custom Field bertipe `FILE`. Diperlukan karena halaman checkout bersifat publik dan tidak memiliki token JWT, sedangkan endpoint unggah existing (`POST /master/saktiform-media`) berada di area terautentikasi — dan pada implementasi saat ini bahkan masih mengembalikan URL statis (bukan hasil unggah sebenarnya). Rincian risiko dan pengendaliannya dibahas pada §21.6 dan §23.

**Request**

```http
POST /produk/form-config/upload HTTP/1.1
Content-Type: multipart/form-data; boundary=----X

------X
Content-Disposition: form-data; name="idProduk"

9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f
------X
Content-Disposition: form-data; name="fieldKey"

upload_desain
------X
Content-Disposition: form-data; name="file"; filename="desain-final.png"
Content-Type: image/png

<binary>
------X--
```

**Response `200 OK`**

```json
{
  "success": true,
  "message": "Upload success",
  "data": {
    "url": "https://cdn.saktiform.id/form-upload/2026/07/28/a1b2c3d4-desain.png",
    "fileName": "desain-final.png",
    "sizeKb": 842,
    "contentType": "image/png",
    "expiresAt": "2026-07-28 15:32"
  }
}
```

Atribut `expiresAt` menandai bahwa objek yang diunggah namun tidak pernah dirujuk oleh order akan dibersihkan oleh pekerjaan terjadwal (*scheduled cleanup*) setelah 60 menit. Tanpa mekanisme ini, endpoint unggah publik akan menjadi tempat penampungan berkas tak terbatas.

**Response `400 Bad Request`**

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": [
    {
      "field": "file",
      "code": "FILE_TYPE_NOT_ALLOWED",
      "message": "Tipe berkas 'application/x-msdownload' tidak diizinkan.",
      "meta": { "accept": ["image/png", "image/jpeg", "application/pdf"] }
    }
  ]
}
```

---

## 14. UI Flow

### 14.1 Halaman Konfigurasi Form Produk (Dashboard)

Konfigurasi form ditempatkan sebagai **tab tersendiri** pada halaman Tambah/Edit Produk, sejajar dengan tab existing (Informasi Produk, Varian, Pembayaran, Testimoni, Tracking). Alasan penempatan ini: konfigurasi form melekat pada produk dan hampir selalu disunting bersamaan dengan penyiapan produk; menjadikannya halaman terpisah akan menambah langkah navigasi tanpa manfaat.

```
┌────────────────────────────────────────────────────────────────────────────────┐
│  Edit Produk — Kaos Polos Premium                              [Simpan Produk] │
├────────────────────────────────────────────────────────────────────────────────┤
│  Informasi │ Varian │ Pembayaran │ ▸ Konfigurasi Form ◂ │ Testimoni │ Tracking │
├────────────────────────────────────────────────────────────────────────────────┤
│                                                                                │
│  Field Bawaan Sistem                                                     (6)   │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │ ⠿ 1  🔒 Nama Penerima                          TEXT      Wajib   [Ubah]  │  │
│  │ ⠿ 3  🔒 Nomor WhatsApp                         PHONE     Wajib   [Ubah]  │  │
│  │ ⠿ 4  🔒 Alamat Lengkap                         TEXTAREA  Wajib   [Ubah]  │  │
│  │ ⠿ 5  🔒 Provinsi                               PROVINCE  Wajib   [Ubah]  │  │
│  │ ⠿ 6  🔒 Kota / Kabupaten                       CITY      Wajib   [Ubah]  │  │
│  │ ⠿ 7  🔒 Kecamatan                              DISTRICT  Wajib   [Ubah]  │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│  ℹ Field bawaan tidak dapat dihapus atau dinonaktifkan. Anda dapat mengubah    │
│    label, placeholder, help text, dan urutan tampilnya.                        │
│                                                                                │
│  Field Tambahan                                              (2 / 50)  [+ Tambah Field] │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │ ⠿ 2  Ukuran Baju            SELECT    Wajib    Aktif    [Ubah] [🚫]      │  │
│  │      └ 312 pesanan memakai field ini — tidak dapat dihapus permanen      │  │
│  │ ⠿ 8  Akun Instagram         TEXT      Opsional Aktif    [Ubah] [🗑]      │  │
│  │      └ belum dipakai — dapat dihapus permanen                            │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│                                                                                │
│  [ Lihat Pratinjau Form ]                        [Batal]  [Simpan Konfigurasi] │
└────────────────────────────────────────────────────────────────────────────────┘
```

Beberapa keputusan UX beserta alasannya:

| Elemen | Keputusan | Alasan |
|---|---|---|
| Dua seksi terpisah (Bawaan / Tambahan) | Ditampilkan sebagai dua kelompok visual, namun **nomor urut berjalan lintas kelompok** (perhatikan System Field bernomor 1, 3–7 sementara Custom Field bernomor 2 dan 8) | Memenuhi BR-5. Pemisahan visual mengomunikasikan perbedaan hak akses; nomor lintas kelompok mengomunikasikan urutan tampil sebenarnya |
| Ikon 🔒 pada System Field | Kunci ditampilkan permanen | Memenuhi US-5 dan NFR-14; pengguna langsung memahami batasan tanpa harus mencoba dan gagal |
| *Drag & drop* (⠿) lintas kelompok | Diizinkan | Satu-satunya cara intuitif menyusun urutan gabungan |
| Tombol 🚫 (nonaktifkan) vs 🗑 (hapus) | Ditentukan server melalui atribut `deletable` | Frontend tidak menduplikasi aturan bisnis (lihat §12.3) |
| Keterangan jumlah pemakaian | Ditampilkan langsung di bawah nama field | Memenuhi US-13: pengguna memahami konsekuensi **sebelum** bertindak, bukan setelah menerima pesan galat |
| Pencacah `2 / 50` | Ditampilkan pada judul seksi | Batas BR-20 disampaikan secara proaktif |
| Tombol "Simpan Konfigurasi" terpisah dari "Simpan Produk" | Dua tombol berbeda | Konfigurasi form disimpan melalui `PUT /produk/{id}/form-config`, terpisah dari `POST /produk`. Pemisahan ini mencegah pola *delete-and-reinsert* yang bermasalah (P-6, FR-36) |

### 14.2 Panel Editor Field

Panel geser (*drawer*) yang terbuka ketika pengguna menekan [Ubah] atau [+ Tambah Field]. Kontrol yang ditampilkan bergantung pada `fieldCategory` dan `fieldType`.

```
┌─────────────────────────────────────────────┐   ┌─────────────────────────────────────────────┐
│  Ubah Field Bawaan                      [×] │   │  Tambah Field Baru                      [×] │
├─────────────────────────────────────────────┤   ├─────────────────────────────────────────────┤
│  Field Key   customer_name         🔒       │   │  Tipe Field   [ Select              ▾ ]     │
│  Tipe Field  TEXT                  🔒       │   │                                             │
│  Wajib Diisi [✓] Ya                🔒       │   │  Label *      [ Ukuran Baju            ]    │
│  Status      Aktif                 🔒       │   │  Placeholder  [ Pilih ukuran           ]    │
│  ─────────────────────────────────────────  │   │  Help Text    [ Lihat tabel ukuran... ]     │
│  Label *     [ Nama Penerima            ]   │   │  Wajib Diisi  [✓] Ya                        │
│  Placeholder [ Masukkan nama penerima   ]   │   │  Status       [✓] Aktif                     │
│  Help Text   [ Nama akan tercetak...    ]   │   │  ─────────────────────────────────────────  │
│                                             │   │  Pilihan *                    [+ Tambah]    │
│  ℹ Perubahan label tidak memengaruhi        │   │   ⠿ [ Small  ] → nilai [ S  ]  [🗑]         │
│    pesanan yang sudah ada.                  │   │   ⠿ [ Medium ] → nilai [ M  ]  [🗑]         │
│                                             │   │   ⠿ [ Large  ] → nilai [ L  ]  [🗑]         │
│              [Batal]  [Terapkan]            │   │  Nilai Bawaan [ Medium              ▾ ]     │
└─────────────────────────────────────────────┘   │              [Batal]  [Terapkan]            │
                                                  └─────────────────────────────────────────────┘
```

Kontrol bertanda 🔒 dirender dalam keadaan *disabled*, **ditentukan berdasarkan atribut `editableAttributes` yang dikirim server**, bukan berdasarkan pemeriksaan `fieldCategory === 'SYSTEM'` yang ditanamkan di frontend. Seksi "Pilihan" hanya muncul ketika `fieldType` bernilai `SELECT`, `RADIO`, atau `CHECKBOX`.

### 14.3 Halaman Checkout — Render Dinamis

Halaman checkout **tidak lagi** memiliki markup form yang ditulis tetap. Ia menjalankan satu putaran *render* atas larik `formConfig`.

Algoritma render:

```
1. GET /produk/checkout?urlCheckout={slug}
2. Baca data.formConfig  → sudah terurut menurut sortOrder, sudah tersaring isActive
3. Bangun state form: untuk setiap field, state[fieldKey] = defaultValue ?? nilai kosong sesuai tipe
4. Untuk setiap field, pilih komponen berdasarkan fieldType:
     TEXT      → <input type="text">
     TEXTAREA  → <textarea>
     NUMBER    → <input type="number" min max>
     EMAIL     → <input type="email">
     PHONE     → <input type="tel"> + normalisasi saat blur
     SELECT    → <select> dari options
     RADIO     → grup <input type="radio"> dari options
     CHECKBOX  → grup <input type="checkbox"> dari options (nilai berupa larik)
     DATE      → <input type="date" min max>
     FILE      → pemilih berkas + unggah ke POST /produk/form-config/upload, simpan URL hasilnya
     PROVINCE  → <select> dimuat dari dataSource
     CITY      → <select> dimuat dari dataSource, dipicu perubahan PROVINCE (cascading)
     DISTRICT  → <select> dimuat dari dataSource, dipicu perubahan CITY; memicu perhitungan ongkir
5. Render label, placeholder, helpText, dan tanda wajib (*) apa adanya dari konfigurasi
6. Terapkan validasi sisi klien dari objek validation (sebagai UX, bukan sebagai otoritas)
7. Pada submit, susun payload:
     - field berkategori SYSTEM → dipetakan ke atribut payload existing melalui tabel pemetaan §11.4
       (customer_name → namaLengkap, phone_number → nomorWhatsapp, address → alamat,
        province → idProvinsi, city → idKota, district → idKecamatan)
     - field berkategori CUSTOM → dikumpulkan ke dalam larik customFields [{ fieldKey, value, meta? }]
8. POST /order/create
9. Bila 400 → tampilkan galat per field dengan memetakan errors[].field ke fieldKey (atau ke atribut
   payload untuk galat System Field); bila 200 → arahkan ke WhatsApp memakai data.phoneNumber & data.message
```

Poin terpenting pada langkah 7: frontend **wajib** memelihara satu tabel pemetaan `field_key` → nama atribut payload untuk keenam System Field. Pemetaan ini bersifat statis dan tidak pernah berubah (dijamin BR-12), sehingga aman untuk ditanamkan di frontend sebagai konstanta. Seluruh field lain diperlakukan sepenuhnya generik.

```
┌────────────────────────────────────────────┐
│  Kaos Polos Premium              Rp 89.000 │
│  ┌──────────────────────────────────────┐  │
│  │        [ gambar produk ]             │  │
│  └──────────────────────────────────────┘  │
│  • Bahan cotton combed 30s                 │
│  • Sablon plastisol                        │
│                                            │
│  Varian    ( ) 1 Pcs   ( ) 3 Pcs           │
│  ────────────────────────────────────────  │
│  Nama Penerima *                    ← sortOrder 1, label dari konfigurasi
│  [ Masukkan nama penerima paket        ]   │
│  Nama akan tercetak pada label pengiriman  ← helpText
│                                            │
│  Ukuran Baju *                      ← sortOrder 2, CUSTOM di antara System Field
│  [ Medium                          ▾ ]     │
│  Lihat tabel ukuran pada deskripsi produk  │
│                                            │
│  Nomor WhatsApp *                   ← sortOrder 3
│  [ Contoh: 08123456789                 ]   │
│                                            │
│  Alamat Lengkap *                          │
│  [                                     ]   │
│  [                                     ]   │
│                                            │
│  Provinsi *      [ Pilih provinsi    ▾ ]   │
│  Kota *          [ Pilih kota        ▾ ]   ← nonaktif hingga provinsi dipilih
│  Kecamatan *     [ Pilih kecamatan   ▾ ]   ← nonaktif hingga kota dipilih
│  Ongkos kirim dihitung berdasarkan kecamatan│
│                                            │
│  Metode Pembayaran  ( ) COD  ( ) Transfer  │
│  ────────────────────────────────────────  │
│  Subtotal                       Rp  89.000 │
│  Ongkir                         Rp  12.000 │
│  Total                          Rp 101.000 │
│                                            │
│            [   Pesan Sekarang   ]          │
└────────────────────────────────────────────┘
```

### 14.4 Halaman Detail Order (Dashboard)

Custom Field ditampilkan pada satu kartu tersendiri, di bawah kartu Informasi Pengiriman.

```
┌──────────────────────────────────────────────────────────────┐
│  Detail Pesanan  #ORD-20260728-0042              [UNPAID ▾]  │
├──────────────────────────────────────────────────────────────┤
│  Informasi Pengiriman                                        │
│  Nama Penerima     Sari Dewi                                 │
│  Nomor WhatsApp    6281234567890                             │
│  Alamat            Jl. Merdeka No. 10, RT 003 RW 005 ...     │
│  Provinsi / Kota   DKI Jakarta / Jakarta Selatan             │
│  Kecamatan         Kebayoran Baru                            │
├──────────────────────────────────────────────────────────────┤
│  Informasi Tambahan                                     (4)  │
│  Ukuran Baju       L                                         │
│  Warna             Hitam, Putih                              │
│  Tanggal Acara     15 September 2026                         │
│  Upload Desain     📎 desain-final.png (842 KB)   [Unduh]    │
│                                                              │
│  ℓ Label ditampilkan sebagaimana saat pesanan dibuat.        │
└──────────────────────────────────────────────────────────────┘
```

Kartu "Informasi Tambahan" **tidak dirender** apabila `customFields` berupa larik kosong, sehingga tampilan order untuk produk tanpa Custom Field tetap identik dengan sebelumnya. Catatan kaki mengenai label bersifat penting bagi Agent (US-18) agar tidak menyangka sistem menampilkan data yang keliru ketika label produk telah berubah.

### 14.5 Diagram Alur Navigasi

```mermaid
flowchart TD
    A[Dashboard: Daftar Produk] --> B[Halaman Edit Produk]
    B --> C[Tab Konfigurasi Form]
    C --> D{Aksi Admin}

    D -->|Ubah System Field| E[Drawer: atribut terkunci disabled]
    D -->|Tambah Custom Field| F[Drawer: pilih tipe field]
    D -->|Ubah Custom Field| G[Drawer: seluruh atribut terbuka]
    D -->|Drag & drop| H[Susun ulang sortOrder]
    D -->|Nonaktifkan| I[isActive = false]
    D -->|Hapus| J{deletable?}

    J -->|true| K[Hapus dari daftar lokal]
    J -->|false| L[Tampilkan dialog: sarankan nonaktifkan]

    E --> M[Terapkan ke state lokal]
    F --> N{Tipe butuh options?}
    N -->|Ya| O[Editor options wajib diisi]
    N -->|Tidak| M
    O --> M
    G --> M
    H --> M
    I --> M
    K --> M
    L --> M

    M --> P[Simpan Konfigurasi]
    P --> Q[PUT /produk/id/form-config]
    Q --> R{Respons}
    R -->|200| S[Sinkronkan state dari respons + toast sukses]
    R -->|400| T[Tandai field bergalat + tampilkan pesan]
    T --> D

    S --> U[Konfigurasi aktif pada checkout]
    U --> V[Customer buka halaman checkout]
    V --> W[GET /produk/checkout]
    W --> X[Render form dinamis]
    X --> Y[Customer mengisi & submit]
    Y --> Z[POST /order/create]
    Z --> AA{Valid?}
    AA -->|Tidak| AB[Tampilkan galat per field]
    AB --> Y
    AA -->|Ya| AC[Order + order_custom_field tersimpan]
    AC --> AD[Redirect ke WhatsApp]
    AC --> AE[Agent membuka Detail Order]
    AE --> AF[Kartu Informasi Tambahan tampil]
```

---

## 15. Sequence Diagram

### 15.1 Penyimpanan Konfigurasi Form

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant FE as Dashboard (Frontend)
    participant PC as ProdukController
    participant FS as ProdukFormConfigService
    participant VAL as FormConfigValidator
    participant PFR as ProdukFormConfigRepository
    participant OCR as OrderCustomFieldRepository
    participant DB as PostgreSQL

    Admin->>FE: Buka tab Konfigurasi Form
    FE->>PC: GET /produk/{id}/form-config
    PC->>FS: getFormConfig(idProduk, workspaceId)
    FS->>DB: SELECT produk WHERE id AND id_workspace
    alt Produk tidak ditemukan / workspace lain
        FS-->>PC: throw NotFound
        PC-->>FE: 404 { success: false }
    else Produk valid
        FS->>PFR: getByIdProdukOrderBySortOrder(idProduk)
        PFR-->>FS: List<ProdukFormConfig>
        FS->>FS: pastikan 6 System Field ada (self-healing, FR-4)
        opt Ada System Field yang belum ada
            FS->>PFR: saveAll(System Field yang kurang)
        end
        FS->>OCR: countUsageGroupByFieldKey(idProduk)
        OCR-->>FS: Map<fieldKey, usageCount>
        FS->>FS: susun editableAttributes & deletable per field
        FS-->>PC: FormConfigResponse
        PC-->>FE: 200 { fields: [...] }
    end

    Admin->>FE: Ubah label, tambah Custom Field, susun ulang urutan
    Admin->>FE: Klik "Simpan Konfigurasi"
    FE->>PC: PUT /produk/{id}/form-config { fields: [...] }
    PC->>FS: saveFormConfig(idProduk, workspaceId, request)

    rect rgb(240, 245, 255)
        note over FS,DB: Satu transaksi (FR-17)
        FS->>PFR: getByIdProduk(idProduk)
        PFR-->>FS: konfigurasi saat ini
        FS->>VAL: validate(existing, incoming)
        VAL->>VAL: 6 System Field lengkap? (BR-10)
        VAL->>VAL: atribut terkunci System Field tidak berubah? (BR-14)
        VAL->>VAL: tipe field dikenal? options konsisten? (BR-27, BR-28)
        VAL->>VAL: defaultValue valid? (BR-30)
        VAL->>VAL: jumlah Custom Field aktif <= 50? (BR-20)
        alt Ada pelanggaran
            VAL-->>FS: List<ErrorDto>
            FS-->>PC: throw ValidationException
            PC-->>FE: 400 { errors: [...] }
        else Lolos validasi
            FS->>FS: bangkitkan fieldKey untuk field baru (slugify, §11.5)
            FS->>OCR: countByIdProdukAndFieldKeyIn(kandidat hapus)
            OCR-->>FS: usageCount per fieldKey
            alt Ada kandidat hapus dengan usageCount > 0
                FS-->>PC: throw ValidationException(FIELD_IN_USE)
                PC-->>FE: 400 { code: FIELD_IN_USE, meta.usageCount }
            else Semua kandidat hapus aman
                FS->>PFR: upsert per fieldKey (FR-36)
                FS->>PFR: delete kandidat hapus dengan usageCount = 0
                FS->>FS: normalkan sortOrder menjadi 1..N (FR-13)
                FS->>PFR: saveAll
                PFR->>DB: INSERT / UPDATE / DELETE
                DB-->>PFR: OK
                FS-->>PC: FormConfigResponse (created/updated/deleted)
                PC-->>FE: 200 { fields: [...] }
            end
        end
    end
    FE-->>Admin: Toast "Konfigurasi form berhasil disimpan"
```

### 15.2 Render Checkout dan Pembuatan Order

```mermaid
sequenceDiagram
    autonumber
    actor Customer
    participant CO as Halaman Checkout
    participant PC as ProdukController
    participant PS as ProdukService
    participant OC as OrderController
    participant OOS as OrderOrchestrationService
    participant CFV as CustomFieldValidator
    participant OS as OrderService
    participant OCR as OrderCustomFieldRepository
    participant EV as ApplicationEventPublisher
    participant OEL as OrderEventListener
    participant WA as WhatsappClientHelper

    Customer->>CO: Buka https://checkout/kaos-polos-premium
    CO->>PC: GET /produk/checkout?urlCheckout=kaos-polos-premium
    PC->>PS: getCheckoutProduk(urlCheckout)
    PS->>PS: ambil formConfig WHERE is_active = true ORDER BY orders
    PS-->>PC: ProdukCheckoutDto { formConfig: [...] }
    PC-->>CO: 200
    CO->>CO: render form dinamis per fieldType (§14.3)

    Customer->>CO: Pilih Provinsi
    CO->>PC: GET /location/city?idProvince=11
    PC-->>CO: daftar kota
    Customer->>CO: Pilih Kota lalu Kecamatan
    CO->>PC: GET /location/district?idCity=1101
    PC-->>CO: daftar kecamatan
    CO->>CO: hitung & tampilkan ongkir

    opt Ada Custom Field bertipe FILE
        Customer->>CO: Pilih berkas
        CO->>PC: POST /produk/form-config/upload (multipart)
        PC-->>CO: { url, fileName, sizeKb, contentType }
        CO->>CO: simpan url ke state field
    end

    Customer->>CO: Klik "Pesan Sekarang"
    CO->>CO: petakan SYSTEM → atribut payload, CUSTOM → customFields
    CO->>OC: POST /order/create { ..., customFields: [...] }
    OC->>OOS: createOrder(dto, actor, ip)

    rect rgb(240, 255, 245)
        note over OOS,OCR: Satu transaksi (FR-29, BR-38)
        OOS->>CFV: validate(idProduk, customFields, source)
        CFV->>CFV: muat konfigurasi AKTIF produk (BR-36)
        CFV->>CFV: tolak fieldKey berkategori SYSTEM (BR-19)
        CFV->>CFV: cek required, tipe, options, validation_rule
        alt Validasi gagal
            CFV-->>OOS: List<ErrorDto>
            OOS-->>OC: throw ValidationException
            OC-->>CO: 400 { errors: [...] }
            CO-->>Customer: Tandai field bergalat
        else Validasi lolos
            OOS->>OS: createOrderInternal(dto, actor, ip)
            OS->>OS: normalisasi nomor WhatsApp
            OS->>OS: hitung ongkir dari gudang + id_kecamatan
            OS->>OS: getOrCreateContact(...)
            OS->>OS: INSERT INTO "order" (System Field pada kolom bertipe kuat, BR-18)
            OS-->>OOS: Order tersimpan
            OOS->>OCR: saveAll(snapshot customFields) (BR-32)
            OCR-->>OOS: OK
        end
    end

    OOS->>EV: publish OrderCreatedEvent(orderId, FOLLOWUP-COD)
    OOS-->>OC: OrderCreatedResponse { phoneNumber, message }
    OC-->>CO: 200
    CO-->>Customer: Redirect ke wa.me dengan pesan konfirmasi

    note over EV,OEL: Setelah commit transaksi (AFTER_COMMIT)
    EV->>OEL: onOrderCreated(event)
    OEL->>WA: kirim pesan follow-up WhatsApp
```

### 15.3 Pembacaan Detail Order oleh Agent

```mermaid
sequenceDiagram
    autonumber
    actor Agent
    participant FE as Dashboard
    participant OC as OrderController
    participant OS as OrderService
    participant OCR as OrderCustomFieldRepository
    participant DB as PostgreSQL

    Agent->>FE: Buka Detail Pesanan
    FE->>OC: GET /order/{id}
    OC->>OS: getOrderDetail(idOrder, workspaceId)
    OS->>DB: SELECT order + join produk/lokasi (validasi workspace)
    DB-->>OS: Order
    OS->>OCR: findByIdOrderOrderBySortOrder(idOrder)
    OCR->>DB: SELECT ... FROM order_custom_field WHERE id_order = ? ORDER BY sort_order
    DB-->>OCR: List<OrderCustomField>
    OCR-->>OS: hasil
    OS->>OS: petakan ke DetailOrderDto.customFields memakai LABEL SNAPSHOT (FR-33)
    OS-->>OC: DetailOrderDto
    OC-->>FE: 200 { ..., customFields: [...] }
    FE-->>Agent: Render kartu "Informasi Tambahan"
```

---

## 16. Activity Diagram

### 16.1 Aktivitas Penyimpanan Konfigurasi Form

```mermaid
flowchart TD
    START([Admin menekan Simpan Konfigurasi]) --> A[Terima payload fields]
    A --> B{Produk ada & milik workspace?}
    B -->|Tidak| E404[404 Produk tidak ditemukan]
    B -->|Ya| C[Muat konfigurasi existing dari DB]

    C --> D{6 System Field lengkap pada payload?}
    D -->|Tidak| E1[400 SYSTEM_FIELD_NOT_DELETABLE]
    D -->|Ya| F[Iterasi setiap field pada payload]

    F --> G{fieldCategory = SYSTEM?}
    G -->|Ya| H{Atribut terkunci diubah?}
    H -->|Ya| E2[400 SYSTEM_FIELD_IMMUTABLE_ATTRIBUTE]
    H -->|Tidak| I[Terima label, placeholder, helpText, sortOrder]

    G -->|Tidak| J{fieldType dikenal?}
    J -->|Tidak| E3[400 INVALID_FIELD_TYPE]
    J -->|Ya| K{Tipe memerlukan options?}
    K -->|Ya| L{options berisi minimal 1 entri?}
    L -->|Tidak| E4[400 OPTIONS_REQUIRED_FOR_TYPE]
    L -->|Ya| M{option.value unik?}
    M -->|Tidak| E5[400 DUPLICATE_OPTION_VALUE]
    M -->|Ya| N
    K -->|Tidak| O{options dikirim berisi?}
    O -->|Ya| E6[400 OPTIONS_NOT_ALLOWED_FOR_TYPE]
    O -->|Tidak| N

    N{defaultValue valid terhadap tipe & options?} -->|Tidak| E7[400 INVALID_DEFAULT_VALUE]
    N -->|Ya| P{fieldKey kosong (field baru)?}
    P -->|Ya| Q[Slugify label, hindari reserved word, selesaikan tabrakan]
    P -->|Tidak| R[Pertahankan fieldKey existing]
    Q --> S
    R --> S

    I --> S[Kumpulkan ke daftar terverifikasi]
    S --> T{Masih ada field pada payload?}
    T -->|Ya| F
    T -->|Tidak| U{Jumlah Custom Field aktif <= 50?}
    U -->|Tidak| E8[400 CUSTOM_FIELD_LIMIT_EXCEEDED]
    U -->|Ya| V[Tentukan kandidat hapus: existing yang tidak ada pada payload]

    V --> W{Ada kandidat berkategori SYSTEM?}
    W -->|Ya| E1
    W -->|Tidak| X[Hitung usageCount kandidat dari order_custom_field]
    X --> Y{Ada kandidat dengan usageCount > 0?}
    Y -->|Ya| E9[400 FIELD_IN_USE + saran DEACTIVATE]
    Y -->|Tidak| Z[Mulai transaksi]

    Z --> AA[UPSERT baris berdasarkan id_produk + field_key]
    AA --> AB[DELETE kandidat hapus]
    AB --> AC[Normalkan sortOrder menjadi 1..N]
    AC --> AD[Set updatedAt = now]
    AD --> AE{Transaksi berhasil?}
    AE -->|Tidak| E10[500 + rollback penuh]
    AE -->|Ya| AF[Commit]
    AF --> AG[Susun respons: created/updated/deleted + daftar final]
    AG --> OK([200 OK])

    E404 --> FAIL([Selesai dengan galat])
    E1 --> FAIL
    E2 --> FAIL
    E3 --> FAIL
    E4 --> FAIL
    E5 --> FAIL
    E6 --> FAIL
    E7 --> FAIL
    E8 --> FAIL
    E9 --> FAIL
    E10 --> FAIL
```

### 16.2 Aktivitas Validasi dan Penyimpanan Order

```mermaid
flowchart TD
    START([POST /order/create diterima]) --> A[Validasi JSR-303 atas field wajib existing]
    A --> B{Lolos?}
    B -->|Tidak| E1[400 daftar galat validasi standar]
    B -->|Ya| C{source = abandoned?}
    C -->|Ya| D[saveAbandonedOrder — Custom Field tidak diproses, OOS-1]
    D --> OKA([200 tanpa order])

    C -->|Tidak| E[Muat konfigurasi AKTIF produk]
    E --> F{source = CST_CHAT atau ADM_ABANDONED?}
    F -->|Ya| G[Lewati pemeriksaan required — BR-39]
    F -->|Tidak| H[Berlakukan pemeriksaan required penuh]

    G --> I[Bangun peta payload customFields]
    H --> I

    I --> J{Ada fieldKey berkategori SYSTEM pada payload?}
    J -->|Ya| E2[400 SYSTEM_FIELD_IN_CUSTOM_PAYLOAD]
    J -->|Tidak| K[Iterasi setiap Custom Field AKTIF milik produk]

    K --> L{Field ada pada payload & bernilai?}
    L -->|Tidak| M{isRequired & pemeriksaan required berlaku?}
    M -->|Ya| E3[Kumpulkan REQUIRED_FIELD_MISSING]
    M -->|Tidak| N[Lewati — tidak membuat baris, BR-33]

    L -->|Ya| O{Nilai sesuai tipe?}
    O -->|Tidak| E4[Kumpulkan INVALID_VALUE_TYPE]
    O -->|Ya| P{Tipe berbasis options?}
    P -->|Ya| Q{Nilai termuat pada options?}
    Q -->|Tidak| E5[Kumpulkan VALUE_NOT_IN_OPTIONS]
    Q -->|Ya| R
    P -->|Tidak| R{Lolos validation_rule?}
    R -->|Tidak| E6[Kumpulkan VALUE_RULE_VIOLATION]
    R -->|Ya| S[Normalisasi nilai — trim, format tanggal, larik JSON]
    S --> T[Siapkan baris snapshot: fieldKey, fieldLabel, fieldType, sortOrder]

    N --> U{Masih ada field?}
    T --> U
    U -->|Ya| K
    U -->|Tidak| V{Ada fieldKey tak dikenal pada payload?}
    V -->|Ya| W[Abaikan + catat WARN — BR-37]
    V -->|Tidak| X
    W --> X{Kumpulan galat kosong?}

    X -->|Tidak| E7[400 seluruh galat sekaligus — tidak ada order tersimpan]
    X -->|Ya| Y[Mulai transaksi]

    Y --> Z[createOrderInternal: normalisasi WA, hitung ongkir, contact, INSERT order]
    Z --> AA[Batch INSERT order_custom_field]
    AA --> AB{Berhasil?}
    AB -->|Tidak| E8[500 + rollback penuh — BR-38]
    AB -->|Ya| AC[Commit]
    AC --> AD[Publish OrderCreatedEvent]
    AD --> AE[Susun OrderCreatedResponse]
    AE --> OK([200 OK])
    AD -.->|AFTER_COMMIT, asinkron| AF[OrderEventListener kirim WhatsApp]

    E1 --> FAIL([Selesai dengan galat])
    E2 --> FAIL
    E7 --> FAIL
    E8 --> FAIL
    E3 --> X
    E4 --> U
    E5 --> U
    E6 --> U
```

---

## 17. ERD Sederhana

```mermaid
erDiagram
    WORKSPACE ||--o{ PRODUK : "memiliki"
    PRODUK    ||--o{ PRODUK_FORM_CONFIG : "dikonfigurasi oleh"
    PRODUK    ||--o{ ORDER : "dipesan melalui"
    PRODUK    ||--o{ ATRIBUT_PRODUK : "punya varian"
    ORDER     ||--o{ ORDER_CUSTOM_FIELD : "menyimpan nilai"
    PRODUK    ||--o{ ORDER_CUSTOM_FIELD : "denormalisasi untuk usageCount"
    PROVINCE  ||--o{ ORDER : "id_provinsi"
    CITY      ||--o{ ORDER : "id_kota"
    DISTRICT  ||--o{ ORDER : "id_kecamatan"
    CONTACT   ||--o{ ORDER : "id_contact"

    WORKSPACE {
        bigint id PK
        varchar nama
    }

    PRODUK {
        uuid id PK
        bigint id_workspace FK
        varchar nama_produk
        varchar url_checkout
        boolean is_deleted
    }

    PRODUK_FORM_CONFIG {
        bigint id PK
        uuid id_produk FK
        varchar field_key "UNIQUE per produk (lower)"
        varchar field_category "SYSTEM | CUSTOM"
        varchar tipe_field "-> fieldType"
        varchar label
        varchar placeholder
        varchar help_text
        boolean is_mandatory "-> isRequired"
        boolean is_active
        integer orders "-> sortOrder"
        jsonb options
        varchar default_value
        jsonb validation_rule
        timestamp created_at
        timestamp updated_at
    }

    ORDER {
        uuid id PK
        varchar order_code
        uuid id_produk FK
        uuid id_atribut_produk FK
        varchar nama_penerima "<- customer_name"
        varchar nomor_whatsapp "<- phone_number"
        varchar alamat "<- address"
        integer id_provinsi "<- province"
        integer id_kota "<- city"
        integer id_kecamatan "<- district"
        varchar status
        varchar pembayaran
        bigint ongkos_kirim
        bigint harga
        varchar source
        timestamp created_at
    }

    ORDER_CUSTOM_FIELD {
        bigint id PK
        uuid id_order FK
        uuid id_produk FK
        varchar field_key "snapshot"
        varchar field_label "snapshot"
        varchar field_type "snapshot"
        text field_value
        jsonb field_value_json
        integer sort_order "snapshot"
        timestamp created_at
    }

    ATRIBUT_PRODUK {
        uuid id PK
        uuid id_produk FK
        varchar deskripsi
        bigint harga
        integer berat
    }

    PROVINCE { integer id PK
        varchar nama }
    CITY { integer id PK
        integer id_province FK
        varchar nama }
    DISTRICT { integer id PK
        integer id_city FK
        varchar nama }
    CONTACT { bigint id PK
        bigint id_workspace FK
        varchar nomor_whatsapp
        varchar nama }
```

Dua relasi pada diagram di atas layak diperhatikan secara khusus.

**Tidak ada relasi langsung antara `PRODUK_FORM_CONFIG` dan `ORDER_CUSTOM_FIELD`.** Keterhubungan keduanya bersifat *logis* melalui pasangan `(id_produk, field_key)`, bukan melalui *foreign key* ke `produk_form_config.id`. Alasannya telah diuraikan pada §11.6: `id` baris konfigurasi tidak stabil, dan field yang belum dipakai boleh dihapus permanen. Konsekuensi yang harus dipahami QA: menghapus baris konfigurasi **tidak** menghapus baris `order_custom_field` — dan itu memang perilaku yang dikehendaki.

**Relasi ganda dari `PRODUK`.** `ORDER_CUSTOM_FIELD` memiliki FK ke `PRODUK` di samping FK ke `ORDER`, padahal `ORDER` sendiri sudah menunjuk `PRODUK`. Denormalisasi ini disengaja demi menjawab kueri `usageCount` tanpa *join* ke tabel `order` yang bervolume besar (§11.6).

---

## 18. Validasi

### 18.1 Prinsip Validasi

Validasi diterapkan pada tiga lapisan, dengan pembagian tanggung jawab yang tegas:

| Lapisan | Peran | Sifat |
|---|---|---|
| **Frontend** | Umpan balik instan kepada pengguna (UX) | **Bukan otoritas.** Boleh dilewati; tidak boleh dijadikan satu-satunya penjaga |
| **Aplikasi (service + validator)** | Otoritas penuh atas seluruh aturan bisnis | Menghasilkan pesan galat informatif berbahasa Indonesia |
| **Basis data (constraint)** | Jaring pengaman terakhir | Menjaga invarian meski ada jalur tulis di luar aplikasi |

Prinsip terpenting: **konfigurasi yang dipakai untuk memvalidasi selalu dibaca ulang dari basis data pada saat submit** (BR-36). Metadata apa pun yang dikirim klien — `fieldType`, `isRequired`, `options` — diabaikan sepenuhnya pada endpoint `POST /order/create`. Klien hanya boleh mengirim `fieldKey` dan `value`. Tanpa aturan ini, penyerang dapat mengirim `{"fieldKey": "x", "isRequired": false}` untuk melewati validasi.

### 18.2 Validasi Konfigurasi Form (`PUT /produk/{id}/form-config`)

#### 18.2.1 Validasi tingkat permintaan

| Aturan | Kondisi gagal | Kode galat |
|---|---|---|
| Produk wajib ada dan berada di bawah Workspace pemanggil | tidak memenuhi | `404` (bukan kode galat validasi) |
| `fields` wajib ada dan tidak kosong | `null` atau larik kosong | `FIELDS_REQUIRED` |
| Jumlah entri maksimum 56 (6 SYSTEM + 50 CUSTOM) | melebihi | `CUSTOM_FIELD_LIMIT_EXCEEDED` |
| Ukuran payload maksimum 256 KB | melebihi | `PAYLOAD_TOO_LARGE` (`413`) |
| Keenam `field_key` SYSTEM wajib hadir | ada yang hilang | `SYSTEM_FIELD_NOT_DELETABLE` |
| `field_key` wajib unik dalam satu payload (case-insensitive) | ada duplikat | `DUPLICATE_FIELD_KEY` |
| Jumlah Custom Field dengan `isActive = true` maksimum 50 | melebihi | `CUSTOM_FIELD_LIMIT_EXCEEDED` |

#### 18.2.2 Validasi per field — atribut umum

| Atribut | Aturan | Kode galat |
|---|---|---|
| `fieldCategory` | wajib; `SYSTEM` atau `CUSTOM` | `INVALID_FIELD_CATEGORY` |
| `fieldKey` | bila diisi: 1–64 karakter, pola `^[a-z][a-z0-9_]*$` | `INVALID_FIELD_KEY_FORMAT` |
| `fieldKey` | bila diisi untuk field baru: tidak boleh termuat pada daftar kata terlarang (§11.5) | `RESERVED_FIELD_KEY` |
| `label` | wajib; 1–150 karakter setelah *trim*; tidak boleh hanya berisi spasi | `LABEL_REQUIRED`, `LABEL_TOO_LONG` |
| `label` | wajib bebas tag HTML (disanitasi, bukan ditolak) | — (sanitasi senyap, §23) |
| `placeholder` | opsional; maksimum 200 karakter | `PLACEHOLDER_TOO_LONG` |
| `helpText` | opsional; maksimum 300 karakter | `HELP_TEXT_TOO_LONG` |
| `sortOrder` | opsional; bila diisi: integer 1–999 | `INVALID_SORT_ORDER` |
| `isRequired` | opsional; boolean | `INVALID_BOOLEAN` |
| `isActive` | opsional; boolean | `INVALID_BOOLEAN` |

#### 18.2.3 Validasi khusus kategori SYSTEM

| Aturan | Kode galat |
|---|---|
| `fieldKey` wajib termuat pada daftar enam System Field | `UNKNOWN_SYSTEM_FIELD` |
| `fieldKey` tidak boleh berbeda dari nilai tersimpan | `SYSTEM_FIELD_IMMUTABLE_ATTRIBUTE` |
| `fieldType` bila dikirim wajib sama dengan nilai tersimpan | `SYSTEM_FIELD_IMMUTABLE_ATTRIBUTE` |
| `isRequired` bila dikirim wajib `true` | `SYSTEM_FIELD_IMMUTABLE_ATTRIBUTE` |
| `isActive` bila dikirim wajib `true` | `SYSTEM_FIELD_IMMUTABLE_ATTRIBUTE` |
| `options` bila dikirim wajib `null` atau kosong | `SYSTEM_FIELD_IMMUTABLE_ATTRIBUTE` |
| `defaultValue` bila dikirim wajib `null` | `SYSTEM_FIELD_IMMUTABLE_ATTRIBUTE` |
| `validation` bila dikirim wajib sama dengan nilai tersimpan | `SYSTEM_FIELD_IMMUTABLE_ATTRIBUTE` |

Catatan implementasi: pendekatan yang direkomendasikan adalah **mengabaikan** atribut terkunci apabila nilainya sama dengan yang tersimpan, dan **menolak** hanya apabila nilainya berbeda. Pendekatan ini membuat frontend dapat mengirim kembali objek field secara utuh (pola *round-trip* yang lazim) tanpa harus menyaring atribut terlebih dahulu, sementara upaya perubahan yang sesungguhnya tetap ditolak.

#### 18.2.4 Validasi khusus kategori CUSTOM per tipe field

| `fieldType` | Aturan spesifik | Kode galat |
|---|---|---|
| `TEXT` | `options` wajib kosong; `validation.maxLength` ≤ 500 | `OPTIONS_NOT_ALLOWED_FOR_TYPE`, `INVALID_VALIDATION_RULE` |
| `TEXTAREA` | `options` wajib kosong; `validation.maxLength` ≤ 2000 | idem |
| `NUMBER` | `options` wajib kosong; bila `min` dan `max` diisi maka `min` ≤ `max` | `INVALID_RANGE` |
| `EMAIL` | `options` wajib kosong; `validation.pattern` tidak diizinkan (pola email ditetapkan sistem) | `VALIDATION_RULE_NOT_ALLOWED` |
| `SELECT` | `options` wajib berisi 1–100 entri | `OPTIONS_REQUIRED_FOR_TYPE`, `TOO_MANY_OPTIONS` |
| `RADIO` | `options` wajib berisi 1–20 entri (di atas 20, `SELECT` lebih tepat secara UX) | idem |
| `CHECKBOX` | `options` wajib berisi 1–50 entri; bila diisi maka `minSelected` ≤ `maxSelected` ≤ jumlah options | idem, `INVALID_RANGE` |
| `DATE` | `options` wajib kosong; `minDate` dan `maxDate` berformat `yyyy-MM-dd`; `minDate` ≤ `maxDate` | `INVALID_DATE_FORMAT`, `INVALID_RANGE` |
| `FILE` | `options` wajib kosong; `accept` wajib berisi 1–10 MIME type dari daftar putih sistem; `maxFileSizeKb` ≤ 5120 | `INVALID_ACCEPT_TYPE`, `FILE_SIZE_LIMIT_EXCEEDED` |
| `PROVINCE`, `CITY`, `DISTRICT`, `PHONE` | **tidak diizinkan** untuk kategori CUSTOM | `FIELD_TYPE_RESERVED_FOR_SYSTEM` |

#### 18.2.5 Validasi `options`

| Aturan | Kode galat |
|---|---|
| Setiap entri wajib memiliki `label` dan `value` yang tidak kosong | `OPTION_INCOMPLETE` |
| `label` maksimum 100 karakter; `value` maksimum 100 karakter | `OPTION_TOO_LONG` |
| `value` wajib unik dalam satu field (case-insensitive) | `DUPLICATE_OPTION_VALUE` |

#### 18.2.6 Validasi `defaultValue`

| Kondisi | Aturan | Kode galat |
|---|---|---|
| Tipe berbasis `options` (bukan `CHECKBOX`) | `defaultValue` wajib salah satu `option.value` | `INVALID_DEFAULT_VALUE` |
| Tipe `CHECKBOX` | `defaultValue` wajib berupa larik; setiap elemen wajib salah satu `option.value` | `INVALID_DEFAULT_VALUE` |
| Tipe `NUMBER` | `defaultValue` wajib berupa angka dan berada dalam rentang `min`–`max` | `INVALID_DEFAULT_VALUE` |
| Tipe `DATE` | `defaultValue` wajib berformat `yyyy-MM-dd` dan berada dalam rentang `minDate`–`maxDate` | `INVALID_DEFAULT_VALUE` |
| Tipe `EMAIL` | `defaultValue` wajib berformat email valid | `INVALID_DEFAULT_VALUE` |
| Tipe `FILE` | `defaultValue` **tidak diizinkan** | `DEFAULT_VALUE_NOT_ALLOWED` |

#### 18.2.7 Validasi penghapusan field

| Kondisi | Hasil | Kode galat |
|---|---|---|
| Kandidat hapus berkategori SYSTEM | Tolak | `SYSTEM_FIELD_NOT_DELETABLE` |
| Kandidat hapus berkategori CUSTOM dengan `usageCount = 0` | Hapus permanen | — |
| Kandidat hapus berkategori CUSTOM dengan `usageCount > 0` | Tolak seluruh permintaan, sertakan `usageCount` dan saran `DEACTIVATE` | `FIELD_IN_USE` |

### 18.3 Validasi Nilai System Field pada Submit Order

Validasi keenam System Field **tidak berubah** dari perilaku existing dan tetap berbasis anotasi JSR-303 pada `CreateOrderDto`, ditambah validasi bisnis di `OrderService`. Perubahan label pada konfigurasi form tidak memengaruhi validasi ini sama sekali — hanya memengaruhi **teks pesan galat** yang ditampilkan kepada pelanggan.

| `field_key` | Validasi | Kode galat |
|---|---|---|
| `customer_name` | `@NotBlank`; 2–150 karakter | `REQUIRED_FIELD_MISSING`, `VALUE_RULE_VIOLATION` |
| `phone_number` | `@NotBlank`; wajib lolos `PhoneNumberUtil.normalizeToIndonesianFormat()` | `REQUIRED_FIELD_MISSING`, `INVALID_PHONE_NUMBER` |
| `address` | `@NotBlank`; 10–500 karakter | `REQUIRED_FIELD_MISSING`, `VALUE_RULE_VIOLATION` |
| `province` | `@NotNull`; wajib merupakan `province.id` yang ada | `REQUIRED_FIELD_MISSING`, `INVALID_LOCATION` |
| `city` | `@NotNull`; wajib ada dan berada di bawah `province` terpilih | `REQUIRED_FIELD_MISSING`, `LOCATION_HIERARCHY_MISMATCH` |
| `district` | `@NotNull`; wajib ada, berada di bawah `city` terpilih, dan memiliki data ongkir untuk gudang produk | `REQUIRED_FIELD_MISSING`, `SHIPPING_RATE_NOT_FOUND` |

Perlu dicatat bahwa validasi hierarki lokasi (`city` di bawah `province`, `district` di bawah `city`) merupakan **penguatan atas perilaku existing**. Implementasi saat ini menerima ketiga id lokasi tanpa memverifikasi konsistensi hierarkinya, sehingga kombinasi yang tidak masuk akal dapat tersimpan. Karena label lokasi kini dapat diubah dan bentuk *dropdown*-nya sepenuhnya digerakkan konfigurasi, penguatan ini menjadi relevan untuk mencegah data pengiriman yang tidak dapat diproses. Perlu dicatat pula bahwa `SHIPPING_RATE_NOT_FOUND` memperbaiki kelemahan nyata pada implementasi saat ini: `ongkirRepository.findByIdOriginCityAndIdDistrict(...)` dipanggil tanpa pemeriksaan `null`, sehingga kecamatan tanpa data ongkir akan menyebabkan `NullPointerException` alih-alih pesan galat yang informatif.

### 18.4 Validasi Nilai Custom Field pada Submit Order

#### 18.4.1 Penanganan `fieldKey` yang tidak dikenal

Terdapat dua mode yang mungkin, dan dokumen ini menetapkan pilihannya:

| Mode | Perilaku | Keputusan |
|---|---|---|
| *Strict* | Tolak `400` bila ada `fieldKey` yang tidak dikenal | **Tidak dipilih** |
| *Lenient* | Abaikan secara senyap, catat pada log level `WARN` | **Dipilih** |

Alasan memilih mode *lenient*: endpoint bersifat publik dan dipanggil dari halaman checkout yang dapat berada dalam keadaan *cache* lama. Bila Admin baru saja menonaktifkan sebuah field, pelanggan yang halamannya sudah termuat sebelum perubahan akan tetap mengirim field tersebut. Menolak permintaan tersebut berarti **kehilangan penjualan karena alasan yang sepenuhnya administratif**. Mengabaikannya secara senyap tetap aman, karena field yang tidak dikenal memang tidak akan pernah disimpan. Log `WARN` tetap dibuat agar anomali dapat terpantau.

Pengecualian tunggal dari mode *lenient*: `fieldKey` yang **dikenal namun berkategori SYSTEM** tetap ditolak keras dengan `SYSTEM_FIELD_IN_CUSTOM_PAYLOAD` (BR-19), karena ini merupakan indikasi upaya penyalahgunaan, bukan *cache* lama.

#### 18.4.2 Validasi nilai per tipe

| `fieldType` | Aturan validasi nilai | Normalisasi sebelum simpan |
|---|---|---|
| `TEXT` | `String`; *trim*; panjang sesuai `minLength`/`maxLength` (default maksimum 500); wajib lolos `pattern` bila diisi | *trim*; sanitasi HTML |
| `TEXTAREA` | `String`; *trim*; maksimum 2000 karakter | *trim*; sanitasi HTML; normalisasi akhir baris menjadi `\n` |
| `NUMBER` | dapat diurai sebagai `BigDecimal`; berada dalam rentang `min`–`max` | normalisasi ke notasi kanonik (menghapus nol di depan, memakai `.` sebagai pemisah desimal) |
| `EMAIL` | wajib lolos pola email; maksimum 254 karakter | *trim*; huruf kecil |
| `SELECT` | `String`; wajib salah satu `option.value` (perbandingan **peka huruf** — nilai berasal dari konfigurasi, bukan dari input bebas) | apa adanya |
| `RADIO` | idem `SELECT` | apa adanya |
| `CHECKBOX` | larik `String`; setiap elemen wajib salah satu `option.value`; tanpa duplikat; jumlah sesuai `minSelected`/`maxSelected` | urutkan mengikuti urutan `options`; simpan ke `field_value_json` sebagai larik dan ke `field_value` sebagai gabungan `", "` |
| `DATE` | `String` berformat `yyyy-MM-dd`; wajib tanggal kalender yang valid; berada dalam rentang `minDate`–`maxDate` | apa adanya (sudah kanonik) |
| `FILE` | `String` berupa URL; wajib berada pada domain penyimpanan milik sistem (daftar putih); objeknya wajib benar-benar ada | simpan URL ke `field_value`, metadata ke `field_value_json` |

Validasi pada `FILE` menuntut perhatian khusus: karena klien mengirim URL (bukan berkas), penyerang dapat mengirim URL sembarang. Karena itu implementasi **wajib** memverifikasi bahwa URL berawalan pada *prefix* penyimpanan sistem dan bahwa objek tersebut benar-benar ada pada MinIO. Rincian pada §23.

#### 18.4.3 Perlakuan nilai kosong

| Nilai diterima | `isRequired = true` | `isRequired = false` |
|---|---|---|
| Atribut tidak ada pada payload | `REQUIRED_FIELD_MISSING` | tidak ada baris dibuat |
| `null` | `REQUIRED_FIELD_MISSING` | tidak ada baris dibuat |
| `""` atau hanya spasi | `REQUIRED_FIELD_MISSING` | tidak ada baris dibuat |
| `[]` (larik kosong untuk `CHECKBOX`) | `REQUIRED_FIELD_MISSING` | tidak ada baris dibuat |
| `"0"` atau `0` | **dianggap terisi** — valid | baris dibuat |
| `false` | **dianggap terisi** — valid | baris dibuat |

Baris terakhir penting untuk dicatat oleh Developer: angka `0` dan boolean `false` adalah nilai yang sah dan **tidak boleh** diperlakukan sebagai kosong. Kesalahan klasik pada implementasi validasi dinamis adalah memakai pemeriksaan kebenaran (*truthiness*) yang menganggap `0` sebagai nilai kosong.

---

## 19. Error Handling

### 19.1 Struktur Galat

Fitur ini mengikuti konvensi codebase (`RestResponse`, `ErrorResponse`, `ErrorDto`), dengan penambahan dua atribut opsional pada `ErrorDto`:

```jsonc
{
  "field":   "ukuran_baju",              // fieldKey, atau path payload untuk galat konfigurasi
  "code":    "VALUE_NOT_IN_OPTIONS",     // BARU — kode mesin, stabil, untuk penanganan frontend
  "message": "Nilai 'XXL' tidak tersedia pada pilihan Ukuran Baju.",
  "meta":    { "allowedValues": ["S","M","L","XL"] }   // BARU — konteks tambahan, opsional
}
```

Penambahan `code` merupakan keputusan desain yang penting. Tanpa kode mesin, frontend terpaksa mencocokkan teks pesan untuk menentukan cara menangani galat — praktik yang rapuh dan akan rusak pada setiap perbaikan redaksi. Atribut `code` bersifat stabil dan wajib diperlakukan sebagai bagian dari kontrak API. Atribut `message` berbahasa Indonesia dan diperuntukkan bagi pengguna akhir; redaksinya boleh berubah kapan saja.

### 19.2 Katalog Kode Galat

#### Galat konfigurasi form (`PUT /produk/{id}/form-config`)

| Kode | HTTP | Pesan (Bahasa Indonesia) | Penanganan frontend |
|---|---|---|---|
| `FIELDS_REQUIRED` | 400 | Daftar field tidak boleh kosong. | Tampilkan galat tingkat form |
| `SYSTEM_FIELD_NOT_DELETABLE` | 400 | System Field '{label}' tidak dapat dihapus. | Muat ulang konfigurasi; tampilkan dialog penjelasan |
| `SYSTEM_FIELD_IMMUTABLE_ATTRIBUTE` | 400 | Atribut '{atribut}' pada System Field '{fieldKey}' tidak dapat diubah. | Kembalikan nilai kontrol ke nilai server |
| `UNKNOWN_SYSTEM_FIELD` | 400 | System Field '{fieldKey}' tidak dikenal. | Muat ulang konfigurasi (indikasi klien kedaluwarsa) |
| `INVALID_FIELD_CATEGORY` | 400 | Kategori field harus SYSTEM atau CUSTOM. | Galat pemrograman; laporkan |
| `INVALID_FIELD_KEY_FORMAT` | 400 | Format field key '{fieldKey}' tidak valid. | Tandai input |
| `RESERVED_FIELD_KEY` | 400 | Field key '{fieldKey}' merupakan kata yang dipakai sistem. Gunakan label lain. | Tandai input label |
| `DUPLICATE_FIELD_KEY` | 400 | Terdapat field key ganda: '{fieldKey}'. | Tandai kedua field |
| `LABEL_REQUIRED` | 400 | Label field wajib diisi. | Tandai input label |
| `LABEL_TOO_LONG` | 400 | Label maksimum 150 karakter. | Tandai input label |
| `INVALID_FIELD_TYPE` | 400 | Tipe field '{tipe}' tidak dikenal. | Tandai pemilih tipe |
| `FIELD_TYPE_RESERVED_FOR_SYSTEM` | 400 | Tipe field '{tipe}' hanya dapat dipakai oleh System Field. | Sembunyikan tipe tersebut dari pemilih |
| `OPTIONS_REQUIRED_FOR_TYPE` | 400 | Tipe field '{tipe}' memerlukan minimal satu pilihan. | Fokuskan ke editor options |
| `OPTIONS_NOT_ALLOWED_FOR_TYPE` | 400 | Tipe field '{tipe}' tidak menerima daftar pilihan. | Sembunyikan editor options |
| `TOO_MANY_OPTIONS` | 400 | Jumlah pilihan maksimum {batas}. | Tandai editor options |
| `OPTION_INCOMPLETE` | 400 | Setiap pilihan wajib memiliki label dan nilai. | Tandai baris option |
| `OPTION_TOO_LONG` | 400 | Label dan nilai pilihan maksimum 100 karakter. | Tandai baris option |
| `DUPLICATE_OPTION_VALUE` | 400 | Nilai pilihan '{value}' ganda. | Tandai baris option |
| `INVALID_DEFAULT_VALUE` | 400 | Nilai bawaan tidak sesuai dengan tipe atau pilihan yang tersedia. | Tandai input nilai bawaan |
| `DEFAULT_VALUE_NOT_ALLOWED` | 400 | Tipe field '{tipe}' tidak menerima nilai bawaan. | Sembunyikan input |
| `INVALID_VALIDATION_RULE` | 400 | Aturan validasi tidak valid: {detail}. | Tandai input terkait |
| `INVALID_RANGE` | 400 | Nilai minimum tidak boleh lebih besar dari maksimum. | Tandai kedua input |
| `INVALID_SORT_ORDER` | 400 | Urutan tampil harus berupa angka 1–999. | Perbaiki secara otomatis |
| `CUSTOM_FIELD_LIMIT_EXCEEDED` | 400 | Jumlah field tambahan aktif maksimum 50. | Nonaktifkan tombol Tambah |
| `FIELD_IN_USE` | 400 | Field '{label}' sudah dipakai oleh {usageCount} pesanan sehingga tidak dapat dihapus. Nonaktifkan field bila Anda tidak ingin menampilkannya lagi. | Tampilkan dialog dengan tombol "Nonaktifkan" |
| `PAYLOAD_TOO_LARGE` | 413 | Ukuran data konfigurasi melampaui batas. | Tampilkan galat tingkat form |

#### Galat submit order (`POST /order/create`)

| Kode | HTTP | Pesan | Penanganan frontend |
|---|---|---|---|
| `REQUIRED_FIELD_MISSING` | 400 | {label} wajib diisi. | Tandai field, geser tampilan ke field pertama yang bergalat |
| `INVALID_VALUE_TYPE` | 400 | Nilai {label} tidak sesuai format yang diharapkan. | Tandai field |
| `VALUE_NOT_IN_OPTIONS` | 400 | Nilai '{value}' tidak tersedia pada pilihan {label}. | Muat ulang konfigurasi lalu tandai field |
| `VALUE_RULE_VIOLATION` | 400 | {label} tidak memenuhi ketentuan: {detail}. | Tandai field |
| `SYSTEM_FIELD_IN_CUSTOM_PAYLOAD` | 400 | Field '{fieldKey}' merupakan System Field dan tidak dapat dikirim melalui customFields. | Galat pemrograman; laporkan |
| `INVALID_PHONE_NUMBER` | 400 | Nomor WhatsApp tidak valid. | Tandai field |
| `INVALID_LOCATION` | 400 | Data lokasi tidak valid. | Setel ulang pemilih lokasi |
| `LOCATION_HIERARCHY_MISMATCH` | 400 | Kota atau kecamatan tidak sesuai dengan provinsi yang dipilih. | Setel ulang pemilih lokasi |
| `SHIPPING_RATE_NOT_FOUND` | 400 | Ongkos kirim untuk kecamatan yang dipilih belum tersedia. Silakan hubungi penjual. | Tampilkan pesan; sarankan hubungi via WhatsApp |
| `FILE_URL_NOT_ALLOWED` | 400 | URL berkas tidak dikenali oleh sistem. | Minta pengguna mengunggah ulang |
| `FILE_NOT_FOUND` | 400 | Berkas yang diunggah tidak ditemukan. Silakan unggah ulang. | Setel ulang field berkas |

#### Galat unggah berkas (`POST /produk/form-config/upload`)

| Kode | HTTP | Pesan |
|---|---|---|
| `FILE_REQUIRED` | 400 | Berkas wajib disertakan. |
| `FILE_TYPE_NOT_ALLOWED` | 400 | Tipe berkas '{contentType}' tidak diizinkan. |
| `FILE_SIZE_LIMIT_EXCEEDED` | 413 | Ukuran berkas melampaui batas {maxFileSizeKb} KB. |
| `FIELD_NOT_FOUND` | 404 | Field '{fieldKey}' tidak ditemukan pada produk ini. |
| `FIELD_NOT_FILE_TYPE` | 400 | Field '{fieldKey}' bukan field unggah berkas. |
| `UPLOAD_RATE_LIMIT` | 429 | Terlalu banyak permintaan unggah. Silakan coba beberapa saat lagi. |
| `STORAGE_UNAVAILABLE` | 503 | Layanan penyimpanan sedang tidak tersedia. Silakan coba beberapa saat lagi. |

### 19.3 Prinsip Penanganan Galat

| Prinsip | Penerapan |
|---|---|
| **Kumpulkan seluruh galat, jangan berhenti pada yang pertama** | Validasi mengumpulkan `List<ErrorDto>` lalu mengembalikannya sekaligus. Sejalan dengan US-15: pelanggan memperbaiki seluruh kesalahan dalam satu putaran |
| ***All-or-nothing*** | Kegagalan apa pun pada `PUT form-config` atau `POST /order/create` membatalkan seluruh operasi (`@Transactional` + rollback) |
| **Pesan memakai label, bukan field key** | Pesan galat menyebut "Ukuran Baju wajib diisi", bukan "ukuran_baju wajib diisi" — pelanggan tidak mengenal *field key* |
| **`field` pada `ErrorDto` memakai field key** | Sebaliknya, atribut `field` memakai `fieldKey` agar frontend dapat memetakannya ke input secara terprogram |
| **Jangan bocorkan detail internal** | Pesan `Exception` mentah, *stack trace*, dan nama tabel tidak boleh muncul pada respons endpoint publik. `ProdukController` dan `OrderController` existing mengembalikan `e.getMessage()` secara langsung — praktik ini **wajib** diperbaiki untuk endpoint baru dan diperlakukan sebagai temuan pada endpoint existing (§23) |
| **Degradasi anggun pada data non-kritis** | Kegagalan menghitung `usageCount` mengembalikan `null` beserta log `WARN`, tanpa menggagalkan respons `GET form-config` (NFR-5) |
| **Idempoten pada operasi tulis nilai** | *Unique index* `(id_order, field_key)` membuat pengiriman ganda tidak menghasilkan baris duplikat |
| **Galat penyimpanan tidak boleh menggagalkan order** — kecuali pada validasi | Kegagalan MinIO terjadi pada tahap unggah (sebelum submit), bukan pada tahap penyimpanan order, sehingga tidak ada risiko order gagal karena penyimpanan objek |

---

## 20. Edge Case

| ID | Skenario | Perilaku yang ditetapkan | Alasan |
|---|---|---|---|
| EC-1 | Admin mengubah konfigurasi **saat** pelanggan sedang mengisi form checkout | Submit divalidasi terhadap konfigurasi terbaru. Field yang baru menjadi wajib namun tidak terkirim menghasilkan `REQUIRED_FIELD_MISSING`; field yang baru dinonaktifkan diabaikan secara senyap | Konfigurasi terbaru adalah kebenaran (BR-36); mode *lenient* (§18.4.1) mencegah kehilangan penjualan pada kasus yang tidak berbahaya |
| EC-2 | Admin menonaktifkan field wajib, lalu pelanggan dengan halaman *cache* mengirimkannya | Nilai diabaikan, order tetap dibuat, tercatat `WARN` | Field nonaktif tidak divalidasi dan tidak disimpan (BR-25) |
| EC-3 | Dua Admin menyimpan konfigurasi produk yang sama secara bersamaan | Penulis terakhir menang (*last-write-wins*). Direkomendasikan menambahkan `If-Unmodified-Since` atau atribut `version` pada Fase 2 (§24) | *Optimistic locking* penuh menambah kompleksitas yang tidak sebanding untuk operasi yang jarang dilakukan bersamaan |
| EC-4 | Custom Field dinonaktifkan lalu diaktifkan kembali dengan `field_key` yang sama | Berfungsi normal; order lama dan baru berbagi `field_key` yang sama dan tetap dapat dibaca | `field_key` bersifat stabil (BR-22); *snapshot* menjaga label per periode |
| EC-5 | Custom Field dihapus permanen (`usageCount = 0`), lalu field baru dibuat dengan label yang sama sehingga menghasilkan `field_key` yang sama | Diizinkan. Karena `usageCount` nol, tidak ada order yang terpengaruh | Aman berdasarkan definisi BR-23 |
| EC-6 | Produk disalin, lalu konfigurasi form salinannya diubah | Kedua produk sepenuhnya independen | Konfigurasi bersifat per produk (BR-1) |
| EC-7 | Produk di-*soft delete* (`is_deleted = true`) | Konfigurasi tetap ada; `order_custom_field` milik order-order produk tersebut tetap utuh dan tetap dapat dibaca | BR-34 |
| EC-8 | Order dihapus permanen oleh administrator sistem | Baris `order_custom_field` terhapus melalui `ON DELETE CASCADE` | Nilai field tidak bermakna tanpa order induk |
| EC-9 | Label diubah menjadi teks yang menghasilkan `field_key` sama dengan field lain | Tidak terjadi; `field_key` Custom Field tidak pernah dihitung ulang setelah dibuat | BR-22 |
| EC-10 | Label hanya berisi emoji atau karakter non-ASCII sepenuhnya | *Slugify* menghasilkan `field`, lalu tabrakan diselesaikan menjadi `field_2`, `field_3`, dan seterusnya | §11.5 langkah 6 dan 8 |
| EC-11 | Pelanggan mengirim `customFields` berupa larik kosong | Diterima; berlaku sebagai tidak mengirim Custom Field. Field wajib yang aktif tetap menghasilkan galat | Konsisten dengan §18.4.3 |
| EC-12 | Pelanggan mengirim entri `customFields` ganda dengan `fieldKey` sama | Entri terakhir menang; *unique index* mencegah baris duplikat | Perilaku deterministik dan idempoten |
| EC-13 | Nilai `CHECKBOX` dikirim sebagai string tunggal alih-alih larik | Diterima dan dibungkus menjadi larik satu elemen | Toleransi wajar terhadap klien; mengurangi kegagalan yang tidak perlu |
| EC-14 | Nilai `NUMBER` dikirim sebagai string (`"12"`) | Diterima dan diurai | JSON longgar pada tipe; menolaknya tidak memberi manfaat |
| EC-15 | Nilai `DATE` dikirim dengan format `dd/MM/yyyy` | Ditolak `INVALID_VALUE_TYPE` dengan pesan yang menyebutkan format yang diharapkan | Ketegasan format tanggal mencegah ambiguitas hari/bulan |
| EC-16 | Nilai `FILE` merujuk URL di luar penyimpanan sistem | Ditolak `FILE_URL_NOT_ALLOWED` | Pencegahan SSRF dan penyisipan tautan (§23) |
| EC-17 | Nilai `FILE` merujuk objek yang sudah dibersihkan oleh *cleanup* (unggah lebih dari 60 menit lalu, order belum dibuat) | Ditolak `FILE_NOT_FOUND`; pelanggan diminta mengunggah ulang | Lebih baik gagal jelas daripada menyimpan URL mati |
| EC-18 | Produk existing yang belum ter-*backfill* dibuka pada layar konfigurasi | *Self-healing* (FR-4) membuat System Field yang kurang sebelum respons dikembalikan | Menjamin BR-10 tanpa bergantung pada keberhasilan *backfill* massal |
| EC-19 | Produk existing memiliki baris `produk_form_config` legacy tanpa `field_key` | *Backfill* memetakannya berdasarkan heuristik label; baris yang tidak dapat dipetakan menjadi Custom Field dengan `field_key` hasil *slugify* (§22.3) | Tidak ada data yang dibuang |
| EC-20 | Produk existing memiliki nilai `orders` dan `is_mandatory` bernilai `NULL` (akibat celah implementasi §1.3.d) | Dinormalkan menjadi `999` dan `false` oleh skrip migrasi | `NULL` tidak dapat diurutkan secara deterministik |
| EC-21 | Order dibuat melalui `POST /chat/order` (jalur agen, `source = CST_CHAT`) untuk produk dengan Custom Field wajib | Order berhasil dibuat tanpa Custom Field; kartu "Informasi Tambahan" tidak muncul | BR-39: memaksakan *required* akan memblokir operasional agen |
| EC-22 | Order abandoned dikonversi menjadi order penuh (`source = ADM_ABANDONED`) | Custom Field tidak tersedia (tidak tertangkap saat abandoned, OOS-1); order tetap berhasil | Konsisten dengan OOS-1 dan BR-39 |
| EC-23 | Bot AI menerima pesan konfirmasi order untuk produk dengan Custom Field | Alur bot tidak berubah; Custom Field tidak menjadi konteks bot pada fase ini | OOS-8 |
| EC-24 | Export Excel dijalankan untuk workspace dengan produk ber-Custom Field | Kolom export tidak berubah; Custom Field tidak tercakup | OOS-7, dengan rencana Fase 2 |
| EC-25 | Produk memiliki 50 Custom Field aktif dan 30 nonaktif | Diizinkan; batas hanya berlaku pada field **aktif** | Field nonaktif tidak dirender sehingga tidak memengaruhi performa checkout |
| EC-26 | Seluruh Custom Field produk dinonaktifkan | Checkout merender hanya enam System Field — identik dengan perilaku sebelum fitur ini | Degradasi bersih |
| EC-27 | Konfigurasi memuat `sortOrder` ganda (mis. dua field bernilai 3) | Diterima lalu dinormalkan menjadi 1..N; urutan relatif ditentukan oleh urutan pada payload | FR-13; mencegah kegagalan hanya karena masalah kosmetik |
| EC-28 | `sortOrder` tidak dikirim sama sekali | Urutan mengikuti urutan entri pada payload, kemudian dinormalkan | Kemudahan bagi klien |
| EC-29 | Nilai `TEXTAREA` memuat *payload* HTML atau `<script>` | Disanitasi sebelum penyimpanan; tersimpan sebagai teks biasa | §23 |
| EC-30 | Pelanggan mengirim `customFields` berukuran sangat besar (misalnya 10 MB teks) | Ditolak `413` oleh batas ukuran permintaan sebelum mencapai lapisan aplikasi | NFR-16 |
| EC-31 | `validation.pattern` diisi regex yang rentan *catastrophic backtracking* | Ditolak pada saat penyimpanan konfigurasi melalui pemeriksaan kompleksitas regex; evaluasi saat submit dibatasi *timeout* | Pencegahan ReDoS (§23) |
| EC-32 | Konfigurasi disimpan tanpa perubahan apa pun | `200` dengan `created`, `updated`, `deleted` yang mencerminkan keadaan (boleh kosong); `updatedAt` tetap diperbarui | Idempoten dari sudut pandang klien |

---

## 21. Dampak terhadap Fitur Existing

Bagian ini merupakan rujukan wajib bagi QA dalam menyusun cakupan uji regresi.

### 21.1 Ringkasan Tingkat Dampak

| Fitur / Komponen existing | Tingkat dampak | Sifat perubahan |
|---|---|---|
| Perhitungan ongkos kirim | **Tidak ada** | System Field tetap pada kolom bertipe kuat |
| Integrasi WhatsApp (konfirmasi & follow-up) | **Tidak ada** | `nama_penerima` dan `nomor_whatsapp` tidak berubah |
| Export Excel order | **Tidak ada** | Kolom export tidak berubah (OOS-7) |
| Dashboard & Reporting | **Tidak ada** | Kolom lokasi tidak berubah |
| Duplicate checking abandoned order | **Tidak ada** | Berbasis `nama_penerima` + 4 digit nomor |
| Pencarian order (`search`) | **Tidak ada** | Berbasis kolom `order` |
| Bot AI / RAG | **Tidak ada** | Tidak menyentuh alur bot (OOS-8) |
| Conversation & Chat | **Tidak ada** | — |
| Blast campaign | **Tidak ada** | — |
| `POST /produk` (simpan produk) | **Sedang** | Pola penyimpanan `formConfig` berubah; validasi dilonggarkan |
| `GET /produk/{id}` (detail produk) | **Rendah** | `formConfig` diperkaya (aditif) |
| `GET /produk/checkout` | **Sedang** | `formConfig` diperkaya; hanya field aktif; terurut |
| `GET /produk/copy` | **Rendah** | Menyalin atribut tambahan |
| `POST /order/create` | **Sedang** | Menerima `customFields`; validasi tambahan |
| `POST /chat/order` (order oleh agen) | **Rendah** | Custom Field dilewati (BR-39) |
| Halaman Checkout (frontend) | **Tinggi** | Ditulis ulang menjadi *renderer* dinamis |
| Halaman Produk (frontend dashboard) | **Tinggi** | Tab konfigurasi form baru |
| Halaman Detail Order (frontend) | **Rendah** | Kartu baru, kondisional |

### 21.2 Dampak pada `ProdukService.saveProduct()` — Perubahan Paling Berisiko

Implementasi saat ini:

```java
// ProdukService.saveProduct() — kondisi existing
if (data.getId() != null){
    ...
    produkFormConfigRepository.deleteProdukFormConfigByIdProduk(produk.getId());  // (1)
    ...
}
...
for (var dataFormConfig : data.getFormConfig()){                                   // (2)
    var config = new ProdukFormConfig();
    config.setIdProduk(savedProduk.getId());
    config.setLabel(dataFormConfig.getLabel());
    config.setPlaceholder(dataFormConfig.getPlaceholder());
    config.setTipeField(dataFormConfig.getTipeField());
    produkFormConfigRepository.save(config);                                       // (3)
}
```

Tiga masalah yang wajib diperbaiki:

| # | Masalah | Konsekuensi bila dibiarkan |
|---|---|---|
| (1) | Seluruh baris konfigurasi dihapus pada setiap update produk | Bila layar Edit Produk menyimpan tanpa memuat `formConfig` (misalnya pengguna hanya mengubah harga dari tab lain, dan frontend mengirim `formConfig: []`), **seluruh konfigurasi form produk lenyap** — termasuk keenam System Field. Ini melanggar BR-10 secara langsung dan merupakan risiko kehilangan data yang nyata |
| (2) | Tidak ada penanganan `null` pada `data.getFormConfig()` | `NullPointerException` bila `formConfig` tidak dikirim. Saat ini tertutup oleh `@NotNull`, namun FR-38 melonggarkan anotasi tersebut sehingga penanganan `null` menjadi wajib |
| (3) | `orders`, `is_mandatory`, `created_at`, `updated_at` tidak pernah diisi | Nilai selalu `NULL` meskipun dibaca kembali pada tiga tempat. Urutan form menjadi tidak deterministik |

Perubahan yang ditetapkan:

```java
// Rancangan target
// 1. Hapus pemanggilan deleteProdukFormConfigByIdProduk() dari saveProduct()
// 2. Untuk produk BARU: panggil formConfigService.seedSystemFields(savedProduk.getId())
// 3. Untuk formConfig inline (bila dikirim): upsert berdasarkan (id_produk, field_key),
//    tolak entri yang berupaya mengubah atribut terkunci System Field
// 4. Selalu isi sortOrder, isRequired, createdAt, updatedAt
// 5. Perlakukan formConfig == null sebagai "tidak ada perubahan konfigurasi"
```

Poin 5 merupakan keputusan yang penting: `formConfig` yang bernilai `null` berarti *"jangan sentuh konfigurasi"*, sedangkan `formConfig` berupa larik kosong (`[]`) juga diperlakukan sebagai *"jangan sentuh konfigurasi"* — **bukan** sebagai "hapus semua". Perbedaan semantik ini penting karena satu-satunya jalur resmi untuk menghapus field adalah `PUT /produk/{id}/form-config`, yang memiliki validasi lengkap. Melalui `POST /produk`, penghapusan field tidak akan pernah terjadi.

### 21.3 Dampak pada `AddProdukDto`

`@NotNull(message = "Form Config Wajib Diisi.")` dilonggarkan (FR-38). Perlu dicatat bahwa `AddProdukDto` merupakan `@Value` (immutable) sehingga atribut `formConfig` tidak memiliki penginisialisasi default — berbeda dari atribut lain seperti `gambarProduk` dan `poinFitur` yang diinisialisasi `new ArrayList<>()`. Setelah `@NotNull` dilonggarkan, atribut ini dapat bernilai `null` dan **setiap** pembacaannya wajib dilindungi pemeriksaan `null`.

Dampak bagi frontend dashboard: layar Produk **tidak perlu lagi** mengirim `formConfig` pada `POST /produk`. Konfigurasi form dikelola sepenuhnya melalui endpoint tersendiri. Ini menyederhanakan payload produk sekaligus menghilangkan kelas galat "konfigurasi hilang karena tab lain menyimpan".

### 21.4 Dampak pada `GET /produk/checkout`

| Aspek | Sebelum | Sesudah | Risiko regresi |
|---|---|---|---|
| Jumlah entri `formConfig` | Seluruh baris (tanpa penyaringan) | Hanya `is_active = true` | **Rendah** — produk existing tidak memiliki field nonaktif setelah *backfill* menetapkan `is_active = true` |
| Urutan entri | Tidak deterministik (`orders` bernilai `NULL`) | `ORDER BY orders ASC` | **Menguntungkan** — memperbaiki perilaku yang sebelumnya acak |
| Atribut per entri | 5 atribut | 13 atribut + 3 alias | **Rendah** — aditif; alias menjaga klien lama (FR-23) |
| Nilai `order` dan `isMandatory` | Sering `NULL` | Selalu terisi | **Menguntungkan** — frontend tidak lagi perlu menangani `NULL` |

Perlu dicatat bahwa `GET /produk/checkout` merupakan endpoint publik yang dipanggil pada setiap kunjungan halaman checkout. Penambahan atribut memperbesar ukuran respons; untuk produk dengan 20 field, pertambahan diperkirakan sekitar 4–6 KB — tidak signifikan, namun tetap perlu dipantau (NFR-1).

### 21.5 Dampak pada Alur Pembuatan Order

Tiga jalur pembuatan order pada codebase, beserta perlakuannya:

| Jalur | Pemicu | Perlakuan Custom Field |
|---|---|---|
| `OrderOrchestrationService.createOrder()` | `POST /order/create` dari halaman checkout publik | **Validasi penuh + penyimpanan** |
| `OrderOrchestrationService.createOrderOnChat()` | `POST /chat/order` oleh agen | Validasi *required* dilewati; nilai disimpan bila dikirim (BR-39). Perlu dicatat bahwa `ChatAddOrderRequest` **tidak** diperluas pada fase ini, sehingga praktis tidak ada Custom Field yang tersimpan melalui jalur ini |
| `OrderService.saveAbandonedOrder()` | `source` mengandung `abandoned` | Custom Field tidak diproses sama sekali (OOS-1) |

Urutan operasi pada `createOrder()` yang wajib dipatuhi:

```
1. Validasi Custom Field  ← DIDAHULUKAN, sebelum penulisan apa pun
2. createOrderInternal()  ← INSERT order
3. saveAll(orderCustomField)
4. publishEvent(OrderCreatedEvent)
```

Validasi **wajib** mendahului langkah 2. Bila validasi dijalankan setelah order tersimpan, kegagalan validasi akan memicu *rollback* — namun `produk.setOrderCount(orderCount + 1)` dan `generateOrderCode()` yang berbasis `OrderSequence` berpotensi menyisakan efek samping (nomor urut yang terlewat). Mendahulukan validasi menghindarkan pemborosan nomor urut order akibat permintaan yang tidak valid.

### 21.6 Dampak pada Layanan Penyimpanan (MinIO)

Custom Field bertipe `FILE` memerlukan jalur unggah yang dapat diakses secara publik. Kondisi existing yang relevan:

- `StorageService.upload(MultipartFile)` dan `uploadImage(MultipartFile)` sudah tersedia dan berfungsi.
- `POST /master/saktiform-media` — satu-satunya endpoint unggah pada `MasterController` — pada implementasi saat ini **mengembalikan URL statis yang di-*hardcode*** dan pemanggilan `masterService.saveSaktiformMedia(file)` dinonaktifkan (dikomentari). Endpoint ini karena itu **tidak dapat dipakai** untuk fitur ini tanpa perbaikan.
- Endpoint tersebut juga berada di area terautentikasi, sedangkan halaman checkout tidak memiliki token.

Implikasi bagi perencanaan: tipe field `FILE` memiliki **ketergantungan pada pekerjaan yang belum selesai** di luar cakupan fitur ini. Rekomendasi yang ditetapkan pada §22.5: tipe `FILE` dirilis pada tahap terakhir, setelah `POST /produk/form-config/upload` beserta pembatasan lajunya (*rate limiting*), daftar putih MIME, dan pekerjaan pembersihan objek yatim selesai diimplementasikan. Delapan tipe field lainnya tidak memiliki ketergantungan ini dan dapat dirilis lebih dahulu.

### 21.7 Dampak pada Frontend Checkout — Perubahan Paling Besar

Halaman checkout saat ini merender form dengan markup tetap. Setelah fitur ini, halaman tersebut wajib berubah menjadi *renderer* dinamis (§14.3). Ini merupakan penulisan ulang komponen, bukan penambahan, dan menyimpan risiko regresi tertinggi pada seluruh fitur ini. Mitigasi yang ditetapkan:

| Mitigasi | Rincian |
|---|---|
| *Feature flag* per workspace | Halaman checkout memakai *renderer* dinamis hanya untuk workspace yang telah diaktifkan; sisanya memakai markup lama |
| Alias kompatibilitas | FR-23 memungkinkan *renderer* lama tetap berfungsi selama masa transisi |
| Pengujian konversi | Pantau *conversion rate* checkout per workspace selama dua minggu setelah pengaktifan; siapkan mekanisme kembali (*rollback*) per workspace |
| Prioritas paritas visual | Rilis pertama *renderer* dinamis wajib menghasilkan tampilan yang identik dengan form lama untuk produk yang belum memiliki Custom Field |

### 21.8 Cakupan Uji Regresi Wajib

| # | Kasus uji regresi | Prioritas |
|---|---|---|
| RT-1 | Order melalui checkout publik: ongkir terhitung benar untuk lima kombinasi gudang–kecamatan | Kritis |
| RT-2 | Pesan WhatsApp konfirmasi terkirim dengan `namaPenerima` dan `namaProduk` yang benar | Kritis |
| RT-3 | Pesan follow-up COD dan Transfer terkirim oleh `OrderEventListener` | Kritis |
| RT-4 | Export Excel menghasilkan seluruh kolom existing dengan nilai yang benar | Kritis |
| RT-5 | `GET /order` dengan filter provinsi, kota, kecamatan, status, jenis pembayaran, dan rentang tanggal | Kritis |
| RT-6 | Pencarian order berdasarkan nama dan nomor WhatsApp | Tinggi |
| RT-7 | Dashboard: `TotalOrderReportView` dan `TotalPendapatanReportView` menghasilkan angka yang sama sebelum dan sesudah rilis | Kritis |
| RT-8 | Abandoned order: penyimpanan, pencarian, dan konversi menjadi order penuh | Tinggi |
| RT-9 | Duplicate checking abandoned order tetap berfungsi | Tinggi |
| RT-10 | `POST /produk` untuk produk baru: enam System Field ter-*seed* | Kritis |
| RT-11 | `POST /produk` untuk update produk **tanpa** mengirim `formConfig`: konfigurasi existing tetap utuh | Kritis |
| RT-12 | `GET /produk/copy`: seluruh konfigurasi tersalin | Tinggi |
| RT-13 | Order melalui `POST /chat/order` oleh agen berhasil untuk produk dengan Custom Field wajib | Tinggi |
| RT-14 | Bot AI tetap merespons pesan masuk dan mengenali pesan konfirmasi order | Tinggi |
| RT-15 | Checkout untuk produk **tanpa** Custom Field menghasilkan tampilan dan perilaku yang identik dengan sebelum rilis | Kritis |

---

## 22. Migration Plan

### 22.1 Kondisi Awal dan Sasaran

| Aspek | Kondisi awal | Sasaran |
|---|---|---|
| Tabel `produk_form_config` | Ada; 9 kolom; tanpa `field_key`, tanpa kategori | 16 kolom; setiap baris memiliki `field_key` dan `field_category` |
| Baris System Field | Tidak ada konsep System Field | Setiap produk aktif memiliki tepat 6 baris `SYSTEM` |
| Nilai `orders`, `is_mandatory` | Sebagian besar `NULL` (celah implementasi §1.3.d) | Selalu terisi |
| Tabel `order_custom_field` | Tidak ada | Ada beserta indeks dan constraint |
| Baris legacy `produk_form_config` | Ada, dengan label dan tipe tanpa identitas | Terpetakan menjadi `SYSTEM` atau `CUSTOM` |

### 22.2 Strategi Umum

Migrasi dijalankan melalui **komponen aplikasi**, bukan skrip SQL manual, mengikuti pola yang sudah ada pada codebase (`BlastSchemaInitializer`, `LabelSchemaInitializer`) yang mengeksekusi DDL/DML idempoten pada `ApplicationReadyEvent`. Alasan pemilihan pendekatan ini:

1. Proyek belum memakai Flyway maupun Liquibase; memperkenalkan alat migrasi baru pada fitur ini akan memperluas cakupan secara tidak proporsional.
2. Pola *schema initializer* sudah menjadi konvensi codebase, sehingga tim sudah terbiasa dengan mekanismenya.
3. `ddl-auto=update` menangani penambahan kolom secara otomatis; *initializer* hanya perlu menangani hal-hal yang tidak dapat dihasilkan Hibernate — *unique index* fungsional, `CHECK constraint`, dan *backfill* data.

Komponen yang dibuat: `ProdukFormConfigSchemaInitializer`, dijalankan sekali pada setiap *startup*, seluruh operasinya idempoten (NFR-15).

### 22.3 Tahapan Migrasi

#### Tahap M-1 — Penambahan Kolom (otomatis oleh Hibernate)

Penambahan atribut pada entity `ProdukFormConfig` menyebabkan `ddl-auto=update` menambahkan tujuh kolom baru (`field_key`, `field_category`, `help_text`, `is_active`, `options`, `default_value`, `validation_rule`) serta membuat tabel `order_custom_field`. Seluruhnya bersifat aditif dan tidak memerlukan intervensi.

**Verifikasi:** kolom-kolom tersebut ada pada `information_schema.columns`; tabel `order_custom_field` ada.

#### Tahap M-2 — Normalisasi Nilai `NULL`

```sql
UPDATE produk_form_config SET orders       = 999    WHERE orders       IS NULL;
UPDATE produk_form_config SET is_mandatory = false  WHERE is_mandatory IS NULL;
UPDATE produk_form_config SET tipe_field   = 'TEXT' WHERE tipe_field   IS NULL OR btrim(tipe_field) = '';
UPDATE produk_form_config SET is_active    = true   WHERE is_active    IS NULL;
UPDATE produk_form_config SET created_at   = now()  WHERE created_at   IS NULL;
UPDATE produk_form_config SET updated_at   = now()  WHERE updated_at   IS NULL;
UPDATE produk_form_config SET label        = 'Field' WHERE label IS NULL OR btrim(label) = '';
```

Mengatasi EC-20. Baris terakhir menangani kemungkinan `label` yang kosong, yang akan menggagalkan *slugify* pada tahap berikutnya.

#### Tahap M-3 — Normalisasi Nilai `tipe_field` Legacy

Kolom `tipe_field` pada data existing tidak tervalidasi terhadap daftar tipe apa pun, sehingga isinya dapat berupa nilai bebas (`"text"`, `"Text"`, `"string"`, `"input"`, dan sebagainya). Pemetaan yang ditetapkan:

| Nilai legacy (tidak peka huruf) | Dipetakan menjadi |
|---|---|
| `text`, `string`, `input`, `char` | `TEXT` |
| `textarea`, `longtext`, `multiline` | `TEXTAREA` |
| `number`, `numeric`, `int`, `integer` | `NUMBER` |
| `email`, `mail` | `EMAIL` |
| `select`, `dropdown`, `combobox` | `SELECT` |
| `radio`, `option` | `RADIO` |
| `checkbox`, `check`, `multiselect` | `CHECKBOX` |
| `date`, `tanggal`, `datepicker` | `DATE` |
| `file`, `upload`, `image`, `foto` | `FILE` |
| nilai lain / tidak dikenal | `TEXT` (dengan log `WARN` memuat `id` baris) |

Nilai tak dikenal dipetakan menjadi `TEXT` alih-alih menggagalkan migrasi. Alasan: `TEXT` adalah tipe paling permisif sehingga tidak akan menolak data yang sudah pernah terkumpul, dan Admin dapat memperbaikinya secara manual di kemudian hari. Menggagalkan *startup* aplikasi karena satu baris konfigurasi yang aneh akan jauh lebih merugikan.

#### Tahap M-4 — *Backfill* `field_key` dan `field_category` Baris Legacy

Ini adalah tahap paling rawan, karena baris legacy tidak memiliki identitas dan hanya dapat dipetakan berdasarkan teks `label`. Algoritma yang ditetapkan, dijalankan per produk:

```
Untuk setiap produk P yang memiliki baris produk_form_config dengan field_key IS NULL:
  1. Ambil seluruh baris legacy milik P, urutkan menurut (orders NULLS LAST, id)
  2. Untuk setiap baris L:
       a. Normalkan label L: huruf kecil, hilangkan tanda baca, pangkas spasi
       b. Cocokkan terhadap tabel heuristik di bawah
       c. Bila cocok DAN field_key hasil pencocokan BELUM dipakai pada produk P:
            → set field_key = hasil pencocokan, field_category = 'SYSTEM',
              is_mandatory = true, is_active = true
       d. Bila tidak cocok, ATAU field_key hasil pencocokan SUDAH dipakai:
            → set field_category = 'CUSTOM',
              field_key = slugify(label) dengan penyelesaian tabrakan (§11.5)
  3. Panggil seedMissingSystemFields(P)  → lengkapi System Field yang belum ada
  4. Normalkan orders menjadi 1..N
```

Tabel heuristik pencocokan label ke System Field:

| Pola label (dinormalkan, pencocokan sebagian) | `field_key` |
|---|---|
| `nama`, `nama lengkap`, `nama penerima`, `nama customer`, `nama konsumen`, `nama pemesan`, `full name`, `name` | `customer_name` |
| `whatsapp`, `no wa`, `nomor wa`, `nomor whatsapp`, `no hp`, `nomor hp`, `telepon`, `handphone`, `phone` | `phone_number` |
| `alamat`, `alamat lengkap`, `alamat pengiriman`, `address` | `address` |
| `provinsi`, `province` | `province` |
| `kota`, `kabupaten`, `kota kabupaten`, `city` | `city` |
| `kecamatan`, `district`, `kec` | `district` |

Urutan pemeriksaan penting: `phone_number` diperiksa **sebelum** `customer_name`, karena label seperti "Nama dan Nomor WhatsApp" (bila ada) lebih tepat diperlakukan sebagai nomor. Demikian pula `city` diperiksa sebelum `district` untuk menghindari kesalahan pada label "Kota/Kecamatan".

Klausa "BELUM dipakai pada produk P" pada langkah 2c mencegah dua baris legacy dipetakan ke `field_key` System Field yang sama — misalnya bila sebuah produk memiliki dua baris berlabel "Nama" dan "Nama Lengkap". Baris kedua akan jatuh ke cabang 2d dan menjadi Custom Field bernama `nama_lengkap`. Ini adalah perilaku yang tepat: tidak ada data yang hilang, dan Admin dapat merapikannya kemudian (EC-19).

#### Tahap M-5 — Seeding System Field untuk Produk Tanpa Konfigurasi

```sql
-- Produk aktif yang tidak memiliki baris produk_form_config sama sekali
INSERT INTO produk_form_config
    (id_produk, field_key, field_category, tipe_field, label, placeholder,
     is_mandatory, is_active, orders, created_at, updated_at)
SELECT p.id, sf.field_key, 'SYSTEM', sf.tipe_field, sf.label, sf.placeholder,
       true, true, sf.sort_order, now(), now()
FROM produk p
CROSS JOIN (VALUES
    ('customer_name', 'TEXT',     'Nama',           'Masukkan nama lengkap',    1),
    ('phone_number',  'PHONE',    'Nomor WhatsApp', 'Contoh: 08123456789',      2),
    ('address',       'TEXTAREA', 'Alamat',         'Masukkan alamat lengkap',  3),
    ('province',      'PROVINCE', 'Provinsi',       'Pilih provinsi',           4),
    ('city',          'CITY',     'Kota',           'Pilih kota',               5),
    ('district',      'DISTRICT', 'Kecamatan',      'Pilih kecamatan',          6)
) AS sf(field_key, tipe_field, label, placeholder, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM produk_form_config pfc
    WHERE pfc.id_produk = p.id AND lower(pfc.field_key) = sf.field_key
);
```

Perlu dicatat bahwa kueri ini mencakup **seluruh** produk, termasuk yang ber-`is_deleted = true`. Menyertakan produk terhapus disengaja: produk dapat dipulihkan, dan biaya enam baris tambahan per produk terhapus jauh lebih murah daripada risiko produk yang dipulihkan berada dalam keadaan konfigurasi tidak lengkap.

#### Tahap M-6 — Pembuatan Index dan Constraint

Dijalankan **setelah** M-4 dan M-5 selesai, karena `uq_pfc_produk_field_key` akan gagal apabila masih ada `field_key` bernilai `NULL` atau ganda.

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uq_pfc_produk_field_key
  ON produk_form_config (id_produk, lower(field_key));
CREATE INDEX IF NOT EXISTS idx_pfc_produk_active_sort
  ON produk_form_config (id_produk, is_active, orders);
CREATE INDEX IF NOT EXISTS idx_pfc_category
  ON produk_form_config (field_category);

-- CHECK constraint ditambahkan hanya bila belum ada
ALTER TABLE produk_form_config
  ADD CONSTRAINT ck_pfc_category CHECK (field_category IN ('SYSTEM','CUSTOM'));
ALTER TABLE produk_form_config
  ADD CONSTRAINT ck_pfc_system_locked
  CHECK (field_category <> 'SYSTEM' OR (is_mandatory = true AND is_active = true));
```

Karena PostgreSQL tidak mendukung `ADD CONSTRAINT IF NOT EXISTS`, *initializer* wajib memeriksa `pg_constraint` terlebih dahulu, atau menangkap galat *duplicate object* dan mengabaikannya.

**Titik kegagalan yang harus diantisipasi:** apabila `CREATE UNIQUE INDEX` gagal karena masih ada duplikat, *initializer* wajib mencatat `ERROR` beserta daftar `(id_produk, field_key)` yang bertabrakan, **namun tidak menggagalkan *startup* aplikasi**. Aplikasi tetap berfungsi tanpa *unique index* (keunikan masih ditegakkan pada lapisan aplikasi); menggagalkan *startup* akan menyebabkan gangguan layanan penuh demi masalah integritas yang bersifat lokal.

#### Tahap M-7 — Verifikasi Pasca-Migrasi

```sql
-- 1. Tidak boleh ada baris tanpa field_key
SELECT count(*) FROM produk_form_config WHERE field_key IS NULL;                        -- harus 0

-- 2. Setiap produk wajib memiliki tepat 6 System Field
SELECT p.id, p.nama_produk, count(pfc.id) AS jumlah_system_field
FROM produk p
LEFT JOIN produk_form_config pfc
       ON pfc.id_produk = p.id AND pfc.field_category = 'SYSTEM'
GROUP BY p.id, p.nama_produk
HAVING count(pfc.id) <> 6;                                                             -- harus kosong

-- 3. Tidak boleh ada System Field yang tidak wajib atau nonaktif
SELECT count(*) FROM produk_form_config
WHERE field_category = 'SYSTEM' AND (is_mandatory <> true OR is_active <> true);        -- harus 0

-- 4. Tidak boleh ada field_key ganda per produk
SELECT id_produk, lower(field_key), count(*)
FROM produk_form_config GROUP BY 1, 2 HAVING count(*) > 1;                              -- harus kosong

-- 5. Tidak boleh ada tipe_field di luar daftar yang dikenal
SELECT DISTINCT tipe_field FROM produk_form_config
WHERE tipe_field NOT IN ('TEXT','TEXTAREA','NUMBER','EMAIL','SELECT','RADIO',
                         'CHECKBOX','DATE','FILE','PROVINCE','CITY','DISTRICT','PHONE'); -- harus kosong

-- 6. Tidak boleh ada nilai NULL pada kolom yang wajib terisi
SELECT count(*) FROM produk_form_config
WHERE orders IS NULL OR is_mandatory IS NULL OR is_active IS NULL OR label IS NULL;      -- harus 0

-- 7. Distribusi hasil backfill (untuk peninjauan manual)
SELECT field_category, count(*) FROM produk_form_config GROUP BY 1;
SELECT field_key, count(*) FROM produk_form_config
WHERE field_category = 'CUSTOM' GROUP BY 1 ORDER BY 2 DESC LIMIT 30;
```

Kueri nomor 7 wajib **ditinjau manual** oleh Product Manager bersama System Analyst. Bila muncul `field_key` Custom Field yang secara jelas seharusnya merupakan System Field (misalnya `nama_penerima_paket` dengan jumlah tinggi), berarti tabel heuristik M-4 perlu diperluas dan *backfill* dijalankan ulang pada lingkungan uji sebelum diterapkan ke produksi.

### 22.4 Rencana *Rollback*

| Tahap | Dapat di-*rollback*? | Cara |
|---|---|---|
| M-1 (penambahan kolom) | Tidak perlu | Kolom baru bersifat aditif; kode lama mengabaikannya |
| M-2, M-3 (normalisasi) | Tidak dapat dipulihkan | Nilai `NULL` yang dinormalkan tidak dapat dikembalikan. **Mitigasi wajib: cadangkan tabel `produk_form_config` sebelum migrasi** (`CREATE TABLE produk_form_config_backup_20260728 AS SELECT * FROM produk_form_config`) |
| M-4 (*backfill*) | Ya, melalui tabel cadangan | Pulihkan dari tabel cadangan |
| M-5 (seeding) | Ya | `DELETE FROM produk_form_config WHERE field_category = 'SYSTEM' AND created_at >= '<waktu migrasi>'` |
| M-6 (index/constraint) | Ya | `DROP INDEX` / `DROP CONSTRAINT` |
| Tabel `order_custom_field` | Ya (sebelum ada data) | `DROP TABLE`. Setelah ada data order, **tidak boleh** di-*drop* |
| Kode aplikasi | Ya | *Rollback* versi aplikasi. Perlu dicatat: aplikasi versi lama tetap berfungsi dengan skema baru karena seluruh perubahan bersifat aditif |

Kemampuan aplikasi versi lama untuk berjalan di atas skema baru merupakan properti penting yang perlu diverifikasi sebelum rilis: ia memungkinkan *rollback* aplikasi tanpa *rollback* basis data, yang merupakan jalur pemulihan tercepat pada kejadian tak terduga.

### 22.5 Rencana Rilis Bertahap

| Fase | Cakupan | Prasyarat | Kriteria keluar |
|---|---|---|---|
| **F-0** | Cadangan basis data; jalankan migrasi M-1 sampai M-7 pada lingkungan *staging* memakai salinan data produksi | — | Seluruh kueri verifikasi M-7 lolos; hasil kueri distribusi ditinjau dan disetujui |
| **F-1** | Rilis backend: entity, repository, service, `GET`/`PUT form-config`, seeding, *self-healing*. Endpoint `POST /order/create` **belum** menerima `customFields` | F-0 selesai | Uji regresi RT-1 sampai RT-15 lolos; tidak ada perubahan perilaku yang terpantau |
| **F-2** | Rilis frontend dashboard: tab Konfigurasi Form (System Field saja — pengubahan label, placeholder, help text, urutan). Custom Field belum ditampilkan | F-1 di produksi selama minimal 3 hari tanpa insiden | Admin dapat mengubah label System Field; halaman checkout menampilkannya dengan benar |
| **F-3** | Rilis *renderer* dinamis pada halaman checkout, di balik *feature flag* per workspace | F-2 selesai | Paritas visual dan fungsional untuk produk tanpa Custom Field (RT-15); *conversion rate* stabil pada workspace percontohan |
| **F-4** | Rilis Custom Field untuk delapan tipe (seluruhnya kecuali `FILE`): backend validasi + penyimpanan, UI editor, *render* checkout, tampilan Detail Order | F-3 diaktifkan pada minimal 20% workspace tanpa insiden | AC-6 sampai AC-21 lolos |
| **F-5** | Rilis tipe `FILE`: endpoint unggah publik, pembatasan laju, daftar putih MIME, pembersihan objek yatim | F-4 selesai; tinjauan keamanan §23 disetujui | AC untuk `FILE` lolos; uji penetrasi terhadap endpoint unggah lolos |
| **F-6** | Penghapusan alias kompatibilitas (`tipeField`, `order`, `isMandatory`) pada respons checkout | Seluruh klien checkout telah bermigrasi (dipantau melalui log versi klien) | Tidak ada klien yang masih membaca atribut alias |

Alasan pemecahan menjadi enam fase: risiko terbesar fitur ini bukan pada Custom Field, melainkan pada **penulisan ulang halaman checkout** (§21.7) dan **migrasi data legacy** (§22.3 tahap M-4). Kedua risiko tersebut diisolasi pada fase tersendiri (F-0/F-3) sehingga dapat diverifikasi dan dikembalikan secara independen dari nilai bisnis utama (F-4).

### 22.6 Kebutuhan Data Uji

| Skenario data uji | Tujuan |
|---|---|
| Produk tanpa baris `produk_form_config` sama sekali | Menguji M-5 |
| Produk dengan 6 baris legacy berlabel Indonesia baku | Menguji M-4 jalur sukses penuh |
| Produk dengan baris legacy berlabel tidak baku ("Nama Penerima Paket", "No. WA Aktif") | Menguji heuristik M-4 |
| Produk dengan dua baris legacy berlabel serupa ("Nama", "Nama Lengkap") | Menguji cabang 2d pada M-4 |
| Produk dengan baris legacy ber-`tipe_field` tidak dikenal | Menguji M-3 |
| Produk dengan baris legacy ber-`orders` dan `is_mandatory` bernilai `NULL` | Menguji M-2 (EC-20) |
| Produk ber-`is_deleted = true` | Menguji cakupan M-5 |
| Produk dengan lebih dari 100 baris legacy | Menguji performa dan batas |

---

## 23. Security Consideration

### 23.1 Permukaan Serangan Baru

Fitur ini memperluas permukaan serangan pada tiga titik, dua di antaranya berada pada **endpoint publik tanpa autentikasi**:

| Titik | Sifat | Tingkat risiko |
|---|---|---|
| `GET /produk/checkout` | Publik; kini mengembalikan lebih banyak metadata konfigurasi | Rendah |
| `POST /order/create` | Publik; kini menerima data berbentuk bebas (`customFields`) | **Tinggi** |
| `POST /produk/form-config/upload` | Publik; menerima unggahan berkas | **Tinggi** |
| `PUT /produk/{id}/form-config` | Terautentikasi; menerima konfigurasi termasuk regex dan HTML | Sedang |

### 23.2 Isolasi Tenant (Multi-Tenancy)

| Kontrol | Penerapan |
|---|---|
| Validasi kepemilikan workspace pada setiap operasi konfigurasi | `GET`/`PUT form-config` wajib memverifikasi `produk.id_workspace` terhadap workspace pemanggil sebelum operasi apa pun |
| Balasan `404`, bukan `403` | Mencegah pengungkapan keberadaan sumber daya lintas tenant (AC-23) |
| `workspaceId` diambil dari token, bukan dari parameter | Bila `workspaceId` diterima sebagai parameter (mengikuti konvensi codebase), nilainya **wajib** diverifikasi terhadap klaim pada JWT. Tanpa verifikasi ini, pemanggil dapat menyisipkan `workspaceId` milik tenant lain |
| `order_custom_field` tidak dapat diakses lintas tenant | Pembacaan selalu melalui `GET /order/{id}` yang sudah memvalidasi workspace |

Perlu dicatat sebagai temuan: konvensi codebase saat ini menerima `workspaceId` sebagai *query parameter* pada banyak endpoint (`GET /order?workspaceId=...`). Pola ini aman **hanya bila** service memverifikasi bahwa akun pemanggil benar-benar berhak atas workspace tersebut. Implementor wajib memastikan verifikasi tersebut ada pada endpoint baru, dan sebaiknya memverifikasi keberadaannya pada endpoint existing sebagai pekerjaan terpisah.

### 23.3 Injeksi dan Sanitasi

| Ancaman | Vektor | Pengendalian |
|---|---|---|
| **Cross-Site Scripting (XSS) tersimpan** | Admin menyimpan `<script>` pada `label`, `placeholder`, `helpText`, atau `option.label`; teks tersebut lalu dirender pada halaman checkout publik | Sanitasi HTML pada saat **penyimpanan** (hapus seluruh tag, biarkan teks) **dan** *escaping* pada saat *render* di frontend. Dua lapisan diperlukan karena data yang sudah tersimpan sebelum sanitasi diberlakukan tetap harus aman saat dirender |
| **XSS tersimpan melalui nilai order** | Pelanggan mengirim `<img onerror=...>` sebagai nilai Custom Field; teks dirender pada Detail Order di dashboard | Sanitasi pada penyimpanan; *escaping* pada render dashboard. Perlu diperhatikan bahwa target serangan di sini adalah **Agent/Admin**, bukan pelanggan — sehingga dampaknya berupa pengambilalihan sesi dashboard |
| **SQL Injection** | `fieldKey` atau nilai yang disisipkan ke kueri | Seluruh akses data memakai *parameterized query* melalui Spring Data JPA. `fieldKey` **tidak boleh** dipakai untuk menyusun nama kolom secara dinamis maupun disisipkan ke *native query* sebagai literal |
| **Injeksi JSON pada kolom `jsonb`** | `options` dan `validation_rule` dikirim sebagai objek sembarang | Deserialisasi ke DTO bertipe kuat (`List<OptionDto>`, `ValidationRuleDto`), **bukan** ke `Map<String, Object>` mentah. Atribut yang tidak dikenal ditolak atau diabaikan secara eksplisit |
| **Injeksi ke pesan WhatsApp** | Nilai Custom Field disisipkan ke templat pesan | Pada fase ini Custom Field **tidak** disisipkan ke pesan WhatsApp. Bila kelak diterapkan (§24), nilai wajib disanitasi terhadap karakter kendali dan dibatasi panjangnya |
| **Injeksi formula CSV/Excel** | Nilai dimulai dengan `=`, `+`, `-`, atau `@`, lalu diekspor ke Excel | Tidak relevan pada fase ini (OOS-7). Bila Custom Field masuk ke export (§24), nilai wajib diawali petik tunggal (*prefix escaping*) |

### 23.4 Pencegahan *Mass Assignment* dan Eskalasi

| Ancaman | Pengendalian |
|---|---|
| Klien mengirim `fieldKey` System Field pada `customFields` untuk menimpa nilai `order` | Ditolak keras `SYSTEM_FIELD_IN_CUSTOM_PAYLOAD` (BR-19). Ini merupakan pertahanan penting: tanpa aturan ini, jalur ganda penulisan nilai System Field akan terbuka |
| Klien mengirim metadata konfigurasi (`isRequired: false`, `options`, `fieldType`) pada `POST /order/create` untuk melewati validasi | Metadata dari klien **diabaikan seluruhnya**; validasi selalu membaca konfigurasi dari basis data (BR-36). DTO submit hanya memiliki atribut `fieldKey`, `value`, dan `meta` — atribut lain tidak dideserialisasi |
| Klien mengirim `fieldKey` milik produk lain | Validasi selalu dibatasi pada konfigurasi produk yang dirujuk `idProduk`; `fieldKey` asing masuk kategori "tidak dikenal" dan diabaikan (§18.4.1) |
| Admin (bukan Superadmin) mengubah konfigurasi produk workspace lain | Validasi kepemilikan workspace (§23.2) |
| Agent mengubah konfigurasi form | `PUT form-config` dibatasi pada peran SUPERADMIN dan ADMIN; AGENT hanya memiliki akses baca |

### 23.5 Keamanan Unggah Berkas (`FILE`)

Ini merupakan area berisiko tertinggi pada fitur ini, karena melibatkan penerimaan berkas dari pihak tak terautentikasi.

| Kontrol | Spesifikasi |
|---|---|
| Daftar putih MIME | Hanya `image/png`, `image/jpeg`, `image/webp`, dan `application/pdf` yang diizinkan pada tingkat sistem. Admin dapat mempersempit, **tidak dapat memperluas** |
| Verifikasi tipe berkas sebenarnya | Validasi berdasarkan *magic bytes* (angka ajaib pada header berkas), **bukan** berdasarkan `Content-Type` atau ekstensi nama berkas yang keduanya dikendalikan klien |
| Batas ukuran | Maksimum 5 MB per berkas pada tingkat sistem; Admin dapat mempersempit melalui `maxFileSizeKb` |
| Penamaan objek | Nama berkas dibangkitkan sistem (UUID + ekstensi tervalidasi). Nama asli disimpan **hanya** sebagai metadata pada `field_value_json` dan **wajib** di-*escape* saat ditampilkan |
| Pencegahan *path traversal* | Nama berkas asli tidak pernah dipakai untuk menyusun jalur objek |
| Pembatasan laju (*rate limiting*) | Maksimum 10 unggahan per alamat IP per 10 menit. Codebase sudah memiliki `BlockedIpFilter` yang dapat diperluas untuk keperluan ini |
| Verifikasi `idProduk` dan `fieldKey` | Unggahan hanya diterima bila produk tersebut benar-benar memiliki Custom Field aktif bertipe `FILE` dengan `fieldKey` tersebut. Ini mencegah endpoint dipakai sebagai penyimpanan berkas umum |
| Pembersihan objek yatim | Pekerjaan terjadwal menghapus objek yang diunggah namun tidak dirujuk oleh order mana pun setelah 60 menit |
| Validasi URL pada submit | Nilai `FILE` wajib berawalan pada *prefix* penyimpanan sistem **dan** objeknya wajib benar-benar ada. Mencegah penyisipan URL eksternal (`FILE_URL_NOT_ALLOWED`, `FILE_NOT_FOUND`) |
| Penyajian berkas | Berkas disajikan melalui *presigned URL* berjangka waktu (`StorageService.getPresignedUrl()` sudah tersedia), bukan melalui *bucket* yang terbuka publik, sehingga berkas pelanggan tidak dapat dijelajahi pihak lain |
| Tanpa eksekusi | *Bucket* penyimpanan wajib menyajikan berkas dengan `Content-Disposition: attachment` dan `X-Content-Type-Options: nosniff` untuk mencegah eksekusi HTML/SVG yang diunggah |

Perlu dicatat bahwa `image/svg+xml` **sengaja tidak** dimasukkan ke daftar putih, karena SVG dapat memuat JavaScript dan merupakan vektor XSS yang lazim.

### 23.6 Penolakan Layanan (*Denial of Service*)

| Ancaman | Pengendalian |
|---|---|
| Penggelembungan konfigurasi (ribuan field) | Batas 50 Custom Field aktif per produk (BR-20); batas 56 entri per payload; batas 100 options per field |
| Penggelembungan payload submit | Batas ukuran permintaan 256 KB (NFR-16); batas panjang per tipe field (§18.4.2) |
| ReDoS melalui `validation.pattern` | Pemeriksaan kompleksitas regex pada saat penyimpanan konfigurasi (menolak *nested quantifier* dan alternasi berlebihan); *timeout* evaluasi 100 ms pada saat submit; panjang pola maksimum 200 karakter (EC-31) |
| Penggelembungan tabel `order_custom_field` | Batas jumlah field per produk membatasi jumlah baris per order secara implisit; `unique index (id_order, field_key)` mencegah duplikasi akibat *retry* |
| Penyalahgunaan endpoint unggah sebagai penyimpanan gratis | Pembatasan laju + verifikasi `idProduk`/`fieldKey` + pembersihan objek yatim (§23.5) |
| Pembacaan `usageCount` yang mahal | Dilayani oleh indeks `idx_ocf_produk_field`; kegagalan atau kelambatan tidak menggagalkan respons (NFR-5) |

### 23.7 Privasi dan Kepatuhan Data

| Pertimbangan | Penerapan |
|---|---|
| Custom Field dapat memuat data pribadi | Admin dapat membuat field bernama "NIK", "Nomor Rekening", atau "Tanggal Lahir". Sistem tidak dapat mencegahnya secara teknis, namun **wajib** menampilkan peringatan pada UI editor field: pengumpulan data pribadi sensitif menjadi tanggung jawab pemilik workspace |
| Tidak ada pencatatan nilai secara utuh pada log | Log validasi mencatat `idProduk` dan `fieldKey`, **tidak** mencatat nilai secara utuh (NFR-12). Bila nilai perlu dicatat untuk penelusuran, wajib dipotong maksimum 32 karakter |
| Data pada `order_custom_field` bersifat permanen | Tidak ada mekanisme penghapusan berdasarkan permintaan subjek data pada fase ini. Bila kelak diperlukan (mis. untuk kepatuhan penghapusan data), diperlukan endpoint administratif tersendiri (§24) |
| Berkas unggahan dapat memuat dokumen pribadi | *Presigned URL* berjangka waktu; tidak ada *bucket* publik (§23.5) |

### 23.8 Perbaikan Praktik Penanganan Galat

Sebagai temuan yang perlu dicatat: `ProdukController` dan `OrderController` pada codebase saat ini mengembalikan `e.getMessage()` secara langsung pada blok `catch`, disertai `e.printStackTrace()`. Pada endpoint publik, praktik ini dapat membocorkan nama tabel, nama kolom, dan detail pengecualian basis data kepada pemanggil anonim.

Ketentuan bagi endpoint yang disentuh fitur ini:

| Ketentuan |
|---|
| Pesan galat pada endpoint publik wajib bersifat generik dan tidak memuat detail internal |
| Detail teknis dicatat pada log server (memakai *logger*, bukan `printStackTrace()`) beserta pengenal korelasi (*correlation id*) |
| Respons galat menyertakan pengenal korelasi sehingga pelanggan dapat menyebutkannya saat menghubungi dukungan |
| Pengecualian validasi dibedakan dari pengecualian sistem: yang pertama menghasilkan `400` beserta pesan informatif, yang kedua menghasilkan `500` beserta pesan generik |

### 23.9 Daftar Periksa Keamanan Sebelum Rilis

| # | Butir | Fase |
|---|---|---|
| SC-1 | Validasi kepemilikan workspace terpasang pada `GET` dan `PUT form-config` | F-1 |
| SC-2 | `PUT form-config` dibatasi pada peran SUPERADMIN dan ADMIN | F-1 |
| SC-3 | Sanitasi HTML pada `label`, `placeholder`, `helpText`, `option.label` | F-1 |
| SC-4 | Deserialisasi `options` dan `validation_rule` ke DTO bertipe kuat | F-1 |
| SC-5 | Pemeriksaan kompleksitas regex pada `validation.pattern` | F-4 |
| SC-6 | `SYSTEM_FIELD_IN_CUSTOM_PAYLOAD` ditolak keras | F-4 |
| SC-7 | Metadata konfigurasi dari klien diabaikan pada submit order | F-4 |
| SC-8 | Batas ukuran permintaan 256 KB terpasang | F-4 |
| SC-9 | *Escaping* nilai Custom Field pada render Detail Order | F-4 |
| SC-10 | Daftar putih MIME + verifikasi *magic bytes* | F-5 |
| SC-11 | Pembatasan laju endpoint unggah | F-5 |
| SC-12 | Verifikasi `idProduk`/`fieldKey` pada endpoint unggah | F-5 |
| SC-13 | Pembersihan objek yatim berjalan terjadwal | F-5 |
| SC-14 | Validasi *prefix* URL dan keberadaan objek pada submit | F-5 |
| SC-15 | *Presigned URL* dipakai untuk penyajian berkas | F-5 |
| SC-16 | Uji penetrasi terhadap endpoint unggah publik | F-5 |

---

## 24. Future Enhancement

Diurutkan menurut estimasi nilai bisnis dibandingkan biaya implementasi.

| ID | Peningkatan | Nilai bisnis | Estimasi biaya | Catatan |
|---|---|---|---|---|
| FE-1 | **Custom Field pada Export Excel** (menutup OOS-7) | Tinggi — data yang terkumpul menjadi dapat diolah di luar sistem | Sedang | Menuntut desain ulang generator Excel dari *interface projection* berkolom tetap menjadi kolom dinamis. Pendekatan yang disarankan: pivot per produk, karena satu berkas export lintas produk akan memiliki kolom yang tidak seragam. Kemungkinan berupa fitur "Export per Produk" tersendiri |
| FE-2 | **Templat form lintas produk** (*form template library*) (menutup OOS-5) | Tinggi — Admin dengan puluhan produk sejenis tidak perlu menyusun ulang | Sedang | Tabel `workspace_form_template` + aksi "Terapkan Templat". Perlu keputusan: templat bersifat salinan (*copy*) atau tautan (*link*). Salinan lebih sederhana dan lebih aman |
| FE-3 | **Custom Field pada abandoned order** (menutup OOS-1) | Sedang — memperkaya data pemulihan keranjang | Rendah | Tabel `abandoned_order_custom_field` dengan struktur serupa, atau satu kolom `jsonb` (karena abandoned order bersifat sementara sehingga kebutuhan kueri lebih rendah) |
| FE-4 | **Filter dan pencarian order berdasarkan Custom Field** | Sedang — Admin dapat mencari "seluruh pesanan ukuran XL" | Sedang | Menuntut indeks pada `(id_produk, field_key, field_value)`; perlu pembatasan agar tidak menghasilkan pemindaian tabel penuh |
| FE-5 | **Custom Field sebagai variabel templat pesan WhatsApp** | Sedang — pesan konfirmasi dapat menyebut ukuran atau warna pesanan | Rendah | Perluasan `MessageTemplateService` dengan variabel `{{custom.ukuran_baju}}`. Wajib menyertakan sanitasi (§23.3) |
| FE-6 | **Custom Field sebagai konteks bot AI / RAG** (menutup OOS-8) | Sedang — bot dapat menjawab pertanyaan mengenai detail pesanan | Sedang | Menyertakan nilai Custom Field ke konteks `user_tendencies` pada Qdrant |
| FE-7 | ***Conditional logic* antar field** (menutup OOS-2) | Sedang — form lebih ringkas dan relevan | Tinggi | Menuntut mesin aturan dan evaluator ekspresi yang konsisten di server dan klien. Sebaiknya dimulai dari bentuk paling sederhana: "tampilkan field B bila field A bernilai X" |
| FE-8 | **Versioning konfigurasi form** (menutup OOS-9) | Rendah–Sedang | Sedang | Tabel `produk_form_config_history`. Perlu dicatat bahwa kebutuhan integritas historis sudah terpenuhi oleh mekanisme *snapshot*; versioning hanya menambah nilai audit ("siapa mengubah apa dan kapan") |
| FE-9 | ***Optimistic locking* pada konfigurasi** (menutup EC-3) | Rendah | Rendah | Kolom `version` + `@Version`, atau `If-Unmodified-Since`. Layak dikerjakan bila muncul keluhan mengenai perubahan yang saling menimpa |
| FE-10 | **Pengelompokan field ke dalam seksi** (menutup OOS-3) | Rendah–Sedang | Sedang | Kolom `section_name` + pengelompokan pada *render*. Menjadi relevan bila jumlah field per produk umumnya tinggi |
| FE-11 | **Perhitungan harga berbasis Custom Field** (menutup OOS-4) | Tinggi — memungkinkan biaya tambahan (bordir, ukuran khusus) | Tinggi | Bersinggungan dengan `AtributProduk`, `ProdukEkstra`, dan `ProdukPembayaran`. Wajib dirancang sebagai fitur harga tersendiri, bukan sebagai perluasan konfigurasi form |
| FE-12 | **Internasionalisasi label** (menutup OOS-10) | Rendah | Sedang | Tabel `produk_form_config_i18n` atau kolom `label_i18n jsonb`. Relevan bila platform melayani pasar lintas bahasa |
| FE-13 | **Analitik per field** (tingkat pengabaian, waktu pengisian) | Sedang — membantu Admin mengoptimalkan form | Sedang | Menuntut instrumentasi pada frontend checkout dan penyimpanan peristiwa |
| FE-14 | **Endpoint administratif penghapusan data pribadi** | Rendah (naik bila ada kewajiban kepatuhan) | Rendah | Penghapusan atau penyamaran (*anonymization*) nilai `order_custom_field` berdasarkan permintaan subjek data |
| FE-15 | **Partisi tabel `order_custom_field`** | Rendah (naik seiring volume) | Sedang | Partisi berdasarkan rentang `created_at` ketika volume melampaui puluhan juta baris (§11.6) |
| FE-16 | **Penyelarasan nama kolom fisik** (`orders` → `sort_order`, dan sebagainya) | Rendah (murni kebersihan kode) | Rendah | Menuntut skrip migrasi manual di luar `ddl-auto`, dan sebaiknya digabungkan dengan pengenalan Flyway/Liquibase (§25 R-8) |

---

## 25. Risiko Implementasi dan Mitigasi

| ID | Risiko | Kemungkinan | Dampak | Mitigasi |
|---|---|---|---|---|
| R-1 | **Kehilangan konfigurasi akibat pola *delete-and-reinsert* existing** — bila FR-36 tidak diimplementasikan dengan benar, penyimpanan produk dari tab lain akan menghapus seluruh konfigurasi form | Sedang | **Kritis** | Hapus pemanggilan `deleteProdukFormConfigByIdProduk()` dari `saveProduct()`; perlakukan `formConfig` bernilai `null` maupun `[]` sebagai "tidak ada perubahan" (§21.2); uji regresi RT-11 bersifat wajib lolos |
| R-2 | ***Backfill* memetakan field legacy secara keliru** — label tidak baku menyebabkan System Field terbaca sebagai Custom Field atau sebaliknya | **Tinggi** | Tinggi | Jalankan M-4 pada salinan data produksi lebih dahulu; tinjau manual hasil kueri distribusi (§22.3 M-7 nomor 7); perluas tabel heuristik berdasarkan temuan; *self-healing* (FR-4) menjamin System Field yang hilang tetap terbentuk; cadangkan tabel sebelum migrasi |
| R-3 | **Regresi pada halaman checkout akibat penulisan ulang *renderer*** | Sedang | **Kritis** — berdampak langsung pada pendapatan | *Feature flag* per workspace (F-3); prioritas paritas visual; pemantauan *conversion rate*; mekanisme kembali per workspace (§21.7) |
| R-4 | **Endpoint unggah publik disalahgunakan** | Sedang | Tinggi | Rangkaian kontrol §23.5; rilis pada fase terakhir (F-5) setelah tinjauan keamanan; uji penetrasi (SC-16) |
| R-5 | **XSS tersimpan melalui label yang disimpan Admin, dirender pada halaman checkout publik** | Sedang | Tinggi | Sanitasi pada penyimpanan **dan** *escaping* pada render (dua lapisan, §23.3) |
| R-6 | **Validasi dinamis mengandung celah** — misalnya `0` atau `false` dianggap kosong | **Tinggi** | Sedang | Tabel perlakuan nilai kosong yang eksplisit (§18.4.3); uji unit wajib mencakup nilai `0`, `false`, `""`, `null`, dan `[]` untuk setiap tipe field |
| R-7 | **`CREATE UNIQUE INDEX` gagal saat migrasi karena masih ada duplikat** | Sedang | Sedang | *Initializer* mencatat `ERROR` beserta daftar duplikat namun **tidak** menggagalkan *startup*; keunikan tetap ditegakkan pada lapisan aplikasi (§22.3 M-6) |
| R-8 | **Ketiadaan alat migrasi terkelola** — seluruh migrasi bergantung pada *initializer* buatan sendiri sehingga sulit diaudit dan diulang secara terkontrol | **Tinggi** | Sedang | Untuk fitur ini, pola *initializer* dipertahankan demi konsistensi. Rekomendasi terpisah: perkenalkan Flyway pada siklus berikutnya (FE-16) |
| R-9 | **Performa `GET /produk/checkout` menurun** akibat ukuran respons yang bertambah | Rendah | Sedang | Indeks `idx_pfc_produk_active_sort`; pemantauan p95 (NFR-1); pertimbangkan *cache* respons checkout per `urlCheckout` bila diperlukan |
| R-10 | **Frontend menanamkan aturan bisnis secara *hardcoded*** (misalnya menentukan sendiri atribut mana yang terkunci) sehingga aturan terduplikasi di dua tempat | Sedang | Sedang | Server menyampaikan `editableAttributes` dan `deletable` secara eksplisit (§12.3); tinjauan kode frontend wajib memeriksa tidak adanya pemeriksaan `fieldCategory === 'SYSTEM'` |
| R-11 | **Ketergantungan tipe `FILE` pada pekerjaan yang belum selesai** — endpoint unggah existing masih mengembalikan URL statis | **Tinggi** | Sedang | Tipe `FILE` dipisahkan ke fase terakhir (F-5); delapan tipe lainnya tidak terpengaruh dan dapat dirilis lebih dahulu (§21.6) |
| R-12 | **Admin membuat form yang terlalu panjang** sehingga menurunkan konversi | Sedang | Sedang | Batas 50 field; peringatan pada UI ketika jumlah field aktif melampaui 12; analitik per field sebagai pekerjaan lanjutan (FE-13) |
| R-13 | **Konfigurasi berubah saat pelanggan mengisi form** menyebabkan submit gagal | Sedang | Rendah | Mode *lenient* untuk field yang tidak dikenal (§18.4.1); pesan galat yang informatif; muat ulang konfigurasi secara otomatis pada galat `VALUE_NOT_IN_OPTIONS` |
| R-14 | **Cakupan pekerjaan frontend diperkirakan terlalu rendah** — *renderer* dinamis dengan 13 tipe field beserta *cascading* lokasi merupakan komponen yang kompleks | Sedang | Sedang | Pecah menjadi fase (F-2 System Field saja, F-3 *renderer*, F-4 Custom Field); perkirakan berdasarkan jumlah tipe, bukan berdasarkan jumlah halaman |
| R-15 | **Nomor urut order terbuang** akibat validasi dijalankan setelah `createOrderInternal()` | Sedang | Rendah | Urutan operasi yang ditetapkan pada §21.5: validasi mendahului penulisan apa pun |

---

## 26. Keputusan Desain (Design Rationale Log)

Bagian ini mencatat keputusan yang memiliki alternatif nyata, beserta alasan pemilihannya. Rujukan `D-n` dipakai di sepanjang dokumen.

### D-1 — Satu tabel konfigurasi dengan diskriminator kategori

| Aspek | Uraian |
|---|---|
| **Keputusan** | System Field dan Custom Field disimpan pada satu tabel `produk_form_config`, dibedakan oleh `field_category` |
| **Alternatif** | Dua tabel terpisah untuk masing-masing kategori |
| **Alasan** | Kebutuhan eksplisit menyatakan frontend hanya membaca satu konfigurasi. Dua tabel memaksa penggabungan di sisi klien, menghasilkan dua ruang `sort_order` yang tidak dapat disatukan tanpa logika tambahan, dan menduplikasi lima belas kolom identik |
| **Konsekuensi yang diterima** | Beberapa kolom (`options`, `default_value`) selalu `NULL` untuk kategori SYSTEM |

### D-2 — Nilai System Field tetap pada kolom bertipe kuat di tabel `order`

| Aspek | Uraian |
|---|---|
| **Keputusan** | Keenam System Field tetap ditulis ke `order.nama_penerima` dan seterusnya |
| **Alternatif** | Menyimpan seluruh nilai (System dan Custom) secara seragam pada `order_custom_field` |
| **Alasan** | Tujuh proses bisnis bergantung pada kolom tersebut melalui *native query*, *interface projection*, *join* master lokasi, dan agregasi laporan (§1.2). Penyeragaman akan menuntut penulisan ulang seluruhnya dengan risiko regresi tinggi dan tanpa manfaat bisnis |
| **Konsekuensi yang diterima** | Terdapat dua mekanisme penyimpanan nilai; diperlukan tabel pemetaan `field_key` → kolom (§11.4) |

### D-3 — Tabel EAV untuk nilai Custom Field, bukan kolom `jsonb` pada `order`

| Aspek | Uraian |
|---|---|
| **Keputusan** | Nilai Custom Field disimpan pada tabel `order_custom_field`, satu baris per field |
| **Alternatif** | Satu kolom `order.custom_fields jsonb` |
| **Alasan** | (1) *Snapshot* label per field lebih alami sebagai kolom, bukan sebagai atribut berulang di dalam JSON; (2) kueri `usageCount` menjadi agregasi indeks sederhana alih-alih pemindaian JSON; (3) FE-4 (filter berdasarkan Custom Field) menjadi jauh lebih murah; (4) `unique index (id_order, field_key)` memberikan idempotensi yang tidak dapat diperoleh dari kolom JSON |
| **Konsekuensi yang diterima** | Volume baris tinggi (§11.6); memerlukan *batch fetch* untuk menghindari N+1 |

### D-4 — `is_required` System Field terkunci bernilai `true`

| Aspek | Uraian |
|---|---|
| **Keputusan** | Keenam System Field selalu wajib; atribut ini tidak dapat dikonfigurasi |
| **Alternatif** | Mengizinkan Admin menjadikan System Field opsional, sebagaimana dapat dibaca dari kebutuhan "Required berlaku baik untuk System maupun Custom Field" |
| **Alasan** | Ongkir memerlukan `district`; WhatsApp memerlukan `phone_number`; pengiriman memerlukan `address`; `province` dan `city` diperlukan bagi konsistensi hierarki lokasi dan pelaporan. Order tanpa nilai-nilai tersebut **tidak dapat diproses** — sistem akan menghasilkan pesanan yang mustahil dipenuhi |
| **Interpretasi kebutuhan** | Atribut `is_required` memang hadir pada kedua kategori (sebagaimana diminta) dan disampaikan pada API; untuk kategori SYSTEM nilainya terkunci dan bersifat baca-saja |
| **Catatan untuk peninjau** | Ini adalah satu-satunya titik di mana dokumen ini mempersempit kebutuhan yang disampaikan. Bila Product Owner menghendaki sebagian System Field dapat dijadikan opsional, keputusan tersebut menuntut pembahasan tersendiri mengenai bagaimana ongkir dihitung dan bagaimana pesanan dikirim tanpa data tersebut |

### D-5 — `options` sebagai kolom `jsonb`, bukan tabel tersendiri

| Aspek | Uraian |
|---|---|
| **Keputusan** | Daftar pilihan disimpan pada kolom `options jsonb` |
| **Alternatif** | Tabel `produk_form_config_option` dengan FK ke konfigurasi |
| **Alasan** | (1) `options` selalu dibaca dan ditulis secara utuh bersama field induknya — tidak pernah dikueri secara mandiri; (2) codebase sudah memakai `@JdbcTypeCode(SqlTypes.JSON)` pada `Order.configPembayaran` dan `ProdukPembayaran.config` sehingga polanya sudah dikenal; (3) tabel tersendiri menambah satu *join* pada jalur terpanas (`GET /produk/checkout`); (4) urutan pilihan terjaga secara alami oleh larik JSON tanpa kolom urutan tambahan |
| **Konsekuensi yang diterima** | Tidak dapat memberlakukan *unique constraint* keunikan `option.value` pada level basis data — ditegakkan pada lapisan aplikasi (BR-29) |

### D-6 — Batas 50 Custom Field aktif per produk

| Aspek | Uraian |
|---|---|
| **Keputusan** | Batas teknis 50 field aktif |
| **Alternatif** | Tanpa batas, sesuai kebutuhan "tanpa batas sesuai kebutuhan produk" |
| **Alasan** | Endpoint checkout bersifat publik. Tanpa batas, satu produk dengan seribu field akan menghasilkan respons checkout berukuran ratusan kilobyte pada setiap kunjungan, membebani *render* di sisi klien, dan membuka jalur penolakan layanan. Angka 50 berada jauh di atas kebutuhan nyata (form checkout dengan lebih dari 12 field sudah menurunkan konversi secara signifikan) |
| **Konsekuensi yang diterima** | Batas bersifat sewenang-wenang dan mungkin perlu ditinjau. Nilai ini sebaiknya dijadikan konfigurasi tingkat sistem agar dapat disesuaikan tanpa rilis |

### D-7 — Validasi *required* dilewati untuk jalur chat dan abandoned

| Aspek | Uraian |
|---|---|
| **Keputusan** | `source` bernilai `CST_CHAT`, `ADM_ABANDONED`, atau varian `abandoned` tidak dikenai validasi *required* Custom Field |
| **Alternatif** | Memberlakukan validasi seragam pada seluruh jalur |
| **Alasan** | Kedua jalur tersebut merupakan entri oleh agen atau sistem, bukan pengisian form oleh pelanggan. Agen yang membuat pesanan dari percakapan tidak memiliki antarmuka untuk mengisi Custom Field pada fase ini; memaksakan *required* akan memblokir operasional secara total |
| **Konsekuensi yang diterima** | Data Custom Field tidak lengkap pada order yang dibuat melalui jalur agen. Ditutup oleh pekerjaan lanjutan: memperluas `ChatAddOrderRequest` dan UI pembuatan order pada layar chat |

### D-8 — Mode *lenient* untuk `fieldKey` yang tidak dikenal

| Aspek | Uraian |
|---|---|
| **Keputusan** | `fieldKey` yang tidak dikenal atau nonaktif diabaikan secara senyap beserta log `WARN` |
| **Alternatif** | Menolak permintaan dengan `400` |
| **Alasan** | Halaman checkout dapat berada dalam keadaan *cache* lama. Menolak pesanan karena Admin baru saja menonaktifkan sebuah field berarti kehilangan penjualan akibat alasan yang sepenuhnya administratif. Mengabaikan tetap aman karena field yang tidak dikenal tidak akan pernah tersimpan |
| **Pengecualian** | `fieldKey` berkategori SYSTEM di dalam `customFields` tetap ditolak keras, karena merupakan indikasi penyalahgunaan (BR-19) |

### D-9 — Nama kolom fisik existing dipertahankan

| Aspek | Uraian |
|---|---|
| **Keputusan** | `orders`, `is_mandatory`, dan `tipe_field` tidak diganti nama; pemetaan dilakukan pada lapisan entity |
| **Alternatif** | Menambahkan kolom baru bernama benar lalu melakukan *backfill* |
| **Alasan** | `ddl-auto=update` tidak dapat mengganti nama kolom. Menambahkan kolom baru menghasilkan dua kolom untuk satu makna, mengharuskan *backfill*, dan meninggalkan kolom mati berisi data lama — sumber kebingungan permanen. Pemetaan pada entity membuat seluruh kode aplikasi dan kontrak API tetap memakai penamaan yang bersih |
| **Konsekuensi yang diterima** | Nama kolom fisik tidak selaras dengan atribut Java. Wajib didokumentasikan (§11.3) agar tidak menyesatkan pembaca skema |

### D-10 — Endpoint konfigurasi terpisah dari endpoint simpan produk

| Aspek | Uraian |
|---|---|
| **Keputusan** | Konfigurasi form dikelola melalui `GET`/`PUT /produk/{id}/form-config`, terpisah dari `POST /produk` |
| **Alternatif** | Tetap membawa `formConfig` secara inline pada payload produk, sebagaimana implementasi saat ini |
| **Alasan** | (1) Menghilangkan kelas galat "konfigurasi hilang karena tab lain menyimpan" (R-1); (2) memungkinkan validasi khusus konfigurasi tanpa membebani validasi produk; (3) memungkinkan `usageCount` dan `editableAttributes` disampaikan tanpa memperbesar payload detail produk; (4) memungkinkan otorisasi yang berbeda (AGENT boleh membaca, tidak boleh menulis) |
| **Konsekuensi yang diterima** | Frontend melakukan dua pemanggilan saat menyimpan produk beserta konfigurasinya. Payload inline tetap didukung demi kompatibilitas, namun tidak lagi dapat menghapus field |

### D-11 — Jalur API memakai penamaan existing (`/produk`, `/order`)

| Aspek | Uraian |
|---|---|
| **Keputusan** | `/produk/{id}/form-config` dan `/order/create`, bukan `/products/{id}/form-config` dan `/orders` |
| **Alternatif** | Mengikuti penamaan berbahasa Inggris sebagaimana disebut pada kebutuhan |
| **Alasan** | Seluruh endpoint existing memakai penamaan berbahasa Indonesia (`ProdukController` → `/produk`, `OrderController` → `/order`). Memperkenalkan jalur berbahasa Inggris untuk satu fitur akan menciptakan inkonsistensi permanen pada permukaan API dan memerlukan konfigurasi keamanan ganda |
| **Konsekuensi yang diterima** | Menyimpang dari contoh pada dokumen kebutuhan. Bila Product Owner menghendaki standardisasi penamaan API, hal tersebut sebaiknya dikerjakan sebagai inisiatif tersendiri yang mencakup seluruh endpoint |

### D-12 — Payload submit System Field tidak memakai `field_key`

| Aspek | Uraian |
|---|---|
| **Keputusan** | `POST /order/create` tetap menerima `namaLengkap`, `nomorWhatsapp`, `alamat`, `idProvinsi`, `idKota`, `idKecamatan` |
| **Alternatif** | Menyeragamkan seluruh pengiriman nilai menjadi berbasis `field_key` |
| **Alasan** | Mengubah nama atribut pada endpoint publik akan memutus kompatibilitas seluruh klien checkout yang sedang berjalan, tanpa manfaat fungsional. `field_key` berperan sebagai kontrak *konfigurasi dan render*, bukan sebagai kontrak *payload submit* |
| **Konsekuensi yang diterima** | Frontend memelihara satu tabel pemetaan statis `field_key` → nama atribut payload untuk enam field (§14.3). Pemetaan ini dijamin tidak pernah berubah oleh BR-12 |

---

## 27. Traceability Matrix

Pemetaan antara kebutuhan, aturan bisnis, requirement, dan kriteria penerimaan — untuk dipakai QA dalam memastikan cakupan uji lengkap.

| Kebutuhan pengguna | Business Rule | Functional Requirement | Acceptance Criteria | Uji regresi |
|---|---|---|---|---|
| System Field selalu ada | BR-9, BR-10 | FR-3, FR-4 | AC-1, AC-2 | RT-10, RT-11 |
| System Field tidak dapat dihapus | BR-11 | FR-7 | AC-5 | — |
| Field Key System tidak dapat diubah | BR-12, BR-14 | FR-5 | AC-4 | — |
| Label dapat diubah | BR-13, BR-17 | FR-6 | AC-3 | RT-1, RT-2 |
| Placeholder dapat diubah | BR-13 | FR-6 | AC-3 | — |
| Help text dapat diisi | BR-13 | FR-6 | AC-2 | — |
| Custom Field dapat ditambah | BR-20, BR-21, BR-27 | FR-8, FR-9, FR-14 | AC-6, AC-7, AC-21 | — |
| Custom Field dapat dihapus bila belum dipakai | BR-23 | FR-11 | AC-19 | — |
| Custom Field tidak dapat dihapus bila sudah dipakai | BR-24 | FR-11 | AC-20 | — |
| Sort Order menentukan urutan render | BR-5 | FR-13, FR-19 | AC-10 | RT-15 |
| Required berlaku pada kedua kategori | BR-15, BR-26 | FR-5, FR-8 | AC-4, AC-16 | — |
| Konfigurasi berlaku per Produk | BR-1, BR-7 | FR-18 | AC-23 | RT-12 |
| Perubahan konfigurasi tidak mengubah order lama | BR-6, BR-32 | FR-27, FR-33 | AC-15 | — |
| Field Inactive tidak tampil pada checkout baru | BR-8, BR-25 | FR-19 | AC-11, AC-12 | — |
| Options untuk Select/Radio/Checkbox | BR-28, BR-29 | FR-15 | AC-8, AC-9 | — |
| Default Value | BR-30 | FR-16 | AC-2 | — |
| System Field disimpan ke tabel Order | BR-18 | — | AC-13 | RT-1 sampai RT-9 |
| Custom Field disimpan ke `order_custom_field` | BR-32 | FR-27 | AC-14 | — |
| Snapshot field_key dan field_label | BR-32 | FR-27, FR-33 | AC-15 | — |
| Frontend membaca satu konfigurasi | BR-2 | FR-19, FR-20 | AC-2, AC-10 | RT-15 |
| Isolasi tenant | BR-7 | FR-18 | AC-23 | — |
| Kompatibilitas mundur | — | FR-23, FR-24 | AC-22 | RT-15 |
| Nol regresi proses existing | BR-18 | FR-36 sampai FR-39 | AC-24 | RT-1 sampai RT-15 |
| Penyalinan produk | BR-35 | FR-39 | AC-25 | RT-12 |

---

## 28. Open Questions

| ID | Pertanyaan | Dampak bila tidak terjawab | Usulan default | Penanggung jawab |
|---|---|---|---|---|
| OQ-1 | Apakah Product Owner menerima keputusan D-4 (`is_required` System Field terkunci `true`)? | Bila ditolak, diperlukan pembahasan ulang mengenai perhitungan ongkir dan pengiriman untuk order berdata tidak lengkap | Terima D-4 | Product Owner |
| OQ-2 | Apakah peran AGENT boleh mengubah konfigurasi form, atau hanya membaca? | Menentukan konfigurasi otorisasi endpoint | Hanya membaca (`PUT` dibatasi SUPERADMIN dan ADMIN) | Product Owner |
| OQ-3 | Apakah tipe `FILE` termasuk dalam cakupan rilis pertama? | Menentukan apakah F-5 wajib atau dapat ditunda | Ditunda ke F-5 sebagai fase terpisah (R-11) | Product Owner, Tech Lead |
| OQ-4 | Berapa batas jumlah Custom Field yang disetujui? | Menentukan nilai konstanta BR-20 | 50 field aktif per produk | Product Owner |
| OQ-5 | Apakah Custom Field perlu masuk ke Export Excel pada rilis pertama? | Bila ya, cakupan bertambah signifikan (FE-1) | Tidak; ditunda (OOS-7) | Product Owner |
| OQ-6 | Apakah tersedia jendela pemeliharaan untuk menjalankan migrasi, atau migrasi wajib berjalan tanpa gangguan layanan? | Menentukan strategi eksekusi M-1 sampai M-6 | Migrasi berjalan pada *startup* aplikasi (tanpa gangguan), dengan cadangan basis data dijadwalkan sebelumnya | Tech Lead, DevOps |
| OQ-7 | Siapa yang meninjau hasil kueri distribusi *backfill* (§22.3 M-7 nomor 7) sebelum penerapan ke produksi? | Risiko R-2 tidak termitigasi | Product Manager bersama System Analyst | Product Manager |
| OQ-8 | Apakah *feature flag* per workspace tersedia pada infrastruktur saat ini, atau perlu dibangun? | Menentukan kelayakan mitigasi R-3. Entity `AppConfig` (konfigurasi *key-value* per workspace) tampak dapat dipakai untuk keperluan ini | Manfaatkan `AppConfig` sebagai mekanisme *flag* | Tech Lead |
| OQ-9 | Apakah nilai Custom Field perlu disertakan pada pesan WhatsApp konfirmasi? | Menentukan apakah FE-5 masuk cakupan | Tidak pada fase ini | Product Owner |
| OQ-10 | Apakah diperlukan pencatatan audit "siapa mengubah konfigurasi form dan kapan"? | Menentukan apakah FE-8 masuk cakupan | Tidak pada fase ini; `updated_at` sudah tersedia | Product Owner |

---

## 29. Lampiran

### 29.1 Glosarium

| Istilah | Definisi |
|---|---|
| **System Field** | Salah satu dari enam field bawaan sistem yang selalu ada pada setiap produk, nilainya disimpan pada kolom bertipe kuat di tabel `order`, dan kontrak teknisnya tidak dapat diubah |
| **Custom Field** | Field tambahan yang didefinisikan Admin per produk, nilainya disimpan pada tabel `order_custom_field` |
| **Field Key** | Pengenal teknis field yang bersifat stabil dan unik per produk; menjadi kontrak antara konfigurasi, frontend, validator, dan penyimpanan |
| **Field Category** | Diskriminator kategori field: `SYSTEM` atau `CUSTOM` |
| **Snapshot** | Salinan `field_key`, `field_label`, `field_type`, dan `sort_order` yang disimpan bersama nilai order, sehingga order lama tetap dapat dibaca dengan benar meskipun konfigurasi produk kemudian berubah |
| **Sort Order** | Nomor urut yang menentukan posisi field pada *render* form; berlaku dalam satu ruang urutan bersama untuk kedua kategori |
| **Upsert by field_key** | Pola penyimpanan yang memperbarui baris bila `field_key` sudah ada dan menyisipkan baris baru bila belum, sebagai pengganti pola *delete-and-reinsert* |
| **Self-healing** | Kemampuan sistem melengkapi System Field yang belum ada pada sebuah produk secara otomatis pada saat konfigurasi dibaca |
| **Slugify** | Proses mengubah label bebas menjadi `field_key` berbentuk `snake_case` ASCII |
| **Lenient mode** | Perlakuan yang mengabaikan `field_key` tidak dikenal pada submit order alih-alih menolak permintaan |
| **Usage Count** | Jumlah order yang memiliki nilai untuk sebuah `field_key` pada sebuah produk; menentukan apakah field dapat dihapus permanen |

### 29.2 Daftar Kode Galat Lengkap

Daftar konsolidasi seluruh kode galat yang diperkenalkan fitur ini, untuk dipakai frontend sebagai konstanta.

```
// Konfigurasi form
FIELDS_REQUIRED
SYSTEM_FIELD_NOT_DELETABLE
SYSTEM_FIELD_IMMUTABLE_ATTRIBUTE
UNKNOWN_SYSTEM_FIELD
INVALID_FIELD_CATEGORY
INVALID_FIELD_KEY_FORMAT
RESERVED_FIELD_KEY
DUPLICATE_FIELD_KEY
LABEL_REQUIRED
LABEL_TOO_LONG
PLACEHOLDER_TOO_LONG
HELP_TEXT_TOO_LONG
INVALID_FIELD_TYPE
FIELD_TYPE_RESERVED_FOR_SYSTEM
OPTIONS_REQUIRED_FOR_TYPE
OPTIONS_NOT_ALLOWED_FOR_TYPE
TOO_MANY_OPTIONS
OPTION_INCOMPLETE
OPTION_TOO_LONG
DUPLICATE_OPTION_VALUE
INVALID_DEFAULT_VALUE
DEFAULT_VALUE_NOT_ALLOWED
INVALID_VALIDATION_RULE
VALIDATION_RULE_NOT_ALLOWED
INVALID_RANGE
INVALID_SORT_ORDER
INVALID_BOOLEAN
INVALID_DATE_FORMAT
INVALID_ACCEPT_TYPE
CUSTOM_FIELD_LIMIT_EXCEEDED
FIELD_IN_USE
PAYLOAD_TOO_LARGE

// Submit order
REQUIRED_FIELD_MISSING
INVALID_VALUE_TYPE
VALUE_NOT_IN_OPTIONS
VALUE_RULE_VIOLATION
SYSTEM_FIELD_IN_CUSTOM_PAYLOAD
INVALID_PHONE_NUMBER
INVALID_LOCATION
LOCATION_HIERARCHY_MISMATCH
SHIPPING_RATE_NOT_FOUND
FILE_URL_NOT_ALLOWED
FILE_NOT_FOUND

// Unggah berkas
FILE_REQUIRED
FILE_TYPE_NOT_ALLOWED
FILE_SIZE_LIMIT_EXCEEDED
FIELD_NOT_FOUND
FIELD_NOT_FILE_TYPE
UPLOAD_RATE_LIMIT
STORAGE_UNAVAILABLE
```

### 29.3 Berkas Codebase yang Terdampak

| Berkas | Jenis perubahan |
|---|---|
| `entity/ProdukFormConfig.java` | Modifikasi — penambahan tujuh atribut, pemetaan enum |
| `entity/OrderCustomField.java` | **Baru** |
| `model/product/ProdukFormConfigDto.java` | Modifikasi — penambahan atribut (perlu dicatat: bertipe `@Value` immutable sehingga konstruktornya berubah; seluruh titik pemanggilan wajib disesuaikan) |
| `model/product/FormFieldConfigDto.java` | **Baru** — DTO kaya untuk endpoint konfigurasi |
| `model/product/FormConfigRequest.java`, `FormConfigResponse.java` | **Baru** |
| `model/product/OptionDto.java`, `ValidationRuleDto.java` | **Baru** |
| `model/product/AddProdukDto.java` | Modifikasi — `@NotNull` pada `formConfig` dilonggarkan |
| `model/product/ProdukCheckoutDto.java`, `ProdukDetailDto.java` | Modifikasi — tipe `formConfig` diperkaya |
| `model/Order/CreateOrderDto.java` | Modifikasi — penambahan `customFields` |
| `model/Order/DetailOrderDto.java` | Modifikasi — penambahan `customFields` |
| `model/Order/OrderCustomFieldDto.java` | **Baru** |
| `model/ErrorDto.java` | Modifikasi — penambahan `code` dan `meta` |
| `repository/ProdukFormConfigRepository.java` | Modifikasi — kueri berbasis `field_key`, urut menurut `orders` |
| `repository/OrderCustomFieldRepository.java` | **Baru** — termasuk kueri agregat `usageCount` |
| `service/ProdukFormConfigService.java` | **Baru** — logika inti fitur |
| `service/ProdukService.java` | Modifikasi — `saveProduct()`, `getDetailProduk()`, `getCheckoutProduk()`, `copyProduk()` |
| `service/order/OrderOrchestrationService.java` | Modifikasi — validasi dan penyimpanan Custom Field |
| `service/order/OrderService.java` | Modifikasi — `getOrderDetail()` menyertakan Custom Field |
| `validators/CustomFieldValidator.java` | **Baru** |
| `validators/FormConfigValidator.java` | **Baru** |
| `controller/ProdukController.java` | Modifikasi — dua endpoint baru + endpoint unggah |
| `configuration/ProdukFormConfigSchemaInitializer.java` | **Baru** — index, constraint, *backfill* |
| `configuration/SecurityConfig.java` | Modifikasi — penambahan `/produk/form-config/upload` ke daftar endpoint publik |
| `util/SlugUtil.java` | **Baru** — implementasi *slugify* §11.5 |
| `model/product/SystemFormField.java` (enum) | **Baru** — sumber kebenaran tunggal §11.4 |
| `model/product/FormFieldType.java` (enum) | **Baru** |

### 29.4 Riwayat Dokumen

| Versi | Tanggal | Perubahan | Penulis |
|---|---|---|---|
| 0.1 | 2026-07-28 | Draf awal lengkap; mencakup analisis kondisi existing pada codebase, dua belas keputusan desain, lima belas risiko, dan sepuluh open question | Product Management & System Analysis |
