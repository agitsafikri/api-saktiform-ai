package com.saktiform.api.model.product;

import com.saktiform.api.validators.NoSpace;
import com.saktiform.api.validators.UniqueProductName;
import com.saktiform.api.validators.UniqueProductUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @NotNull(message = "Form Config Wajib Diisi.")
    List<ProdukFormConfigDto> formConfig;
    List<ProdukEkstraDto> ekstra;
    @NotNull(message = "Narasi Tombol Wajib Diisi.")
    String narasiTombol;
    List<ProdukTestimoniDto> testimoni = new ArrayList<>();
    String facebookPixelId;
    String googleGtmId;
    String embededCheckoutScript;
    String embededPurchaseScript;
}
