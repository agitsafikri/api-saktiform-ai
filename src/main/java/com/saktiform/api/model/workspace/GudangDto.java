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
    @NonNull
    String namaGudang;
    @NonNull
    String alamat;
    @NonNull
    Integer idProvinsi;
    @NonNull
    Integer idKota;
    @NonNull
    Integer idKecamatan;
}