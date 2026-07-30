package com.saktiform.api.model.Order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Payload pembuatan order.
 *
 * <p>Nama atribut System Field sengaja <b>tidak</b> diubah menjadi {@code field_key}.
 * Mengubahnya akan memutus seluruh klien checkout yang sedang berjalan tanpa manfaat
 * fungsional: {@code field_key} berperan sebagai kontrak konfigurasi dan render,
 * bukan kontrak payload submit.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderDto {
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
    String source;

    /**
     * Nilai Custom Field. Opsional — klien lama yang tidak mengenal fitur ini tetap
     * berfungsi. Divalidasi terhadap konfigurasi aktif produk pada saat submit.
     */
    @Valid
    @Size(max = 56, message = "Jumlah field tambahan maksimum 56.")
    List<CustomFieldValueDto> customFields;
}
