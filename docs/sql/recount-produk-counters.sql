-- Hitung ulang Produk.order_count dan Produk.sold_count dari tabel order.
--
-- Jalankan SATU KALI secara manual setelah deploy perbaikan counter produk
-- (order DELETE sebelumnya tetap terhitung di order_count, dan pergantian produk
-- saat edit order tidak memindahkan counter).
--
-- Aturan (sama dengan OrderService.applyProdukCounterChange):
--   order_count = jumlah order dengan status bukan DELETE
--   sold_count  = jumlah order dengan status PAID
--
-- Disarankan dijalankan di luar jam sibuk; bungkus dalam transaksi agar bisa dicek dulu.

BEGIN;

UPDATE produk p
SET order_count = (SELECT COUNT(*) FROM public."order" o
                   WHERE o.id_produk = p.id AND o.status IS DISTINCT FROM 'DELETE'),
    sold_count  = (SELECT COUNT(*) FROM public."order" o
                   WHERE o.id_produk = p.id AND o.status = 'PAID');

-- Periksa hasilnya, lalu COMMIT (atau ROLLBACK bila tidak sesuai).
-- SELECT id, nama_produk, order_count, sold_count FROM produk ORDER BY nama_produk;

COMMIT;
