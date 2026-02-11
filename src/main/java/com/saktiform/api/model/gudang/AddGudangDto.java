package com.saktiform.api.model.gudang;

import com.saktiform.api.entity.Gudang;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link Gudang}
 */
@Value
public class AddGudangDto implements Serializable {
    Long id;
    @NotNull
    String namaGudang;
    @NotNull
    String alamat;
    @NotNull
    Integer idProvinsi;
    @NotNull
    Integer idKota;
    @NotNull
    Integer idKecamatan;
    @NotNull
    Long idWorkspace;
}