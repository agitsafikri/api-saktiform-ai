package com.saktiform.api.model.product;

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
    List<ProdukFormConfigDto> formConfig = new ArrayList<>();
    List<ProdukEkstraDto> ekstra = new ArrayList<>();
    String narasiTombol;
    List<ProdukTestimoniDto> testimoni = new ArrayList<>();
    List<String> metodePembayaran = new ArrayList<>();
}
