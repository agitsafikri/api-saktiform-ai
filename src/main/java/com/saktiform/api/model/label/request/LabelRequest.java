package com.saktiform.api.model.label.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request create/update master label. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LabelRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String colorHex;     // #RRGGBB (divalidasi & dinormalisasi di service)
}
