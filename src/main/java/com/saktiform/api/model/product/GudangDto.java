package com.saktiform.api.model.product;

import com.saktiform.api.entity.Gudang;
import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link Gudang}
 */
@Value
public class GudangDto implements Serializable {
    Long id;
    String namaGudang;
    String alamat;
    Integer idProvinsi;
    Integer idKota;
    Integer idKecamatan;
}