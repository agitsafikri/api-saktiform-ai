package com.saktiform.api.model.product;

import lombok.Value;

import java.io.Serializable;
import java.util.UUID;

/**
 * DTO for {@link com.saktiform.api.entity.AtributProduk}
 */
@Value
public class AtributProdukDto implements Serializable {
    UUID id;
    String deskripsi;
    Long harga;
    Integer berat;
}