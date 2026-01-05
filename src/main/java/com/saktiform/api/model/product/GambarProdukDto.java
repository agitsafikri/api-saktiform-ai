package com.saktiform.api.model.product;

import com.saktiform.api.entity.GambarProduk;
import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link GambarProduk}
 */
@Value
public class GambarProdukDto implements Serializable {
    Long id;
    String urlGambar;
}