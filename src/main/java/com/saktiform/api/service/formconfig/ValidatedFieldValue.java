package com.saktiform.api.service.formconfig;

import com.saktiform.api.entity.ProdukFormConfig;
import com.saktiform.api.model.product.formconfig.FormFieldType;

/**
 * Nilai Custom Field yang sudah tervalidasi dan ternormalisasi, siap di-snapshot.
 *
 * <p>Membawa {@code fieldLabel}, {@code fieldType}, dan {@code sortOrder} yang diambil
 * dari konfigurasi <b>pada saat validasi</b> — bukan pada saat penyimpanan. Jarak waktu
 * keduanya sangat kecil, namun mengambilnya sekali di titik validasi menjamin nilai yang
 * tersimpan konsisten dengan nilai yang divalidasi.
 */
public class ValidatedFieldValue {

    private final String fieldKey;
    private final String fieldLabel;
    private final FormFieldType fieldType;
    private final String textValue;
    private final Integer sortOrder;

    private ValidatedFieldValue(String fieldKey, String fieldLabel, FormFieldType fieldType,
                                String textValue, Integer sortOrder) {
        this.fieldKey = fieldKey;
        this.fieldLabel = fieldLabel;
        this.fieldType = fieldType;
        this.textValue = textValue;
        this.sortOrder = sortOrder;
    }

    public static ValidatedFieldValue of(ProdukFormConfig config, String value) {
        return new ValidatedFieldValue(
                config.getFieldKey(),
                config.getLabel(),
                config.getFieldType(),
                value,
                config.getSortOrder() == null ? 999 : config.getSortOrder());
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public String getFieldLabel() {
        return fieldLabel;
    }

    public FormFieldType getFieldType() {
        return fieldType;
    }

    public String getTextValue() {
        return textValue;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }
}
