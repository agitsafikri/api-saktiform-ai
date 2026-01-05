package com.saktiform.api.model.Order;
import java.math.BigInteger;
import java.util.UUID;

public interface ExportOrderListDto {
    UUID getId();
    String getOrderCode();
    String getNamaCustomer();
    String getNomorWhatsapp();
    String getNamaProduk();
    String getProvinsi();
    String getStatus();
    String getNotes();
    String getTanggalOrder();
    String getPaidAt();
    String getJenisPembayaran();
    String getAlamat();
    String getKota();
    String getKecamatan();
    BigInteger getDiskon();
    BigInteger getOngkir();
    BigInteger getHarga();
    String getHandleBy();
    String getKodeProduk();
    String getVariation();
    BigInteger getBerat();

}

