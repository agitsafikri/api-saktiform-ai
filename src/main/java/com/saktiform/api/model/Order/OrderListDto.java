package com.saktiform.api.model.Order;
import java.util.UUID;

public interface OrderListDto {
    UUID getId();
    String getNamaCustomer();
    String getNomorWhatsapp();
    String getNamaProduk();
    String getProvinsi();
    String getStatus();
    String getNotes();
    String getTanggalOrder();
    String getPaidAt();
    String getJenisPembayaran();
    Boolean getStatusEkspor();
}

