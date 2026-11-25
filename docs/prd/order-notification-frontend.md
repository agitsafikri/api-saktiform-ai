# PRD — Notifikasi Order Masuk (Frontend / Dashboard)

| Field | Value |
|---|---|
| Feature | Penghitung "order baru" real-time per workspace (badge, banner, toast berbatas), di-reset saat list order dimuat ulang |
| Modul | **Pesanan** + layout dashboard (sidebar) |
| Dokumen terkait | [Architecture — WebSocket](../architecture.md), [FE Business Modules](../frontend/business-modules.md) (§Real-Time Behaviour, §4 Pesanan), [FE UI Flow](../frontend/ui-flow.md) |
| Stack | Vue 3 (`<script setup>`, Composition API), TypeScript, Vite, Pinia, STOMP over SockJS |
| Status | **Ready for Implementation**. Backend sudah diimplementasikan dan diuji |
| Scope | Frontend saja; kontrak WebSocket mengikuti backend |
| Last updated | 2026-11-23 |
| Target pembaca | Frontend Developer, QA, Product |

> PRD ini menambahkan notifikasi saat ada order baru. Dashboard hanya diberi tahu bahwa ada order baru. **List order tidak diperbarui otomatis.** Jumlahnya terus bertambah sampai list order dimuat ulang. PRD ini tidak berisi kode final.

---

## 1. Latar Belakang & Tujuan

Sekarang user baru tahu ada order masuk setelah me-refresh halaman Pesanan. Backend kini mengirim event `ORDER_CREATED` lewat WebSocket ke topic milik setiap workspace.

Tujuan:

- User langsung tahu ada order baru dan berapa jumlahnya, di halaman mana pun di dashboard.
- User sendiri yang memutuskan kapan memuat ulang list. List tidak berubah sendiri saat sedang dibaca, difilter, atau dicentang untuk export.

---

## 2. Aturan Bisnis (dari backend, wajib dipahami)

| # | Aturan |
|---|---|
| BR-1 | Event hanya dikirim untuk order **baru dari checkout**. Order yang dibuat agen di Chat (`CST_CHAT`), abandoned cart, edit order, dan konfirmasi order **tidak** mengirim event. |
| BR-2 | Event dikirim ke `/topic/orders/{workspaceId}`. Setiap workspace terpisah; user hanya menerima event workspace yang sedang aktif. |
| BR-3 | Event dikirim setelah order tersimpan. Setiap event berarti satu order baru yang sudah ada di database. |
| BR-4 | Isi event minimal (`orderId`, `createdAt`), **tanpa data pelanggan**. Detail order diambil lewat `GET /order`. |
| BR-5 | Event yang terjadi saat koneksi WebSocket putus **tidak dikirim ulang**, sehingga jumlahnya bisa kurang dari jumlah order sebenarnya. Ini sudah disepakati; tidak ada endpoint `new-count`. |
| BR-6 | Penghitung disimpan di frontend saja (tidak di database dan tidak di `localStorage`). Setelah reload browser, angkanya 0. |

---

## 3. Scope

### In scope

- Koneksi WebSocket untuk order yang aktif selama user berada di layout dashboard.
- State `newOrderCount` dan aturan reset-nya (§5).
- Badge di menu sidebar **Pesanan** (§6.1).
- Banner "order baru" di halaman `/pesanan` dengan tombol muat ulang (§6.2).
- Toast "order baru masuk" dengan batas frekuensi (§6.4).

### Out of scope

- Memperbarui list order secara otomatis.
- Toast yang bisa diklik untuk membuka halaman Pesanan.
- Notifikasi browser (`Notification` API), service worker, dan suara.
- Notifikasi saat dashboard ditutup.
- Menyimpan penghitung antar-reload, antar-tab, atau antar-device.
- Notifikasi untuk order `CST_CHAT`, abandoned cart, atau perubahan status order.

---

## 4. Kontrak WebSocket

