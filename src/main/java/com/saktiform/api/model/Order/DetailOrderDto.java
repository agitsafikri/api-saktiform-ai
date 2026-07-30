package com.saktiform.api.model.Order;

import com.saktiform.api.model.location.CityDto;
import com.saktiform.api.model.location.DistrictDto;
import com.saktiform.api.model.location.ProvinceDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DetailOrderDto {
    UUID id;
    UUID idProduk;
    String namaProduk;
    AttributProdukOrder atributProduk = new AttributProdukOrder();
    Long diskon;
    Long ongkir;
    String metodePembayaran;
    String namaPenerima;
    String nomorWhatsapp;
    String alamat;
    ProvinceDto provinsi;
    CityDto kota;
    DistrictDto kecamatan;
    String status;
    String  tanggalOrder;
    String handleBy;
    String notes;

    /**
     * Nilai Custom Field beserta label hasil snapshot. Larik kosong (bukan {@code null})
     * bila order tidak memiliki Custom Field, sehingga klien tidak perlu null-check.
     */
    List<OrderCustomFieldDto> customFields = new ArrayList<>();
}
