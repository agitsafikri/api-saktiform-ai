package com.saktiform.api.model.product.formconfig;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

/**
 * Representasi field untuk halaman checkout publik ({@code GET /produk/checkout}).
 *
 * <p>Sengaja tidak memuat {@code id} internal maupun atribut administratif
 * ({@code usageCount}, {@code editableAttributes}, timestamp) — endpoint ini publik.
 * Field yang dikirim sudah tersaring {@code isActive = true} dan terurut menurut
 * {@code sortOrder}, sehingga klien cukup merender sesuai urutan larik.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FormFieldCheckoutDto implements Serializable {

    private String fieldKey;
    private FieldCategory fieldCategory;
    private FormFieldType fieldType;
    private String label;
    private String placeholder;
    private String helpText;
    private Boolean isRequired;
    private String defaultValue;
    private List<OptionDto> options;
    private Integer sortOrder;
    private ValidationRuleDto validation;
    private String dataSource;

    // ── Alias kompatibilitas untuk klien checkout versi lama. ──
    // Getter turunan (bukan atribut tersimpan) sehingga tidak ada state ganda.
    // Klien baru WAJIB memakai fieldType / sortOrder / isRequired.
    // Dijadwalkan dihapus setelah seluruh klien bermigrasi.

    public String getTipeField() {
        return fieldType == null ? null : fieldType.name();
    }

    public Integer getOrder() {
        return sortOrder;
    }

    public Boolean getIsMandatory() {
        return isRequired;
    }
}
