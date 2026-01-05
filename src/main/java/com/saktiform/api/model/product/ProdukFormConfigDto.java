package com.saktiform.api.model.product;

import com.saktiform.api.entity.ProdukFormConfig;
import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link ProdukFormConfig}
 */
@Value
public class ProdukFormConfigDto implements Serializable {
    String tipeField;
    String label;
    String placeholder;
    Integer order;
    Boolean isMandatory;
}