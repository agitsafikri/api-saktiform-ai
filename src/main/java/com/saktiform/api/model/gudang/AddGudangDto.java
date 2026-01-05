package com.saktiform.api.model.gudang;

import com.saktiform.api.entity.Gudang;
import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link Gudang}
 */
@Value
public class AddGudangDto implements Serializable {
    Long id;
    String namaGudang;
    String alamat;
    Integer provinsi;
    Integer kota;
    Integer kecamatan;
    Long idWorkspace;
}