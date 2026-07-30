package com.saktiform.api.model.product;

import com.saktiform.api.model.product.formconfig.FormFieldCheckoutDto;
import jakarta.validation.constraints.NotNull;
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
public class ProdukCheckoutDto {
    UUID id;
    String namaProduk;
    List<String> gambarProduk = new ArrayList<String>();
    List<String> poinFitur = new ArrayList<>();
    List<AtributProdukDto> atributProduk = new ArrayList<AtributProdukDto>();
    List<FormFieldCheckoutDto> formConfig = new ArrayList<>();
    List<ProdukEkstraDto> ekstra = new ArrayList<>();
    String narasiTombol;
    List<ProdukTestimoniDto> testimoni = new ArrayList<>();
    List<String> metodePembayaran = new ArrayList<>();
    String idFacebookPixelId;
    String idGoogleGtmId;
    String embededCheckoutScript;
    String embededPurchaseScript;

    /**
     * Sembunyikan seluruh label field pada form. Renderer tetap WAJIB memasang
     * {@code aria-label} agar form tetap dapat diakses pembaca layar.
     */
    Boolean hideFormLabel;

    /** Sembunyikan tampilan harga. Tidak memengaruhi perhitungan apa pun. */
    Boolean hidePrice;
}
