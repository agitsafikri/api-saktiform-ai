package com.saktiform.api.model.product.formconfig;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

/**
 * Representasi kaya sebuah field untuk layar konfigurasi dashboard
 * ({@code GET}/{@code PUT /produk/{id}/form-config}).
 *
 * <p>{@code editableAttributes} dan {@code deletable} adalah <b>kontrak izin</b>:
 * frontend menonaktifkan kontrol berdasarkan keduanya dan dilarang menyimpulkan sendiri
 * dari {@code fieldCategory}. Aturan mana yang terkunci adalah milik backend dan dapat
 * berkembang tanpa memutus klien.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FormFieldConfigDto implements Serializable {

    private String fieldKey;
    private FieldCategory fieldCategory;
    private FormFieldType fieldType;
    private String label;
    private String placeholder;
    private String helpText;
    private Boolean isRequired;
    private Boolean isActive;
    private String defaultValue;
    private List<OptionDto> options;
    private Integer sortOrder;
    private ValidationRuleDto validation;
    private String dataSource;

    /** Jumlah order yang memakai field ini. {@code null} bila agregasi gagal dihitung. */
    private Long usageCount;

    /** Atribut yang boleh diubah untuk field ini. */
    private List<String> editableAttributes;

    /** {@code false} untuk System Field dan untuk Custom Field yang sudah dipakai order. */
    private Boolean deletable;

    // ── Alias kompatibilitas untuk dashboard versi lama. ──
    // Getter turunan (bukan atribut tersimpan) sehingga tidak ada state ganda.
    // Sebelum fitur ini, `GET /produk/{id}` mengembalikan ProdukFormConfigDto dengan
    // nama `tipeField`, `order`, dan `isMandatory`. Tanpa alias ini, seksi konfigurasi
    // form pada dashboard membaca `undefined` dan tampak seolah konfigurasinya hilang.
    // Klien baru WAJIB memakai fieldType / sortOrder / isRequired.
    // Dijadwalkan dihapus berbarengan dengan alias pada FormFieldCheckoutDto.

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
