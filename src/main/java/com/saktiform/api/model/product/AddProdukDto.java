package com.saktiform.api.model.product;

import com.saktiform.api.model.product.formconfig.FormFieldRequest;
import com.saktiform.api.validators.NoSpace;
import com.saktiform.api.validators.UniqueProductName;
import com.saktiform.api.validators.UniqueProductUrl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Setter;
import lombok.Value;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Value
@UniqueProductName
@UniqueProductUrl
@Setter
public class AddProdukDto implements Serializable {
    UUID id;
    @NotNull(message = "Workspace ID is required.")
    Long idWorkspace;
    @NotBlank(message = "Nama Produk Wajib Diisi.")
    String namaProduk;
    @NoSpace(message = "URL Checkout tidak boleh mengandung spasi.")
    @NotBlank(message = "URL Checkout Wajib Diisi.")
    String urlCheckout;
    List<String> gambarProduk = new ArrayList<>();
    @NotNull(message = "Poin Fitur Wajib Diisi.")
    List<String> poinFitur = new ArrayList<>();
    @NotNull(message = "Atribut Produk Wajib Diisi.")
    List<AtributProdukDto> atributProduk = new ArrayList<>();
    @NotNull(message = "Pembayaran Wajib Diisi.")
    List<PembayaranDto> pembayaran = new ArrayList<>();
    @NotNull(message = "Gudang ID is required.")
    Long idGudang;
    /**
     * Konfigurasi form checkout, dikirim bersamaan dengan pembuatan/pembaruan produk.
     *
     * <p>Opsional. Enam System Field selalu di-seed sistem, sehingga klien cukup
     * mengirim Custom Field yang diperlukan — atau daftar lengkap bila ingin menentukan
     * label dan urutan tampil sekaligus.
     *
     * <p>Bersifat <b>merge</b>: entri yang cocok diperbarui, yang belum ada dibuat, dan
     * field yang tidak disebut tetap utuh. Penghapusan field hanya melalui
     * {@code PUT /produk/{id}/form-config}.
     */
    @Valid
    @Size(max = 56, message = "Jumlah field maksimum 56.")
    List<FormFieldRequest> formConfig;
    List<ProdukEkstraDto> ekstra;
    @NotNull(message = "Narasi Tombol Wajib Diisi.")
    String narasiTombol;
    List<ProdukTestimoniDto> testimoni = new ArrayList<>();
    String facebookPixelId;
    String googleGtmId;
    String embededCheckoutScript;
    String embededPurchaseScript;

    // Setelan tampilan checkout. Sengaja ditempatkan di akhir agar urutan argumen
    // konstruktor (Lombok @Value) yang sudah dipakai pemanggil lama tidak bergeser.
    /** Sembunyikan seluruh label field pada form checkout. Default {@code false}. */
    Boolean hideFormLabel;
    /** Sembunyikan tampilan harga pada halaman checkout. Default {@code false}. */
    Boolean hidePrice;
}
