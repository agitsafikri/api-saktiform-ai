package com.saktiform.api.model.workspace;

import lombok.*;

import java.io.Serializable;

/**
 * DTO for {@link Gudang}
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GudangDto implements Serializable {
    String namaGudang;
    String alamat;
    Integer idProvinsi;
    Integer idKota;
    Integer idKecamatan;
}