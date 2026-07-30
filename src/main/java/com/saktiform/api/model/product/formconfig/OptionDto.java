package com.saktiform.api.model.product.formconfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Satu pilihan pada field bertipe {@code SELECT}.
 *
 * <p>Disimpan sebagai elemen larik pada kolom {@code produk_form_config.options} ({@code jsonb}).
 * Wajib memiliki konstruktor tanpa argumen agar dapat dideserialisasi Jackson.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OptionDto implements Serializable {

    @NotBlank(message = "Label pilihan wajib diisi.")
    @Size(max = 100, message = "Label pilihan maksimum 100 karakter.")
    private String label;

    @NotBlank(message = "Nilai pilihan wajib diisi.")
    @Size(max = 100, message = "Nilai pilihan maksimum 100 karakter.")
    private String value;
}
