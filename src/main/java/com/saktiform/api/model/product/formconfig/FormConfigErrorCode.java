package com.saktiform.api.model.product.formconfig;

/**
 * Kode galat fitur Konfigurasi Form Produk.
 *
 * <p>Kode bersifat <b>stabil</b> dan merupakan bagian dari kontrak API — frontend
 * bercabang atas kode ini, bukan atas teks pesan. Mengubah nilainya = perubahan
 * kontrak yang memutus klien.
 */
public final class FormConfigErrorCode {

    // ── Konfigurasi form (PUT /produk/{id}/form-config) ──
    public static final String FIELDS_REQUIRED = "FIELDS_REQUIRED";
    public static final String SYSTEM_FIELD_NOT_DELETABLE = "SYSTEM_FIELD_NOT_DELETABLE";
    public static final String SYSTEM_FIELD_IMMUTABLE_ATTRIBUTE = "SYSTEM_FIELD_IMMUTABLE_ATTRIBUTE";
    public static final String UNKNOWN_SYSTEM_FIELD = "UNKNOWN_SYSTEM_FIELD";
    public static final String INVALID_FIELD_CATEGORY = "INVALID_FIELD_CATEGORY";
    public static final String INVALID_FIELD_KEY_FORMAT = "INVALID_FIELD_KEY_FORMAT";
    public static final String DUPLICATE_FIELD_KEY = "DUPLICATE_FIELD_KEY";
    public static final String LABEL_REQUIRED = "LABEL_REQUIRED";
    public static final String LABEL_TOO_LONG = "LABEL_TOO_LONG";
    public static final String INVALID_FIELD_TYPE = "INVALID_FIELD_TYPE";
    public static final String FIELD_TYPE_RESERVED_FOR_SYSTEM = "FIELD_TYPE_RESERVED_FOR_SYSTEM";
    public static final String OPTIONS_REQUIRED_FOR_TYPE = "OPTIONS_REQUIRED_FOR_TYPE";
    public static final String OPTIONS_NOT_ALLOWED_FOR_TYPE = "OPTIONS_NOT_ALLOWED_FOR_TYPE";
    public static final String TOO_MANY_OPTIONS = "TOO_MANY_OPTIONS";
    public static final String OPTION_INCOMPLETE = "OPTION_INCOMPLETE";
    public static final String OPTION_TOO_LONG = "OPTION_TOO_LONG";
    public static final String DUPLICATE_OPTION_VALUE = "DUPLICATE_OPTION_VALUE";
    public static final String INVALID_DEFAULT_VALUE = "INVALID_DEFAULT_VALUE";
    public static final String INVALID_VALIDATION_RULE = "INVALID_VALIDATION_RULE";
    public static final String INVALID_RANGE = "INVALID_RANGE";
    public static final String CUSTOM_FIELD_LIMIT_EXCEEDED = "CUSTOM_FIELD_LIMIT_EXCEEDED";
    public static final String FIELD_IN_USE = "FIELD_IN_USE";

    // ── Submit order (POST /order/create) ──
    public static final String REQUIRED_FIELD_MISSING = "REQUIRED_FIELD_MISSING";
    public static final String INVALID_VALUE_TYPE = "INVALID_VALUE_TYPE";
    public static final String VALUE_NOT_IN_OPTIONS = "VALUE_NOT_IN_OPTIONS";
    public static final String VALUE_RULE_VIOLATION = "VALUE_RULE_VIOLATION";
    public static final String SYSTEM_FIELD_IN_CUSTOM_PAYLOAD = "SYSTEM_FIELD_IN_CUSTOM_PAYLOAD";
    public static final String SHIPPING_RATE_NOT_FOUND = "SHIPPING_RATE_NOT_FOUND";

    private FormConfigErrorCode() {
    }
}
