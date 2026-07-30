#!/usr/bin/env bash
# Seed 5 produk dummy dengan konfigurasi form berbeda untuk workspace Santara Store (id=2).
#
# Produk dan konfigurasi form dibuat dalam SATU permintaan POST /produk — `formConfig`
# ikut di payload produk. Enam System Field selalu di-seed sistem, sehingga payload cukup
# memuat Custom Field (atau daftar lengkap bila ingin menentukan label & urutan sekaligus).
#
# Pemakaian: bash scripts/seed-dummy-santara.sh [BASE_URL]
set -euo pipefail

BASE="${1:-http://localhost:8081}"
WS=2
GUDANG=2
USERNAME="${SUPERADMIN_USERNAME:-superadmin}"
PASSWORD="${SUPERADMIN_PASSWORD:-PasswordAdmin1234}"

TOKEN=$(curl -s -X POST "$BASE/account/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "GAGAL login" >&2
  exit 1
fi
echo "Login OK"

# buat_produk <label> <nama> <url> <narasi> <atribut_json> <fitur_json> <form_config_json>
buat_produk() {
  local tajuk="$1" nama="$2" url="$3" narasi="$4" atribut="$5" fitur="$6" formconfig="$7"
  local code
  echo "$tajuk"
  code=$(curl -s -o /tmp/seed_produk.json -w "%{http_code}" -X POST "$BASE/produk" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d "{
      \"idWorkspace\": $WS,
      \"namaProduk\": \"$nama\",
      \"urlCheckout\": \"$url\",
      \"idGudang\": $GUDANG,
      \"narasiTombol\": \"$narasi\",
      \"poinFitur\": $fitur,
      \"atributProduk\": $atribut,
      \"pembayaran\": [{\"tipe\":\"COD\",\"config\":{}},{\"tipe\":\"TRANSFER\",\"config\":{}}],
      \"gambarProduk\": [],
      \"testimoni\": [],
      \"formConfig\": $formconfig
    }")
  if [ "$code" != "200" ]; then
    echo "  GAGAL (HTTP $code): $(head -c 400 /tmp/seed_produk.json)" >&2
    return 1
  fi
  local jumlah
  jumlah=$(curl -s "$BASE/produk/checkout?urlCheckout=$url" | grep -o '"fieldKey"' | wc -l | tr -d ' ')
  echo "  OK — $jumlah field pada form checkout"
}

# ── 1. Kaos Distro — dua SELECT, disisipkan DI ANTARA System Field ──
# Mengirim daftar lengkap agar urutan tampil ditentukan sendiri.
buat_produk "[1/5] Kaos Distro Premium" \
  "Kaos Distro Premium" "kaos-distro-premium" "Pesan Sekarang" \
  '[{"deskripsi":"1 Pcs","harga":89000,"berat":250},{"deskripsi":"3 Pcs (Hemat)","harga":249000,"berat":750}]' \
  '["Bahan cotton combed 30s","Sablon plastisol tahan lama","Jahitan rantai"]' \
  '[
    {"fieldKey":"customer_name","fieldCategory":"SYSTEM","label":"Nama Penerima","placeholder":"Masukkan nama penerima","sortOrder":1},
    {"fieldCategory":"CUSTOM","fieldType":"SELECT","label":"Ukuran Baju","placeholder":"Pilih ukuran","helpText":"Lihat tabel ukuran pada deskripsi produk","isRequired":true,"defaultValue":"M","sortOrder":2,
     "options":[{"label":"S (Lebar 46cm)","value":"S"},{"label":"M (Lebar 49cm)","value":"M"},{"label":"L (Lebar 52cm)","value":"L"},{"label":"XL (Lebar 55cm)","value":"XL"}]},
    {"fieldCategory":"CUSTOM","fieldType":"SELECT","label":"Warna","placeholder":"Pilih warna","isRequired":true,"sortOrder":3,
     "options":[{"label":"Hitam","value":"HITAM"},{"label":"Putih","value":"PUTIH"},{"label":"Navy","value":"NAVY"}]},
    {"fieldKey":"phone_number","fieldCategory":"SYSTEM","label":"Nomor WhatsApp","sortOrder":4},
    {"fieldKey":"address","fieldCategory":"SYSTEM","label":"Alamat Lengkap","placeholder":"Jalan, nomor rumah, RT/RW, patokan","sortOrder":5},
    {"fieldKey":"province","fieldCategory":"SYSTEM","label":"Provinsi","sortOrder":6},
    {"fieldKey":"city","fieldCategory":"SYSTEM","label":"Kota / Kabupaten","sortOrder":7},
    {"fieldKey":"district","fieldCategory":"SYSTEM","label":"Kecamatan","helpText":"Ongkos kirim dihitung berdasarkan kecamatan","sortOrder":8}
  ]'

