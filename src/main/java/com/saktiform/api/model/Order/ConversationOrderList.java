package com.saktiform.api.model.Order;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Getter
@Setter
public class ConversationOrderList {
    UUID id;
    String tanggalOrder;
    String namaProduk;
    String variasiProduk;
    String status;

    private  static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public ConversationOrderList(UUID id, LocalDateTime tanggalOrder, String namaProduk, String variasiProduk, String status) {
        this.id = id;
        this.tanggalOrder = tanggalOrder.atZone(ZoneId.of("Asia/Jakarta"))
                .format(formatter);
        this.namaProduk = namaProduk;
        this.variasiProduk = variasiProduk;
        this.status = status;
    }
}
