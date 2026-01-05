package com.saktiform.api.model.Order;

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
public class UpdateOrderDto {
    @NotNull
    UUID id;
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
    Integer idKota;
    @NotNull
    Integer idProvinsi;
    @NotNull
    Integer idKecamatan;
    @NotBlank
    @NotNull
    String metodePembayaran;
    String notes;
    Long diskon;
    @NotNull
    OrderStatus status;
}