# ── 2. Kue Ulang Tahun — SELECT + TEXT berbatas + TEXTAREA opsional ──
# Hanya mengirim Custom Field; System Field memakai label bawaan dan tampil lebih dulu.
buat_produk "[2/5] Kue Ulang Tahun Custom" \
  "Kue Ulang Tahun Custom" "kue-ulang-tahun-custom" "Pesan Kue Sekarang" \
  '[{"deskripsi":"Diameter 16 cm","harga":185000,"berat":1200},{"deskripsi":"Diameter 20 cm","harga":275000,"berat":1800}]' \
  '["Butter cream premium","Tanpa pengawet","Bisa request desain"]' \
  '[
    {"fieldCategory":"CUSTOM","fieldType":"SELECT","label":"Rasa Kue","placeholder":"Pilih rasa","isRequired":true,
     "options":[{"label":"Cokelat","value":"COKELAT"},{"label":"Keju","value":"KEJU"},{"label":"Red Velvet","value":"RED_VELVET"},{"label":"Tiramisu","value":"TIRAMISU"}]},
    {"fieldCategory":"CUSTOM","fieldType":"TEXT","label":"Tulisan pada Kue","placeholder":"Contoh: Happy Birthday Sarah","helpText":"Maksimal 40 karakter agar muat di permukaan kue","isRequired":true,
     "validation":{"minLength":2,"maxLength":40}},
    {"fieldCategory":"CUSTOM","fieldType":"TEXTAREA","label":"Catatan Dekorasi","placeholder":"Warna tema, karakter, hiasan tambahan","isRequired":false,
     "validation":{"maxLength":300}}
  ]'

# ── 3. Jasa Servis AC — field teknis, satu di antaranya NONAKTIF ──
buat_produk "[3/5] Jasa Servis AC Panggilan" \
  "Jasa Servis AC Panggilan" "jasa-servis-ac" "Pesan Jasa" \
  '[{"deskripsi":"Cuci AC 1/2 - 1 PK","harga":75000,"berat":1},{"deskripsi":"Cuci AC 1.5 - 2 PK","harga":110000,"berat":1}]' \
  '["Teknisi bersertifikat","Garansi 14 hari","Termasuk vakum freon"]' \
  '[
    {"fieldCategory":"CUSTOM","fieldType":"SELECT","label":"Tipe AC","placeholder":"Pilih tipe","isRequired":true,
     "options":[{"label":"Split Wall","value":"SPLIT_WALL"},{"label":"Cassette","value":"CASSETTE"},{"label":"Standing Floor","value":"STANDING"},{"label":"Window","value":"WINDOW"}]},
    {"fieldCategory":"CUSTOM","fieldType":"TEXT","label":"Merk AC","placeholder":"Contoh: Daikin, Panasonic","isRequired":true,"validation":{"maxLength":60}},
    {"fieldCategory":"CUSTOM","fieldType":"TEXTAREA","label":"Keluhan","placeholder":"Ceritakan kondisi AC saat ini","helpText":"Semakin detail, semakin siap teknisi kami","isRequired":true,
     "validation":{"minLength":10,"maxLength":500}},
    {"fieldCategory":"CUSTOM","fieldType":"TEXT","label":"Kode Promo","placeholder":"Opsional","isRequired":false,"isActive":false}
  ]'

# ── 4. Parfum — hanya SELECT, satu dengan defaultValue ──
buat_produk "[4/5] Parfum Inspired 30ml" \
  "Parfum Inspired 30ml" "parfum-inspired-30ml" "Beli Sekarang" \
  '[{"deskripsi":"30 ml","harga":65000,"berat":150},{"deskripsi":"30 ml (Beli 2)","harga":120000,"berat":300}]' \
  '["Tahan hingga 8 jam","Bibit parfum grade A","Botol kaca premium"]' \
  '[
    {"fieldCategory":"CUSTOM","fieldType":"SELECT","label":"Pilihan Aroma","placeholder":"Pilih aroma","isRequired":true,
     "options":[{"label":"Fresh Citrus","value":"FRESH_CITRUS"},{"label":"Woody Musk","value":"WOODY_MUSK"},{"label":"Floral Sweet","value":"FLORAL_SWEET"},{"label":"Aqua Marine","value":"AQUA_MARINE"},{"label":"Vanilla Latte","value":"VANILLA_LATTE"}]},
    {"fieldCategory":"CUSTOM","fieldType":"SELECT","label":"Tipe Tutup Botol","placeholder":"Pilih tipe","isRequired":false,"defaultValue":"SPRAY",
     "options":[{"label":"Spray","value":"SPRAY"},{"label":"Roll On","value":"ROLL_ON"}]}
  ]'

# ── 5. Hampers — satu TEXTAREA opsional ──
buat_produk "[5/5] Hampers Lebaran Signature" \
  "Hampers Lebaran Signature" "hampers-lebaran-signature" "Pesan Hampers" \
  '[{"deskripsi":"Paket Silver","harga":215000,"berat":1500},{"deskripsi":"Paket Gold","harga":385000,"berat":2500}]' \
  '["Kemasan eksklusif","Kartu ucapan gratis","Isi bisa disesuaikan"]' \
  '[
    {"fieldCategory":"CUSTOM","fieldType":"TEXTAREA","label":"Pesan pada Kartu Ucapan","placeholder":"Tulis pesan untuk penerima hampers","helpText":"Kosongkan bila tidak memerlukan kartu ucapan","isRequired":false,
     "validation":{"maxLength":200}}
  ]'

echo
echo "Selesai. Ringkasan:"
echo "  1. Kaos Distro Premium        -> kaos-distro-premium        (2 SELECT disisipkan di antara System Field)"
echo "  2. Kue Ulang Tahun Custom     -> kue-ulang-tahun-custom     (SELECT + TEXT berbatas + TEXTAREA opsional)"
echo "  3. Jasa Servis AC Panggilan   -> jasa-servis-ac             (4 Custom Field, 1 NONAKTIF)"
echo "  4. Parfum Inspired 30ml       -> parfum-inspired-30ml       (2 SELECT, satu dengan defaultValue)"
echo "  5. Hampers Lebaran Signature  -> hampers-lebaran-signature  (1 TEXTAREA opsional)"
