package com.saktiform.api.model.product.formconfig;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

/**
 * Payload {@code PUT /produk/{id}/form-config}.
 *
 * <p>Semantik <b>full replace dengan upsert by field_key</b>: daftar {@code fields}
 * merepresentasikan keadaan akhir yang dikehendaki. Baris existing yang tidak muncul
 * pada daftar dianggap permintaan penghapusan dan diproses menurut aturan
 * "System Field tidak dapat dihapus" serta "Custom Field yang sudah dipakai tidak
 * dapat dihapus permanen".
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FormConfigRequest implements Serializable {

    @NotEmpty(message = "Daftar field tidak boleh kosong.")
    @Size(max = 56, message = "Jumlah field maksimum 56.")
    @Valid
    private List<FormFieldRequest> fields;
}