| Item | Nilai |
|---|---|
| Endpoint | `/ws` (STOMP over SockJS, sama dengan Chat) |
| Topic | `/topic/orders/{workspaceId}` |
| Heartbeat | 10 detik (konfigurasi broker yang sudah ada) |

Payload:

```json
{
  "eventId": "8f1c…",
  "type": "ORDER_CREATED",
  "data": { "orderId": "3b2a…", "createdAt": "2026-11-23T03:15:00Z" },
  "timestamp": "2026-11-23T03:15:00Z"
}
```

| Field | Keterangan |
|---|---|
| `eventId` | UUID unik per event; dipakai untuk de-duplikasi |
| `type` | Selalu `ORDER_CREATED` di topic ini. Abaikan event dengan `type` lain, untuk berjaga-jaga kalau nanti ada event baru. |
| `data.orderId` | UUID order baru |
| `data.createdAt` / `timestamp` | ISO-8601 **UTC**. Kalau ditampilkan, konversi ke WIB. |

> **Catatan keamanan:** `/ws` saat ini tidak memeriksa token. Tetap kirim `Authorization: Bearer <token>` di `connectHeaders` STOMP. Tidak berpengaruh sekarang, tapi nanti tidak perlu diubah lagi saat backend menambahkan pengecekan token.

---

## 5. State & Siklus Hidup

### 5.1 Koneksi

Sekarang `useWebSocket().connectGlobal()` baru tersambung saat `/chat` dibuka dan **memutus koneksi saat keluar dari `/chat`**. Notifikasi order harus aktif di semua halaman dashboard, jadi **tidak boleh bergantung pada koneksi Chat.**

Pendekatan yang disarankan: composable atau store terpisah, misalnya `useOrderNotification`, yang:

- dijalankan sekali di **layout dashboard** (bukan di halaman `/pesanan`),
- punya client STOMP sendiri, sehingga Chat bisa connect/disconnect tanpa memengaruhi notifikasi order,
- reconnect otomatis (mis. `reconnectDelay: 5000`).

Dua koneksi per tab (Chat dan order) bisa diterima. Kalau tim lebih suka satu client bersama, client itu harus memakai reference counting supaya keluar dari `/chat` tidak memutus subscription order.

### 5.2 State (Pinia)

```ts
newOrderCount: number          // awal 0
seenEventIds: Set<string>      // de-duplikasi, dikosongkan saat reset
toastCooldownUntil: number     // epoch ms; selama belum lewat, toast baru ditahan (§6.4)
pendingToastCount: number      // order yang masuk selama cooldown, untuk toast ringkasan
```

| Kejadian | Aksi |
|---|---|
| Event `ORDER_CREATED` diterima, `eventId` belum pernah diterima | `newOrderCount += 1`, simpan `eventId`, lalu jalankan aturan toast (§6.4) |
| Event dengan `eventId` yang sudah pernah diterima | abaikan |
| `GET /order` (list) **berhasil** dimuat, karena alasan apa pun: buka `/pesanan`, refresh, ganti filter atau pencarian, ganti halaman, klik "Muat ulang" | `newOrderCount = 0`, kosongkan `seenEventIds` |
| `GET /order` gagal | penghitung **tidak** di-reset |
| `GET /order/export` | penghitung **tidak** di-reset (export bukan memuat list) |
| Workspace aktif berganti | unsubscribe topic lama, `newOrderCount = 0`, batalkan toast ringkasan yang tertunda, subscribe `/topic/orders/{workspaceIdBaru}` |
| Logout / keluar layout dashboard | unsubscribe, disconnect, `newOrderCount = 0`, batalkan toast ringkasan yang tertunda |
| Reload browser | otomatis 0 (BR-6) |

> Reset dipasang **di satu tempat**: di action store Pesanan yang memanggil `GET /order`, setelah respons sukses. Jangan dipasang di setiap tombol, supaya tidak ada jalur muat ulang yang terlewat.

