package com.saktiform.api.model.product.formconfig;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Enam System Field bawaan. <b>Satu-satunya tempat daftar ini didefinisikan.</b>
 *
 * <p>Dipakai oleh: seeding produk baru, self-healing saat konfigurasi dibaca,
 * validator konfigurasi, validator nilai submit, pembangkit {@code field_key},
 * dan backfill pada {@code ProdukFormConfigSchemaInitializer}.
 *
 * <p>Kolom tujuan pada tabel {@code order} sengaja dicantumkan sebagai dokumentasi —
 * pemetaan payload submit tetap memakai nama atribut existing pada {@code CreateOrderDto}
 * dan tidak berubah oleh fitur ini.
 */
public enum SystemFormField {

    CUSTOMER_NAME("customer_name", FormFieldType.TEXT, "Nama", "Masukkan nama lengkap", 1,
            "nama_penerima",
            List.of("nama", "nama lengkap", "nama penerima", "nama customer",
                    "nama konsumen", "nama pemesan", "full name", "name")),

    PHONE_NUMBER("phone_number", FormFieldType.PHONE, "Nomor WhatsApp", "Contoh: 08123456789", 2,
            "nomor_whatsapp",
            List.of("whatsapp", "no wa", "nomor wa", "nomor whatsapp", "no hp",
                    "nomor hp", "telepon", "handphone", "phone")),

    ADDRESS("address", FormFieldType.TEXTAREA, "Alamat", "Masukkan alamat lengkap", 3,
            "alamat",
            List.of("alamat", "alamat lengkap", "alamat pengiriman", "address")),

    PROVINCE("province", FormFieldType.PROVINCE, "Provinsi", "Pilih provinsi", 4,
            "id_provinsi",
            List.of("provinsi", "province")),

    CITY("city", FormFieldType.CITY, "Kota", "Pilih kota", 5,
            "id_kota",
            List.of("kota", "kabupaten", "kota kabupaten", "city")),

    DISTRICT("district", FormFieldType.DISTRICT, "Kecamatan", "Pilih kecamatan", 6,
            "id_kecamatan",
            List.of("kecamatan", "district", "kec"));

    private final String key;
    private final FormFieldType type;
    private final String defaultLabel;
    private final String defaultPlaceholder;
    private final int defaultSortOrder;
    private final String orderColumn;
    private final List<String> labelAliases;

    SystemFormField(String key, FormFieldType type, String defaultLabel, String defaultPlaceholder,
                    int defaultSortOrder, String orderColumn, List<String> labelAliases) {
        this.key = key;
        this.type = type;
        this.defaultLabel = defaultLabel;
        this.defaultPlaceholder = defaultPlaceholder;
        this.defaultSortOrder = defaultSortOrder;
        this.orderColumn = orderColumn;
        this.labelAliases = labelAliases;
    }

    public String getKey() {
        return key;
    }

    public FormFieldType getType() {
        return type;
    }

    public String getDefaultLabel() {
        return defaultLabel;
    }

    public String getDefaultPlaceholder() {
        return defaultPlaceholder;
    }

    public int getDefaultSortOrder() {
        return defaultSortOrder;
    }

    /** Kolom tujuan pada tabel {@code order} — dokumentasi pemetaan. */
    public String getOrderColumn() {
        return orderColumn;
    }

    /** Aturan validasi bawaan yang ditampilkan ke frontend. Tidak dapat diubah Admin. */
    public ValidationRuleDto defaultValidationRule() {
        return switch (this) {
            case CUSTOMER_NAME -> ValidationRuleDto.ofLength(2, 150);
            case ADDRESS -> ValidationRuleDto.ofLength(10, 500);
            case PHONE_NUMBER -> ValidationRuleDto.ofPattern("^(\\+62|62|0)8[1-9][0-9]{6,11}$");
            default -> null;
        };
    }

    /** Endpoint master lokasi untuk field cascading. {@code null} bila tidak relevan. */
    public String getDataSource() {
        return switch (this) {
            case PROVINCE -> "/location/province";
            case CITY -> "/location/city?idProvince={province}";
            case DISTRICT -> "/location/district?idCity={city}";
            default -> null;
        };
    }

    private static final Map<String, SystemFormField> BY_KEY = new HashMap<>();

    static {
        for (SystemFormField f : values()) {
            BY_KEY.put(f.key, f);
        }
    }

    public static Optional<SystemFormField> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_KEY.get(key.trim().toLowerCase(Locale.ROOT)));
    }

    public static boolean isSystemKey(String key) {
        return byKey(key).isPresent();
    }

    public static Set<String> allKeys() {
        return BY_KEY.keySet();
    }

    /**
     * Heuristik pemetaan label legacy ke System Field, dipakai saat backfill.
     *
     * <p><b>Urutan pemeriksaan penting:</b> {@code PHONE_NUMBER} diperiksa sebelum
     * {@code CUSTOMER_NAME} (label "Nama &amp; No WA" lebih tepat sebagai nomor);
     * {@code CITY} sebelum {@code DISTRICT}.
     *
     * @param normalizedLabel label yang sudah lowercase, tanpa tanda baca, dan ter-trim
     */
    public static Optional<SystemFormField> matchByLabel(String normalizedLabel) {
        if (normalizedLabel == null || normalizedLabel.isBlank()) {
            return Optional.empty();
        }
        SystemFormField[] order = {PHONE_NUMBER, CUSTOMER_NAME, ADDRESS, PROVINCE, CITY, DISTRICT};
        for (SystemFormField f : order) {
            for (String alias : f.labelAliases) {
                if (normalizedLabel.equals(alias) || normalizedLabel.contains(alias)) {
                    return Optional.of(f);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Kata yang tidak boleh dipakai sebagai {@code field_key} Custom Field —
     * memuat kunci System Field beserta padanan bahasa Indonesianya.
     */
    public static final Set<String> RESERVED_KEYS = Set.of(
            "customer_name", "phone_number", "address", "province", "city", "district",
            "nama", "nama_penerima", "nama_lengkap", "nomor_whatsapp", "no_wa",
            "alamat", "provinsi", "kota", "kecamatan"
    );
}
