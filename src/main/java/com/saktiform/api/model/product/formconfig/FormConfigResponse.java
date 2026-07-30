package com.saktiform.api.model.product.formconfig;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Response {@code GET /produk/{id}/form-config}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FormConfigResponse implements Serializable {

    private UUID idProduk;
    private String namaProduk;
    private Integer totalField;
    private Integer totalCustomFieldActive;
    private Integer customFieldLimit;
    private List<FormFieldConfigDto> fields = new ArrayList<>();
}
