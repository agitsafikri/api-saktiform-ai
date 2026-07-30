package com.saktiform.api.model.product.formconfig;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Response {@code PUT /produk/{id}/form-config}.
 *
 * <p>Menyertakan daftar {@code fields} lengkap hasil penyimpanan — termasuk
 * {@code fieldKey} yang baru dibangkitkan server dan {@code sortOrder} yang sudah
 * dinormalkan 1..N — sehingga frontend dapat langsung menyinkronkan state-nya tanpa
 * memanggil {@code GET} ulang.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FormConfigSaveResponse implements Serializable {

    private UUID idProduk;
    private Integer totalField;
    private List<String> created = new ArrayList<>();
    private List<String> updated = new ArrayList<>();
    private List<String> deleted = new ArrayList<>();
    private List<FormFieldConfigDto> fields = new ArrayList<>();
}