---

## 6. Tampilan

### 6.1 Badge sidebar (semua halaman dashboard)

- Badge angka di menu **Pesanan**, memakai komponen `Badge` yang sudah ada.
- Tampil hanya saat `newOrderCount > 0`. Lebih dari 99 ditampilkan **"99+"**.
- Berlaku untuk semua role yang melihat menu Pesanan (OWNER, ADMIN, CUSTOMER_SERVICE).

### 6.2 Banner di halaman `/pesanan`

- Tampil di atas tabel saat `newOrderCount > 0`:
  *"**3 order baru** masuk. [Muat ulang]"*
- **Muat ulang** memanggil `GET /order` dengan **filter dan pencarian yang sedang aktif**, lalu kembali ke **halaman 1**, karena order terbaru ada di awal list.
- Saat memuat, tombol menampilkan loading dan tidak bisa diklik dua kali.
- Banner hilang setelah list berhasil dimuat (penghitung = 0).
- Tidak ada tombol tutup tanpa memuat ulang. Supaya angka di banner dan badge selalu sama, satu-satunya cara reset adalah memuat ulang list.

### 6.3 Teks & filter

Kalau list sedang difilter (mis. provinsi tertentu), order baru belum tentu muncul setelah dimuat ulang. Karena itu teksnya **"order baru masuk"**, bukan "order baru di list ini". Penghitung mencakup semua order checkout di workspace, apa pun filternya.

### 6.4 Toast dengan batas frekuensi (semua halaman dashboard)

Toast menarik perhatian tepat saat order masuk. Badge dan banner tetap menjadi sumber angka yang lengkap. Toast hanya pemberitahuan sesaat, dan **boleh tidak muncul** tanpa memengaruhi penghitung.

**Batas: maksimal 1 toast setiap 10 detik** (`TOAST_COOLDOWN_MS = 10_000`, dibuat konstanta agar mudah diubah).

| Kondisi saat event diterima | Aksi |
|---|---|
| Di luar masa cooldown | Tampilkan toast *"Order baru masuk"*, lalu mulai cooldown 10 detik |
| Masih dalam masa cooldown | Jangan tampilkan toast; `pendingToastCount += 1` |
| Cooldown berakhir dan `pendingToastCount > 0` | Tampilkan **satu** toast ringkasan *"N order baru masuk"* (N = `pendingToastCount`), set `pendingToastCount = 0`, mulai cooldown baru |
| Cooldown berakhir dan `pendingToastCount = 0` | Tidak ada aksi |
| Tab browser sedang tidak terlihat (`document.visibilityState === 'hidden'`) | Jangan tampilkan toast dan jangan tambah `pendingToastCount`. Badge tetap bertambah dan akan terlihat saat user kembali ke tab. |
| List order dimuat ulang (penghitung di-reset) | Batalkan toast ringkasan yang tertunda (`pendingToastCount = 0`); user sudah melihat order terbarunya |

Contoh: 10 order masuk dalam 3 detik. Hasilnya 1 toast "Order baru masuk", lalu sekitar 10 detik kemudian 1 toast "9 order baru masuk". Badge menunjukkan 10. Toast tidak muncul 10 kali.

Ketentuan tampilan:

- Pakai komponen alert yang sudah ada (`alertStore.setAlert()`), dengan tipe **info/sukses**, bukan error. Kalau `alertStore` belum punya tipe info, tambahkan tanpa mengubah tipe yang sudah ada.
- **Jangan menimpa alert error yang sedang tampil.** Kalau alert error sedang tampil, lewati toast order kali itu (tidak perlu diantrekan). Penghitung tetap bertambah.
- Toast hilang sendiri sesuai durasi alert yang sudah ada (3 detik) dan tidak bisa diklik.
- Toast tetap muncul walaupun user sedang di `/pesanan`, karena banner tidak selalu terlihat kalau halaman sedang di-scroll.

