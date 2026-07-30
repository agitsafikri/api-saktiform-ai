package com.saktiform.api.model.product.formconfig;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Tipe field pada konfigurasi form produk.
 *
 * <p>Custom Field hanya boleh memakai tiga tipe: {@link #TEXT}, {@link #TEXTAREA},
 * dan {@link #SELECT}. Empat tipe lainnya bersifat {@code systemOnly} — dipakai oleh
 * System Field agar frontend dapat memutuskan cara render berdasarkan <b>tipe</b>,
 * bukan berdasarkan {@code fieldKey}.
 */
public enum FormFieldType {

    /** Input teks satu baris. */
    TEXT(false, false),
    /** Input teks banyak baris. */
    TEXTAREA(false, false),
    /** Dropdown pilihan; wajib memiliki {@code options}. */
    SELECT(true, false),

    /** Nomor telepon — dinormalisasi server. Khusus System Field. */
    PHONE(false, true),
    /** Dropdown provinsi dari master lokasi. Khusus System Field. */
    PROVINCE(false, true),
    /** Dropdown kota, cascading dari provinsi. Khusus System Field. */
    CITY(false, true),
    /** Dropdown kecamatan, cascading dari kota. Khusus System Field. */
    DISTRICT(false, true);

    private final boolean requiresOptions;
    private final boolean systemOnly;

    FormFieldType(boolean requiresOptions, boolean systemOnly) {
        this.requiresOptions = requiresOptions;
        this.systemOnly = systemOnly;
    }

    /** {@code true} bila tipe ini wajib memiliki daftar {@code options}. */
    public boolean isRequiresOptions() {
        return requiresOptions;
    }

    /** {@code true} bila tipe ini hanya boleh dipakai System Field. */
    public boolean isSystemOnly() {
        return systemOnly;
    }

    /** Batas maksimum jumlah options untuk tipe ini. 0 bila tipe tidak memakai options. */
    public int getMaxOptions() {
        return this == SELECT ? 100 : 0;
    }

    /** Batas panjang nilai yang diterima saat submit order. */
    public int getMaxValueLength() {
        return switch (this) {
            case TEXTAREA -> 2000;
            case TEXT, SELECT -> 500;
            default -> 500;
        };
    }

    /**
     * Parsing toleran untuk nilai legacy pada kolom {@code tipe_field} yang belum
     * ternormalisasi (mis. {@code "text"}, {@code "dropdown"}).
     *
     * <p>Tipe yang sudah dihapus dari cakupan fitur (number, email, radio, checkbox,
     * date, file) dipetakan ke padanan terdekat yang masih didukung agar data legacy
     * tidak hilang: pilihan ganda menjadi {@link #SELECT}, sisanya menjadi {@link #TEXT}.
     *
     * @return {@code null} bila nilai tidak dikenal sama sekali
     */
    public static FormFieldType parseLegacy(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase()) {
            case "text", "string", "input", "char" -> TEXT;
            case "textarea", "longtext", "multiline" -> TEXTAREA;
            case "select", "dropdown", "combobox" -> SELECT;
            case "phone", "telepon", "tel", "whatsapp" -> PHONE;
            case "province", "provinsi" -> PROVINCE;
            case "city", "kota" -> CITY;
            case "district", "kecamatan" -> DISTRICT;
            // tipe di luar cakupan — dipetakan ke padanan terdekat
            case "radio", "option", "checkbox", "check", "multiselect" -> SELECT;
            case "number", "numeric", "int", "integer",
                 "email", "mail",
                 "date", "tanggal", "datepicker",
                 "file", "upload", "image", "foto" -> TEXT;
            default -> null;
        };
    }

    /**
     * Deserialisasi dari JSON yang toleran terhadap huruf kecil dan istilah legacy,
     * sehingga payload lama ({@code "text"}, {@code "dropdown"}) tetap dapat diterima
     * tanpa memaksa klien bermigrasi lebih dahulu.
     */
    @JsonCreator
    public static FormFieldType fromJson(String raw) {
        FormFieldType strict = parseStrict(raw);
        return strict != null ? strict : parseLegacy(raw);
    }

    /** Parsing ketat untuk input API. Mengembalikan {@code null} bila tidak dikenal. */
    public static FormFieldType parseStrict(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
