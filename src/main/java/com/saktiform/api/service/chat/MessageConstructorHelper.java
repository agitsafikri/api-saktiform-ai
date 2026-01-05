package com.saktiform.api.service.chat;


import com.saktiform.api.entity.Order;
import com.saktiform.api.repository.OrderRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MessageConstructorHelper {
    private final OrderRepository orderRepository;

    public MessageConstructorHelper(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }


    public String createFollowupMessage() {
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
                Alamat: {alamat}
                Kota: {kota}
                Kecamatan: {kecamatan}
                
                kakak bisa menyiapkan {total} dan pastikan untuk nomor telpon dan alamatnya sudah benar ya kak?
                
                Paketnya sudah saya bungkusin ya kak tinggal ditempel resi milik kaka dan saya antar ke pihak ekspedisi hari ini🥰🙏🏻 Kaka bisa terima paket dr kurir kira2 di jam berapa ? Pagi siang atau sore kak? 🙏☺
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

            // Data produk
            map.put("nama_produk", order.getProduk().getNamaProduk());
            map.put("atribut_produk", order.getAtributProduk().getDeskripsi());
            map.put("harga_produk", order.getAtributProduk().getHarga());
            map.put("berat_produk", order.getAtributProduk().getBerat() + " gram");

            // Data order
            map.put("kode_order", order.getOrderCode());
            map.put("status_order", order.getStatus());
            map.put("tanggal_order", order.getCreatedAt());
            map.put("tanggal_paid", order.getPaidAt());
            map.put("ongkir", order.getOngkosKirim());
            var diskon = order.getDiskon() != null ? order.getDiskon() : 0;
            map.put("diskon", diskon);
            var total = order.getAtributProduk().getHarga() + order.getOngkosKirim() -diskon;
            map.put("total", total);

            // Address (optional)
            map.put("alamat", order.getAlamat());
            map.put("provinsi", order.getProvinsi().getProvinceName());
            map.put("kota", order.getKota().getCityName());
            map.put("kecamatan", order.getKecamatan().getDistrictName());


            // Payment
            map.put("metode_pembayaran", order.getPembayaran());
        }

        return map;
    }





}
