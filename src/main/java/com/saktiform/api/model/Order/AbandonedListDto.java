package com.saktiform.api.model.Order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AbandonedListDto {
    UUID id;
    String namaCustomer;
    String nomorWhatsapp;
    String namaProduk;
    String alamat;
    String provinsi;
    String kota;
    String kecamatan;
}
