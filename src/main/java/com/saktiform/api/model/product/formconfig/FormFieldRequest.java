package com.saktiform.api.model.product.formconfig;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

/**
 * Satu entri definisi field. Dipakai pada dua jalur:
 * {@code POST /produk} (bersamaan dengan pembuatan/pembaruan produk) dan
 * {@code PUT /produk/{id}/form-config}.
 *
 * <p>{@code fieldKey} kosong berarti field baru — server yang membangkitkannya melalui
 * slugify atas {@code label}. Untuk kategori {@code SYSTEM}, atribut terkunci
 * ({@code fieldType}, {@code isRequired}, {@code isActive}, {@code options},
 * {@code defaultValue}) boleh dikirim selama nilainya sama dengan yang tersimpan;
 * nilai yang berbeda ditolak.
 *
 * <p><b>Kompatibilitas payload lama.</b> Atribut {@code tipeField}, {@code order}, dan
 * {@code isMandatory} diterima sebagai alias. {@code fieldCategory} boleh dikosongkan —
 * pada jalur {@code POST /produk} kategorinya disimpulkan dari kecocokan label terhadap
 * field yang sudah ada.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FormFieldRequest implements Serializable {

    @Size(max = 64, message = "Field key maksimum 64 karakter.")
    private String fieldKey;

    /**
     * Opsional pada {@code POST /produk}; wajib pada {@code PUT .../form-config}
     * (ditegakkan validator, bukan anotasi, karena aturannya berbeda per jalur).
     */
    private FieldCategory fieldCategory;

    /** Wajib untuk kategori CUSTOM; diabaikan/diverifikasi untuk SYSTEM. */
    @JsonAlias({"tipeField", "tipe_field"})
    private FormFieldType fieldType;

    @NotBlank(message = "Label field wajib diisi.")
    @Size(max = 150, message = "Label maksimum 150 karakter.")
    private String label;

    @Size(max = 200, message = "Placeholder maksimum 200 karakter.")
    private String placeholder;

    @Size(max = 300, message = "Help text maksimum 300 karakter.")
    private String helpText;

    @JsonAlias({"isMandatory", "is_mandatory"})
    private Boolean isRequired;

    @JsonAlias({"isActive", "is_active"})
    private Boolean isActive;

    @Size(max = 500, message = "Nilai bawaan maksimum 500 karakter.")
    private String defaultValue;

    @Valid
    private List<OptionDto> options;

    @Valid
    private ValidationRuleDto validation;

    @JsonAlias({"order", "orders"})
    @Min(value = 1, message = "Urutan tampil minimal 1.")
    @Max(value = 999, message = "Urutan tampil maksimal 999.")
    private Integer sortOrder;
}
