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
public class AbandonedOrderDetailDto {
    UUID id;
    UUID idProduk;
    String namaCustomer;
    String nomorWhatsapp;
    String namaProduk;
    String alamat;
    Provinsi provinsi;
    Kota kota;
    Kecamatan kecamatan;
    String metodePembayaran;

    public AbandonedOrderDetailDto(UUID id, String namaCustomer, String nomorWhatsapp, UUID idProduk, String namaProduk, String alamat, Integer idProvinsi, String provinsi, Integer idKota, String kota, Integer idKecamatan, String kecamatan, String metodePembayaran){
        this.id = id;
        this.idProduk = idProduk;
        this.namaCustomer = namaCustomer;
        this.nomorWhatsapp = nomorWhatsapp;
        this.namaProduk = namaProduk;
        this.alamat = alamat;
        this.provinsi = provinsi != null ? new Provinsi(idProvinsi, provinsi) : null;
        this.kota = kota != null ? new Kota(idKota, kota) : null;
        this.kecamatan = kecamatan != null ? new Kecamatan(idKecamatan, kecamatan) : null;
        this.metodePembayaran = metodePembayaran;
    }

    @AllArgsConstructor
    @Getter
    private class Provinsi {
        Integer id;
        String nama;
    }

    @AllArgsConstructor
    @Getter
    private class Kota {
        Integer id;
        String nama;
    }

    @AllArgsConstructor
    @Getter
    private class Kecamatan {
        Integer id;
        String nama;
    }
}


