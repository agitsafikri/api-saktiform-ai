package com.saktiform.api.model.chat;

import com.saktiform.api.model.OrderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatAddOrderRequest {
    @NotNull
    UUID idProduk;
    @NotNull
    UUID idAtributProduk;
    @NotBlank
    @NotNull
    String namaLengkap;
    @NotBlank
    @NotNull
    String nomorWhatsapp;
    @NotBlank
    @NotNull
    String alamat;
    @NotNull
    Integer idProvinsi;
    @NotNull
    Integer idKota;
    @NotNull
    Integer idKecamatan;
    @NotBlank
    @NotNull
    String metodePembayaran;
    @NotNull
    OrderStatus status;
    String notes;
    Long diskon;
    @NotNull
    String source;
}
