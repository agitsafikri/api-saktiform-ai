package com.saktiform.api.model.product;

import com.saktiform.api.entity.ProdukPembayaran;
import lombok.Value;

import java.io.Serializable;
import java.util.Map;

/**
 * DTO for {@link ProdukPembayaran}
 */
@Value
public class PembayaranDto implements Serializable {
    String tipe;
    Map<String, Object> config;
}