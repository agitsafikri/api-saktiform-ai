package com.saktiform.api.model.product;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link ProdukTestimoni}
 */
@Data
@AllArgsConstructor
@Value
public class ProdukTestimoniDto implements Serializable {
    String nama;
    String pesan;
    String urlGambar;
}