---

## 7. Error Handling

| Kondisi | Penanganan |
|---|---|
| Koneksi WebSocket gagal / putus | Diam-diam reconnect; **jangan** tampilkan alert berulang. Boleh `console.warn`. |
| Event dengan format tidak dikenal | Abaikan |
| "Muat ulang" gagal | Alert error yang sudah ada (`alertStore`). Banner tetap tampil dan penghitung tidak di-reset. |

---

## 8. Acceptance Criteria

| # | Kriteria |
|---|---|
| AC-1 | Order dibuat dari halaman checkout → dalam beberapa detik badge Pesanan bertambah 1, di halaman dashboard mana pun (Beranda, Produk, Chat, Pesanan). |
| AC-2 | Tiga order checkout berturut-turut → badge **3**, dan banner di `/pesanan` berbunyi "3 order baru". |
| AC-3 | List order **tidak** berubah sendiri saat event diterima (baris, halaman, dan centang export tetap). |
| AC-4 | Klik "Muat ulang" → list dimuat dengan filter yang sama, kembali ke halaman 1, order baru tampil (kalau cocok dengan filter), badge dan banner hilang. |
| AC-5 | Ganti filter, cari, ganti halaman, atau refresh `/pesanan` → penghitung kembali ke 0. |
| AC-6 | Export pesanan **tidak** mengubah penghitung. |
| AC-7 | Order dibuat agen dari form tambah pesanan di Chat → penghitung **tidak** bertambah. |
| AC-8 | Order checkout di workspace B tidak menambah penghitung user yang sedang aktif di workspace A. |
| AC-9 | Ganti workspace → penghitung 0, dan event workspace baru mulai terhitung. |
| AC-10 | Masuk lalu keluar halaman `/chat` → notifikasi order tetap berfungsi (koneksi Chat terputus tidak memengaruhinya). |
| AC-11 | Penghitung > 99 tampil "99+". |
| AC-12 | Matikan jaringan beberapa detik lalu nyalakan lagi → koneksi pulih sendiri dan event berikutnya terhitung, tanpa alert berulang. Order selama jaringan mati tidak terhitung (BR-5), dan ini diterima. |
| AC-13 | Edit order (termasuk mengganti nomor WA) atau abandoned cart **tidak** menambah penghitung. |
| AC-14 | Satu order checkout → satu toast "Order baru masuk" muncul di halaman dashboard mana pun dan hilang sendiri. |
| AC-15 | 5 order dalam 3 detik → hanya 1 toast "Order baru masuk", lalu sekitar 10 detik kemudian 1 toast "4 order baru masuk". Badge menunjukkan 5. |
| AC-16 | Order masuk saat tab browser tidak aktif → tidak ada toast yang menumpuk saat kembali ke tab, tetapi badge sudah bertambah. |
| AC-17 | Saat alert error sedang tampil, order masuk tidak menimpa alert error tersebut. |
| AC-18 | Klik "Muat ulang" saat toast ringkasan masih tertunda → toast ringkasan tidak muncul. |

Cara cepat menguji (QA): buka dashboard di satu tab, buat order lewat halaman checkout produk di tab lain atau dari HP. Pantau frame STOMP di DevTools → Network → WS.

---

## 9. Ketergantungan & Rollout

1. Backend sudah siap. Topic `/topic/orders/{workspaceId}` sudah aktif dan tidak mengubah topic Chat.
2. Frontend bisa di-deploy kapan saja. Tanpa frontend baru, event dikirim tapi tidak ada yang subscribe (tidak berdampak).
3. Perbarui `docs/frontend/business-modules.md` (§Real-Time Behaviour dan §4 Pesanan) bersamaan dengan implementasi.
4. Pekerjaan lanjutan terpisah: otorisasi SUBSCRIBE di backend (cek JWT dan keanggotaan workspace). Setelah itu, `connectHeaders` dari §4 menjadi wajib.
