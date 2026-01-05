package com.saktiform.api.entity;

import lombok.Value;

import java.io.Serializable;
import java.util.Map;

/**
 * DTO for {@link ProdukPembayaran}
 */
@Value
public class ProdukPembayaranDto implements Serializable {
    String pembayaran;
    Map<String, Object> config;
}