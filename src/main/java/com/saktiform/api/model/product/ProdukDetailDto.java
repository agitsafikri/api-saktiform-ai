package com.saktiform.api.model.product;

import com.saktiform.api.model.product.formconfig.FormFieldConfigDto;
import lombok.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProdukDetailDto implements Serializable {
    UUID id;
    String namaProduk;
    String urlCheckout;
    List<String> gambarProduk = new ArrayList<String>();
    List<String> poinFitur = new ArrayList<>();
    List<AtributProdukDto> atributProduk = new ArrayList<AtributProdukDto>();
    List<PembayaranDto> pembayaran = new ArrayList<>();
    GudangDto gudang;
    List<FormFieldConfigDto> formConfig = new ArrayList<>();
    List<ProdukEkstraDto> ekstra = new ArrayList<>();
    String narasiTombol;
    List<ProdukTestimoniDto> testimoni = new ArrayList<>();
    String idFacebookPixelId;
    String idGoogleGtmId;
    String embededCheckoutScript;
    String embededPurchaseScript;

    /** Sembunyikan seluruh label field pada form checkout. */
    Boolean hideFormLabel;

    /** Sembunyikan tampilan harga pada halaman checkout. */
    Boolean hidePrice;
}
