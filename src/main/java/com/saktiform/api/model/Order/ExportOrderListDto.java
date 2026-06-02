package com.saktiform.api.model.Order;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    default String getTanggalOrder(){
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        return formatter.format(
                getTanggalOrderRaw()
                        .atZone(ZoneId.of("UTC"))
                        .withZoneSameInstant(ZoneId.of("Asia/Jakarta"))
        );
    };
    @JsonIgnore
    Instant getTanggalOrderRaw();
    default String getPaidAt(){
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        if (getPaidAtRaw() == null) return "";
        return formatter.format(
                getPaidAtRaw()
                        .atZone(ZoneId.of("UTC"))
                        .withZoneSameInstant(ZoneId.of("Asia/Jakarta"))
        );
    };
    @JsonIgnore
    Instant getPaidAtRaw();
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

