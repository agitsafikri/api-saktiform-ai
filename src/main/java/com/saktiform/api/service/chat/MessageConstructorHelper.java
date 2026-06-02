package com.saktiform.api.service.chat;


import com.saktiform.api.entity.Order;
import com.saktiform.api.repository.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MessageConstructorHelper {
    private final OrderRepository orderRepository;
    private final FiturProdukRepository fiturProdukRepository;
    private final ProdukPembayaranRepository produkPembayaranRepository;
    private final ProdukRepository produkRepository;
    private final AtributProdukRepository atributProdukRepository;
    private final GudangRepository gudangRepository;
    private final ProvinceRepository provinceRepository;
    private final CityRepository cityRepository;
    private final DistrictRepository districtRepository;

    public MessageConstructorHelper(OrderRepository orderRepository,
                                    FiturProdukRepository fiturProdukRepository,
                                    ProdukPembayaranRepository produkPembayaranRepository, ProdukRepository produkRepository, AtributProdukRepository atributProdukRepository, GudangRepository gudangRepository, ProvinceRepository provinceRepository, CityRepository cityRepository, DistrictRepository districtRepository) {
        this.orderRepository = orderRepository;
        this.fiturProdukRepository = fiturProdukRepository;
        this.produkPembayaranRepository = produkPembayaranRepository;
        this.produkRepository = produkRepository;
        this.atributProdukRepository = atributProdukRepository;
        this.gudangRepository = gudangRepository;
        this.provinceRepository = provinceRepository;
        this.cityRepository = cityRepository;
        this.districtRepository = districtRepository;
    }


    public String createFollowupCodMessage() {
        String template = """
        Selamat datang di Toko kami Kaka {nama_customer} 😊\nPesanan anda:
        Produk: {nama_produk}
        Harga: {harga_produk}
        Ongkir: {ongkir}
        Total: {total}
        
        Dikirim ke:
        Nama: {nama_customer}
        No HP: {telepon_customer}
        Alamat: {alamat_customer}
        Kota: {kota_customer}
        Kecamatan: {kecamatan_customer}
        
        kakak bisa menyiapkan {total} dan pastikan untuk nomor telpon dan alamatnya sudah benar ya kak?
        
        Paketnya sudah saya bungkusin ya kak tinggal ditempel resi milik kaka dan saya antar ke pihak ekspedisi hari ini🥰🙏🏻 Kaka bisa terima paket dr kurir kira2 di jam berapa ? Pagi siang atau sore kak? 🙏☺
        """;

        return template;
    }

    public String createFollowupTransferMessage() {
        String template = """
        Selamat datang di Toko kami Kaka {nama_customer} 😊
        Pesanan anda:
        Produk: {nama_produk}
        Harga: {harga_produk}
        Ongkir: {ongkir}
        Total: {total}
        
        Dikirim ke:
        Nama: {nama_customer}
        No HP: {telepon_customer}
        Alamat: {alamat_customer}
        Kota: {kota_customer}
        Kecamatan: {kecamatan_customer}
        
        Silahkan transfer senilai {total}, ke salah satu rekening dibawah ini ka:
        {deskripsi_transfer_bank}
        Paketnya sudah saya bungkusin ya kak tinggal ditempel resi milik kka dan saya antar ke pihak ekspedisi hari ini🥰🙏🏻 Kaka bisa terima paket dr kurir kira2 di jam berapa ? Pagi siang atau sore kak? 🙏☺
        """;

        return template;
    }

    public String createConfirmationCodMessage() {
        String template = """
                Baik Kak {nama_customer} pesanan sudah kami terima 😊
                Untuk detailnya:
                Produk: {nama_produk}
                Harga: {harga_produk}
                Ongkir: {ongkir}
                Total: {total}
                
                Dikirim ke:
                Nama: {nama_customer}
                No HP: {telepon_customer}
                Alamat: {alamat_customer}
                Kota: {kota_customer}
                Kecamatan: {kecamatan_customer}
                
                kakak bisa menyiapkan {total}
                Paketnya sudah saya bungkusin ya kak tinggal ditempel resi milik kaka dan saya antar ke pihak ekspedisi hari ini🥰🙏🏻
                """;

        return template;
    }

    public String createConfirmationTransferMessage() {
        String template = """
                Baik Kak {nama_customer} pesanan sudah kami terima 😊
                Untuk detailnya:
                Produk: {nama_produk}
                Harga: {harga_produk}
                Ongkir: {ongkir}
                Total: {total}
                
                Dikirim ke:
                Nama: {nama_customer}
                No HP: {telepon_customer}
                Alamat: {alamat_customer}
                Kota: {kota_customer}
                Kecamatan: {kecamatan_customer}
                
                Silahkan transfer senilai {total}, ke salah satu rekening dibawah ini ka:
                {deskripsi_transfer_bank}
                Paketnya sudah saya bungkusin ya kak tinggal ditempel resi milik kaka dan saya antar ke pihak ekspedisi hari ini🥰🙏🏻
                """;

        return template;
    }



    public String confirmationMessage(String namaProduk, String customerName) {
        String template = """
                Halo, saya sudah melakukan pemesanan {nama_produk}, atas nama {nama_customer}. Mohon segera diproses ya 🙏🏻""";
        template = template.replace("{nama_customer}", customerName);
        template = template.replace("{nama_produk}", namaProduk);

        return template;
    }

    public String fillTemplate(String template, Map<String, ?> variables) {
        String result = template;
        for (var entry : variables.entrySet()) {
            result = result.replace(
                    "{" + entry.getKey() + "}",
                    entry.getValue() != null ? entry.getValue().toString() : ""
            );
        }
        return result;
    }


    public Map<String, Object> buildOrderParams(UUID orderId) {
        Map<String, Object> map = new HashMap<>();
        Order order = null;
        if (orderId != null){
             order = orderRepository.findById(orderId).get();
        }



        if(order != null){
            map.put("nama_customer", order.getNamaPenerima());
            map.put("telepon_customer", order.getNomorWhatsapp());

            var produk = produkRepository.findById(order.getIdProduk()).get();
            var atributProduk = atributProdukRepository.findById(order.getIdAtributProduk()).get();
            var gudang = gudangRepository.findById(produk.getIdGudang()).get();
            var provinsiGudang = provinceRepository.findById(gudang.getIdProvinsi()).get();
            var kotaGudang = cityRepository.findById(gudang.getIdKota()).get();
            var kecamatanGudang = districtRepository.findById(gudang.getIdKecamatan()).get();
            var provinsiCust = provinceRepository.findById(order.getIdProvinsi()).get();
            var kotaCust = cityRepository.findById(order.getIdKota()).get();
            var kecamatanCust = districtRepository.findById(order.getIdKecamatan()).get();


            // Data produk
            map.put("nama_produk", produk.getNamaProduk());
            map.put("atribut_produk", atributProduk.getDeskripsi());
            map.put("harga_produk", formatRupiah(atributProduk.getHarga()));
            map.put("berat_produk", atributProduk.getBerat() + " gram");
            var fitur = fiturProdukRepository.getFiturProduksByIdProduk(produk.getId());
            String pointFitur = "";
            for (var fiturProduk : fitur) {
              pointFitur = fiturProduk.getDeskripsi() + ", " + pointFitur;
            }
            map.put("poin_fitur", pointFitur);

            //Gudang produk
            map.put("alamat_gudang", gudang.getAlamat());
            map.put("provinsi_gudang", provinsiGudang.getProvinceName());
            map.put("kota_gudang", kotaGudang.getCityName());
            map.put("kecamatan_gudang", kecamatanGudang.getDistrictName());

            // Data order
            map.put("kode_order", order.getOrderCode());
            map.put("status_order", order.getStatus());
            map.put("tanggal_order", order.getCreatedAt());
            map.put("tanggal_paid", order.getPaidAt());
            map.put("ongkir", formatRupiah(order.getOngkosKirim()));
            var diskon = order.getDiskon() != null ? order.getDiskon() : 0;
            map.put("diskon", formatRupiah(diskon));
            var total = atributProduk.getHarga() + order.getOngkosKirim() -diskon;
            map.put("total", formatRupiah(total));

            // Address (optional) customer
            map.put("alamat_customer", order.getAlamat());
            map.put("provinsi_customer", provinsiCust.getProvinceName());
            map.put("kota_customer", kotaCust.getCityName());
            map.put("kecamatan_customer", kecamatanCust.getDistrictName());


            // Payment
            map.put("metode_pembayaran", order.getPembayaran());
            if(order.getPembayaran().equalsIgnoreCase("COD")){
                map.put("deskripsi_transfer_bank", "");
            }else {
                var config = produkPembayaranRepository.findByIdProdukAndPembayaran( order.getIdProduk(), order.getPembayaran());
                if (config != null  && config.getConfig() != null && order.getConfigPembayaran().get("deskripsi") != null){
                    map.put("deskripsi_transfer_bank", order.getConfigPembayaran().get("deskripsi").toString());
                }else {
                    map.put("deskripsi_transfer_bank", "");
                }

            }
        }

        return map;
    }

    public String getOrderSystemInfo(UUID idConversation) {

        List<UUID> orderId = orderRepository.getOrderIdsByIdConversation(idConversation);
        String template = """
        Pesanan - {kode_order}
            Nama Penerima: {nama_customer}
            Produk: {nama_produk}
            Deskripsi produk: {poin_fitur}
            Variasi: {atribut_produk}
            Harga: {harga_produk}
            Status: {status_order}
            Ongkir: {ongkir}
            Diskon: {diskon}
            Total: {total}
            Dikirim ke:
              Alamat: {alamat_customer}
              Provinsi: {provinsi_customer}
              Kota: {kota_customer}
              Kecamatan: {kecamatan_customer}
              Metode Pembayaran: {metode_pembayaran}
              Deskripsi Transfer Bank: {deskripsi_transfer_bank}
        
            Dikirim dari gudang:
              Alamat: {alamat_gudang}
              Provinsi: {provinsi_gudang}
              Kota: {kota_gudang}
              Kecamatan: {kecamatan_gudang}
        
        """;

        String result = "DATA SISTEM: \n";
        if (orderId != null && orderId.size() > 0) {
            for (UUID id : orderId) {
                var param = buildOrderParams(id);
                result = result + fillTemplate(template, param);
            }
        }else {
            return null;
        }
        return result;
    }

    private String formatRupiah(long amount) {
        return "Rp. " + String.format("%,d", amount).replace(",", ".");
    }





}
