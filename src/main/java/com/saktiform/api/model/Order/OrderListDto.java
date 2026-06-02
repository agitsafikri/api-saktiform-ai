package com.saktiform.api.model.Order;
import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.print.DocFlavor;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public interface OrderListDto {
    UUID getId();
    String getOrderCode();
    String getNamaCustomer();
    String getNomorWhatsapp();
    String getNamaProduk();
    String getProvinsi();
    String getStatus();
    String getNotes();
    default String getTanggalOrder(){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return getTanggalOrderRaw().atZone(ZoneId.of("Asia/Jakarta")).format(formatter);
    };
    @JsonIgnore
    Instant getTanggalOrderRaw();
    default String getPaidAt(){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (getPaidAtRaw() == null) return "";
        return  getPaidAtRaw().atZone(ZoneId.of("Asia/Jakarta")).format(formatter);
    };
    @JsonIgnore
    Instant getPaidAtRaw();
    String getJenisPembayaran();
    Boolean getStatusEkspor();
}

